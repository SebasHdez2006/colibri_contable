package sv.edu.ues.colibri.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea detalle de una partida contable.
 */
public final class PartidaDetalle {

    private final UUID id;
    private UUID partidaId;
    private int linea;
    private String codigoCuenta;
    private String auxiliar;
    private String centroCosto;
    private BigDecimal debe;
    private BigDecimal haber;

    /**
     * Crea una línea de partida con importes normalizados a dos decimales.
     *
     * @param id identificador único de la línea
     * @param partidaId identificador de la partida padre
     * @param linea número de línea
     * @param codigoCuenta código de cuenta afectado
     * @param auxiliar auxiliar contable opcional
     * @param centroCosto centro de costo opcional
     * @param debe importe al debe
     * @param haber importe al haber
     */
    public PartidaDetalle(UUID id, UUID partidaId, int linea, String codigoCuenta, String auxiliar,
                          String centroCosto, BigDecimal debe, BigDecimal haber) {
        this.id = Objects.requireNonNull(id);
        this.partidaId = partidaId;
        this.linea = linea;
        this.codigoCuenta = Objects.requireNonNull(codigoCuenta);
        this.auxiliar = auxiliar;
        this.centroCosto = centroCosto;
        this.debe = normalizar(debe);
        this.haber = normalizar(haber);
    }

    /**
     * Crea una línea nueva sin auxiliar ni centro de costo.
     *
     * @param codigoCuenta código de la cuenta
     * @param debe importe al debe
     * @param haber importe al haber
     * @return línea de partida nueva
     */
    public static PartidaDetalle nueva(String codigoCuenta, BigDecimal debe, BigDecimal haber) {
        return nueva(codigoCuenta, null, null, debe, haber);
    }

    /**
     * Crea una línea nueva con auxiliar y centro de costo opcionales.
     *
     * @param codigoCuenta código de la cuenta
     * @param auxiliar auxiliar contable opcional
     * @param centroCosto centro de costo opcional
     * @param debe importe al debe
     * @param haber importe al haber
     * @return línea de partida nueva
     */
    public static PartidaDetalle nueva(String codigoCuenta, String auxiliar, String centroCosto,
                                       BigDecimal debe, BigDecimal haber) {
        return new PartidaDetalle(UUID.randomUUID(), null, 0, codigoCuenta, auxiliar, centroCosto, debe, haber);
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valida que la línea tenga importes consistentes con la cuenta de movimiento.
     */
    public void validarImportes() {
        if (debe.signum() < 0 || haber.signum() < 0) {
            throw new IllegalArgumentException("Los importes Debe y Haber no pueden ser negativos.");
        }
        boolean tieneDebe = debe.signum() > 0;
        boolean tieneHaber = haber.signum() > 0;
        if (tieneDebe && tieneHaber) {
            throw new IllegalArgumentException("Cada línea solo puede tener Debe o Haber positivo, no ambos.");
        }
        if (!tieneDebe && !tieneHaber) {
            throw new IllegalArgumentException("Cada línea debe tener un importe en Debe o en Haber.");
        }
        if (!Cuenta.esCodigoMovimiento(codigoCuenta)) {
            throw new IllegalArgumentException(
                    "Solo las subcuentas de seis dígitos admiten movimiento: " + codigoCuenta);
        }
    }

    /**
     * Obtiene el identificador de la línea.
     *
     * @return UUID de la línea
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene el identificador de la partida asociada.
     *
     * @return UUID de la partida o {@code null}
     */
    public UUID getPartidaId() {
        return partidaId;
    }

    /**
     * Actualiza la partida asociada.
     *
     * @param partidaId nuevo identificador de partida
     */
    public void setPartidaId(UUID partidaId) {
        this.partidaId = partidaId;
    }

    /**
     * Obtiene el número de línea.
     *
     * @return número de línea
     */
    public int getLinea() {
        return linea;
    }

    /**
     * Actualiza el número de línea.
     *
     * @param linea nuevo número de línea
     */
    public void setLinea(int linea) {
        this.linea = linea;
    }

    /**
     * Obtiene el código de cuenta.
     *
     * @return código contable
     */
    public String getCodigoCuenta() {
        return codigoCuenta;
    }

    /**
     * Actualiza el código de cuenta.
     *
     * @param codigoCuenta nuevo código contable
     */
    public void setCodigoCuenta(String codigoCuenta) {
        this.codigoCuenta = Objects.requireNonNull(codigoCuenta);
    }

    /**
     * Obtiene el auxiliar contable.
     *
     * @return auxiliar o {@code null}
     */
    public String getAuxiliar() {
        return auxiliar;
    }

    /**
     * Actualiza el auxiliar contable.
     *
     * @param auxiliar nuevo auxiliar
     */
    public void setAuxiliar(String auxiliar) {
        this.auxiliar = auxiliar;
    }

    /**
     * Obtiene el centro de costo.
     *
     * @return centro de costo o {@code null}
     */
    public String getCentroCosto() {
        return centroCosto;
    }

    /**
     * Actualiza el centro de costo.
     *
     * @param centroCosto nuevo centro de costo
     */
    public void setCentroCosto(String centroCosto) {
        this.centroCosto = centroCosto;
    }

    /**
     * Obtiene el importe al debe.
     *
     * @return importe normalizado
     */
    public BigDecimal getDebe() {
        return debe;
    }

    /**
     * Actualiza el importe al debe.
     *
     * @param debe nuevo importe
     */
    public void setDebe(BigDecimal debe) {
        this.debe = normalizar(debe);
    }

    /**
     * Obtiene el importe al haber.
     *
     * @return importe normalizado
     */
    public BigDecimal getHaber() {
        return haber;
    }

    /**
     * Actualiza el importe al haber.
     *
     * @param haber nuevo importe
     */
    public void setHaber(BigDecimal haber) {
        this.haber = normalizar(haber);
    }
}
