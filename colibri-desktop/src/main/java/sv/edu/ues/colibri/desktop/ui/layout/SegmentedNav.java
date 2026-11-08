package sv.edu.ues.colibri.desktop.ui.layout;

import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Conmutador plano para alternar entre pocas vistas dentro de una misma página
 * (alternativa ligera a {@code TabPane}: una fila de botones y un solo contenido
 * visible a la vez).
 */
public final class SegmentedNav extends VBox {

    private final StackPane contenido = new StackPane();
    private final ToggleGroup grupo = new ToggleGroup();

    /**
     * Crea el conmutador.
     *
     * @param etiquetas  textos de las opciones
     * @param contenidos nodos asociados, en el mismo orden
     */
    public SegmentedNav(List<String> etiquetas, List<? extends Node> contenidos) {
        if (etiquetas.size() != contenidos.size() || etiquetas.isEmpty()) {
            throw new IllegalArgumentException("Debe haber una etiqueta por contenido");
        }
        getStyleClass().add("segmented-nav");
        FlowPane barra = new FlowPane();
        barra.getStyleClass().add("segmented-bar");
        for (int i = 0; i < etiquetas.size(); i++) {
            Node nodo = contenidos.get(i);
            nodo.setVisible(false);
            nodo.setManaged(false);
            contenido.getChildren().add(nodo);
            ToggleButton boton = new ToggleButton(etiquetas.get(i));
            boton.getStyleClass().add("segmented-button");
            boton.setToggleGroup(grupo);
            boton.setOnAction(e -> {
                if (!boton.isSelected()) {
                    boton.setSelected(true);
                }
            });
            boton.selectedProperty().addListener((o, a, activo) -> {
                nodo.setVisible(activo);
                nodo.setManaged(activo);
            });
            barra.getChildren().add(boton);
        }
        ((ToggleButton) barra.getChildren().getFirst()).setSelected(true);
        VBox.setVgrow(contenido, Priority.ALWAYS);
        getChildren().addAll(barra, contenido);
    }
}
