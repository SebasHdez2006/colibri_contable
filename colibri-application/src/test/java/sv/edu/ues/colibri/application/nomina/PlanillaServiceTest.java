package sv.edu.ues.colibri.application.nomina;

import org.junit.jupiter.api.Test;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica el cálculo de planilla, cargas patronales y asientos asociados.
 */
class PlanillaServiceTest {

    @Test
    void calculaPlanillaDelCasoConCargasYPrestacionesSeparadas() {
        PlanillaService.Resultado resultado = PlanillaService.calcular(solicitud(10, new BigDecimal("900.00")));

        assertEquals(new BigDecimal("27.00"), resultado.isssLaboral());
        assertEquals(new BigDecimal("67.50"), resultado.isssPatronal());
        assertEquals(new BigDecimal("65.25"), resultado.previsionalLaboral());
        assertEquals(new BigDecimal("78.75"), resultado.previsionalPatronal());
        assertEquals(new BigDecimal("9.00"), resultado.incaf());
        assertEquals(new BigDecimal("37.50"), resultado.aguinaldoProvisionado());
        assertEquals(new BigDecimal("11.25"), resultado.vacacionesProvisionadas());
        assertEquals(new BigDecimal("807.75"), resultado.liquidoPorPagar());
        assertEquals(new BigDecimal("1104.00"), resultado.costoTotalEmpresa());
        assertEquals(new BigDecimal("155.25"), resultado.cargasPatronales());
        assertEquals(new BigDecimal("48.75"), resultado.prestacionesProvisionadas());
    }

    @Test
    void noAplicaIncafCuandoLaPlanillaNoAlcanzaElUmbral() {
        PlanillaService.Resultado resultado = PlanillaService.calcular(solicitud(9, new BigDecimal("900.00")));

        assertEquals(new BigDecimal("0.00"), resultado.incaf());
        assertEquals(new BigDecimal("1095.00"), resultado.costoTotalEmpresa());
    }

    @Test
    void aplicaLaPoliticaPatronalParametrizadaEnLugarDelCasoPorDefecto() {
        PlanillaService.Resultado resultado = PlanillaService.calcularConPolitica(
                solicitud(12, new BigDecimal("1000.00")),
                new BigDecimal("0.08"), new BigDecimal("0.09"), new BigDecimal("0.02"), 12);

        assertEquals(new BigDecimal("80.00"), resultado.isssPatronal());
        assertEquals(new BigDecimal("90.00"), resultado.previsionalPatronal());
        assertEquals(new BigDecimal("20.00"), resultado.incaf());
        assertEquals(new BigDecimal("190.00"), resultado.cargasPatronales());
    }

    @Test
    void previsualizaAsientoBalanceadoConLasCuentasLaboralesActuales() {
        List<PartidaDetalle> lineas = PlanillaService.previsualizarAsiento(
                solicitud(10, new BigDecimal("900.00")));

        BigDecimal debe = lineas.stream().map(PartidaDetalle::getDebe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal haber = lineas.stream().map(PartidaDetalle::getHaber)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("1104.00"), debe);
        assertEquals(debe, haber);
        assertEquals("610101", lineas.getFirst().getCodigoCuenta());
        assertEquals(new BigDecimal("807.75"), lineas.stream()
                .filter(linea -> "210301".equals(linea.getCodigoCuenta()))
                .findFirst().orElseThrow().getHaber());
        assertEquals(new BigDecimal("9.00"), lineas.stream()
                .filter(linea -> "210801".equals(linea.getCodigoCuenta()))
                .findFirst().orElseThrow().getHaber());
    }

    @Test
    void impideDeduccionesMayoresQueElSalario() {
        PlanillaService.Solicitud invalida = new PlanillaService.Solicitud(
                LocalDate.of(2026, 1, 31), 10, new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("101.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "CC-01");

        assertThrows(DomainException.class, () -> PlanillaService.calcular(invalida));
    }

    private static PlanillaService.Solicitud solicitud(int empleados, BigDecimal salario) {
        return new PlanillaService.Solicitud(LocalDate.of(2026, 1, 31), empleados, salario,
                new BigDecimal("0.03"), new BigDecimal("0.0725"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "CC-01");
    }
}
