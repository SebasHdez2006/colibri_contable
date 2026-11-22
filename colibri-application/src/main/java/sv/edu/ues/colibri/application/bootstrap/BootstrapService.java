package sv.edu.ues.colibri.application.bootstrap;

import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.Ejercicio;
import sv.edu.ues.colibri.domain.model.Empresa;
import sv.edu.ues.colibri.domain.model.EstadoPeriodo;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.RolUsuario;
import sv.edu.ues.colibri.domain.security.PasswordHasher;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.ContadorRepository;
import sv.edu.ues.colibri.persistence.repo.EmpresaRepository;
import sv.edu.ues.colibri.persistence.repo.PeriodoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Inicializa empresa, ejercicio 2026, períodos y credencial del contador si no existen.
 * La contraseña inicial y el código de recuperación se generan en la primera ejecución;
 * el código se escribe en %USERPROFILE%\.colibri\recovery-code.txt
 */
/**
 * Servicio de arranque que crea datos iniciales cuando la base está vacía.
 */
public class BootstrapService {

    public static final String USUARIO_DEFAULT = "contador";
    public static final String PASSWORD_DEFAULT = "Colibri2026!";
    public static final String USUARIO_SOPORTE = "soporte";
    public static final String PASSWORD_SOPORTE = "Soporte2026!";

    private final JdbcSupport jdbc;
    private final EmpresaRepository empresaRepository = new EmpresaRepository();
    private final ContadorRepository contadorRepository = new ContadorRepository();
    private final PeriodoRepository periodoRepository = new PeriodoRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    private String codigoRecuperacionGenerado;

    /**
     * Crea el servicio de inicialización.
     *
     * @param jdbc soporte JDBC transaccional
     */
    public BootstrapService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Obtiene el código de recuperación generado durante el arranque.
     *
     * @return código de recuperación o {@code null}
     */
    public String getCodigoRecuperacionGenerado() {
        return codigoRecuperacionGenerado;
    }

    /**
     * Garantiza la existencia de empresa, usuarios demo y ejercicio inicial.
     */
    public void asegurarDatosIniciales() {
        jdbc.runInTransaction(conn -> {
            if (empresaRepository.obtenerUnica(conn).isEmpty()) {
                Empresa empresa = new Empresa(
                        UUID.randomUUID(),
                        "Soluciones Empresariales Colibrí, S.A. de C.V.",
                        "Colibrí",
                        "SIMULADO-NIT",
                        "SIMULADO-NRC",
                        "USD",
                        "America/El_Salvador",
                        LocalDateTime.now()
                );
                empresaRepository.insertar(conn, empresa);
            }

            Empresa empresa = empresaRepository.obtenerUnica(conn).orElseThrow();

            asegurarUsuarioDemo(conn, USUARIO_DEFAULT, "Contador Colibrí", PASSWORD_DEFAULT,
                    RolUsuario.CONTADOR, "contador@colibri.sv");
            asegurarUsuarioDemo(conn, USUARIO_SOPORTE, "Soporte Técnico Colibrí", PASSWORD_SOPORTE,
                    RolUsuario.SOPORTE_TECNICO, "soporte@colibri.sv");

            if (periodoRepository.buscarEjercicioPorAnio(conn, 2026).isEmpty()) {
                int anio = 2026;
                Ejercicio ejercicio = new Ejercicio(
                        UUID.randomUUID(),
                        empresa.getId(),
                        anio,
                        LocalDate.of(anio, 1, 1),
                        LocalDate.of(anio, 12, 31),
                        true,
                        1
                );
                periodoRepository.insertarEjercicio(conn, ejercicio);
                for (int mes = 1; mes <= 12; mes++) {
                    YearMonth ym = YearMonth.of(anio, mes);
                    Periodo periodo = new Periodo(
                            UUID.randomUUID(),
                            ejercicio.getId(),
                            mes,
                            ym.atDay(1),
                            ym.atEndOfMonth(),
                            mes == 1 ? EstadoPeriodo.ABIERTO : EstadoPeriodo.ABIERTO,
                            1
                    );
                    periodoRepository.insertarPeriodo(conn, periodo);
                }
            }
        });
    }

    /**
     * Crea las cuentas iniciales solo cuando todavía no existen. Un arranque no
     * es un mecanismo de recuperación: nunca debe restablecer credenciales,
     * roles, estado ni datos de una cuenta ya administrada.
     */
    /**
     * Crea una cuenta demo únicamente si todavía no existe.
     *
     * @param conn conexión activa de la transacción
     * @param usuario nombre de usuario
     * @param nombre nombre visible
     * @param password contraseña inicial
     * @param rol rol funcional
     * @param email correo electrónico
     */
    private void asegurarUsuarioDemo(java.sql.Connection conn, String usuario, String nombre,
                                     String password, RolUsuario rol, String email) {
        var existente = contadorRepository.buscarPorUsuario(conn, usuario);
        if (existente.isPresent()) {
            return;
        }

        byte[] salt = PasswordHasher.generarSal();
        byte[] hash = PasswordHasher.hash(password.toCharArray(), salt);
        String codigo = PasswordHasher.generarCodigoRecuperacion();
        byte[] codigoSalt = PasswordHasher.generarSal();
        byte[] codigoHash = PasswordHasher.hash(codigo.toCharArray(), codigoSalt);
        if (USUARIO_DEFAULT.equals(usuario)) {
            this.codigoRecuperacionGenerado = codigo;
        }
        Contador creado = new Contador(
                UUID.randomUUID(), usuario, nombre, hash, salt, 0, null,
                codigoHash, codigoSalt, true, null, rol, email);
        contadorRepository.insertar(conn, creado);
        bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                creado.getId(), "BOOTSTRAP", "USUARIO", creado.getId().toString(),
                null, usuario, "Cuenta inicial creada"));
    }
}
