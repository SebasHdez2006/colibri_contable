package sv.edu.ues.colibri.application.panel;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas agregadas para el tablero de la pantalla de inicio.
 * Combina indicadores contables (partidas contabilizadas) con indicadores
 * operativos (licencias, órdenes, planes, documentos) para ofrecer una vista
 * gerencial sin duplicar la lógica de los reportes formales.
 */
public class PanelInicioService {

    private final JdbcSupport jdbc;
    private final AuthService auth;

    /**
     * Crea el servicio de panel de inicio.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     */
    public PanelInicioService(JdbcSupport jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    /** Resultado completo del tablero para un período. */
    public record ResumenPanel(
            LocalDate periodoInicio,
            LocalDate periodoFin,
            BigDecimal ingresosMes,
            BigDecimal ingresosMesAnterior,
            BigDecimal variacionIngresos,
            BigDecimal utilidadPeriodo,
            BigDecimal carteraClientes,
            BigDecimal dtesPendientes,
            long documentosPendientesCobro,
            ProductoTop productoTop,
            int usuariosSaasMes,
            List<SaasPlan> saasPorPlan,
            List<OrdenComparativo> ordenes,
            List<CifComparativo> cif,
            List<PlanTarifa> planes,
            List<ClienteTop> clientesTop,
            List<Alerta> alertas,
            List<PuntoMes> serieMensual) {
    }

    /** Punto mensual de ingresos y egresos contabilizados para las gráficas. */
    public record PuntoMes(int anio, int mes, BigDecimal ingresos, BigDecimal egresos) {
        public String etiqueta() {
            return "%04d-%02d".formatted(anio, mes);
        }
    }

    /** Producto con mayor volumen de unidades vendidas. */
    public record ProductoTop(String nombre, long unidades, BigDecimal ingresos) {
    }

    public record SaasPlan(String plan, int usuariosMes) {
    }

    public record CifComparativo(String centro, BigDecimal presupuestado,
                                 BigDecimal aplicado, BigDecimal variacion) {
    }

    /** Comparación presupuesto vs. costo real de una orden de servicio. */
    public record OrdenComparativo(String codigo, String descripcion, BigDecimal presupuesto,
            BigDecimal costoReal, BigDecimal variacionPct, String estado) {
    }

    /** Tarifa vigente de un plan comercial activo. */
    public record PlanTarifa(String codigo, String nombre, String modalidad,
            BigDecimal tarifaBase, String tipoFacturacion) {
    }

    /** Cliente con mayor facturación acumulada en el período. */
    public record ClienteTop(String nombre, BigDecimal facturado, long documentos) {
    }

    /** Alerta operativa para el tablero. */
    public record Alerta(String severidad, String mensaje) {
    }

    /**
     * Calcula el resumen gerencial del período indicado.
     *
     * @param periodo período activo; si es {@code null} se devuelve un resumen vacío
     * @return métricas agregadas del tablero
     */
    public ResumenPanel resumen(Periodo periodo) {
        auth.exigirContador();
        if (periodo == null) {
            return new ResumenPanel(null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, null, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        LocalDate inicio = periodo.getFechaInicio();
        LocalDate fin = periodo.getFechaFin();
        LocalDate inicioAnterior = inicio.minusMonths(1).withDayOfMonth(1);
        LocalDate finAnterior = inicio.minusDays(1);

        return jdbc.inTransaction(conn -> new ResumenPanel(
                inicio, fin,
                ingresos(conn, inicio, fin),
                ingresos(conn, inicioAnterior, finAnterior),
                variacionPct(ingresos(conn, inicio, fin), ingresos(conn, inicioAnterior, finAnterior)),
                utilidad(conn, inicio, fin),
                saldoCuenta(conn, "1102%", fin),
                dtesPendientes(conn, inicio, fin),
                documentosPendientes(conn),
                productoTop(conn, inicio, fin),
                usuariosSaas(conn, inicio),
                saasPorPlan(conn, inicio),
                ordenes(conn),
                cif(conn, periodo),
                planes(conn),
                clientesTop(conn, inicio, fin),
                alertas(conn, periodo),
                serieMensual(conn, inicio.minusMonths(5).withDayOfMonth(1), fin)));
    }

    /**
     * Serie mensual de ingresos y egresos entre dos fechas, usada por la
     * gráfica de barras cuando el usuario elige el rango de años a comparar.
     * Los meses sin movimiento se rellenan con cero para que el eje del
     * rango elegido sea continuo y comparable mes a mes.
     *
     * @param desde inicio del rango (inclusive)
     * @param hasta fin del rango (inclusive)
     * @return un punto por cada mes del rango, ordenado cronológicamente
     */
    public List<PuntoMes> serieMensualEntre(LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            return List.of();
        }
        LocalDate ini = desde.withDayOfMonth(1);
        LocalDate fin = hasta.withDayOfMonth(1);
        List<PuntoMes> conDatos = jdbc.inTransaction(conn -> serieMensual(conn, ini, fin));
        Map<String, PuntoMes> porMes = new HashMap<>();
        for (PuntoMes p : conDatos) {
            porMes.put(p.etiqueta(), p);
        }
        List<PuntoMes> serie = new ArrayList<>();
        for (LocalDate mes = ini; !mes.isAfter(fin); mes = mes.plusMonths(1)) {
            PuntoMes punto = porMes.get("%04d-%02d".formatted(mes.getYear(), mes.getMonthValue()));
            serie.add(punto != null ? punto
                    : new PuntoMes(mes.getYear(), mes.getMonthValue(), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return serie;
    }

    /**
     * Años con al menos una partida contabilizada, para poblar los selectores
     * de rango de la gráfica de barras del tablero.
     */
    public List<Integer> aniosConMovimientos() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT DISTINCT EXTRACT(YEAR FROM p.fecha_contable)::int AS anio
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    WHERE p.estado = 'CONTABILIZADA'
                    ORDER BY anio
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<Integer> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(rs.getInt("anio"));
                }
                return lista;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar años con movimientos", e);
            }
        });
    }

