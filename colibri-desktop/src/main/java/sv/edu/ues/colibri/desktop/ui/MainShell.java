package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
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
import java.util.Locale;

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
        menu.setPie(construirPie());

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

    private HBox crearBarra() {
        breadcrumb.getStyleClass().addAll("top-context", "breadcrumb-bar");

        Button buscar = new Button("Buscar");
        buscar.setGraphic(BrandIcon.create(BrandIcon.Symbol.SEARCH));
        buscar.getStyleClass().addAll("button", "secondary-button", "search-pill");
        buscar.setOnAction(e -> paleta.alternar());
        buscar.setAccessibleHelp("Abrir la paleta de comandos (Ctrl+K)");
        Label kbd = new Label("Ctrl K");
        kbd.getStyleClass().add("kbd-hint");
        HBox busqueda = new HBox(8, buscar, kbd);
        busqueda.setAlignment(Pos.CENTER_RIGHT);
        busqueda.getStyleClass().add("search-box");

        HBox controles = new HBox(12, busqueda);
        controles.setAlignment(Pos.CENTER_RIGHT);
        Node chip = crearChipPeriodo();
        if (chip != null) {
            controles.getChildren().add(0, chip);
        }

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(breadcrumb, spacer, controles);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
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

    private Node construirPie() {
        VBox pie = new VBox(10);
        pie.getStyleClass().add("pie-content");
        pie.setPadding(new Insets(12, 14, 12, 14));
        var sesion = context.auth().getSesionActual();
        if (sesion == null) {
            return pie;
        }
        if (menu.isColapsado()) {
            pie.setAlignment(Pos.CENTER);
            pie.getChildren().add(avatar(sesion.getNombre()));
            HBox zoom = new HBox(6,
                    botonZoom(BrandIcon.Symbol.MINUS, "Reducir zoom", this::zoomOut),
                    botonZoom(BrandIcon.Symbol.PLUS, "Aumentar zoom", this::zoomIn));
            zoom.setAlignment(Pos.CENTER);
            zoom.getStyleClass().add("footer-zoom");
            Button salir = botonZoom(BrandIcon.Symbol.LOGOUT, "Cerrar sesión", this::cerrarSesion);
            zoomIndicator = null;
            pie.getChildren().addAll(zoom, salir);
            return pie;
        }
        VBox datos = new VBox(1,
                etiqueta(sesion.getNombre(), "pie-name"),
                etiqueta(sesion.getRol().etiqueta(), "pie-role"));
        datos.setMinWidth(0);
        HBox.setHgrow(datos, Priority.ALWAYS);
        Button salir = new Button("Cerrar sesión");
        salir.setGraphic(BrandIcon.create(BrandIcon.Symbol.LOGOUT));
        salir.getStyleClass().addAll("button", "secondary-button", "pie-logout");
        salir.setOnAction(e -> cerrarSesion());
        HBox usuario = new HBox(10, avatar(sesion.getNombre()), datos, salir);
        usuario.setAlignment(Pos.CENTER_LEFT);
        pie.getChildren().add(usuario);
        pie.getChildren().add(crearControlesZoom());
        return pie;
    }

    private Label avatar(String nombre) {
        String[] partes = nombre.trim().split("\\s+");
        String iniciales = (partes.length > 0 && !partes[0].isEmpty() ? partes[0].substring(0, 1) : "?")
                + (partes.length > 1 && !partes[1].isEmpty() ? partes[1].substring(0, 1) : "");
        Label l = new Label(iniciales.toUpperCase(Locale.ROOT));
        l.getStyleClass().add("avatar");
        return l;
    }

    private Label etiqueta(String texto, String clase) {
        Label l = new Label(texto);
        l.getStyleClass().add(clase);
        l.setTextOverrun(OverrunStyle.ELLIPSIS);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
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
        menu.setPie(construirPie());
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

        Button zoomOut = botonZoom(BrandIcon.Symbol.MINUS, "Reducir zoom", this::zoomOut);
        Button zoomReset = botonZoom(BrandIcon.Symbol.REFRESH, "Restablecer zoom", this::zoomReset);
        Button zoomIn = botonZoom(BrandIcon.Symbol.PLUS, "Aumentar zoom", this::zoomIn);

        HBox controles = new HBox(zoomOut, zoomIndicator, zoomReset, zoomIn);
        controles.getStyleClass().add("zoom-controls");
        controles.setAlignment(Pos.CENTER);
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
