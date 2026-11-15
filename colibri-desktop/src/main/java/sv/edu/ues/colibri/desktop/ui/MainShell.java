package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.AppLog;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.layout.Breakpoints;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;
import sv.edu.ues.colibri.desktop.ui.nav.NavRegistry;
import sv.edu.ues.colibri.desktop.ui.nav.Navegador;
import sv.edu.ues.colibri.desktop.ui.nav.SectionHost;
import sv.edu.ues.colibri.desktop.ui.nav.SideNav;
import sv.edu.ues.colibri.domain.model.Contador;

import java.util.List;

/**
 * Shell principal de la aplicación de escritorio.
 *
 * Es un único {@link BorderPane}: menú lateral con grupos desplegables a la
 * izquierda, barra superior con breadcrumb y zoom, y una sola página en el
 * centro. No hay desplazamiento global ni pestañas.
 */
public class MainShell extends BorderPane implements Navegador {

    private static final double ZOOM_MIN = 0.85;
    private static final double ZOOM_MAX = 1.35;
    private static final double ZOOM_STEP = 0.05;
    private static final double ZOOM_BASE_FONT = 14.0;

    private final AppContext context;
    private final Runnable onLogout;
    private final boolean soporte;
    private final List<NavItem> arbol;
    private final SideNav menu;
    private final SectionHost host = new SectionHost();
    private final HBox breadcrumb = new HBox();
    private Label zoomIndicator;
    private HBox zoomControls;
    private boolean colapsadoAutomatico;
    private double zoomFactor = 1.0;

    /**
     * Construye la interfaz principal y carga la vista inicial según el rol de la
     * sesión.
     *
     * @param context  contexto de servicios de la aplicación
     * @param onLogout acción a ejecutar cuando el usuario cierre sesión
     */
    public MainShell(AppContext context, Runnable onLogout) {
        this.context = context;
        this.onLogout = onLogout;
        Contador sesion = context.auth().getSesionActual();
        this.soporte = sesion != null && sesion.esSoporteTecnico();
        getStyleClass().add("app-shell");

        arbol = soporte ? NavRegistry.soporte(context) : NavRegistry.contador(context, this);
        menu = new SideNav("Colibrí", soporte ? "SOPORTE TÉCNICO" : "CONTABILIDAD Y CONTROL", arbol,
                this::abrirConSesion, this::alternarMenu);
        setLeft(menu);

        BorderPane centro = new BorderPane();
        centro.setTop(crearBarra());
        centro.setCenter(host);
        centro.setMinSize(0, 0);
        setCenter(centro);

        sceneProperty().addListener((observable, anterior, actual) -> instalarAtajosZoom(actual));
        Breakpoints bp = Breakpoints.instalar(this);
        bp.nivelProperty().addListener((o, anterior, nivel) -> {
            ajustarMenuAlAncho(nivel);
            ajustarBarraAlNivel(nivel);
        });
        aplicarZoom();

        abrirConSesion(arbol.getFirst().esGrupo() ? arbol.getFirst().hijos().getFirst() : arbol.getFirst());
    }

    /**
     * Abre una sección por id; un grupo abre su primera sección.
     *
     * @param id identificador de grupo u hoja
     */
    @Override
    public void ir(String id) {
        menu.destino(id).ifPresent(this::abrirConSesion);
    }

    /**
     * Menú lateral (expuesto para pruebas y accesibilidad).
     *
     * @return menú lateral
     */
    public SideNav menu() {
        return menu;
    }

    /**
     * Vista que ocupa actualmente el centro.
     *
     * @return nodo raíz de la sección abierta
     */
    public Node seccionActual() {
        return host.vista();
    }

    /**
     * Sección abierta actualmente.
     *
     * @return ítem de navegación activo
     */
    public NavItem itemActual() {
        return host.actual();
    }

