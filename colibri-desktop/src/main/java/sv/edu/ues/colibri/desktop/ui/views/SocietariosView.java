package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Libros societarios: se emitirán sobre el expediente corporativo; por ahora
 * remite a los reportes obligatorios.
 */
public final class SocietariosView extends VBox {

    /** Crea la vista informativa de libros societarios. */
    public SocietariosView() {
        Label titulo = new Label("Libros societarios");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label("Actas, accionistas y capital social del expediente corporativo.");
        subtitulo.setWrapText(true);
        subtitulo.getStyleClass().add("view-subtitle");
        Label nota = new Label("""
                Libros societarios (actas, accionistas y capital) se emitirán sobre el expediente corporativo.
                Mientras tanto, el aporte de capital y los estados financieros se consultan en Obligatorios.
                """.strip());
        nota.setWrapText(true);
        nota.getStyleClass().add("section-note");
        VBox tarjeta = new VBox(10, nota);
        tarjeta.getStyleClass().add("form-card");
        getChildren().addAll(titulo, subtitulo, tarjeta);
    }
}
