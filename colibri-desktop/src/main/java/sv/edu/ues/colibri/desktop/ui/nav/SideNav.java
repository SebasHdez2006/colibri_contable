package sv.edu.ues.colibri.desktop.ui.nav;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import sv.edu.ues.colibri.desktop.ui.BrandIcon;
import sv.edu.ues.colibri.desktop.ui.BrandLogo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Menú lateral con grupos tipo acordeón. Solo emite solicitudes de navegación;
 * quien decide si se abre una sección (sesión, errores) es el shell.
 */
public final class SideNav extends BorderPane {

    private static final PseudoClass COLAPSADO = PseudoClass.getPseudoClass("colapsado");
    private static final double LOGO = 34;

    private final Consumer<NavItem> alSolicitar;
    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final Map<String, String> padreDe = new LinkedHashMap<>();
    private final Map<String, Button> botones = new LinkedHashMap<>();
    private final Map<String, VBox> hijosDe = new LinkedHashMap<>();
    private final Map<String, StackPane> chevrones = new LinkedHashMap<>();
    private final List<Label> etiquetas = new ArrayList<>();
    private final List<HBox> contenidos = new ArrayList<>();
    private final HBox marca;
    private final VBox textoMarca;
    private final StackPane logo;
    private final Button alternar = new Button();
    private final Runnable alAlternar;
    private Popup flyout;
    private boolean colapsado;
    private String activo;

    /**
     * Construye el menú.
     *
     * @param titulo      nombre de la marca
     * @param subtitulo   leyenda bajo la marca
     * @param arbol       grupos y hojas de primer nivel
     * @param alSolicitar acción al pulsar un ítem navegable
     * @param alAlternar  acción del botón de contraer/expandir
     */
    public SideNav(String titulo, String subtitulo, List<NavItem> arbol,
                   Consumer<NavItem> alSolicitar, Runnable alAlternar) {
        this.alSolicitar = alSolicitar;
        this.alAlternar = alAlternar;
        getStyleClass().add("side-nav");

        Label nombre = new Label(titulo);
        nombre.getStyleClass().add("side-brand-name");
        nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
        nombre.setMaxWidth(Double.MAX_VALUE);
        Label producto = new Label(subtitulo);
        producto.getStyleClass().add("side-brand-subtitle");
        producto.setTextOverrun(OverrunStyle.ELLIPSIS);
        producto.setMaxWidth(Double.MAX_VALUE);
        textoMarca = new VBox(nombre, producto);
        textoMarca.getStyleClass().add("side-brand-text");
        textoMarca.setMinWidth(0);
        HBox.setHgrow(textoMarca, Priority.ALWAYS);

        logo = new StackPane(new BrandLogo(LOGO, 26));
        logo.getStyleClass().add("side-logo");
        logo.setMinSize(LOGO, LOGO);
        logo.setPrefSize(LOGO, LOGO);
        logo.setMaxSize(LOGO, LOGO);
        logo.setMouseTransparent(true);

        alternar.getStyleClass().add("menu-toggle");
        alternar.setGraphic(BrandIcon.create(BrandIcon.Symbol.MENU));
        alternar.getProperties().put("colibri-auto-icon", true);
        alternar.setAccessibleHelp("Alterna entre el menú de navegación expandido y contraído.");
        alternar.setAccessibleText("Contraer menú de navegación");
        alternar.setOnAction(e -> this.alAlternar.run());
        Tooltip.install(alternar, new Tooltip("Contraer o expandir el menú"));

        marca = new HBox(logo, textoMarca, alternar);
        marca.getStyleClass().add("side-brand");
        marca.setAlignment(Pos.CENTER_LEFT);

        VBox lista = new VBox();
        lista.getStyleClass().add("side-menu");
        int indice = 0;
        for (NavItem item : arbol) {
            lista.getChildren().add(crear(item, null));
            indice++;
            // Separador visual tras «Temas contables» (segundo ítem de primer nivel).
            if (indice == 2) {
                Separator divisor = new Separator();
                divisor.getStyleClass().add("nav-divider");
                lista.getChildren().add(divisor);
            }
        }
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(0);
        scroll.getStyleClass().add("menu-scroll-pane");
        setTop(marca);
        setCenter(scroll);
    }

    /**
     * Instala el contenido del pie del menú (usuario, zoom, sesión).
     * El shell lo reconstruye al alternar el estado contraído/expandido.
     *
     * @param contenido nodo raíz del pie, o {@code null} para quitarlo
     */
    public void setPie(Node contenido) {
        if (contenido == null) {
            setBottom(null);
            return;
        }
        VBox contenedor = new VBox(contenido);
        contenedor.getStyleClass().add("side-footer");
        setBottom(contenedor);
    }

