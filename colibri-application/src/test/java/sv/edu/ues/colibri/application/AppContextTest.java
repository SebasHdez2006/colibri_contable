package sv.edu.ues.colibri.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica reglas auxiliares de arranque del contexto de aplicación.
 */
class AppContextTest {

    @Test
    void semillaDemoPermaneceDeshabilitadaSinBanderaExplicita() {
        assertFalse(AppContext.semillaCasoDemoHabilitada(null, null));
        assertFalse(AppContext.semillaCasoDemoHabilitada("false", "false"));
    }

    @Test
    void semillaDemoSoloSeHabilitaConValorTrueExplicito() {
        assertTrue(AppContext.semillaCasoDemoHabilitada("true", null));
        assertTrue(AppContext.semillaCasoDemoHabilitada(null, "TRUE"));
        assertFalse(AppContext.semillaCasoDemoHabilitada("1", "yes"));
    }
}
