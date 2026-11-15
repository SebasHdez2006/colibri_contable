package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Vista informativa sobre el flujo de respaldo administrado por soporte técnico.
 */
public class RespaldoView extends VBox {

    /**
     * Crea una vista informativa sin acciones operativas.
     */
    public RespaldoView() {
        setSpacing(16);
        setPadding(new Insets(16));
        Label titulo = new Label("Respaldo de información");
        titulo.getStyleClass().add("view-title");
        Label info = new Label("Los respaldos de la base de datos los ejecuta el soporte técnico desde su "
                + "módulo. El Contador no administra la infraestructura.");
        info.getStyleClass().add("view-subtitle");
        getChildren().addAll(titulo, info);
    }
}