    private VBox crear(NavItem item, String padre) {
        items.put(item.id(), item);
        if (padre != null) {
            padreDe.put(item.id(), padre);
        }
        Button boton = item.esGrupo() ? botonGrupo(item) : botonHoja(item, padre != null);
        botones.put(item.id(), boton);
        VBox caja = new VBox(boton);
        caja.getStyleClass().add("nav-entry");
        if (item.esGrupo()) {
            VBox hijos = new VBox();
            hijos.getStyleClass().add("nav-children");
            for (NavItem hijo : item.hijos()) {
                hijos.getChildren().add(crear(hijo, item.id()));
            }
            hijos.setVisible(false);
            hijos.setManaged(false);
            hijosDe.put(item.id(), hijos);
            caja.getChildren().add(hijos);
        }
        return caja;
    }

    private Button botonHoja(NavItem item, boolean hijo) {
        Button b = base(item);
        if (hijo) {
            b.getStyleClass().add("nav-child-button");
            b.setGraphic(etiquetaGrafica(item, false));
        } else {
            b.setGraphic(etiquetaGrafica(item, false));
        }
        b.setOnAction(e -> alSolicitar.accept(item));
        return b;
    }

    private Button botonGrupo(NavItem item) {
        Button b = base(item);
        b.getStyleClass().add("nav-group-button");
        b.setGraphic(etiquetaGrafica(item, true));
        b.setAccessibleHelp("Expandir o contraer el módulo " + item.etiqueta());
        b.setOnAction(e -> pulsarGrupo(item, b));
        return b;
    }

