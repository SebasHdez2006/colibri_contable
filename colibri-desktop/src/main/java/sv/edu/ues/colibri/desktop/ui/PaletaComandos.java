package sv.edu.ues.colibri.desktop.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;

import java.text.Normalizer;
import java.util.List;
import java.util.function.Consumer;

/**
 * Paleta de comandos (Ctrl+K): búsqueda difusa sobre las secciones de
 * navegación y acciones globales. Capa flotante sobre el área de trabajo.
 */
public final class PaletaComandos extends StackPane {

    private static final String ACCION_SESION = "accion-cerrar-sesion";

    private final TextField campo = new TextField();
    private final ListView<Opcion> lista = new ListView<>();
    private final ObservableList<Opcion> todas = FXCollections.observableArrayList();
    private final ObservableList<Opcion> visibles = FXCollections.observableArrayList();
    private final Consumer<NavItem> alAbrir;
    private final Runnable alCerrarSesion;
    private boolean abierta;

    private record Opcion(String id, String titulo, String modulo,
                          BrandIcon.Symbol icono, NavItem destino, boolean accion) {
    }

    /**
     * Construye la paleta.
     *
     * @param arbol           árbol de navegación del rol activo
     * @param alAbrir         acción al elegir una sección
     * @param alCerrarSesion  acción al elegir "Cerrar sesión"
     */
    public PaletaComandos(List<NavItem> arbol, Consumer<NavItem> alAbrir,
                          Runnable alCerrarSesion) {
        this.alAbrir = alAbrir;
        this.alCerrarSesion = alCerrarSesion;
        getStyleClass().add("palette-overlay");
        setVisible(false);
        setManaged(false);

        for (NavItem raiz : arbol) {
            if (raiz.esGrupo()) {
                for (NavItem hijo : raiz.hijos()) {
                    agregar(new Opcion(hijo.id(), hijo.etiqueta(), raiz.etiqueta(),
                            hijo.icono(), hijo, false));
                }
            } else {
                agregar(new Opcion(raiz.id(), raiz.etiqueta(), null,
                        raiz.icono(), raiz, false));
            }
        }
        agregar(new Opcion(ACCION_SESION, "Cerrar sesión", "Acción",
                BrandIcon.Symbol.LOGOUT, null, true));

        campo.getStyleClass().add("palette-field");
        campo.setPromptText("Buscar sección o acción…");
        campo.setOnAction(e -> abrirSeleccion());
        campo.textProperty().addListener((o, anterior, nuevo) -> filtrar(nuevo));
        campo.addEventFilter(KeyEvent.KEY_PRESSED, this::teclaCampo);

        lista.getStyleClass().add("palette-list");
        lista.setItems(visibles);
        lista.setPlaceholder(new Label("Sin resultados"));
        lista.setCellFactory(this::celda);
        lista.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                abrirSeleccion();
            }
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        Label pista = new Label("↑↓ navegar   Enter abrir   Esc cerrar");
        pista.getStyleClass().add("palette-hint");

        VBox panel = new VBox(10, campo, lista, pista);
        panel.getStyleClass().add("palette-panel");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(520);
        panel.setMaxWidth(560);

        Region scrim = new Region();
        scrim.getStyleClass().add("palette-scrim");
        scrim.setOnMouseClicked(e -> cerrar());

        getChildren().addAll(scrim, panel);
    }

    /** Alterna la visibilidad de la paleta. */
    public void alternar() {
        if (abierta) {
            cerrar();
        } else {
            abrir();
        }
    }

    /** Abre la paleta con la lista completa y el campo vacío. */
    public void abrir() {
        abierta = true;
        setVisible(true);
        setManaged(true);
        toFront();
        campo.setText("");
        visibles.setAll(todas);
        if (!visibles.isEmpty()) {
            lista.getSelectionModel().selectFirst();
            lista.scrollTo(0);
        }
        FadeTransition fundido = new FadeTransition(Duration.millis(120), this);
        fundido.setFromValue(0);
        fundido.setToValue(1);
        setOpacity(0);
        fundido.play();
        Platform.runLater(campo::requestFocus);
    }

    /** Cierra la paleta y limpia el filtro. */
    public void cerrar() {
        abierta = false;
        setVisible(false);
        setManaged(false);
        campo.clear();
    }

    /** @return {@code true} si la paleta está visible */
    public boolean isAbierta() {
        return abierta;
    }

    private void agregar(Opcion opcion) {
        todas.add(opcion);
    }

    private void filtrar(String texto) {
        String consulta = normalizar(texto);
        visibles.clear();
        if (consulta.isEmpty()) {
            visibles.addAll(todas);
        } else {
            for (Opcion o : todas) {
                String objetivo = normalizar(o.titulo() + " " + (o.modulo() == null ? "" : o.modulo()));
                if (objetivo.contains(consulta)) {
                    visibles.add(o);
                }
            }
        }
        if (!visibles.isEmpty()) {
            lista.getSelectionModel().selectFirst();
            lista.scrollTo(0);
        } else {
            lista.getSelectionModel().clearSelection();
        }
    }

    private void teclaCampo(KeyEvent evento) {
        switch (evento.getCode()) {
            case UP, KP_UP -> moverSeleccion(-1);
            case DOWN, KP_DOWN -> moverSeleccion(1);
            case ENTER -> abrirSeleccion();
            case ESCAPE -> cerrar();
            default -> {
            }
        }
        evento.consume();
    }

    private void moverSeleccion(int delta) {
        int actual = lista.getSelectionModel().getSelectedIndex();
        int siguiente = actual + delta;
        if (siguiente < 0) {
            siguiente = visibles.size() - 1;
        } else if (siguiente >= visibles.size()) {
            siguiente = 0;
        }
        if (!visibles.isEmpty()) {
            lista.getSelectionModel().select(siguiente);
            lista.scrollTo(siguiente);
        }
    }

    private void abrirSeleccion() {
        Opcion elegida = lista.getSelectionModel().getSelectedItem();
        if (elegida == null) {
            return;
        }
        cerrar();
        if (elegida.accion()) {
            alCerrarSesion.run();
        } else {
            alAbrir.accept(elegida.destino());
        }
    }

    private ListCell<Opcion> celda(ListView<Opcion> vista) {
        return new ListCell<>() {
            private final HBox fila = new HBox(10);
            private final Label titulo = new Label();
            private final Label modulo = new Label();
            private final Node icono = new StackPane();

            {
                titulo.getStyleClass().add("palette-item-title");
                modulo.getStyleClass().add("palette-item-sub");
                VBox textos = new VBox(1, titulo, modulo);
                textos.setMinWidth(0);
                HBox.setHgrow(textos, Priority.ALWAYS);
                fila.getChildren().addAll(icono, textos);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.setPadding(new Insets(8, 10, 8, 10));
            }

            @Override
            protected void updateItem(Opcion opcion, boolean vacia) {
                super.updateItem(opcion, vacia);
                if (vacia || opcion == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                titulo.setText(opcion.titulo());
                modulo.setText(opcion.modulo() == null ? "Sección" : opcion.modulo());
                modulo.setVisible(true);
                setGraphic(fila);
                if (opcion.icono() != null) {
                    ((StackPane) icono).getChildren().setAll(BrandIcon.create(opcion.icono()));
                } else {
                    ((StackPane) icono).getChildren().clear();
                }
            }
        };
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
