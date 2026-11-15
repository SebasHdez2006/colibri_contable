package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.OrigenPartida;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para cabeceras y detalles de partidas contables.
 */
public class PartidaRepository {

    /**
     * Inserta una partida con todos sus detalles.
     *
     * @param conn conexión activa
     * @param partida partida a persistir
     */
    public void insertar(Connection conn, Partida partida) {
        String sql = """
                INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen,
                                     documento_soporte, evento_id, partida_revertida_id, motivo_anulacion,
                                     creado_por, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindCabecera(ps, partida);
            ps.executeUpdate();
            guardarDetalles(conn, partida);
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar partida", e);
        }
    }

    /**
     * Actualiza una partida en estado borrador o aprobada y reemplaza sus detalles.
     *
     * @param conn conexión activa
     * @param partida partida modificada
     */
    public void actualizarBorrador(Connection conn, Partida partida) {
        if (partida.getEstado() != EstadoPartida.BORRADOR && partida.getEstado() != EstadoPartida.APROBADA) {
            throw new PersistenceException("Solo se actualizan borradores o aprobadas");
        }
        String sql = """
                UPDATE partida SET tipo = ?, fecha_contable = ?, periodo_id = ?, concepto = ?, estado = ?,
                    documento_soporte = ?, evento_id = ?, actualizado_en = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, partida.getTipo().name());
            ps.setObject(2, partida.getFechaContable());
            ps.setObject(3, partida.getPeriodoId());
            ps.setString(4, partida.getConcepto());
            ps.setString(5, partida.getEstado().name());
            ps.setString(6, partida.getDocumentoSoporte());
            ps.setObject(7, partida.getEventoId());
            ps.setObject(8, LocalDateTime.now());
            ps.setObject(9, partida.getId());
            ps.executeUpdate();
            eliminarDetalles(conn, partida.getId());
            guardarDetalles(conn, partida);
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar partida", e);
        }
    }

    /**
     * Actualiza estado, folio y motivo de una partida.
     *
     * @param conn conexión activa
     * @param partida partida modificada
     */
    public void actualizarEstadoYFolio(Connection conn, Partida partida) {
        String sql = """
                UPDATE partida SET estado = ?, folio = ?, motivo_anulacion = ?, actualizado_en = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, partida.getEstado().name());
            ps.setString(2, partida.getFolio());
            ps.setString(3, partida.getMotivoAnulacion());
            ps.setObject(4, LocalDateTime.now());
            ps.setObject(5, partida.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar estado de partida", e);
        }
    }

    /**
     * Asigna el siguiente folio correlativo para un tipo y año dados.
     *
     * @param conn conexión activa
     * @param tipo tipo de partida
     * @param anio año fiscal
     * @return folio generado
     */
    public String asignarFolio(Connection conn, TipoPartida tipo, int anio) {
        String upsert = """
                INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (?, ?, 1)
                ON CONFLICT (anio, tipo) DO UPDATE SET ultimo = folio_secuencia.ultimo + 1
                RETURNING ultimo
                """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setInt(1, anio);
            ps.setString(2, tipo.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long correlativo = rs.getLong(1);
                return String.format("%s-%d-%06d", tipo.prefijoFolio(), anio, correlativo);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al asignar folio", e);
        }
    }

    /**
     * Busca una partida por identificador junto con sus detalles.
     *
     * @param conn conexión activa
     * @param id identificador de la partida
     * @return partida encontrada o vacío
     */
    public Optional<Partida> buscarPorId(Connection conn, UUID id) {
        String sql = "SELECT * FROM partida WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Partida partida = mapCabecera(rs);
                partida.cargarDetalles(cargarDetalles(conn, id));
                return Optional.of(partida);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar partida", e);
        }
    }

    /**
     * Lista partidas filtradas por estado.
     *
     * @param conn conexión activa
     * @param estado estado a filtrar
     * @return partidas encontradas
     */
    public List<Partida> listarPorEstado(Connection conn, EstadoPartida estado) {
        String sql = "SELECT * FROM partida WHERE estado = ? ORDER BY creado_en DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Partida> lista = new ArrayList<>();
                while (rs.next()) {
                    Partida p = mapCabecera(rs);
                    p.cargarDetalles(cargarDetalles(conn, p.getId()));
                    lista.add(p);
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar partidas", e);
        }
    }

    /**
     * Lista las partidas más recientes.
     *
     * @param conn conexión activa
     * @param limite máximo de registros
     * @return partidas recientes
     */
    public List<Partida> listarRecientes(Connection conn, int limite) {
        return listarFiltradas(conn, null, null, null, null, limite);
    }

    /**
     * Lista partidas recientes aplicando filtros opcionales de estado, tipo y fecha.
     *
     * @param conn conexión activa
     * @param estado estado a filtrar o {@code null} para todos
     * @param tipo tipo de partida a filtrar o {@code null} para todos
     * @param desde fecha contable mínima o {@code null}
     * @param hasta fecha contable máxima o {@code null}
     * @param limite máximo de registros
     * @return partidas encontradas ordenadas por actualización descendente
     */
    public List<Partida> listarFiltradas(Connection conn, EstadoPartida estado, TipoPartida tipo,
                                         LocalDate desde, LocalDate hasta, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM partida WHERE 1=1");
        List<Object> parametros = new ArrayList<>();
        if (estado != null) {
            sql.append(" AND estado = ?");
            parametros.add(estado.name());
        }
        if (tipo != null) {
            sql.append(" AND tipo = ?");
            parametros.add(tipo.name());
        }
        if (desde != null) {
            sql.append(" AND fecha_contable >= ?");
            parametros.add(desde);
        }
        if (hasta != null) {
            sql.append(" AND fecha_contable <= ?");
            parametros.add(hasta);
        }
        sql.append(" ORDER BY actualizado_en DESC LIMIT ?");
        parametros.add(limite);
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Partida> lista = new ArrayList<>();
                while (rs.next()) {
                    Partida p = mapCabecera(rs);
                    p.cargarDetalles(cargarDetalles(conn, p.getId()));
                    lista.add(p);
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar partidas filtradas", e);
        }
    }

    private void guardarDetalles(Connection conn, Partida partida) throws SQLException {
        String sql = """
                INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PartidaDetalle d : partida.getDetalles()) {
                ps.setObject(1, d.getId());
                ps.setObject(2, partida.getId());
                ps.setInt(3, d.getLinea());
                ps.setString(4, d.getCodigoCuenta());
                ps.setString(5, d.getAuxiliar());
                ps.setString(6, d.getCentroCosto());
                ps.setBigDecimal(7, d.getDebe());
                ps.setBigDecimal(8, d.getHaber());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void eliminarDetalles(Connection conn, UUID partidaId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM partida_detalle WHERE partida_id = ?")) {
            ps.setObject(1, partidaId);
            ps.executeUpdate();
        }
    }

    private List<PartidaDetalle> cargarDetalles(Connection conn, UUID partidaId) throws SQLException {
        String sql = "SELECT * FROM partida_detalle WHERE partida_id = ? ORDER BY linea";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, partidaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PartidaDetalle> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new PartidaDetalle(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("partida_id"),
                            rs.getInt("linea"),
                            rs.getString("codigo_cuenta"),
                            rs.getString("auxiliar"),
                            rs.getString("centro_costo"),
                            rs.getBigDecimal("debe") != null ? rs.getBigDecimal("debe") : BigDecimal.ZERO,
                            rs.getBigDecimal("haber") != null ? rs.getBigDecimal("haber") : BigDecimal.ZERO
                    ));
                }
                return lista;
            }
        }
    }

    private void bindCabecera(PreparedStatement ps, Partida p) throws SQLException {
        ps.setObject(1, p.getId());
        ps.setString(2, p.getTipo().name());
        ps.setString(3, p.getFolio());
        ps.setObject(4, p.getFechaContable());
        ps.setObject(5, p.getPeriodoId());
        ps.setString(6, p.getConcepto());
        ps.setString(7, p.getEstado().name());
        ps.setString(8, p.getOrigen().name());
        ps.setString(9, p.getDocumentoSoporte());
        ps.setObject(10, p.getEventoId());
        ps.setObject(11, p.getPartidaRevertidaId());
        ps.setString(12, p.getMotivoAnulacion());
        ps.setObject(13, p.getCreadoPor());
        ps.setObject(14, p.getCreadoEn());
        ps.setObject(15, p.getActualizadoEn());
    }

    private Partida mapCabecera(ResultSet rs) throws SQLException {
        return new Partida(
                (UUID) rs.getObject("id"),
                TipoPartida.valueOf(rs.getString("tipo")),
                rs.getString("folio"),
                rs.getObject("fecha_contable", LocalDate.class),
                (UUID) rs.getObject("periodo_id"),
                rs.getString("concepto"),
                EstadoPartida.valueOf(rs.getString("estado")),
                OrigenPartida.valueOf(rs.getString("origen")),
                rs.getString("documento_soporte"),
                (UUID) rs.getObject("evento_id"),
                (UUID) rs.getObject("partida_revertida_id"),
                rs.getString("motivo_anulacion"),
                (UUID) rs.getObject("creado_por"),
                rs.getObject("creado_en", LocalDateTime.class),
                rs.getObject("actualizado_en", LocalDateTime.class)
        );
    }
}
