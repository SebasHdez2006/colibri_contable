package sv.edu.ues.colibri.desktop.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Contador;

import java.util.function.Consumer;

/**
 * Pantalla de inicio de sesión de la aplicación de escritorio.
 */
public class LoginView extends StackPane {

    /**
     * Crea la vista de autenticación y conecta la acción de acceso exitoso.
     *
     * @param context   contexto de aplicación para invocar servicios de
     *                  autenticación
     * @param onSuccess acción a ejecutar cuando las credenciales sean válidas
     */
    public LoginView(AppContext context, Consumer<Contador> onSuccess) {
        getStyleClass().add("login-root");

        VBox identidad = new VBox(18);
        identidad.getStyleClass().add("login-brand-panel");
        identidad.setMinWidth(260);
        identidad.setMaxWidth(380);
        identidad.setAlignment(Pos.CENTER);
        identidad.setFillWidth(true);
        HBox.setHgrow(identidad, Priority.ALWAYS);
        Label empresa = new Label("Soluciones Empresariales\nColibrí");
        empresa.getStyleClass().add("login-company");
        empresa.setMaxWidth(Double.MAX_VALUE);
        empresa.setAlignment(Pos.CENTER);
        Label lema = new Label("Agilidad inteligente en movimiento");
        lema.getStyleClass().add("login-tagline");
        lema.setMaxWidth(Double.MAX_VALUE);
        lema.setAlignment(Pos.CENTER);
        BrandLogo logoBox = new BrandLogo(176, 132);
        logoBox.getStyleClass().add("login-logo");
        identidad.getChildren().addAll(logoBox, empresa, lema);

        VBox formulario = new VBox(12);
        formulario.getStyleClass().add("login-form");
        HBox.setHgrow(formulario, Priority.ALWAYS);

        Label titulo = new Label("Acceso al sistema");
        titulo.getStyleClass().add("login-title");
        Label subtitulo = new Label("Inicie sesión con sus credenciales");
        subtitulo.getStyleClass().add("login-subtitle");

        TextField usuario = new TextField();
        usuario.setPromptText("Nombre de usuario");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        Button entrar = new Button("Iniciar sesión");
        entrar.setDefaultButton(true);
        entrar.setOnAction(e -> {
            error.setText("");
            try {
                Contador c = context.auth().login(usuario.getText().trim(), password.getText().toCharArray());
                if (c.tieneClaveTemporalPendiente() && !cambiarClaveObligatoria(context)) {
                    context.auth().logout();
                    error.setText("Debe establecer una clave propia para continuar.");
                    return;
                }
                onSuccess.accept(c);
            } catch (DomainException ex) {
                sv.edu.ues.colibri.desktop.AppLog.error("Login rechazado para " + usuario.getText(), ex);
                error.setText(ex.getMessage());
            } catch (Throwable ex) {
                sv.edu.ues.colibri.desktop.AppLog.error("Fallo al abrir el panel", ex);
                error.setText((ex.getClass().getSimpleName()) + ": "
                        + (ex.getMessage() == null ? "revise ~/.colibri/colibri.log" : ex.getMessage()));
            }
        });

        Hyperlink recuperar = new Hyperlink("Recuperar contraseña con código local");
        recuperar.setOnAction(e -> UiDialogs.mostrarRecuperacion(context));

        Label usuarioLabel = new Label("Usuario");
        usuarioLabel.getStyleClass().add("field-label");
        usuarioLabel.setLabelFor(usuario);
        Label passwordLabel = new Label("Contraseña");
        passwordLabel.getStyleClass().add("field-label");
        passwordLabel.setLabelFor(password);
        usuario.setMaxWidth(Double.MAX_VALUE);
        password.setMaxWidth(Double.MAX_VALUE);
        entrar.setMaxWidth(Double.MAX_VALUE);

        formulario.getChildren().addAll(titulo, subtitulo, usuarioLabel, usuario,
                passwordLabel, password, entrar, recuperar, error);

        HBox tarjeta = new HBox(identidad, formulario);
        tarjeta.getStyleClass().add("login-card");
        tarjeta.setAlignment(Pos.CENTER);
        StackPane.setMargin(tarjeta, new Insets(24));
        getChildren().add(tarjeta);

        Runnable adaptar = () -> {
            boolean anchoCompacto = getWidth() < 780;
            boolean compacto = anchoCompacto || getHeight() < 520;
            identidad.setVisible(!anchoCompacto);
            identidad.setManaged(!anchoCompacto);
            tarjeta.getStyleClass().remove("login-card-compact");
            if (compacto) {
                tarjeta.getStyleClass().add("login-card-compact");
                formulario.setMaxWidth(anchoCompacto ? 460 : Double.MAX_VALUE);
            } else {
                formulario.setMaxWidth(Double.MAX_VALUE);
            }
        };
        widthProperty().addListener((observable, anterior, nuevo) -> adaptar.run());
        heightProperty().addListener((observable, anterior, nuevo) -> adaptar.run());

        UiTheme.decorate(this);
    }

    /**
     * Diálogo obligatorio para reemplazar la clave temporal por una clave propia.
     *
     * @param context contexto de aplicación para invocar el cambio de clave
     * @return {@code true} si el usuario guardó una clave válida
     */
    private boolean cambiarClaveObligatoria(AppContext context) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Clave temporal");
        dialog.setHeaderText("Su contraseña actual es una clave temporal emitida por soporte. "
                + "Defina una clave propia de 12 a 128 caracteres para continuar.");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña");
        PasswordField confirma = new PasswordField();
        confirma.setPromptText("Confirmar contraseña");
        Label errorClave = new Label();
        errorClave.getStyleClass().add("error-label");
        VBox contenido = new VBox(8, nueva, confirma, errorClave);
        contenido.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(contenido);
        ButtonType aceptar = new ButtonType("Guardar clave", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(aceptar).addEventFilter(ActionEvent.ACTION, ev -> {
            String clave = nueva.getText();
            if (clave == null || clave.length() < 12 || clave.length() > 128) {
                errorClave.setText("La contraseña debe tener entre 12 y 128 caracteres.");
                ev.consume();
                return;
            }
            if (!clave.equals(confirma.getText())) {
                errorClave.setText("Las contraseñas no coinciden.");
                ev.consume();
                return;
            }
            try {
                context.auth().cambiarClavePropia(clave.toCharArray());
            } catch (Exception ex) {
                errorClave.setText(ex.getMessage());
                ev.consume();
            }
        });
        UiTheme.style(dialog);
        return dialog.showAndWait().filter(tipo -> tipo == aceptar).isPresent();
    }
}
