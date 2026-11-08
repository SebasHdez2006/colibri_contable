package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * Ensambla el esqueleto visual común de los módulos con pestañas de la
 * aplicación.
 *
 * Esta utilidad centraliza el encabezado, el área de pestañas, la decoración
 * visual de contenido cargado de forma anticipada o diferida y el ajuste
 * responsivo de la altura de cada vista.
 */
public final class ModuleView {

    private ModuleView() {
    }

    /**
     * Construye una vista de módulo con encabezado, subtítulo y pestañas.
     *
     * @param titulo título visible del módulo
     * @param ayuda  texto descriptivo que orienta al usuario
     * @param tabs   pestañas que componen el contenido funcional del módulo
     * @return contenedor raíz listo para insertarse en la navegación principal
     */
    public static VBox of(String titulo, String ayuda, Tab... tabs) {
        Label heading = new Label(titulo);
        heading.getStyleClass().add("view-title");
        heading.setWrapText(true);
        heading.setMaxWidth(Double.MAX_VALUE);
        Label subtitle = new Label(ayuda);
        subtitle.getStyleClass().add("view-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        TabPane pane = new TabPane(tabs);
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pane.setMaxHeight(Region.USE_PREF_SIZE);
        pane.getSelectionModel().selectedItemProperty().addListener((observable, anterior, actual) -> {
            cargarSiNecesario(actual);
            programarAjuste(pane, actual);
        });
        pane.widthProperty().addListener(
                (observable, anterior, actual) -> programarAjuste(pane, pane.getSelectionModel().getSelectedItem()));
        cargarSiNecesario(pane.getSelectionModel().getSelectedItem());
        programarAjuste(pane, pane.getSelectionModel().getSelectedItem());
        VBox box = new VBox(16, heading, subtitle, pane);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("content-view");
        return box;
    }

    /**
     * Crea una pestaña estática y prepara su contenido con el tema institucional.
     *
     * @param titulo    título de la pestaña
     * @param contenido nodo raíz que se mostrará en la pestaña
     * @return pestaña no cerrable con contenido ya decorado
     */
    public static Tab tab(String titulo, Node contenido) {
        prepararContenido(contenido);
        Tab tab = new Tab(titulo, contenido);
        tab.setClosable(false);
        return tab;
    }

    /**
     * Carga el contenido al construir el módulo y, si falla, presenta un error
     * recuperable en lugar de una pestaña visualmente vacía.
     *
     * @param titulo    título de la pestaña
     * @param proveedor factoría que construye el contenido de la pestaña
     * @return pestaña no cerrable que carga el contenido de inmediato
     */
    public static Tab resilientTab(String titulo, Supplier<? extends Node> proveedor) {
        Tab tab = tab(titulo, mensajeCarga());
        Runnable carga = () -> cargar(tab, titulo, proveedor);
        tab.getProperties().put("colibri-resilient-load", carga);
        carga.run();
        return tab;
    }

    /**
     * Crea una pestaña bajo demanda. Así, un error de consulta de una pestaña
     * no impide abrir el módulo completo ni las demás funciones del contador.
     *
     * @param titulo    título de la pestaña
     * @param proveedor factoría que construye el contenido al seleccionarse
     * @return pestaña no cerrable que difiere la carga hasta que el usuario la abre
     */
    public static Tab lazyTab(String titulo, Supplier<? extends Node> proveedor) {
        Tab tab = tab(titulo, mensajeCarga());
        tab.getProperties().put("colibri-lazy-load", (Runnable) () -> cargar(tab, titulo, proveedor));
        tab.setOnSelectionChanged(evento -> cargarSiNecesario(tab));
        return tab;
    }

    private static void cargarSiNecesario(Tab tab) {
        if (tab == null || Boolean.TRUE.equals(tab.getProperties().get("colibri-loaded"))) {
            return;
        }
        Object accion = tab.getProperties().get("colibri-lazy-load");
        if (accion instanceof Runnable carga) {
            carga.run();
        }
    }

    private static void cargar(Tab tab, String titulo, Supplier<? extends Node> proveedor) {
        try {
            Node contenido = proveedor.get();
            prepararContenido(contenido);
            tab.setContent(contenido);
            tab.getProperties().put("colibri-loaded", true);
            Platform.runLater(() -> {
                TabPane pane = tab.getTabPane();
                if (pane != null) {
                    ajustarAltura(pane, tab);
                }
            });
        } catch (Exception ex) {
            tab.setContent(errorCarga(tab, titulo, proveedor, ex));
        }
    }

    private static void programarAjuste(TabPane pane, Tab actual) {
        if (pane == null || actual == null) {
            return;
        }
        Platform.runLater(() -> ajustarAltura(pane, actual));
    }

    private static void ajustarAltura(TabPane pane, Tab actual) {
        if (pane == null || actual == null || actual.getContent() == null) {
            return;
        }
        Node contenido = actual.getContent();
        contenido.applyCss();
        contenido.autosize();
        double ancho = Math.max(320, pane.getWidth());
        double altoContenido = contenido.prefHeight(ancho);
        if (Double.isNaN(altoContenido) || altoContenido <= 0) {
            altoContenido = contenido.getBoundsInParent().getHeight();
        }
        if (Double.isNaN(altoContenido) || altoContenido <= 0) {
            altoContenido = contenido.getBoundsInLocal().getHeight();
        }
        if (Double.isNaN(altoContenido) || altoContenido <= 0) {
            altoContenido = 360;
        }
        pane.setPrefHeight(altoContenido + 62);
    }

    private static VBox errorCarga(Tab tab, String titulo, Supplier<? extends Node> proveedor, Exception ex) {
        Label detalle = new Label(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        detalle.setWrapText(true);
        Button reintentar = new Button("Reintentar carga");
        reintentar.setOnAction(evento -> cargar(tab, titulo, proveedor));
        VBox error = new VBox(10, new Label("No se pudo cargar " + titulo + "."), detalle, reintentar);
        error.getStyleClass().addAll("form-card", "module-load-error");
        prepararContenido(error);
        return error;
    }

    private static VBox mensajeCarga() {
        VBox carga = new VBox(new Label("Seleccione esta pestaña para cargar sus datos."));
        carga.getStyleClass().add("form-card");
        prepararContenido(carga);
        return carga;
    }

    private static void prepararContenido(Node contenido) {
        if (contenido instanceof Parent parent) {
            ResponsiveLayouts.ajustarAlContenido(parent);
            UiTheme.decorate(parent);
        }
    }
}
