package sv.edu.ues.colibri.desktop.ui.layout;

import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * Lista maestra y panel de detalle. En el nivel compacto de {@link Breakpoints}
 * la lista pasa arriba y el detalle abajo; en los demás, lista a la izquierda.
 *
 * @param <T> tipo de elemento de la lista
 */
public final class MasterDetail<T> extends BorderPane {

    private final ListView<T> lista = new ListView<>();
    private final ScrollPane detalle = new ScrollPane();
    private final Function<T, Node> fabricaDetalle;

    /**
     * Crea el panel maestro-detalle y selecciona el primer elemento.
     *
     * @param elementos      elementos de la lista
     * @param titulo         texto visible de cada elemento
     * @param fabricaDetalle construye el detalle del elemento seleccionado
     */
    public MasterDetail(List<T> elementos, Function<T, String> titulo, Function<T, Node> fabricaDetalle) {
        this.fabricaDetalle = fabricaDetalle;
        getStyleClass().add("master-detail");
        lista.getStyleClass().add("master-list");
        lista.getItems().setAll(elementos);
        lista.setCellFactory(v -> new ListCell<>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(lista.widthProperty().subtract(12));
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : titulo.apply(item));
            }
        });
        detalle.setFitToWidth(true);
        detalle.setFitToHeight(true);
        detalle.getStyleClass().addAll("master-detail-body", "section-scroll-pane");
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, elegido) -> mostrar(elegido));
        setCenter(detalle);
        distribuir(false);
        sceneProperty().addListener((o, a, escena) -> Breakpoints.de(this).ifPresent(bp -> {
            bp.nivelProperty().addListener((x, y, nivel) -> distribuir(nivel == Breakpoints.Nivel.COMPACT));
            distribuir(bp.nivel() == Breakpoints.Nivel.COMPACT);
        }));
        if (!elementos.isEmpty()) {
            lista.getSelectionModel().selectFirst();
        }
    }

    /**
     * Lista maestra, útil para pruebas y selección programática.
     *
     * @return lista de elementos
     */
    public ListView<T> lista() {
        return lista;
    }

    private void mostrar(T elemento) {
        if (elemento == null) {
            detalle.setContent(null);
            return;
        }
        VBox contenido = new VBox(fabricaDetalle.apply(elemento));
        contenido.getStyleClass().add("master-detail-content");
        detalle.setContent(contenido);
    }

    private void distribuir(boolean compacto) {
        lista.getStyleClass().remove("master-list-compact");
        if (compacto) {
            lista.getStyleClass().add("master-list-compact");
            setLeft(null);
            setTop(lista);
        } else {
            setTop(null);
            setLeft(lista);
        }
    }
}
