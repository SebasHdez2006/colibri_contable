package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Empresa;

import java.io.File;

/**
 * Vista para editar el perfil corporativo y acceder al bloque de períodos.
 */
public class EmpresaPeriodosView extends VBox {

    /**
     * Crea la vista de perfil corporativo con los datos cargados desde el sistema.
     *
     * @param context contexto de aplicación con acceso a la información de empresa
     */
    public EmpresaPeriodosView(AppContext context) {
        Empresa empresa = context.periodos().empresa().orElse(null);
        TextField razon = field(empresa != null ? empresa.getRazonSocial() : "");
        razon.setPromptText("Razón social según escritura");
        UiCatalogos.ayuda(razon, "Nombre legal de la empresa; aparece en encabezados de reportes.");
        TextField comercial = field(empresa != null ? empresa.getNombreComercial() : "");
        comercial.setPromptText("Nombre comercial");
        UiCatalogos.ayuda(comercial, "Nombre de fantasía usado en documentos comerciales.");
        TextField nit = field(empresa != null ? empresa.getNit() : "");
        nit.setPromptText("0614-010101-101-0");
        UiCatalogos.ayuda(nit, "Número de Identificación Tributaria de la empresa.");
        TextField nrc = field(empresa != null ? empresa.getNrc() : "");
        nrc.setPromptText("NRC asignado por el MH");
        UiCatalogos.ayuda(nrc, "Número de Registro de Contribuyente del Ministerio de Hacienda.");
        TextField actividad = field(empresa != null ? nvl(empresa.getActividadEconomica()) : "6201 — Programación informática");
        actividad.setPromptText("Ej. 6201 — Programación informática");
        UiCatalogos.ayuda(actividad, "Actividad económica del catálogo CAT-019; clasifica el giro de la empresa.");
        TextField direccion = field(empresa != null ? nvl(empresa.getDireccionFiscal()) : "");
        direccion.setPromptText("Dirección fiscal completa");
        UiCatalogos.ayuda(direccion, "Domicilio fiscal declarado; se imprime en documentos tributarios.");
        TextField logo = field(empresa != null ? nvl(empresa.getLogoRuta()) : "");
        logo.setPromptText("Ruta del archivo de imagen");
        UiCatalogos.ayuda(logo, "Imagen PNG o JPG que encabeza reportes y documentos; use «Elegir logo».");
        TextField representante = field(empresa != null ? nvl(empresa.getRepresentanteLegal()) : "");
        representante.setPromptText("Nombre completo");
        UiCatalogos.ayuda(representante, "Representante legal que firma los estados financieros.");
        TextField dui = field(empresa != null ? nvl(empresa.getDuiRepresentante()) : "");
        dui.setPromptText("0000-00000-0000");
        UiCatalogos.ayuda(dui, "Documento Único de Identidad del representante legal.");
        TextField contador = field(empresa != null ? nvl(empresa.getContadorNombre()) : "Contador Colibrí");
        contador.setPromptText("Nombre del contador");
        UiCatalogos.ayuda(contador, "Contador que firma y certifica los estados financieros.");
        TextField cvpcpa = field(empresa != null ? nvl(empresa.getRegistroCvpcpa()) : "");
        cvpcpa.setPromptText("Ej. CV-12345");
        UiCatalogos.ayuda(cvpcpa, "Registro ante el Consejo de Vigilancia de la Profesión de Contaduría y Auditoría.");

        ImageView vistaLogo = new ImageView();
        vistaLogo.setFitWidth(96);
        vistaLogo.setFitHeight(96);
        vistaLogo.setPreserveRatio(true);
        vistaLogo.setSmooth(true);
        vistaLogo.setVisible(false);
        vistaLogo.setManaged(false);
        Runnable actualizarVista = () -> {
            String ruta = logo.getText() == null ? "" : logo.getText().trim();
            if (ruta.isBlank()) {
                vistaLogo.setImage(null);
                vistaLogo.setVisible(false);
                vistaLogo.setManaged(false);
                return;
            }
            try {
                Image imagen = new Image(new File(ruta).toURI().toString(), 96, 96, true, true);
                if (!imagen.isError() && imagen.getWidth() > 0) {
                    vistaLogo.setImage(imagen);
                    vistaLogo.setVisible(true);
                    vistaLogo.setManaged(true);
                } else {
                    vistaLogo.setImage(null);
                    vistaLogo.setVisible(false);
                    vistaLogo.setManaged(false);
                }
            } catch (RuntimeException ex) {
                vistaLogo.setImage(null);
                vistaLogo.setVisible(false);
                vistaLogo.setManaged(false);
            }
        };
        logo.textProperty().addListener((o, a, b) -> actualizarVista.run());

        Button elegirLogo = new Button("Elegir logo");
        elegirLogo.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Logo institucional");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (file != null) {
                logo.setText(file.getAbsolutePath());
            }
        });
        actualizarVista.run();

        VBox izquierda = tarjeta("Datos fiscales",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Razón social", razon),
                        ResponsiveLayouts.field("Nombre comercial", comercial)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("NIT", nit),
                        ResponsiveLayouts.field("NRC", nrc),
                        ResponsiveLayouts.field("Actividad económica (CAT-019)", actividad)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Dirección fiscal", direccion)));

        VBox derecha = tarjeta("Logo y firmantes",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Logo institucional", logo), elegirLogo,
                        vistaLogo),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Representante legal", representante),
                        ResponsiveLayouts.field("DUI representante", dui)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Contador firmante", contador),
                        ResponsiveLayouts.field("Registro CVPCPA (colegio de contadores)", cvpcpa)));

        HBox columnas = new HBox(16, izquierda, derecha);
        HBox.setHgrow(izquierda, Priority.ALWAYS);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        izquierda.setMaxWidth(Double.MAX_VALUE);
        derecha.setMaxWidth(Double.MAX_VALUE);

        Button guardar = new Button("Guardar perfil corporativo");
        guardar.setOnAction(e -> {
            try {
                context.periodos().actualizarEmpresa(
                        razon.getText().trim(), comercial.getText().trim(),
                        nit.getText().trim(), nrc.getText().trim(),
                        actividad.getText().trim(), direccion.getText().trim(), logo.getText().trim(),
                        representante.getText().trim(), dui.getText().trim(),
                        contador.getText().trim(), cvpcpa.getText().trim());
                UiDialogs.info("Empresa", "Perfil corporativo actualizado para encabezados y firmas.");
            } catch (DomainException ex) {
                UiDialogs.errorDe(ex);
            }
        });

        Label titulo = new Label("Perfil corporativo");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Perfil corporativo, datos fiscales, logo y firmantes. Los períodos viven en Parametrización.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);
        VBox.setVgrow(columnas, Priority.ALWAYS);
        getChildren().setAll(titulo, ayuda, columnas, guardar);
    }

    private VBox tarjeta(String titulo, javafx.scene.Node... contenido) {
        Label heading = new Label(titulo);
        heading.getStyleClass().add("section-heading");
        heading.setWrapText(true);
        heading.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(12, heading);
        card.getChildren().addAll(contenido);
        card.getStyleClass().addAll("card", "form-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    private TextField field(String value) {
        TextField field = new TextField(value == null ? "" : value);
        return field;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
