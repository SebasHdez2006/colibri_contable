package sv.edu.ues.colibri.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.AppLog;
import sv.edu.ues.colibri.desktop.ui.views.CostosModuleView;
import sv.edu.ues.colibri.desktop.ui.views.EmpresaPeriodosView;
import sv.edu.ues.colibri.desktop.ui.views.InicioView;
import sv.edu.ues.colibri.desktop.ui.views.InventarioModuleView;
import sv.edu.ues.colibri.desktop.ui.views.ParametrizacionView;
import sv.edu.ues.colibri.desktop.ui.views.RegistrosModuleView;
import sv.edu.ues.colibri.desktop.ui.views.ReportesModuleView;
import sv.edu.ues.colibri.desktop.ui.views.SoporteTecnicoView;
import sv.edu.ues.colibri.desktop.ui.views.TemasContablesView;
import sv.edu.ues.colibri.domain.model.Contador;

import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Shell principal de la aplicación de escritorio.
 *
 * Integra el menú lateral, la barra superior, el control de sesión y la
 * navegación entre módulos funcionales del sistema contable.
 */
public class MainShell extends BorderPane {

    /** Ancho de la barra lateral expandida y colapsada (solo iconos). */
    private static final double ANCHO_EXPANDIDO = 240;
    private static final double ANCHO_COLAPSADO = 60;
    private static final double LOGO_ANCHO = 34;
    private static final double LOGO_ALTO = 34;
    private static final double ZOOM_MIN = 0.85;
    private static final double ZOOM_MAX = 1.35;
    private static final double ZOOM_STEP = 0.05;
    private static final double ZOOM_BASE_FONT = 14.0;

    private final AppContext context;
    private final VBox content = new VBox();
    private final Runnable onLogout;
    private final List<Button> navigation = new ArrayList<>();
    private final boolean soporte;

    private ScrollPane menuScroll;
    private ScrollPane contentScroll;
    private VBox menu;
    private StackPane logoHolder;
    private VBox brandText;
    private HBox brandRow;
    private Button toggle;
    private Label zoomIndicator;
    private Parent vistaActual;
    private boolean colapsado;
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
        content.getStyleClass().add("content-host");
        content.setFillWidth(true);
        content.setSpacing(16);
        content.setPadding(new Insets(24, 24, 28, 24));
        content.setMaxWidth(Double.MAX_VALUE);

        menu = crearMenu();
        menuScroll = crearScroll(menu, "menu-scroll-pane", true);
        menuScroll.setFitToHeight(false);
        setLeft(menuScroll);

        contentScroll = crearScroll(content, "section-scroll-pane", true);
        contentScroll.viewportBoundsProperty().addListener((observable, anterior, actual) -> {
            if (vistaActual != null) {
                ResponsiveLayouts.ajustarAncho(vistaActual, actual.getWidth());
            }
        });
        sceneProperty().addListener((observable, anterior, actual) -> instalarAtajosZoom(actual));

        BorderPane centro = new BorderPane();
        centro.setTop(crearBarra());
        centro.setCenter(contentScroll);
        setCenter(centro);

        aplicarEstadoMenu();
        aplicarZoom();

