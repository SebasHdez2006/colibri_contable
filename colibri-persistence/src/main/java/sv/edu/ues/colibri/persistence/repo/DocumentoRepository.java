package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Documento;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para documentos tributarios y sus vínculos.
 */
public class DocumentoRepository {

    /**
     * Inserta un documento.
     *
     * @param conn conexión activa
     * @param d documento a persistir
     */
    public void insertar(Connection conn, Documento d) {
        String sql = """
                INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit,
                    tercero_id, contrato_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido,
                    estado, es_simulacion)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, d.getId());
            ps.setString(2, d.getTipo());
            ps.setString(3, d.getEmisorNit());
            ps.setString(4, d.getNumeroControl());
            ps.setString(5, d.getCodigoGeneracion());
            ps.setString(6, d.getReceptorNit());
            ps.setObject(7, d.getTerceroId());
            ps.setObject(8, d.getContratoId());
            ps.setObject(9, d.getFechaEmision());
            ps.setBigDecimal(10, d.getBaseGravada());
            ps.setBigDecimal(11, d.getIva());
            ps.setBigDecimal(12, d.getExento());
            ps.setBigDecimal(13, d.getTotal());
            ps.setBoolean(14, d.isIvaIncluido());
            ps.setString(15, d.getEstado());
            ps.setBoolean(16, d.isSimulacion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar documento (¿código de generación duplicado?)", e);
        }
    }

    /**
     * Marca un documento como contabilizado.
     *
     * @param conn conexión activa
     * @param id identificador del documento
     */
    public void marcarContabilizado(Connection conn, UUID id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE documento SET estado='CONTABILIZADO' WHERE id=?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar documento", e);
        }
    }

    /**
     * Vincula un documento con un evento contable.
     *
     * @param conn conexión activa
     * @param docId identificador del documento
     * @param eventoId identificador del evento
     * @param tipoEvento tipo de evento
     * @param partidaId identificador de la partida asociada
     */
    public void vincularEvento(Connection conn, UUID docId, UUID eventoId, String tipoEvento, UUID partidaId) {
        String sql = """
                INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id)
                VALUES (?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, docId);
            ps.setObject(3, eventoId);
            ps.setString(4, tipoEvento);
            ps.setObject(5, partidaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al vincular documento-evento (¿duplicado?)", e);
        }
    }

    /**
     * Enlace contable de un documento: la partida asociada y el tipo de
     * evento con que fue registrado (VENTA, COMPRA, COBRO, etc.).
     */
    public record EnlaceDocumento(UUID partidaId, String tipoEvento) {
    }

    /**
     * Recupera el enlace contable principal de un documento, prefiriendo los
     * tipos que alimentan el Libro de IVA.
     *
     * @param conn conexión activa
     * @param documentoId identificador del documento
     * @return enlace encontrado o vacío
     */
    public Optional<EnlaceDocumento> enlaceContable(Connection conn, UUID documentoId) {
        String sql = """
                SELECT partida_id, tipo_evento FROM documento_evento
                WHERE documento_id = ? AND partida_id IS NOT NULL
                ORDER BY CASE tipo_evento
                    WHEN 'VENTA' THEN 0 WHEN 'COMPRA' THEN 1
                    WHEN 'ANTICIPO' THEN 2 ELSE 3 END
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new EnlaceDocumento((UUID) rs.getObject(1), rs.getString(2)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar el enlace del documento", e);
        }
    }

    /**
     * Registra en el documento de ajuste el identificador del documento que
     * origina la nota de crédito o débito.
     *
     * @param conn conexión activa
     * @param docId identificador de la nota emitida
     * @param origenId identificador del documento ajustado
     */
    public void vincularOrigen(Connection conn, UUID docId, UUID origenId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE documento SET documento_origen_id=? WHERE id=?")) {
            ps.setObject(1, origenId);
            ps.setObject(2, docId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al vincular la nota con su documento origen", e);
        }
    }

    /**
     * Lista documentos ordenados por fecha de emisión y creación.
     *
     * @param conn conexión activa
     * @return lista de documentos
     */
    public List<Documento> listar(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM documento ORDER BY fecha_emision DESC, creado_en DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Documento> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar documentos", e);
        }
    }

    /**
     * Busca un documento por identificador.
     *
     * @param conn conexión activa
     * @param id identificador del documento
     * @return documento encontrado o vacío
     */
    public Optional<Documento> porId(Connection conn, UUID id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM documento WHERE id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar documento", e);
        }
    }

    public BigDecimal importeAplicado(Connection conn, UUID documentoId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(importe),0) FROM aplicacion_pago WHERE documento_origen_id=?")) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar aplicaciones del documento", e);
        }
    }

    public BigDecimal importeAplicadoEnDocumento(Connection conn, UUID documentoId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(importe),0) FROM aplicacion_pago WHERE documento_pago_id=?")) {
            ps.setObject(1, documentoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar saldo del DTE", e);
        }
    }

    public void registrarAplicacion(Connection conn, UUID origenId, UUID dteId, UUID partidaId,
                                    BigDecimal importe, LocalDate fecha, String concepto) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO aplicacion_pago
                    (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, origenId);
            ps.setObject(3, dteId);
            ps.setObject(4, partidaId);
            ps.setBigDecimal(5, importe);
            ps.setObject(6, fecha);
            ps.setString(7, concepto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al registrar aplicación de anticipo", e);
        }
    }

    /**
     * Lista los anticipos de un cliente con saldo disponible (documentos CCF
     * vinculados a un evento ANTICIPO).
     *
     * @param conn conexión activa
     * @param terceroId identificador del cliente
     * @return anticipos contabilizados del cliente
     */
    public List<Documento> listarAnticipos(Connection conn, UUID terceroId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT DISTINCT d.* FROM documento d
                JOIN documento_evento de ON de.documento_id = d.id AND de.tipo_evento = 'ANTICIPO'
                WHERE d.tercero_id = ? AND d.estado = 'CONTABILIZADO'
                ORDER BY d.fecha_emision DESC
                """)) {
            ps.setObject(1, terceroId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Documento> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar anticipos", e);
        }
    }

    /**
     * Lista los DTEs cobrables de un cliente (CCF/FACTURA contabilizados que no
     * son anticipos).
     *
     * @param conn conexión activa
     * @param terceroId identificador del cliente
     * @return documentos fiscales del cliente
     */
    public List<Documento> listarDtesCliente(Connection conn, UUID terceroId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT d.* FROM documento d
                WHERE d.tercero_id = ? AND d.estado = 'CONTABILIZADO'
                  AND d.tipo IN ('CCF','FACTURA')
                  AND NOT EXISTS (SELECT 1 FROM documento_evento de
                                  WHERE de.documento_id = d.id AND de.tipo_evento = 'ANTICIPO')
                ORDER BY d.fecha_emision DESC
                """)) {
            ps.setObject(1, terceroId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Documento> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar DTEs del cliente", e);
        }
    }

    /**
     * Suma el IVA de documentos de débito fiscal en un rango.
     *
     * @param conn conexión activa
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return suma de IVA
     */
    public BigDecimal sumaIvaDebito(Connection conn, LocalDate desde, LocalDate hasta) {
        return suma(conn, """
                SELECT COALESCE(SUM(iva),0) FROM documento
                WHERE estado='CONTABILIZADO' AND tipo IN ('CCF','FACTURA')
                  AND fecha_emision BETWEEN ? AND ?
                  AND emisor_nit = (SELECT nit FROM empresa LIMIT 1)
                """, desde, hasta);
    }

    /**
     * Suma el IVA de documentos de crédito fiscal en un rango.
     *
     * @param conn conexión activa
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return suma de IVA
     */
    public BigDecimal sumaIvaCredito(Connection conn, LocalDate desde, LocalDate hasta) {
        return suma(conn, """
                SELECT COALESCE(SUM(d.iva),0) FROM documento d
                WHERE d.estado='CONTABILIZADO' AND d.tipo IN ('CCF','FACTURA')
                  AND d.fecha_emision BETWEEN ? AND ?
                  AND d.receptor_nit = (SELECT nit FROM empresa LIMIT 1)
                """, desde, hasta);
    }

    private BigDecimal suma(Connection conn, String sql, LocalDate desde, LocalDate hasta) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, desde);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al sumar IVA", e);
        }
    }

    private Documento map(ResultSet rs) throws SQLException {
        return new Documento(
                (UUID) rs.getObject("id"),
                rs.getString("tipo"),
                rs.getString("emisor_nit"),
                rs.getString("numero_control"),
                rs.getString("codigo_generacion"),
                rs.getString("receptor_nit"),
                (UUID) rs.getObject("tercero_id"),
                (UUID) rs.getObject("contrato_id"),
                rs.getObject("fecha_emision", LocalDate.class),
                rs.getBigDecimal("base_gravada"),
                rs.getBigDecimal("iva"),
                rs.getBigDecimal("exento"),
                rs.getBigDecimal("total"),
                rs.getBoolean("iva_incluido"),
                rs.getString("estado"),
                rs.getBoolean("es_simulacion")
        );
    }
}
