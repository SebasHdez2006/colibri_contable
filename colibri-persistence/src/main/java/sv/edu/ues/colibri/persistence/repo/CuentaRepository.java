package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;
import sv.edu.ues.colibri.domain.model.NaturalezaCuenta;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

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
 * Repositorio JDBC para persistir y consultar cuentas contables.
 */
public class CuentaRepository {

    /**
     * Lista todas las cuentas ordenadas por código.
     *
     * @param conn conexión activa
     * @return lista de cuentas
     */
    public List<Cuenta> listarTodas(Connection conn) {
        String sql = "SELECT * FROM cuenta ORDER BY codigo";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Cuenta> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(map(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar cuentas", e);
        }
    }

    /**
     * Busca una cuenta por su código.
     *
     * @param conn conexión activa
     * @param codigo código de cuenta
     * @return cuenta encontrada o vacío
     */
    public Optional<Cuenta> buscarPorCodigo(Connection conn, String codigo) {
        String sql = "SELECT * FROM cuenta WHERE codigo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar cuenta", e);
        }
    }

    /**
     * Cuenta el total de cuentas registradas.
     *
     * @param conn conexión activa
     * @return cantidad de cuentas
     */
    public long contar(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM cuenta");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new PersistenceException("Error al contar cuentas", e);
        }
    }

    /**
     * Verifica si una cuenta ya tiene movimientos asociados.
     *
     * @param conn conexión activa
     * @param codigo código de cuenta
     * @return {@code true} si existe al menos un detalle asociado
     */
    public boolean tieneMovimientos(Connection conn, String codigo) {
        String sql = "SELECT 1 FROM partida_detalle WHERE codigo_cuenta = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al verificar movimientos", e);
        }
    }

    /**
     * Inserta una cuenta nueva.
     *
     * @param conn conexión activa
     * @param cuenta cuenta a persistir
     */
    public void insertar(Connection conn, Cuenta cuenta) {
        String sql = """
                INSERT INTO cuenta (id, codigo, nombre, codigo_padre, nivel, naturaleza, rubro, estado,
                                    vigencia_desde, vigencia_hasta, admite_movimiento)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, cuenta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar cuenta", e);
        }
    }

    /**
     * Actualiza nombre, estado, rubro y vigencia de una cuenta.
     *
     * @param conn conexión activa
     * @param cuenta cuenta modificada
     */
    public void actualizarNombreYEstado(Connection conn, Cuenta cuenta) {
        String sql = """
                UPDATE cuenta SET nombre = ?, estado = ?, rubro = ?, vigencia_hasta = ?
                WHERE codigo = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cuenta.getNombre());
            ps.setString(2, cuenta.getEstado().name());
            ps.setString(3, cuenta.getRubro());
            if (cuenta.getVigenciaHasta() != null) {
                ps.setObject(4, cuenta.getVigenciaHasta());
            } else {
                ps.setObject(4, null);
            }
            ps.setString(5, cuenta.getCodigo());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar cuenta", e);
        }
    }

    private void bind(PreparedStatement ps, Cuenta cuenta) throws SQLException {
        ps.setObject(1, cuenta.getId());
        ps.setString(2, cuenta.getCodigo());
        ps.setString(3, cuenta.getNombre());
        ps.setString(4, cuenta.getCodigoPadre());
        ps.setInt(5, cuenta.getNivel());
        ps.setString(6, cuenta.getNaturaleza().name());
        ps.setString(7, cuenta.getRubro());
        ps.setString(8, cuenta.getEstado().name());
        ps.setObject(9, cuenta.getVigenciaDesde());
        ps.setObject(10, cuenta.getVigenciaHasta());
        ps.setBoolean(11, cuenta.isAdmiteMovimiento());
    }

    private Cuenta map(ResultSet rs) throws SQLException {
        return new Cuenta(
                (UUID) rs.getObject("id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("codigo_padre"),
                rs.getInt("nivel"),
                NaturalezaCuenta.valueOf(rs.getString("naturaleza")),
                rs.getString("rubro"),
                EstadoCuenta.valueOf(rs.getString("estado")),
                rs.getObject("vigencia_desde", LocalDate.class),
                rs.getObject("vigencia_hasta", LocalDate.class),
                rs.getBoolean("admite_movimiento")
        );
    }
}