        if (soporte) {
            mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_USUARIOS));
        } else {
            mostrarInicio();
        }
        if (!navigation.isEmpty()) {
            seleccionar(navigation.getFirst());
        }
    }

    /**
     * Crea la barra superior con contexto, controles de zoom y acción de salida.
     */
    private HBox crearBarra() {
        var sesion = context.auth().getSesionActual();
        Label contextLabel = new Label(soporte ? "Soporte técnico" : "Centro de control contable");
        contextLabel.getStyleClass().add("top-context");
        Label user = new Label(sesion == null ? ""
                : sesion.getRol().etiqueta() + ": " + sesion.getNombre());
        user.getStyleClass().add("user-pill");
        Button salir = new Button("Cerrar sesión");
        UiTheme.decorateButton(salir);
        salir.setOnAction(e -> {
            context.auth().logout();
            onLogout.run();
        });
        HBox zoomControls = crearControlesZoom();
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, contextLabel, spacer, zoomControls, user, salir);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Construye el menú lateral con accesos a los módulos habilitados.
     */
    private VBox crearMenu() {
        VBox menuBox = new VBox(6);
        menuBox.getStyleClass().add("side-menu");

        Label nombre = new Label("Colibrí");
        nombre.getStyleClass().add("side-brand-name");
        nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
        nombre.setMaxWidth(Double.MAX_VALUE);
        Label producto = new Label(soporte ? "SOPORTE TÉCNICO" : "CONTABILIDAD Y CONTROL");
        producto.getStyleClass().add("side-brand-subtitle");
        producto.setTextOverrun(OverrunStyle.ELLIPSIS);
        producto.setMaxWidth(Double.MAX_VALUE);

        brandText = new VBox(1, nombre, producto);
        brandText.getStyleClass().add("side-brand-text");
        brandText.setMinWidth(0);
        HBox.setHgrow(brandText, Priority.ALWAYS);

        // El logo vive aislado en su propio contenedor de tamaño fijo.
        BrandLogo mark = new BrandLogo(LOGO_ANCHO, 26);
        logoHolder = new StackPane(mark);
        logoHolder.getStyleClass().add("side-logo");
        logoHolder.setMinSize(LOGO_ANCHO, LOGO_ALTO);
        logoHolder.setPrefSize(LOGO_ANCHO, LOGO_ALTO);
        logoHolder.setMaxSize(LOGO_ANCHO, LOGO_ALTO);
        logoHolder.setMouseTransparent(true);

        toggle = new Button();
        toggle.getStyleClass().add("menu-toggle");
        toggle.setGraphic(BrandIcon.create(BrandIcon.Symbol.MENU));
        toggle.getProperties().put("colibri-auto-icon", true);
        toggle.setAccessibleHelp("Alterna entre el menú de navegación expandido y contraído.");
        toggle.setOnAction(e -> {
            colapsado = !colapsado;
            aplicarEstadoMenu();
        });
        Tooltip.install(toggle, new Tooltip("Contraer o expandir el menú"));

        brandRow = new HBox(9, logoHolder, brandText, toggle);
        brandRow.getStyleClass().add("side-brand");
        brandRow.setAlignment(Pos.CENTER_LEFT);
        menuBox.getChildren().add(brandRow);

        if (soporte) {
            menuBox.getChildren().addAll(
                    boton("Usuarios", BrandIcon.Symbol.USERS,
                            () -> mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_USUARIOS))),
                    boton("Base de datos", BrandIcon.Symbol.BACKUP,
                            () -> mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_BD))),
                    boton("Entorno", BrandIcon.Symbol.BUILDING,
                            () -> mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_ENTORNO))),
                    boton("Monitoreo", BrandIcon.Symbol.REPORT,
                            () -> mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_MONITOREO))),
                    boton("Accesos", BrandIcon.Symbol.KEY,
                            () -> mostrar(new SoporteTecnicoView(context, SoporteTecnicoView.SECCION_ACCESOS))));
        } else {
            menuBox.getChildren().addAll(
                    boton("Inicio", BrandIcon.Symbol.HOME, this::mostrarInicio),
                    boton("Temas contables", BrandIcon.Symbol.DOCUMENT,
                            () -> mostrar(new TemasContablesView(this::abrirModuloDesdeTema))),
                    new Separator(),
                    boton("Empresa", BrandIcon.Symbol.BUILDING,
                            () -> mostrar(new EmpresaPeriodosView(context))),
                    boton("Parametrización", BrandIcon.Symbol.CATALOG,
                            () -> mostrar(new ParametrizacionView(context))),
                    boton("Registros", BrandIcon.Symbol.JOURNAL,
                            () -> mostrar(new RegistrosModuleView(context))),
                    boton("Inventario", BrandIcon.Symbol.INVENTORY,
                            () -> mostrar(new InventarioModuleView(context))),
                    boton("Costos", BrandIcon.Symbol.MONEY,
                            () -> mostrar(new CostosModuleView(context))),
                    boton("Reportes", BrandIcon.Symbol.REPORT,
                            () -> mostrar(new ReportesModuleView(context))));
        }
        return menuBox;
    }

    private void aplicarEstadoMenu() {
        double ancho = colapsado ? ANCHO_COLAPSADO : ANCHO_EXPANDIDO;
        menu.setPrefWidth(ancho);
        menu.setMinWidth(ancho);
        menuScroll.setPrefWidth(ancho);
        menuScroll.setMinWidth(ancho);
        menuScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        boolean mostrarMarca = !colapsado;
        toggle.setAccessibleText(colapsado
                ? "Expandir menú de navegación"
                : "Contraer menú de navegación");
        logoHolder.setVisible(mostrarMarca);
        logoHolder.setManaged(mostrarMarca);
        brandText.setVisible(mostrarMarca);
        brandText.setManaged(mostrarMarca);
        brandRow.setAlignment(colapsado ? Pos.CENTER : Pos.CENTER_LEFT);

        getStyleClass().remove("menu-colapsado");
        if (colapsado) {
            getStyleClass().add("menu-colapsado");
        }

        for (Button button : navigation) {
            String texto = (String) button.getProperties().get("colibri-nav-text");
            if (texto == null) {
                continue;
            }
            button.setText(colapsado ? "" : texto);
            button.setAlignment(colapsado ? Pos.CENTER : Pos.CENTER_LEFT);
            button.setGraphicTextGap(colapsado ? 0 : 12);
            Tooltip tip = (Tooltip) button.getProperties().get("colibri-nav-tip");
            if (tip != null) {
                if (colapsado) {
                    Tooltip.install(button, tip);
                } else {
                    Tooltip.uninstall(button, tip);
                }
            }
        }
    }

    private void mostrarInicio() {
        mostrar(new InicioView(context, () -> {
            seleccionarPorTexto("Registros");
            mostrar(new RegistrosModuleView(context));
        }));
    }

    private void abrirModuloDesdeTema(String modulo) {
        seleccionarPorTexto(modulo);
        switch (modulo) {
            case "Empresa" -> mostrar(new EmpresaPeriodosView(context));
            case "Parametrización" -> mostrar(new ParametrizacionView(context));
            case "Registros" -> mostrar(new RegistrosModuleView(context));
            case "Inventario" -> mostrar(new InventarioModuleView(context));
            case "Costos" -> mostrar(new CostosModuleView(context));
            case "Reportes" -> mostrar(new ReportesModuleView(context));
            default -> throw new IllegalArgumentException("Módulo no disponible: " + modulo);
        }
    }

    /**
     * Inserta una vista en el panel central y ajusta su comportamiento responsivo.
     *
     * @param view componente raíz de la sección a mostrar
     */
    private void mostrar(Parent view) {
        view.getStyleClass().add("content-view");
        ResponsiveLayouts.ajustarAlContenido(view);
        UiTheme.decorate(view);
        content.getChildren().setAll(view);
        vistaActual = view;
        ResponsiveLayouts.ajustarAncho(view, contentScroll.getViewportBounds().getWidth());
    }

    private ScrollPane crearScroll(Parent contenido, String estilo) {
        return crearScroll(contenido, estilo, true);
    }

    private ScrollPane crearScroll(Parent contenido, String estilo, boolean fitToWidth) {
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(fitToWidth);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setMinHeight(0);
        scroll.getStyleClass().add(estilo);
        return scroll;
    }

    private HBox crearControlesZoom() {
        zoomIndicator = new Label("100%");
        zoomIndicator.getStyleClass().add("zoom-pill");

        Button zoomOut = botonZoom(BrandIcon.Symbol.MINUS, "Reducir zoom", this::zoomOut);
        Button zoomReset = botonZoom(BrandIcon.Symbol.REFRESH, "Restablecer zoom", this::zoomReset);
        Button zoomIn = botonZoom(BrandIcon.Symbol.PLUS, "Aumentar zoom", this::zoomIn);

        HBox controles = new HBox(6, zoomOut, zoomIndicator, zoomReset, zoomIn);
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
        if (vistaActual != null) {
            ResponsiveLayouts.ajustarAncho(vistaActual, contentScroll.getViewportBounds().getWidth());
        }
    }

    private void instalarAtajosZoom(javafx.scene.Scene scene) {
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

    /**
     * Crea un botón de navegación para el menú lateral.
     *
     * @param texto  etiqueta visible y referencia funcional del módulo
     * @param icono  símbolo gráfico asociado al módulo
     * @param action acción a ejecutar al seleccionar el módulo
     * @return botón configurado para navegación y accesibilidad
     */
    private Button boton(String texto, BrandIcon.Symbol icono, Runnable action) {
        Button b = new Button(texto);
        b.setId("nav-" + Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setTextOverrun(OverrunStyle.ELLIPSIS);
        b.setGraphic(BrandIcon.create(icono));
        b.setGraphicTextGap(12);
        b.setAccessibleText(texto);
        b.setAccessibleHelp("Abrir el módulo " + texto);
        b.getStyleClass().add("nav-button");
        b.getProperties().put("colibri-nav-text", texto);
        b.getProperties().put("colibri-nav-tip", new Tooltip(texto));
        navigation.add(b);
        b.setOnAction(e -> {
            try {
                context.auth().exigirSesion();
            } catch (Exception ex) {
                AppLog.error("Sesión no válida al abrir " + texto, ex);
                UiDialogs.error(ex.getMessage());
                onLogout.run();
                return;
            }
            try {
                seleccionar(b);
                action.run();
            } catch (Exception ex) {
                // Un error de un módulo no es un error de autenticación: se
                // conserva la sesión y se deja al usuario en la última vista válida.
                AppLog.error("No se pudo abrir el módulo " + texto, ex);
                UiDialogs.error("No se pudo abrir " + texto + ": " + ex.getMessage());
            }
        });
        return b;
    }

    private void seleccionar(Button seleccionado) {
        navigation.forEach(button -> button.getStyleClass().remove("nav-button-active"));
        seleccionado.getStyleClass().add("nav-button-active");
    }

    private void seleccionarPorTexto(String texto) {
        navigation.stream()
                .filter(button -> texto.equals(button.getProperties().get("colibri-nav-text")))
                .findFirst()
                .ifPresent(this::seleccionar);
    }
}
