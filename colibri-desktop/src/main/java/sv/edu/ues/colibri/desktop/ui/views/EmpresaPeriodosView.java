package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.desktop.ui.ModuleView;
import javafx.stage.FileChooser;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
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
        setSpacing(0);
        setPadding(new Insets(0));

        Empresa empresa = context.periodos().empresa().orElse(null);
        TextField razon = field(empresa != null ? empresa.getRazonSocial() : "");
        TextField comercial = field(empresa != null ? empresa.getNombreComercial() : "");
        TextField nit = field(empresa != null ? empresa.getNit() : "");
        TextField nrc = field(empresa != null ? empresa.getNrc() : "");
        TextField actividad = field(empresa != null ? nvl(empresa.getActividadEconomica()) : "6201 — Programación informática");
        TextField direccion = field(empresa != null ? nvl(empresa.getDireccionFiscal()) : "");
        TextField logo = field(empresa != null ? nvl(empresa.getLogoRuta()) : "");
        TextField representante = field(empresa != null ? nvl(empresa.getRepresentanteLegal()) : "");
        TextField dui = field(empresa != null ? nvl(empresa.getDuiRepresentante()) : "");
        TextField contador = field(empresa != null ? nvl(empresa.getContadorNombre()) : "Contador Colibrí");
        TextField cvpcpa = field(empresa != null ? nvl(empresa.getRegistroCvpcpa()) : "");

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

        VBox perfil = tarjeta("Perfil corporativo e información fiscal",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Razón social", razon),
                        ResponsiveLayouts.field("Nombre comercial", comercial)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("NIT", nit),
                        ResponsiveLayouts.field("NRC", nrc),
                        ResponsiveLayouts.field("Actividad CAT-019", actividad)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Dirección fiscal", direccion)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Logo institucional", logo), elegirLogo),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Representante legal", representante),
                        ResponsiveLayouts.field("DUI representante", dui)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Contador firmante", contador),
                        ResponsiveLayouts.field("Registro CVPCPA", cvpcpa)));

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
                UiDialogs.error(ex.getMessage());
            }
        });

        VBox contenido = new VBox(12, perfil, guardar);
        contenido.setPadding(new Insets(8, 0, 0, 0));
        getChildren().setAll(ModuleView.of("Empresa",
                "Perfil corporativo, datos fiscales, logo y firmantes. Los períodos viven en Parametrización.",
                ModuleView.tab("Perfil corporativo", contenido)));
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
