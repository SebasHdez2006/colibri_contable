package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.AppLog;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.layout.Breakpoints;
import sv.edu.ues.colibri.desktop.ui.nav.NavFlyout;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;
import sv.edu.ues.colibri.desktop.ui.nav.NavRegistry;
import sv.edu.ues.colibri.desktop.ui.nav.Navegador;
import sv.edu.ues.colibri.desktop.ui.nav.SectionHost;
import sv.edu.ues.colibri.desktop.ui.nav.SideNav;
import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.Periodo;

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
    private static final String[] MESES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};

    private final AppContext context;
    private final Runnable onLogout;
    private final boolean soporte;
    private final List<NavItem> arbol;
    private final SideNav menu;
    private final SectionHost host = new SectionHost();
    private final HBox breadcrumb = new HBox();
    private Popup migasFlyout;
    private PaletaComandos paleta;
    private Label zoomIndicator;
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
        paleta = new PaletaComandos(arbol, this::abrirConSesion, this::cerrarSesion);
        // El perfil y los controles de sesión viven en la cabecera. Dejar el
        // pie del menú vacío evita el bloque comprimido que ocultaba nombres y
        // convertía acciones importantes en botones sólo con iconos.
        menu.setPie(null);

        BorderPane centro = new BorderPane();
        centro.setTop(crearBarra());
        centro.setCenter(new StackPane(host, paleta));
        centro.setMinSize(0, 0);
        setCenter(centro);

        sceneProperty().addListener((observable, anterior, actual) -> instalarAtajos(actual));
        Breakpoints bp = Breakpoints.instalar(this);
        bp.nivelProperty().addListener((o, anterior, nivel) -> ajustarMenuAlAncho(nivel));
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

    /**
     * Flyout de secciones del módulo desplegado desde el breadcrumb, o
     * {@code null} si aún no se ha invocado.
     *
     * @return popup del breadcrumb (diagnóstico y pruebas)
     */
    Popup migasFlyout() {
        return migasFlyout;
    }

    private VBox crearBarra() {
        breadcrumb.getStyleClass().addAll("top-context", "breadcrumb-bar");

        Button buscar = new Button("Buscar");
        buscar.getStyleClass().addAll("button", "secondary-button", "search-pill");
        buscar.setOnAction(e -> paleta.alternar());
        buscar.setAccessibleHelp("Abrir la paleta de comandos (Ctrl+K)");
        Label kbd = new Label("Ctrl K");
        kbd.getStyleClass().add("kbd-hint");
        HBox busqueda = new HBox(8, buscar, kbd);
        busqueda.setAlignment(Pos.CENTER_RIGHT);
        busqueda.getStyleClass().add("search-box");

        HBox accionesPagina = new HBox(12, busqueda);
        accionesPagina.setAlignment(Pos.CENTER_RIGHT);
        Node chip = crearChipPeriodo();
        if (chip != null) {
            accionesPagina.getChildren().add(0, chip);
        }

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox navegacion = new HBox(breadcrumb, spacer, accionesPagina);
        navegacion.setAlignment(Pos.CENTER_LEFT);
        navegacion.getStyleClass().add("top-navigation-row");

        HBox herramientas = crearHerramientasSesion();
        VBox bar = new VBox(8, navegacion, herramientas);
        bar.getStyleClass().add("top-bar");
        bar.setFillWidth(true);
        return bar;
    }

    private Node crearChipPeriodo() {
        if (soporte) {
            return null;
        }
        try {
            var periodo = context.periodos().periodoAbierto();
            Label chip = new Label();
            chip.getStyleClass().add("period-chip");
            if (periodo.isEmpty()) {
                chip.setText("Sin período abierto");
                chip.getStyleClass().add("period-chip-none");
                return chip;
            }
            Periodo p = periodo.get();
            String estado = switch (p.getEstado()) {
                case ABIERTO -> "Abierto";
                case EN_REVISION -> "En revisión";
                case CERRADO -> "Cerrado";
            };
            chip.setText("%s %d · %s".formatted(
                    MESES[p.getMes() - 1], p.getFechaInicio().getYear(), estado));
            chip.getStyleClass().add(switch (p.getEstado()) {
                case ABIERTO -> "period-chip-abierto";
                case EN_REVISION -> "period-chip-revision";
                case CERRADO -> "period-chip-cerrado";
            });
            return chip;
        } catch (Exception ex) {
            AppLog.error("No se pudo leer el período para la barra superior", ex);
            return null;
        }
    }

    private void cerrarSesion() {
        context.auth().logout();
        onLogout.run();
    }

    /**
     * Controles de usuario visibles y con texto completo en la parte superior.
     * Ninguna acción depende de un icono aislado ni de una etiqueta truncada.
     */
    private HBox crearHerramientasSesion() {
        HBox herramientas = new HBox(10);
        herramientas.getStyleClass().add("top-session-tools");
        herramientas.setAlignment(Pos.CENTER_RIGHT);
        var sesion = context.auth().getSesionActual();
        if (sesion == null) {
            return herramientas;
        }
        Label nombre = new Label(sesion.getNombre());
        nombre.getStyleClass().add("top-profile-name");
        Label rol = new Label(sesion.getRol().etiqueta());
        rol.getStyleClass().add("top-profile-role");
        VBox datos = new VBox(1, nombre, rol);
        datos.getStyleClass().add("top-profile");
        Button salir = new Button("Cerrar sesión");
        salir.getStyleClass().addAll("button", "secondary-button", "top-session-button");
        salir.setOnAction(e -> cerrarSesion());
        herramientas.getChildren().addAll(datos, crearControlesZoom(), new TemaSwitch(), salir);
        return herramientas;
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
            breadcrumb.getChildren().add(solo);
            return;
        }
        Hyperlink modulo = new Hyperlink(padre.etiqueta());
        modulo.setAccessibleHelp("Desplegar las secciones de " + padre.etiqueta());
        // El nombre del módulo despliega sus secciones para saltar entre
        // ellas sin pasar por el menú lateral.
        modulo.setOnAction(e -> {
            if (migasFlyout != null && migasFlyout.isShowing()) {
                migasFlyout.hide();
                return;
            }
            migasFlyout = NavFlyout.mostrar(modulo, NavFlyout.Posicion.DEBAJO,
                    padre.hijos(), this::abrirConSesion);
        });
        Label separador = new Label("›");
        separador.getStyleClass().add("breadcrumb-sep");
        Label seccion = new Label(item.etiqueta());
        seccion.getStyleClass().add("breadcrumb-current");
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

    private HBox crearControlesZoom() {
        zoomIndicator = new Label("100%");
        zoomIndicator.getStyleClass().add("zoom-pill");

        Button zoomOut = botonZoom("Reducir zoom", this::zoomOut);
        Button zoomReset = botonZoom("Restablecer", this::zoomReset);
        Button zoomIn = botonZoom("Aumentar zoom", this::zoomIn);

        HBox controles = new HBox(zoomOut, zoomIndicator, zoomReset, zoomIn);
        controles.getStyleClass().add("zoom-controls");
        controles.setAlignment(Pos.CENTER);
        return controles;
    }

    private Button botonZoom(String texto, Runnable action) {
        Button button = new Button(texto);
        button.setAccessibleText(texto);
        button.setAccessibleHelp(texto);
        button.setOnAction(e -> action.run());
        button.getStyleClass().addAll("zoom-button", "top-zoom-button");
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

    private void instalarAtajos(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                paleta::alternar);
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