    private Button base(NavItem item) {
        Button b = new Button();
        b.setId(item.id());
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAccessibleText(item.etiqueta());
        b.setAccessibleHelp("Abrir " + item.etiqueta());
        b.getStyleClass().add("nav-button");
        b.getProperties().put("colibri-nav-text", item.etiqueta());
        b.getProperties().put("colibri-nav-tip", new Tooltip(item.etiqueta()));
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private HBox etiquetaGrafica(NavItem item, boolean conChevron) {
        HBox fila = new HBox();
        fila.getStyleClass().add("nav-content");
        fila.setAlignment(Pos.CENTER_LEFT);
        contenidos.add(fila);
        if (item.icono() != null) {
            fila.getChildren().add(BrandIcon.create(item.icono()));
        }
        Label texto = new Label(item.etiqueta());
        texto.getStyleClass().add("nav-label");
        texto.setTextOverrun(OverrunStyle.ELLIPSIS);
        texto.setMinWidth(0);
        texto.setMouseTransparent(true);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setMaxWidth(Double.MAX_VALUE);
        etiquetas.add(texto);
        fila.getChildren().add(texto);
        if (conChevron) {
            StackPane chevron = BrandIcon.create(BrandIcon.Symbol.CHEVRON_RIGHT);
            chevron.getStyleClass().add("nav-chevron");
            chevrones.put(item.id(), chevron);
            fila.getChildren().add(chevron);
        }
        return fila;
    }

    private void pulsarGrupo(NavItem grupo, Button boton) {
        if (colapsado) {
            mostrarFlyout(grupo, boton);
            return;
        }
        boolean abierto = hijosDe.get(grupo.id()).isVisible();
        boolean activoEnGrupo = activo != null && grupo.id().equals(padreDe.get(activo));
        if (activoEnGrupo || abierto) {
            expandir(grupo.id(), !abierto);
            return;
        }
        expandir(grupo.id(), true);
        alSolicitar.accept(grupo.hijos().getFirst());
    }

    /**
     * Muestra un flyout de vidrio con las secciones del grupo cuando el menú
     * está contraído. Sustituye al menú contextual nativo.
     */
    private void mostrarFlyout(NavItem grupo, Button boton) {
        if (flyout != null && flyout.isShowing()) {
            flyout.hide();
            return;
        }
        flyout = NavFlyout.mostrar(boton, NavFlyout.Posicion.DERECHA,
                grupo.hijos(), alSolicitar);
    }

    private void expandir(String idGrupo, boolean abrir) {
        if (abrir) {
            hijosDe.forEach((id, caja) -> mostrarHijos(id, id.equals(idGrupo)));
        } else {
            mostrarHijos(idGrupo, false);
        }
    }

    /**
     * Muestra las secciones de un módulo sin cambiar la vista que el usuario
     * está consultando. Se usa desde el breadcrumb superior para que el nombre
     * del módulo funcione como acceso directo al acordeón lateral.
     *
     * @param idGrupo identificador de un grupo de navegación
     */
    public void mostrarAcordeon(String idGrupo) {
        if (!hijosDe.containsKey(idGrupo)) {
            return;
        }
        // El acceso del breadcrumb debe revelar las secciones incluso si el
        // menú se contrajo manualmente; abrir el acordeón sin el texto sería
        // una acción sin resultado visible.
        if (colapsado) {
            setColapsado(false);
        }
        expandir(idGrupo, true);
    }

    private void mostrarHijos(String idGrupo, boolean visible) {
        VBox caja = hijosDe.get(idGrupo);
        boolean mostrar = visible && !colapsado;
        caja.setVisible(mostrar);
        caja.setManaged(mostrar);
        StackPane chevron = chevrones.get(idGrupo);
        if (chevron != null) {
            chevron.setRotate(visible ? 90 : 0);
        }
        botones.get(idGrupo).getProperties().put("colibri-expandido", visible);
    }

    /**
     * Marca el ítem como activo y expande su grupo.
     *
     * @param id identificador de la sección abierta
     */
    public void seleccionar(String id) {
        if (!items.containsKey(id)) {
            return;
        }
        activo = id;
        botones.values().forEach(b -> {
            b.getStyleClass().remove("nav-button-active");
            b.getStyleClass().remove("nav-group-active");
        });
        botones.get(id).getStyleClass().add("nav-button-active");
        String padre = padreDe.get(id);
        if (padre != null) {
            botones.get(padre).getStyleClass().add("nav-group-active");
            expandir(padre, true);
        } else {
            hijosDe.keySet().forEach(g -> mostrarHijos(g, false));
        }
    }

    /**
     * Cambia entre menú expandido y solo íconos.
     *
     * @param valor {@code true} para contraer
     */
    public void setColapsado(boolean valor) {
        colapsado = valor;
        pseudoClassStateChanged(COLAPSADO, valor);
        alternar.setAccessibleText(valor ? "Expandir menú de navegación" : "Contraer menú de navegación");
        marca.setAlignment(valor ? Pos.CENTER : Pos.CENTER_LEFT);
        for (Node n : List.of(logo, textoMarca)) {
            n.setVisible(!valor);
            n.setManaged(!valor);
        }
        etiquetas.forEach(l -> {
            l.setVisible(!valor);
            l.setManaged(!valor);
        });
        chevrones.values().forEach(c -> {
            c.setVisible(!valor);
            c.setManaged(!valor);
        });
        contenidos.forEach(f -> f.setAlignment(valor ? Pos.CENTER : Pos.CENTER_LEFT));
        botones.forEach((id, b) -> {
            Tooltip tip = (Tooltip) b.getProperties().get("colibri-nav-tip");
            if (valor) {
                Tooltip.install(b, tip);
            } else {
                Tooltip.uninstall(b, tip);
            }
        });
        hijosDe.forEach((id, caja) -> {
            boolean visible = !valor && Boolean.TRUE.equals(botones.get(id).getProperties().get("colibri-expandido"));
            caja.setVisible(visible);
            caja.setManaged(visible);
        });
    }

    /**
     * Estado del menú.
     *
     * @return {@code true} si está contraído
     */
    public boolean isColapsado() {
        return colapsado;
    }

    /**
     * Busca un ítem por id.
     *
     * @param id identificador
     * @return ítem, si existe
     */
    public Optional<NavItem> item(String id) {
        return Optional.ofNullable(items.get(id));
    }

    /**
     * Resuelve un id al ítem navegable (un grupo resuelve a su primera sección).
     *
     * @param id identificador de grupo u hoja
     * @return primera hoja navegable
     */
    public Optional<NavItem> destino(String id) {
        NavItem item = items.get(id);
        if (item == null) {
            return Optional.empty();
        }
        return Optional.of(item.esGrupo() ? item.hijos().getFirst() : item);
    }

    /**
     * Botón de un ítem, para pruebas y atajos.
     *
     * @param id identificador
     * @return botón, si existe
     */
    public Optional<Button> boton(String id) {
        return Optional.ofNullable(botones.get(id));
    }

    /**
     * Ids de todos los ítems, en orden de aparición.
     *
     * @return identificadores
     */
    public List<String> ids() {
        return List.copyOf(items.keySet());
    }

    /**
     * Ids de los ítems de primer nivel (grupos y hojas), en orden de aparición.
     *
     * @return identificadores
     */
    public List<String> idsRaiz() {
        return items.keySet().stream().filter(id -> !padreDe.containsKey(id)).toList();
    }
}
