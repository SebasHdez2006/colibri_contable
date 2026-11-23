package sv.edu.ues.colibri.application.costos;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.calculation.CostosCalculator;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Presupuesto, aplicación y cierre de costos indirectos por período y centro.
 *
 * <p>La cédula conserva las bases y los importes que originaron la tasa. La
 * aplicación deja una partida y un consumo de orden trazable; el cierre libera
 * los controles 5201/5202 y lleva sólo la diferencia a costo de proyectos.
 * Las claves de evento y los auxiliares únicos permiten reintentar cada paso
 * sin duplicar una partida ni el consumo asociado.</p>
 */
public final class CifService {

    private static final String CUENTA_CIF_REAL_PREDETERMINADA = "520101";
    private static final String CUENTA_CIF_APLICADO_ORDENES = "520201";
    private static final String CUENTA_RESULTADO_CIF_PROYECTOS = "510103";
    private static final BigDecimal TOLERANCIA_REDONDEO = new BigDecimal("0.01");

    public record CifEntrada(LocalDate fecha, String centroCosto, BigDecimal cifEstimado,
                             BigDecimal baseEstimada, BigDecimal baseReal, BigDecimal cifReal) {
    }

    /** Base real que se aplica a una orden concreta durante el período. */
    public record AplicacionOrdenEntrada(LocalDate fecha, String centroCosto, String codigoOrden,
                                         BigDecimal baseAplicada, String descripcion) {
    }

    /** Evidencia y partida de la aplicación de CIF a una orden. */
    public record AplicacionOrden(String codigoOrden, String centroCosto, BigDecimal tasaPredeterminada,
                                  BigDecimal baseAplicada, BigDecimal importeAplicado, Partida partida) {
    }

    /** Resultado del cierre de controles CIF para un centro y período. */
    public record CierreVariacion(String centroCosto, BigDecimal cifReal,
                                  BigDecimal cifAplicadoEsperado, BigDecimal cifAplicadoContable,
                                  BigDecimal variacion, CostosCalculator.EstadoCif estado,
                                  Partida partida) {
    }

    private record CedulaCif(BigDecimal cifEstimado, BigDecimal baseEstimada,
                              BigDecimal cifReal, BigDecimal baseReal) {
        CostosCalculator.AnalisisCif analizar() {
            return CostosCalculator.analizarCif(cifEstimado, baseEstimada, baseReal, cifReal);
        }
    }

    private record AplicacionPersistida(UUID ordenId, BigDecimal baseAplicada,
                                        BigDecimal tasaPredeterminada, BigDecimal importe,
                                        LocalDate fechaAplicacion, String descripcion,
                                        UUID partidaId, String claveEvento) {
    }

    private record CierrePersistido(String cuentaCifReal, BigDecimal cifReal,
                                    BigDecimal cifAplicado, BigDecimal variacion,
                                    LocalDate fechaCierre, UUID partidaId, String claveEvento) {
    }

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PeriodoAppService periodos;
    private final PartidaService partidas;
    private final PartidaRepository partidaRepository = new PartidaRepository();

    /**
     * Crea el servicio de CIF sin operaciones de aplicación o cierre.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     * @param periodos servicio de períodos
     */
    public CifService(JdbcSupport jdbc, AuthService auth, PeriodoAppService periodos) {
        this(jdbc, auth, periodos, null);
    }

