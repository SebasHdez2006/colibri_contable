package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ModuleView;

/**
 * Módulo contenedor de las secciones de inventario y costeo asociado.
 */
public class InventarioModuleView extends VBox {

    /**
     * Construye las pestañas del módulo de inventario.
     *
     * @param context contexto de aplicación para inicializar las subvistas
     */
    public InventarioModuleView(AppContext context) {
        getChildren().setAll(ModuleView.of("Inventario",
                "Activos comercializables: control perpetuo de licencias y catálogo de servicios/accesos.",
                ModuleView.lazyTab("Digitales", () -> new InventarioCostosView(context, InventarioCostosView.Panel.DIGITALES)),
                ModuleView.lazyTab("Servicios / Accesos", () -> new InventarioCostosView(context, InventarioCostosView.Panel.SERVICIOS))));
    }
}
