package sv.edu.ues.colibri.domain.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica el cálculo de resultados mercantiles y utilidades.
 */
class ResultadosCalculatorTest {

    @Test
    void calculaCedulaAnaliticaDeVentasComprasEInventario() {
        var resultado = ResultadosCalculator.calcularResultadoMercantil(
                bd("180000"), bd("12000"), bd("3000"),
                bd("90000"), bd("3000"), bd("16000"), BigDecimal.ZERO,
                bd("210000"), bd("115000"));

        assertAmount("165000.00", resultado.ventasNetas());
        assertAmount("93000.00", resultado.comprasTotales());
        assertAmount("77000.00", resultado.comprasNetas());
        assertAmount("287000.00", resultado.mercanciasDisponibles());
        assertAmount("172000.00", resultado.costoVentas());
        assertAmount("-7000.00", resultado.utilidadBruta());
    }

    @Test
    void reproduceConciliacionDeUnaLicenciaDigital() {
        var resultado = ResultadosCalculator.calcularResultadoMercantil(
                bd("240"), BigDecimal.ZERO, BigDecimal.ZERO,
                bd("500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, bd("400"));

        assertAmount("500.00", resultado.mercanciasDisponibles());
        assertAmount("100.00", resultado.costoVentas());
        assertAmount("140.00", resultado.utilidadBruta());
    }

    @Test
    void calculaUtilidadesDeOperacionAntesDeImpuestoYNeta() {
        BigDecimal operacion = ResultadosCalculator.utilidadOperacion(bd("23000"), bd("12000"));
        BigDecimal antesImpuestos = ResultadosCalculator.utilidadAntesImpuestos(operacion,
                bd("500"), bd("1500"), BigDecimal.ZERO);

        assertAmount("11000.00", operacion);
        assertAmount("10000.00", antesImpuestos);
        assertAmount("7500.00", ResultadosCalculator.utilidadNeta(antesImpuestos, bd("2500")));
    }

    @Test
    void impideInventarioFinalYDeduccionesInconsistentes() {
        assertThrows(IllegalArgumentException.class,
                () -> ResultadosCalculator.ventasNetas(bd("10"), bd("11"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> ResultadosCalculator.costoVentas(bd("10"), bd("11")));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected=" + expected + ", actual=" + actual);
    }
}
