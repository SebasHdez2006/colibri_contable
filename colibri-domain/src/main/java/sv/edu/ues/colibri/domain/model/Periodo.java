package sv.edu.ues.colibri.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un período contable asociado a un ejercicio.
 */
public final class Periodo {

    private final UUID id;
    private final UUID ejercicioId;
    private final int mes;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private EstadoPeriodo estado;
    private int version;

    /**
     * Crea un período contable con sus fechas de vigencia y versión.
     *
     * @param id identificador único del período
     * @param ejercicioId identificador del ejercicio contable padre
     * @param mes mes calendario del período
     * @param fechaInicio fecha inicial del rango
     * @param fechaFin fecha final del rango
     * @param estado estado operativo actual
     * @param version versión usada para control de concurrencia
     */
    public Periodo(UUID id, UUID ejercicioId, int mes, LocalDate fechaInicio, LocalDate fechaFin,
                   EstadoPeriodo estado, int version) {
        this.id = Objects.requireNonNull(id);
        this.ejercicioId = Objects.requireNonNull(ejercicioId);
        this.mes = mes;
        this.fechaInicio = Objects.requireNonNull(fechaInicio);
        this.fechaFin = Objects.requireNonNull(fechaFin);
        this.estado = Objects.requireNonNull(estado);
        this.version = version;
    }

    /**
     * Indica si el período admite captura de operaciones.
     *
     * @return {@code true} cuando el período está abierto
     */
    public boolean admiteCaptura() {
        return estado == EstadoPeriodo.ABIERTO;
    }

    /**
     * Indica si el período admite ajustes de cierre.
     *
     * @return {@code true} cuando el período está abierto o en revisión
     */
    public boolean admiteAjustesCierre() {
        return estado == EstadoPeriodo.ABIERTO || estado == EstadoPeriodo.EN_REVISION;
    }

    /**
     * Obtiene el identificador del período.
     *
     * @return identificador único
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene el identificador del ejercicio asociado.
     *
     * @return identificador del ejercicio padre
     */
    public UUID getEjercicioId() {
        return ejercicioId;
    }

    /**
     * Obtiene el mes calendario del período.
     *
     * @return número de mes
     */
    public int getMes() {
        return mes;
    }

    /**
     * Obtiene la fecha de inicio del período.
     *
     * @return fecha inicial
     */
    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    /**
     * Obtiene la fecha de fin del período.
     *
     * @return fecha final
     */
    public LocalDate getFechaFin() {
        return fechaFin;
    }

    /**
     * Obtiene el estado actual del período.
     *
     * @return estado del período
     */
    public EstadoPeriodo getEstado() {
        return estado;
    }

    /**
     * Actualiza el estado del período.
     *
     * @param estado nuevo estado
     */
    public void setEstado(EstadoPeriodo estado) {
        this.estado = Objects.requireNonNull(estado);
    }

    /**
     * Obtiene la versión del período.
     *
     * @return versión de concurrencia
     */
    public int getVersion() {
        return version;
    }

    /**
     * Actualiza la versión del período.
     *
     * @param version nuevo valor de versión
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Verifica si una fecha cae dentro del rango del período.
     *
     * @param fecha fecha a evaluar
     * @return {@code true} si la fecha pertenece al período
     */
    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
}
