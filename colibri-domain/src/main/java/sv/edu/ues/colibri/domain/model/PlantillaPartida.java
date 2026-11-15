package sv.edu.ues.colibri.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Define la estructura reutilizable de una partida recurrente.
 */
public record PlantillaPartida(UUID id, String codigo, String nombre, TipoPartida tipo,
                               String concepto, boolean activa, LocalDateTime creadoEn,
                               List<PartidaDetalle> detalles) {
    public PlantillaPartida {
        Objects.requireNonNull(id);
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(concepto);
        detalles = List.copyOf(detalles);
    }
}
