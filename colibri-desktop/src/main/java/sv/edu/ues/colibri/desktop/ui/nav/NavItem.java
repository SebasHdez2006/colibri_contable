package sv.edu.ues.colibri.desktop.ui.nav;

import javafx.scene.Node;
import sv.edu.ues.colibri.desktop.ui.BrandIcon;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Nodo del árbol de navegación. Es la fuente única del menú lateral, el
 * breadcrumb, los enlaces internos y las pruebas de navegación.
 *
 * @param id        identificador estable (también id del botón en el menú)
 * @param etiqueta  texto visible
 * @param icono     símbolo del ítem o grupo (nulo en hijos)
 * @param pagina    arquetipo de página en que se muestra (solo hojas)
 * @param fabrica   construye la vista (solo hojas)
 * @param hijos     secciones del grupo (vacío en hojas)
 */
public record NavItem(String id, String etiqueta, BrandIcon.Symbol icono, Arquetipo pagina,
                      Supplier<? extends Node> fabrica, List<NavItem> hijos) {

    /** Cómo se aloja la vista dentro del centro del shell. */
    public enum Arquetipo { SCROLL, FILL }

    /**
     * Crea una hoja de primer nivel.
     */
    public static NavItem hoja(String etiqueta, BrandIcon.Symbol icono, Arquetipo pagina,
                               Supplier<? extends Node> fabrica) {
        return new NavItem("nav-" + slug(etiqueta), etiqueta, icono, pagina, fabrica, List.of());
    }

    /**
     * Crea una sección hija de un grupo.
     */
    public static NavItem seccion(String idGrupo, String etiqueta, Arquetipo pagina,
                                  Supplier<? extends Node> fabrica) {
        return new NavItem(idGrupo + "-" + slug(etiqueta), etiqueta, null, pagina, fabrica, List.of());
    }

    /**
     * Crea un grupo expandible; el id se deriva de la etiqueta.
     */
    public static NavItem grupo(String etiqueta, BrandIcon.Symbol icono, List<NavItem> hijos) {
        return new NavItem("nav-" + slug(etiqueta), etiqueta, icono, null, null, List.copyOf(hijos));
    }

    /**
     * Indica si el ítem agrupa secciones.
     */
    public boolean esGrupo() {
        return !hijos.isEmpty();
    }

    /**
     * Normaliza un texto a minúsculas ASCII separadas por guiones.
     *
     * @param texto texto original
     * @return identificador seguro
     */
    public static String slug(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
