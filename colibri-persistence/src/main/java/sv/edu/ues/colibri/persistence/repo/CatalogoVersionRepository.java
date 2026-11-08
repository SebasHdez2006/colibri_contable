package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repositorio JDBC para el estado de aprobación de la versión inicial del catálogo.
 */
public class CatalogoVersionRepository {

    /**
     * Verifica si la versión inicial del catálogo ya fue aprobada.
     *
     * @param conn conexión activa
     * @return {@code true} si ya está aprobada
     */
    public boolean estaAprobado(Connection conn) {
        String sql = "SELECT aprobado FROM catalogo_version WHERE version = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar versión de catálogo", e);
        }
    }

    /**
     * Marca la versión inicial como aprobada.
     *
     * @param conn conexión activa
     * @param contadorId usuario aprobador
     */
    public void aprobar(Connection conn, UUID contadorId) {
        String sql = """
                UPDATE catalogo_version SET aprobado = TRUE, aprobado_en = ?, aprobado_por = ?
                WHERE version = 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, LocalDateTime.now());
            ps.setObject(2, contadorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al aprobar catálogo", e);
        }
    }
}
