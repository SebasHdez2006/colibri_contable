package sv.edu.ues.colibri.application.bootstrap;

import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Siembra los datos iniciales del caso contable demostrativo una sola vez.
 */
public final class CasoContableSeedService {


    private static final String PREFIJO_SEMILLA = "SEED-CASO-D-2026-";
    private static final String NIT_EMPRESA = "SIMULADO-NIT";
    private final JdbcSupport jdbc;
    private final PartidaRepository partidas = new PartidaRepository();

        /**
     * Crea el servicio de siembra del caso contable.
     *
     * @param jdbc soporte transaccional
     */
    public CasoContableSeedService(JdbcSupport jdbc) {

        this.jdbc = jdbc;
    }

        /**
     * Asegura que el caso demostrativo esté cargado en la base de datos.
     */
    public void asegurarDatosIniciales() {

        jdbc.runInTransaction(this::sembrar);
    }

    private void sembrar(Connection conn) throws SQLException {
        corregirDuplicadoOrden(conn);
        if (yaFueSembrado(conn)) {
            return;
        }

        UUID cliente = crearTercero(conn, "SIM-ANEXOD-CLI", "Cliente del caso integral D", true, false);
        UUID proveedorProduccion = crearTercero(conn, "SIM-ANEXOD-PROD",
                "Servicios de producción de software", false, true);
        UUID proveedorNube = crearTercero(conn, "SIM-ANEXOD-CLOUD",
                "Servicios de nube del caso", false, true);
        UUID proveedorAlquiler = crearTercero(conn, "SIM-ANEXOD-RENT",
                "Arrendador del caso", false, true);

        UUID contratoSaas = crearContrato(conn, cliente, "CASO-D-SAAS", "SAAS",
                "Tres meses de acceso SaaS; un mes servido y dos pendientes",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "150.00");
        UUID contratoLicencia = crearContrato(conn, cliente, "CASO-D-LICENCIA", "LICENCIA",
                "Licencia descargable de Colibrí Inventarios",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "240.00");
        UUID contratoProduccion = crearContrato(conn, proveedorProduccion, "CASO-D-PRODUCCION", "PROYECTO",
                "Servicios externos atribuibles a la versión 1.0",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), "500.00");
        UUID contratoNube = crearContrato(conn, proveedorNube, "CASO-D-NUBE", "SOPORTE",
                "Cobertura de nube por tres meses",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "90.00");
        UUID contratoAlquiler = crearContrato(conn, proveedorAlquiler, "CASO-D-ALQUILER", "SOPORTE",
                "Alquiler por tres meses",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "600.00");
        UUID contratoOrden = crearContrato(conn, cliente, "OS2026001-CONTRATO", "PROYECTO",
                "Aplicación de inventarios a medida",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "2500.00");
        UUID ordenId = ordenPrincipal(conn, cliente, contratoOrden);
        BigDecimal costoOrdenExistente = costoOrden(conn, ordenId, false);
        if (costoOrdenExistente.signum() != 0
            && costoOrdenExistente.compareTo(new BigDecimal("1800.00")) != 0) {
            throw new SQLException("OS2026001 ya contiene un costo distinto de $1,800.00; "
                + "se requiere revisar el auxiliar antes de sembrar el caso.");
        }
        boolean sembrarConsumosOrden = costoOrdenExistente.signum() == 0;

        UUID comprobanteAporte = crearDocumento(conn, "COMPROBANTE", "ACTA-SIMULADA", NIT_EMPRESA,
                null, null, LocalDate.of(2026, 1, 1), "0.00", "0.00", "0.00", "CASO-D-DOC-01");
        UUID facturaSaas = crearDocumento(conn, "CCF", NIT_EMPRESA, "SIM-ANEXOD-CLI", cliente,
                contratoSaas, LocalDate.of(2026, 1, 1), "150.00", "19.50", "169.50", "CASO-D-DOC-02");
        UUID facturaProduccion = crearDocumento(conn, "CCF", "SIM-ANEXOD-PROD", NIT_EMPRESA,
                proveedorProduccion, contratoProduccion, LocalDate.of(2026, 1, 5),
                "500.00", "65.00", "565.00", "CASO-D-DOC-03");
        UUID facturaLicencia = crearDocumento(conn, "CCF", NIT_EMPRESA, "SIM-ANEXOD-CLI", cliente,
                contratoLicencia, LocalDate.of(2026, 1, 10),
                "240.00", "31.20", "271.20", "CASO-D-DOC-04");
        UUID facturaNube = crearDocumento(conn, "CCF", "SIM-ANEXOD-CLOUD", NIT_EMPRESA,
                proveedorNube, contratoNube, LocalDate.of(2026, 1, 15),
                "90.00", "11.70", "101.70", "CASO-D-DOC-05");
        UUID facturaAlquiler = crearDocumento(conn, "CCF", "SIM-ANEXOD-RENT", NIT_EMPRESA,
                proveedorAlquiler, contratoAlquiler, LocalDate.of(2026, 1, 15),
                "600.00", "78.00", "678.00", "CASO-D-DOC-06");

