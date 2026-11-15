package sv.edu.ues.colibri.domain.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprueba fórmulas de ajustes contables y límites de seguridad.
 */
class AjustesCalculatorTest {

    @Test
    void calculaInteresDiferidosYDevengosDelMaterialDeReferencia() {
        assertAmount("2500.00", AjustesCalculator.interesAcumulado(bd("50000"), bd("0.20"), 3));
        assertAmount("6000.00", AjustesCalculator.gastoDiferidoConsumido(bd("18000"), 3, 1));
        assertAmount("7500.00", AjustesCalculator.ingresoAcumulado(bd("15000"), 15, 30));
    }

    @Test
    void calculaDepreciacionYEvitaSobrepasarLaBaseDepreciable() {
        assertAmount("400.00", AjustesCalculator.depreciacionAnual(bd("1200"), BigDecimal.ZERO, 3));
        assertAmount("33.33", AjustesCalculator.depreciacionPeriodica(bd("400"), 12));

        var primeraCuota = AjustesCalculator.depreciacionSegura(bd("1200"), BigDecimal.ZERO,
                36, BigDecimal.ZERO, 1);
        assertAmount("33.33333333", primeraCuota.cuotaTeoricaPorMes());
        assertAmount("33.33", primeraCuota.cargoPeriodo());
        assertAmount("33.33", primeraCuota.depreciacionAcumuladaFinal());

        var ultimaCuota = AjustesCalculator.depreciacionSegura(bd("1200"), BigDecimal.ZERO,
                36, bd("1199.90"), 3);
        assertAmount("0.10", ultimaCuota.cargoPeriodo());
        assertAmount("1200.00", ultimaCuota.depreciacionAcumuladaFinal());
        assertEquals(true, ultimaCuota.totalmenteDepreciado());
    }

    @Test
    void calculaAgotamientoAmortizacionEIncobrables() {
        BigDecimal tasa = AjustesCalculator.tasaAgotamiento(bd("100000"), bd("10000"));
        assertAmount("10.00000000", tasa);
        assertAmount("30000.00", AjustesCalculator.cargoAgotamiento(bd("3000"), tasa));
        assertAmount("333.33", AjustesCalculator.amortizacion(bd("20000"), 60));
        assertAmount("175000.00", AjustesCalculator.estimacionIncobrables(bd("3500000"), bd("0.05")));
    }

    @Test
    void validaDuracionesYLimitesDeLosAjustes() {
        assertThrows(IllegalArgumentException.class,
                () -> AjustesCalculator.gastoDiferidoConsumido(bd("90"), 3, 4));
        assertThrows(IllegalArgumentException.class,
                () -> AjustesCalculator.ingresoAcumulado(bd("100"), 4, 3));
        assertThrows(IllegalArgumentException.class,
                () -> AjustesCalculator.depreciacionSegura(bd("100"), BigDecimal.ZERO,
                        12, bd("101"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> AjustesCalculator.estimacionIncobrables(bd("100"), bd("1.01")));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected=" + expected + ", actual=" + actual);
    }
}
