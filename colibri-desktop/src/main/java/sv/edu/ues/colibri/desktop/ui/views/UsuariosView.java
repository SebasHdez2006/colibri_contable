package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.domain.model.Contador;

import java.time.format.DateTimeFormatter;

public class UsuariosView extends VBox {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppContext context;
    private final TableView<Contador> tabla = new TableView<>();

    public UsuariosView(AppContext context) {
        this.context = context;
        setSpacing(12);
        setPadding(new Insets(16));

        Label titulo = new Label("Usuarios");
        titulo.getStyleClass().add("view-title");

        TableColumn<Contador, String> usuario = new TableColumn<>("Usuario");
        usuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuario()));
        TableColumn<Contador, String> nombre = new TableColumn<>("Nombre");
        nombre.setPrefWidth(240);
        nombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        TableColumn<Contador, String> estado = new TableColumn<>("Estado");
        estado.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isActivo() ? "Activo" : "Inactivo"));
        TableColumn<Contador, String> acceso = new TableColumn<>("Último acceso");
        acceso.setPrefWidth(160);
        acceso.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUltimoAcceso() == null
                ? "Sin acceso" : c.getValue().getUltimoAcceso().format(FECHA_HORA)));
        tabla.getColumns().addAll(usuario, nombre, estado, acceso);

        TextField nuevoUsuario = new TextField();
        TextField nuevoNombre = new TextField();
        PasswordField nuevaPassword = new PasswordField();
        Button crear = new Button("Crear usuario");
        crear.setOnAction(e -> {
            try {
                String codigo = context.auth().crearUsuario(nuevoUsuario.getText(), nuevoNombre.getText(),
                        nuevaPassword.getText().toCharArray());
                UiDialogs.info("Usuario creado", "Entregue a la persona su código de recuperación:\n" + codigo);
                nuevoUsuario.clear();
                nuevoNombre.clear();
                nuevaPassword.clear();
                refrescar();
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });

        Button cambiarEstado = new Button("Desactivar usuario");
        cambiarEstado.setOnAction(e -> {
            Contador seleccionado = tabla.getSelectionModel().getSelectedItem();
            if (seleccionado == null) {
                return;
            }
            try {
                context.auth().cambiarEstadoUsuario(seleccionado.getId(), !seleccionado.isActivo());
                refrescar();
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, anterior, seleccionado) -> {
            cambiarEstado.setText(seleccionado != null && !seleccionado.isActivo()
                    ? "Activar usuario" : "Desactivar usuario");
            cambiarEstado.setDisable(seleccionado == null
                    || seleccionado.getId().equals(context.auth().getSesionActual().getId()));
        });

        var formulario = ResponsiveLayouts.row(
            ResponsiveLayouts.field("Nombre de usuario", nuevoUsuario),
            ResponsiveLayouts.field("Nombre completo", nuevoNombre),
            ResponsiveLayouts.field("Contraseña inicial", nuevaPassword), crear);
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(titulo, formulario, UiTables.conBusqueda(tabla, "usuarios", usuarioFila ->
                usuarioFila.getUsuario() + " " + usuarioFila.getNombre() + " "
                        + usuarioFila.getRol().etiqueta()), cambiarEstado);
        refrescar();
    }

    private void refrescar() {
        tabla.setItems(FXCollections.observableArrayList(context.auth().listarUsuarios()));
    }
}
