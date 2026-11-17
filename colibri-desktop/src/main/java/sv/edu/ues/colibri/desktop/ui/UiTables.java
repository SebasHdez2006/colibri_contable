package sv.edu.ues.colibri.desktop.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Convenciones accesibles para las tablas del escritorio.
 *
 * <p>Una tabla no debe depender del lector para descubrir que admite búsqueda,
 * ni ocultar columnas cuando el ancho de la ventana es menor que su contenido.
 * Este componente deja visible un filtro propio y preserva ambos scrolls de
 * {@link TableView}. El texto de búsqueda se normaliza, por lo que "codigo" y
 * "código" producen el mismo resultado.</p>
 */
public final class UiTables {

    private UiTables() {
    }

    /**
     * Envuelve una tabla con un filtro local y conserva sus filas originales.
     * Debe llamarse después de cargar las columnas e ítems iniciales de la tabla.
     *
     * @param tabla tabla que se presentará
     * @param descripcion nombre de los datos que se buscarán
     * @param texto función que expone el contenido localizable de una fila
     * @param <T> tipo de fila
     * @return contenedor con el campo de búsqueda y la tabla
     */
    public static <T> VBox conBusqueda(TableView<T> tabla, String descripcion,
                                       Function<T, String> texto) {
        // No se copia: algunas pantallas mantienen una ObservableList editable
        // (por ejemplo las líneas de una partida) y el filtro debe reflejar los
        // cambios al instante.
        ObservableList<T> fuente = tabla.getItems();
        FilteredList<T> visibles = new FilteredList<>(fuente, fila -> true);
        AtomicReference<FilteredList<T>> filtroActual = new AtomicReference<>(visibles);
        AtomicBoolean reemplazando = new AtomicBoolean();
        tabla.setItems(visibles);
        // El modo sin constreñir permite ver todas las columnas mediante la barra
        // horizontal, en vez de comprimir títulos y valores hasta volverlos ilegibles.
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinWidth(0);
        tabla.setMaxWidth(Double.MAX_VALUE);
        tabla.setMaxHeight(Double.MAX_VALUE);
        tabla.getStyleClass().add("table-with-search");

        TextField filtro = new TextField();
        filtro.setPromptText("Buscar en " + descripcion + "…");
        filtro.setAccessibleText("Filtrar tabla de " + descripcion);
        filtro.setAccessibleHelp("Escriba texto para filtrar las filas de esta tabla.");
        filtro.getStyleClass().add("table-search-field");
        filtro.textProperty().addListener((obs, anterior, consulta) -> {
            String buscado = normalizar(consulta);
            filtroActual.get().setPredicate(fila -> buscado.isBlank()
                    || normalizar(texto.apply(fila)).contains(buscado));
        });
        // Muchas vistas recargan filas después de guardar. Mantener el filtro
        // instalado evita que una recarga convierta la búsqueda en un adorno.
        tabla.itemsProperty().addListener((obs, anterior, actual) -> {
            if (reemplazando.get() || actual instanceof FilteredList<?>) {
                return;
            }
            FilteredList<T> nuevaVista = new FilteredList<>(actual, fila -> true);
            filtroActual.set(nuevaVista);
            String buscado = normalizar(filtro.getText());
            nuevaVista.setPredicate(fila -> buscado.isBlank()
                    || normalizar(texto.apply(fila)).contains(buscado));
            reemplazando.set(true);
            tabla.setItems(nuevaVista);
            reemplazando.set(false);
        });

        Label ayuda = new Label("Filtro de la tabla");
        ayuda.setLabelFor(filtro);
        ayuda.getStyleClass().add("table-search-label");
        VBox contenedor = new VBox(6, ayuda, filtro, tabla);
        contenedor.getStyleClass().add("table-search-container");
        contenedor.setFillWidth(true);
        contenedor.setMinWidth(0);
        contenedor.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return contenedor;
    }

    /** Actualiza la fuente de una tabla configurada con {@link #conBusqueda}. */
    @SuppressWarnings("unchecked")
    public static <T> void reemplazarFilas(TableView<T> tabla, java.util.Collection<T> filas) {
        if (tabla.getItems() instanceof FilteredList<?> filtrada) {
            ObservableList<T> fuente = (ObservableList<T>) ((FilteredList<?>) filtrada).getSource();
            fuente.setAll(filas);
        } else {
            tabla.setItems(FXCollections.observableArrayList(filas));
        }
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
