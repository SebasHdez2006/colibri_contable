package sv.edu.ues.colibri.application.costos;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Comprueba las fórmulas de estimación de costos y esfuerzo.
 */
class EstimacionCostosServiceTest {
    @Test void calculaCostoCifPrecioYMargen() {
        var r = new EstimacionCostosService().estimar(EstimacionCostosService.Metodo.TRES_PUNTOS,
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("25"));
        assertEquals(new BigDecimal("101.67"), r.esfuerzoHoras());
        assertEquals(new BigDecimal("1220.00"), r.costoTotal());
        assertEquals(new BigDecimal("1626.67"), r.precioUnitario());
        assertTrue(r.recomendacion().contains("optimista"));
    }

    @Test void aplicaEcuacionesDeEstimacion() {
        var service = new EstimacionCostosService();
        assertEquals(new BigDecimal("20.00000000"), service.tresPuntos(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));
        assertEquals(0, new BigDecimal("120").compareTo(service.puntosFuncion(new BigDecimal("100"), new BigDecimal("1.2"))));
        assertEquals(new BigDecimal("12000"), service.lineasCodigo(new BigDecimal("100"), new BigDecimal("1.3333333333")).setScale(0, java.math.RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("117.64705882"), service.desgasteTrabajo(new BigDecimal("100"), new BigDecimal("15")));
    }

    @Test void aplicaCocomoYPutnamConParametros() {
        var service = new EstimacionCostosService();
        assertTrue(service.cocomo(new BigDecimal("10"), new BigDecimal("15"), BigDecimal.ONE, new BigDecimal("160")).signum() > 0);
        assertTrue(service.slimPutnam(new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ONE).signum() > 0);
    }

    @Test void resumePromedioMedianaYVariabilidadDeLasEstimaciones() {
        var service = new EstimacionCostosService();
        List<EstimacionCostosService.Resultado> resultados = List.of(
                resultadoConCosto("100"), resultadoConCosto("200"), resultadoConCosto("300"));

        var estadisticas = service.resumirCostos(resultados);

        assertEquals(3, estadisticas.muestras());
        assertEquals(new BigDecimal("200.00"), estadisticas.promedio());
        assertEquals(new BigDecimal("200.00"), estadisticas.mediana());
        assertEquals(new BigDecimal("100.00"), estadisticas.minimo());
        assertEquals(new BigDecimal("300.00"), estadisticas.maximo());
        assertEquals(new BigDecimal("200.00"), estadisticas.rango());
        assertEquals(new BigDecimal("10000.00"), estadisticas.varianzaMuestral());
        assertEquals(new BigDecimal("100.00"), estadisticas.desviacionEstandarMuestral());
        assertEquals(new BigDecimal("50.00"), estadisticas.coeficienteVariacion());
    }

    @Test void resumenDeUnaSolaEstimacionTieneVariabilidadCero() {
        var estadisticas = new EstimacionCostosService().resumirCostos(
                List.of(resultadoConCosto("175")));

        assertEquals(new BigDecimal("0.00"), estadisticas.varianzaMuestral());
        assertEquals(new BigDecimal("0.00"), estadisticas.desviacionEstandarMuestral());
        assertEquals(new BigDecimal("0.00"), estadisticas.coeficienteVariacion());
    }

    private static EstimacionCostosService.Resultado resultadoConCosto(String costo) {
        BigDecimal valor = new BigDecimal(costo);
        return new EstimacionCostosService.Resultado(EstimacionCostosService.Metodo.TRES_PUNTOS,
                valor, valor, BigDecimal.ZERO, valor, valor, BigDecimal.ZERO, "Prueba");
    }
}
