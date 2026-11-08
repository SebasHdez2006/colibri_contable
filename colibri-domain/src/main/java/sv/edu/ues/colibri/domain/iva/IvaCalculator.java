package sv.edu.ues.colibri.domain.iva;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo de IVA 13 % (H03.04): neto o incluido.
 */
public final class IvaCalculator {

    public static final BigDecimal TASA_DEFAULT = new BigDecimal("0.13");

    private IvaCalculator() {
    }

    public record Resultado(BigDecimal base, BigDecimal iva, BigDecimal total) {
    }

    public static Resultado desdeNeto(BigDecimal baseNeto, BigDecimal tasa) {
        BigDecimal base = importeNoNegativo(baseNeto, "base neta");
        BigDecimal iva = scale2(base.multiply(tasaValida(tasa)));
        return new Resultado(base, iva, base.add(iva));
    }

    public static Resultado desdeIncluido(BigDecimal totalIncluido, BigDecimal tasa) {
        BigDecimal total = importeNoNegativo(totalIncluido, "total incluido");
        BigDecimal divisor = BigDecimal.ONE.add(tasaValida(tasa));
        BigDecimal base = total.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(base);
        return new Resultado(base, iva, total);
    }

    /**
     * IVA de importación = (valor en aduana + DAI) × tasa. La base del resultado
     * incluye el arancel y el total incorpora el IVA liquidado.
     */
    public static Resultado desdeImportacion(BigDecimal valorEnAduana, BigDecimal dai, BigDecimal tasa) {
        BigDecimal base = scale2(importeNoNegativo(valorEnAduana, "valor en aduana")
                .add(importeNoNegativo(dai, "DAI")));
        BigDecimal iva = scale2(base.multiply(tasaValida(tasa)));
        return new Resultado(base, iva, base.add(iva));
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal importeNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El importe de " + etiqueta + " no puede ser negativo.");
        }
        return scale2(valor);
    }

    private static BigDecimal tasaValida(BigDecimal tasa) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La tasa de IVA debe estar entre 0 y 1.");
        }
        return tasa;
    }
}
