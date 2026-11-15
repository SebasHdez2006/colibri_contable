package sv.edu.ues.colibri.domain.model;

import org.junit.jupiter.api.Test;
import sv.edu.ues.colibri.domain.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica reglas de validación, aprobación y reversión de partidas.
 */
class PartidaRulesTest {

    @Test
    void diferenciaDeUnCentavoImpideAprobar() {
        Partida partida = Partida.borrador(TipoPartida.DI, LocalDate.of(2026, 1, 10),
                UUID.randomUUID(), "Prueba", "SOP-1", UUID.randomUUID(), UUID.randomUUID());
        partida.reemplazarDetalles(List.of(
                PartidaDetalle.nueva("110102", new BigDecimal("100.00"), BigDecimal.ZERO),
                PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("99.99"))
        ));
        assertFalse(partida.estaEquilibrada());
        assertEquals(new BigDecimal("0.01"), partida.diferencia());
        DomainException ex = assertThrows(DomainException.class, partida::aprobar);
        assertTrue(ex.getMessage().contains("0.01") || ex.getMessage().toLowerCase().contains("iguales"));
    }

    @Test
    void partidaEquilibradaSeAprueba() {
        Partida partida = Partida.borrador(TipoPartida.DI, LocalDate.of(2026, 1, 10),
                UUID.randomUUID(), "Aporte", "ACTA-1", UUID.randomUUID(), UUID.randomUUID());
        partida.reemplazarDetalles(List.of(
                PartidaDetalle.nueva("110102", new BigDecimal("10000.00"), BigDecimal.ZERO),
                PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("10000.00"))
        ));
        partida.aprobar();
        assertEquals(EstadoPartida.APROBADA, partida.getEstado());
    }

    @Test
    void soloSubcuentasDeSeisDigitosEnDetalle() {
        assertThrows(IllegalArgumentException.class, () ->
                PartidaDetalle.nueva("1101", BigDecimal.ONE, BigDecimal.ZERO).validarImportes());
        assertTrue(Cuenta.esCodigoMovimiento("110102"));
        assertFalse(Cuenta.esCodigoMovimiento("1101"));
        assertFalse(Cuenta.esCodigoMovimiento("11"));
    }

    @Test
    void ambosLadosEnMismaLineaEsInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                PartidaDetalle.nueva("110102", BigDecimal.ONE, BigDecimal.ONE).validarImportes());
    }

    @Test
    void unaPartidaDeReversionNoPuedeRevertirseOtraVez() {
        Partida original = partidaContabilizada("AJ-001");
        Partida reversion = original.crearReversion(LocalDate.of(2026, 2, 1), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        reversion.aprobar();
        reversion.marcarContabilizada("AJ-002");

        DomainException ex = assertThrows(DomainException.class, () -> reversion.crearReversion(
                LocalDate.of(2026, 2, 2), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("corrección nueva"));
    }

    private static Partida partidaContabilizada(String folio) {
        Partida partida = Partida.borrador(TipoPartida.AJ, LocalDate.of(2026, 1, 31), UUID.randomUUID(),
                "Ajuste de prueba", "SOP-REV", UUID.randomUUID(), UUID.randomUUID());
        partida.reemplazarDetalles(List.of(
                PartidaDetalle.nueva("610104", new BigDecimal("100.00"), BigDecimal.ZERO),
                PartidaDetalle.nueva("120201", BigDecimal.ZERO, new BigDecimal("100.00"))));
        partida.aprobar();
        partida.marcarContabilizada(folio);
        return partida;
    }
}
