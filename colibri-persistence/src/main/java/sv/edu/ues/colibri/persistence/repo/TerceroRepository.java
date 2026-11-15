package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Tercero;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para terceros, clientes y proveedores.
 */
public class TerceroRepository {

    /**
     * Inserta un tercero.
     *
     * @param conn conexión activa
     * @param t tercero a persistir
     */
    public void insertar(Connection conn, Tercero t) {
        String sql = """
                INSERT INTO tercero (id, nit, nrc, razon_social, nombre_comercial, es_cliente, es_proveedor, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, t.getId());
            ps.setString(2, t.getNit());
            ps.setString(3, t.getNrc());
            ps.setString(4, t.getRazonSocial());
            ps.setString(5, t.getNombreComercial());
            ps.setBoolean(6, t.isCliente());
            ps.setBoolean(7, t.isProveedor());
            ps.setBoolean(8, t.isActivo());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar tercero (¿NIT duplicado?)", e);
        }
    }

    /**
     * Actualiza un tercero existente.
     *
     * @param conn conexión activa
     * @param t tercero modificado
     */
    public void actualizar(Connection conn, Tercero t) {
        String sql = """
                UPDATE tercero SET nit=?, nrc=?, razon_social=?, nombre_comercial=?,
                    es_cliente=?, es_proveedor=?, activo=? WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getNit());
            ps.setString(2, t.getNrc());
            ps.setString(3, t.getRazonSocial());
            ps.setString(4, t.getNombreComercial());
            ps.setBoolean(5, t.isCliente());
            ps.setBoolean(6, t.isProveedor());
            ps.setBoolean(7, t.isActivo());
            ps.setObject(8, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar tercero", e);
        }
    }

    /**
     * Lista los terceros ordenados por razón social.
     *
     * @param conn conexión activa
     * @return lista de terceros
     */
    public List<Tercero> listar(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM tercero ORDER BY razon_social");
             ResultSet rs = ps.executeQuery()) {
            List<Tercero> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar terceros", e);
        }
    }

    /**
     * Busca un tercero por identificador.
     *
     * @param conn conexión activa
     * @param id identificador del tercero
     * @return tercero encontrado o vacío
     */
    public Optional<Tercero> porId(Connection conn, UUID id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM tercero WHERE id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar tercero", e);
        }
    }

    /**
     * Busca un tercero por NIT.
     *
     * @param conn conexión activa
     * @param nit NIT a consultar
     * @return tercero encontrado o vacío
     */
    public Optional<Tercero> porNit(Connection conn, String nit) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM tercero WHERE nit=?")) {
            ps.setString(1, nit);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar tercero por NIT", e);
        }
    }

    private Tercero map(ResultSet rs) throws SQLException {
        return new Tercero(
                (UUID) rs.getObject("id"),
                rs.getString("nit"),
                rs.getString("nrc"),
                rs.getString("razon_social"),
                rs.getString("nombre_comercial"),
                rs.getBoolean("es_cliente"),
                rs.getBoolean("es_proveedor"),
                rs.getBoolean("activo")
        );
    }
}
