package sv.edu.ues.colibri.application.costos;

import org.junit.jupiter.api.Test;
import sv.edu.ues.colibri.domain.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la lógica de costeo por procesos y su conciliación PEPS.
 */
class CosteoPorProcesosServiceTest {

    @Test
    void calculaCedulaPepsPorElementoYConciliaElCosto() {
        var entrada = new CosteoPorProcesosService.ProcesoEntrada(
                "SAAS-OPERACION", LocalDate.of(2026, 10, 31),
                bd("20"), bd("1"), bd("0.25"), bd("0"), bd("0"),
                bd("120"), bd("110"), bd("30"), bd("1"), bd("0.50"),
                bd("720"), bd("2280"));

        var resultado = CosteoPorProcesosService.calcular(entrada);

        assertThat(resultado.balanceFisico().unidadesTransferidas()).isEqualByComparingTo("110.00000000");
        assertThat(resultado.material().unidadesEquivalentes().terminadasPeps())
                .isEqualByComparingTo("120.00000000");
        assertThat(resultado.conversion().unidadesEquivalentes().terminadasPeps())
                .isEqualByComparingTo("120.00000000");
        assertThat(resultado.material().peps().costoUnitario()).isEqualByComparingTo("6.00000000");
        assertThat(resultado.conversion().peps().costoUnitario()).isEqualByComparingTo("19.00000000");
        assertThat(resultado.costoInventarioFinalPeps()).isEqualByComparingTo("465.00");
        assertThat(resultado.costoTransferidoPeps()).isEqualByComparingTo("2535.00");
    }

    @Test
    void rechazaWipInicialQuePermaneceAlFinalEnModeloPepsSimplificado() {
        var entrada = new CosteoPorProcesosService.ProcesoEntrada(
                "SAAS-OPERACION", LocalDate.of(2026, 10, 31),
                bd("20"), bd("1"), bd("0.25"), bd("100"), bd("50"),
                bd("10"), bd("15"), bd("15"), bd("1"), bd("0.50"),
                bd("720"), bd("2280"));

        assertThatThrownBy(() -> CosteoPorProcesosService.calcular(entrada))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("inventario inicial en proceso");
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }
}
