package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio JDBC para registrar y consultar auditoría operativa.
 */
public class BitacoraRepository {

    /**
     * Inserta una entrada de bitácora.
     *
     * @param conn conexión activa
     * @param entrada entrada de auditoría a persistir
     */
    public void registrar(Connection conn, BitacoraEntrada entrada) {
        String sql = """
                INSERT INTO bitacora (id, contador_id, fecha_hora, accion, entidad, entidad_id,
                                      valor_anterior, valor_nuevo, motivo, referencia)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, entrada.getId());
            ps.setObject(2, entrada.getContadorId());
            ps.setObject(3, entrada.getFechaHora());
            ps.setString(4, entrada.getAccion());
            ps.setString(5, entrada.getEntidad());
            ps.setString(6, entrada.getEntidadId());
            ps.setString(7, entrada.getValorAnterior());
            ps.setString(8, entrada.getValorNuevo());
            ps.setString(9, entrada.getMotivo());
            ps.setString(10, entrada.getReferencia());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al registrar bitácora", e);
        }
    }

    /**
     * Lista los eventos de acceso más recientes.
     *
     * @param conn conexión activa
     * @return lista de entradas de bitácora
     */
    public List<BitacoraEntrada> listarAccesos(Connection conn) {
        String sql = """
                SELECT * FROM bitacora
                WHERE accion IN ('LOGIN','LOGIN_FALLIDO','LOGOUT','DESBLOQUEO','CLAVE_TEMPORAL','RECUPERACION')
                ORDER BY fecha_hora DESC LIMIT 200
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<BitacoraEntrada> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar accesos", e);
        }
    }

    private BitacoraEntrada map(ResultSet rs) throws SQLException {
        return new BitacoraEntrada(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("contador_id"),
                rs.getObject("fecha_hora", LocalDateTime.class),
                rs.getString("accion"),
                rs.getString("entidad"),
                rs.getString("entidad_id"),
                rs.getString("valor_anterior"),
                rs.getString("valor_nuevo"),
                rs.getString("motivo"),
                rs.getString("referencia"));
    }
}
