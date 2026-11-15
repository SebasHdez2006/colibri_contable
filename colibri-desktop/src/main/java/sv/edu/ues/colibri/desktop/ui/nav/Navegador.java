package sv.edu.ues.colibri.desktop.ui.nav;

/** Permite a las vistas pedir al shell que abra otra sección. */
@FunctionalInterface
public interface Navegador {

    /**
     * Abre la sección indicada; un grupo resuelve a su primera sección.
     *
     * @param id identificador de grupo u hoja (por ejemplo {@code nav-registros-libro-diario})
     */
    void ir(String id);
}