        UUID enero = periodo(conn, 2026, 1);
        UUID febrero = periodo(conn, 2026, 2);
        UUID usuario = usuarioInicial(conn);

        UUID aporte = insertarPartida(conn, usuario, enero, TipoPartida.IN,
                LocalDate.of(2026, 1, 1), "Aporte de capital", "CASO-D-DOC-01", "01-APORTE",
                comprobanteAporte, "AJUSTE", List.of(linea("110102", "10000.00", "0"),
                        linea("310101", "0", "10000.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.IN,
                LocalDate.of(2026, 1, 1), "Cobro anticipado de tres meses SaaS", "CASO-D-DOC-02",
                "02-SAAS", facturaSaas, "ANTICIPO",
                List.of(linea("110102", "169.50", "0"), linea("210702", "0", "150.00"),
                        linea("210201", "0", "19.50")));
        UUID liberacion = insertarPartida(conn, usuario, enero, TipoPartida.EG,
                LocalDate.of(2026, 1, 5), "Servicios de producción de versión 1.0; lote de cinco licencias",
                "CASO-D-DOC-03", "03-PRODUCCION", facturaProduccion, "COMPRA",
                List.of(linea("110702", "500.00", "0"), linea("110301", "65.00", "0"),
                        linea("110102", "0", "565.00")));
        UUID ventaLicencia = insertarPartida(conn, usuario, enero, TipoPartida.DI,
                LocalDate.of(2026, 1, 10), "Entrega de licencia Colibrí Inventarios y costo de salida",
                "CASO-D-DOC-04", "04-LICENCIA", facturaLicencia, "VENTA",
                List.of(linea("110201", "271.20", "0"), linea("510302", "100.00", "0"),
                        linea("410602", "0", "240.00"), linea("210201", "0", "31.20"),
                        linea("110702", "0", "100.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.EG,
                LocalDate.of(2026, 1, 15), "Cobertura de nube pagada por anticipado", "CASO-D-DOC-05",
                "05-NUBE", facturaNube, "COMPRA",
                List.of(linea("110401", "90.00", "0"), linea("110301", "11.70", "0"),
                        linea("110102", "0", "101.70")));
        insertarPartida(conn, usuario, enero, TipoPartida.EG,
                LocalDate.of(2026, 1, 15), "Alquiler pagado por anticipado", "CASO-D-DOC-06",
                "06-ALQUILER", facturaAlquiler, "COMPRA",
                List.of(linea("110402", "600.00", "0"), linea("110301", "78.00", "0"),
                        linea("110102", "0", "678.00")));

        insertarPartida(conn, usuario, enero, TipoPartida.AJ, LocalDate.of(2026, 1, 31),
                "A1 Devengo del primer mes SaaS", "A1-2026-01", "A1-SAAS", null, null,
                List.of(linea("210702", "50.00", "0"), linea("410402", "0", "50.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.AJ, LocalDate.of(2026, 1, 31),
                "A2 Consumo de nube", "A2-2026-01", "A2-NUBE", null, null,
                List.of(linea("510201", "30.00", "0"), linea("110401", "0", "30.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.AJ, LocalDate.of(2026, 1, 31),
                "A3 Consumo de alquiler", "A3-2026-01", "A3-ALQUILER", null, null,
                List.of(linea("610102", "200.00", "0"), linea("110402", "0", "200.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.AJ, LocalDate.of(2026, 1, 31),
                "A4 Electricidad consumida no facturada", "A4-2026-01", "A4-ELECTRICIDAD", null, null,
                List.of(linea("610102", "40.00", "0"), linea("210802", "0", "40.00")));
        insertarPartida(conn, usuario, enero, TipoPartida.AJ, LocalDate.of(2026, 1, 31),
                "A5 Compensación de IVA del período", "A5-2026-01", "A5-IVA", null, null,
                List.of(linea("210201", "50.70", "0"), linea("110301", "0", "50.70")));

        UUID lote = insertarLote(conn, liberacion);
        List<UUID> licencias = insertarLicencias(conn, lote, cliente, contratoLicencia, facturaLicencia);
        insertarKardex(conn, lote, null, "LIBERACION", LocalDate.of(2026, 1, 5), 5,
                new BigDecimal("100.000000"), new BigDecimal("500.00"), 5,
                new BigDecimal("500.00"), liberacion, "Liberación de lote", usuario);
        insertarKardex(conn, lote, licencias.get(0), "ENTREGA", LocalDate.of(2026, 1, 10), -1,
                new BigDecimal("100.000000"), new BigDecimal("-100.00"), 4,
                new BigDecimal("400.00"), ventaLicencia, "Entrega de licencia", usuario);

        UUID facturaOrden = crearDocumento(conn, "CCF", NIT_EMPRESA, "SIM-ANEXOD-CLI", cliente,
                contratoOrden, LocalDate.of(2026, 2, 28),
                "2500.00", "325.00", "2825.00", "CASO-OS2026001-FACTURA");
        insertarPartida(conn, usuario, febrero, TipoPartida.IN,
                LocalDate.of(2026, 2, 28), "Ingreso por orden OS2026001 aceptada", "CASO-OS2026001-FACTURA",
                "OS-VENTA", facturaOrden, "VENTA",
                List.of(linea("110202", "2825.00", "0"), linea("410101", "0", "2500.00"),
                        linea("210201", "0", "325.00")));

        if (sembrarConsumosOrden) {
            registrarConsumo(conn, usuario, febrero, ordenId, "MOD",
                "Mano de obra directa: 100 horas", new BigDecimal("100"), new BigDecimal("12"),
                new BigDecimal("1200.00"), LocalDate.of(2026, 2, 25),
                List.of(linea("110501", "1200.00", "0", null, "OS2026001"),
                    linea("110102", "0", "1200.00", null, "OS2026001")));
            registrarConsumo(conn, usuario, febrero, ordenId, "SERVICIO",
                "Recursos exclusivos del proyecto", BigDecimal.ONE, new BigDecimal("300"),
                new BigDecimal("300.00"), LocalDate.of(2026, 2, 25),
                List.of(linea("110502", "300.00", "0", null, "OS2026001"),
                    linea("110102", "0", "300.00", null, "OS2026001")));
            insertarPartida(conn, usuario, febrero, TipoPartida.EG,
                LocalDate.of(2026, 2, 25), "CIF real de OS2026001", "OS2026001-CIF-REAL",
                "OS-CIF-REAL", null, null,
                List.of(linea("520101", "300.00", "0", null, "OS2026001"),
                    linea("110102", "0", "300.00", null, "OS2026001")));
            registrarConsumo(conn, usuario, febrero, ordenId, "CIF",
                "CIF aplicado: 100 horas a $3.00", new BigDecimal("100"), new BigDecimal("3"),
                new BigDecimal("300.00"), LocalDate.of(2026, 2, 25),
                List.of(linea("110503", "300.00", "0", null, "OS2026001"),
                    linea("520201", "0", "300.00", null, "OS2026001")));
        }
        if (sembrarConsumosOrden || !tieneCostoReconocido(conn)) {
            insertarPartida(conn, usuario, febrero, TipoPartida.DI, LocalDate.of(2026, 2, 28),
                "Reconocimiento costo OS2026001", "OS2026001-COSTO-RECONOCIDO",
                "OS-COSTO-RECONOCIDO", null, null,
                List.of(linea("510101", "1800.00", "0", null, "OS2026001"),
                    linea("110501", "0", "1200.00", null, "OS2026001"),
                    linea("110502", "0", "300.00", null, "OS2026001"),
                    linea("110503", "0", "300.00", null, "OS2026001")));
        }

        marcarOrdenEntregada(conn, ordenId);
    }

    private boolean yaFueSembrado(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM evento_contable WHERE clave_unica LIKE ? LIMIT 1")) {
            ps.setString(1, PREFIJO_SEMILLA + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void corregirDuplicadoOrden(Connection conn) throws SQLException {
        UUID ordenId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM orden_servicio WHERE codigo = 'OS2026001'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                ordenId = (UUID) rs.getObject(1);
            }
        }
        BigDecimal costoPrevio = costoOrden(conn, ordenId, false);
        BigDecimal costoSembrado = costoOrden(conn, ordenId, true);
        if (costoPrevio.compareTo(new BigDecimal("1800.00")) != 0
                || costoSembrado.compareTo(new BigDecimal("1800.00")) != 0) {
            return;
        }
        for (String evento : List.of("OS-MOD", "OS-SERVICIO", "OS-CIF", "OS-CIF-REAL",
                "OS-COSTO-RECONOCIDO")) {
            eliminarEventoSeed(conn, evento);
        }
    }

    private BigDecimal costoOrden(Connection conn, UUID ordenId, boolean sembrado) throws SQLException {
        String filtro = sembrado ? "e.clave_unica LIKE ?" : "COALESCE(e.clave_unica,'') NOT LIKE ?";
        String sql = """
                SELECT COALESCE(SUM(c.importe),0)
                FROM orden_consumo c
                LEFT JOIN partida p ON p.id=c.partida_id
                LEFT JOIN evento_contable e ON e.id=p.evento_id
            WHERE c.orden_id=? AND %s
            """.formatted(filtro);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ordenId);
            ps.setString(2, PREFIJO_SEMILLA + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).setScale(2);
            }
        }
    }

    private boolean tieneCostoReconocido(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM partida p
                LEFT JOIN evento_contable e ON e.id=p.evento_id
                WHERE p.concepto='Reconocimiento costo OS2026001'
                  AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                  AND COALESCE(e.clave_unica,'') NOT LIKE ?
                LIMIT 1
                """)) {
            ps.setString(1, PREFIJO_SEMILLA + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void eliminarEventoSeed(Connection conn, String sufijo) throws SQLException {
        String clave = PREFIJO_SEMILLA + sufijo;
        try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM orden_consumo
                WHERE partida_id IN (SELECT id FROM partida WHERE evento_id=(SELECT id FROM evento_contable WHERE clave_unica=?))
                """)) {
            ps.setString(1, clave);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE evento_contable SET partida_contabilizada_id=NULL WHERE clave_unica=?")) {
            ps.setString(1, clave);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM partida WHERE evento_id=(SELECT id FROM evento_contable WHERE clave_unica=?)")) {
            ps.setString(1, clave);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM evento_contable WHERE clave_unica=?")) {
            ps.setString(1, clave);
            ps.executeUpdate();
        }
    }

    private UUID crearTercero(Connection conn, String nit, String nombre,
                              boolean cliente, boolean proveedor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                ON CONFLICT (nit) DO NOTHING
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, nit);
            ps.setString(3, nombre);
            ps.setString(4, nombre);
            ps.setBoolean(5, cliente);
            ps.setBoolean(6, proveedor);
            ps.executeUpdate();
        }
        return buscarId(conn, "SELECT id FROM tercero WHERE nit = ?", nit);
    }

    private UUID crearContrato(Connection conn, UUID terceroId, String codigo, String modalidad,
                               String descripcion, LocalDate desde, LocalDate hasta, String monto)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO contrato (id, tercero_id, codigo, modalidad, descripcion,
                                      fecha_inicio, fecha_fin, monto_sin_iva, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                ON CONFLICT (codigo) DO NOTHING
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, terceroId);
            ps.setString(3, codigo);
            ps.setString(4, modalidad);
            ps.setString(5, descripcion);
            ps.setObject(6, desde);
            ps.setObject(7, hasta);
            ps.setBigDecimal(8, decimal(monto));
            ps.executeUpdate();
        }
        return buscarId(conn, "SELECT id FROM contrato WHERE codigo = ?", codigo);
    }

