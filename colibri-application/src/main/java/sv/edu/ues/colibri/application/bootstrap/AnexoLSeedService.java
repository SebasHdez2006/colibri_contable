package sv.edu.ues.colibri.application.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import sv.edu.ues.colibri.application.support.JsonSupport;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
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
            run(c, "INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, "
                            + "costo_unitario, unidades_disponibles, fecha_liberacion, partida_id) VALUES (?, "
                            + "(SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id "
                            + "WHERE p.codigo = 'COL-INV' AND pv.version_label = ?), ?, ?, ?, ?, ?, ?, ?)",
                    lote, l.get("version").asText(), l.get("codigo_lote").asText(),
                    l.get("unidades_estimadas").asInt(), dec(l.get("costo_total")), dec(l.get("costo_unitario")),
                    l.get("unidades_disponibles").asInt(), fecha(l.get("fecha_liberacion")),
                    l.hasNonNull("folio") ? partidas.get(l.get("folio").asText()) : null);
            for (JsonNode x : l.get("licencias")) {
                boolean entregada = "ENTREGADA".equals(x.get("estado").asText());
                LocalDate entrega = x.hasNonNull("fecha") ? fecha(x.get("fecha")) : null;
                run(c, "INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, "
                                + "fecha_entrega, fecha_activacion, costo_unitario) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)",
                        UUID.randomUUID(), lote, x.get("numero").asInt(), x.get("estado").asText(),
                        entregada ? terceros.get(x.get("cliente").asText()) : null,
                        entregada ? documentos.get(x.get("documento").asText()) : null,
                        entrega, entrega, dec(x.get("costo_unitario")));
            }
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
        for (JsonNode a : seed.get("activos_intangibles")) {
            run(c, "INSERT INTO activo_intangible (id, codigo, nombre, cuenta_activo, cuenta_amort_acum, cuenta_gasto, "
                            + "costo, vida_meses, fecha_uso, valor_residual, amortizacion_acum, activo) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)", UUID.randomUUID(),
                    a.get("codigo").asText(), a.get("nombre").asText(), a.get("cuenta_activo").asText(),
                    a.get("cuenta_amort_acum").asText(), a.get("cuenta_gasto").asText(), dec(a.get("costo")),
                    a.get("vida_meses").asInt(), fecha(a.get("fecha_uso")), dec(a.get("valor_residual")),
                    dec(a.get("amortizacion_acum")));
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
