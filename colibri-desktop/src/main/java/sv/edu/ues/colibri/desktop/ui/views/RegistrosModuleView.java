package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ModuleView;

/**
 * Módulo contenedor del flujo de registros contables y auxiliares operativos.
 */
public class RegistrosModuleView extends VBox {

    /**
     * Construye las pestañas del módulo de registros.
     *
     * @param context contexto de aplicación para inicializar las subvistas
     */
    public RegistrosModuleView(AppContext context) {
        getChildren().setAll(ModuleView.of("Registros",
                "Motor transaccional: documentos, diario operativo y ajustes de cierre.",
                ModuleView.lazyTab("Documentos", () -> new DocumentosView(context)),
                ModuleView.lazyTab("Libro diario", () -> new PartidasView(context)),
                ModuleView.lazyTab("Planilla", () -> new PlanillaView(context)),
                ModuleView.lazyTab("Ajustes", () -> new AjustesCierreView(context))));
    }
}
