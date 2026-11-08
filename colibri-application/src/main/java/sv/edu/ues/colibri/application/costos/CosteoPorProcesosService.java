package sv.edu.ues.colibri.application.costos;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.calculation.CostosCalculator;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Costeo por procesos con cédula física y unidades equivalentes por elemento.
 * Los importes de entrada se conservan por período; los resultados se calculan
 * desde esa evidencia para que sean reproducibles con PEPS, promedio y UEPS.
 */
public final class CosteoPorProcesosService {

    public record ProcesoEntrada(String codigoProceso, LocalDate fecha,
                                 BigDecimal unidadesIniciales,
                                 BigDecimal avanceInicialMaterial,
                                 BigDecimal avanceInicialConversion,
                                 BigDecimal costoInicialMaterial,
                                 BigDecimal costoInicialConversion,
                                 BigDecimal unidadesIniciadas,
                                 BigDecimal unidadesTerminadas,
                                 BigDecimal unidadesFinales,
                                 BigDecimal avanceFinalMaterial,
                                 BigDecimal avanceFinalConversion,
                                 BigDecimal costoPeriodoMaterial,
                                 BigDecimal costoPeriodoConversion) {
    }

    public record ElementoProceso(CostosCalculator.UnidadesEquivalentes unidadesEquivalentes,
                                  CostosCalculator.AsignacionProceso peps,
                                  CostosCalculator.AsignacionProceso promedio,
                                  CostosCalculator.AsignacionProceso ueps) {
    }

    public record ResultadoProceso(CostosCalculator.BalanceFisico balanceFisico,
                                   ElementoProceso material,
                                   ElementoProceso conversion,
                                   BigDecimal costoInventarioFinalPeps,
                                   BigDecimal costoTransferidoPeps,
                                   BigDecimal costoInventarioFinalPromedio,
                                   BigDecimal costoTransferidoPromedio) {
    }

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PeriodoAppService periodos;