    /**
     * Crea el servicio de CIF con soporte opcional para partidas.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     * @param periodos servicio de períodos
     * @param partidas servicio de partidas, o {@code null} si solo se registra la cédula
     */
    public CifService(JdbcSupport jdbc, AuthService auth, PeriodoAppService periodos,
                      PartidaService partidas) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.periodos = periodos;
        this.partidas = partidas;
    }

    /** Guarda la evidencia del período y devuelve tasa, CIF aplicado y variación. */
    public CostosCalculator.AnalisisCif guardar(CifEntrada entrada) {
        auth.exigirContador();
        if (entrada == null || entrada.fecha() == null) {
            throw new DomainException("La fecha del análisis CIF es obligatoria.");
        }
        String centro = normalizarCentro(entrada.centroCosto());
        exigirCentro(centro);
        CostosCalculator.AnalisisCif analisis;
        try {
            analisis = CostosCalculator.analizarCif(entrada.cifEstimado(), entrada.baseEstimada(),
                    entrada.baseReal(), entrada.cifReal());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage(), e);
        }
        Periodo periodo = periodos.exigirPeriodoAbiertoParaFecha(entrada.fecha());
        validarCedulaModificable(periodo.getId(), centro, analisis);
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO cif_presupuesto_periodo
                        (id, periodo_id, centro_codigo, cif_estimado, base_estimada, cif_real, base_real)
                    VALUES (?,?,?,?,?,?,?)
                    ON CONFLICT (periodo_id, centro_codigo) DO UPDATE SET
                        cif_estimado=EXCLUDED.cif_estimado, base_estimada=EXCLUDED.base_estimada,
                        cif_real=EXCLUDED.cif_real, base_real=EXCLUDED.base_real
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, periodo.getId());
                ps.setString(3, centro);
                ps.setBigDecimal(4, analisis.cifEstimados());
                ps.setBigDecimal(5, analisis.baseEstimada());
                ps.setBigDecimal(6, analisis.cifReales());
                ps.setBigDecimal(7, analisis.baseReal());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo guardar el análisis CIF", e);
            }
        });
        return analisis;
    }

    /**
     * F-CIF-02: aplica la tasa de la cédula a una orden y registra Debe 110503
     * contra Haber 520201. Cada orden puede recibir una aplicación por centro y
     * período, con una clave idempotente que admite reintentos seguros.
     */
    public AplicacionOrden aplicarAOrden(AplicacionOrdenEntrada entrada) {
        auth.exigirContador();
        if (entrada == null || entrada.fecha() == null) {
            throw new DomainException("La fecha de aplicación de CIF es obligatoria.");
        }
        String centro = normalizarCentro(entrada.centroCosto());
        exigirCentro(centro);
        String codigoOrden = textoObligatorio(entrada.codigoOrden(), "El código de la orden");
        BigDecimal base = cantidadPositiva(entrada.baseAplicada(), "La base aplicada");
        Periodo periodo = periodos.exigirPeriodoAbiertoParaFecha(entrada.fecha());
        UUID ordenId = exigirOrdenId(codigoOrden);

        Optional<AplicacionPersistida> existente = buscarAplicacion(periodo.getId(), centro, ordenId);
        if (existente.isPresent()) {
            validarMismaAplicacion(existente.get(), base);
            completarConsumoOrden(periodo.getId(), existente.get());
            return construirAplicacion(codigoOrden, centro, existente.get());
        }

        CedulaCif cedula = exigirCedula(periodo.getId(), centro);
        CostosCalculator.AnalisisCif analisis = cedula.analizar();
        BigDecimal importe = CostosCalculator.cifAplicados(base, analisis.tasaPredeterminada());
        if (importe.signum() <= 0) {
            throw new DomainException("La tasa CIF y la base aplicada deben producir un importe mayor que cero.");
        }
        String descripcion = descripcion(entrada.descripcion(), codigoOrden);
        String clave = claveAplicacion(periodo.getId(), centro, ordenId);
        Partida partida = asegurarPartida(entrada.fecha(), "Aplicación CIF a orden " + codigoOrden,
                codigoOrden, clave, List.of(
                        PartidaDetalle.nueva("110503", codigoOrden, centro, importe, BigDecimal.ZERO),
                        PartidaDetalle.nueva(CUENTA_CIF_APLICADO_ORDENES, codigoOrden, centro,
                                BigDecimal.ZERO, importe)));
        AplicacionPersistida persistida = asegurarAplicacion(periodo.getId(), centro, ordenId, base,
                analisis.tasaPredeterminada(), importe, entrada.fecha(), descripcion, partida.getId(), clave);
        return construirAplicacion(codigoOrden, centro, persistida);
    }

    /** Cierra la variación usando 520101 como cuenta de CIF real del centro. */
    public CierreVariacion cerrarVariacion(LocalDate fecha, String centroCosto) {
        return cerrarVariacion(fecha, centroCosto, CUENTA_CIF_REAL_PREDETERMINADA);
    }

    /**
     * F-CIF-03: cancela CIF aplicado contra CIF real y lleva la sub/sobreaplicación
     * a 510103. La cédula debe estar conciliada con las aplicaciones a órdenes;
     * una diferencia de hasta un centavo se admite por redondeo de distribuciones.
     */
    public CierreVariacion cerrarVariacion(LocalDate fecha, String centroCosto, String cuentaCifReal) {
        auth.exigirContador();
        if (fecha == null) {
            throw new DomainException("La fecha de cierre CIF es obligatoria.");
        }
        String centro = normalizarCentro(centroCosto);
        exigirCentro(centro);
        String cuentaReal = normalizarCuentaCifReal(cuentaCifReal);
        Periodo periodo = periodos.exigirPeriodoAbiertoParaFecha(fecha);
        Optional<CierrePersistido> existente = buscarCierre(periodo.getId(), centro);
        if (existente.isPresent()) {
            return construirCierre(centro, existente.get(), exigirCedula(periodo.getId(), centro).analizar());
        }

        CostosCalculator.AnalisisCif analisis = exigirCedula(periodo.getId(), centro).analizar();
        BigDecimal aplicadoContable = totalAplicado(periodo.getId(), centro);
        validarConciliacionAplicaciones(analisis.cifAplicados(), aplicadoContable);
        // El CIF real que se cancela es el saldo mayorizado de la cuenta de control
        // en el período y centro, no el monto digitado en la cédula: así la cuenta
        // queda en cero aunque la cédula difiera del mayor o existan asientos
        // manuales de CIF real.
        BigDecimal real = jdbc.inTransaction(conn ->
                saldoCuentaCifReal(conn, cuentaReal, centro, periodo.getId()));
        if (real.signum() == 0 && aplicadoContable.signum() == 0) {
            throw new DomainException("No hay CIF real ni CIF aplicado que cerrar en este centro.");
        }
        BigDecimal variacion = real.subtract(aplicadoContable).setScale(2, RoundingMode.HALF_UP);
        String clave = claveCierre(periodo.getId(), centro);
        Partida partida = asegurarPartida(fecha, "Cierre de variación CIF " + centro, centro, clave,
                lineasCierre(real, aplicadoContable, variacion, cuentaReal, centro));
        CierrePersistido persistido = asegurarCierre(periodo.getId(), centro, cuentaReal, real,
                aplicadoContable, variacion, fecha, partida.getId(), clave);
        return construirCierre(centro, persistido, analisis);
    }

    private List<PartidaDetalle> lineasCierre(BigDecimal real, BigDecimal aplicado, BigDecimal variacion,
                                               String cuentaReal, String centro) {
        java.util.ArrayList<PartidaDetalle> lineas = new java.util.ArrayList<>();
        if (aplicado.signum() > 0) {
            lineas.add(PartidaDetalle.nueva(CUENTA_CIF_APLICADO_ORDENES, null, centro,
                    aplicado, BigDecimal.ZERO));
        }
        if (real.signum() > 0) {
            lineas.add(PartidaDetalle.nueva(cuentaReal, null, centro, BigDecimal.ZERO, real));
        } else if (real.signum() < 0) {
            // Saldo acreedor en la cuenta de CIF real: se debita su valor absoluto
            // para dejar la cuenta en cero sin descuadrar el asiento.
            lineas.add(PartidaDetalle.nueva(cuentaReal, null, centro, real.abs(), BigDecimal.ZERO));
        }
        if (variacion.signum() > 0) {
            lineas.add(PartidaDetalle.nueva(CUENTA_RESULTADO_CIF_PROYECTOS, null, centro,
                    variacion, BigDecimal.ZERO));
        } else if (variacion.signum() < 0) {
            lineas.add(PartidaDetalle.nueva(CUENTA_RESULTADO_CIF_PROYECTOS, null, centro,
                    BigDecimal.ZERO, variacion.abs()));
        }
        return List.copyOf(lineas);
    }

    private AplicacionOrden construirAplicacion(String codigoOrden, String centro,
                                                 AplicacionPersistida aplicacion) {
        Partida partida = buscarPartida(aplicacion.partidaId());
        return new AplicacionOrden(codigoOrden, centro, aplicacion.tasaPredeterminada(),
                aplicacion.baseAplicada(), aplicacion.importe(), partida);
    }

    private CierreVariacion construirCierre(String centro, CierrePersistido cierre,
                                            CostosCalculator.AnalisisCif analisis) {
        CostosCalculator.EstadoCif estado = estado(cierre.variacion());
        return new CierreVariacion(centro, cierre.cifReal(), analisis.cifAplicados(),
                cierre.cifAplicado(), cierre.variacion(), estado, buscarPartida(cierre.partidaId()));
    }

    private Partida asegurarPartida(LocalDate fecha, String concepto, String soporte, String clave,
                                    List<PartidaDetalle> lineas) {
        exigirServicioPartidas();
        Optional<Partida> existente = buscarPartidaPorEvento(clave);
        if (existente.isPresent()) {
            return contabilizarSiPendiente(existente.get());
        }
        try {
            Partida borrador = partidas.guardarBorrador(TipoPartida.DI, fecha, concepto, soporte,
                    clave, lineas, null);
            return partidas.contabilizar(borrador.getId());
        } catch (DomainException ex) {
            Optional<Partida> concurrente = buscarPartidaPorEvento(clave);
            if (concurrente.isPresent()) {
                return contabilizarSiPendiente(concurrente.get());
            }
            throw ex;
        }
    }

    private Partida contabilizarSiPendiente(Partida partida) {
        if (partida.getEstado() == EstadoPartida.CONTABILIZADA) {
            return partida;
        }
        if (partida.getEstado() == EstadoPartida.REVERTIDA || partida.getEstado() == EstadoPartida.ANULADA) {
            throw new DomainException("La partida CIF existente fue " + partida.getEstado().name().toLowerCase()
                    + "; registre una nueva aplicación o cierre en un período abierto.");
        }
        return partidas.contabilizar(partida.getId());
    }

    private AplicacionPersistida asegurarAplicacion(UUID periodoId, String centro, UUID ordenId,
                                                     BigDecimal base, BigDecimal tasa, BigDecimal importe,
                                                     LocalDate fecha, String descripcion, UUID partidaId,
                                                     String clave) {
        return jdbc.inTransaction(conn -> {
            Optional<AplicacionPersistida> existente = buscarAplicacion(conn, periodoId, centro, ordenId);
            if (existente.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO cif_aplicacion_orden
                            (id, periodo_id, centro_codigo, orden_id, base_aplicada, tasa_predeterminada,
                             importe, fecha_aplicacion, descripcion, partida_id, clave_evento)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (periodo_id, centro_codigo, orden_id) DO NOTHING
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, periodoId);
                    ps.setString(3, centro);
                    ps.setObject(4, ordenId);
                    ps.setBigDecimal(5, base);
                    ps.setBigDecimal(6, tasa);
                    ps.setBigDecimal(7, importe);
                    ps.setObject(8, fecha);
                    ps.setString(9, descripcion);
                    ps.setObject(10, partidaId);
                    ps.setString(11, clave);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new PersistenceException("No se pudo vincular la aplicación CIF con la orden", e);
                }
                existente = buscarAplicacion(conn, periodoId, centro, ordenId);
            }
            AplicacionPersistida aplicacion = existente.orElseThrow(() ->
                    new PersistenceException("No se pudo recuperar la aplicación CIF registrada"));
            validarMismaAplicacion(aplicacion, base);
            asegurarConsumoOrden(conn, periodoId, aplicacion);
            return aplicacion;
        });
    }

    private void asegurarConsumoOrden(java.sql.Connection conn, UUID periodoId,
                                      AplicacionPersistida aplicacion) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO orden_consumo
                    (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id)
                SELECT ?, ?, 'CIF', ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM orden_consumo WHERE partida_id = ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, aplicacion.ordenId());
            ps.setString(3, aplicacion.descripcion());
            ps.setBigDecimal(4, aplicacion.baseAplicada());
            ps.setBigDecimal(5, tarifaParaAuxiliar(aplicacion));
            ps.setBigDecimal(6, aplicacion.importe());
            ps.setObject(7, periodoId);
            ps.setObject(8, aplicacion.fechaAplicacion());
            ps.setObject(9, aplicacion.partidaId());
            ps.setObject(10, aplicacion.partidaId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo registrar el consumo CIF de la orden", e);
        }
    }

    /** Repara un auxiliar pendiente si una aplicación contabilizada se reintenta. */
    private void completarConsumoOrden(UUID periodoId, AplicacionPersistida aplicacion) {
        jdbc.runInTransaction(conn -> asegurarConsumoOrden(conn, periodoId, aplicacion));
    }

    private CierrePersistido asegurarCierre(UUID periodoId, String centro, String cuentaReal,
                                            BigDecimal real, BigDecimal aplicado, BigDecimal variacion,
                                            LocalDate fecha, UUID partidaId, String clave) {
        return jdbc.inTransaction(conn -> {
            Optional<CierrePersistido> existente = buscarCierre(conn, periodoId, centro);
            if (existente.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO cif_cierre_periodo
                            (id, periodo_id, centro_codigo, cuenta_cif_real, cif_real, cif_aplicado,
                             variacion, fecha_cierre, partida_id, clave_evento)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (periodo_id, centro_codigo) DO NOTHING
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, periodoId);
                    ps.setString(3, centro);
                    ps.setString(4, cuentaReal);
                    ps.setBigDecimal(5, real);
                    ps.setBigDecimal(6, aplicado);
                    ps.setBigDecimal(7, variacion);
                    ps.setObject(8, fecha);
                    ps.setObject(9, partidaId);
                    ps.setString(10, clave);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new PersistenceException("No se pudo guardar el cierre de variación CIF", e);
                }
                existente = buscarCierre(conn, periodoId, centro);
            }
            return existente.orElseThrow(() -> new PersistenceException("No se pudo recuperar el cierre CIF"));
        });
    }

    private Optional<AplicacionPersistida> buscarAplicacion(UUID periodoId, String centro, UUID ordenId) {
        return jdbc.inTransaction(conn -> buscarAplicacion(conn, periodoId, centro, ordenId));
    }

    private Optional<AplicacionPersistida> buscarAplicacion(java.sql.Connection conn, UUID periodoId,
                                                             String centro, UUID ordenId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT orden_id, base_aplicada, tasa_predeterminada, importe, fecha_aplicacion,
                       descripcion, partida_id, clave_evento
                FROM cif_aplicacion_orden
                WHERE periodo_id=? AND centro_codigo=? AND orden_id=?
                """)) {
            ps.setObject(1, periodoId);
            ps.setString(2, centro);
            ps.setObject(3, ordenId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AplicacionPersistida((UUID) rs.getObject("orden_id"),
                        rs.getBigDecimal("base_aplicada"), rs.getBigDecimal("tasa_predeterminada"),
                        rs.getBigDecimal("importe"), rs.getObject("fecha_aplicacion", LocalDate.class),
                        rs.getString("descripcion"), (UUID) rs.getObject("partida_id"),
                        rs.getString("clave_evento")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo consultar la aplicación CIF de la orden", e);
        }
    }

    private Optional<CierrePersistido> buscarCierre(UUID periodoId, String centro) {
        return jdbc.inTransaction(conn -> buscarCierre(conn, periodoId, centro));
    }

    private Optional<CierrePersistido> buscarCierre(java.sql.Connection conn, UUID periodoId, String centro) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT cuenta_cif_real, cif_real, cif_aplicado, variacion, fecha_cierre, partida_id, clave_evento
                FROM cif_cierre_periodo WHERE periodo_id=? AND centro_codigo=?
                """)) {
            ps.setObject(1, periodoId);
            ps.setString(2, centro);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CierrePersistido(rs.getString("cuenta_cif_real"),
                        rs.getBigDecimal("cif_real"), rs.getBigDecimal("cif_aplicado"),
                        rs.getBigDecimal("variacion"), rs.getObject("fecha_cierre", LocalDate.class),
                        (UUID) rs.getObject("partida_id"), rs.getString("clave_evento")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo consultar el cierre CIF", e);
        }
    }

    private CedulaCif exigirCedula(UUID periodoId, String centro) {
        return buscarCedula(periodoId, centro).orElseThrow(() ->
                new DomainException("Primero registre la cédula CIF del período y centro seleccionado."));
    }

    private Optional<CedulaCif> buscarCedula(UUID periodoId, String centro) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT cif_estimado, base_estimada, cif_real, base_real
                    FROM cif_presupuesto_periodo WHERE periodo_id=? AND centro_codigo=?
                    """)) {
                ps.setObject(1, periodoId);
                ps.setString(2, centro);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new CedulaCif(rs.getBigDecimal("cif_estimado"),
                            rs.getBigDecimal("base_estimada"), rs.getBigDecimal("cif_real"),
                            rs.getBigDecimal("base_real")));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo consultar la cédula CIF", e);
            }
        });
    }

    private Optional<Partida> buscarPartidaPorEvento(String clave) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT p.id
                    FROM evento_contable e
                    JOIN partida p ON p.evento_id=e.id
                    WHERE e.clave_unica=?
                    ORDER BY CASE p.estado
                        WHEN 'CONTABILIZADA' THEN 0
                        WHEN 'APROBADA' THEN 1
                        WHEN 'BORRADOR' THEN 2
                        ELSE 3 END, p.creado_en
                    LIMIT 1
                    """)) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return partidaRepository.buscarPorId(conn, (UUID) rs.getObject(1));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo comprobar la partida CIF existente", e);
            }
        });
    }

    private Partida buscarPartida(UUID partidaId) {
        return jdbc.inTransaction(conn -> partidaRepository.buscarPorId(conn, partidaId)
                .orElseThrow(() -> new PersistenceException("No se encontró la partida CIF vinculada")));
    }

    private UUID exigirOrdenId(String codigoOrden) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM orden_servicio WHERE codigo=?")) {
                ps.setString(1, codigoOrden);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + codigoOrden);
                    }
                    return (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo consultar la orden para aplicar CIF", e);
            }
        });
    }

    private BigDecimal totalAplicado(UUID periodoId, String centro) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT COALESCE(SUM(a.importe), 0)
                    FROM cif_aplicacion_orden a
                    JOIN partida p ON p.id=a.partida_id
                    WHERE a.periodo_id=? AND a.centro_codigo=? AND p.estado='CONTABILIZADA'
                    """)) {
                ps.setObject(1, periodoId);
                ps.setString(2, centro);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo sumar el CIF aplicado del período", e);
            }
        });
    }

    /**
     * Saldo deudor mayorizado de la cuenta de CIF real para el período y centro.
     * Incluye partidas revertidas porque su contrapartida de reversa también está
     * contabilizada: ambas se anulan y el saldo refleja el mayor real.
     */
    private BigDecimal saldoCuentaCifReal(Connection conn, String cuentaReal, String centro,
                                          UUID periodoId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(d.debe - d.haber), 0)
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE d.codigo_cuenta = ? AND d.centro_costo = ?
                  AND p.periodo_id = ? AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                """)) {
            ps.setString(1, cuentaReal);
            ps.setString(2, centro);
            ps.setObject(3, periodoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo leer el saldo de la cuenta de CIF real", e);
        }
    }

    private void validarCedulaModificable(UUID periodoId, String centro,
                                          CostosCalculator.AnalisisCif propuesta) {
        Optional<CedulaCif> anterior = buscarCedula(periodoId, centro);
        if (anterior.isEmpty() || mismaCedula(anterior.get().analizar(), propuesta)) {
            return;
        }
        boolean tieneTrazabilidad = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT EXISTS (SELECT 1 FROM cif_aplicacion_orden WHERE periodo_id=? AND centro_codigo=?)
                        OR EXISTS (SELECT 1 FROM cif_cierre_periodo WHERE periodo_id=? AND centro_codigo=?)
                    """)) {
                ps.setObject(1, periodoId);
                ps.setString(2, centro);
                ps.setObject(3, periodoId);
                ps.setString(4, centro);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo validar la trazabilidad CIF", e);
            }
        });
        if (tieneTrazabilidad) {
            throw new DomainException("La cédula CIF ya tiene aplicaciones o cierre contabilizados; "
                    + "revértalos antes de modificar sus bases o importes.");
        }
    }

    private void exigirCentro(String centro) {
        boolean existe = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM centro_costo WHERE codigo=?")) {
                ps.setString(1, centro);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo validar el centro de costo", e);
            }
        });
        if (!existe) {
            throw new DomainException("Centro de costo no encontrado: " + centro);
        }
    }

    private void validarMismaAplicacion(AplicacionPersistida existente, BigDecimal baseSolicitada) {
        if (existente.baseAplicada().compareTo(baseSolicitada) != 0) {
            throw new DomainException("La orden ya tiene CIF aplicado en este centro y período con una base distinta. "
                    + "Revise o revierta la aplicación existente antes de reemplazarla.");
        }
    }

    private void validarConciliacionAplicaciones(BigDecimal esperado, BigDecimal aplicado) {
        if (esperado.subtract(aplicado).abs().compareTo(TOLERANCIA_REDONDEO) > 0) {
            throw new DomainException("El CIF aplicado a órdenes ($" + aplicado + ") no concilia con la cédula ($"
                    + esperado + "). Distribuya toda la base real antes del cierre.");
        }
    }

    private static boolean mismaCedula(CostosCalculator.AnalisisCif anterior,
                                       CostosCalculator.AnalisisCif propuesta) {
        return anterior.cifEstimados().compareTo(propuesta.cifEstimados()) == 0
                && anterior.baseEstimada().compareTo(propuesta.baseEstimada()) == 0
                && anterior.baseReal().compareTo(propuesta.baseReal()) == 0
                && anterior.cifReales().compareTo(propuesta.cifReales()) == 0;
    }

    private static CostosCalculator.EstadoCif estado(BigDecimal variacion) {
        if (variacion.signum() > 0) {
            return CostosCalculator.EstadoCif.SUBAPLICADO;
        }
        if (variacion.signum() < 0) {
            return CostosCalculator.EstadoCif.SOBREAPLICADO;
        }
        return CostosCalculator.EstadoCif.EQUILIBRADO;
    }

    private static BigDecimal tarifaParaAuxiliar(AplicacionPersistida aplicacion) {
        return aplicacion.importe().divide(aplicacion.baseAplicada(), 6, RoundingMode.HALF_UP);
    }

    private static String normalizarCentro(String centro) {
        String normalizado = centro == null || centro.isBlank() ? "GENERAL" : centro.trim();
        if (normalizado.length() > 20) {
            throw new DomainException("El código de centro de costo no puede superar 20 caracteres.");
        }
        return normalizado;
    }

    private static String normalizarCuentaCifReal(String cuenta) {
        String normalizada = textoObligatorio(cuenta, "La cuenta de CIF real");
        if (!normalizada.matches("5201\\d{2}")) {
            throw new DomainException("La cuenta de CIF real debe ser una subcuenta 5201xx.");
        }
        return normalizada;
    }

    private static String descripcion(String valor, String codigoOrden) {
        String resultado = valor == null || valor.isBlank() ? "Aplicación CIF a " + codigoOrden : valor.trim();
        if (resultado.length() > 300) {
            throw new DomainException("La descripción de la aplicación CIF no puede superar 300 caracteres.");
        }
        return resultado;
    }

    private static String textoObligatorio(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    private static BigDecimal cantidadPositiva(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() <= 0) {
            throw new DomainException(etiqueta + " debe ser mayor que cero.");
        }
        return valor.setScale(6, RoundingMode.HALF_UP);
    }

    private static String claveAplicacion(UUID periodoId, String centro, UUID ordenId) {
        return "CIF-ORD-" + periodoId + "-" + centro + "-" + ordenId;
    }

    private static String claveCierre(UUID periodoId, String centro) {
        return "CIF-CIERRE-" + periodoId + "-" + centro;
    }

    private void exigirServicioPartidas() {
        if (partidas == null) {
            throw new IllegalStateException("Esta instancia de CifService sólo permite registrar la cédula; "
                    + "inyecte PartidaService para aplicar o cerrar CIF.");
        }
    }
}
