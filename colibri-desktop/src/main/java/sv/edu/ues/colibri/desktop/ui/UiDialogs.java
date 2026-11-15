package sv.edu.ues.colibri.desktop.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.domain.exception.DomainException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Utilidades de diálogo para mostrar mensajes, confirmaciones y formularios simples.
 */
public final class UiDialogs {

    private UiDialogs() {
    }

    /**
     * Muestra un mensaje de error modal.
     *
     * @param mensaje texto que se presentará al usuario
     */
    public static void error(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje, ButtonType.OK);
        alert.setHeaderText("Error");
        UiTheme.style(alert);
        alert.showAndWait();
    }

    /**
     * Muestra un mensaje informativo modal.
     *
     * @param titulo encabezado visible del cuadro de diálogo
     * @param mensaje contenido del mensaje
     */
    public static void info(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje, ButtonType.OK);
        alert.setHeaderText(titulo);
        UiTheme.style(alert);
        alert.showAndWait();
    }

    /**
     * Solicita confirmación al usuario.
     *
     * @param titulo encabezado visible del cuadro de diálogo
     * @param mensaje contenido de la confirmación
     * @return {@code true} si el usuario acepta
     */
    public static boolean confirmar(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(titulo);
        UiTheme.style(alert);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /**
     * Presenta el flujo local de recuperación de contraseña mediante código de un solo uso.
     *
     * @param context contexto de aplicación con acceso al servicio de autenticación
     */
    public static void mostrarRecuperacion(AppContext context) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Recuperación local");
        dialog.setHeaderText("Código de un solo uso (archivo ~/.colibri/recovery-code.txt)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField usuario = new TextField();
        usuario.setPromptText("Nombre de usuario");
        TextField codigo = new TextField();
        PasswordField nueva = new PasswordField();
        PasswordField confirmar = new PasswordField();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        Label usuarioLabel = new Label("Usuario");
        usuarioLabel.setLabelFor(usuario);
        Label codigoLabel = new Label("Código de recuperación");
        codigoLabel.setLabelFor(codigo);
        Label nuevaLabel = new Label("Nueva contraseña");
        nuevaLabel.setLabelFor(nueva);
        Label confirmarLabel = new Label("Confirmar contraseña");
        confirmarLabel.setLabelFor(confirmar);
        grid.addRow(0, usuarioLabel, usuario);
        grid.addRow(1, codigoLabel, codigo);
        grid.addRow(2, nuevaLabel, nueva);
        grid.addRow(3, confirmarLabel, confirmar);
        Label pista = new Label("La contraseña debe tener entre 12 y 128 caracteres "
                + "y coincidir en ambos campos.");
        pista.getStyleClass().add("muted-caption");
        pista.setWrapText(true);
        VBox contenido = new VBox(8, grid, pista);
        dialog.getDialogPane().setContent(contenido);
        UiTheme.style(dialog);

        // La validación corre antes de cerrar: si falla, el diálogo permanece abierto.
        AtomicReference<String> nuevoCodigo = new AtomicReference<>();
        Button aceptar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        aceptar.addEventFilter(ActionEvent.ACTION, e -> {
            String fallo = validarRecuperacion(usuario, codigo, nueva, confirmar);
            if (fallo != null) {
                error(fallo);
                e.consume();
                return;
            }
            try {
                nuevoCodigo.set(context.auth().recuperarConCodigo(
                        usuario.getText(),
                        codigo.getText().trim(),
                        nueva.getText().toCharArray()));
            } catch (DomainException ex) {
                error(ex.getMessage());
                e.consume();
            }
        });

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK && nuevoCodigo.get() != null) {
                info("Contraseña actualizada",
                        "Guarde el nuevo código de recuperación:\n" + nuevoCodigo.get());
            }
        });
    }

    private static String validarRecuperacion(TextField usuario, TextField codigo,
                                              PasswordField nueva, PasswordField confirmar) {
        if (usuario.getText() == null || usuario.getText().isBlank()) {
            return "Ingrese el nombre de usuario.";
        }
        if (codigo.getText() == null || codigo.getText().isBlank()) {
            return "Ingrese el código de recuperación.";
        }
        int largo = nueva.getText() == null ? 0 : nueva.getText().length();
        if (largo < 12 || largo > 128) {
            return "La contraseña debe tener entre 12 y 128 caracteres.";
        }
        if (!nueva.getText().equals(confirmar.getText())) {
            return "La confirmación de la contraseña no coincide.";
        }
        return null;
    }

    /**
     * Muestra un diálogo informativo con contenido arbitrario (tablas, formularios).
     *
     * @param titulo   encabezado visible del cuadro de diálogo
     * @param contenido nodo raíz del contenido
     */
    public static void mostrar(String titulo, Node contenido) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.setHeaderText(titulo);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setContent(contenido);
        UiTheme.style(dialog);
        dialog.showAndWait();
    }

    /**
     * Solicita una cadena de texto al usuario.
     *
     * @param titulo título de la ventana
     * @param prompt mensaje de guía
     * @return texto ingresado o {@code null} si el usuario cancela
     */
    public static String pedirTexto(String titulo, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(titulo);
        dialog.setHeaderText(prompt);
        UiTheme.style(dialog);
        return dialog.showAndWait().orElse(null);
    }
}
