package sv.edu.ues.colibri.application.costos;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valida el cálculo gerencial de costos directos, estándar y ABC.
 */
class CosteoGerencialServiceTest {

    private final CosteoGerencialService service = new CosteoGerencialService();

    @Test
    void separaCostoDirectoDelCifEstandarYCalculaLaVariacion() {
        var directo = service.calcularDirecto(new BigDecimal("400"), new BigDecimal("600"), 100);
        var estandar = service.analizarEstandar(new BigDecimal("400"), new BigDecimal("600"),
                new BigDecimal("200"), new BigDecimal("1250"), 100);

        assertEquals(new BigDecimal("1000.00"), directo.total());
        assertEquals(new BigDecimal("10.00"), directo.unitario());
        assertEquals(new BigDecimal("1200.00"), estandar.totalEstandar());
        assertEquals(new BigDecimal("50.00"), estandar.variacion());
        assertEquals(new BigDecimal("12.00"), estandar.unitarioEstandar());
    }

    @Test
    void acumulaCifPorActividadParaElCostoAbc() {
        var abc = service.calcularAbc(new BigDecimal("400"), new BigDecimal("600"), List.of(
                new CosteoGerencialService.ActividadAbc("Aseguramiento de calidad", new BigDecimal("80")),
                new CosteoGerencialService.ActividadAbc("Infraestructura", new BigDecimal("120"))), 100);

        assertEquals(new BigDecimal("200.00"), abc.cifPorActividades());
        assertEquals(new BigDecimal("1200.00"), abc.total());
        assertEquals(new BigDecimal("12.00"), abc.unitario());
    }
}
