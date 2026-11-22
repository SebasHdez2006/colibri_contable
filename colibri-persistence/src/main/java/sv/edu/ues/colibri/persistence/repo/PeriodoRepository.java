package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Ejercicio;
import sv.edu.ues.colibri.domain.model.EstadoPeriodo;
import sv.edu.ues.colibri.domain.model.Periodo;
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
 * Repositorio JDBC para ejercicios y períodos contables.
 */
public class PeriodoRepository {

    /**
     * Inserta un ejercicio contable.
     *
     * @param conn conexión activa
     * @param ejercicio ejercicio a persistir
     */
    public void insertarEjercicio(Connection conn, Ejercicio ejercicio) {
        String sql = """
                INSERT INTO ejercicio (id, empresa_id, anio, fecha_inicio, fecha_fin, activo, version_catalogo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ejercicio.getId());
            ps.setObject(2, ejercicio.getEmpresaId());
            ps.setInt(3, ejercicio.getAnio());
            ps.setObject(4, ejercicio.getFechaInicio());
            ps.setObject(5, ejercicio.getFechaFin());
            ps.setBoolean(6, ejercicio.isActivo());
            ps.setInt(7, ejercicio.getVersionCatalogo());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar ejercicio", e);
        }
    }

    /**
     * Inserta un período contable.
     *
     * @param conn conexión activa
     * @param periodo período a persistir
     */
    public void insertarPeriodo(Connection conn, Periodo periodo) {
        String sql = """
                INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, periodo.getId());
            ps.setObject(2, periodo.getEjercicioId());
            ps.setInt(3, periodo.getMes());
            ps.setObject(4, periodo.getFechaInicio());
            ps.setObject(5, periodo.getFechaFin());
            ps.setString(6, periodo.getEstado().name());
            ps.setInt(7, periodo.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar período", e);
        }
    }

    /**
     * Obtiene el ejercicio activo más reciente.
     *
     * @param conn conexión activa
     * @return ejercicio activo o vacío
     */
    public Optional<Ejercicio> ejercicioActivo(Connection conn) {
        String sql = "SELECT * FROM ejercicio WHERE activo = TRUE ORDER BY anio DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapEjercicio(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new PersistenceException("Error al obtener ejercicio", e);
        }
    }

    /**
     * Busca un ejercicio por año fiscal.
     *
     * @param conn conexión activa
     * @param anio año fiscal
     * @return ejercicio encontrado o vacío
     */
    public Optional<Ejercicio> buscarEjercicioPorAnio(Connection conn, int anio) {
        String sql = "SELECT * FROM ejercicio WHERE anio = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapEjercicio(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar ejercicio por año", e);
        }
    }

    /**
     * Lista todos los ejercicios.
     *
     * @param conn conexión activa
     * @return lista de ejercicios
     */
    public List<Ejercicio> listarEjercicios(Connection conn) {
        String sql = "SELECT * FROM ejercicio ORDER BY anio";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Ejercicio> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapEjercicio(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar ejercicios", e);
        }
    }

    /**
     * Lista períodos de un ejercicio específico.
     *
     * @param conn conexión activa
     * @param ejercicioId identificador del ejercicio
     * @return lista de períodos
     */
    public List<Periodo> listarPorEjercicio(Connection conn, UUID ejercicioId) {
        String sql = "SELECT * FROM periodo WHERE ejercicio_id = ? ORDER BY mes";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ejercicioId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Periodo> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapPeriodo(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al listar períodos", e);
        }
    }

    /**
     * Busca un período por identificador.
     *
     * @param conn conexión activa
     * @param id identificador del período
     * @return período encontrado o vacío
     */
    public Optional<Periodo> buscarPorId(Connection conn, UUID id) {
        String sql = "SELECT * FROM periodo WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPeriodo(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar período", e);
        }
    }

    /**
     * Busca el período que contiene una fecha dada.
     *
     * @param conn conexión activa
     * @param fecha fecha a evaluar
     * @return período encontrado o vacío
     */
    public Optional<Periodo> buscarPorFecha(Connection conn, LocalDate fecha) {
        String sql = """
                SELECT p.* FROM periodo p
                JOIN ejercicio e ON e.id = p.ejercicio_id
                WHERE e.activo = TRUE AND p.fecha_inicio <= ? AND p.fecha_fin >= ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fecha);
            ps.setObject(2, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPeriodo(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al buscar período por fecha", e);
        }
    }

    /**
     * Obtiene el período abierto actual más antiguo por fecha de inicio.
     *
     * @param conn conexión activa
     * @return período abierto o vacío
     */
    public Optional<Periodo> periodoAbiertoActual(Connection conn) {
        String sql = """
                SELECT p.* FROM periodo p
                JOIN ejercicio e ON e.id = p.ejercicio_id
                WHERE e.activo = TRUE AND p.estado = 'ABIERTO'
                ORDER BY p.fecha_inicio
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapPeriodo(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new PersistenceException("Error al obtener período abierto", e);
        }
    }

    /**
     * Obtiene la versión de catálogo más reciente registrada.
     *
     * @param conn conexión activa
     * @return versión vigente del catálogo
     */
    public int versionCatalogoVigente(Connection conn) {
        String sql = "SELECT COALESCE(MAX(version), 1) FROM catalogo_version";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new PersistenceException("Error al obtener la versión de catálogo", e);
        }
    }

    /**
     * Actualiza el estado y la versión de un período.
     *
     * @param conn conexión activa
     * @param periodo período modificado
     */
    /**
     * Activa o desactiva un ejercicio anual. Un ejercicio inactivo deja de
     * participar en la captura de operaciones.
     *
     * @param conn conexión activa
     * @param ejercicioId identificador del ejercicio
     * @param activo nuevo estado
     */
    public void actualizarActivoEjercicio(Connection conn, UUID ejercicioId, boolean activo) {
        String sql = "UPDATE ejercicio SET activo = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, activo);
            ps.setObject(2, ejercicioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar el estado del ejercicio", e);
        }
    }

    public void actualizarEstado(Connection conn, Periodo periodo) {
        String sql = "UPDATE periodo SET estado = ?, version = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, periodo.getEstado().name());
            ps.setInt(2, periodo.getVersion());
            ps.setObject(3, periodo.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar período", e);
        }
    }

    private Ejercicio mapEjercicio(ResultSet rs) throws SQLException {
        return new Ejercicio(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("empresa_id"),
                rs.getInt("anio"),
                rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_fin", LocalDate.class),
                rs.getBoolean("activo"),
                rs.getInt("version_catalogo")
        );
    }

    private Periodo mapPeriodo(ResultSet rs) throws SQLException {
        return new Periodo(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("ejercicio_id"),
                rs.getInt("mes"),
                rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_fin", LocalDate.class),
                EstadoPeriodo.valueOf(rs.getString("estado")),
                rs.getInt("version")
        );
    }
}
