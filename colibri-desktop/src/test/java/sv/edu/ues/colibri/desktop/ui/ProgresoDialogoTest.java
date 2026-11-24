package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replica el mecanismo de {@code ReportesView#conProgreso} para verificar que
 * el diálogo de avance se cierra solo cuando la tarea de exportación termina.
 * Se omite si JavaFX no está disponible en el entorno de pruebas.
 */
class ProgresoDialogoTest {

    private static boolean disponible;

    @BeforeAll
    static void iniciarJavaFx() {
        try {
            CountDownLatch listo = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                listo.countDown();
            });
            disponible = listo.await(15, TimeUnit.SECONDS);
        } catch (IllegalStateException yaIniciado) {
            disponible = true;
        } catch (Exception ex) {
            disponible = false;
            System.err.println("JavaFX no disponible: " + ex.getMessage());
        }
    }

    @AfterAll
    static void detenerJavaFx() {
        if (disponible) {
            Platform.exit();
        }
    }

    @Test
    void elDialogoDeProgresoSeCierraAlTerminarLaTarea() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(disponible, "JavaFX no disponible");
        AtomicReference<Dialog<Void>> dialogoRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch mostrado = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Task<Void> tarea = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(400);
                        return null;
                    }
                };
                Dialog<Void> dialogo = construirComoConProgreso(tarea, () -> { });
                dialogoRef.set(dialogo);
                dialogo.show();
                mostrado.countDown();
                Thread hilo = new Thread(tarea, "test-exportacion");
                hilo.setDaemon(true);
                hilo.start();
            } catch (Throwable t) {
                error.set(t);
                mostrado.countDown();
            }
        });

        assertTrue(mostrado.await(15, TimeUnit.SECONDS), "El diálogo no se mostró");
        assertTrue(error.get() == null, "Error al montar el diálogo: " + error.get());

        Dialog<Void> dialogo = dialogoRef.get();
        assertTrue(dialogo.isShowing(), "El diálogo debería estar visible mientras corre la tarea");

        // La tarea tarda ~400 ms; el cierre debe ocurrir poco después en el hilo FX.
        long limite = System.currentTimeMillis() + 10_000;
        while (dialogo.isShowing() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertFalse(dialogo.isShowing(),
                "El diálogo de progreso quedó abierto después de terminar la tarea");
    }

    @Test
    void elDialogoDeProgresoSeCierraAunqueTarde() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(disponible, "JavaFX no disponible");
        AtomicReference<Dialog<Void>> dialogoRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch mostrado = new CountDownLatch(1);
        AtomicBoolean termino = new AtomicBoolean();

        Platform.runLater(() -> {
            try {
                Task<Void> tarea = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        for (int i = 0; i < 20; i++) {
                            updateMessage("Paso " + i);
                            Thread.sleep(100);
                        }
                        termino.set(true);
                        return null;
                    }
                };
                Dialog<Void> dialogo = construirComoConProgreso(tarea, () -> { });
                dialogoRef.set(dialogo);
                dialogo.show();
                mostrado.countDown();
                Thread hilo = new Thread(tarea, "test-exportacion-lenta");
                hilo.setDaemon(true);
                hilo.start();
            } catch (Throwable t) {
                error.set(t);
                mostrado.countDown();
            }
        });

        assertTrue(mostrado.await(15, TimeUnit.SECONDS));
        Dialog<Void> dialogo = dialogoRef.get();
        // A mitad de la tarea larga el diálogo debe seguir abierto.
        Thread.sleep(600);
        assertTrue(dialogo.isShowing(), "El diálogo no debería cerrarse antes de terminar");

        long limite = System.currentTimeMillis() + 15_000;
        while (dialogo.isShowing() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertTrue(termino.get(), "La tarea debería haber terminado");
        assertFalse(dialogo.isShowing(),
                "El diálogo de progreso quedó abierto después de terminar la tarea lenta");
    }

    /**
     * Reconstruye el diálogo exactamente como {@code ReportesView#conProgreso}
     * para aislar el mecanismo de cierre del resto de la vista.
     */
    private static Dialog<Void> construirComoConProgreso(Task<?> tarea, Runnable alTerminar) {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle("Exportar");
        dialogo.setHeaderText("Generando reporte");
        dialogo.getDialogPane().getButtonTypes().clear();
        dialogo.setOnCloseRequest(e -> {
            if (!tarea.isDone()) {
                e.consume();
            }
        });

        ProgressBar barra = new ProgressBar();
        barra.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Label estado = new Label("Iniciando exportación");
        estado.setWrapText(true);
        VBox contenido = new VBox(10, barra, estado);
        contenido.setPadding(new Insets(16));
        dialogo.getDialogPane().setContent(contenido);

        java.util.concurrent.atomic.AtomicBoolean cerrado = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cerrarDialogo = () -> {
            if (!cerrado.compareAndSet(false, true)) {
                return;
            }
            Runnable cerrar = () -> {
                if (!dialogo.isShowing()) {
                    return;
                }
                var ventana = dialogo.getDialogPane().getScene() == null ? null
                        : dialogo.getDialogPane().getScene().getWindow();
                if (ventana != null) {
                    ventana.hide();
                }
            };
            if (Platform.isFxApplicationThread()) {
                cerrar.run();
            } else {
                Platform.runLater(cerrar);
            }
        };

        tarea.messageProperty().addListener((obs, anterior, mensaje) -> {
            if (mensaje != null && !mensaje.isBlank()) {
                estado.setText(mensaje);
            }
        });
        tarea.stateProperty().addListener((obs, anterior, actual) -> {
            if (actual == Worker.State.SUCCEEDED || actual == Worker.State.FAILED
                    || actual == Worker.State.CANCELLED) {
                cerrarDialogo.run();
            }
        });
        tarea.setOnSucceeded(e -> {
            cerrarDialogo.run();
            alTerminar.run();
        });
        tarea.setOnFailed(e -> {
            cerrarDialogo.run();
            UiDialogs.error("La exportación falló.");
        });
        tarea.setOnCancelled(e -> cerrarDialogo.run());
        return dialogo;
    }
}
