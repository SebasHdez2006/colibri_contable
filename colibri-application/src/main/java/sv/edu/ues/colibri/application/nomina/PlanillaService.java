package sv.edu.ues.colibri.application.nomina;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;
import sv.edu.ues.colibri.persistence.repo.PeriodoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Devenga la planilla agregada de un período y deja documentadas las tasas que
 * se usaron. Las tasas laborales se reciben explícitamente porque dependen de
 * topes, exclusiones y normativa vigente; las patronales se obtienen de la
 * parametrización vigente y quedan guardadas junto con la planilla.
 */
public final class PlanillaService {

    public static final BigDecimal TASA_ISSS_PATRONAL = new BigDecimal("0.075");
    public static final BigDecimal TASA_PREVISIONAL_PATRONAL = new BigDecimal("0.0875");
    public static final BigDecimal TASA_INCAF = new BigDecimal("0.01");
    public static final int UMBRAL_INCAF_EMPLEADOS = 10;

    private static final String CUENTA_GASTO_PLANILLA = "610101";
    private static final String CUENTA_SALARIO_NETO = "210301";
    private static final String CUENTA_ISSS_LABORAL = "210401";
    private static final String CUENTA_ISSS_PATRONAL = "210402";
    private static final String CUENTA_PREVISION_LABORAL = "210501";
    private static final String CUENTA_PREVISION_PATRONAL = "210502";
    private static final String CUENTA_VACACIONES = "210601";
    private static final String CUENTA_AGUINALDO = "210602";
    private static final String CUENTA_OTRAS_PRESTACIONES = "210603";
    private static final String CUENTA_ISR_TRABAJADORES = "210205";
    private static final String CUENTA_OTRAS_DEDUCCIONES = "210804";
    private static final String CUENTA_INCAF = "210801";

    private static final String PARAM_TASA_ISSS_PATRONAL = "rrhh.tasa.isss.patronal";
    private static final String PARAM_TASA_PREVISIONAL_PATRONAL = "rrhh.tasa.previsional.patronal";
    private static final String PARAM_TASA_INCAF = "rrhh.tasa.incaf";
    private static final String PARAM_UMBRAL_INCAF = "rrhh.umbral.incaf.empleados";
    private static final String PARAM_TASA_ISSS_LABORAL = "rrhh.tasa.isss.laboral";
    private static final String PARAM_TASA_PREVISIONAL_LABORAL = "rrhh.tasa.previsional.laboral";
    private static final BigDecimal TASA_ISSS_LABORAL_RESPALDO = new BigDecimal("0.03");
    private static final BigDecimal TASA_PREVISIONAL_LABORAL_RESPALDO = new BigDecimal("0.0725");

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final PeriodoAppService periodos;
    private final PartidaRepository partidaRepository = new PartidaRepository();
    private final PeriodoRepository periodoRepository = new PeriodoRepository();

