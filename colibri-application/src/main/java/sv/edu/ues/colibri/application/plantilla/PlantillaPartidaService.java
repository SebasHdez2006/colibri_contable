package sv.edu.ues.colibri.application.plantilla;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.PlantillaPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.PlantillaPartidaRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Administración y generación de borradores desde asientos recurrentes. */
public final class PlantillaPartidaService {
    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final PeriodoAppService periodos;
    private final PlantillaPartidaRepository repository = new PlantillaPartidaRepository();

    public PlantillaPartidaService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
                                   PeriodoAppService periodos) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.periodos = periodos;
    }

    public List<PlantillaPartida> listar() {
        auth.exigirContador();
        return jdbc.inTransaction(repository::listarActivas);
    }

    public PlantillaPartida guardar(PlantillaPartida plantilla) {
        auth.exigirContador();
        if (plantilla.detalles().size() < 2) {
            throw new DomainException("Una plantilla debe tener al menos dos líneas.");
        }
        plantilla.detalles().forEach(PartidaDetalle::validarImportes);
        jdbc.runInTransaction(conn -> repository.insertar(conn, plantilla));
        return plantilla;
    }

    /**
     * Crea un borrador para el período indicado. Nunca aprueba ni contabiliza.
     */
    public Partida generarBorrador(String codigo, LocalDate fecha) {
        auth.exigirContador();
        periodos.exigirPeriodoAbiertoParaFecha(fecha);
        PlantillaPartida plantilla = jdbc.inTransaction(conn -> repository.porCodigo(conn, codigo))
                .orElseThrow(() -> new DomainException("Plantilla no encontrada: " + codigo));
        List<PartidaDetalle> detalles = plantilla.detalles().stream()
                .map(d -> PartidaDetalle.nueva(d.getCodigoCuenta(), d.getAuxiliar(), d.getCentroCosto(),
                        d.getDebe(), d.getHaber()))
                .toList();
        return partidas.guardarBorrador(plantilla.tipo(), fecha, plantilla.concepto(),
                "PLANTILLA-" + codigo + "-" + fecha, "PLANTILLA-" + codigo + "-" + fecha,
                new ArrayList<>(detalles), null);
    }
}
