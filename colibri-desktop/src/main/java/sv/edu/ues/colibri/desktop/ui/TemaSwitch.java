package sv.edu.ues.colibri.desktop.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Interruptor Claro/Oscuro del shell. La perilla se desliza sobre la pista
 * en 160 ms; el estado se persiste con {@link UiTheme#fijarTema(boolean)}
 * y se refleja en la raíz de la escena actual.
 */
public class TemaSwitch extends Pane {

    private static final double DURACION_MS = 160;

    private final BooleanProperty oscuro = new SimpleBooleanProperty(UiTheme.temaOscuro());
    private final DoubleProperty avance = new SimpleDoubleProperty();
    private final Region perilla = new Region();
    private Timeline animacion;

    public TemaSwitch() {
        getStyleClass().add("tema-switch");
        setFocusTraversable(true);
        perilla.getStyleClass().add("tema-switch-knob");
        getChildren().add(perilla);
        avance.set(oscuro.get() ? 0 : 1);
        actualizarEstadoVisual();

        oscuro.addListener((o, anterior, actual) -> deslizar());
        setOnMouseClicked(e -> alternar());
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                alternar();
                e.consume();
            }
        });
    }

    /**
     * Estado actual del interruptor.
     *
     * @return {@code true} cuando el tema elegido es oscuro
     */
    public boolean isOscuro() {
        return oscuro.get();
    }

    /**
     * Propiedad observable del estado para vincularla en pruebas o vistas.
     *
     * @return propiedad booleana del tema
     */
    public BooleanProperty oscuroProperty() {
        return oscuro;
    }

    /**
     * Alterna el tema, lo persiste y lo aplica a la escena actual.
     */
    public void alternar() {
        oscuro.set(!oscuro.get());
        UiTheme.fijarTema(oscuro.get());
        Node raiz = getScene() == null ? null : getScene().getRoot();
        UiTheme.aplicarTema(raiz);
        actualizarEstadoVisual();
    }

    @Override
    protected void layoutChildren() {
        double knobW = perilla.prefWidth(-1);
        double knobH = perilla.prefHeight(-1);
        if (knobW <= 0) {
            knobW = 14;
        }
        if (knobH <= 0) {
            knobH = 14;
        }
        double margen = Math.max(2, (getHeight() - knobH) / 2);
        double recorrido = Math.max(0, getWidth() - knobW - 2 * margen);
        perilla.resizeRelocate(knobW, knobH, margen, (getHeight() - knobH) / 2);
        perilla.setTranslateX(avance.get() * recorrido);
    }

    private void deslizar() {
        if (animacion != null) {
            animacion.stop();
        }
        animacion = new Timeline(new KeyFrame(Duration.millis(DURACION_MS),
                new KeyValue(avance, oscuro.get() ? 0 : 1)));
        animacion.play();
    }

    private void actualizarEstadoVisual() {
        getStyleClass().removeAll("tema-switch--claro", "tema-switch--oscuro");
        getStyleClass().add(oscuro.get() ? "tema-switch--oscuro" : "tema-switch--claro");
        String texto = oscuro.get()
                ? "Tema oscuro. Activar para cambiar a claro."
                : "Tema claro. Activar para cambiar a oscuro.";
        setAccessibleText(texto);
        setAccessibleHelp("Cambiar entre el tema oscuro y el tema claro de la aplicación");
    }
}
