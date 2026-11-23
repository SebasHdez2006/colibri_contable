package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;

/**
 * Vista de consulta y mantenimiento básico del catálogo de cuentas.
 */
public class CatalogoView extends VBox {

    private final AppContext context;
    private final TableView<Cuenta> tabla = new TableView<>();

    /**
     * Crea la vista del catálogo con acciones de edición y cambio de estado.
     *
     * @param context contexto de aplicación con acceso al servicio de catálogo
     */
    public CatalogoView(AppContext context) {
        this.context = context;
        setSpacing(16);
        setPadding(new Insets(16));

        Label titulo = new Label("Catálogo de cuentas");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Vista rápida del catálogo. La creación de subcuentas, naturaleza y posting está en Parametrización.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("view-subtitle");

        TableColumn<Cuenta, String> colCodigo = new TableColumn<>("Código");
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        TableColumn<Cuenta, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setPrefWidth(220);
        colNombre.setMinWidth(130);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        TableColumn<Cuenta, String> colNat = new TableColumn<>("Naturaleza");
        colNat.setCellValueFactory(c -> new SimpleStringProperty(switch (c.getValue().getNaturaleza()) {
            case DEUDORA -> "Deudora";
            case ACREEDORA -> "Acreedora";
            case AMBAS -> "Deudora y acreedora";
        }));
        TableColumn<Cuenta, String> colEst = new TableColumn<>("Estado");
        colEst.setCellValueFactory(c -> new SimpleStringProperty(switch (c.getValue().getEstado()) {
            case ACTIVA -> "Activa";
            case DESACTIVADA -> "Desactivada";
            case BLOQUEADA -> "Bloqueada";
        }));
        TableColumn<Cuenta, String> colMov = new TableColumn<>("Movimiento");
        colMov.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isAdmiteMovimiento() ? "Sí" : "No"));
        tabla.getColumns().addAll(colCodigo, colNombre, colNat, colEst, colMov);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        refrescar();

        Button editar = new Button("Editar denominación");
        editar.setOnAction(e -> {
            Cuenta sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) {
                return;
            }
            String nuevo = UiDialogs.pedirTexto("Denominación", "Nuevo nombre para " + sel.getCodigo());
            if (nuevo != null && !nuevo.isBlank()) {
                try {
                    context.catalogo().actualizarDenominacion(sel.getCodigo(), nuevo.trim());
                    refrescar();
                } catch (DomainException ex) {
                    UiDialogs.errorDe(ex);
                }
            }
        });

        ComboBox<EstadoCuenta> estadoBox = new ComboBox<>(FXCollections.observableArrayList(EstadoCuenta.values()));
        estadoBox.setConverter(UiCatalogos.converterEstadoCuenta());
        estadoBox.setPromptText("Nuevo estado");
        estadoBox.setTooltip(new Tooltip("Estado al que pasará la cuenta seleccionada."));
        Button cambiarEstado = new Button("Cambiar estado");
        cambiarEstado.setOnAction(e -> {
            Cuenta sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null || estadoBox.getValue() == null) {
                return;
            }
            try {
                context.catalogo().cambiarEstado(sel.getCodigo(), estadoBox.getValue());
                refrescar();
            } catch (DomainException ex) {
                UiDialogs.errorDe(ex);
            }
        });

        TextField filtro = new TextField();
        filtro.setPromptText("Filtrar por código o nombre");
        UiCatalogos.ayuda(filtro, "Escriba parte del código o de la denominación para filtrar la tabla.");
        filtro.textProperty().addListener((o, a, b) -> filtrar(b));

        var acciones = ResponsiveLayouts.row(editar, estadoBox, cambiarEstado);
        acciones.getStyleClass().add("toolbar");
        getChildren().addAll(titulo, ayuda, filtro, tabla, acciones);
    }

    private void refrescar() {
        tabla.setItems(FXCollections.observableArrayList(context.catalogo().listar()));
    }

    private void filtrar(String texto) {
        String t = texto == null ? "" : texto.toLowerCase();
        tabla.setItems(FXCollections.observableArrayList(
                context.catalogo().listar().stream()
                        .filter(c -> c.getCodigo().contains(t) || c.getNombre().toLowerCase().contains(t))
                        .toList()));
    }
}
