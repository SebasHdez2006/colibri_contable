package sv.edu.ues.colibri.desktop.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Aplica la hoja de estilo institucional y asigna iconografía semántica a controles.
 */
public final class UiTheme {

    private static final String STYLESHEET = "/sv/edu/ues/colibri/desktop/colibri.css";
    private static final String PROP_VARIANTE = "colibri-variante";
    private static final String CLASE_TEMA_CLARO = "tema-claro";
    private static final String CLASE_TEMA_OSCURO = "tema-oscuro";
    private static final Preferences PREFERENCIAS = Preferences.userNodeForPackage(UiTheme.class);
    private static final String CLAVE_TEMA = "tema";

    /** Variante visual explícita de botón; sustituye a la inferencia por texto. */
    public enum Variante { PRIMARIA, SECUNDARIA, EXITO, PELIGRO }

    private UiTheme() {
    }

    /**
     * Aplica la hoja de estilo de la aplicación a una escena.
     *
     * @param scene escena a decorar
     */
    public static void apply(Scene scene) {
        String css = stylesheetUrl();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        aplicarTema(scene.getRoot());
    }

    /**
     * Aplica la hoja de estilo y el decorado de controles a un diálogo.
     *
     * @param dialog diálogo a decorar
     */
    public static void style(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        String css = stylesheetUrl();
        if (!pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        pane.getStyleClass().add("colibri-dialog");
        aplicarTema(pane);
        decorate(pane);
    }

    /**
     * Tema elegido por el usuario: oscuro por defecto, claro si se activó
     * el switch. La preferencia se guarda por usuario del sistema.
     *
     * @return {@code true} si el tema activo es oscuro
     */
    public static boolean temaOscuro() {
        return !"claro".equals(PREFERENCIAS.get(CLAVE_TEMA, "oscuro"));
    }

    /**
     * Fija el tema y lo recuerda para las próximas ejecuciones.
     *
     * @param oscuro {@code true} para tema oscuro, {@code false} para claro
     */
    public static void fijarTema(boolean oscuro) {
        PREFERENCIAS.put(CLAVE_TEMA, oscuro ? "oscuro" : "claro");
    }

    /**
     * Aplica la clase de tema guardada a un nodo raíz (escena o diálogo)
     * para que todos sus descendientes hereden los tokens semánticos.
     *
     * @param root nodo que recibirá la clase de tema
     */
    public static void aplicarTema(Node root) {
        if (root == null) {
            return;
        }
        root.getStyleClass().removeAll(CLASE_TEMA_CLARO, CLASE_TEMA_OSCURO);
        root.getStyleClass().add(temaOscuro() ? CLASE_TEMA_OSCURO : CLASE_TEMA_CLARO);
    }

    /**
     * Recorre un árbol visual y aplica decoración automática a sus controles.
     *
     * @param root nodo raíz del árbol visual
     */
    public static void decorate(Parent root) {
        decorateNode(root);
    }

    private static void decorateNode(Node node) {
        if (node instanceof Button button) {
            decorateButton(button);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(UiTheme::decorateNode);
        }
    }

    /**
     * Declara la variante visual de un botón de forma explícita. Tiene
     * prioridad sobre la inferencia por texto, que se mantiene como
     * compatibilidad transitoria para las vistas aún no migradas.
     *
     * @param button botón a decorar
     * @param variante variante visual a aplicar
     */
    public static void variante(Button button, Variante variante) {
        button.getProperties().put(PROP_VARIANTE, variante);
        decorateButton(button);
    }

    /**
     * Crea un botón con variante explícita ya aplicada.
     *
     * @param texto etiqueta del botón
     * @param variante variante visual
     * @return botón listo para usar
     */
    public static Button boton(String texto, Variante variante) {
        Button button = new Button(texto);
        variante(button, variante);
        return button;
    }

    /**
     * Asigna icono y estilo de color a un botón según su etiqueta textual.
     *
     * @param button botón a decorar
     */
    public static void decorateButton(Button button) {
        String text = Objects.requireNonNullElse(button.getText(), "").toLowerCase(Locale.ROOT);
        boolean automaticIcon = Boolean.TRUE.equals(button.getProperties().get("colibri-auto-icon"));
        if (button.getGraphic() == null || automaticIcon) {
            button.setGraphic(BrandIcon.create(iconFor(text)));
            button.getProperties().put("colibri-auto-icon", true);
        }

        button.getStyleClass().removeAll(
                "primary-button", "secondary-button", "success-button", "danger-button");

        Variante variante = (Variante) button.getProperties().get(PROP_VARIANTE);
        if (variante == Variante.PELIGRO) {
            addClass(button, "danger-button");
        } else if (variante == Variante.EXITO) {
            addClass(button, "success-button");
        } else if (variante == Variante.PRIMARIA) {
            addClass(button, "primary-button");
        } else if (variante == Variante.SECUNDARIA) {
            addClass(button, "secondary-button");
        } else if (containsAny(text, "anular", "cerrar período", "desactivar", "quitar")) {
            addClass(button, "danger-button");
        } else if (containsAny(text, "aprobar", "contabilizar", "activar")) {
            addClass(button, "success-button");
        } else if (containsAny(text, "iniciar sesión", "guardar", "generar reporte", "crear usuario",
                "registrar y contabilizar", "ir a registros", "exportar paquete",
                "nuevo usuario", "probar conexión", "generar respaldo", "desbloquear")) {
            addClass(button, "primary-button");
        } else {
            addClass(button, "secondary-button");
        }

        if (!Boolean.TRUE.equals(button.getProperties().get("colibri-text-listener"))) {
            button.getProperties().put("colibri-text-listener", true);
            button.textProperty().addListener((observable, oldText, newText) -> decorateButton(button));
        }
    }

    private static BrandIcon.Symbol iconFor(String text) {
        if (containsAny(text, "iniciar sesión")) return BrandIcon.Symbol.LOGIN;
        if (containsAny(text, "cerrar sesión")) return BrandIcon.Symbol.LOGOUT;
        if (containsAny(text, "guardar")) return BrandIcon.Symbol.SAVE;
        if (containsAny(text, "editar")) return BrandIcon.Symbol.EDIT;
        if (containsAny(text, "cambiar estado", "activar", "desactivar")) return BrandIcon.Symbol.SWITCH;
        if (containsAny(text, "agregar", "nueva", "crear")) return BrandIcon.Symbol.PLUS;
        if (containsAny(text, "quitar")) return BrandIcon.Symbol.MINUS;
        if (containsAny(text, "aprobar", "contabilizar", "registrar", "liberar", "reconocer", "liquidar")) {
            return BrandIcon.Symbol.CHECK;
        }
        if (containsAny(text, "anular", "cerrar período")) return BrandIcon.Symbol.CANCEL;
        if (containsAny(text, "revertir")) return BrandIcon.Symbol.UNDO;
        if (containsAny(text, "cargar", "refrescar")) return BrandIcon.Symbol.REFRESH;
        if (containsAny(text, "exportar")) return BrandIcon.Symbol.DOWNLOAD;
        if (containsAny(text, "generar respaldo")) return BrandIcon.Symbol.BACKUP;
        if (containsAny(text, "generar reporte")) return BrandIcon.Symbol.REPORT;
        if (containsAny(text, "vender")) return BrandIcon.Symbol.MONEY;
        if (containsAny(text, "partidas")) return BrandIcon.Symbol.JOURNAL;
        return BrandIcon.Symbol.PLAY;
    }

    private static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) return true;
        }
        return false;
    }

    private static void addClass(Node node, String styleClass) {
        if (!node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    /**
     * URL externa de la hoja de estilo principal. Los popups y ventanas
     * auxiliares la registran en su propia escena para heredar los tokens.
     *
     * @return URL de {@code colibri.css}
     */
    public static String hojaEstilos() {
        return stylesheetUrl();
    }

    private static String stylesheetUrl() {
        URL resource = UiTheme.class.getResource(STYLESHEET);
        return Objects.requireNonNull(resource, "No se encontró " + STYLESHEET).toExternalForm();
    }
}
