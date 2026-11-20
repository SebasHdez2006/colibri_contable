package sv.edu.ues.colibri.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculadora monetaria centralizada (Invariante 1 de la auditoría forense).
 *
 * <p>Todo cálculo monetario del sistema debe pasar por esta clase para garantizar:
 * <ul>
 *   <li>Precisión intermedia de {@value #SCALE_INTERMEDIATE} decimales.</li>
 *   <li>Redondeo explícito {@link RoundingMode#HALF_UP} al presentar ({@value #SCALE_FINAL} decimales).</li>
 *   <li>Cero uso de {@code double}/{@code float} en rutas monetarias.</li>
 * </ul>
 *
 * <p>Uso típico:
 * <pre>{@code
 * BigDecimal subtotal = MonetaryCalculator.multiply(cantidad, precioUnitario);
 * BigDecimal total    = MonetaryCalculator.finalRound(subtotal);
 * }</pre>
 */
public final class MonetaryCalculator {

    /** Decimales para cálculos intermedios (mayor que la escala de almacenamiento). */
    public static final int SCALE_INTERMEDIATE = 4;

    /** Decimales de presentación/persistencia monetaria. */
    public static final int SCALE_FINAL = 2;

    /** Modo de redondeo contable estándar. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MonetaryCalculator() {
    }

    /**
     * Suma dos importes con precisión intermedia.
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return require(a).add(require(b)).setScale(SCALE_INTERMEDIATE, ROUNDING);
    }

    /**
     * Resta dos importes con precisión intermedia.
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return require(a).subtract(require(b)).setScale(SCALE_INTERMEDIATE, ROUNDING);
    }

    /**
     * Multiplica dos importes con precisión intermedia.
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return require(a).multiply(require(b)).setScale(SCALE_INTERMEDIATE, ROUNDING);
    }

    /**
     * Divide con precisión intermedia y redondeo explícito.
     *
     * @throws ArithmeticException si el divisor es cero
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        BigDecimal divisor = require(b);
        if (divisor.signum() == 0) {
            throw new ArithmeticException("División por cero en cálculo monetario");
        }
        return require(a).divide(divisor, SCALE_INTERMEDIATE, ROUNDING);
    }

    /**
     * Redondeo final a escala de presentación ({@value #SCALE_FINAL}).
     */
    public static BigDecimal finalRound(BigDecimal value) {
        return require(value).setScale(SCALE_FINAL, ROUNDING);
    }

    /**
     * Multiplica cantidad (entera o decimal) por precio unitario y redondea a
     * escala final — el caso de uso más frecuente (línea de documento).
     */
    public static BigDecimal lineTotal(BigDecimal cantidad, BigDecimal precioUnitario) {
        return finalRound(multiply(cantidad, precioUnitario));
    }

    /**
     * Suma una colección de importes y devuelve el total a escala final.
     */
    public static BigDecimal sumFinal(Iterable<BigDecimal> valores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : valores) {
            if (v != null) {
                total = total.add(v);
            }
        }
        return finalRound(total);
    }

    private static BigDecimal require(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
