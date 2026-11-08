package sv.edu.ues.colibri.persistence.repo;

import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.PlantillaPartida;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositorio JDBC para plantillas de partidas recurrentes. */
public class PlantillaPartidaRepository {
    public void insertar(Connection conn, PlantillaPartida plantilla) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO plantilla_partida (id, codigo, nombre, tipo, concepto, activo)
                VALUES (?,?,?,?,?,?)
                """)) {
            ps.setObject(1, plantilla.id());
            ps.setString(2, plantilla.codigo());
            ps.setString(3, plantilla.nombre());
            ps.setString(4, plantilla.tipo().name());
            ps.setString(5, plantilla.concepto());
            ps.setBoolean(6, plantilla.activa());
            ps.executeUpdate();
            try (PreparedStatement detalle = conn.prepareStatement("""
                    INSERT INTO plantilla_detalle
                        (id, plantilla_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
                    VALUES (?,?,?,?,?,?,?,?)
                    """)) {
                for (int i = 0; i < plantilla.detalles().size(); i++) {
                    PartidaDetalle d = plantilla.detalles().get(i);
                    detalle.setObject(1, UUID.randomUUID());
                    detalle.setObject(2, plantilla.id());
                    detalle.setInt(3, i + 1);
                    detalle.setString(4, d.getCodigoCuenta());
                    detalle.setString(5, d.getAuxiliar());
                    detalle.setString(6, d.getCentroCosto());
                    detalle.setBigDecimal(7, d.getDebe());
                    detalle.setBigDecimal(8, d.getHaber());
                    detalle.addBatch();
                }
                detalle.executeBatch();
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo guardar la plantilla de partida", e);
        }
    }

    public List<PlantillaPartida> listarActivas(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM plantilla_partida WHERE activo=true ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {
            List<PlantillaPartida> resultado = new ArrayList<>();
            while (rs.next()) resultado.add(map(conn, rs));
            return resultado;
        } catch (SQLException e) {
            throw new PersistenceException("No se pudieron listar las plantillas", e);
        }
    }

    public Optional<PlantillaPartida> porCodigo(Connection conn, String codigo) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM plantilla_partida WHERE codigo=? AND activo=true")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(conn, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo consultar la plantilla", e);
        }
    }

    private PlantillaPartida map(Connection conn, ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        List<PartidaDetalle> detalles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM plantilla_detalle WHERE plantilla_id=? ORDER BY linea")) {
            ps.setObject(1, id);
            try (ResultSet dr = ps.executeQuery()) {
                while (dr.next()) detalles.add(PartidaDetalle.nueva(dr.getString("codigo_cuenta"),
                        dr.getString("auxiliar"), dr.getString("centro_costo"),
                        dr.getBigDecimal("debe"), dr.getBigDecimal("haber")));
            }
        }
        return new PlantillaPartida(id, rs.getString("codigo"), rs.getString("nombre"),
                TipoPartida.valueOf(rs.getString("tipo")), rs.getString("concepto"),
                rs.getBoolean("activo"), rs.getTimestamp("creado_en").toLocalDateTime(), detalles);
    }
}
