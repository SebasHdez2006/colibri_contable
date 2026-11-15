package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.Empresa;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para la única empresa configurada en el sistema.
 */
public class EmpresaRepository {

    /**
     * Obtiene la empresa configurada, si existe.
     *
     * @param conn conexión activa
     * @return empresa o vacío
     */
    public Optional<Empresa> obtenerUnica(Connection conn) {
        String sql = "SELECT * FROM empresa LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new PersistenceException("Error al obtener empresa", e);
        }
    }

    /**
     * Inserta la empresa configurada.
     *
     * @param conn conexión activa
     * @param empresa datos de la empresa
     */
    public void insertar(Connection conn, Empresa empresa) {
        String sql = """
                INSERT INTO empresa (id, razon_social, nombre_comercial, nit, nrc, actividad_economica,
                    direccion_fiscal, logo_ruta, representante_legal, dui_representante, contador_nombre,
                    registro_cvpcpa, moneda, zona_horaria, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, empresa.getId());
            ps.setString(2, empresa.getRazonSocial());
            ps.setString(3, empresa.getNombreComercial());
            ps.setString(4, empresa.getNit());
            ps.setString(5, empresa.getNrc());
            ps.setString(6, empresa.getActividadEconomica());
            ps.setString(7, empresa.getDireccionFiscal());
            ps.setString(8, empresa.getLogoRuta());
            ps.setString(9, empresa.getRepresentanteLegal());
            ps.setString(10, empresa.getDuiRepresentante());
            ps.setString(11, empresa.getContadorNombre());
            ps.setString(12, empresa.getRegistroCvpcpa());
            ps.setString(13, empresa.getMoneda());
            ps.setString(14, empresa.getZonaHoraria());
            ps.setObject(15, empresa.getActualizadoEn());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al insertar empresa", e);
        }
    }

    /**
     * Actualiza la información de la empresa.
     *
     * @param conn conexión activa
     * @param empresa datos modificados
     */
    public void actualizar(Connection conn, Empresa empresa) {
        String sql = """
                UPDATE empresa SET razon_social = ?, nombre_comercial = ?, nit = ?, nrc = ?,
                    actividad_economica = ?, direccion_fiscal = ?, logo_ruta = ?, representante_legal = ?,
                    dui_representante = ?, contador_nombre = ?, registro_cvpcpa = ?, actualizado_en = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, empresa.getRazonSocial());
            ps.setString(2, empresa.getNombreComercial());
            ps.setString(3, empresa.getNit());
            ps.setString(4, empresa.getNrc());
            ps.setString(5, empresa.getActividadEconomica());
            ps.setString(6, empresa.getDireccionFiscal());
            ps.setString(7, empresa.getLogoRuta());
            ps.setString(8, empresa.getRepresentanteLegal());
            ps.setString(9, empresa.getDuiRepresentante());
            ps.setString(10, empresa.getContadorNombre());
            ps.setString(11, empresa.getRegistroCvpcpa());
            ps.setObject(12, empresa.getActualizadoEn());
            ps.setObject(13, empresa.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Error al actualizar empresa", e);
        }
    }

    private Empresa map(ResultSet rs) throws SQLException {
        return new Empresa(
                (UUID) rs.getObject("id"),
                rs.getString("razon_social"),
                rs.getString("nombre_comercial"),
                rs.getString("nit"),
                rs.getString("nrc"),
                rs.getString("actividad_economica"),
                rs.getString("direccion_fiscal"),
                rs.getString("logo_ruta"),
                rs.getString("representante_legal"),
                rs.getString("dui_representante"),
                rs.getString("contador_nombre"),
                rs.getString("registro_cvpcpa"),
                rs.getString("moneda"),
                rs.getString("zona_horaria"),
                rs.getObject("actualizado_en", LocalDateTime.class)
        );
    }
}
