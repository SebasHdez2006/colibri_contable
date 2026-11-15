package sv.edu.ues.colibri.desktop.ui.nav;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import sv.edu.ues.colibri.desktop.ui.BrandIcon;
import sv.edu.ues.colibri.desktop.ui.UiTheme;

import java.util.List;
import java.util.function.Consumer;

/**
 * Menú flotante de vidrio con las secciones de un módulo. Sustituye al menú
 * contextual nativo y es reutilizable desde el menú lateral y el breadcrumb.
 */
public final class NavFlyout {

    /** Punto de anclaje del flyout respecto al nodo que lo invoca. */
    public enum Posicion {
        /** Debajo del ancla, alineado a su borde izquierdo. */
        DEBAJO,
        /** A la derecha del ancla, a la altura de su borde superior. */
        DERECHA
    }

    private NavFlyout() {
    }

    /**
     * Muestra el flyout debajo del ancla.
     *
     * @return el popup creado (puede no estar visible si el ancla no tiene escena)
     */
    public static Popup mostrar(Node ancla, List<NavItem> items, Consumer<NavItem> accion) {
        return mostrar(ancla, Posicion.DEBAJO, items, accion);
    }

    /**
     * Muestra el flyout en la posición indicada y ejecuta {@code accion} al
     * elegir una sección.
     */
    public static Popup mostrar(Node ancla, Posicion posicion, List<NavItem> items,
                                Consumer<NavItem> accion) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        VBox contenido = new VBox();
        contenido.getStyleClass().add("nav-flyout");
        for (NavItem item : items) {
            Button opcion = new Button(item.etiqueta());
            opcion.setId("popup-" + item.id());
            opcion.getStyleClass().add("nav-flyout-item");
            if (item.icono() != null) {
                opcion.setGraphic(BrandIcon.create(item.icono()));
            }
            opcion.setAccessibleText("Abrir " + item.etiqueta());
            opcion.setOnAction(e -> {
                popup.hide();
                accion.accept(item);
            });
            contenido.getChildren().add(opcion);
        }
        popup.getContent().add(contenido);
        // La escena del popup no hereda la hoja de estilos: se registra al
        // mostrarse para que los tokens -color-* resuelvan igual que en la app.
        popup.setOnShown(e -> {
            var escena = popup.getScene();
            if (escena != null) {
                String css = UiTheme.hojaEstilos();
                if (!escena.getStylesheets().contains(css)) {
                    escena.getStylesheets().add(css);
                }
                contenido.applyCss();
            }
        });

        Window ventana = ancla.getScene() == null ? null : ancla.getScene().getWindow();
        if (ventana == null) {
            return popup;
        }
        var limites = ancla.getBoundsInLocal();
        Point2D origen = posicion == Posicion.DERECHA
                ? ancla.localToScreen(new Point2D(limites.getWidth() + 6, 0))
                : ancla.localToScreen(new Point2D(0, limites.getHeight() + 4));
        popup.show(ventana, origen.getX(), origen.getY());
        return popup;
    }
}
