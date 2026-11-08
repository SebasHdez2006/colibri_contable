package sv.edu.ues.colibri.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Logotipo oficial de Colibrí montado sobre una placa clara. La placa permite
 * que la parte navy del arte (cabeza y pico) se distinga sobre los fondos
 * oscuros del panel de acceso y del menú lateral.
 */
public final class BrandLogo extends StackPane {

    /**
     * Crea una placa con el logotipo institucional centrado.
     *
     * @param ladoPlaca lado del contenedor cuadrado
     * @param ladoMarca tamaño de la imagen de marca
     */
    public BrandLogo(double ladoPlaca, double ladoMarca) {
        getStyleClass().add("brand-logo-badge");
        setMinSize(ladoPlaca, ladoPlaca);
        setPrefSize(ladoPlaca, ladoPlaca);
        setMaxSize(ladoPlaca, ladoPlaca);
        setAlignment(Pos.CENTER);
        setMouseTransparent(true);

        ImageView vista = BrandAssets.vistaMarca(ladoMarca);
        vista.setAccessibleText("Logotipo de Soluciones Empresariales Colibrí");
        vista.setMouseTransparent(true);
        getChildren().add(vista);
    }
}
