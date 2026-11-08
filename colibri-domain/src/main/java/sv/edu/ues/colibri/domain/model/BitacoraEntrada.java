package sv.edu.ues.colibri.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de auditoría para registrar acciones sobre entidades del sistema.
 */
public final class BitacoraEntrada {

    private final UUID id;
    private final UUID contadorId;
    private final LocalDateTime fechaHora;
    private final String accion;
    private final String entidad;
    private final String entidadId;
    private final String valorAnterior;
    private final String valorNuevo;
    private final String motivo;
    private final String referencia;

    /**
     * Crea una entrada de bitácora con todos sus atributos.
     *
     * @param id identificador de la entrada
     * @param contadorId identificador del usuario responsable
     * @param fechaHora instante de registro
     * @param accion acción ejecutada
     * @param entidad entidad afectada
     * @param entidadId identificador de la entidad afectada
     * @param valorAnterior valor previo registrado
     * @param valorNuevo valor nuevo registrado
     * @param motivo motivo del cambio
     * @param referencia referencia opcional de trazabilidad
     */
    public BitacoraEntrada(UUID id, UUID contadorId, LocalDateTime fechaHora, String accion,
                           String entidad, String entidadId, String valorAnterior, String valorNuevo,
                           String motivo, String referencia) {
        this.id = Objects.requireNonNull(id);
        this.contadorId = contadorId;
        this.fechaHora = Objects.requireNonNull(fechaHora);
        this.accion = Objects.requireNonNull(accion);
        this.entidad = Objects.requireNonNull(entidad);
        this.entidadId = entidadId;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.motivo = motivo;
        this.referencia = referencia;
    }

    /**
     * Crea una entrada de bitácora con marca temporal y UUID generados automáticamente.
     *
     * @param contadorId identificador del usuario responsable
     * @param accion acción ejecutada
     * @param entidad entidad afectada
     * @param entidadId identificador de la entidad afectada
     * @param valorAnterior valor previo registrado
     * @param valorNuevo valor nuevo registrado
     * @param motivo motivo del cambio
     * @return nueva entrada lista para persistirse
     */
    public static BitacoraEntrada de(UUID contadorId, String accion, String entidad, String entidadId,
                                     String valorAnterior, String valorNuevo, String motivo) {
        return new BitacoraEntrada(UUID.randomUUID(), contadorId, LocalDateTime.now(), accion,
                entidad, entidadId, valorAnterior, valorNuevo, motivo, null);
    }

    /**
     * Obtiene el identificador de la entrada.
     *
     * @return UUID de la entrada
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene el identificador del contador responsable.
     *
     * @return UUID del usuario responsable
     */
    public UUID getContadorId() {
        return contadorId;
    }

    /**
     * Obtiene la fecha y hora de registro.
     *
     * @return instante de la entrada
     */
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /**
     * Obtiene la acción registrada.
     *
     * @return nombre de la acción
     */
    public String getAccion() {
        return accion;
    }

    /**
     * Obtiene la entidad afectada.
     *
     * @return nombre de la entidad
     */
    public String getEntidad() {
        return entidad;
    }

    /**
     * Obtiene el identificador textual de la entidad afectada.
     *
     * @return identificador de negocio
     */
    public String getEntidadId() {
        return entidadId;
    }

    /**
     * Obtiene el valor anterior registrado.
     *
     * @return valor previo
     */
    public String getValorAnterior() {
        return valorAnterior;
    }

    /**
     * Obtiene el valor nuevo registrado.
     *
     * @return valor nuevo
     */
    public String getValorNuevo() {
        return valorNuevo;
    }

    /**
     * Obtiene el motivo del cambio.
     *
     * @return motivo o {@code null}
     */
    public String getMotivo() {
        return motivo;
    }

    /**
     * Obtiene la referencia adicional de trazabilidad.
     *
     * @return referencia o {@code null}
     */
    public String getReferencia() {
        return referencia;
    }
}
