package sv.edu.ues.colibri.application.libros;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de generación de libros contables y estados financieros.
 */
public class LibroContableService {

    private static final String FILTRO_CASO_D = "AND p.evento_id IN "
            + "(SELECT id FROM evento_contable WHERE clave_unica LIKE 'SEED-CASO-D-2026-%')";

    private final JdbcSupport jdbc;
    private final AuthService auth;

    /**
     * Crea el servicio de libros contables.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     */
    public LibroContableService(JdbcSupport jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    public record LineaDiario(String folio, LocalDate fecha, String referencia, String concepto,
                              String cuenta, String nombreCuenta, BigDecimal debe, BigDecimal haber) {
    }

    public record LineaMayor(String cuenta, String nombre, BigDecimal debe,
                             BigDecimal haber, BigDecimal saldo) {
    }

    public record LineaMayorLibro(String cuenta, String nombre, BigDecimal debeAcumulado,
                                  BigDecimal haberAcumulado, BigDecimal saldoDeudor,
                                  BigDecimal saldoAcreedor) {
    }

    public record LineaHojaTrabajo(String cuenta, String nombre,
                                   BigDecimal debe, BigDecimal haber,
                                   BigDecimal ajusteDebe, BigDecimal ajusteHaber,
                                   BigDecimal ajustadaDebe, BigDecimal ajustadaHaber,
                                   BigDecimal resultadosDebe, BigDecimal resultadosHaber,
                                   BigDecimal capitalDebe, BigDecimal capitalHaber,
                                   BigDecimal situacionDebe, BigDecimal situacionHaber) {
    }

    public record LineaCuentaT(LocalDate fecha, String folio, String referencia, String concepto,
                               BigDecimal debe, BigDecimal haber) {
    }

    public record LineaEstado(String codigo, String concepto, BigDecimal importe,
                              int nivel, boolean subtotal) {
    }

    /**
     * Movimiento de patrimonio presentado con una columna para el resultado del período.
     * El resultado se obtiene de las cuentas de resultados para no depender de que el
     * cierre anual haya sido contabilizado antes de emitir el estado.
     */
    public record LineaCambioPatrimonio(String codigo, String concepto,
                                        BigDecimal saldoInicial, BigDecimal movimientos,
                                        BigDecimal resultadoPeriodo, BigDecimal saldoFinal,
                                        boolean subtotal) {
    }

    private record CuentaMovimiento(String codigo, String nombre, String rubro,
                                    BigDecimal debe, BigDecimal haber) {
        BigDecimal saldoDeudor() {
            return debe.subtract(haber);
        }

        BigDecimal saldoAcreedor() {
            return haber.subtract(debe);
        }
    }

    private record MovimientoEfectivo(String folio, LocalDate fecha, String concepto,
                                      BigDecimal importe, boolean inversion,
                                      boolean financiacion) {
    }

    /**
     * Genera el libro diario para un rango de fechas.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas del diario
     */
    public List<LineaDiario> diario(LocalDate desde, LocalDate hasta) {
        return diario(desde, hasta, false);
    }

    /**
     * Genera el libro diario para un rango y opcionalmente filtra el caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas del diario
     */
    public List<LineaDiario> diario(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            String sql = """
                          SELECT p.folio, p.fecha_contable, p.documento_soporte, p.concepto,
                              d.codigo_cuenta, c.nombre AS nombre_cuenta, d.debe, d.haber
                    FROM partida p
                    JOIN partida_detalle d ON d.partida_id = p.id
                    JOIN cuenta c ON c.codigo = d.codigo_cuenta
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                                        %s
                    ORDER BY p.fecha_contable, p.folio, d.linea
                                        """.formatted(filtroEvento(soloCasoD));
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaDiario> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new LineaDiario(rs.getString("folio"),
                                rs.getObject("fecha_contable", LocalDate.class),
                            rs.getString("documento_soporte"), rs.getString("concepto"),
                            rs.getString("codigo_cuenta"), rs.getString("nombre_cuenta"),
                                rs.getBigDecimal("debe"), rs.getBigDecimal("haber")));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error diario", e);
            }
        });
    }

    /**
     * Genera la cuenta T de una cuenta específica.
     *
     * @param codigo código de cuenta
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return movimientos de la cuenta T
     */
    public List<LineaCuentaT> cuentaT(String codigo, LocalDate desde, LocalDate hasta) {
        return cuentaT(codigo, desde, hasta, false);
    }

    /**
     * Genera la cuenta T de una cuenta específica con filtro opcional por caso D.
     *
     * @param codigo código de cuenta
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return movimientos de la cuenta T
     */
    public List<LineaCuentaT> cuentaT(String codigo, LocalDate desde, LocalDate hasta,
                                      boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            String sql = """
                    SELECT p.fecha_contable, p.folio, p.documento_soporte, p.concepto, d.debe, d.haber
                    FROM partida p
                    JOIN partida_detalle d ON d.partida_id = p.id
                    WHERE d.codigo_cuenta = ?
                      AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                                        %s
                    ORDER BY p.fecha_contable, p.folio, d.linea
                                        """.formatted(filtroEvento(soloCasoD));
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo);
                ps.setObject(2, desde);
                ps.setObject(3, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaCuentaT> lineas = new ArrayList<>();
                    while (rs.next()) {
                        lineas.add(new LineaCuentaT(rs.getObject("fecha_contable", LocalDate.class),
                                rs.getString("folio"), rs.getString("documento_soporte"),
                                rs.getString("concepto"), rs.getBigDecimal("debe"),
                                rs.getBigDecimal("haber")));
                    }
                    return lineas;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al generar cuenta T", e);
            }
        });
    }

    /**
     * Calcula el saldo de una cuenta hasta una fecha de corte.
     *
     * @param codigo código de cuenta
     * @param fechaCorte fecha de corte
     * @return saldo calculado
     */
    public BigDecimal saldoCuentaHasta(String codigo, LocalDate fechaCorte) {
        return saldoCuentaHasta(codigo, fechaCorte, false);
    }

    /**
     * Calcula el saldo de una cuenta con filtro opcional por caso D.
     *
     * @param codigo código de cuenta
     * @param fechaCorte fecha de corte
     * @param soloCasoD {@code true} para limitar el cálculo al caso D
     * @return saldo calculado
     */
    public BigDecimal saldoCuentaHasta(String codigo, LocalDate fechaCorte, boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> movimientosHasta(conn, fechaCorte, soloCasoD).stream()
                .filter(cuenta -> cuenta.codigo().equals(codigo))
                .findFirst().map(CuentaMovimiento::saldoDeudor).orElse(BigDecimal.ZERO));
    }

    /**
     * Genera la balanza de comprobación.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas de balanza
     */
    public List<LineaMayor> balanza(LocalDate desde, LocalDate hasta) {
        return balanza(desde, hasta, false);
    }

    /**
     * Genera la balanza de comprobación con filtro opcional por caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas de balanza
     */
    public List<LineaMayor> balanza(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            String sql = """
                    SELECT d.codigo_cuenta, c.nombre,
                           COALESCE(SUM(d.debe),0) AS debe,
                           COALESCE(SUM(d.haber),0) AS haber
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    JOIN cuenta c ON c.codigo = d.codigo_cuenta
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                                        %s
                    GROUP BY d.codigo_cuenta, c.nombre
                    ORDER BY d.codigo_cuenta
                                        """.formatted(filtroEvento(soloCasoD));
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaMayor> list = new ArrayList<>();
                    while (rs.next()) {
                        BigDecimal debe = rs.getBigDecimal("debe");
                        BigDecimal haber = rs.getBigDecimal("haber");
                        list.add(new LineaMayor(rs.getString("codigo_cuenta"), rs.getString("nombre"),
                                debe, haber, debe.subtract(haber)));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error balanza", e);
            }
        });
    }

        /**
         * Genera el libro mayor hasta una fecha de corte.
         *
         * @param fechaCorte fecha de corte
         * @return líneas del libro mayor
         */
        public List<LineaMayorLibro> libroMayor(LocalDate fechaCorte) {
            return libroMayor(fechaCorte, false);
        }

        /**
         * Genera el libro mayor hasta una fecha de corte con filtro opcional por caso D.
         *
         * @param fechaCorte fecha de corte
         * @param soloCasoD {@code true} para limitar el reporte al caso D
         * @return líneas del libro mayor
         */
        public List<LineaMayorLibro> libroMayor(LocalDate fechaCorte, boolean soloCasoD) {
        auth.exigirContador();
            return jdbc.inTransaction(conn -> movimientosHasta(conn, fechaCorte, soloCasoD).stream()
                    .map(cuenta -> new LineaMayorLibro(cuenta.codigo(), cuenta.nombre(),
                            cuenta.debe(), cuenta.haber(), cuenta.saldoDeudor().max(BigDecimal.ZERO),
                            cuenta.saldoAcreedor().max(BigDecimal.ZERO)))
                    .toList());
        }

    /**
     * Genera la hoja de trabajo para un rango de fechas.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas de hoja de trabajo
     */
    public List<LineaHojaTrabajo> hojaTrabajo(LocalDate desde, LocalDate hasta) {
        return hojaTrabajo(desde, hasta, false);
    }

    /**
     * Genera la hoja de trabajo con filtro opcional por caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas de hoja de trabajo
     */
    public List<LineaHojaTrabajo> hojaTrabajo(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            String sql = """
                    SELECT d.codigo_cuenta, c.nombre,
                           COALESCE(SUM(CASE WHEN p.tipo <> 'AJ'
                               OR p.fecha_contable < ? THEN d.debe ELSE 0 END), 0) AS debe,
                           COALESCE(SUM(CASE WHEN p.tipo <> 'AJ'
                               OR p.fecha_contable < ? THEN d.haber ELSE 0 END), 0) AS haber,
                           COALESCE(SUM(CASE WHEN p.tipo = 'AJ'
                               AND p.fecha_contable BETWEEN ? AND ? THEN d.debe ELSE 0 END), 0) AS ajuste_debe,
                           COALESCE(SUM(CASE WHEN p.tipo = 'AJ'
                               AND p.fecha_contable BETWEEN ? AND ? THEN d.haber ELSE 0 END), 0) AS ajuste_haber
                    FROM partida p
                    JOIN partida_detalle d ON d.partida_id = p.id
                    JOIN cuenta c ON c.codigo = d.codigo_cuenta
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable <= ?
                                        %s
                    GROUP BY d.codigo_cuenta, c.nombre
                    ORDER BY d.codigo_cuenta
                                        """.formatted(filtroEvento(soloCasoD));
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, desde);
                ps.setObject(2, desde);
                ps.setObject(3, desde);
                ps.setObject(4, hasta);
                ps.setObject(5, desde);
                ps.setObject(6, hasta);
                ps.setObject(7, hasta);
                
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaHojaTrabajo> filas = new ArrayList<>();
                    BigDecimal resultadoDebe = BigDecimal.ZERO;
                    BigDecimal resultadoHaber = BigDecimal.ZERO;
                    while (rs.next()) {
                        BigDecimal debe = rs.getBigDecimal("debe");
                        BigDecimal haber = rs.getBigDecimal("haber");
                        BigDecimal ajusteDebe = rs.getBigDecimal("ajuste_debe");
                        BigDecimal ajusteHaber = rs.getBigDecimal("ajuste_haber");
                        BigDecimal saldoAjustado = debe.add(ajusteDebe).subtract(haber).subtract(ajusteHaber);
                        boolean resultado = esResultado(rs.getString("codigo_cuenta"));
                        boolean patrimonio = esPatrimonio(rs.getString("codigo_cuenta"));
                        BigDecimal resultadosDebe = resultado
                                ? saldoAjustado.max(BigDecimal.ZERO) : BigDecimal.ZERO;
                        BigDecimal resultadosHaber = resultado
                                ? saldoAjustado.negate().max(BigDecimal.ZERO) : BigDecimal.ZERO;
                        if (resultado) {
                            resultadoDebe = resultadoDebe.add(resultadosDebe);
                            resultadoHaber = resultadoHaber.add(resultadosHaber);
                        }
                        filas.add(new LineaHojaTrabajo(rs.getString("codigo_cuenta"),
                                rs.getString("nombre"), debe, haber, ajusteDebe, ajusteHaber,
                            saldoAjustado.max(BigDecimal.ZERO), saldoAjustado.negate().max(BigDecimal.ZERO),
                            resultadosDebe, resultadosHaber,
                            patrimonio ? saldoAjustado.max(BigDecimal.ZERO) : BigDecimal.ZERO,
                            patrimonio ? saldoAjustado.negate().max(BigDecimal.ZERO) : BigDecimal.ZERO,
                            esBalance(rs.getString("codigo_cuenta"))
                                ? saldoAjustado.max(BigDecimal.ZERO) : BigDecimal.ZERO,
                            esBalance(rs.getString("codigo_cuenta"))
                                ? saldoAjustado.negate().max(BigDecimal.ZERO) : BigDecimal.ZERO));
                    }
                    // La utilidad o pérdida del período completa tanto el estado de capital
                    // como el balance general sin alterar las líneas contables originales.
                    BigDecimal utilidadNeta = resultadoHaber.subtract(resultadoDebe);
                    filas.add(new LineaHojaTrabajo("", "Resultado del período", BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            utilidadNeta.negate().max(BigDecimal.ZERO), utilidadNeta.max(BigDecimal.ZERO),
                            utilidadNeta.negate().max(BigDecimal.ZERO), utilidadNeta.max(BigDecimal.ZERO)));
                    return filas;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al generar hoja de trabajo", e);
            }
        });
    }

    /**
     * Genera el estado de resultados.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas del estado de resultados
     */
    public List<LineaEstado> estadoResultados(LocalDate desde, LocalDate hasta) {
        return estadoResultados(desde, hasta, false);
    }

    /**
     * Genera el estado de resultados con filtro opcional por caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas del estado de resultados
     */
    public List<LineaEstado> estadoResultados(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        auth.exigirContador();
        List<CuentaMovimiento> cuentas = jdbc.inTransaction(
                conn -> movimientos(conn, desde, hasta, soloCasoD));
        return construirEstadoResultados(cuentas);
    }

    /**
     * Genera el estado de situación financiera.
     *
     * @param fechaCorte fecha de corte
     * @return líneas del estado de situación
     */
    public List<LineaEstado> estadoSituacion(LocalDate fechaCorte) {
        return estadoSituacion(fechaCorte, false);
    }

    /**
     * Genera el estado de situación financiera con filtro opcional por caso D.
     *
     * @param fechaCorte fecha de corte
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas del estado de situación
     */
    public List<LineaEstado> estadoSituacion(LocalDate fechaCorte, boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            List<CuentaMovimiento> saldos = movimientosHasta(conn, fechaCorte, soloCasoD);
            List<LineaEstado> lineas = new ArrayList<>();
            lineas.add(encabezado("ACTIVOS"));
            lineas.add(encabezado("ACTIVOS CORRIENTES"));
            BigDecimal activosCorrientes = agregarGrupo(lineas, saldos, "Disponibles", false,
                    cuenta -> "ACTIVO_CORRIENTE".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "1101"));
            activosCorrientes = activosCorrientes.add(agregarGrupo(lineas, saldos,
                    "Realizables", false, cuenta -> "ACTIVO_CORRIENTE".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "1102", "1103", "1105", "1106",
                            "1107", "1108")));
            activosCorrientes = activosCorrientes.add(agregarGrupo(lineas, saldos,
                    "Otros activos corrientes", false,
                    cuenta -> "ACTIVO_CORRIENTE".equals(cuenta.rubro())
                            && !tienePrefijo(cuenta.codigo(), "1101", "1102", "1103", "1105",
                            "1106", "1107", "1108")));
            lineas.add(total("TOTAL ACTIVOS CORRIENTES", activosCorrientes));

            lineas.add(encabezado("ACTIVOS NO CORRIENTES"));
            BigDecimal activosNoCorrientes = agregarGrupo(lineas, saldos,
                    "Activos fijos tangibles", false,
                    cuenta -> "ACTIVO_NO_CORRIENTE".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "1201", "1202"));
            activosNoCorrientes = activosNoCorrientes.add(agregarGrupo(lineas, saldos,
                    "Activos fijos intangibles", false,
                    cuenta -> "ACTIVO_NO_CORRIENTE".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "1203", "1204")));
            lineas.add(total("TOTAL ACTIVOS NO CORRIENTES", activosNoCorrientes));

            BigDecimal otrosActivos = agregarGrupo(lineas, saldos, "Otros activos", false,
                    cuenta -> "ACTIVO_NO_CORRIENTE".equals(cuenta.rubro())
                            && !tienePrefijo(cuenta.codigo(), "1201", "1202", "1203", "1204"));
            BigDecimal activos = activosCorrientes.add(activosNoCorrientes).add(otrosActivos);
            lineas.add(total("TOTAL ACTIVOS", activos));

            lineas.add(encabezado("PARTICIPACIONES (PASIVO Y PATRIMONIO)"));
            lineas.add(encabezado("PASIVOS"));
            BigDecimal pasivos = agregarGrupo(lineas, saldos, "Pasivos corrientes", true,
                    cuenta -> "PASIVO_CORRIENTE".equals(cuenta.rubro()));
            pasivos = pasivos.add(agregarGrupo(lineas, saldos, "Pasivos no corrientes", true,
                    cuenta -> "PASIVO_NO_CORRIENTE".equals(cuenta.rubro())
                            && !tienePrefijo(cuenta.codigo(), "2202")));
            pasivos = pasivos.add(agregarGrupo(lineas, saldos, "Diferidos", true,
                    cuenta -> "PASIVO_NO_CORRIENTE".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "2202")));
            lineas.add(total("TOTAL PASIVOS", pasivos));

            lineas.add(encabezado("PATRIMONIO"));
            BigDecimal patrimonio = agregarGrupo(lineas, saldos, "Capital social", true,
                    cuenta -> "PATRIMONIO".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "3101"));
            patrimonio = patrimonio.add(agregarGrupo(lineas, saldos, "Reservas", true,
                    cuenta -> "PATRIMONIO".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "3102")));
            patrimonio = patrimonio.add(agregarGrupo(lineas, saldos, "Resultados acumulados", true,
                    cuenta -> "PATRIMONIO".equals(cuenta.rubro())
                            && tienePrefijo(cuenta.codigo(), "3103")));
            patrimonio = patrimonio.add(agregarGrupo(lineas, saldos, "Otros componentes del patrimonio", true,
                    cuenta -> "PATRIMONIO".equals(cuenta.rubro())
                            && !"310401".equals(cuenta.codigo())
                            && !tienePrefijo(cuenta.codigo(), "3101", "3102", "3103")));

            LocalDate inicioEjercicio = LocalDate.of(fechaCorte.getYear(), 1, 1);
            BigDecimal resultado = resultadoNeto(
                    movimientos(conn, inicioEjercicio, fechaCorte, soloCasoD));
            lineas.add(new LineaEstado("", "Resultado del ejercicio", resultado, 1, false));
            patrimonio = patrimonio.add(resultado);
            lineas.add(total("TOTAL PATRIMONIO", patrimonio));
            BigDecimal pasivosPatrimonio = pasivos.add(patrimonio);
            lineas.add(total("TOTAL PASIVOS Y PATRIMONIO", pasivosPatrimonio));
            lineas.add(total("DIFERENCIA DE BALANCE", activos.subtract(pasivosPatrimonio)));
            return lineas;
        });
    }

    /**
     * Estado de cambios en el patrimonio. Las variaciones de capital, reservas y
     * resultados acumulados proceden de las cuentas patrimoniales; el resultado del
     * período se deriva del estado de resultados. Cuando ya existe una partida de
     * cierre en 310401 y las cuentas de resultado quedaron en cero, se presenta ese
     * movimiento para no omitir el resultado contabilizado.
     */
    /**
     * Genera el estado de cambios en el patrimonio.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas del estado de cambios en el patrimonio
     */
    public List<LineaCambioPatrimonio> estadoCambiosPatrimonio(LocalDate desde, LocalDate hasta) {
        return estadoCambiosPatrimonio(desde, hasta, false);
    }

    /**
     * Genera el estado de cambios en el patrimonio con filtro opcional por caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas del estado de cambios en el patrimonio
     */
    public List<LineaCambioPatrimonio> estadoCambiosPatrimonio(LocalDate desde, LocalDate hasta,
                                                                 boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            LocalDate corteInicial = desde.equals(LocalDate.MIN) ? LocalDate.MIN : desde.minusDays(1);
            List<CuentaMovimiento> iniciales = movimientosHasta(conn, corteInicial, soloCasoD);
            List<CuentaMovimiento> finales = movimientosHasta(conn, hasta, soloCasoD);
            List<CuentaMovimiento> movimientosPeriodo = movimientos(conn, desde, hasta, soloCasoD);
            return construirEstadoCambiosPatrimonio(iniciales, finales, movimientosPeriodo);
        });
    }

    /**
     * Estado de flujos de efectivo por método directo basado en las partidas que
     * afectan Caja chica o Bancos (grupo 1101). La clasificación es por la
     * contrapartida de cada partida: activo no corriente = inversión; patrimonio o
     * pasivo no corriente = financiación; las demás partidas = operación. Las
     * operaciones mixtas deben registrarse en partidas separadas para una
     * clasificación más granular.
     */
    /**
     * Genera el estado de flujos de efectivo.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas del estado de flujos de efectivo
     */
    public List<LineaEstado> estadoFlujosEfectivo(LocalDate desde, LocalDate hasta) {
        return estadoFlujosEfectivo(desde, hasta, false);
    }

    /**
     * Genera el estado de flujos de efectivo con filtro opcional por caso D.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param soloCasoD {@code true} para limitar el reporte al caso D
     * @return líneas del estado de flujos de efectivo
     */
    public List<LineaEstado> estadoFlujosEfectivo(LocalDate desde, LocalDate hasta,
                                                   boolean soloCasoD) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            LocalDate corteInicial = desde.equals(LocalDate.MIN) ? LocalDate.MIN : desde.minusDays(1);
            BigDecimal saldoInicial = saldoEfectivoHasta(conn, corteInicial, soloCasoD);
            List<MovimientoEfectivo> movimientos = movimientosEfectivo(conn, desde, hasta, soloCasoD);
            List<LineaEstado> lineas = new ArrayList<>();
            lineas.add(new LineaEstado("", "Método directo: movimientos de Caja chica y Bancos (grupo 1101)",
                    BigDecimal.ZERO, 0, false));

            BigDecimal operacion = agregarFlujo(lineas, movimientos, "OPERACION",
                    "Flujos de actividades de operación");
            BigDecimal inversion = agregarFlujo(lineas, movimientos, "INVERSION",
                    "Flujos de actividades de inversión");
            BigDecimal financiacion = agregarFlujo(lineas, movimientos, "FINANCIACION",
                    "Flujos de actividades de financiación");
            BigDecimal variacion = operacion.add(inversion).add(financiacion);
            BigDecimal saldoFinal = saldoEfectivoHasta(conn, hasta, soloCasoD);

            lineas.add(total("AUMENTO (DISMINUCIÓN) NETA DEL EFECTIVO", variacion));
            lineas.add(new LineaEstado("", "Efectivo y equivalentes al inicio del período",
                    saldoInicial, 0, false));
            lineas.add(total("EFECTIVO Y EQUIVALENTES AL FINAL DEL PERÍODO", saldoFinal));
            lineas.add(total("DIFERENCIA DE CONCILIACIÓN DEL EFECTIVO",
                    saldoInicial.add(variacion).subtract(saldoFinal)));
            return lineas;
        });
    }

    private List<LineaCambioPatrimonio> construirEstadoCambiosPatrimonio(
            List<CuentaMovimiento> iniciales, List<CuentaMovimiento> finales,
            List<CuentaMovimiento> movimientosPeriodo) {
        Map<String, CuentaMovimiento> porCuentaInicial = porCodigo(iniciales);
        Map<String, CuentaMovimiento> porCuentaFinal = porCodigo(finales);
        List<String> codigos = new ArrayList<>();
        for (CuentaMovimiento cuenta : iniciales) {
            if (esPatrimonioPresentable(cuenta) && !codigos.contains(cuenta.codigo())) {
                codigos.add(cuenta.codigo());
            }
        }
        for (CuentaMovimiento cuenta : finales) {
            if (esPatrimonioPresentable(cuenta) && !codigos.contains(cuenta.codigo())) {
                codigos.add(cuenta.codigo());
            }
        }

        List<LineaCambioPatrimonio> lineas = new ArrayList<>();
        BigDecimal saldoInicialTotal = BigDecimal.ZERO;
        BigDecimal movimientosTotal = BigDecimal.ZERO;
        BigDecimal saldoFinalCuentas = BigDecimal.ZERO;
        for (String codigo : codigos) {
            CuentaMovimiento inicial = porCuentaInicial.get(codigo);
            CuentaMovimiento finalCuenta = porCuentaFinal.get(codigo);
            BigDecimal saldoInicial = saldoAcreedor(inicial);
            BigDecimal saldoFinal = saldoAcreedor(finalCuenta);
            BigDecimal movimiento = saldoFinal.subtract(saldoInicial);
            String nombre = finalCuenta != null ? finalCuenta.nombre() : inicial.nombre();
            lineas.add(new LineaCambioPatrimonio(codigo, nombre, saldoInicial, movimiento,
                    BigDecimal.ZERO, saldoFinal, false));
            saldoInicialTotal = saldoInicialTotal.add(saldoInicial);
            movimientosTotal = movimientosTotal.add(movimiento);
            saldoFinalCuentas = saldoFinalCuentas.add(saldoFinal);
        }

        BigDecimal resultadoCalculado = resultadoNeto(movimientosPeriodo);
        BigDecimal movimientoResultadoContabilizado = saldoAcreedor(porCuentaFinal.get("310401"))
                .subtract(saldoAcreedor(porCuentaInicial.get("310401")));
        BigDecimal resultado = resultadoCalculado.signum() != 0
                ? resultadoCalculado : movimientoResultadoContabilizado;
        lineas.add(new LineaCambioPatrimonio("", "Resultado del período", BigDecimal.ZERO,
                BigDecimal.ZERO, resultado, resultado, false));

        BigDecimal saldoFinalPresentado = saldoFinalCuentas.add(resultado);
        lineas.add(new LineaCambioPatrimonio("", "TOTAL PATRIMONIO", saldoInicialTotal,
                movimientosTotal, resultado, saldoFinalPresentado, true));
        lineas.add(new LineaCambioPatrimonio("", "DIFERENCIA DE CONCILIACIÓN DEL PATRIMONIO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                saldoInicialTotal.add(movimientosTotal).add(resultado).subtract(saldoFinalPresentado), true));
        return lineas;
    }

    private Map<String, CuentaMovimiento> porCodigo(List<CuentaMovimiento> cuentas) {
        Map<String, CuentaMovimiento> resultado = new LinkedHashMap<>();
        for (CuentaMovimiento cuenta : cuentas) {
            resultado.put(cuenta.codigo(), cuenta);
        }
        return resultado;
    }

    private boolean esPatrimonioPresentable(CuentaMovimiento cuenta) {
        return "PATRIMONIO".equals(cuenta.rubro()) && !"310401".equals(cuenta.codigo());
    }

    private BigDecimal saldoAcreedor(CuentaMovimiento cuenta) {
        return cuenta == null ? BigDecimal.ZERO : cuenta.saldoAcreedor();
    }

    private BigDecimal agregarFlujo(List<LineaEstado> lineas, List<MovimientoEfectivo> movimientos,
                                    String tipo, String titulo) {
        lineas.add(new LineaEstado("", titulo, BigDecimal.ZERO, 0, false));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (MovimientoEfectivo movimiento : movimientos) {
            if (!tipoFlujo(movimiento).equals(tipo)) {
                continue;
            }
            lineas.add(new LineaEstado(movimiento.folio(), movimiento.fecha() + " · "
                    + movimiento.concepto(), movimiento.importe(), 1, false));
            subtotal = subtotal.add(movimiento.importe());
        }
        lineas.add(total("Flujo neto de " + titulo.toLowerCase(java.util.Locale.ROOT), subtotal));
        return subtotal;
    }

    private String tipoFlujo(MovimientoEfectivo movimiento) {
        if (movimiento.financiacion()) {
            return "FINANCIACION";
        }
        if (movimiento.inversion()) {
            return "INVERSION";
        }
        return "OPERACION";
    }

    private BigDecimal saldoEfectivoHasta(Connection conn, LocalDate fechaCorte, boolean soloCasoD) {
        String sql = """
                SELECT COALESCE(SUM(d.debe - d.haber), 0)
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE d.codigo_cuenta LIKE '1101%%'
                  AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                  AND p.fecha_contable <= ?
                  %s
                """.formatted(filtroEvento(soloCasoD));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fechaCorte);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular el saldo de efectivo", e);
        }
    }

    private List<MovimientoEfectivo> movimientosEfectivo(Connection conn, LocalDate desde,
                                                           LocalDate hasta, boolean soloCasoD) {
        String sql = """
                SELECT p.folio, p.fecha_contable, p.concepto,
                       COALESCE(SUM(CASE WHEN d.codigo_cuenta LIKE '1101%%'
                           THEN d.debe - d.haber ELSE 0 END), 0) AS importe,
                       BOOL_OR(CASE WHEN d.codigo_cuenta NOT LIKE '1101%%'
                           AND c.rubro = 'ACTIVO_NO_CORRIENTE' THEN TRUE ELSE FALSE END) AS inversion,
                       BOOL_OR(CASE WHEN d.codigo_cuenta NOT LIKE '1101%%'
                           AND (c.rubro = 'PASIVO_NO_CORRIENTE' OR c.rubro = 'PATRIMONIO')
                           THEN TRUE ELSE FALSE END) AS financiacion
                FROM partida p
                JOIN partida_detalle d ON d.partida_id = p.id
                JOIN cuenta c ON c.codigo = d.codigo_cuenta
                WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                  AND p.fecha_contable BETWEEN ? AND ?
                  %s
                GROUP BY p.id, p.folio, p.fecha_contable, p.concepto
                HAVING SUM(CASE WHEN d.codigo_cuenta LIKE '1101%%'
                    THEN d.debe - d.haber ELSE 0 END) <> 0
                ORDER BY p.fecha_contable, p.folio
                """.formatted(filtroEvento(soloCasoD));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, desde);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                List<MovimientoEfectivo> movimientos = new ArrayList<>();
                while (rs.next()) {
                    movimientos.add(new MovimientoEfectivo(rs.getString("folio"),
                            rs.getObject("fecha_contable", LocalDate.class), rs.getString("concepto"),
                            rs.getBigDecimal("importe"), rs.getBoolean("inversion"),
                            rs.getBoolean("financiacion")));
                }
                return movimientos;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al generar el estado de flujos de efectivo", e);
        }
    }

    private List<CuentaMovimiento> movimientos(Connection conn, LocalDate desde, LocalDate hasta) {
        return movimientos(conn, desde, hasta, false);
    }

    private List<CuentaMovimiento> movimientos(Connection conn, LocalDate desde, LocalDate hasta,
                                               boolean soloCasoD) {
        return consultarMovimientos(conn, "p.fecha_contable BETWEEN ? AND ?", desde, hasta, soloCasoD);
    }

    private List<CuentaMovimiento> movimientosHasta(Connection conn, LocalDate hasta) {
        return movimientosHasta(conn, hasta, false);
    }

    private List<CuentaMovimiento> movimientosHasta(Connection conn, LocalDate hasta, boolean soloCasoD) {
        return consultarMovimientos(conn, "p.fecha_contable <= ?", hasta, null, soloCasoD);
    }

    private List<CuentaMovimiento> consultarMovimientos(Connection conn, String filtroFechas,
                                                        LocalDate desde, LocalDate hasta,
                                                        boolean soloCasoD) {
        String sql = """
                SELECT c.codigo, c.nombre, c.rubro,
                       COALESCE(SUM(d.debe), 0) AS debe,
                       COALESCE(SUM(d.haber), 0) AS haber
                FROM cuenta c
                JOIN partida_detalle d ON d.codigo_cuenta = c.codigo
                JOIN partida p ON p.id = d.partida_id
                WHERE length(c.codigo) = 6
                  AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                                    AND %s
                                        %s
                GROUP BY c.codigo, c.nombre, c.rubro
                ORDER BY c.codigo
                                                                """.formatted(filtroFechas, filtroEvento(soloCasoD));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, desde);
            if (filtroFechas.contains("BETWEEN")) {
                ps.setObject(2, hasta);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CuentaMovimiento> cuentas = new ArrayList<>();
                while (rs.next()) {
                    cuentas.add(new CuentaMovimiento(rs.getString("codigo"), rs.getString("nombre"),
                            rs.getString("rubro"), rs.getBigDecimal("debe"), rs.getBigDecimal("haber")));
                }
                return cuentas;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular saldos por cuenta", e);
        }
    }

    private String filtroEvento(boolean soloCasoD) {
        return soloCasoD ? FILTRO_CASO_D : "";
    }

    private List<LineaEstado> construirEstadoResultados(List<CuentaMovimiento> cuentas) {
        List<LineaEstado> lineas = new ArrayList<>();
        BigDecimal ventasBrutas = cuentas.stream().filter(c -> c.codigo().startsWith("4"))
                .map(CuentaMovimiento::haber).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal devolucionesYDescuentos = cuentas.stream().filter(c -> c.codigo().startsWith("4"))
                .map(CuentaMovimiento::debe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ingresos = ventasBrutas.subtract(devolucionesYDescuentos);
        lineas.add(new LineaEstado("", "VENTAS Y SERVICIOS", BigDecimal.ZERO, 0, false));
        for (CuentaMovimiento cuenta : cuentas) {
            if (cuenta.codigo().startsWith("4")) {
                lineas.add(new LineaEstado(cuenta.codigo(), cuenta.nombre(), cuenta.saldoAcreedor(),
                        1, false));
            }
        }
        lineas.add(total("VENTAS BRUTAS", ventasBrutas));
        lineas.add(new LineaEstado("", "Devoluciones y descuentos sobre ventas",
                devolucionesYDescuentos, 1, false));
        lineas.add(total("VENTAS NETAS", ingresos));

        BigDecimal costos = agregarClase(lineas, cuentas, "51", "COSTO DE VENTAS Y PRESTACIÓN", true);
        BigDecimal variacionCif = cuentas.stream().filter(c -> c.codigo().startsWith("52"))
                .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        lineas.add(new LineaEstado("", "Variación de costos indirectos de fabricación", variacionCif,
                1, false));
        costos = costos.add(variacionCif);
        lineas.add(total("COSTO DE VENTAS", costos));
        BigDecimal utilidadBruta = ingresos.subtract(costos);
        lineas.add(total("UTILIDAD BRUTA", utilidadBruta));

        BigDecimal gastosAdministracion = agregarClase(lineas, cuentas, "6101",
                "Gastos de administración", true);
        BigDecimal gastosVenta = agregarClase(lineas, cuentas, "6102",
                "Gastos de venta y comercialización", true);
        BigDecimal otrosGastosOperacion = agregarClase(lineas, cuentas, "6103",
                "Otros gastos operativos", true);
        BigDecimal gastosNoClasificados = cuentas.stream()
                .filter(c -> c.codigo().startsWith("6")
                        && !tienePrefijo(c.codigo(), "6101", "6102", "6103"))
                .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        lineas.add(new LineaEstado("", "Otros gastos de operación no clasificados",
                gastosNoClasificados, 1, false));
        BigDecimal gastos = gastosAdministracion.add(gastosVenta).add(otrosGastosOperacion)
                .add(gastosNoClasificados);
        lineas.add(total("TOTAL GASTOS DE OPERACIÓN", gastos));
        BigDecimal utilidadOperacion = utilidadBruta.subtract(gastos);
        lineas.add(total("UTILIDAD DE OPERACIÓN", utilidadOperacion));

        BigDecimal ingresosFinancieros = monto(cuentas, "710101", false);
        BigDecimal gastosFinancieros = monto(cuentas, "710102", true);
        lineas.add(new LineaEstado("710101", "Ingresos financieros", ingresosFinancieros, 1, false));
        lineas.add(new LineaEstado("710102", "Gastos financieros", gastosFinancieros, 1, false));
        BigDecimal resultadoFinanciero = ingresosFinancieros.subtract(gastosFinancieros);
        lineas.add(total("RESULTADO FINANCIERO NETO", resultadoFinanciero));
        BigDecimal otrosResultados = cuentas.stream()
                .filter(c -> c.codigo().startsWith("7")
                        && !c.codigo().startsWith("7101")
                        && !c.codigo().startsWith("7102")
                        && !c.codigo().startsWith("7103"))
                .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
        lineas.add(new LineaEstado("", "Otros ingresos (gastos) netos", otrosResultados, 1, false));
        BigDecimal antesImpuesto = utilidadOperacion.add(resultadoFinanciero).add(otrosResultados);
        lineas.add(total("RESULTADO ANTES DE IMPUESTOS", antesImpuesto));

        BigDecimal impuestoCorriente = cuentas.stream().filter(c -> c.codigo().startsWith("7102"))
            .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gastoImpuestoDiferido = cuentas.stream().filter(c -> c.codigo().equals("710301"))
            .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ingresoDiferido = cuentas.stream().filter(c -> c.codigo().equals("710302"))
            .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impuestoDiferidoNeto = gastoImpuestoDiferido.subtract(ingresoDiferido);
        BigDecimal impuesto = impuestoCorriente.add(impuestoDiferidoNeto);
        lineas.add(new LineaEstado("", "Impuesto corriente", impuestoCorriente, 1, false));
        lineas.add(new LineaEstado("", "Impuesto diferido neto", impuestoDiferidoNeto, 1, false));
        lineas.add(total("UTILIDAD (PÉRDIDA) NETA DEL PERÍODO", antesImpuesto.subtract(impuesto)));
        return lineas;
    }

    private BigDecimal resultadoNeto(List<CuentaMovimiento> cuentas) {
        BigDecimal ingresos = cuentas.stream().filter(c -> c.codigo().startsWith("4"))
                .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costosGastos = cuentas.stream()
                .filter(c -> c.codigo().startsWith("5") || c.codigo().startsWith("6"))
                .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otrosResultados = cuentas.stream().filter(c -> c.codigo().startsWith("7")
                        && !c.codigo().startsWith("7102") && !c.codigo().startsWith("7103"))
                .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impuestos = cuentas.stream()
                .filter(c -> c.codigo().startsWith("7102") || c.codigo().startsWith("7103"))
                .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ingresos.subtract(costosGastos).add(otrosResultados).subtract(impuestos);
    }

    private BigDecimal agregarClase(List<LineaEstado> lineas, List<CuentaMovimiento> cuentas,
                                    String prefijo, String titulo, boolean deudora) {
        lineas.add(new LineaEstado("", titulo, BigDecimal.ZERO, 0, false));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CuentaMovimiento cuenta : cuentas) {
            if (cuenta.codigo().startsWith(prefijo)) {
                BigDecimal importe = deudora ? cuenta.saldoDeudor() : cuenta.saldoAcreedor();
                lineas.add(new LineaEstado(cuenta.codigo(), cuenta.nombre(), importe, 1, false));
                subtotal = subtotal.add(importe);
            }
        }
        lineas.add(total("Subtotal " + titulo.toLowerCase(java.util.Locale.ROOT), subtotal));
        return subtotal;
    }

    private BigDecimal monto(List<CuentaMovimiento> cuentas, String codigo, boolean deudora) {
        return cuentas.stream().filter(c -> c.codigo().equals(codigo)).findFirst()
                .map(c -> deudora ? c.saldoDeudor() : c.saldoAcreedor()).orElse(BigDecimal.ZERO);
    }

    private LineaEstado encabezado(String concepto) {
        return new LineaEstado("", concepto, BigDecimal.ZERO, 0, false);
    }

    private BigDecimal agregarGrupo(List<LineaEstado> lineas, List<CuentaMovimiento> cuentas,
                                    String titulo, boolean acreedora,
                                    java.util.function.Predicate<CuentaMovimiento> filtro) {
        lineas.add(new LineaEstado("", titulo, BigDecimal.ZERO, 0, false));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CuentaMovimiento cuenta : cuentas) {
            if (filtro.test(cuenta)) {
                BigDecimal importe = acreedora ? cuenta.saldoAcreedor() : cuenta.saldoDeudor();
                lineas.add(new LineaEstado(cuenta.codigo(), cuenta.nombre(), importe, 1, false));
                subtotal = subtotal.add(importe);
            }
        }
        lineas.add(total("Subtotal " + titulo.toLowerCase(java.util.Locale.ROOT), subtotal));
        return subtotal;
    }

    private boolean tienePrefijo(String codigo, String... prefijos) {
        for (String prefijo : prefijos) {
            if (codigo.startsWith(prefijo)) {
                return true;
            }
        }
        return false;
    }

    private LineaEstado total(String concepto, BigDecimal importe) {
        return new LineaEstado("", concepto, importe, 0, true);
    }

    private boolean esResultado(String codigo) {
        return codigo.startsWith("4") || codigo.startsWith("5")
                || codigo.startsWith("6") || codigo.startsWith("7");
    }

    private boolean esBalance(String codigo) {
        return codigo.startsWith("1") || codigo.startsWith("2") || codigo.startsWith("3");
    }

    private boolean esPatrimonio(String codigo) {
        return codigo.startsWith("3");
    }

    /**
     * Resume indicadores principales de los estados financieros.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return mapa con indicadores de resumen
     */
    public Map<String, BigDecimal> resumenEstados(LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            // Flujos del período con el mismo criterio de estadoResultados.
            List<CuentaMovimiento> periodo = movimientos(conn, desde, hasta);
            // Saldos acumulados con el mismo criterio de estadoSituacion.
            List<CuentaMovimiento> saldos = movimientosHasta(conn, hasta);
            // Resultado del ejercicio (año en curso hasta la fecha de corte),
            // idéntico al que estadoSituacion incorpora al patrimonio.
            LocalDate inicioEjercicio = LocalDate.of(hasta.getYear(), 1, 1);
            List<CuentaMovimiento> ejercicio = movimientos(conn, inicioEjercicio, hasta);

            BigDecimal ingresos = periodo.stream().filter(c -> c.codigo().startsWith("4"))
                    .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal costos = periodo.stream().filter(c -> c.codigo().startsWith("5"))
                    .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal gastos = periodo.stream().filter(c -> c.codigo().startsWith("6"))
                    .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal resultado = resultadoNeto(periodo);

            BigDecimal activos = saldos.stream()
                    .filter(c -> "ACTIVO_CORRIENTE".equals(c.rubro())
                            || "ACTIVO_NO_CORRIENTE".equals(c.rubro()))
                    .map(CuentaMovimiento::saldoDeudor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pasivos = saldos.stream()
                    .filter(c -> "PASIVO_CORRIENTE".equals(c.rubro())
                            || "PASIVO_NO_CORRIENTE".equals(c.rubro()))
                    .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
            // 310401 es la cuenta de cierre del resultado del ejercicio; se excluye
            // para no duplicarlo (mismo criterio que estadoSituacion).
            BigDecimal patrimonio = saldos.stream()
                    .filter(c -> "PATRIMONIO".equals(c.rubro()) && !"310401".equals(c.codigo()))
                    .map(CuentaMovimiento::saldoAcreedor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal resultadoEjercicio = resultadoNeto(ejercicio);
            BigDecimal patrimonioTotal = patrimonio.add(resultadoEjercicio);

            Map<String, BigDecimal> map = new LinkedHashMap<>();
            map.put("ingresos", ingresos);
            map.put("costos", costos);
            map.put("gastos", gastos);
            map.put("resultado", resultado);
            map.put("activos", activos);
            map.put("pasivos", pasivos);
            map.put("patrimonio", patrimonioTotal);
            map.put("pasivoPatrimonio", pasivos.add(patrimonioTotal));
            return map;
        });
    }

    public record LineaIva(LocalDate fecha, String tipo, String nit, String tercero, String codigo,
                           BigDecimal base, BigDecimal iva, BigDecimal total) {
    }

    public record LineaAuxiliar(LocalDate fecha, String folio, String cuenta, String dimension,
                                String concepto, BigDecimal debe, BigDecimal haber,
                                BigDecimal saldo) {
    }

    /**
     * Genera el libro de IVA según el tipo solicitado.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @param tipoLibro tipo de libro a emitir
     * @return líneas del libro de IVA
     */
    public List<LineaIva> libroIva(LocalDate desde, LocalDate hasta, String tipoLibro) {
        auth.exigirContador();
        String filtro = switch (tipoLibro) {
            case "COMPRAS" -> "AND d.tipo IN ('CCF','FACTURA','NC','ND') AND de.tipo_evento = 'COMPRA'";
            case "VENTAS_CCF" -> "AND d.tipo = 'CCF' AND de.tipo_evento IN ('VENTA','ANTICIPO')";
            default -> "AND d.tipo IN ('FACTURA','NC','ND') AND de.tipo_evento IN ('VENTA','ANTICIPO')";
        };
        return jdbc.inTransaction(conn -> {
            String sql = """
                    SELECT d.fecha_emision, d.tipo, COALESCE(t.nit, d.receptor_nit, d.emisor_nit) AS nit,
                           COALESCE(t.razon_social, '') AS tercero, d.codigo_generacion,
                           d.base_gravada, d.iva, d.total
                    FROM documento d
                    LEFT JOIN tercero t ON t.id = d.tercero_id
                    LEFT JOIN documento_evento de ON de.documento_id = d.id
                    WHERE d.fecha_emision BETWEEN ? AND ? AND d.estado <> 'ANULADO'
                    """ + filtro + " ORDER BY d.fecha_emision, d.codigo_generacion";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaIva> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new LineaIva(rs.getObject(1, LocalDate.class), rs.getString(2),
                                rs.getString(3), rs.getString(4), rs.getString(5),
                                rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al generar libro de IVA", e);
            }
        });
    }

    /**
     * Genera un auxiliar contable filtrado por tipo.
     *
     * @param tipo tipo de auxiliar
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return líneas del auxiliar
     */
    public List<LineaAuxiliar> auxiliar(String tipo, LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        String dimension = switch (tipo) {
            case "CLIENTES", "PROVEEDORES" -> "d.auxiliar";
            case "CENTRO_COSTO" -> "d.centro_costo";
            case "ORDEN" -> "p.documento_soporte";
            default -> "d.auxiliar";
        };
        String filtroCuenta = switch (tipo) {
            case "CLIENTES" -> "AND d.codigo_cuenta LIKE '1102%'";
            case "PROVEEDORES" -> "AND d.codigo_cuenta LIKE '2101%'";
            case "INVENTARIO" -> "AND d.codigo_cuenta LIKE '1107%'";
            case "ORDEN" -> "AND d.codigo_cuenta LIKE '1105%'";
            case "CENTRO_COSTO" -> "AND d.centro_costo IS NOT NULL";
            default -> "";
        };
        return jdbc.inTransaction(conn -> {
            Map<String, BigDecimal> saldos = new LinkedHashMap<>();
            String sqlInicial = """
                    SELECT COALESCE(%s,''), COALESCE(SUM(d.debe - d.haber),0)
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable < ?
                    %s
                    GROUP BY %s
                    """.formatted(dimension, filtroCuenta, dimension);
            try (PreparedStatement ps = conn.prepareStatement(sqlInicial)) {
                ps.setObject(1, desde);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        saldos.put(rs.getString(1), rs.getBigDecimal(2));
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al calcular saldo inicial del auxiliar", e);
            }
            String sql = """
                    SELECT p.fecha_contable, p.folio, d.codigo_cuenta, COALESCE(%s,''), p.concepto, d.debe, d.haber
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                    %s
                    ORDER BY p.fecha_contable, p.folio, d.linea
                    """.formatted(dimension, filtroCuenta);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    List<LineaAuxiliar> list = new ArrayList<>();
                    while (rs.next()) {
                        String dim = rs.getString(4);
                        BigDecimal saldo = saldos.getOrDefault(dim, BigDecimal.ZERO)
                                .add(rs.getBigDecimal(6)).subtract(rs.getBigDecimal(7));
                        saldos.put(dim, saldo);
                        list.add(new LineaAuxiliar(rs.getObject(1, LocalDate.class), rs.getString(2),
                                rs.getString(3), dim, rs.getString(5),
                                rs.getBigDecimal(6), rs.getBigDecimal(7), saldo));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al generar auxiliar", e);
            }
        });
    }
}
