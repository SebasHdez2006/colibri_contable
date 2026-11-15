package sv.edu.ues.colibri.desktop.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.util.Duration;

/**
 * Movimiento de la aplicación: entradas de página suaves y respeto a la
 * preferencia de "reducir movimiento" de la escena.
 */
public final class Motion {

    /** Duración base de entrada de página (escala de movimiento del sistema). */
    private static final Duration ENTRADA = Duration.millis(220);

    private Motion() {
    }

    /**
     * Aplica fundido y desplazamiento vertical sutil a una página recién
     * insertada. Si la escena prefiere movimiento reducido, el nodo queda
     * visible de inmediato sin animar.
     *
     * @param node página insertada en el contenedor
     */
    public static void entrada(Node node) {
        if (node == null) {
            return;
        }
        if (movimientoReducido(node)) {
            node.setOpacity(1);
            node.setTranslateY(0);
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(8);
        FadeTransition fundido = new FadeTransition(ENTRADA, node);
        fundido.setFromValue(0);
        fundido.setToValue(1);
        TranslateTransition desplazamiento = new TranslateTransition(ENTRADA, node);
        desplazamiento.setFromY(8);
        desplazamiento.setToY(0);
        new ParallelTransition(fundido, desplazamiento).play();
    }

    private static boolean movimientoReducido(Node node) {
        Scene scene = node.getScene();
        if (scene != null) {
            return scene.getPreferences().isReducedMotion();
        }
        return Platform.getPreferences().isReducedMotion();
    }
}
