package sv.edu.ues.colibri.desktop.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Acceso perezoso a los recursos gráficos oficiales de la identidad Colibrí
 * empaquetados en {@code /sv/edu/ues/colibri/desktop/img/}.
 */
public final class BrandAssets {

    private static final String BASE = "/sv/edu/ues/colibri/desktop/img/";

    private static final Path ICONO_EMPRESA_PNG = Paths.get("D:\\Backup_Sebas\\UES\\Ciclo 6\\SIC115\\Proyecto\\Colibri\\img\\Colibri_C_fondo_blanco_256px.png");

    private static Image marca;
    private static Image imagotipo;
    private static Image splash;
    private static List<Image> iconos;

    private BrandAssets() {
    }

    /** Símbolo del colibrí (PNG con transparencia) para fondos claros. */
    public static Image marca() {
        if (marca == null) {
            marca = cargar("brand-mark.png");
        }
        return marca;
    }

    /** Imagotipo completo con nombre y lema de la empresa. */
    public static Image imagotipo() {
        if (imagotipo == null) {
            imagotipo = cargar("brand-imagotipo.jpg");
        }
        return imagotipo;
    }

    /** Splash screen de arranque. */
    public static Image splash() {
        if (splash == null) {
            splash = cargar("splash.png");
        }
        return splash;
    }

    /** Iconos para la barra de tareas y el marco de la ventana. */
    public static List<Image> iconosVentana() {
        if (iconos == null) {
            iconos = List.of(cargarIconoEmpresaPng());
        }
        return iconos;
    }

    /**
     * Obtiene una vista escalada del símbolo de marca.
     *
     * @param lado tamaño deseado en ancho y alto
     * @return vista de imagen lista para insertar en la interfaz
     */
    public static ImageView vistaMarca(double lado) {
        return ajustar(marca(), lado, lado);
    }

    /**
     * Obtiene una vista escalada del imagotipo completo.
     *
     * @param ancho ancho deseado de la imagen
     * @return vista de imagen lista para insertar en la interfaz
     */
    public static ImageView vistaImagotipo(double ancho) {
        return ajustar(imagotipo(), ancho, 0);
    }

    private static ImageView ajustar(Image imagen, double ancho, double alto) {
        ImageView vista = new ImageView(imagen);
        vista.setSmooth(true);
        vista.setPreserveRatio(true);
        if (ancho > 0) {
            vista.setFitWidth(ancho);
        }
        if (alto > 0) {
            vista.setFitHeight(alto);
        }
        return vista;
    }

    private static Image cargarIconoEmpresaPng() {
        Image png = cargarDesdeArchivoSiExiste(ICONO_EMPRESA_PNG);
        if (png != null) {
            return png;
        }
        return cargar("icon-256.png");
    }

    private static Image cargarDesdeArchivoSiExiste(Path ruta) {
        if (!Files.exists(ruta)) {
            return null;
        }
        Image imagen = new Image(ruta.toUri().toString());
        if (imagen.isError()) {
            return null;
        }
        return imagen;
    }

    private static Image cargar(String recurso) {
        URL url = BrandAssets.class.getResource(BASE + recurso);
        if (url == null) {
            throw new IllegalStateException("Recurso gráfico no encontrado: " + BASE + recurso);
        }
        return new Image(url.toExternalForm());
    }
}