    private UUID crearDocumento(Connection conn, String tipo, String emisor, String receptor,
                                UUID terceroId, UUID contratoId, LocalDate fecha,
                                String base, String iva, String total, String codigo)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion,
                    receptor_nit, tercero_id, contrato_id, fecha_emision, base_gravada, iva,
                    exento, total, iva_incluido, estado, es_simulacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, FALSE, 'CONTABILIZADO', TRUE)
                """)) {
            ps.setObject(1, id);
            ps.setString(2, tipo);
            ps.setString(3, emisor);
            ps.setString(4, "SIM-" + codigo);
            ps.setString(5, codigo);
            ps.setString(6, receptor);
            ps.setObject(7, terceroId);
            ps.setObject(8, contratoId);
            ps.setObject(9, fecha);
            ps.setBigDecimal(10, decimal(base));
            ps.setBigDecimal(11, decimal(iva));
            ps.setBigDecimal(12, decimal(total));
            ps.executeUpdate();
        }
        return id;
    }

    private UUID insertarPartida(Connection conn, UUID usuarioId, UUID periodoId, TipoPartida tipo,
                                 LocalDate fecha, String concepto, String soporte, String claveEvento,
                                 UUID documentoId, String tipoEvento, List<LineaSeed> lineas)
            throws SQLException {
        UUID eventoId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO evento_contable (id, clave_unica, descripcion)
                VALUES (?, ?, ?)
                """)) {
            ps.setObject(1, eventoId);
            ps.setString(2, PREFIJO_SEMILLA + claveEvento);
            ps.setString(3, concepto);
            ps.executeUpdate();
        }

        UUID partidaId = UUID.randomUUID();
        String folio = partidas.asignarFolio(conn, tipo, fecha.getYear());
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado,
                    origen, documento_soporte, evento_id, creado_por)
                VALUES (?, ?, ?, ?, ?, ?, 'CONTABILIZADA', 'AUTOMATICO', ?, ?, ?)
                """)) {
            ps.setObject(1, partidaId);
            ps.setString(2, tipo.name());
            ps.setString(3, folio);
            ps.setObject(4, fecha);
            ps.setObject(5, periodoId);
            ps.setString(6, concepto);
            ps.setString(7, soporte);
            ps.setObject(8, eventoId);
            ps.setObject(9, usuarioId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar,
                    centro_costo, debe, haber)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int linea = 1;
            for (LineaSeed detalle : lineas) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, partidaId);
                ps.setInt(3, linea++);
                ps.setString(4, detalle.cuenta());
                ps.setString(5, detalle.auxiliar());
                ps.setString(6, detalle.centroCosto());
                ps.setBigDecimal(7, detalle.debe());
                ps.setBigDecimal(8, detalle.haber());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE evento_contable SET partida_contabilizada_id = ? WHERE id = ?")) {
            ps.setObject(1, partidaId);
            ps.setObject(2, eventoId);
            ps.executeUpdate();
        }
        if (documentoId != null) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, documentoId);
                ps.setObject(3, eventoId);
                ps.setString(4, tipoEvento);
                ps.setObject(5, partidaId);
                ps.executeUpdate();
            }
        }
        return partidaId;
    }

    private UUID insertarLote(Connection conn, UUID partidaId) throws SQLException {
        UUID versionId = buscarId(conn, """
                SELECT pv.id FROM producto_version pv
                JOIN producto p ON p.id = pv.producto_id
                WHERE p.codigo = ? AND pv.version_label = ?
                """, "COL-INV", "1.0");
        UUID loteId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas,
                    costo_total, costo_unitario, unidades_disponibles, fecha_liberacion, partida_id)
                VALUES (?, ?, ?, 5, 500.00, 100.000000, 4, ?, ?)
                """)) {
            ps.setObject(1, loteId);
            ps.setObject(2, versionId);
            ps.setString(3, "CASO-D-COL-INV-1.0");
            ps.setObject(4, LocalDate.of(2026, 1, 5));
            ps.setObject(5, partidaId);
            ps.executeUpdate();
        }
        return loteId;
    }

    private List<UUID> insertarLicencias(Connection conn, UUID loteId, UUID clienteId,
                                         UUID contratoId, UUID documentoId) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id,
                    documento_id, fecha_entrega, fecha_activacion, costo_unitario)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 100.000000)
                """)) {
            for (int numero = 1; numero <= 5; numero++) {
                UUID licenciaId = UUID.randomUUID();
                ids.add(licenciaId);
                ps.setObject(1, licenciaId);
                ps.setObject(2, loteId);
                ps.setInt(3, numero);
                boolean entregada = numero == 1;
                ps.setString(4, entregada ? "ENTREGADA" : "DISPONIBLE");
                ps.setObject(5, entregada ? clienteId : null);
                ps.setObject(6, entregada ? contratoId : null);
                ps.setObject(7, entregada ? documentoId : null);
                ps.setObject(8, entregada ? LocalDate.of(2026, 1, 10) : null);
                ps.setObject(9, entregada ? LocalDate.of(2026, 1, 10) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids;
    }

    private void insertarKardex(Connection conn, UUID loteId, UUID licenciaId, String tipo,
                                LocalDate fecha, int unidades, BigDecimal costoUnitario,
                                BigDecimal importe, int saldoUnidades, BigDecimal saldoValor,
                                UUID partidaId, String detalle, UUID creadoPor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO licencia_movimiento (id, lote_id, licencia_id, tipo, fecha, unidades,
                    costo_unitario, importe, saldo_unidades, saldo_valor, partida_id, detalle,
                    creado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, loteId);
            ps.setObject(3, licenciaId);
            ps.setString(4, tipo);
            ps.setObject(5, fecha);
            ps.setInt(6, unidades);
            ps.setBigDecimal(7, costoUnitario);
            ps.setBigDecimal(8, importe);
            ps.setInt(9, saldoUnidades);
            ps.setBigDecimal(10, saldoValor);
            ps.setObject(11, partidaId);
            ps.setString(12, detalle);
            ps.setObject(13, creadoPor);
            ps.executeUpdate();
        }
    }

    private UUID ordenPrincipal(Connection conn, UUID clienteId, UUID contratoId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE orden_servicio
                SET cliente_id = COALESCE(cliente_id, ?), contrato_id = COALESCE(contrato_id, ?)
                WHERE codigo = 'OS2026001'
                """)) {
            ps.setObject(1, clienteId);
            ps.setObject(2, contratoId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No existe la orden semilla OS2026001.");
            }
        }
        return buscarId(conn, "SELECT id FROM orden_servicio WHERE codigo = ?", "OS2026001");
    }

    private UUID registrarConsumo(Connection conn, UUID usuarioId, UUID periodoId, UUID ordenId,
                                  String tipo, String descripcion, BigDecimal cantidad, BigDecimal tarifa,
                                  BigDecimal importe, LocalDate fecha, List<LineaSeed> lineas)
            throws SQLException {
        UUID partidaId = insertarPartida(conn, usuarioId, periodoId, TipoPartida.DI, fecha,
                "Costo OS2026001: " + descripcion, "OS2026001", "OS-" + tipo,
                null, null, lineas);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa,
                    importe, periodo_id, fecha, partida_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, ordenId);
            ps.setString(3, tipo);
            ps.setString(4, descripcion);
            ps.setBigDecimal(5, cantidad);
            ps.setBigDecimal(6, tarifa);
            ps.setBigDecimal(7, importe);
            ps.setObject(8, periodoId);
            ps.setObject(9, fecha);
            ps.setObject(10, partidaId);
            ps.executeUpdate();
        }
        return partidaId;
    }

    private void marcarOrdenEntregada(Connection conn, UUID ordenId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orden_servicio SET estado = 'ENTREGADA' WHERE id = ?")) {
            ps.setObject(1, ordenId);
            ps.executeUpdate();
        }
    }

    private UUID periodo(Connection conn, int anio, int mes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT p.id FROM periodo p JOIN ejercicio e ON e.id = p.ejercicio_id
                WHERE e.anio = ? AND p.mes = ? ORDER BY e.activo DESC LIMIT 1
                """)) {
            ps.setInt(1, anio);
            ps.setInt(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No existe el período " + anio + "-" + mes + ".");
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private UUID usuarioInicial(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM contador WHERE activo = TRUE ORDER BY usuario LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("No existe un usuario activo para atribuir los asientos semilla.");
            }
            return (UUID) rs.getObject(1);
        }
    }

    private UUID buscarId(Connection conn, String sql, String... valores) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int index = 0; index < valores.length; index++) {
                ps.setString(index + 1, valores[index]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No se encontró el registro requerido por la semilla.");
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private LineaSeed linea(String cuenta, String debe, String haber) {
        return linea(cuenta, debe, haber, null, null);
    }

    private LineaSeed linea(String cuenta, String debe, String haber, String auxiliar, String centroCosto) {
        return new LineaSeed(cuenta, auxiliar, centroCosto, decimal(debe), decimal(haber));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private record LineaSeed(String cuenta, String auxiliar, String centroCosto,
                             BigDecimal debe, BigDecimal haber) {
    }
}