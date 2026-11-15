package sv.edu.ues.colibri.desktop;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.BrandAssets;
import sv.edu.ues.colibri.desktop.ui.LoginView;
import sv.edu.ues.colibri.desktop.ui.MainShell;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiTheme;

/**
 * Punto de entrada de la aplicación de escritorio Colibrí.
 *
 * Gestiona el arranque visual, la inicialización del contexto de servicios y el
 * cierre ordenado de recursos de infraestructura.
 */
public class ColibriApp extends Application {

    private AppContext context;
    private InstanceLock instanceLock;
    private StackPane splashRoot;

    /**
     * Inicia la interfaz JavaFX, muestra la pantalla de bienvenida y continúa
     * con el login una vez que los servicios están listos.
     *
     * @param stage ventana principal proporcionada por JavaFX
     */
    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> AppLog.error("Hilo " + t.getName(), e));
        stage.getIcons().addAll(BrandAssets.iconosVentana());

        Stage splash = mostrarSplash();
        Thread arranque = new Thread(() -> {
            Arranque fallo = iniciarServicios();
            Platform.runLater(() -> {
                if (fallo != null) {
                    splash.close();
                    alertaFatal(fallo);
                    Platform.exit();
                    return;
                }
                stage.setTitle("Colibrí — Sistema Contable");
                stage.setMinWidth(560);
                stage.setMinHeight(440);
                Scene escena = escenaLogin(stage);
                // Crossfade: el login aparece sobre el splash y ambos se
                // disuelven a la vez; el splash se cierra al terminar.
                escena.getRoot().setOpacity(0);
                stage.setMaximized(false);
                stage.setScene(escena);
                stage.centerOnScreen();
                stage.show();
                FadeTransition salida = new FadeTransition(Duration.millis(340), splashRoot);
                salida.setToValue(0);
                FadeTransition entrada = new FadeTransition(Duration.millis(340), escena.getRoot());
                entrada.setFromValue(0);
                entrada.setToValue(1);
                salida.setOnFinished(e -> splash.close());
                salida.play();
                entrada.play();
            });
        }, "colibri-arranque");
        arranque.setDaemon(true);
        arranque.start();
    }

    /**
     * Inicializa candado de instancia, contexto de aplicación y dependencias de arranque.
     *
     * @return información de error si el arranque falla; {@code null} en caso exitoso
     */
    private Arranque iniciarServicios() {
        try {
            instanceLock = InstanceLock.adquirir();
        } catch (Exception e) {
            AppLog.error("No se pudo adquirir el candado de instancia", e);
            return new Arranque("Instancia en ejecución", e.getMessage());
        }
        try {
            context = AppContext.iniciar();
            AppLog.info("Aplicación iniciada");
            return null;
        } catch (Exception e) {
            AppLog.error("No se pudo iniciar AppContext", e);
            return new Arranque("No se pudo iniciar la aplicación", """
                    Verifique PostgreSQL en 127.0.0.1:5434, la base 'colibri' y
                    %USERPROFILE%\\.colibri\\application.local.properties

                    Detalle: %s
                    """.formatted(e.getMessage()));
        }
    }

    /**
     * Muestra la pantalla de carga mientras se inicializan los servicios.
     *
     * @return stage del splash
     */
    private Stage mostrarSplash() {
        ImageView logo = new ImageView(BrandAssets.splash());
        logo.setAccessibleText("Pantalla de inicio de Soluciones Empresariales Colibrí");
        logo.setSmooth(true);
        logo.setPreserveRatio(true);
        logo.setFitWidth(560);

        StackPane root = new StackPane(logo);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("splash-root");
        splashRoot = root;

        Stage splash = new Stage(StageStyle.TRANSPARENT);
        splash.getIcons().addAll(BrandAssets.iconosVentana());
        Scene scene = new Scene(root, Color.TRANSPARENT);
        UiTheme.apply(scene);
        splash.setScene(scene);
        splash.centerOnScreen();
        splash.show();
        return splash;
    }

    /**
     * Presenta una alerta de error cuando la aplicación no puede arrancar.
     *
     * @param fallo detalle del problema detectado
     */
    private void alertaFatal(Arranque fallo) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Colibrí");
        UiTheme.style(alert);
        alert.setHeaderText(fallo.titulo());
        alert.setContentText(fallo.detalle());
        alert.showAndWait();
    }

    /**
     * Carga la vista de inicio de sesión y, al autenticarse, reemplaza la escena principal.
     *
     * @param stage ventana principal donde se inyecta la escena
     */
    private void mostrarLogin(Stage stage) {
        Scene scene = escenaLogin(stage);
        stage.setMaximized(false);
        stage.setScene(scene);
        stage.centerOnScreen();
        FadeTransition entrada = new FadeTransition(Duration.millis(320), scene.getRoot());
        entrada.setFromValue(0);
        entrada.setToValue(1);
        entrada.play();
    }

    /**
     * Construye la escena de inicio de sesión con su callback de acceso.
     *
     * @param stage ventana principal donde se inyectará la escena
     * @return escena lista para mostrarse o fijarse en el stage
     */
    private Scene escenaLogin(Stage stage) {
        LoginView login = new LoginView(context, usuario -> {
            AppLog.info("Sesión iniciada: " + usuario.getUsuario() + " / " + usuario.getRol());
            MainShell shell = new MainShell(context, () -> mostrarLogin(stage));
            Scene scene = new Scene(shell, 1100, 700);
            UiTheme.apply(scene);
            stage.setTitle(usuario.esSoporteTecnico()
                    ? "Colibrí — Soporte técnico"
                    : "Colibrí — Sistema Contable");
            stage.setScene(scene);
            stage.setMaximized(true);
        });
        ResponsiveLayouts.ajustarAlContenido(login);
        Scene scene = new Scene(login, 920, 600);
        UiTheme.apply(scene);
        return scene;
    }

    /**
     * Libera la sesión y cierra recursos al finalizar la aplicación.
     *
     * @throws Exception si ocurre un error durante el cierre gestionado por JavaFX
     */
    @Override
    public void stop() throws Exception {
        if (context != null) {
            context.auth().logout();
            context.close();
        }
        if (instanceLock != null) {
            instanceLock.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record Arranque(String titulo, String detalle) {
    }
}
