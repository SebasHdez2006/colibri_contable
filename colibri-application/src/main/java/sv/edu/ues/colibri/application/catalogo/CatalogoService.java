package sv.edu.ues.colibri.application.catalogo;

import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;
import sv.edu.ues.colibri.domain.model.NaturalezaCuenta;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.CatalogoVersionRepository;
import sv.edu.ues.colibri.persistence.repo.CuentaRepository;
import sv.edu.ues.colibri.application.auth.AuthService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de catálogo de cuentas y versión inicial del plan contable.
 */
public class CatalogoService {

    private final JdbcSupport jdbc;
    private final AuthService authService;
    private final CuentaRepository cuentaRepository = new CuentaRepository();
    private final CatalogoVersionRepository catalogoVersionRepository = new CatalogoVersionRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    /**
     * Crea el servicio de catálogo.
     *
     * @param jdbc soporte transaccional
     * @param authService servicio de autenticación
     */
    public CatalogoService(JdbcSupport jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    /**
     * Lista todas las cuentas del catálogo.
     *
     * @return cuentas registradas
     */
    public List<Cuenta> listar() {
        authService.exigirContador();
        return jdbc.inTransaction(cuentaRepository::listarTodas);
    }

    /**
     * Cuenta la cantidad total de cuentas del catálogo.
     *
     * @return número de cuentas
     */
    public long contarCuentas() {
        authService.exigirContador();
        return jdbc.inTransaction(cuentaRepository::contar);
    }

    /**
     * Indica si el catálogo inicial ya fue aprobado.
     *
     * @return {@code true} si existe aprobación registrada
     */
    public boolean catalogoAprobado() {
        return jdbc.inTransaction(catalogoVersionRepository::estaAprobado);
    }

    /**
     * Aprueba la versión inicial del catálogo de cuentas.
     */
    public void aprobarCatalogoInicial() {
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            if (catalogoVersionRepository.estaAprobado(conn)) {
                throw new DomainException("El catálogo inicial ya fue aprobado.");
            }
            catalogoVersionRepository.aprobar(conn, contador.getId());
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "APROBAR_CATALOGO", "CATALOGO", "1",
                    "pendiente", "aprobado", "Aprobación versión inicial tabla VIII"));
        });
    }

    /**
     * Crea una subcuenta de movimiento bajo una cuenta padre de cuatro dígitos.
     *
     * @param codigo código de la nueva cuenta
     * @param nombre nombre de la cuenta
     * @param codigoPadre código de la cuenta padre
     * @param naturaleza naturaleza contable
     * @param rubro rubro de clasificación
     */
    public void crearSubcuenta(String codigo, String nombre, String codigoPadre,
                               NaturalezaCuenta naturaleza, String rubro) {
        var contador = authService.exigirContador();
        if (!Cuenta.esCodigoMovimiento(codigo)) {
            throw new DomainException("Las nuevas cuentas de movimiento deben tener código de 6 dígitos.");
        }
        jdbc.runInTransaction(conn -> {
            if (cuentaRepository.buscarPorCodigo(conn, codigo).isPresent()) {
                throw new DomainException("Ya existe la cuenta " + codigo);
            }
            Cuenta padre = cuentaRepository.buscarPorCodigo(conn, codigoPadre)
                    .orElseThrow(() -> new DomainException("Cuenta padre no encontrada: " + codigoPadre));
            if (padre.getNivel() != 4) {
                throw new DomainException("El padre de una subcuenta de movimiento debe ser cuenta de 4 dígitos.");
            }
            // La subcuenta debe extender el código del padre y heredar su naturaleza
            // y rubro: una cuenta hija no puede contradecir la clasificación del grupo.
            if (!codigo.startsWith(codigoPadre)) {
                throw new DomainException("El código " + codigo + " debe iniciar con el prefijo de la "
                        + "cuenta padre " + codigoPadre + ".");
            }
            Cuenta nueva = new Cuenta(UUID.randomUUID(), codigo, nombre, codigoPadre, 6,
                    padre.getNaturaleza(), padre.getRubro(), EstadoCuenta.ACTIVA,
                    LocalDate.now(), null, true);
            cuentaRepository.insertar(conn, nueva);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "CREAR_CUENTA", "CUENTA", codigo, null, nombre, null));
        });
    }

    /**
     * Actualiza el nombre de una cuenta existente.
     *
     * @param codigo código de la cuenta
     * @param nuevoNombre nuevo nombre
     */
    public void actualizarDenominacion(String codigo, String nuevoNombre) {
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            Cuenta cuenta = cuentaRepository.buscarPorCodigo(conn, codigo)
                    .orElseThrow(() -> new DomainException("Cuenta no encontrada"));
            String anterior = cuenta.getNombre();
            cuenta.setNombre(nuevoNombre);
            cuentaRepository.actualizarNombreYEstado(conn, cuenta);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "EDITAR_CUENTA", "CUENTA", codigo, anterior, nuevoNombre, null));
        });
    }

    /**
     * Cambia el estado de una cuenta existente.
     *
     * @param codigo código de la cuenta
     * @param nuevoEstado nuevo estado
     */
    public void cambiarEstado(String codigo, EstadoCuenta nuevoEstado) {
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            Cuenta cuenta = cuentaRepository.buscarPorCodigo(conn, codigo)
                    .orElseThrow(() -> new DomainException("Cuenta no encontrada"));
            if (nuevoEstado == EstadoCuenta.DESACTIVADA) {
                if (cuentaRepository.tieneMovimientos(conn, codigo)) {
                    // Una cuenta con movimientos conserva su historial y sus saldos:
                    // para impedir nuevas imputaciones se usa BLOQUEADA, no DESACTIVADA.
                    throw new DomainException("La cuenta " + codigo + " tiene movimientos "
                            + "registrados y no puede desactivarse. Si desea impedir nuevos "
                            + "movimientos, cámbiela a BLOQUEADA.");
                }
                if (referenciadaEnPerfilPosting(conn, codigo)) {
                    throw new DomainException("La cuenta " + codigo + " está asociada a un perfil "
                            + "de contabilización y no puede desactivarse.");
                }
            }
            String anterior = cuenta.getEstado().name();
            cuenta.setEstado(nuevoEstado);
            cuentaRepository.actualizarNombreYEstado(conn, cuenta);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ESTADO_CUENTA", "CUENTA", codigo, anterior, nuevoEstado.name(), null));
        });
    }

    /**
     * Indica si la cuenta aparece en algún perfil de contabilización activo.
     *
     * @param conn conexión activa
     * @param codigo código de cuenta
     * @return {@code true} si algún perfil la referencia en Debe, Haber o IVA
     */
    private boolean referenciadaEnPerfilPosting(Connection conn, String codigo) {
        String sql = """
                SELECT COUNT(*) FROM posting_profile
                WHERE cuenta_debe = ? OR cuenta_haber = ? OR cuenta_iva = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setString(2, codigo);
            ps.setString(3, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al verificar perfiles de contabilización", e);
        }
    }

    /**
     * Obtiene una cuenta de movimiento activa o falla si no cumple las reglas.
     *
     * @param codigo código de la cuenta
     * @return cuenta válida para registrar movimientos
     */
    public Cuenta exigirMovimientoActiva(String codigo) {
        return jdbc.inTransaction(conn -> {
            Cuenta cuenta = cuentaRepository.buscarPorCodigo(conn, codigo)
                    .orElseThrow(() -> new DomainException("Cuenta no encontrada: " + codigo));
            if (!cuenta.puedeRecibirMovimiento()) {
                throw new DomainException("La cuenta " + codigo
                        + " no admite movimiento (debe ser subcuenta activa de 6 dígitos).");
            }
            return cuenta;
        });
    }
}
