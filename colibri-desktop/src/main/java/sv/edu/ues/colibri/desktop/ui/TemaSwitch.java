package sv.edu.ues.colibri.desktop.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

/**
 * Control textual para alternar el tema de color.
 *
 * <p>El estado se expresa con palabras y no con una perilla o pictograma: así
 * el usuario sabe cuál tema está activo antes de pulsar el control.</p>
 */
public final class TemaSwitch extends Pane {

    private final BooleanProperty oscuro = new SimpleBooleanProperty(UiTheme.temaOscuro());
    private final Label etiqueta = new Label();

    public TemaSwitch() {
        getStyleClass().add("tema-switch");
        setFocusTraversable(true);
        etiqueta.getStyleClass().add("tema-switch-label");
        etiqueta.setMouseTransparent(true);
        getChildren().add(etiqueta);
        actualizarEstadoVisual();

        oscuro.addListener((origen, anterior, actual) -> actualizarEstadoVisual());
        setOnMouseClicked(evento -> alternar());
        setOnKeyPressed(evento -> {
            if (evento.getCode() == KeyCode.SPACE || evento.getCode() == KeyCode.ENTER) {
                alternar();
                evento.consume();
            }
        });
    }

    public boolean isOscuro() {
        return oscuro.get();
    }

    public BooleanProperty oscuroProperty() {
        return oscuro;
    }

    /** Alterna el tema, lo persiste y lo aplica a la escena actual. */
    public void alternar() {
        oscuro.set(!oscuro.get());
        UiTheme.fijarTema(oscuro.get());
        Node raiz = getScene() == null ? null : getScene().getRoot();
        UiTheme.aplicarTema(raiz);
    }

    @Override
    protected void layoutChildren() {
        etiqueta.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    private void actualizarEstadoVisual() {
        getStyleClass().removeAll("tema-switch--claro", "tema-switch--oscuro");
        getStyleClass().add(oscuro.get() ? "tema-switch--oscuro" : "tema-switch--claro");
        etiqueta.setText(oscuro.get() ? "Tema oscuro" : "Tema claro");
        String proximaAccion = oscuro.get() ? "Cambiar a tema claro" : "Cambiar a tema oscuro";
        setAccessibleText("Tema actual: " + etiqueta.getText() + ". " + proximaAccion + ".");
        setAccessibleHelp("Cambiar entre el tema claro y oscuro de la aplicación");
    }
}
