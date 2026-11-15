package sv.edu.ues.colibri.application.configuracion;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Servicio de parametrización de políticas, perfiles de contabilización y catálogos operativos.
 */
public class ParametrizacionService {

    private final JdbcSupport jdbc;
    private final AuthService auth;

    /**
     * Crea el servicio de parametrización.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     */
    public ParametrizacionService(JdbcSupport jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    /**
     * Parámetro genérico del sistema con su valor textual y descripción.
     */
    public record Parametro(String clave, String valor, String descripcion) {
    }

    /**
     * Parámetros fiscales y cuentas contables requeridas para operaciones tributarias.
     */
    public record ParametroFiscal(BigDecimal iva, BigDecimal retencionIva, BigDecimal pagoCuenta,
                                  BigDecimal retencionIsrServicios, BigDecimal retencionIsrIntangibles,
                                  String cuentaCredito, String cuentaDebito, String cuentaPorPagar,
                                  String cuentaRetencionIva, String cuentaPagoCuenta, String catalogoMh) {
    }

    /**
     * Perfil de contabilización para un evento operativo.
     */
    public record PostingProfile(String evento, String etiqueta, String cuentaDebe,
                                 String cuentaHaber, String cuentaIva) {
    }

    /**
     * Centro de costo parametrizado.
     */
    public record CentroCosto(String codigo, String nombre, boolean activo) {
    }

    /**
     * Plan de servicio parametrizado con sus cuentas y modalidad.
     */
    public record PlanServicio(UUID id, String codigo, String nombre, String modalidad,
                               BigDecimal tarifa, String tipoFacturacion, String cuentaIngreso, boolean activo) {
    }

    /**
     * Lista los parámetros de política configurados.
     *
     * @return lista de parámetros
     */
    public List<Parametro> listarPoliticas() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> query(conn,
                "SELECT clave, valor, descripcion FROM parametro_sistema ORDER BY clave", rs ->
                        new Parametro(rs.getString(1), rs.getString(2), rs.getString(3))));
    }

    /**
     * Actualiza el valor de una política por clave.
     *
     * @param clave clave del parámetro
     * @param valor valor a persistir
     */
    public void guardarPolitica(String clave, String valor) {
        auth.exigirContador();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE parametro_sistema SET valor=?, actualizado_en=? WHERE clave=?")) {
                ps.setString(1, valor);
                ps.setObject(2, LocalDateTime.now());
                ps.setString(3, clave);
                if (ps.executeUpdate() == 0) {
                    throw new DomainException("Parámetro no encontrado: " + clave);
                }
            }
        });
    }

    /**
     * Lee la configuración fiscal persistida.
     *
     * @return parámetros fiscales
     */
    public ParametroFiscal parametrosFiscales() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM parametro_fiscal LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DomainException("No hay parámetros fiscales");
                }
                return new ParametroFiscal(
                        rs.getBigDecimal("iva_tasa"),
                        rs.getBigDecimal("retencion_iva"),
                        rs.getBigDecimal("pago_cuenta_isr"),
                        rs.getBigDecimal("retencion_isr_servicios"),
                        rs.getBigDecimal("retencion_isr_intangibles"),
                        rs.getString("cuenta_iva_credito"),
                        rs.getString("cuenta_iva_debito"),
                        rs.getString("cuenta_iva_por_pagar"),
                        rs.getString("cuenta_retencion_iva"),
                        rs.getString("cuenta_pago_cuenta"),
                        rs.getString("catalogo_mh"));
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer parámetros fiscales", e);
            }
        });
    }

    /**
     * Obtiene la parametrización fiscal que puede utilizarse en una operación
     * contable. Las pantallas pueden leer la configuración sin validar para
     * corregirla; las operaciones, en cambio, no continúan con cuentas o tasas
     * inexistentes.
     */
    public ParametroFiscal exigirParametrosFiscales() {
        ParametroFiscal fiscal = parametrosFiscales();
        validarTasa(fiscal.iva(), "IVA");
        validarTasa(fiscal.retencionIva(), "retención de IVA");
        validarTasa(fiscal.pagoCuenta(), "pago a cuenta de ISR");
        validarTasa(fiscal.retencionIsrServicios(), "retención ISR de servicios");
        validarTasa(fiscal.retencionIsrIntangibles(), "retención ISR de intangibles");
        exigirCuentaAfectable(fiscal.cuentaCredito(), "cuenta de IVA crédito fiscal");
        exigirCuentaAfectable(fiscal.cuentaDebito(), "cuenta de IVA débito fiscal");
        exigirCuentaAfectable(fiscal.cuentaPorPagar(), "cuenta de IVA por pagar");
        exigirCuentaAfectable(fiscal.cuentaRetencionIva(), "cuenta de retención de IVA");
        exigirCuentaAfectable(fiscal.cuentaPagoCuenta(), "cuenta de pago a cuenta");
        return fiscal;
    }

    /**
     * Guarda la configuración fiscal validada.
     *
     * @param p parámetros fiscales a persistir
     */
    public void guardarFiscal(ParametroFiscal p) {
        auth.exigirContador();
        if (p == null) {
            throw new DomainException("Los parámetros fiscales son obligatorios.");
        }
        validarTasa(p.iva(), "IVA");
        validarTasa(p.retencionIva(), "retención de IVA");
        validarTasa(p.pagoCuenta(), "pago a cuenta de ISR");
        validarTasa(p.retencionIsrServicios(), "retención ISR de servicios");
        validarTasa(p.retencionIsrIntangibles(), "retención ISR de intangibles");
        exigirCuentaAfectable(p.cuentaCredito(), "cuenta de IVA crédito fiscal");
        exigirCuentaAfectable(p.cuentaDebito(), "cuenta de IVA débito fiscal");
        exigirCuentaAfectable(p.cuentaPorPagar(), "cuenta de IVA por pagar");
        exigirCuentaAfectable(p.cuentaRetencionIva(), "cuenta de retención de IVA");
        exigirCuentaAfectable(p.cuentaPagoCuenta(), "cuenta de pago a cuenta");
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE parametro_fiscal SET iva_tasa=?, retencion_iva=?, pago_cuenta_isr=?,
                        retencion_isr_servicios=?, retencion_isr_intangibles=?, cuenta_iva_credito=?,
                        cuenta_iva_debito=?, cuenta_iva_por_pagar=?, cuenta_retencion_iva=?,
                        cuenta_pago_cuenta=?, catalogo_mh=?, actualizado_en=?
                    """)) {
                ps.setBigDecimal(1, p.iva());
                ps.setBigDecimal(2, p.retencionIva());
                ps.setBigDecimal(3, p.pagoCuenta());
                ps.setBigDecimal(4, p.retencionIsrServicios());
                ps.setBigDecimal(5, p.retencionIsrIntangibles());
                ps.setString(6, p.cuentaCredito());
                ps.setString(7, p.cuentaDebito());
                ps.setString(8, p.cuentaPorPagar());
                ps.setString(9, p.cuentaRetencionIva());
                ps.setString(10, p.cuentaPagoCuenta());
                ps.setString(11, p.catalogoMh());
                ps.setObject(12, LocalDateTime.now());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Lista los perfiles de contabilización disponibles.
     *
     * @return lista de perfiles
     */
    public List<PostingProfile> listarPosting() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> query(conn,
                "SELECT evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva FROM posting_profile ORDER BY evento",
                rs -> new PostingProfile(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5))));
    }

    /** Devuelve un perfil operativo completo y con cuentas utilizables. */
    public PostingProfile exigirPerfilPosting(String evento) {
        auth.exigirContador();
        String eventoNormalizado = evento == null ? "" : evento.trim().toUpperCase(Locale.ROOT);
        if (eventoNormalizado.isBlank()) {
            throw new DomainException("Indique el evento para el perfil de contabilización.");
        }
        PostingProfile perfil = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva
                    FROM posting_profile WHERE evento = ?
                    """)) {
                ps.setString(1, eventoNormalizado);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("No existe un perfil de contabilización para "
                                + eventoNormalizado + ". Configúrelo antes de registrar la operación.");
                    }
                    return new PostingProfile(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al obtener el perfil de contabilización", e);
            }
        });
        exigirCuentaAfectable(perfil.cuentaDebe(), "cuenta Debe del perfil " + eventoNormalizado);
        exigirCuentaAfectable(perfil.cuentaHaber(), "cuenta Haber del perfil " + eventoNormalizado);
        if (perfil.cuentaIva() != null && !perfil.cuentaIva().isBlank()) {
            exigirCuentaAfectable(perfil.cuentaIva(), "cuenta IVA del perfil " + eventoNormalizado);
        }
        return perfil;
    }

    /**
     * Usa la cuenta del perfil por defecto; una cuenta indicada expresamente por
     * el usuario solo funciona como override y recibe la misma validación.
     */
    public String resolverCuenta(String override, String cuentaConfigurada, String etiqueta) {
        String cuenta = override == null || override.isBlank() ? cuentaConfigurada : override.trim();
        exigirCuentaAfectable(cuenta, etiqueta);
        return cuenta.trim();
    }

    /**
     * Guarda un perfil de contabilización validado.
     *
     * @param p perfil a persistir
     */
    public void guardarPosting(PostingProfile p) {
        auth.exigirContador();
        if (p == null) {
            throw new DomainException("El perfil de contabilización es obligatorio.");
        }
        String evento = p.evento() == null ? "" : p.evento().trim().toUpperCase(Locale.ROOT);
        if (evento.isBlank()) {
            throw new DomainException("El evento del perfil de contabilización es obligatorio.");
        }
        if (!evento.matches("[A-Z0-9_]{2,40}")) {
            throw new DomainException("El evento debe tener de 2 a 40 caracteres: mayúsculas, "
                    + "dígitos y guion bajo (ej. VENTA, MI_EVENTO).");
        }
        exigirCuentaAfectable(p.cuentaDebe(), "cuenta Debe del perfil " + evento);
        exigirCuentaAfectable(p.cuentaHaber(), "cuenta Haber del perfil " + evento);
        if (p.cuentaIva() != null && !p.cuentaIva().isBlank()) {
            exigirCuentaAfectable(p.cuentaIva(), "cuenta IVA del perfil " + evento);
        }
        String etiqueta = p.etiqueta() == null || p.etiqueta().isBlank()
                ? evento : p.etiqueta().trim();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO posting_profile (evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva,
                        actualizado_en)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT (evento) DO UPDATE SET etiqueta=EXCLUDED.etiqueta,
                        cuenta_debe=EXCLUDED.cuenta_debe, cuenta_haber=EXCLUDED.cuenta_haber,
                        cuenta_iva=EXCLUDED.cuenta_iva, actualizado_en=EXCLUDED.actualizado_en
                    """)) {
                ps.setString(1, evento);
                ps.setString(2, etiqueta);
                ps.setString(3, p.cuentaDebe());
                ps.setString(4, p.cuentaHaber());
                ps.setString(5, p.cuentaIva());
                ps.setObject(6, LocalDateTime.now());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Lista los centros de costo configurados.
     *
     * @return lista de centros de costo
     */
    public List<CentroCosto> listarCentros() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> query(conn,
                "SELECT codigo, nombre, activo FROM centro_costo ORDER BY codigo",
                rs -> new CentroCosto(rs.getString(1), rs.getString(2), rs.getBoolean(3))));
    }

    /**
     * Guarda o actualiza un centro de costo.
     *
     * @param c centro de costo a persistir
     */
    public void guardarCentro(CentroCosto c) {
        auth.exigirContador();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO centro_costo (codigo, nombre, activo) VALUES (?,?,?)
                    ON CONFLICT (codigo) DO UPDATE SET nombre=EXCLUDED.nombre, activo=EXCLUDED.activo
                    """)) {
                ps.setString(1, c.codigo().trim());
                ps.setString(2, c.nombre().trim());
                ps.setBoolean(3, c.activo());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Lista los planes de servicio configurados.
     *
     * @return lista de planes
     */
    public List<PlanServicio> listarPlanes() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> query(conn,
                "SELECT id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso, activo FROM plan_servicio ORDER BY codigo",
                rs -> new PlanServicio((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7), rs.getBoolean(8))));
    }

    /**
     * Guarda o actualiza un plan de servicio.
     *
     * @param plan plan a persistir
     */
    public void guardarPlan(PlanServicio plan) {
        auth.exigirContador();
        if (plan == null) {
            throw new DomainException("El plan de servicio es obligatorio.");
        }
        String codigo = textoObligatorio(plan.codigo(), "El código del plan");
        String nombre = textoObligatorio(plan.nombre(), "El nombre del plan");
        String modalidad = textoObligatorio(plan.modalidad(), "La modalidad del plan").toUpperCase(Locale.ROOT);
        if (!List.of("LICENCIA", "SAAS", "PROYECTO", "SERVICIO").contains(modalidad)) {
            throw new DomainException("Modalidad de plan inválida.");
        }
        String facturacion = textoObligatorio(plan.tipoFacturacion(), "El tipo de facturación").toUpperCase(Locale.ROOT);
        if (plan.tarifa() == null || plan.tarifa().signum() < 0) {
            throw new DomainException("La tarifa base del plan no puede ser negativa.");
        }
        String cuentaIngreso = exigirCuentaAfectable(plan.cuentaIngreso(), "cuenta de ingreso del plan");
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso, activo)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON CONFLICT (codigo) DO UPDATE SET nombre=EXCLUDED.nombre, modalidad=EXCLUDED.modalidad,
                        tarifa_base=EXCLUDED.tarifa_base, tipo_facturacion=EXCLUDED.tipo_facturacion,
                        cuenta_ingreso=EXCLUDED.cuenta_ingreso, activo=EXCLUDED.activo
                    """)) {
                ps.setObject(1, plan.id() == null ? UUID.randomUUID() : plan.id());
                ps.setString(2, codigo);
                ps.setString(3, nombre);
                ps.setString(4, modalidad);
                ps.setBigDecimal(5, plan.tarifa());
                ps.setString(6, facturacion);
                ps.setString(7, cuentaIngreso);
                ps.setBoolean(8, plan.activo());
                ps.executeUpdate();
            }
        });
    }

    private String textoObligatorio(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    /**
     * Verifica que una cuenta exista, esté activa y admita movimientos.
     *
     * @param codigo código de cuenta a validar
     * @param etiqueta descripción funcional de la cuenta
     * @return código normalizado de la cuenta
     */
    public String exigirCuentaAfectable(String codigo, String etiqueta) {
        auth.exigirContador();
        String cuenta = codigo == null ? "" : codigo.trim();
        if (cuenta.isBlank()) {
            throw new DomainException("Falta configurar " + etiqueta + ".");
        }
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT admite_movimiento, estado FROM cuenta WHERE codigo = ?
                    """)) {
                ps.setString(1, cuenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("La " + etiqueta + " no existe en el catálogo: " + cuenta);
                    }
                    if (!rs.getBoolean("admite_movimiento") || !"ACTIVA".equals(rs.getString("estado"))) {
                        throw new DomainException("La " + etiqueta
                                + " debe ser una cuenta activa y afectable: " + cuenta);
                    }
                    return cuenta;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar " + etiqueta, e);
            }
        });
    }

    private void validarTasa(BigDecimal tasa, String etiqueta) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainException("La tasa de " + etiqueta + " debe estar entre 0 y 1.");
        }
    }

    private <T> List<T> query(Connection conn, String sql, RowMap<T> map) {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<T> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map.map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new PersistenceException("Error de consulta", e);
        }
    }

    @FunctionalInterface
    private interface RowMap<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