    private List<PuntoMes> serieMensual(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT EXTRACT(YEAR FROM p.fecha_contable)::int AS anio,
                       EXTRACT(MONTH FROM p.fecha_contable)::int AS mes,
                       COALESCE(SUM(CASE WHEN d.codigo_cuenta LIKE '4%'
                            THEN d.haber - d.debe ELSE 0 END), 0) AS ingresos,
                       COALESCE(SUM(CASE WHEN d.codigo_cuenta LIKE '5%'
                            OR d.codigo_cuenta LIKE '6%' OR d.codigo_cuenta LIKE '7%'
                            THEN d.debe - d.haber ELSE 0 END), 0) AS egresos
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE p.estado = 'CONTABILIZADA'
                  AND p.fecha_contable BETWEEN ? AND ?
                GROUP BY 1, 2
                ORDER BY 1, 2
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                List<PuntoMes> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new PuntoMes(rs.getInt("anio"), rs.getInt("mes"),
                            rs.getBigDecimal("ingresos"), rs.getBigDecimal("egresos")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular la serie mensual", e);
        }
    }

    private BigDecimal ingresos(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(d.haber - d.debe), 0) AS ingreso
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE p.estado = 'CONTABILIZADA'
                  AND p.fecha_contable BETWEEN ? AND ?
                  AND d.codigo_cuenta LIKE '4%'
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("ingreso") : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular ingresos del período", e);
        }
    }

    private BigDecimal utilidad(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(
                    CASE WHEN d.codigo_cuenta LIKE '4%' THEN d.haber - d.debe
                         ELSE -(d.debe - d.haber) END), 0) AS utilidad
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE p.estado = 'CONTABILIZADA'
                  AND p.fecha_contable BETWEEN ? AND ?
                  AND (d.codigo_cuenta LIKE '4%' OR d.codigo_cuenta LIKE '5%'
                       OR d.codigo_cuenta LIKE '6%' OR d.codigo_cuenta LIKE '7%')
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("utilidad") : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular utilidad del período", e);
        }
    }

    private BigDecimal saldoCuenta(Connection conn, String patron, LocalDate corte) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(d.debe - d.haber), 0) AS saldo
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE p.estado = 'CONTABILIZADA'
                  AND p.fecha_contable <= ?
                  AND d.codigo_cuenta LIKE ?
                """)) {
            ps.setDate(1, Date.valueOf(corte));
            ps.setString(2, patron);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("saldo") : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar saldo de cartera", e);
        }
    }

    private long documentosPendientes(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) AS pendientes
                FROM documento
                WHERE estado = 'CONTABILIZADO'
                  AND tipo IN ('CCF', 'FACTURA')
                  AND total > COALESCE((SELECT SUM(a.importe) FROM aplicacion_pago a
                                       WHERE a.documento_pago_id = documento.id), 0)
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("pendientes") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al contar documentos pendientes", e);
        }
    }

    private BigDecimal dtesPendientes(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(d.total - COALESCE(a.aplicado, 0)), 0)
                FROM documento d
                LEFT JOIN (
                    SELECT documento_pago_id, SUM(importe) aplicado
                    FROM aplicacion_pago GROUP BY documento_pago_id
                ) a ON a.documento_pago_id = d.id
                WHERE d.estado = 'CONTABILIZADO'
                  AND d.tipo IN ('CCF', 'FACTURA')
                  AND d.fecha_emision BETWEEN ? AND ?
                  AND d.total > COALESCE(a.aplicado, 0)
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular DTEs pendientes", e);
        }
    }

    private ProductoTop productoTop(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT pr.nombre, COUNT(*) AS unidades,
                       COALESCE(SUM(doc.total), 0) AS ingresos
                FROM licencia li
                JOIN lote_licencia lo ON lo.id = li.lote_id
                JOIN producto_version pv ON pv.id = lo.version_id
                JOIN producto pr ON pr.id = pv.producto_id
                LEFT JOIN documento doc ON doc.id = li.documento_id
                WHERE li.estado = 'ENTREGADA'
                  AND doc.fecha_emision BETWEEN ? AND ?
                GROUP BY pr.nombre
                ORDER BY unidades DESC, ingresos DESC
                LIMIT 1
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
               if (!rs.next()) {
                   return null;
               }
               return new ProductoTop(rs.getString("nombre"), rs.getLong("unidades"),
                       rs.getBigDecimal("ingresos"));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular producto más vendido", e);
        }
    }

    private int usuariosSaas(Connection conn, LocalDate inicioPeriodo) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(usuarios_mes), 0) AS usuarios
                FROM saas_usuario_mes
                WHERE periodo = ?
                """)) {
            ps.setDate(1, Date.valueOf(inicioPeriodo.withDayOfMonth(1)));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("usuarios") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar usuarios SaaS", e);
        }
    }

    private List<SaasPlan> saasPorPlan(Connection conn, LocalDate inicioPeriodo) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(ps.nombre, 'Plan no identificado') plan, SUM(s.usuarios_mes) usuarios
                FROM saas_usuario_mes s LEFT JOIN plan_servicio ps ON ps.id = s.plan_id
                WHERE s.periodo = ? GROUP BY ps.nombre ORDER BY usuarios DESC
                """)) {
            ps.setDate(1, Date.valueOf(inicioPeriodo.withDayOfMonth(1)));
            try (ResultSet rs = ps.executeQuery()) {
                List<SaasPlan> lista = new ArrayList<>();
                while (rs.next()) lista.add(new SaasPlan(rs.getString("plan"), rs.getInt("usuarios")));
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al desglosar usuarios SaaS", e);
        }
    }

    private List<CifComparativo> cif(Connection conn, Periodo periodo) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT p.centro_codigo, p.cif_estimado presupuestado,
                       COALESCE(SUM(a.importe), 0) aplicado
                FROM cif_presupuesto_periodo p
                LEFT JOIN cif_aplicacion_orden a
                  ON a.periodo_id = p.periodo_id AND a.centro_codigo = p.centro_codigo
                WHERE p.periodo_id = ?
                GROUP BY p.centro_codigo, p.cif_estimado ORDER BY p.centro_codigo
                """)) {
            ps.setObject(1, periodo.getId());
            try (ResultSet rs = ps.executeQuery()) {
                List<CifComparativo> lista = new ArrayList<>();
                while (rs.next()) {
                    BigDecimal presupuestado = rs.getBigDecimal("presupuestado");
                    BigDecimal aplicado = rs.getBigDecimal("aplicado");
                    lista.add(new CifComparativo(rs.getString("centro_codigo"), presupuestado,
                            aplicado, aplicado.subtract(presupuestado)));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar CIF aplicado y presupuestado", e);
        }
    }

    private List<OrdenComparativo> ordenes(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.codigo, o.descripcion, o.presupuesto, o.estado,
                       COALESCE((SELECT SUM(c.importe) FROM orden_consumo c
                                 WHERE c.orden_id = o.id), 0) AS costo_real
                FROM orden_servicio o
                WHERE o.estado <> 'CANCELADA'
                ORDER BY o.codigo
                """); ResultSet rs = ps.executeQuery()) {
            List<OrdenComparativo> lista = new ArrayList<>();
            while (rs.next()) {
                BigDecimal presupuesto = rs.getBigDecimal("presupuesto");
                BigDecimal real = rs.getBigDecimal("costo_real");
                lista.add(new OrdenComparativo(rs.getString("codigo"), rs.getString("descripcion"),
                        presupuesto, real, variacionPct(real, presupuesto),
                        rs.getString("estado")));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al comparar órdenes", e);
        }
    }

    private List<PlanTarifa> planes(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT codigo, nombre, modalidad, tarifa_base, tipo_facturacion
                FROM plan_servicio
                WHERE activo = TRUE
                ORDER BY modalidad, codigo
                """); ResultSet rs = ps.executeQuery()) {
            List<PlanTarifa> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new PlanTarifa(rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("modalidad"), rs.getBigDecimal("tarifa_base"),
                        rs.getString("tipo_facturacion")));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar planes vigentes", e);
        }
    }

    private List<ClienteTop> clientesTop(Connection conn, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT t.id AS tercero_id, COALESCE(t.razon_social, 'Sin tercero') AS cliente,
                       COALESCE(SUM(d.total), 0) AS facturado,
                       COUNT(*) AS documentos
                FROM documento d
                LEFT JOIN tercero t ON t.id = d.tercero_id
                WHERE d.estado <> 'ANULADO'
                  AND d.tipo IN ('CCF', 'FACTURA')
                  AND d.fecha_emision BETWEEN ? AND ?
                GROUP BY t.id, t.razon_social
                ORDER BY facturado DESC
                LIMIT 5
                """)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                List<ClienteTop> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new ClienteTop(rs.getString("cliente"),
                            rs.getBigDecimal("facturado"), rs.getLong("documentos")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular clientes principales", e);
        }
    }

    private List<Alerta> alertas(Connection conn, Periodo periodo) {
        List<Alerta> lista = new ArrayList<>();
        borradoresAntiguos(conn, lista);
        lotesSinMovimiento(conn, lista);
        long diasCierre = ChronoUnit.DAYS.between(LocalDate.now(), periodo.getFechaFin());
        if (diasCierre >= 0 && diasCierre <= 7) {
            lista.add(new Alerta("AVISO",
                    "El período cierra en " + diasCierre + " día(s) — prepara el cierre mensual."));
        } else if (diasCierre < 0) {
            lista.add(new Alerta("ALTA",
                    "El período venció hace " + Math.abs(diasCierre) + " día(s) y sigue abierto."));
        }
        ordenesSobrecosto(conn, lista);
        return lista;
    }

    private void borradoresAntiguos(Connection conn, List<Alerta> lista) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) AS pendientes
                FROM partida
                WHERE estado = 'BORRADOR'
                  AND creado_en < CURRENT_TIMESTAMP - INTERVAL '5 days'
                """); ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getLong("pendientes") > 0) {
                lista.add(new Alerta("AVISO",
                        rs.getLong("pendientes") + " borrador(es) con más de 5 días sin contabilizar."));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al revisar borradores", e);
        }
    }

    private void lotesSinMovimiento(Connection conn, List<Alerta> lista) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) AS lotes
                FROM lote_licencia lo
                WHERE lo.fecha_liberacion IS NOT NULL
                  AND lo.unidades_disponibles = lo.unidades_estimadas
                """); ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getLong("lotes") > 0) {
                lista.add(new Alerta("INFO",
                        rs.getLong("lotes") + " lote(s) liberado(s) sin licencias entregadas."));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al revisar lotes", e);
        }
    }

    private void ordenesSobrecosto(Connection conn, List<Alerta> lista) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.codigo
                FROM orden_servicio o
                WHERE o.estado IN ('ABIERTA', 'EN_PROCESO')
                  AND COALESCE((SELECT SUM(c.importe) FROM orden_consumo c
                                WHERE c.orden_id = o.id), 0) > o.presupuesto
                """); ResultSet rs = ps.executeQuery()) {
            List<String> codigos = new ArrayList<>();
            while (rs.next()) {
                codigos.add(rs.getString("codigo"));
            }
            if (!codigos.isEmpty()) {
                lista.add(new Alerta("ALTA",
                        "Orden(es) con sobrecosto sobre presupuesto: " + String.join(", ", codigos)));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al revisar sobrecostos de órdenes", e);
        }
    }

    private BigDecimal variacionPct(BigDecimal actual, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return actual.subtract(base)
                .divide(base, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
