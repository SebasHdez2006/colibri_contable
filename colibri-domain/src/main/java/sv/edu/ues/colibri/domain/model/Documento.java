package sv.edu.ues.colibri.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un documento tributario o de soporte relacionado con la operación contable.
 */
public final class Documento {
    private final UUID id;
    private String tipo;
    private String emisorNit;
    private String numeroControl;
    private String codigoGeneracion;
    private String receptorNit;
    private UUID terceroId;
    private UUID contratoId;
    private LocalDate fechaEmision;
    private BigDecimal baseGravada;
    private BigDecimal iva;
    private BigDecimal exento;
    private BigDecimal total;
    private boolean ivaIncluido;
    private String estado;
    private boolean simulacion;

    /**
     * Crea un documento con todos sus datos fiscales y de trazabilidad.
     *
     * @param id identificador único
     * @param tipo tipo documental
     * @param emisorNit NIT del emisor
     * @param numeroControl número de control
     * @param codigoGeneracion código de generación
     * @param receptorNit NIT del receptor
     * @param terceroId identificador del tercero relacionado
     * @param contratoId identificador del contrato relacionado
     * @param fechaEmision fecha de emisión
     * @param baseGravada base imponible gravada
     * @param iva impuesto calculado
     * @param exento monto exento
     * @param total total del documento
     * @param ivaIncluido indica si el IVA ya está incluido en el total
     * @param estado estado documental
     * @param simulacion indica si el documento es simulado
     */
    public Documento(UUID id, String tipo, String emisorNit, String numeroControl, String codigoGeneracion,
                     String receptorNit, UUID terceroId, UUID contratoId, LocalDate fechaEmision,
                     BigDecimal baseGravada, BigDecimal iva, BigDecimal exento, BigDecimal total,
                     boolean ivaIncluido, String estado, boolean simulacion) {
        this.id = Objects.requireNonNull(id);
        this.tipo = tipo;
        this.emisorNit = emisorNit;
        this.numeroControl = numeroControl;
        this.codigoGeneracion = codigoGeneracion;
        this.receptorNit = receptorNit;
        this.terceroId = terceroId;
        this.contratoId = contratoId;
        this.fechaEmision = fechaEmision;
        this.baseGravada = baseGravada;
        this.iva = iva;
        this.exento = exento;
        this.total = total;
        this.ivaIncluido = ivaIncluido;
        this.estado = estado;
        this.simulacion = simulacion;
    }

    /**
     * Obtiene el identificador del documento.
     *
     * @return UUID del documento
     */
    public UUID getId() { return id; }
    /**
     * Obtiene el tipo documental.
     *
     * @return tipo del documento
     */
    public String getTipo() { return tipo; }
    /**
     * Obtiene el NIT del emisor.
     *
     * @return NIT emisor
     */
    public String getEmisorNit() { return emisorNit; }
    /**
     * Obtiene el número de control.
     *
     * @return número de control
     */
    public String getNumeroControl() { return numeroControl; }
    /**
     * Obtiene el código de generación.
     *
     * @return código de generación
     */
    public String getCodigoGeneracion() { return codigoGeneracion; }
    /**
     * Obtiene el NIT del receptor.
     *
     * @return NIT receptor
     */
    public String getReceptorNit() { return receptorNit; }
    /**
     * Obtiene el identificador del tercero relacionado.
     *
     * @return UUID del tercero o {@code null}
     */
    public UUID getTerceroId() { return terceroId; }
    /**
     * Obtiene el identificador del contrato relacionado.
     *
     * @return UUID del contrato o {@code null}
     */
    public UUID getContratoId() { return contratoId; }
    /**
     * Obtiene la fecha de emisión.
     *
     * @return fecha de emisión
     */
    public LocalDate getFechaEmision() { return fechaEmision; }
    /**
     * Obtiene la base gravada.
     *
     * @return base gravada
     */
    public BigDecimal getBaseGravada() { return baseGravada; }
    /**
     * Obtiene el monto de IVA.
     *
     * @return IVA calculado
     */
    public BigDecimal getIva() { return iva; }
    /**
     * Obtiene el monto exento.
     *
     * @return monto exento
     */
    public BigDecimal getExento() { return exento; }
    /**
     * Obtiene el total del documento.
     *
     * @return total monetario
     */
    public BigDecimal getTotal() { return total; }
    /**
     * Indica si el IVA ya está incluido en el total.
     *
     * @return {@code true} si el IVA se encuentra incluido
     */
    public boolean isIvaIncluido() { return ivaIncluido; }
    /**
     * Obtiene el estado documental.
     *
     * @return estado del documento
     */
    public String getEstado() { return estado; }
    /**
     * Actualiza el estado documental.
     *
     * @param estado nuevo estado
     */
    public void setEstado(String estado) { this.estado = estado; }
    /**
     * Indica si el documento pertenece a una simulación.
     *
     * @return {@code true} si es un documento simulado
     */
    public boolean isSimulacion() { return simulacion; }
}
