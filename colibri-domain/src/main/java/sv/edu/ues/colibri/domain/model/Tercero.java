package sv.edu.ues.colibri.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa una persona o entidad relacionada con documentos y operaciones.
 */
public final class Tercero {
    private final UUID id;
    private String nit;
    private String nrc;
    private String razonSocial;
    private String nombreComercial;
    private boolean cliente;
    private boolean proveedor;
    private boolean activo;

    /**
     * Crea un tercero con su información fiscal y de clasificación.
     *
     * @param id identificador único
     * @param nit NIT del tercero
     * @param nrc NRC del tercero
     * @param razonSocial razón social o nombre legal
     * @param nombreComercial nombre comercial opcional
     * @param cliente indica si actúa como cliente
     * @param proveedor indica si actúa como proveedor
     * @param activo indica si el tercero está habilitado
     */
    public Tercero(UUID id, String nit, String nrc, String razonSocial, String nombreComercial,
                   boolean cliente, boolean proveedor, boolean activo) {
        this.id = Objects.requireNonNull(id);
        this.nit = Objects.requireNonNull(nit);
        this.nrc = nrc;
        this.razonSocial = Objects.requireNonNull(razonSocial);
        this.nombreComercial = nombreComercial;
        this.cliente = cliente;
        this.proveedor = proveedor;
        this.activo = activo;
    }

    /**
     * Obtiene el identificador del tercero.
     *
     * @return UUID del tercero
     */
    public UUID getId() { return id; }
    /**
     * Obtiene el NIT del tercero.
     *
     * @return NIT
     */
    public String getNit() { return nit; }
    /**
     * Actualiza el NIT.
     *
     * @param nit nuevo NIT
     */
    public void setNit(String nit) { this.nit = Objects.requireNonNull(nit); }
    /**
     * Obtiene el NRC.
     *
     * @return NRC o {@code null}
     */
    public String getNrc() { return nrc; }
    /**
     * Actualiza el NRC.
     *
     * @param nrc nuevo NRC
     */
    public void setNrc(String nrc) { this.nrc = nrc; }
    /**
     * Obtiene la razón social.
     *
     * @return razón social
     */
    public String getRazonSocial() { return razonSocial; }
    /**
     * Actualiza la razón social.
     *
     * @param razonSocial nueva razón social
     */
    public void setRazonSocial(String razonSocial) { this.razonSocial = Objects.requireNonNull(razonSocial); }
    /**
     * Obtiene el nombre comercial.
     *
     * @return nombre comercial o {@code null}
     */
    public String getNombreComercial() { return nombreComercial; }
    /**
     * Actualiza el nombre comercial.
     *
     * @param nombreComercial nuevo nombre comercial
     */
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
    /**
     * Indica si el tercero actúa como cliente.
     *
     * @return {@code true} si es cliente
     */
    public boolean isCliente() { return cliente; }
    /**
     * Actualiza la marca de cliente.
     *
     * @param cliente nuevo valor
     */
    public void setCliente(boolean cliente) { this.cliente = cliente; }
    /**
     * Indica si el tercero actúa como proveedor.
     *
     * @return {@code true} si es proveedor
     */
    public boolean isProveedor() { return proveedor; }
    /**
     * Actualiza la marca de proveedor.
     *
     * @param proveedor nuevo valor
     */
    public void setProveedor(boolean proveedor) { this.proveedor = proveedor; }
    /**
     * Indica si el tercero está activo.
     *
     * @return {@code true} si está habilitado
     */
    public boolean isActivo() { return activo; }
    /**
     * Actualiza el estado de activación.
     *
     * @param activo nuevo estado
     */
    public void setActivo(boolean activo) { this.activo = activo; }

    /**
     * Devuelve una representación legible del tercero.
     *
     * @return cadena con NIT y razón social
     */
    @Override
    public String toString() {
        return nit + " — " + razonSocial;
    }
}
