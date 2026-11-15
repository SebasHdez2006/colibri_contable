package sv.edu.ues.colibri.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/** Marca Colibrí del acceso: símbolo oficial sobre la placa de identidad. */
public final class BrandMark extends StackPane {

    /**
     * Crea el contenedor de marca con el isotipo oficial.
     *
     * @param width   ancho disponible del panel
     * @param height  alto disponible del panel
     * @param compact indica si el panel está reducido
     */
    public BrandMark(double width, double height, boolean compact) {
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        setAlignment(Pos.CENTER);
        getStyleClass().add("brand-mark");
        double lado = Math.min(width, height) * (compact ? 0.78 : 0.9);
        ImageView vista = BrandAssets.vistaMarca(lado);
        vista.setMouseTransparent(true);
        getChildren().add(vista);
    }
}
