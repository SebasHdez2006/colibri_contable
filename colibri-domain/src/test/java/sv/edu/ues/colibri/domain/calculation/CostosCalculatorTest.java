package sv.edu.ues.colibri.domain.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sv.edu.ues.colibri.domain.calculation.CostosCalculator.Opcion;

/**
 * Valida las fórmulas de costos, CIF, planilla y decisiones gerenciales.
 */
class CostosCalculatorTest {

    @Test
    void calculaElementosDelCostoProductividadYCif() {
        var costo = CostosCalculator.costoProducto(bd("300"), bd("1200"), bd("300"));

        assertAmount("1800.00", costo.costoProduccion());
        assertAmount("1500.00", costo.costoPrimo());
        assertAmount("1500.00", costo.costoConversion());
        assertAmount("0.01000000", CostosCalculator.productividad(bd("1"), bd("100")));

        var cif = CostosCalculator.analizarCif(bd("1200"), bd("400"), bd("400"), bd("1300"));
        assertAmount("3.00000000", cif.tasaPredeterminada());
        assertAmount("1200.00", cif.cifAplicados());
        assertAmount("100.00", cif.variacion());
        assertEquals(CostosCalculator.EstadoCif.SUBAPLICADO, cif.estado());
        assertAmount("300.00", CostosCalculator.cifAplicados(bd("100"), cif.tasaPredeterminada()));
    }

    @Test
    void calculaPlanillaAportesFactoresYVacaciones() {
        var planilla = CostosCalculator.calcularPlanilla(bd("900"), bd("15500"), 15,
                bd("48.75"), BigDecimal.ZERO, bd("0.75"));

        assertAmount("27.00", planilla.isssLaboral());
        assertAmount("65.25", planilla.previsionalLaboral());
        assertAmount("807.75", planilla.liquidoAntesRenta());
        assertAmount("67.50", planilla.isssPatronal());
        assertAmount("78.75", planilla.previsionalPatronal());
        assertAmount("155.00", planilla.incafTotalPlanilla());
        assertAmount("9.00", planilla.incafAsignado());
        assertAmount("155.25", planilla.aportesPatronales());
        assertAmount("1104.00", planilla.costoIntegral());
        assertAmount("1.22666667", planilla.factorRecargo());
        assertAmount("1.63555556", planilla.factorRecargoEficiencia());
        assertAmount("37.76", CostosCalculator.salarioDiarioPrivado(bd("207.69")));
        assertAmount("41.54", CostosCalculator.salarioDiarioCincoDias(bd("207.69")));
        assertAmount("585.00", CostosCalculator.remuneracionVacaciones(bd("30")));
        assertAmount("9.20", CostosCalculator.costoHoraProductiva(planilla.costoIntegral(), bd("120")));
    }

    @Test
    void noAplicaIncafCuandoNoSeAlcanzaElMinimoDeTrabajadores() {
        var planilla = CostosCalculator.calcularPlanilla(bd("900"), bd("8100"), 9,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);

        assertAmount("0.00", planilla.incafTotalPlanilla());
        assertAmount("0.00", planilla.incafAsignado());
        assertAmount("146.25", planilla.aportesPatronales());
    }

    @Test
    void calculaCostoPrecioYUtilidadDeOrden() {
        var orden = CostosCalculator.analizarOrden(bd("1800"), BigDecimal.ONE,
                bd("1.3888888889"), bd("2500"));

        assertAmount("1800.00", orden.costoUnitario());
        assertAmount("2500.00", orden.precioSugerido());
        assertAmount("700.00", orden.utilidadReal());
    }

    @Test
    void calculaUnidadesEquivalentesYAsignaCostosPorProceso() {
        var fisico = CostosCalculator.balanceFisico(bd("20"), bd("120"), bd("30"));
        assertAmount("110.00000000", fisico.unidadesTransferidas());

        var conversion = CostosCalculator.unidadesEquivalentes(bd("110"), bd("20"), bd("0.25"),
                bd("30"), bd("0.50"), bd("90"));
        assertAmount("15.00000000", conversion.simpleFinal());
        assertAmount("120.00000000", conversion.terminadasPeps());
        assertAmount("120.00000000", conversion.iniciadasYTerminadasPeps());
        assertAmount("125.00000000", conversion.promedioPonderado());

        var pepsMateriales = CostosCalculator.asignarPeps(bd("100"), bd("720"), bd("120"), bd("30"));
        assertAmount("6.00000000", pepsMateriales.costoUnitario());
        assertAmount("180.00", pepsMateriales.costoInventarioFinal());
        assertAmount("640.00", pepsMateriales.costoUnidadesTransferidas());

        var pepsConversion = CostosCalculator.asignarPeps(bd("90"), bd("2280"), bd("120"), bd("15"));
        assertAmount("19.00000000", pepsConversion.costoUnitario());
        assertAmount("285.00", pepsConversion.costoInventarioFinal());
        assertAmount("2085.00", pepsConversion.costoUnidadesTransferidas());

        var ueps = CostosCalculator.asignarUeps(bd("100"), bd("720"), bd("120"), bd("110"));
        assertAmount("660.00", ueps.costoUnidadesTransferidas());
        assertAmount("160.00", ueps.costoInventarioFinal());

        var promedio = CostosCalculator.asignarPromedio(bd("100"), bd("720"), bd("140"), bd("30"));
        assertAmount("5.85714286", promedio.costoUnitario());
        assertAmount("175.71", promedio.costoInventarioFinal());
        assertAmount("644.29", promedio.costoUnidadesTransferidas());
    }

    @Test
    void calculaCostosRelevantesParaDecisiones() {
        var diferencial = CostosCalculator.analizarDiferencial(bd("2100"), bd("1800"));
        assertAmount("300.00", diferencial.diferenciaAmenosB());
        assertEquals(Opcion.B, diferencial.recomendacion());

        var conservarVender = CostosCalculator.analizarConservarOVender(bd("700"), bd("800"), bd("1200"));
        assertAmount("-500.00", conservarVender.resultadoConservar());
        assertAmount("-400.00", conservarVender.resultadoVender());
        assertEquals(Opcion.VENDER, conservarVender.recomendacion());
        assertAmount("16000.00", CostosCalculator.costoOportunidad(bd("16000")));

        var fabricarComprar = CostosCalculator.analizarFabricarComprar(bd("100"), bd("200"), bd("30"),
                bd("20"), bd("400"));
        assertAmount("350.00", fabricarComprar.costoFabricar());
        assertAmount("-50.00", fabricarComprar.diferenciaFabricarMenosComprar());
        assertEquals(Opcion.FABRICAR, fabricarComprar.recomendacion());
    }

    @Test
    void rechazaBasesYProporcionesInvalidas() {
        assertThrows(IllegalArgumentException.class,
                () -> CostosCalculator.tasaPredeterminadaCif(bd("1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> CostosCalculator.unidadesEquivalentes(BigDecimal.ONE, BigDecimal.ZERO, bd("1.01"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> CostosCalculator.balanceFisico(bd("1"), BigDecimal.ZERO, bd("2")));
        assertThrows(IllegalArgumentException.class,
                () -> CostosCalculator.calcularPlanilla(bd("100"), bd("99"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected=" + expected + ", actual=" + actual);
    }
}
