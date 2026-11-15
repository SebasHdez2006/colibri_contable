package sv.edu.ues.colibri.desktop.ui.layout;

import org.junit.jupiter.api.Test;
import sv.edu.ues.colibri.desktop.ui.layout.Breakpoints.Nivel;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreakpointsTest {

    @Test
    void anchoDeterminaElNivel() {
        assertEquals(Nivel.COMPACT, Breakpoints.calcular(560));
        assertEquals(Nivel.COMPACT, Breakpoints.calcular(899.9));
        assertEquals(Nivel.MEDIUM, Breakpoints.calcular(900));
        assertEquals(Nivel.MEDIUM, Breakpoints.calcular(1299));
        assertEquals(Nivel.WIDE, Breakpoints.calcular(1300));
    }

    @Test
    void identificadoresDeNavegacionSonEstables() {
        assertEquals("nav-temas-contables", NavItem.hoja("Temas contables", null, NavItem.Arquetipo.SCROLL,
                () -> null).id());
        assertEquals("nav-registros-libro-diario",
                NavItem.seccion("nav-registros", "Libro diario", NavItem.Arquetipo.SCROLL, () -> null).id());
        assertEquals("servicios-accesos", NavItem.slug("Servicios / Accesos"));
        assertEquals("parametrizacion", NavItem.slug("Parametrización"));
    }
}
