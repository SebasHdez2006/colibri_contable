package sv.edu.ues.colibri.desktop.ui.layout;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Dos secciones repartiendo el ancho al 50 % cada una. En el nivel compacto de
 * {@link Breakpoints} se apilan en una sola columna; en los demás niveles se
 * colocan lado a lado con la misma anchura y altura.
 */
public final class DosColumnas extends GridPane {

    private final Region izquierda;
    private final Region derecha;

    /**
     * Crea la pareja de secciones.
     *
     * @param izquierda sección superior/izquierda
     * @param derecha   sección inferior/derecha
     */
    public DosColumnas(Region izquierda, Region derecha) {
        this.izquierda = izquierda;
        this.derecha = derecha;
        getStyleClass().add("two-col");
        setHgap(14);
        setVgap(14);
        izquierda.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        derecha.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getChildren().addAll(izquierda, derecha);
        sceneProperty().addListener((o, a, escena) -> Breakpoints.de(this).ifPresent(bp -> {
            bp.nivelProperty().addListener((x, y, nivel) -> distribuir(nivel == Breakpoints.Nivel.COMPACT));
            distribuir(bp.nivel() == Breakpoints.Nivel.COMPACT);
        }));
        distribuir(false);
    }

    private void distribuir(boolean compacto) {
        ColumnConstraints mitad = new ColumnConstraints();
        mitad.setPercentWidth(compacto ? 100 : 50);
        mitad.setHgrow(Priority.ALWAYS);
        getColumnConstraints().setAll(compacto ? List.of(mitad) : List.of(mitad, mitad));
        GridPane.setConstraints(izquierda, 0, 0);
        GridPane.setConstraints(derecha, compacto ? 0 : 1, compacto ? 1 : 0);
    }
}