    public CosteoPorProcesosService(JdbcSupport jdbc, AuthService auth, PeriodoAppService periodos) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.periodos = periodos;
    }

    /** Persiste la cédula para su período y devuelve las asignaciones calculadas. */
    public ResultadoProceso guardar(ProcesoEntrada entrada) {
        auth.exigirContador();
        validarEntrada(entrada);
        Periodo periodo = periodos.exigirPeriodoAbiertoParaFecha(entrada.fecha());
        ResultadoProceso resultado = calcular(entrada);
        jdbc.runInTransaction(conn -> guardar(conn, periodo.getId(), entrada));
        return resultado;
    }

    public Optional<ProcesoEntrada> buscar(String codigoProceso, LocalDate fecha) {
        auth.exigirContador();
        String codigo = codigo(codigoProceso);
        if (fecha == null) {
            throw new DomainException("La fecha del proceso es obligatoria.");
        }
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT unidades_iniciales, avance_inicial_material, avance_inicial_conversion,
                           costo_inicial_material, costo_inicial_conversion, unidades_iniciadas,
                           unidades_terminadas, unidades_finales, avance_final_material,
                           avance_final_conversion, costo_periodo_material, costo_periodo_conversion
                    FROM proceso_costo_periodo cp
                    JOIN periodo p ON p.id = cp.periodo_id
                    WHERE cp.codigo_proceso=?
                      AND p.fecha_inicio <= ? AND p.fecha_fin >= ?
                    """)) {
                ps.setString(1, codigo);
                ps.setObject(2, fecha);
                ps.setObject(3, fecha);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new ProcesoEntrada(codigo, fecha,
                            rs.getBigDecimal("unidades_iniciales"),
                            rs.getBigDecimal("avance_inicial_material"),
                            rs.getBigDecimal("avance_inicial_conversion"),
                            rs.getBigDecimal("costo_inicial_material"),
                            rs.getBigDecimal("costo_inicial_conversion"),
                            rs.getBigDecimal("unidades_iniciadas"),
                            rs.getBigDecimal("unidades_terminadas"),
                            rs.getBigDecimal("unidades_finales"),
                            rs.getBigDecimal("avance_final_material"),
                            rs.getBigDecimal("avance_final_conversion"),
                            rs.getBigDecimal("costo_periodo_material"),
                            rs.getBigDecimal("costo_periodo_conversion")));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo consultar el proceso de costos", e);
            }
        });
    }

    /** Calcula los tres métodos sin consultar ni modificar la base de datos. */
    public static ResultadoProceso calcular(ProcesoEntrada entrada) {
        validarEntrada(entrada);
        try {
            CostosCalculator.BalanceFisico balance = CostosCalculator.balanceFisico(
                    entrada.unidadesIniciales(), entrada.unidadesIniciadas(), entrada.unidadesFinales());
            if (balance.unidadesTransferidas().compareTo(entrada.unidadesTerminadas()) != 0) {
                throw new DomainException("Las unidades terminadas deben coincidir con la cédula física: "
                        + balance.unidadesTransferidas().stripTrailingZeros().toPlainString() + ".");
            }
            // La cédula PEPS de esta versión asume que se terminaron todas las
            // unidades que venían en proceso. Sin ese dato de trazabilidad, un
            // saldo final que aún incluya WIP inicial requeriría separar las
            // unidades iniciales y las iniciadas durante el período.
            if (entrada.unidadesTerminadas().compareTo(entrada.unidadesIniciales()) < 0) {
                throw new DomainException("El costeo PEPS simplificado requiere que las unidades terminadas "
                        + "incluyan todo el inventario inicial en proceso.");
            }
            BigDecimal iniciadasYTerminadas = balance.unidadesTransferidas()
                    .subtract(entrada.unidadesIniciales());
            ElementoProceso material = calcularElemento(balance, entrada.unidadesIniciales(),
                    entrada.avanceInicialMaterial(), entrada.unidadesFinales(), entrada.avanceFinalMaterial(),
                    iniciadasYTerminadas, entrada.costoInicialMaterial(), entrada.costoPeriodoMaterial());
            ElementoProceso conversion = calcularElemento(balance, entrada.unidadesIniciales(),
                    entrada.avanceInicialConversion(), entrada.unidadesFinales(), entrada.avanceFinalConversion(),
                    iniciadasYTerminadas, entrada.costoInicialConversion(), entrada.costoPeriodoConversion());
            return new ResultadoProceso(balance, material, conversion,
                    material.peps().costoInventarioFinal().add(conversion.peps().costoInventarioFinal()),
                    material.peps().costoUnidadesTransferidas().add(conversion.peps().costoUnidadesTransferidas()),
                    material.promedio().costoInventarioFinal().add(conversion.promedio().costoInventarioFinal()),
                    material.promedio().costoUnidadesTransferidas().add(conversion.promedio().costoUnidadesTransferidas()));
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage(), e);
        }
    }

    private static ElementoProceso calcularElemento(CostosCalculator.BalanceFisico balance,
                                                    BigDecimal unidadesIniciales, BigDecimal avanceInicial,
                                                    BigDecimal unidadesFinales, BigDecimal avanceFinal,
                                                    BigDecimal iniciadasYTerminadas,
                                                    BigDecimal costoInicial, BigDecimal costoPeriodo) {
        CostosCalculator.UnidadesEquivalentes equivalentes = CostosCalculator.unidadesEquivalentes(
                balance.unidadesTransferidas(), unidadesIniciales, avanceInicial, unidadesFinales,
                avanceFinal, iniciadasYTerminadas);
        CostosCalculator.AsignacionProceso peps = CostosCalculator.asignarPeps(costoInicial, costoPeriodo,
                equivalentes.terminadasPeps(), equivalentes.simpleFinal());
        CostosCalculator.AsignacionProceso promedio = CostosCalculator.asignarPromedio(costoInicial,
                costoPeriodo, equivalentes.promedioPonderado(), equivalentes.simpleFinal());
        CostosCalculator.AsignacionProceso ueps = CostosCalculator.asignarUeps(costoInicial, costoPeriodo,
                equivalentes.terminadasPeps(), balance.unidadesTransferidas());
        return new ElementoProceso(equivalentes, peps, promedio, ueps);
    }

    private void guardar(java.sql.Connection conn, UUID periodoId, ProcesoEntrada e) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO proceso_costo_periodo (id, codigo_proceso, periodo_id, unidades_iniciales,
                    avance_inicial_material, avance_inicial_conversion, costo_inicial_material,
                    costo_inicial_conversion, unidades_iniciadas, unidades_terminadas, unidades_finales,
                    avance_final_material, avance_final_conversion, costo_periodo_material,
                    costo_periodo_conversion)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (codigo_proceso, periodo_id) DO UPDATE SET
                    unidades_iniciales=EXCLUDED.unidades_iniciales,
                    avance_inicial_material=EXCLUDED.avance_inicial_material,
                    avance_inicial_conversion=EXCLUDED.avance_inicial_conversion,
                    costo_inicial_material=EXCLUDED.costo_inicial_material,
                    costo_inicial_conversion=EXCLUDED.costo_inicial_conversion,
                    unidades_iniciadas=EXCLUDED.unidades_iniciadas,
                    unidades_terminadas=EXCLUDED.unidades_terminadas,
                    unidades_finales=EXCLUDED.unidades_finales,
                    avance_final_material=EXCLUDED.avance_final_material,
                    avance_final_conversion=EXCLUDED.avance_final_conversion,
                    costo_periodo_material=EXCLUDED.costo_periodo_material,
                    costo_periodo_conversion=EXCLUDED.costo_periodo_conversion
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, codigo(e.codigoProceso()));
            ps.setObject(3, periodoId);
            ps.setBigDecimal(4, e.unidadesIniciales());
            ps.setBigDecimal(5, e.avanceInicialMaterial());
            ps.setBigDecimal(6, e.avanceInicialConversion());
            ps.setBigDecimal(7, e.costoInicialMaterial());
            ps.setBigDecimal(8, e.costoInicialConversion());
            ps.setBigDecimal(9, e.unidadesIniciadas());
            ps.setBigDecimal(10, e.unidadesTerminadas());
            ps.setBigDecimal(11, e.unidadesFinales());
            ps.setBigDecimal(12, e.avanceFinalMaterial());
            ps.setBigDecimal(13, e.avanceFinalConversion());
            ps.setBigDecimal(14, e.costoPeriodoMaterial());
            ps.setBigDecimal(15, e.costoPeriodoConversion());
            ps.executeUpdate();
        }
    }

    private static void validarEntrada(ProcesoEntrada entrada) {
        if (entrada == null) {
            throw new DomainException("La cédula de proceso es obligatoria.");
        }
        codigo(entrada.codigoProceso());
        if (entrada.fecha() == null) {
            throw new DomainException("La fecha del proceso es obligatoria.");
        }
        validarNoNegativo(entrada.unidadesIniciales(), "unidades iniciales");
        validarPorcentaje(entrada.avanceInicialMaterial(), "avance inicial de material");
        validarPorcentaje(entrada.avanceInicialConversion(), "avance inicial de conversión");
        validarNoNegativo(entrada.costoInicialMaterial(), "costo inicial de material");
        validarNoNegativo(entrada.costoInicialConversion(), "costo inicial de conversión");
        validarNoNegativo(entrada.unidadesIniciadas(), "unidades iniciadas");
        validarNoNegativo(entrada.unidadesTerminadas(), "unidades terminadas");
        validarNoNegativo(entrada.unidadesFinales(), "unidades finales");
        validarPorcentaje(entrada.avanceFinalMaterial(), "avance final de material");
        validarPorcentaje(entrada.avanceFinalConversion(), "avance final de conversión");
        validarNoNegativo(entrada.costoPeriodoMaterial(), "costo del período de material");
        validarNoNegativo(entrada.costoPeriodoConversion(), "costo del período de conversión");
    }

    private static String codigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new DomainException("El código del proceso es obligatorio.");
        }
        return codigo.trim();
    }

    private static void validarNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new DomainException("Las " + etiqueta + " no pueden ser negativas.");
        }
    }

    private static void validarPorcentaje(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainException("El " + etiqueta + " debe estar entre 0 y 1.");
        }
    }
}
