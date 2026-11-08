package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ModuleView;

import java.util.List;

/**
 * Módulo contenedor de la consulta y exportación de reportes contables.
 */
public class ReportesModuleView extends VBox {

    /**
     * Construye las pestañas de reportes obligatorios, auxiliares y societarios.
     *
     * @param context contexto de aplicación para generar los reportes
     */
    public ReportesModuleView(AppContext context) {
        Label societarios = new Label("""
                Libros societarios (actas, accionistas y capital) se emitirán sobre el expediente corporativo.
                Mientras tanto, el aporte de capital y los estados financieros se consultan en Obligatorios.
                """.strip());
        societarios.setWrapText(true);
        societarios.getStyleClass().add("section-note");
        VBox card = new VBox(10, societarios);
        card.getStyleClass().add("form-card");
        card.setPadding(new Insets(12));

        getChildren().setAll(ModuleView.of("Reportes",
                "Lectura y exportación para el rango seleccionado. El ZIP incluye libros y estados en una carpeta correspondiente al año inicial del rango.",
                ModuleView.lazyTab("Obligatorios", () -> new ReportesView(context, List.of(
                        ReportesView.DIARIO, ReportesView.MAYOR, ReportesView.BALANZA, ReportesView.CUENTA_T,
                        ReportesView.HOJA_TRABAJO, ReportesView.SITUACION, ReportesView.RESULTADOS,
                        ReportesView.LIBRO_IVA_COMPRAS, ReportesView.LIBRO_IVA_CCF, ReportesView.LIBRO_IVA_FCF))),
                ModuleView.lazyTab("Auxiliares", () -> new ReportesView(context, List.of(
                        ReportesView.AUX_CLIENTES, ReportesView.AUX_PROVEEDORES,
                        ReportesView.AUX_INVENTARIO, ReportesView.AUX_ORDEN, ReportesView.AUX_CC))),
                ModuleView.tab("Societarios", card)));
    }
}

