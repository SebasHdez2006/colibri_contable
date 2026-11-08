package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.EventoContable;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para eventos contables usados como llave de idempotencia.
 */
public class EventoRepository {

    /**
     * Busca un evento por su clave única.
     *
     * @param conn conexión activa
     * @param clave clave del evento
     * @return evento encontrado o vacío
     */
    public Optional<EventoContable> buscarPorClave(Connection conn, String clave) {
        String sql = "SELECT * FROM evento_contable WHERE clave_unica = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar evento", e);
        }
    }

    /**
     * Busca un evento por identificador.
     *
     * @param conn conexión activa
     * @param id identificador del evento
     * @return evento encontrado o vacío
     */
    public Optional<EventoContable> buscarPorId(Connection conn, UUID id) {
        String sql = "SELECT * FROM evento_contable WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar evento por id", e);
        }
    }

    /**
     * Inserta un evento contable.
     *
     * @param conn conexión activa
     * @param evento evento a persistir
     */
    public void insertar(Connection conn, EventoContable evento) {
        String sql = """
                INSERT INTO evento_contable (id, clave_unica, descripcion, partida_contabilizada_id, creado_en)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, evento.getId());
            ps.setString(2, evento.getClaveUnica());
            ps.setString(3, evento.getDescripcion());
            ps.setObject(4, evento.getPartidaContabilizadaId());
            ps.setObject(5, evento.getCreadoEn());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar evento", e);
        }
    }

    /**
     * Vincula una partida contabilizada a un evento.
     *
     * @param conn conexión activa
     * @param eventoId identificador del evento
     * @param partidaId identificador de la partida
     */
    public void vincularPartida(Connection conn, UUID eventoId, UUID partidaId) {
        String sql = "UPDATE evento_contable SET partida_contabilizada_id = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, partidaId);
            ps.setObject(2, eventoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al vincular evento", e);
        }
    }

    private EventoContable map(ResultSet rs) throws SQLException {
        return new EventoContable(
                (UUID) rs.getObject("id"),
                rs.getString("clave_unica"),
                rs.getString("descripcion"),
                (UUID) rs.getObject("partida_contabilizada_id"),
                rs.getObject("creado_en", LocalDateTime.class)
        );
    }
}
