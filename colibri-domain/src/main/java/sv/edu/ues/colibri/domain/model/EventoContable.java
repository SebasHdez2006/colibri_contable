package sv.edu.ues.colibri.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de evento económico para idempotencia (documento+evento o evento interno).
 */
public final class EventoContable {

    private final UUID id;
    private final String claveUnica;
    private final String descripcion;
    private UUID partidaContabilizadaId;
    private final LocalDateTime creadoEn;

    public EventoContable(UUID id, String claveUnica, String descripcion,
                          UUID partidaContabilizadaId, LocalDateTime creadoEn) {
        this.id = Objects.requireNonNull(id);
        this.claveUnica = Objects.requireNonNull(claveUnica);
        this.descripcion = descripcion;
        this.partidaContabilizadaId = partidaContabilizadaId;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public String getClaveUnica() {
        return claveUnica;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public UUID getPartidaContabilizadaId() {
        return partidaContabilizadaId;
    }

    public void setPartidaContabilizadaId(UUID partidaContabilizadaId) {
        this.partidaContabilizadaId = partidaContabilizadaId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
