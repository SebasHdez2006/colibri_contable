package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ModuleView;

/**
 * Módulo contenedor de las vistas relacionadas con costos y decisiones
 * gerenciales.
 */
public class CostosModuleView extends VBox {

    /**
     * Construye el conjunto de pestañas del módulo de costos.
     *
     * @param context contexto de aplicación necesario para inicializar las
     *                subvistas
     */
    public CostosModuleView(AppContext context) {
        getChildren().setAll(ModuleView.of("Costos",
                "Costos directos, CIF, inventarios y decisiones de precio para cada línea comercial.",
                ModuleView.resilientTab("A la medida",
                        () -> new InventarioCostosView(context, InventarioCostosView.Panel.MEDIDA)),
                ModuleView.resilientTab("Licencias descargables",
                        () -> new InventarioCostosView(context, InventarioCostosView.Panel.LICENCIAS)),
                ModuleView.resilientTab("Suscripción SaaS",
                        () -> new InventarioCostosView(context, InventarioCostosView.Panel.SAAS)),
                ModuleView.lazyTab("Procesos y CIF", () -> new ProcesosCifView(context)),
                ModuleView.resilientTab("Estimación y políticas", () -> new EstimacionCostosView(context))));
    }
}
