package sv.edu.ues.colibri.application.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import sv.edu.ues.colibri.application.support.JsonSupport;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Siembra el ejercicio histórico del Anexo L (apertura al 30-sep, diario de
 * octubre de 2026 y auxiliares) a partir de {@code /seed/anexo_l.json}.
 *
 * Es el equivalente Java de {@code scripts/db/seed_anexo_l.sql}. Parte de una
 * base sin partidas (ver {@code scripts/db/reset_total.sql}) y es idempotente.
 */
public final class AnexoLSeedService {

    static final String RECURSO = "/seed/anexo_l.json";

    private final JdbcSupport jdbc;

    /**
     * Crea el servicio de siembra del Anexo L.
     *
     * @param jdbc soporte transaccional
     */
    public AnexoLSeedService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Carga el Anexo L si todavía no fue sembrado.
     *
     * @throws IllegalStateException si existen partidas ajenas al seed
     */
    public void asegurarDatosIniciales() {
        JsonNode raiz = leer();
        jdbc.runInTransaction(conn -> sembrar(conn, raiz));
    }

    private JsonNode leer() {
        try (InputStream in = AnexoLSeedService.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No se encontró " + RECURSO);
            }
            return JsonSupport.mapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RECURSO, e);
        }
    }

    private void sembrar(Connection c, JsonNode raiz) throws SQLException {
        String prefijo = raiz.at("/meta/prefijo_evento").asText();
        if (existe(c, "SELECT 1 FROM evento_contable WHERE clave_unica LIKE ? LIMIT 1", prefijo + "%")) {
            return;
        }
        if (existe(c, "SELECT 1 FROM partida LIMIT 1")) {
            throw new IllegalStateException("La base ya contiene partidas; ejecute scripts/db/reset_total.sql "
                    + "antes de sembrar el Anexo L.");
        }
        JsonNode seed = raiz.get("seed");
        int anio = raiz.at("/ejercicio/anio").asInt();
        UUID contador = uuid(c, "SELECT id FROM contador WHERE usuario = 'contador'");
        Map<Integer, UUID> periodos = new HashMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            periodos.put(mes, uuid(c, "SELECT p.id FROM periodo p JOIN ejercicio e ON e.id = p.ejercicio_id "
                    + "WHERE e.anio = ? AND p.mes = ?", anio, mes));
        }
        run(c, "UPDATE catalogo_version SET aprobado = TRUE, aprobado_en = CURRENT_TIMESTAMP, aprobado_por = ? "
                + "WHERE version = 1", contador);

        Map<String, UUID> terceros = new HashMap<>();
        for (JsonNode t : seed.get("terceros")) {
            UUID id = UUID.randomUUID();
            terceros.put(t.get("nit").asText(), id);
            run(c, "INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, TRUE)", id, t.get("nit").asText(),
                    t.get("razon_social").asText(), t.get("razon_social").asText(),
                    t.get("es_cliente").asBoolean(), t.get("es_proveedor").asBoolean());
        }
        Map<String, UUID> documentos = new HashMap<>();
        for (JsonNode d : seed.get("documentos")) {
            UUID id = UUID.randomUUID();
            String codigo = d.get("codigo").asText();
            documentos.put(codigo, id);
            run(c, "INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, "
                            + "tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, "
                            + "es_simulacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, FALSE, 'CONTABILIZADO', TRUE)",
                    id, d.get("tipo").asText(), d.get("emisor_nit").asText(), "SIM-" + codigo, codigo,
                    d.get("receptor_nit").asText(), terceros.get(d.get("tercero_nit").asText()),
                    fecha(d.get("fecha")), dec(d.get("base_gravada")), dec(d.get("iva")), dec(d.get("total")));
        }
        for (JsonNode v : seed.get("producto_versiones")) {
            run(c, "INSERT INTO producto_version (id, producto_id, version_label, estado) "
                            + "VALUES (?, (SELECT id FROM producto WHERE codigo = ?), ?, ?)",
                    UUID.randomUUID(), v.get("producto").asText(), v.get("version").asText(),
                    v.get("estado").asText());
        }

        Map<String, UUID> partidas = new HashMap<>();
        Map<String, LocalDate> fechas = new HashMap<>();
        for (JsonNode p : seed.get("partidas")) {
            String folio = p.get("folio").asText();
            LocalDate fecha = fecha(p.get("fecha"));
            UUID evento = UUID.randomUUID();
            UUID partida = UUID.randomUUID();
            partidas.put(folio, partida);
            fechas.put(folio, fecha);
            String concepto = recortar(p.get("concepto").asText(), 500);
            run(c, "INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES (?, ?, ?)",
                    evento, prefijo + p.get("clave").asText(), concepto);
            run(c, "INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, "
                            + "documento_soporte, evento_id, creado_por) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'CONTABILIZADA', ?, ?, ?, ?)",
                    partida, p.get("tipo").asText(), folio, fecha, periodos.get(fecha.getMonthValue()), concepto,
                    p.get("origen").asText(), p.get("soporte").asText(), evento, contador);
            int linea = 1;
            for (JsonNode l : p.get("lineas")) {
                run(c, "INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, "
                                + "debe, haber) VALUES (?, ?, ?, ?, ?, NULL, ?, ?)",
                        UUID.randomUUID(), partida, linea++, l.get("cuenta").asText(),
                        l.hasNonNull("auxiliar") ? l.get("auxiliar").asText() : null,
                        dec(l.get("debe")), dec(l.get("haber")));
            }
            run(c, "UPDATE evento_contable SET partida_contabilizada_id = ? WHERE id = ?", partida, evento);
            if (p.hasNonNull("documento")) {
                run(c, "INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) "
                                + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), documentos.get(p.get("documento").asText()),
                        evento, p.get("tipo_evento").asText(), partida);
            }
        }
        for (JsonNode a : seed.get("aplicaciones_pago")) {
            run(c, "INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, "
                            + "fecha, concepto) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                    documentos.get(a.get("origen").asText()), documentos.get(a.get("pago").asText()),
                    partidas.get(a.get("folio").asText()), dec(a.get("importe")), fecha(a.get("fecha")),
                    a.get("concepto").asText());
        }
        for (JsonNode f : seed.get("folio_secuencia")) {
            run(c, "INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (?, ?, ?) "
                            + "ON CONFLICT (anio, tipo) DO UPDATE SET ultimo = GREATEST(folio_secuencia.ultimo, EXCLUDED.ultimo)",
                    f.get("anio").asInt(), f.get("tipo").asText(), f.get("ultimo").asLong());
        }

        for (JsonNode l : seed.get("lotes")) {
            UUID lote = UUID.randomUUID();
            LocalDate liberacion = fecha(l.get("fecha_liberacion"));
            run(c, "INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, "
                            + "costo_unitario, unidades_disponibles, fecha_liberacion, partida_id) VALUES (?, "
                            + "(SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id "
                            + "WHERE p.codigo = 'COL-INV' AND pv.version_label = ?), ?, ?, ?, ?, ?, ?, ?)",
                    lote, l.get("version").asText(), l.get("codigo_lote").asText(),
                    l.get("unidades_estimadas").asInt(), dec(l.get("costo_total")), dec(l.get("costo_unitario")),
                    l.get("unidades_disponibles").asInt(), liberacion,
                    l.hasNonNull("folio") ? partidas.get(l.get("folio").asText()) : null);
            List<LicenciaKardex> unidades = new ArrayList<>();
            for (JsonNode x : l.get("licencias")) {
                UUID licenciaId = UUID.randomUUID();
                boolean entregada = "ENTREGADA".equals(x.get("estado").asText());
                LocalDate entrega = x.hasNonNull("fecha_entrega") ? fecha(x.get("fecha_entrega"))
                        : (x.hasNonNull("fecha") ? fecha(x.get("fecha")) : null);
                BigDecimal costo = dec(x.get("costo_unitario"));
                run(c, "INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, "
                                + "fecha_entrega, fecha_activacion, costo_unitario) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)",
                        licenciaId, lote, x.get("numero").asInt(), x.get("estado").asText(),
                        entregada ? terceros.get(x.get("cliente").asText()) : null,
                        entregada ? documentos.get(x.get("documento").asText()) : null,
                        entrega, entrega, costo);
                unidades.add(new LicenciaKardex(licenciaId, x.get("numero").asInt(),
                        x.get("estado").asText(),
                        entrega != null ? entrega : liberacion, costo));
            }
            sembrarKardexLote(c, lote, liberacion, dec(l.get("costo_total")),
                    dec(l.get("costo_unitario")),
                    l.hasNonNull("folio") ? partidas.get(l.get("folio").asText()) : null,
                    unidades.size(), contador, unidades);
        }
        for (JsonNode o : seed.get("ordenes")) {
            UUID orden = UUID.randomUUID();
            run(c, "INSERT INTO orden_servicio (id, codigo, cliente_id, descripcion, presupuesto, precio_venta, estado) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", orden, o.get("codigo").asText(),
                    terceros.get(o.get("cliente").asText()), o.get("descripcion").asText(),
                    dec(o.get("presupuesto")), dec(o.get("precio_venta")), o.get("estado").asText());
            String folio = o.get("folio_consumos").asText();
            LocalDate fecha = fechas.get(folio);
            for (JsonNode k : o.get("consumos")) {
                run(c, "INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, "
                                + "periodo_id, fecha, partida_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), orden, k.get("tipo").asText(), k.get("descripcion").asText(),
                        dec(k.get("cantidad")), dec(k.get("tarifa")), dec(k.get("importe")),
                        periodos.get(fecha.getMonthValue()), fecha, partidas.get(folio));
            }
        }
        Map<String, UUID> activos = new HashMap<>();
        for (JsonNode a : seed.get("activos_fijos")) {
            UUID id = UUID.randomUUID();
            activos.put(a.get("codigo").asText(), id);
            run(c, "INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, "
                            + "vida_meses, fecha_uso, valor_residual, depreciacion_acum, activo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)", id, a.get("codigo").asText(),
                    a.get("nombre").asText(), a.get("cuenta_activo").asText(), a.get("cuenta_dep_acum").asText(),
                    a.get("cuenta_gasto").asText(), dec(a.get("costo")), a.get("vida_meses").asInt(),
                    fecha(a.get("fecha_uso")), dec(a.get("valor_residual")), dec(a.get("depreciacion_acum")));
        }
        for (JsonNode q : seed.get("cuotas_depreciacion")) {
            run(c, "INSERT INTO depreciacion_cuota (id, activo_id, periodo_id, importe, partida_id) "
                            + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), activos.get(q.get("activo").asText()),
                    periodos.get(q.get("periodo_mes").asInt()), dec(q.get("importe")),
                    partidas.get(q.get("folio").asText()));
        }
        Map<String, UUID> intangibles = new HashMap<>();
        for (JsonNode a : seed.get("activos_intangibles")) {
            UUID id = UUID.randomUUID();
            intangibles.put(a.get("codigo").asText(), id);
            run(c, "INSERT INTO activo_intangible (id, codigo, nombre, cuenta_activo, cuenta_amort_acum, cuenta_gasto, "
                            + "costo, vida_meses, fecha_uso, valor_residual, amortizacion_acum, activo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)", id,
                    a.get("codigo").asText(), a.get("nombre").asText(), a.get("cuenta_activo").asText(),
                    a.get("cuenta_amort_acum").asText(), a.get("cuenta_gasto").asText(), dec(a.get("costo")),
                    a.get("vida_meses").asInt(), fecha(a.get("fecha_uso")), dec(a.get("valor_residual")),
                    dec(a.get("amortizacion_acum")));
        }
        for (JsonNode q : seed.get("cuotas_amortizacion")) {
            run(c, "INSERT INTO amortizacion_cuota (id, activo_id, periodo_id, importe, partida_id) "
                            + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), intangibles.get(q.get("activo").asText()),
                    periodos.get(q.get("periodo_mes").asInt()), dec(q.get("importe")),
                    partidas.get(q.get("folio").asText()));
        }
        for (JsonNode e : seed.get("estimaciones_cartera")) {
            run(c, "INSERT INTO estimacion_cartera_cuota (id, periodo_id, base_estimacion, porcentaje, importe, "
                            + "partida_id) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                    periodos.get(e.get("periodo_mes").asInt()), dec(e.get("base_estimacion")),
                    dec(e.get("porcentaje")), dec(e.get("importe")), partidas.get(e.get("folio").asText()));
        }
        for (JsonNode u : seed.get("saas_usuario_mes")) {
            run(c, "INSERT INTO saas_usuario_mes (plan_id, periodo, usuarios_mes) VALUES "
                            + "((SELECT id FROM plan_servicio WHERE codigo = ?), ?, ?)",
                    u.get("plan").asText(), LocalDate.parse(u.get("periodo").asText()),
                    u.get("usuarios_mes").asInt());
        }
        for (JsonNode p : seed.get("planillas")) {
            run(c, "INSERT INTO planilla_periodo (id, periodo_id, fecha_contable, empleados, salario_nominal, "
                            + "tasa_isss_laboral, tasa_previsional_laboral, isr_retenido, otras_deducciones, "
                            + "isss_laboral, isss_patronal, previsional_laboral, previsional_patronal, incaf, "
                            + "liquido_por_pagar, costo_patronal_total, tasa_isss_patronal, "
                            + "tasa_previsional_patronal, tasa_incaf, umbral_incaf_empleados, centro_costo, "
                            + "clave_evento, partida_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), periodos.get(p.get("periodo_mes").asInt()), fecha(p.get("fecha_contable")),
                    p.get("empleados").asInt(), dec(p.get("salario_nominal")), dec(p.get("tasa_isss_laboral")),
                    dec(p.get("tasa_previsional_laboral")), dec(p.get("isr_retenido")),
                    dec(p.get("otras_deducciones")), dec(p.get("isss_laboral")), dec(p.get("isss_patronal")),
                    dec(p.get("previsional_laboral")), dec(p.get("previsional_patronal")), dec(p.get("incaf")),
                    dec(p.get("liquido_por_pagar")), dec(p.get("costo_patronal_total")),
                    dec(p.get("tasa_isss_patronal")), dec(p.get("tasa_previsional_patronal")),
                    dec(p.get("tasa_incaf")), p.get("umbral_incaf_empleados").asInt(),
                    p.get("centro_costo").asText(), prefijo + p.get("clave_evento").asText(),
                    partidas.get(p.get("folio").asText()));
        }
        for (JsonNode mes : raiz.at("/ejercicio/periodos_cerrados")) {
            run(c, "UPDATE periodo SET estado = 'CERRADO' WHERE id = ?", periodos.get(mes.asInt()));
        }
    }

    /**
     * Reconstruye el kárdex histórico de un lote semilla: la liberación suma
     * todas las unidades y cada licencia entregada, devuelta o revocada deja
     * su movimiento en orden cronológico. Las ventas del Anexo L son partidas
     * agregadas por lote, por lo que las entregas no llevan partida propia y
     * una devolución posterior no puede revertir el asiento original.
     */
    private static void sembrarKardexLote(Connection c, UUID loteId, LocalDate liberacion,
            BigDecimal costoTotal, BigDecimal costoUnitarioLote, UUID partidaLote, int unidadesLote,
            UUID contador, List<LicenciaKardex> unidades) throws SQLException {
        int saldoU = unidadesLote;
        BigDecimal saldoV = costoTotal.setScale(2, RoundingMode.HALF_UP);
        kardex(c, loteId, null, "LIBERACION", liberacion, unidadesLote, costoUnitarioLote,
                costoTotal.setScale(2, RoundingMode.HALF_UP), saldoU, saldoV, partidaLote,
                "Liberación de lote", contador);
        List<LicenciaKardex> movimientos = unidades.stream()
                .filter(u -> "ENTREGADA".equals(u.estado()) || "DEVUELTA".equals(u.estado())
                        || "REVOCADA".equals(u.estado()))
                .sorted(Comparator.comparing(LicenciaKardex::fecha)
                        .thenComparingInt(LicenciaKardex::numero))
                .toList();
        for (LicenciaKardex u : movimientos) {
            switch (u.estado()) {
                case "ENTREGADA" -> {
                    saldoU -= 1;
                    BigDecimal importe = u.costo().negate().setScale(2, RoundingMode.HALF_UP);
                    saldoV = saldoV.add(importe);
                    kardex(c, loteId, u.id(), "ENTREGA", u.fecha(), -1, u.costo(), importe,
                            saldoU, saldoV, null, null, contador);
                }
                case "DEVUELTA" -> {
                    saldoU += 1;
                    BigDecimal importe = u.costo().setScale(2, RoundingMode.HALF_UP);
                    saldoV = saldoV.add(importe);
                    kardex(c, loteId, u.id(), "DEVOLUCION", u.fecha(), 1, u.costo(), importe,
                            saldoU, saldoV, null, "Devolución histórica", contador);
                }
                case "REVOCADA" -> kardex(c, loteId, u.id(), "REVOCACION", u.fecha(), 0, u.costo(),
                        BigDecimal.ZERO.setScale(2), saldoU, saldoV, null,
                        "Revocación histórica", contador);
                default -> { }
            }
        }
    }

    private static void kardex(Connection c, UUID loteId, UUID licenciaId, String tipo,
            LocalDate fecha, int unidades, BigDecimal costoUnitario, BigDecimal importe,
            int saldoU, BigDecimal saldoV, UUID partidaId, String detalle, UUID contador)
            throws SQLException {
        run(c, "INSERT INTO licencia_movimiento (id, lote_id, licencia_id, tipo, fecha, unidades, "
                        + "costo_unitario, importe, saldo_unidades, saldo_valor, partida_id, detalle, "
                        + "creado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), loteId, licenciaId, tipo, fecha, unidades, costoUnitario, importe,
                saldoU, saldoV, partidaId, detalle, contador);
    }

    private record LicenciaKardex(UUID id, int numero, String estado, LocalDate fecha,
            BigDecimal costo) {
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static BigDecimal dec(JsonNode n) {
        return new BigDecimal(n.asText());
    }

    private static LocalDate fecha(JsonNode n) {
        return LocalDate.parse(n.asText());
    }

    private static void run(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static boolean existe(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static UUID uuid(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("El seed del Anexo L requiere datos previos: " + sql);
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