    /**
     * Crea el servicio de planillas.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     * @param partidas servicio de partidas
     * @param periodos servicio de períodos
     */
    public PlanillaService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
                           PeriodoAppService periodos) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.periodos = periodos;
    }

    /**
     * Parámetros de la planilla agregada. Las retenciones laborales se
     * proporcionan por período, para no codificar topes legales ni tablas de
     * renta que cambian con el tiempo.
     */
    public record Solicitud(LocalDate fechaContable, int empleados, BigDecimal salarioNominal,
                            BigDecimal tasaIsssLaboral, BigDecimal tasaPrevisionalLaboral,
                            BigDecimal isrRetenido, BigDecimal otrasDeducciones,
                            BigDecimal otrasPrestaciones, String centroCosto) {
    }

    /** Resultado reproducible del cálculo antes o después de contabilizarlo. */
    public record Resultado(BigDecimal salarioNominal, BigDecimal isssLaboral,
                            BigDecimal isssPatronal, BigDecimal previsionalLaboral,
                            BigDecimal previsionalPatronal, BigDecimal incaf,
                            BigDecimal aguinaldoProvisionado,
                            BigDecimal vacacionesProvisionadas,
                            BigDecimal otrasPrestaciones, BigDecimal isrRetenido,
                            BigDecimal otrasDeducciones, BigDecimal liquidoPorPagar,
                            BigDecimal costoTotalEmpresa) {
        public BigDecimal deduccionesLaborales() {
            return isssLaboral.add(previsionalLaboral).add(isrRetenido).add(otrasDeducciones);
        }

        public BigDecimal cargasPatronales() {
            return isssPatronal.add(previsionalPatronal).add(incaf);
        }

        public BigDecimal prestacionesProvisionadas() {
            return aguinaldoProvisionado.add(vacacionesProvisionadas).add(otrasPrestaciones);
        }
    }

    /** La partida contabilizada junto con los importes persistidos para auditoría. */
    public record PlanillaRegistrada(Partida partida, Resultado resultado) {
    }

    /** Tasas efectivas que se aplican y se preservan en la cédula de planilla. */
    private record PoliticaPatronal(BigDecimal tasaIsss, BigDecimal tasaPrevisional,
                                    BigDecimal tasaIncaf, int umbralIncaf) {
    }

    /** Tasas laborales vigentes en {@code parametro_sistema}; se muestran sin edición en la UI. */
    public record TasasLaborales(BigDecimal isss, BigDecimal previsional) {
    }

    /** Fotografía inmutable de la cédula que sustenta el asiento de planilla. */
    private record PlanillaSnapshot(UUID id, UUID periodoId, LocalDate fechaContable, int empleados,
                                    BigDecimal salarioNominal, BigDecimal tasaIsssLaboral,
                                    BigDecimal tasaPrevisionalLaboral, BigDecimal isrRetenido,
                                    BigDecimal otrasDeducciones, BigDecimal otrasPrestaciones,
                                    PoliticaPatronal politica, Resultado resultado,
                                     String centroCosto, String claveEvento, UUID partidaId) {
    }

    /**
     * Construye el asiento que se contabilizaría, sin consultar ni modificar la
     * base de datos. Sirve para que la UI pueda mostrar la conciliación Debe /
     * Haber antes de confirmar el devengo.
     */
    /**
     * Previsualiza el asiento de planilla con tasas de referencia.
     *
     * @param solicitud datos de cálculo
     * @return líneas del asiento estimado
     */
    public static List<PartidaDetalle> previsualizarAsiento(Solicitud solicitud) {
        Resultado resultado = calcular(solicitud);
        return List.copyOf(detalles(resultado, centroCosto(solicitud.centroCosto())));
    }

    /**
     * Previsualización operativa: usa la parametrización vigente, igual que el
     * registro definitivo. La variante estática conserva el caso didáctico
     * puro y sus tasas de referencia para cálculos sin infraestructura.
     */
    /**
     * Previsualiza el asiento con la parametrización vigente del sistema.
     *
     * @param solicitud datos de cálculo
     * @return líneas del asiento estimado
     */
    public List<PartidaDetalle> previsualizarAsientoConfigurado(Solicitud solicitud) {
        Resultado resultado = calcularConfigurado(solicitud);
        return List.copyOf(detalles(resultado, centroCosto(solicitud.centroCosto())));
    }

    /**
     * Calcula el costo laboral sin modificar datos. El aguinaldo se provisiona
     * mensualmente como quince días de salario entre doce meses; las vacaciones
     * como el recargo legal del 30 % sobre quince días, también mensualizado.
     */
    /**
     * Calcula la planilla con la política de referencia embebida.
     *
     * @param solicitud datos de cálculo
     * @return resultado de la planilla
     */
    public static Resultado calcular(Solicitud solicitud) {
        return calcularConPolitica(solicitud, politicaCaso());
    }

    /** Calcula con los parámetros persistidos que regirán la contabilización. */
    /**
     * Calcula la planilla con la parametrización vigente.
     *
     * @param solicitud datos de cálculo
     * @return resultado de la planilla
     */
    public Resultado calcularConfigurado(Solicitud solicitud) {
        auth.exigirContador();
        return calcularConPolitica(conTasasLaboralesVigentes(solicitud), politicaPatronalVigente());
    }

    /**
     * Tasas laborales vigentes para mostrar en la interfaz. Se leen de
     * {@code parametro_sistema} para que la parametrización sea la única fuente
     * editable; la vista las presenta de sólo lectura.
     */
    public TasasLaborales tasasLaboralesVigentes() {
        auth.exigirContador();
        Map<String, String> valores = leerValoresParametro(
                List.of(PARAM_TASA_ISSS_LABORAL, PARAM_TASA_PREVISIONAL_LABORAL));
        BigDecimal isss = tasaParametrizada(valores, PARAM_TASA_ISSS_LABORAL,
                TASA_ISSS_LABORAL_RESPALDO, "ISSS laboral");
        BigDecimal previsional = tasaParametrizada(valores, PARAM_TASA_PREVISIONAL_LABORAL,
                TASA_PREVISIONAL_LABORAL_RESPALDO, "cotización previsional laboral");
        validarTasa(isss, "ISSS laboral");
        validarTasa(previsional, "cotización previsional laboral");
        return new TasasLaborales(isss, previsional);
    }

    /** Sustituye las tasas laborales de la solicitud por las configuradas. */
    private Solicitud conTasasLaboralesVigentes(Solicitud solicitud) {
        TasasLaborales tasas = tasasLaboralesVigentes();
        return new Solicitud(solicitud.fechaContable(), solicitud.empleados(),
                solicitud.salarioNominal(), tasas.isss(), tasas.previsional(),
                solicitud.isrRetenido(), solicitud.otrasDeducciones(),
                solicitud.otrasPrestaciones(), solicitud.centroCosto());
    }

    private Map<String, String> leerValoresParametro(List<String> claves) {
        return jdbc.inTransaction(conn -> {
            String marcadores = String.join(", ", java.util.Collections.nCopies(claves.size(), "?"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT clave, valor FROM parametro_sistema WHERE clave IN (" + marcadores + ")")) {
                for (int i = 0; i < claves.size(); i++) {
                    ps.setString(i + 1, claves.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, String> resultado = new HashMap<>();
                    while (rs.next()) {
                        resultado.put(rs.getString("clave"), rs.getString("valor"));
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron leer los parámetros de planilla", e);
            }
        });
    }

    static Resultado calcularConPolitica(Solicitud solicitud, BigDecimal tasaIsssPatronal,
                                         BigDecimal tasaPrevisionalPatronal, BigDecimal tasaIncaf,
                                         int umbralIncaf) {
        return calcularConPolitica(solicitud, new PoliticaPatronal(tasaIsssPatronal,
                tasaPrevisionalPatronal, tasaIncaf, umbralIncaf));
    }

    private static Resultado calcularConPolitica(Solicitud solicitud, PoliticaPatronal politica) {
        validarSolicitud(solicitud);
        validarPolitica(politica);
        BigDecimal salario = dinero(solicitud.salarioNominal());
        BigDecimal isssLaboral = porcentaje(salario, solicitud.tasaIsssLaboral());
        BigDecimal previsionalLaboral = porcentaje(salario, solicitud.tasaPrevisionalLaboral());
        BigDecimal isssPatronal = porcentaje(salario, politica.tasaIsss());
        BigDecimal previsionalPatronal = porcentaje(salario, politica.tasaPrevisional());
        BigDecimal incaf = solicitud.empleados() >= politica.umbralIncaf()
                ? porcentaje(salario, politica.tasaIncaf()) : BigDecimal.ZERO.setScale(2);

        // 15 días / 30 días / 12 meses = 1/24 del salario mensual.
        BigDecimal aguinaldo = salario.divide(new BigDecimal("24"), 2, RoundingMode.HALF_UP);
        // (15 / 30) * 30 % / 12 = 1.25 % del salario mensual.
        BigDecimal vacaciones = porcentaje(salario, new BigDecimal("0.0125"));
        BigDecimal isr = dinero(solicitud.isrRetenido());
        BigDecimal otrasDeducciones = dinero(solicitud.otrasDeducciones());
        BigDecimal otrasPrestaciones = dinero(solicitud.otrasPrestaciones());
        BigDecimal deducciones = isssLaboral.add(previsionalLaboral).add(isr).add(otrasDeducciones);
        if (deducciones.compareTo(salario) > 0) {
            throw new DomainException("Las deducciones laborales no pueden superar el salario nominal.");
        }
        BigDecimal liquido = salario.subtract(deducciones).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoTotal = salario.add(isssPatronal).add(previsionalPatronal).add(incaf)
                .add(aguinaldo).add(vacaciones).add(otrasPrestaciones)
                .setScale(2, RoundingMode.HALF_UP);
        return new Resultado(salario, isssLaboral, isssPatronal, previsionalLaboral,
                previsionalPatronal, incaf, aguinaldo, vacaciones, otrasPrestaciones,
                isr, otrasDeducciones, liquido, costoTotal);
    }

    /**
     * Genera una sola partida AJ por período. La restricción de base de datos
     * impide que la misma planilla vuelva a devengarse por error.
     */
    /**
     * Registra o recupera la planilla del período correspondiente.
     *
     * @param solicitud datos de cálculo
     * @return planilla registrada junto con su resultado
     */
    public PlanillaRegistrada registrar(Solicitud solicitud) {
        auth.exigirContador();
        Solicitud efectiva = conTasasLaboralesVigentes(solicitud);
        validarSolicitud(efectiva);
        // Una cédula previa puede requerir únicamente el enlace de una partida
        // ya contabilizada. Esa recuperación no debe quedar bloqueada porque el
        // período se cerró después de que se publicó el asiento.
        Optional<PlanillaSnapshot> porFecha = buscarSnapshotPorFecha(efectiva.fechaContable());
        if (porFecha.isPresent()) {
            PlanillaSnapshot snapshot = porFecha.get();
            validarMismaSolicitud(snapshot, efectiva);
            return recuperarSnapshot(snapshot, buscarPeriodo(snapshot.periodoId()));
        }

        Periodo periodo = periodos.exigirPeriodoAbiertoParaFecha(efectiva.fechaContable());
        String claveEvento = "PLANILLA-" + periodo.getId();

        Optional<PlanillaSnapshot> previa = buscarSnapshot(periodo.getId());
        if (previa.isPresent()) {
            PlanillaSnapshot snapshot = previa.get();
            validarMismaSolicitud(snapshot, efectiva);
            return recuperarSnapshot(snapshot, periodo);
        }

        PoliticaPatronal politica = politicaPatronalVigente();
        Resultado resultado = calcularConPolitica(efectiva, politica);
        PlanillaSnapshot snapshot = crearSnapshot(periodo, efectiva, resultado, politica, claveEvento);
        return recuperarSnapshot(snapshot, periodo);
    }

    private PlanillaRegistrada recuperarSnapshot(PlanillaSnapshot snapshot, Periodo periodo) {
        Partida partida = asegurarPartidaSnapshot(snapshot, periodo);
        vincularPartida(snapshot, partida);
        return new PlanillaRegistrada(partida, snapshot.resultado());
    }

    private static List<PartidaDetalle> detalles(Resultado resultado, String centroCosto) {
        List<PartidaDetalle> lineas = new ArrayList<>();
        lineas.add(PartidaDetalle.nueva(CUENTA_GASTO_PLANILLA, null, centroCosto,
                resultado.costoTotalEmpresa(), BigDecimal.ZERO));
        agregarAbono(lineas, CUENTA_ISSS_LABORAL, resultado.isssLaboral());
        agregarAbono(lineas, CUENTA_ISSS_PATRONAL, resultado.isssPatronal());
        agregarAbono(lineas, CUENTA_PREVISION_LABORAL, resultado.previsionalLaboral());
        agregarAbono(lineas, CUENTA_PREVISION_PATRONAL, resultado.previsionalPatronal());
        agregarAbono(lineas, CUENTA_INCAF, resultado.incaf());
        agregarAbono(lineas, CUENTA_AGUINALDO, resultado.aguinaldoProvisionado());
        agregarAbono(lineas, CUENTA_VACACIONES, resultado.vacacionesProvisionadas());
        agregarAbono(lineas, CUENTA_OTRAS_PRESTACIONES, resultado.otrasPrestaciones());
        agregarAbono(lineas, CUENTA_ISR_TRABAJADORES, resultado.isrRetenido());
        agregarAbono(lineas, CUENTA_OTRAS_DEDUCCIONES, resultado.otrasDeducciones());
        agregarAbono(lineas, CUENTA_SALARIO_NETO, resultado.liquidoPorPagar());
        return lineas;
    }

    private static void agregarAbono(List<PartidaDetalle> lineas, String cuenta, BigDecimal importe) {
        if (importe.signum() > 0) {
            lineas.add(PartidaDetalle.nueva(cuenta, BigDecimal.ZERO, importe));
        }
    }

    private static PoliticaPatronal politicaCaso() {
        return new PoliticaPatronal(TASA_ISSS_PATRONAL, TASA_PREVISIONAL_PATRONAL,
                TASA_INCAF, UMBRAL_INCAF_EMPLEADOS);
    }

    /** Lee una única fotografía de las políticas que se aplicará al devengo. */
    private PoliticaPatronal politicaPatronalVigente() {
        Map<String, String> valores = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT clave, valor FROM parametro_sistema
                    WHERE clave IN (?, ?, ?, ?)
                    """)) {
                ps.setString(1, PARAM_TASA_ISSS_PATRONAL);
                ps.setString(2, PARAM_TASA_PREVISIONAL_PATRONAL);
                ps.setString(3, PARAM_TASA_INCAF);
                ps.setString(4, PARAM_UMBRAL_INCAF);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, String> resultado = new HashMap<>();
                    while (rs.next()) {
                        resultado.put(rs.getString("clave"), rs.getString("valor"));
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron leer las políticas patronales vigentes", e);
            }
        });
        PoliticaPatronal politica = new PoliticaPatronal(
                tasaParametrizada(valores, PARAM_TASA_ISSS_PATRONAL, TASA_ISSS_PATRONAL,
                        "ISSS patronal"),
                tasaParametrizada(valores, PARAM_TASA_PREVISIONAL_PATRONAL, TASA_PREVISIONAL_PATRONAL,
                        "cotización previsional patronal"),
                tasaParametrizada(valores, PARAM_TASA_INCAF, TASA_INCAF, "INCAF"),
                enteroParametrizado(valores, PARAM_UMBRAL_INCAF, UMBRAL_INCAF_EMPLEADOS,
                        "umbral de empleados para INCAF"));
        validarPolitica(politica);
        return politica;
    }

    private static BigDecimal tasaParametrizada(Map<String, String> valores, String clave,
                                                BigDecimal respaldo, String etiqueta) {
        String valor = valores.get(clave);
        if (valor == null || valor.isBlank()) {
            return respaldo;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            throw new DomainException("El parámetro " + etiqueta + " (" + clave + ") no es numérico.", e);
        }
    }

    private static int enteroParametrizado(Map<String, String> valores, String clave, int respaldo,
                                            String etiqueta) {
        String valor = valores.get(clave);
        if (valor == null || valor.isBlank()) {
            return respaldo;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new DomainException("El parámetro " + etiqueta + " (" + clave + ") debe ser entero.", e);
        }
    }

    private static void validarPolitica(PoliticaPatronal politica) {
        if (politica == null) {
            throw new DomainException("Las políticas patronales son obligatorias.");
        }
        validarTasa(politica.tasaIsss(), "ISSS patronal");
        validarTasa(politica.tasaPrevisional(), "cotización previsional patronal");
        validarTasa(politica.tasaIncaf(), "INCAF");
        if (politica.umbralIncaf() <= 0) {
            throw new DomainException("El umbral de empleados para INCAF debe ser mayor que cero.");
        }
    }

    private Optional<PlanillaSnapshot> buscarSnapshot(UUID periodoId) {
        return jdbc.inTransaction(conn -> buscarSnapshot(conn, periodoId));
    }

    private Optional<PlanillaSnapshot> buscarSnapshotPorFecha(LocalDate fechaContable) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT periodo_id FROM planilla_periodo WHERE fecha_contable=?
                    """)) {
                ps.setObject(1, fechaContable);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return buscarSnapshot(conn, (UUID) rs.getObject(1));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo localizar la cédula de planilla por fecha", e);
            }
        });
    }

    private Periodo buscarPeriodo(UUID periodoId) {
        return jdbc.inTransaction(conn -> periodoRepository.buscarPorId(conn, periodoId)
                .orElseThrow(() -> new PersistenceException("No se encontró el período de la cédula de planilla")));
    }

    private Optional<PlanillaSnapshot> buscarSnapshot(java.sql.Connection conn, UUID periodoId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, periodo_id, fecha_contable, empleados, salario_nominal,
                       tasa_isss_laboral, tasa_previsional_laboral, tasa_isss_patronal,
                       tasa_previsional_patronal, tasa_incaf, umbral_incaf_empleados,
                       isr_retenido, otras_deducciones, otras_prestaciones,
                       isss_laboral, isss_patronal, previsional_laboral, previsional_patronal, incaf,
                       aguinaldo_provisionado, vacaciones_provisionadas, liquido_por_pagar,
                       costo_patronal_total, centro_costo, clave_evento, partida_id
                FROM planilla_periodo WHERE periodo_id=?
                """)) {
            ps.setObject(1, periodoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Resultado resultado = new Resultado(
                        rs.getBigDecimal("salario_nominal"), rs.getBigDecimal("isss_laboral"),
                        rs.getBigDecimal("isss_patronal"), rs.getBigDecimal("previsional_laboral"),
                        rs.getBigDecimal("previsional_patronal"), rs.getBigDecimal("incaf"),
                        rs.getBigDecimal("aguinaldo_provisionado"), rs.getBigDecimal("vacaciones_provisionadas"),
                        rs.getBigDecimal("otras_prestaciones"), rs.getBigDecimal("isr_retenido"),
                        rs.getBigDecimal("otras_deducciones"), rs.getBigDecimal("liquido_por_pagar"),
                        rs.getBigDecimal("costo_patronal_total"));
                PoliticaPatronal politica = new PoliticaPatronal(rs.getBigDecimal("tasa_isss_patronal"),
                        rs.getBigDecimal("tasa_previsional_patronal"), rs.getBigDecimal("tasa_incaf"),
                        rs.getInt("umbral_incaf_empleados"));
                return Optional.of(new PlanillaSnapshot((UUID) rs.getObject("id"),
                        (UUID) rs.getObject("periodo_id"), rs.getObject("fecha_contable", LocalDate.class),
                        rs.getInt("empleados"), rs.getBigDecimal("salario_nominal"),
                        rs.getBigDecimal("tasa_isss_laboral"), rs.getBigDecimal("tasa_previsional_laboral"),
                        rs.getBigDecimal("isr_retenido"), rs.getBigDecimal("otras_deducciones"),
                        rs.getBigDecimal("otras_prestaciones"), politica, resultado,
                        rs.getString("centro_costo"), rs.getString("clave_evento"),
                        (UUID) rs.getObject("partida_id")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo recuperar la cédula de planilla", e);
        }
    }

    private PlanillaSnapshot crearSnapshot(Periodo periodo, Solicitud solicitud, Resultado resultado,
                                           PoliticaPatronal politica, String claveEvento) {
        String centro = centroCosto(solicitud.centroCosto());
        PlanillaSnapshot nueva = new PlanillaSnapshot(UUID.randomUUID(), periodo.getId(), solicitud.fechaContable(),
                solicitud.empleados(), resultado.salarioNominal(), solicitud.tasaIsssLaboral(),
                solicitud.tasaPrevisionalLaboral(), resultado.isrRetenido(), resultado.otrasDeducciones(),
                resultado.otrasPrestaciones(), politica, resultado, centro, claveEvento, null);
        return jdbc.inTransaction(conn -> {
            Optional<PlanillaSnapshot> existente = buscarSnapshot(conn, periodo.getId());
            if (existente.isPresent()) {
                validarMismaSolicitud(existente.get(), solicitud);
                return existente.get();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO planilla_periodo (
                        id, periodo_id, fecha_contable, empleados, salario_nominal,
                        tasa_isss_laboral, tasa_previsional_laboral, tasa_isss_patronal,
                        tasa_previsional_patronal, tasa_incaf, umbral_incaf_empleados,
                        isr_retenido, otras_deducciones,
                        isss_laboral, isss_patronal, previsional_laboral, previsional_patronal, incaf,
                        aguinaldo_provisionado, vacaciones_provisionadas, otras_prestaciones,
                        liquido_por_pagar, costo_patronal_total, centro_costo, clave_evento, partida_id
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
                    ON CONFLICT (periodo_id) DO NOTHING
                    """)) {
                ps.setObject(1, nueva.id());
                ps.setObject(2, nueva.periodoId());
                ps.setObject(3, nueva.fechaContable());
                ps.setInt(4, nueva.empleados());
                ps.setBigDecimal(5, nueva.resultado().salarioNominal());
                ps.setBigDecimal(6, nueva.tasaIsssLaboral());
                ps.setBigDecimal(7, nueva.tasaPrevisionalLaboral());
                ps.setBigDecimal(8, nueva.politica().tasaIsss());
                ps.setBigDecimal(9, nueva.politica().tasaPrevisional());
                ps.setBigDecimal(10, nueva.politica().tasaIncaf());
                ps.setInt(11, nueva.politica().umbralIncaf());
                ps.setBigDecimal(12, nueva.resultado().isrRetenido());
                ps.setBigDecimal(13, nueva.resultado().otrasDeducciones());
                ps.setBigDecimal(14, nueva.resultado().isssLaboral());
                ps.setBigDecimal(15, nueva.resultado().isssPatronal());
                ps.setBigDecimal(16, nueva.resultado().previsionalLaboral());
                ps.setBigDecimal(17, nueva.resultado().previsionalPatronal());
                ps.setBigDecimal(18, nueva.resultado().incaf());
                ps.setBigDecimal(19, nueva.resultado().aguinaldoProvisionado());
                ps.setBigDecimal(20, nueva.resultado().vacacionesProvisionadas());
                ps.setBigDecimal(21, nueva.resultado().otrasPrestaciones());
                ps.setBigDecimal(22, nueva.resultado().liquidoPorPagar());
                ps.setBigDecimal(23, nueva.resultado().costoTotalEmpresa());
                ps.setString(24, nueva.centroCosto());
                ps.setString(25, nueva.claveEvento());
                if (ps.executeUpdate() == 1) {
                    return nueva;
                }
                PlanillaSnapshot concurrente = buscarSnapshot(conn, periodo.getId()).orElseThrow(() ->
                        new PersistenceException("No se pudo recuperar la cédula de planilla concurrente"));
                validarMismaSolicitud(concurrente, solicitud);
                return concurrente;
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo fotografiar la cédula de planilla", e);
            }
        });
    }

    private void validarMismaSolicitud(PlanillaSnapshot snapshot, Solicitud solicitud) {
        validarSolicitud(solicitud);
        boolean coincide = snapshot.fechaContable().equals(solicitud.fechaContable())
                && snapshot.empleados() == solicitud.empleados()
                && snapshot.salarioNominal().compareTo(dinero(solicitud.salarioNominal())) == 0
                && snapshot.tasaIsssLaboral().compareTo(solicitud.tasaIsssLaboral()) == 0
                && snapshot.tasaPrevisionalLaboral().compareTo(solicitud.tasaPrevisionalLaboral()) == 0
                && snapshot.isrRetenido().compareTo(dinero(solicitud.isrRetenido())) == 0
                && snapshot.otrasDeducciones().compareTo(dinero(solicitud.otrasDeducciones())) == 0
                && snapshot.otrasPrestaciones().compareTo(dinero(solicitud.otrasPrestaciones())) == 0
                && snapshot.centroCosto().equals(centroCosto(solicitud.centroCosto()));
        if (!coincide) {
            throw new DomainException("Ya existe una cédula de planilla pendiente o contabilizada para este período; "
                    + "no puede sustituirse con datos distintos.");
        }
    }

    private Partida asegurarPartidaSnapshot(PlanillaSnapshot snapshot, Periodo periodo) {
        if (!periodo.getId().equals(snapshot.periodoId())) {
            throw new DomainException("La cédula de planilla no pertenece al período seleccionado.");
        }
        if (snapshot.partidaId() != null) {
            Partida vinculada = partidas.buscar(snapshot.partidaId()).orElseThrow(() ->
                    new PersistenceException("No se encontró la partida vinculada a la planilla"));
            return contabilizarSiPendiente(vinculada);
        }
        Optional<Partida> existente = buscarPartidaPorEvento(snapshot.claveEvento());
        if (existente.isPresent()) {
            return contabilizarSiPendiente(existente.get());
        }
        try {
            Partida borrador = partidas.guardarBorrador(TipoPartida.AJ, snapshot.fechaContable(),
                    "Devengo de planilla y cargas patronales", "Planilla " + periodo.getMes(),
                    snapshot.claveEvento(), detalles(snapshot.resultado(), snapshot.centroCosto()), null);
            return partidas.contabilizar(borrador.getId());
        } catch (DomainException | PersistenceException ex) {
            Optional<Partida> concurrente = buscarPartidaPorEvento(snapshot.claveEvento());
            if (concurrente.isPresent()) {
                return contabilizarSiPendiente(concurrente.get());
            }
            throw ex;
        }
    }

    private Optional<Partida> buscarPartidaPorEvento(String claveEvento) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT p.id FROM evento_contable e
                    JOIN partida p ON p.evento_id=e.id
                    WHERE e.clave_unica=?
                    ORDER BY CASE p.estado
                        WHEN 'CONTABILIZADA' THEN 0
                        WHEN 'APROBADA' THEN 1
                        WHEN 'BORRADOR' THEN 2
                        ELSE 3 END, p.creado_en
                    LIMIT 1
                    """)) {
                ps.setString(1, claveEvento);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return partidaRepository.buscarPorId(conn, (UUID) rs.getObject(1));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo comprobar la partida de planilla existente", e);
            }
        });
    }

    private Partida contabilizarSiPendiente(Partida partida) {
        if (partida.getEstado() == EstadoPartida.CONTABILIZADA) {
            return partida;
        }
        if (partida.getEstado() == EstadoPartida.REVERTIDA || partida.getEstado() == EstadoPartida.ANULADA) {
            throw new DomainException("La partida de planilla existente fue "
                    + partida.getEstado().name().toLowerCase() + "; registre una corrección nueva.");
        }
        return partidas.contabilizar(partida.getId());
    }

    private void vincularPartida(PlanillaSnapshot snapshot, Partida partida) {
        if (snapshot.partidaId() != null && !snapshot.partidaId().equals(partida.getId())) {
            throw new DomainException("La cédula de planilla ya está vinculada a otra partida.");
        }
        if (snapshot.partidaId() != null) {
            return;
        }
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE planilla_periodo SET partida_id=? WHERE id=? AND partida_id IS NULL
                    """)) {
                ps.setObject(1, partida.getId());
                ps.setObject(2, snapshot.id());
                if (ps.executeUpdate() == 0) {
                    PlanillaSnapshot actual = buscarSnapshot(conn, snapshot.periodoId()).orElseThrow(() ->
                            new PersistenceException("La cédula de planilla desapareció durante su recuperación"));
                    if (!partida.getId().equals(actual.partidaId())) {
                        throw new DomainException("La cédula de planilla ya está vinculada a otra partida.");
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo vincular la cédula de planilla con su partida", e);
            }
        });
    }

    private static String centroCosto(String centroCosto) {
        return centroCosto == null || centroCosto.isBlank() ? "CC-01" : centroCosto.trim();
    }

    private static BigDecimal porcentaje(BigDecimal base, BigDecimal tasa) {
        return base.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinero(BigDecimal importe) {
        if (importe == null || importe.signum() < 0) {
            throw new DomainException("Los importes de la planilla no pueden ser negativos.");
        }
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarSolicitud(Solicitud solicitud) {
        if (solicitud == null || solicitud.fechaContable() == null) {
            throw new DomainException("La fecha contable de la planilla es obligatoria.");
        }
        if (solicitud.empleados() <= 0) {
            throw new DomainException("La planilla debe indicar al menos un empleado.");
        }
        dinero(solicitud.salarioNominal());
        if (solicitud.salarioNominal().signum() <= 0) {
            throw new DomainException("El salario nominal debe ser mayor que cero.");
        }
        validarTasa(solicitud.tasaIsssLaboral(), "ISSS laboral");
        validarTasa(solicitud.tasaPrevisionalLaboral(), "cotización previsional laboral");
        dinero(solicitud.isrRetenido());
        dinero(solicitud.otrasDeducciones());
        dinero(solicitud.otrasPrestaciones());
    }

    private static void validarTasa(BigDecimal tasa, String etiqueta) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainException("La tasa de " + etiqueta + " debe estar entre cero y uno.");
        }
    }
}
