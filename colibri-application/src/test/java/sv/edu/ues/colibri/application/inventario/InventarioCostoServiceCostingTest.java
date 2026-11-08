package sv.edu.ues.colibri.application.inventario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida el reparto de costos y centavos en cálculos de inventario y SaaS.
 */
class InventarioCostoServiceCostingTest {

        @Test
        void distribuyeLosCentavosDelLoteSinDejarResiduoEnInventario() {
                BigDecimal primera = InventarioCostoService.costoContablePorUnidad(
                                new BigDecimal("10.00"), 3, 1);
                BigDecimal segunda = InventarioCostoService.costoContablePorUnidad(
                                new BigDecimal("10.00"), 3, 2);
                BigDecimal tercera = InventarioCostoService.costoContablePorUnidad(
                                new BigDecimal("10.00"), 3, 3);

                assertEquals(new BigDecimal("3.34"), primera);
                assertEquals(new BigDecimal("3.33"), segunda);
                assertEquals(new BigDecimal("3.33"), tercera);
                assertEquals(new BigDecimal("10.00"), primera.add(segunda).add(tercera));
        }

        @Test
        void distribuyeLosCentavosSaasEntrePlanesSinDiferenciaDeConciliacion() {
                UUID planA = UUID.randomUUID();
                UUID planB = UUID.randomUUID();
                UUID planC = UUID.randomUUID();
                List<InventarioCostoService.SaasParticipacion> planes = List.of(
                                new InventarioCostoService.SaasParticipacion(planA, "A", 1),
                                new InventarioCostoService.SaasParticipacion(planB, "B", 1),
                                new InventarioCostoService.SaasParticipacion(planC, "C", 1));

                BigDecimal a = InventarioCostoService.asignarPorUsuariosMes(new BigDecimal("100.00"), planes, planA);
                BigDecimal b = InventarioCostoService.asignarPorUsuariosMes(new BigDecimal("100.00"), planes, planB);
                BigDecimal c = InventarioCostoService.asignarPorUsuariosMes(new BigDecimal("100.00"), planes, planC);

                assertEquals(new BigDecimal("33.34"), a);
                assertEquals(new BigDecimal("33.33"), b);
                assertEquals(new BigDecimal("33.33"), c);
                assertEquals(new BigDecimal("100.00"), a.add(b).add(c));
        }

        @Test
        void calculaVarianzaYRentabilidadDelCostoSegunLosRegistros() {
                var servicio = new InventarioCostoService(null, null, null, null);
                var comparacion = servicio.compararCostos(UUID.randomUUID(), "OS-TEST",
                                new BigDecimal("1800.00"), new BigDecimal("1250.00"), new BigDecimal("2500.00"));

                assertEquals(new BigDecimal("-550.00"), comparacion.variacionAbsoluta());
                assertEquals(new BigDecimal("-30.56"), comparacion.variacionPorcentual());
                assertEquals(new BigDecimal("69.44"), comparacion.indiceEjecucion());
                assertEquals(new BigDecimal("550.00"), comparacion.ahorroPresupuestario());
                assertEquals(BigDecimal.ZERO.setScale(2), comparacion.sobrecosto());
                assertEquals(new BigDecimal("700.00"), comparacion.utilidadEsperada());
                assertEquals(new BigDecimal("1250.00"), comparacion.utilidadReal());
                assertEquals(new BigDecimal("50.00"), comparacion.rentabilidadSobreVenta());
                assertTrue(comparacion.diagnostico().contains("Ahorro"));
        }
}
