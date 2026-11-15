package sv.edu.ues.colibri.desktop.ui.layout;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Arquetipos de página. Cada sección del menú lateral ocupa exactamente una
 * página; el desplazamiento vive aquí y no en el shell.
 */
public final class Page {

    private Page() {
    }

    /**
     * Página con un único {@link ScrollPane}: la vista crece hasta el alto de la
     * ventana (para que las tablas con {@code vgrow} aprovechen el espacio) y
     * desplaza solo cuando su contenido mínimo no cabe.
     *
     * @param vista contenido de la sección
     * @return región lista para insertarse en el centro del shell
     */
    public static Region scroll(Node vista) {
        VBox cuerpo = new VBox(vista);
        cuerpo.setFillWidth(true);
        cuerpo.setMaxWidth(Double.MAX_VALUE);
        if (vista instanceof Region region) {
            VBox.setVgrow(region, Priority.ALWAYS);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        cuerpo.getStyleClass().add("page-body");
        ScrollPane scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinHeight(0);
        scroll.getStyleClass().addAll("page-scroll", "section-scroll-pane");
        return scroll;
    }

    /**
     * Página sin desplazamiento externo: la vista recibe todo el espacio y se
     * encarga de su propio desbordamiento (listas, tablas, paneles internos).
     *
     * @param vista contenido de la sección
     * @return región lista para insertarse en el centro del shell
     */
    public static Region fill(Node vista) {
        BorderPane pagina = new BorderPane(vista);
        pagina.setMinHeight(0);
        pagina.getStyleClass().addAll("page-body", "page-fill");
        return pagina;
    }
}
