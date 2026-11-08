package sv.edu.ues.colibri.domain.iva;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida el cálculo del IVA sobre importes netos, incluidos e importados.
 */
class IvaCalculatorTest {

    @Test
    void ventaMilMasIva() {
        var r = IvaCalculator.desdeNeto(new BigDecimal("1000"), IvaCalculator.TASA_DEFAULT);
        assertThat(r.base()).isEqualByComparingTo("1000.00");
        assertThat(r.iva()).isEqualByComparingTo("130.00");
        assertThat(r.total()).isEqualByComparingTo("1130.00");
    }

    @Test
    void precioIncluido113() {
        var r = IvaCalculator.desdeIncluido(new BigDecimal("113"), IvaCalculator.TASA_DEFAULT);
        assertThat(r.base()).isEqualByComparingTo("100.00");
        assertThat(r.iva()).isEqualByComparingTo("13.00");
        assertThat(r.total()).isEqualByComparingTo("113.00");
    }

    @Test
    void licencia240() {
        var r = IvaCalculator.desdeNeto(new BigDecimal("240"), IvaCalculator.TASA_DEFAULT);
        assertThat(r.iva()).isEqualByComparingTo("31.20");
        assertThat(r.total()).isEqualByComparingTo("271.20");
    }

    @Test
    void ivaImportacionIncluyeElDaiEnLaBase() {
        var r = IvaCalculator.desdeImportacion(new BigDecimal("1000"), new BigDecimal("100"),
                IvaCalculator.TASA_DEFAULT);
        assertThat(r.base()).isEqualByComparingTo("1100.00");
        assertThat(r.iva()).isEqualByComparingTo("143.00");
        assertThat(r.total()).isEqualByComparingTo("1243.00");
    }
}
