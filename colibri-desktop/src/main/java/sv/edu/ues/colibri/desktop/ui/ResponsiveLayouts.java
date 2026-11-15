package sv.edu.ues.colibri.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Utilidades para construir contenedores JavaFX que se ajustan al ancho disponible.
 */
public final class ResponsiveLayouts {

    private ResponsiveLayouts() {
    }

    /**
     * Crea una fila flexible de controles con separación uniforme.
     *
     * @param children nodos a colocar en la fila
     * @return contenedor adaptable a múltiples tamaños de ventana
     */
    public static FlowPane row(Node... children) {
        FlowPane row = new FlowPane(16, 16, children);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWrapLength(720);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("form-field-row");
        // El valor inicial sirve antes del primer layout. Después, el ancho de
        // envoltura sigue al espacio que realmente entrega la página: dejarlo
        // fijo en 720 px hacía que formularios y paneles se cortaran o dejaran
        // media pantalla vacía en monitores anchos.
        row.widthProperty().addListener((observable, anterior, actual) -> {
            if (actual.doubleValue() > 0) {
                row.setPrefWrapLength(Math.max(280, actual.doubleValue() - 4));
            }
        });
        return row;
    }

    /**
     * Crea un campo de formulario con su etiqueta y control asociado.
     *
     * @param labelText texto visible de la etiqueta
     * @param control control gráfico que recibirá el foco semántico de la etiqueta
     * @return columna vertical con etiqueta y control
     */
    public static VBox field(String labelText, Region control) {
        Label label = new Label(labelText);
        label.setLabelFor(control);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        control.setMinWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
        VBox field = new VBox(4, label, control);
        field.getStyleClass().add("form-field");
        field.setFillWidth(true);
        field.setMinWidth(160);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    /**
     * Crea una tarjeta de sección sin nota descriptiva adicional.
     *
     * @param titulo título de la sección
     * @param contenido nodos interiores de la tarjeta
     * @return contenedor con estilo de tarjeta
     */
    public static VBox sectionCard(String titulo, Node... contenido) {
        return sectionCard(titulo, null, contenido);
    }

    /**
     * Crea una tarjeta de sección con título, nota opcional y contenido.
     *
     * @param titulo título de la sección
     * @param nota texto auxiliar opcional
     * @param contenido nodos interiores de la tarjeta
     * @return contenedor con estilo de tarjeta
     */
    public static VBox sectionCard(String titulo, String nota, Node... contenido) {
        Label heading = new Label(titulo);
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(12);
        card.getChildren().add(heading);
        if (nota != null && !nota.isBlank()) {
            Label note = new Label(nota);
            note.getStyleClass().add("section-note");
            note.setWrapText(true);
            note.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(note);
        }
        if (contenido != null && contenido.length > 0) {
            Separator divider = new Separator();
            divider.getStyleClass().add("section-divider");
            card.getChildren().add(divider);
            card.getChildren().addAll(contenido);
        }
        card.getStyleClass().addAll("form-card", "section-card");
        card.setPadding(new Insets(20));
        card.setFillWidth(true);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /**
     * Recalcula el ancho de envoltura de las filas de campos para que las tarjetas
     * refluyan al cambiar el tamaño de la ventana o al colapsar la barra lateral.
     */
    public static void ajustarAncho(Parent root, double ancho) {
        if (root == null || ancho <= 0) {
            return;
        }
        double wrap = Math.max(280, ancho - 48);
        for (Node node : root.lookupAll(".form-field-row")) {
            if (node instanceof FlowPane fila) {
                fila.setPrefWrapLength(wrap);
            }
        }
    }

    /**
     * Hace que un árbol de controles se adapte al ancho y alto disponibles,
     * favoreciendo que formularios, tablas y listas crezcan con su contenedor.
     */
    public static void ajustarAlContenido(Parent root) {
        if (root == null) {
            return;
        }
        ajustarNodo(root);
    }

    private static void ajustarNodo(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        if (node instanceof VBox vbox) {
            vbox.setFillWidth(true);
        }
        if (node instanceof ScrollPane scrollPane) {
            scrollPane.setFitToWidth(true);
        }
        if (node instanceof TableView<?> tabla) {
            tabla.setMaxWidth(Double.MAX_VALUE);
            tabla.setMaxHeight(Double.MAX_VALUE);
        }
        if (node instanceof ListView<?> lista) {
            lista.setMaxWidth(Double.MAX_VALUE);
            lista.setMaxHeight(Double.MAX_VALUE);
        }
        if (node instanceof TabPane tabs) {
            tabs.setMaxWidth(Double.MAX_VALUE);
            tabs.setMaxHeight(Double.MAX_VALUE);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ajustarNodo(child);
            }
        }
    }
}