    private HBox crearBarra() {
        var sesion = context.auth().getSesionActual();
        breadcrumb.getStyleClass().addAll("top-context", "breadcrumb-bar");
        String nombreCompleto = sesion == null ? "" : sesion.getRol().etiqueta() + ": " + sesion.getNombre();
        Label user = new Label(nombreCompleto);
        user.getStyleClass().add("user-pill");
        user.setMinWidth(0);
        user.setMaxWidth(220);
        user.setTextOverrun(OverrunStyle.ELLIPSIS);
        if (sesion != null) {
            Tooltip.install(user, new Tooltip(nombreCompleto));
        }
        Button salir = new Button("Cerrar sesión");
        UiTheme.decorateButton(salir);
        salir.setOnAction(e -> {
            context.auth().logout();
            onLogout.run();
        });
        zoomControls = crearControlesZoom();
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(breadcrumb, spacer, zoomControls, user, salir);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void abrirConSesion(NavItem item) {
        try {
            context.auth().exigirSesion();
        } catch (Exception ex) {
            AppLog.error("Sesión no válida al abrir " + item.etiqueta(), ex);
            UiDialogs.error(ex.getMessage());
            onLogout.run();
            return;
        }
        host.mostrar(item);
        menu.seleccionar(item.id());
        actualizarBreadcrumb(item);
    }

    private void actualizarBreadcrumb(NavItem item) {
        breadcrumb.getChildren().clear();
        NavItem padre = arbol.stream().filter(g -> g.hijos().contains(item)).findFirst().orElse(null);
        if (padre == null) {
            Label solo = new Label(item.etiqueta());
            solo.setMinWidth(0);
            solo.setMaxWidth(Double.MAX_VALUE);
            solo.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(solo, Priority.ALWAYS);
            breadcrumb.getChildren().add(solo);
            return;
        }
        Hyperlink modulo = new Hyperlink(padre.etiqueta());
        modulo.setMinWidth(0);
        modulo.setMaxWidth(140);
        modulo.setTextOverrun(OverrunStyle.ELLIPSIS);
        modulo.setAccessibleHelp("Mostrar las secciones de " + padre.etiqueta()
                + " en el menú lateral");
        // El nombre del módulo en la barra superior funciona como acceso al
        // acordeón. Conservamos la sección actual para que el usuario pueda
        // elegir explícitamente cuál abrir después.
        modulo.setOnAction(e -> menu.mostrarAcordeon(padre.id()));
        Label separador = new Label("›");
        separador.getStyleClass().add("breadcrumb-sep");
        Label seccion = new Label(item.etiqueta());
        seccion.getStyleClass().add("breadcrumb-current");
        seccion.setMinWidth(0);
        seccion.setMaxWidth(Double.MAX_VALUE);
        seccion.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(seccion, Priority.ALWAYS);
        Tooltip.install(seccion, new Tooltip(item.etiqueta()));
        breadcrumb.getChildren().addAll(modulo, separador, seccion);
    }

    private void alternarMenu() {
        colapsadoAutomatico = false;
        menu.setColapsado(!menu.isColapsado());
        // Notifica a la vista activa del nuevo ancho disponible tras el colapso/expansión.
        Platform.runLater(() -> {
            if (host.vista() instanceof Parent parent) {
                ResponsiveLayouts.ajustarAncho(parent, host.getWidth());
            }
        });
    }

    private void ajustarMenuAlAncho(Breakpoints.Nivel nivel) {
        if (nivel == Breakpoints.Nivel.COMPACT && !menu.isColapsado()) {
            colapsadoAutomatico = true;
            menu.setColapsado(true);
        } else if (nivel != Breakpoints.Nivel.COMPACT && colapsadoAutomatico) {
            colapsadoAutomatico = false;
            menu.setColapsado(false);
        }
    }

    /** En ventanas compactas los controles de zoom ceden espacio a la sesión. */
    private void ajustarBarraAlNivel(Breakpoints.Nivel nivel) {
        if (zoomControls == null) {
            return;
        }
        boolean compacto = nivel == Breakpoints.Nivel.COMPACT;
        zoomControls.setVisible(!compacto);
        zoomControls.setManaged(!compacto);
    }

    private HBox crearControlesZoom() {
        zoomIndicator = new Label("100%");
        zoomIndicator.getStyleClass().add("zoom-pill");

        Button zoomOut = botonZoom(BrandIcon.Symbol.MINUS, "Reducir zoom", this::zoomOut);
        Button zoomReset = botonZoom(BrandIcon.Symbol.REFRESH, "Restablecer zoom", this::zoomReset);
        Button zoomIn = botonZoom(BrandIcon.Symbol.PLUS, "Aumentar zoom", this::zoomIn);

        HBox controles = new HBox(zoomOut, zoomIndicator, zoomReset, zoomIn);
        controles.getStyleClass().add("zoom-controls");
        controles.setAlignment(Pos.CENTER_RIGHT);
        return controles;
    }

    private Button botonZoom(BrandIcon.Symbol simbolo, String ayuda, Runnable action) {
        Button button = new Button();
        button.setGraphic(BrandIcon.create(simbolo));
        button.setAccessibleText(ayuda);
        button.setAccessibleHelp(ayuda);
        button.setOnAction(e -> action.run());
        button.getStyleClass().add("zoom-button");
        UiTheme.decorateButton(button);
        return button;
    }

    private void zoomIn() {
        setZoom(zoomFactor + ZOOM_STEP);
    }

    private void zoomOut() {
        setZoom(zoomFactor - ZOOM_STEP);
    }

    private void zoomReset() {
        setZoom(1.0);
    }

    private void setZoom(double valor) {
        zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, valor));
        aplicarZoom();
    }

    private void aplicarZoom() {
        setStyle("-fx-font-size: %.2fpx;".formatted(ZOOM_BASE_FONT * zoomFactor));
        if (zoomIndicator != null) {
            zoomIndicator.setText("%d%%".formatted(Math.round(zoomFactor * 100)));
        }
    }

    private void instalarAtajosZoom(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), this::zoomIn);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN), this::zoomIn);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), this::zoomOut);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN),
                this::zoomOut);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN),
                this::zoomReset);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.NUMPAD0, KeyCombination.SHORTCUT_DOWN),
                this::zoomReset);
    }
}
