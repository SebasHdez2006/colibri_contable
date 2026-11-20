package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Herramienta de revisión visual (no se distribuye): captura cada sección del
 * shell en PNG a los tres anchos de {@code Breakpoints}.
 *
 * <p>Ejecutar headless con Monocle:</p>
 * <pre>
 * mvnw -q -pl colibri-desktop test-compile ^
 *   org.codehaus.mojo:exec-maven-plugin:3.5.0:java ^
 *   "-Dexec.mainClass=sv.edu.ues.colibri.desktop.ui.SnapshotRunner" ^
 *   "-Dexec.classpathScope=test" ^
 *   "-Dglass.platform=Monocle" "-Dmonocle.platform=Headless" "-Dprism.order=sw"
 * </pre>
 *
 * <p>Variables: {@code COLIBRI_SNAP_DB_URL} (por defecto la base sembrada con el
 * Anexo L, {@code colibri_scratch}), {@code COLIBRI_SNAP_DB_USER},
 * {@code COLIBRI_SNAP_DB_PASSWORD} y {@code COLIBRI_SNAP_DIR} (salida, por
 * defecto {@code target/snapshots}).</p>
 */
public final class SnapshotRunner {

    private static final int[][] ANCHOS = {
            {1360, 820},   // WIDE
            {1080, 760},   // MEDIUM
            {660, 640}     // COMPACT
    };

    private SnapshotRunner() {
    }

    public static void main(String[] args) throws Exception {
        String url = env("COLIBRI_SNAP_DB_URL", "jdbc:postgresql://127.0.0.1:5434/colibri_scratch");
        String usuario = env("COLIBRI_SNAP_DB_USER", "colibri");
        String clave = env("COLIBRI_SNAP_DB_PASSWORD", "colibri");
        boolean soporte = "SOPORTE".equalsIgnoreCase(env("COLIBRI_SNAP_ROLE", "CONTADOR"));
        Path salida = Path.of(env("COLIBRI_SNAP_DIR",
                Path.of(System.getProperty("user.dir"), "target", "snapshots").toString()));
        Files.createDirectories(salida);

        CountDownLatch listo = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            listo.countDown();
        });
        if (!listo.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX no arrancó a tiempo");
        }

        AppContext ctx = AppContext.iniciarConConfig(new DatabaseConfig(url, usuario, clave, usuario, clave));
        try {
            ctx.auth().login(soporte ? BootstrapService.USUARIO_SOPORTE : BootstrapService.USUARIO_DEFAULT,
                    (soporte ? BootstrapService.PASSWORD_SOPORTE : BootstrapService.PASSWORD_DEFAULT).toCharArray());
            if (!soporte && !ctx.catalogo().catalogoAprobado()) {
                ctx.catalogo().aprobarCatalogoInicial();
            }
            CompletableFuture<Void> fin = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    preparar(ctx, salida);
                    fin.complete(null);
                } catch (Throwable ex) {
                    fin.completeExceptionally(ex);
                }
            });
            fin.get(180, TimeUnit.SECONDS);
            System.out.println("Capturas en " + salida);
        } finally {
            ctx.close();
            Platform.runLater(Platform::exit);
        }
    }

    private static void preparar(AppContext ctx, Path salida) throws Exception {
        MainShell shell = new MainShell(ctx, () -> { });
        Stage stage = new Stage();
        try {
            for (int[] par : ANCHOS) {
                enFx(() -> {
                    Scene anterior = stage.getScene();
                    if (anterior != null) {
                        anterior.setRoot(new javafx.scene.layout.Region());
                    }
                    Scene scene = new Scene(shell, par[0], par[1]);
                    UiTheme.apply(scene);
                    stage.setScene(scene);
                    stage.show();
                });
                Path dir = salida.resolve(String.valueOf(par[0]));
                Files.createDirectories(dir);
                for (String raiz : shell.menu().idsRaiz()) {
                    NavItem item = shell.menu().item(raiz).orElseThrow();
                    List<String> ids = item.hijos().isEmpty()
                            ? List.of(raiz)
                            : item.hijos().stream().map(NavItem::id).toList();
                    for (String id : ids) {
                        enFx(() -> shell.ir(id));
                        // Las transiciones de navegación duran 140-220 ms. La
                        // evidencia estática debe representar el estado final,
                        // incluido el resaltado del menú, no un fotograma
                        // intermedio del cambio de vista.
                        Thread.sleep(260);
                        enFx(() -> capturar(shell, id, dir));
                    }
                }
            }
        } finally {
            stage.close();
        }
    }

    private static void capturar(MainShell shell, String id, Path dir) throws Exception {
        shell.applyCss();
        shell.layout();
        // SectionHost aplica la entrada visual con un fundido de 220 ms. La
        // captura es una prueba estática, no una reproducción de la animación:
        // fijar el estado final evita PNG vacíos por tomar el frame inicial.
        var host = shell.lookup(".section-host");
        Node pagina = null;
        if (host instanceof javafx.scene.Parent parent && !parent.getChildrenUnmodifiable().isEmpty()) {
            pagina = parent.getChildrenUnmodifiable().getFirst();
        }
        if (pagina != null) {
            pagina.setOpacity(1);
            pagina.setTranslateY(0);
        }
        WritableImage imagen = shell.snapshot(new SnapshotParameters(), null);
        guardar(imagen, dir.resolve(id + ".png"));
    }

    private static void enFx(Ejecucion accion) {
        CompletableFuture<Void> fin = new CompletableFuture<>();
        if (Platform.isFxApplicationThread()) {
            try {
                accion.ejecutar();
                fin.complete(null);
            } catch (Throwable ex) {
                fin.completeExceptionally(ex);
            }
        } else {
            Platform.runLater(() -> {
                try {
                    accion.ejecutar();
                    fin.complete(null);
                } catch (Throwable ex) {
                    fin.completeExceptionally(ex);
                }
            });
        }
        try {
            fin.get(60, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Falló la captura en el hilo JavaFX", ex);
        }
    }

    @FunctionalInterface
    private interface Ejecucion {
        void ejecutar() throws Exception;
    }

    private static void guardar(WritableImage imagen, Path archivo) throws Exception {
        int ancho = (int) imagen.getWidth();
        int alto = (int) imagen.getHeight();
        BufferedImage buf = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = imagen.getPixelReader();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                buf.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buf, "png", archivo.toFile());
    }

    private static String env(String nombre, String defecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
