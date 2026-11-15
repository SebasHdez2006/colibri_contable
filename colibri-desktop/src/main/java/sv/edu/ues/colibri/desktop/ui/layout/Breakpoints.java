package sv.edu.ues.colibri.desktop.ui.layout;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.Optional;

/**
 * Único punto donde se decide la responsividad por ancho de ventana. Publica el
 * nivel como pseudo-clase ({@code :compact}, {@code :medium}, {@code :wide}) sobre
 * la raíz del shell, de modo que el CSS reaccione con selectores como
 * {@code .app-shell:compact .side-nav}.
 */
public final class Breakpoints {

    /** Niveles de ancho disponibles. */
    public enum Nivel { COMPACT, MEDIUM, WIDE }

    static final double LIMITE_COMPACTO = 900;
    static final double LIMITE_MEDIO = 1300;

    private static final String CLAVE = "colibri-breakpoints";
    private static final PseudoClass PC_COMPACT = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass PC_MEDIUM = PseudoClass.getPseudoClass("medium");
    private static final PseudoClass PC_WIDE = PseudoClass.getPseudoClass("wide");

    private final ReadOnlyObjectWrapper<Nivel> nivel = new ReadOnlyObjectWrapper<>(Nivel.WIDE);

    private Breakpoints() {
    }

    /**
     * Calcula el nivel que corresponde a un ancho.
     *
     * @param ancho ancho en píxeles de la ventana
     * @return nivel de responsividad
     */
    public static Nivel calcular(double ancho) {
        if (ancho < LIMITE_COMPACTO) {
            return Nivel.COMPACT;
        }
        return ancho < LIMITE_MEDIO ? Nivel.MEDIUM : Nivel.WIDE;
    }

    /**
     * Instala el observador sobre la raíz indicada.
     *
     * @param raiz región cuyo ancho determina el nivel y que recibe las pseudo-clases
     * @return instancia asociada a la raíz
     */
    public static Breakpoints instalar(Region raiz) {
        Breakpoints bp = new Breakpoints();
        raiz.getProperties().put(CLAVE, bp);
        bp.nivel.addListener((o, a, n) -> bp.aplicar(raiz, n));
        raiz.widthProperty().addListener((o, a, n) -> {
            if (n.doubleValue() > 0) {
                bp.nivel.set(calcular(n.doubleValue()));
            }
        });
        bp.aplicar(raiz, bp.nivel.get());
        return bp;
    }

    /**
     * Busca la instancia instalada en la raíz de la escena del nodo.
     *
     * @param nodo cualquier nodo ya insertado en una escena
     * @return instancia, si el shell la instaló
     */
    public static Optional<Breakpoints> de(Node nodo) {
        if (nodo == null || nodo.getScene() == null || nodo.getScene().getRoot() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((Breakpoints) nodo.getScene().getRoot().getProperties().get(CLAVE));
    }

    /**
     * Nivel observable.
     *
     * @return propiedad de solo lectura con el nivel actual
     */
    public ReadOnlyObjectProperty<Nivel> nivelProperty() {
        return nivel.getReadOnlyProperty();
    }

    /**
     * Nivel actual.
     *
     * @return nivel vigente
     */
    public Nivel nivel() {
        return nivel.get();
    }

    private void aplicar(Region raiz, Nivel n) {
        raiz.pseudoClassStateChanged(PC_COMPACT, n == Nivel.COMPACT);
        raiz.pseudoClassStateChanged(PC_MEDIUM, n == Nivel.MEDIUM);
        raiz.pseudoClassStateChanged(PC_WIDE, n == Nivel.WIDE);
    }
}
