package sv.edu.ues.colibri.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un ejercicio contable anual de la empresa.
 */
public final class Ejercicio {

    private final UUID id;
    private final UUID empresaId;
    private final int anio;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private boolean activo;
    private int versionCatalogo;

    /**
     * Crea un ejercicio contable con su rango de fechas y estado.
     *
     * @param id identificador único
     * @param empresaId identificador de la empresa
     * @param anio año del ejercicio
     * @param fechaInicio fecha inicial
     * @param fechaFin fecha final
     * @param activo indica si el ejercicio está activo
     * @param versionCatalogo versión del catálogo asociada
     */
    public Ejercicio(UUID id, UUID empresaId, int anio, LocalDate fechaInicio, LocalDate fechaFin,
                     boolean activo, int versionCatalogo) {
        this.id = Objects.requireNonNull(id);
        this.empresaId = Objects.requireNonNull(empresaId);
        this.anio = anio;
        this.fechaInicio = Objects.requireNonNull(fechaInicio);
        this.fechaFin = Objects.requireNonNull(fechaFin);
        this.activo = activo;
        this.versionCatalogo = versionCatalogo;
    }

    /**
     * Obtiene el identificador del ejercicio.
     *
     * @return UUID del ejercicio
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene la empresa asociada.
     *
     * @return UUID de la empresa
     */
    public UUID getEmpresaId() {
        return empresaId;
    }

    /**
     * Obtiene el año del ejercicio.
     *
     * @return año fiscal
     */
    public int getAnio() {
        return anio;
    }

    /**
     * Obtiene la fecha de inicio.
     *
     * @return fecha inicial del ejercicio
     */
    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    /**
     * Obtiene la fecha de cierre.
     *
     * @return fecha final del ejercicio
     */
    public LocalDate getFechaFin() {
        return fechaFin;
    }

    /**
     * Indica si el ejercicio está activo.
     *
     * @return {@code true} si el ejercicio está habilitado
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Actualiza el estado de activación.
     *
     * @param activo nuevo estado
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Obtiene la versión del catálogo asociada.
     *
     * @return versión del catálogo
     */
    public int getVersionCatalogo() {
        return versionCatalogo;
    }

    /**
     * Actualiza la versión del catálogo asociada.
     *
     * @param versionCatalogo nueva versión del catálogo
     */
    public void setVersionCatalogo(int versionCatalogo) {
        this.versionCatalogo = versionCatalogo;
    }
}
