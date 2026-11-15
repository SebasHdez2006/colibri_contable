package sv.edu.ues.colibri.desktop.ui.nav;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.desktop.AppLog;
import sv.edu.ues.colibri.desktop.ui.Motion;
import sv.edu.ues.colibri.desktop.ui.UiTheme;
import sv.edu.ues.colibri.desktop.ui.layout.Page;

/**
 * Zona central del shell: aloja una sola página a la vez. Si la vista falla al
 * construirse muestra una tarjeta con "Reintentar" en lugar de dejar la zona vacía.
 */
public final class SectionHost extends StackPane {

    private NavItem actual;
    private Node vista;

    /** Crea el contenedor central. */
    public SectionHost() {
        getStyleClass().add("section-host");
        setMinSize(0, 0);
    }

    /**
     * Construye y muestra la sección.
     *
     * @param item hoja de navegación a mostrar
     * @return la vista creada (o la tarjeta de error)
     */
    public Node mostrar(NavItem item) {
        actual = item;
        Node contenido;
        try {
            contenido = item.fabrica().get();
        } catch (Exception ex) {
            AppLog.error("No se pudo cargar " + item.etiqueta(), ex);
            contenido = errorCarga(item, ex);
        }
        if (contenido instanceof Parent parent) {
            if (!contenido.getStyleClass().contains("module-load-error")) {
                contenido.getStyleClass().add("content-view");
            }
            UiTheme.decorate(parent);
        }
        Region pagina = item.pagina() == NavItem.Arquetipo.FILL ? Page.fill(contenido) : Page.scroll(contenido);
        getChildren().setAll(pagina);
        Motion.entrada(pagina);
        vista = contenido;
        return contenido;
    }

    /**
     * Sección mostrada actualmente.
     *
     * @return ítem activo o {@code null}
     */
    public NavItem actual() {
        return actual;
    }

    /**
     * Vista mostrada actualmente.
     *
     * @return nodo raíz de la vista o {@code null}
     */
    public Node vista() {
        return vista;
    }

    private VBox errorCarga(NavItem item, Exception ex) {
        Label detalle = new Label(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        detalle.setWrapText(true);
        Button reintentar = new Button("Reintentar carga");
        reintentar.setOnAction(e -> mostrar(item));
        VBox error = new VBox(10, new Label("No se pudo cargar " + item.etiqueta() + "."), detalle, reintentar);
        error.getStyleClass().addAll("form-card", "module-load-error");
        return error;
    }
}
