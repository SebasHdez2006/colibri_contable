package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.RolUsuario;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para usuarios del sistema y sus credenciales.
 */
public class ContadorRepository {

    /**
     * Lista los usuarios ordenados por nombre y usuario.
     *
     * @param conn conexión activa
     * @return usuarios existentes
     */
    public List<Contador> listar(Connection conn) {
        String sql = "SELECT * FROM contador ORDER BY nombre, usuario";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Contador> usuarios = new ArrayList<>();
            while (rs.next()) {
                usuarios.add(map(rs));
            }
            return usuarios;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar usuarios", e);
        }
    }

    /**
     * Cuenta los usuarios activos.
     *
     * @param conn conexión activa
     * @return cantidad de usuarios activos
     */
    public long contarActivos(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM contador WHERE activo = TRUE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new PersistenceException("Error al contar usuarios activos", e);
        }
    }

    /**
     * Busca el primer usuario activo disponible.
     *
     * @param conn conexión activa
     * @return usuario activo o vacío
     */
    public Optional<Contador> buscarActivo(Connection conn) {
        String sql = "SELECT * FROM contador WHERE activo = TRUE ORDER BY usuario LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar contador", e);
        }
    }

    /**
     * Busca un usuario por nombre de acceso.
     *
     * @param conn conexión activa
     * @param usuario nombre de usuario
     * @return usuario encontrado o vacío
     */
    public Optional<Contador> buscarPorUsuario(Connection conn, String usuario) {
        String sql = "SELECT * FROM contador WHERE usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar contador por usuario", e);
        }
    }

    /**
     * Busca un usuario por identificador.
     *
     * @param conn conexión activa
     * @param id identificador del usuario
     * @return usuario encontrado o vacío
     */
    public Optional<Contador> buscarPorId(Connection conn, UUID id) {
        String sql = "SELECT * FROM contador WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar contador por id", e);
        }
    }

    /**
     * Inserta un usuario nuevo.
     *
     * @param conn conexión activa
     * @param contador usuario a persistir
     */
    public void insertar(Connection conn, Contador contador) {
        String sql = """
                INSERT INTO contador (id, usuario, nombre, password_hash, password_salt, intentos_fallidos,
                                      bloqueado_hasta, codigo_recuperacion_hash, codigo_recuperacion_salt,
                                      activo, ultimo_acceso, rol, email)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, contador);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar contador", e);
        }
    }

    /**
     * Actualiza datos sensibles de autenticación y control de acceso.
     *
     * @param conn conexión activa
     * @param contador usuario modificado
     */
    public void actualizarSeguridad(Connection conn, Contador contador) {
        String sql = """
                UPDATE contador SET password_hash = ?, password_salt = ?, intentos_fallidos = ?,
                    bloqueado_hasta = ?, codigo_recuperacion_hash = ?, codigo_recuperacion_salt = ?,
                    ultimo_acceso = ?, clave_temporal_expira = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, contador.getPasswordHash());
            ps.setBytes(2, contador.getPasswordSalt());
            ps.setInt(3, contador.getIntentosFallidos());
            ps.setObject(4, contador.getBloqueadoHasta());
            ps.setBytes(5, contador.getCodigoRecuperacionHash());
            ps.setBytes(6, contador.getCodigoRecuperacionSalt());
            ps.setObject(7, contador.getUltimoAcceso());
            ps.setObject(8, contador.getClaveTemporalExpira());
            ps.setObject(9, contador.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar seguridad del contador", e);
        }
    }

    /**
     * Actualiza nombre, correo, rol y estado del usuario.
     *
     * @param conn conexión activa
     * @param contador usuario modificado
     */
    public void actualizarPerfil(Connection conn, Contador contador) {
        String sql = "UPDATE contador SET nombre = ?, email = ?, rol = ?, activo = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contador.getNombre());
            ps.setString(2, contador.getEmail());
            ps.setString(3, contador.getRol().name());
            ps.setBoolean(4, contador.isActivo());
            ps.setObject(5, contador.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar perfil de usuario", e);
        }
    }

    /**
     * Cambia el estado activo de un usuario.
     *
     * @param conn conexión activa
     * @param id identificador del usuario
     * @param activo nuevo estado
     */
    public void actualizarEstado(Connection conn, UUID id, boolean activo) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE contador SET activo = ? WHERE id = ?")) {
            ps.setBoolean(1, activo);
            ps.setObject(2, id);
            if (ps.executeUpdate() != 1) {
                throw new PersistenceException("Usuario no encontrado");
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar estado del usuario", e);
        }
    }

    private void bind(PreparedStatement ps, Contador c) throws SQLException {
        ps.setObject(1, c.getId());
        ps.setString(2, c.getUsuario());
        ps.setString(3, c.getNombre());
        ps.setBytes(4, c.getPasswordHash());
        ps.setBytes(5, c.getPasswordSalt());
        ps.setInt(6, c.getIntentosFallidos());
        ps.setObject(7, c.getBloqueadoHasta());
        ps.setBytes(8, c.getCodigoRecuperacionHash());
        ps.setBytes(9, c.getCodigoRecuperacionSalt());
        ps.setBoolean(10, c.isActivo());
        ps.setObject(11, c.getUltimoAcceso());
        ps.setString(12, c.getRol() == null ? RolUsuario.CONTADOR.name() : c.getRol().name());
        ps.setString(13, c.getEmail());
    }

    private Contador map(ResultSet rs) throws SQLException {
        String rol = rs.getString("rol");
        if (rol == null || rol.isBlank()) {
            rol = RolUsuario.CONTADOR.name();
        }
        Contador contador = new Contador(
                (UUID) rs.getObject("id"),
                rs.getString("usuario"),
                rs.getString("nombre"),
                rs.getBytes("password_hash"),
                rs.getBytes("password_salt"),
                rs.getInt("intentos_fallidos"),
                rs.getObject("bloqueado_hasta", LocalDateTime.class),
                rs.getBytes("codigo_recuperacion_hash"),
                rs.getBytes("codigo_recuperacion_salt"),
                rs.getBoolean("activo"),
                rs.getObject("ultimo_acceso", LocalDateTime.class),
                RolUsuario.valueOf(rol),
                rs.getString("email")
        );
        contador.setClaveTemporalExpira(
                rs.getObject("clave_temporal_expira", LocalDateTime.class));
        return contador;
    }
}
