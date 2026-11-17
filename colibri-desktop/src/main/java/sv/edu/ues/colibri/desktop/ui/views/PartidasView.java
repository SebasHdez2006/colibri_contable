package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.desktop.ui.CatalogoCombo;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.desktop.ui.UiTheme;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.Empresa;
import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.EstadoPeriodo;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.Tercero;
import sv.edu.ues.colibri.domain.model.TipoPartida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PartidasView extends VBox {

    private static final String[] MESES = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
    private static final int LIMITE_RECIENTES = 200;
    private static final DecimalFormat MONEDA = new DecimalFormat("#,##0.00");

    private final AppContext context;
    private final ObservableList<LineaEditable> lineas = FXCollections.observableArrayList();
    private final TableView<LineaEditable> tabla = new TableView<>(lineas);
    private final TableView<Partida> recientes = new TableView<>();
    private final Label totalDebe = new Label("Debe: 0.00");
    private final Label totalHaber = new Label("Haber: 0.00");
    private final Label diferencia = new Label("Diferencia: 0.00");
    private final Label periodoLbl = new Label();
    private final Label selFolio = new Label();
    private final Label selOrigen = new Label();
    private final Label selConcepto = new Label();
    private final Label cuadreLbl = new Label("Cuadre: —");
    private final Label inspectorVacio = new Label("Seleccione una partida de la lista.");
    private FlowPane balanceBar;
    private UUID partidaActualId;
    private boolean sucia;
    private CeldaEditableUI edicionActiva;

    private final ComboBox<TipoPartida> tipo = new ComboBox<>(
            FXCollections.observableArrayList(TipoPartida.values()));
    private final DatePicker fecha = new DatePicker(LocalDate.of(2026, 1, 15));
    private final TextField concepto = new TextField();
    private final TextField soporte = new TextField();
    private final TextField claveEvento = new TextField();

    private List<Cuenta> cuentasMovimiento = List.of();
    private List<OpcionAuxiliar> auxiliares = List.of();
    private List<ParametrizacionService.CentroCosto> centros = List.of();
    private Map<String, String> nombrePorCuenta = Map.of();

    private CatalogoCombo<Cuenta> cuentaCombo;
    private CatalogoCombo<OpcionAuxiliar> auxiliarCombo;
    private CatalogoCombo<ParametrizacionService.CentroCosto> centroCombo;
    private final TextField debeCampo = new TextField();
    private final TextField haberCampo = new TextField();

    private final ComboBox<String> filtroEstado = new ComboBox<>(FXCollections.observableArrayList(
            "Todos", "Borrador", "Aprobada", "Contabilizada", "Anulada", "Revertida"));
    private final ComboBox<String> filtroTipo = new ComboBox<>();
    private final DatePicker filtroDesde = new DatePicker();
    private final DatePicker filtroHasta = new DatePicker();

    public PartidasView(AppContext context) {
        this.context = context;
        setSpacing(10);
        setPadding(new Insets(16));
        cargarCatalogos();
        lineas.addListener((ListChangeListener<LineaEditable>) cambio -> marcarSucia());

        Label titulo = new Label("Libro diario");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label("Capture y contabilice partidas manuales del libro diario.");
        subtitulo.getStyleClass().add("view-subtitle");

        tipo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TipoPartida value) {
                return value == null ? "" : value.getEtiqueta();
            }

            @Override
            public TipoPartida fromString(String value) {
                return Arrays.stream(TipoPartida.values())
                        .filter(tipoPartida -> tipoPartida.getEtiqueta().equals(value))
                        .findFirst().orElse(null);
            }
        });
        tipo.setValue(TipoPartida.DI);
        tipo.valueProperty().addListener((o, a, b) -> marcarSucia());
        fecha.valueProperty().addListener((o, a, b) -> {
            marcarSucia();
            actualizarPeriodoLbl();
        });
        concepto.setPromptText("Concepto de la partida");
        concepto.setPrefWidth(460);
        concepto.setMinWidth(280);
        concepto.textProperty().addListener((o, a, b) -> marcarSucia());
        soporte.setPromptText("Documento / soporte");
        soporte.textProperty().addListener((o, a, b) -> marcarSucia());
        claveEvento.setPromptText("Referencia del evento (opcional)");
        claveEvento.setPrefWidth(280);
        claveEvento.getStyleClass().add("event-reference");
        claveEvento.textProperty().addListener((o, a, b) -> marcarSucia());

        periodoLbl.getStyleClass().add("status-pill");
        VBox periodoCampo = new VBox(4, new Label("Período"), periodoLbl);
        periodoCampo.setMinWidth(130);
        periodoCampo.setPrefWidth(210);
        periodoCampo.setMaxWidth(340);

        VBox captura = ResponsiveLayouts.sectionCard("Partidas contables",
                "Complete los datos del asiento y agregue sus líneas; el debe y el haber "
                        + "deben cuadrar antes de contabilizar.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Tipo de partida", tipo),
                        ResponsiveLayouts.field("Fecha contable", fecha), periodoCampo),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Concepto", concepto)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Documento de soporte", soporte),
                        ResponsiveLayouts.field("Referencia del evento", claveEvento)));

        cuentaCombo = new CatalogoCombo<>(cuentasMovimiento, conversorCuenta(),
                Cuenta::getCodigo, this::buscarCuentas);
        cuentaCombo.getEditor().setPromptText("Buscar cuenta");
        auxiliarCombo = new CatalogoCombo<>(auxiliares, conversorAuxiliar(),
                OpcionAuxiliar::codigo, this::buscarAuxiliares);
        auxiliarCombo.getEditor().setPromptText("Tercero, lote u orden");
        centroCombo = new CatalogoCombo<>(centros, conversorCentro(),
                ParametrizacionService.CentroCosto::codigo, this::buscarCentros);
        centroCombo.getEditor().setPromptText("Centro de costo");
        debeCampo.setPromptText("0.00");
        haberCampo.setPromptText("0.00");

        Button agregar = new Button("Agregar línea");
        agregar.setOnAction(e -> agregarLinea());
        Button quitar = new Button("Quitar línea");
        quitar.setOnAction(e -> {
            LineaEditable sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) {
                lineas.remove(sel);
                actualizarTotales();
            }
        });
        Button cuadrar = new Button("Cuadrar con cuenta");
        cuadrar.setOnAction(e -> mostrarCuadre());

        var lineBox = ResponsiveLayouts.row(ResponsiveLayouts.field("Cuenta", cuentaCombo),
                ResponsiveLayouts.field("Auxiliar", auxiliarCombo),
                ResponsiveLayouts.field("Centro", centroCombo),
                ResponsiveLayouts.field("Debe (USD)", debeCampo),
                ResponsiveLayouts.field("Haber (USD)", haberCampo),
                agregar, quitar, cuadrar);
        configurarTablaLineas();
        tabla.setMinHeight(300);
        tabla.setPrefHeight(380);

        Button nueva = UiTheme.boton("Nueva", UiTheme.Variante.SECUNDARIA);
        Button guardar = UiTheme.boton("Guardar borrador", UiTheme.Variante.PRIMARIA);
        Button contabilizar = UiTheme.boton("Contabilizar", UiTheme.Variante.EXITO);
        Button comprobante = UiTheme.boton("Comprobante", UiTheme.Variante.SECUNDARIA);
        Button evidencia = UiTheme.boton("Cadena de evidencia", UiTheme.Variante.SECUNDARIA);

        MenuButton mas = new MenuButton("Más");
        mas.getStyleClass().add("secondary-button");
        MenuItem miDuplicar = new MenuItem("Duplicar partida");
        miDuplicar.setOnAction(e -> duplicarPartida());
        MenuItem miImportar = new MenuItem("Importar CSV");
        miImportar.setOnAction(e -> importarCsv());
        MenuItem miAprobar = new MenuItem("Aprobar");
        miAprobar.setOnAction(e -> aprobarPartida());
        MenuItem miAnular = new MenuItem("Anular");
        miAnular.setOnAction(e -> anularPartida());
        MenuItem miRevertir = new MenuItem("Revertir contabilizada");
        miRevertir.setOnAction(e -> revertirPartida());
        mas.getItems().addAll(miDuplicar, miImportar, miAprobar, miAnular, miRevertir);

        configurarRecientes();

        nueva.setOnAction(e -> nuevaPartida());
        guardar.setOnAction(e -> guardarBorrador());
        contabilizar.setOnAction(e -> contabilizarPartida());
        comprobante.setOnAction(e -> mostrarComprobanteSeleccionada());
        evidencia.setOnAction(e -> mostrarEvidencia());

        DatePicker exportDesde = new DatePicker(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        DatePicker exportHasta = new DatePicker(LocalDate.now());
        Button exportar = new Button("Exportar diario filtrado");
        exportar.setOnAction(e -> exportarDiario(exportDesde.getValue(), exportHasta.getValue()));

        // Barra de cuadre persistente bajo la tabla de líneas.
        balanceBar = ResponsiveLayouts.row(totalDebe, totalHaber, diferencia);
        balanceBar.getStyleClass().add("balance-bar");

        var acciones = ResponsiveLayouts.row(nueva, guardar, contabilizar, mas);
        VBox lineasCard = ResponsiveLayouts.sectionCard("Líneas del asiento",
                "Cada línea registra cuenta, auxiliar y centro de costo con su importe en "
                        + "debe o haber; el cuadre se muestra debajo de la tabla.",
                lineBox, UiTables.conBusqueda(tabla, "líneas del asiento", linea ->
                        linea.getCodigo() + " " + linea.getAuxiliar() + " " + linea.getCentro()
                                + " " + linea.getDebe() + " " + linea.getHaber()));
        tabla.getStyleClass().add("journal-lines-table");
        VBox.setVgrow(lineasCard, Priority.ALWAYS);

        // Columna izquierda: lista de partidas con sus filtros.
        Label tituloLista = new Label("Partidas");
        tituloLista.getStyleClass().add("section-heading");
        VBox listaCard = new VBox(10, tituloLista,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Estado", filtroEstado),
                        ResponsiveLayouts.field("Tipo", filtroTipo),
                        ResponsiveLayouts.field("Desde", filtroDesde),
                        ResponsiveLayouts.field("Hasta", filtroHasta)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Diario desde", exportDesde),
                        ResponsiveLayouts.field("Diario hasta", exportHasta), exportar),
                UiTables.conBusqueda(recientes, "partidas", partida ->
                        String.valueOf(partida.getFolio()) + " " + partida.getConcepto() + " "
                                + partida.getFechaContable() + " " + partida.getEstado() + " "
                                + partida.getOrigen()));
        listaCard.getStyleClass().addAll("surface-card", "journal-list");
        listaCard.setPrefWidth(320);
        listaCard.setMinWidth(300);
        recientes.setMinHeight(160);
        VBox.setVgrow(recientes, Priority.ALWAYS);

        // Columna central: captura, líneas, cuadre, acciones e inspector
        // inferior con el detalle de la partida seleccionada.
        VBox centro = new VBox(12, captura, lineasCard, balanceBar, acciones,
                inspectorInferior(comprobante, evidencia));
        centro.setMinWidth(0);
        HBox.setHgrow(centro, Priority.ALWAYS);

        HBox columnas = new HBox(16, listaCard, centro);
        columnas.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(titulo, subtitulo, columnas);

        recientes.getSelectionModel().getSelectedItems()
                .addListener((ListChangeListener<Partida>) c ->
                        actualizarInspector(recientes.getSelectionModel().getSelectedItem()));

        haberCampo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isControlDown() && !e.isAltDown()
                    && !e.isShiftDown()) {
                agregarLinea();
                e.consume();
            }
        });
        setOnKeyPressed(this::atajosVista);

        refrescarRecientes();
        actualizarTotales();
        actualizarPeriodoLbl();
    }

    // ------------------------------------------------------------------
    // Catálogos de captura (cuenta, auxiliar, centro de costo)
    // ------------------------------------------------------------------

    private void cargarCatalogos() {
        try {
            List<Cuenta> todas = context.catalogo().listar();
            nombrePorCuenta = todas.stream()
                    .collect(Collectors.toMap(Cuenta::getCodigo, Cuenta::getNombre, (a, b) -> a));
            cuentasMovimiento = todas.stream().filter(Cuenta::puedeRecibirMovimiento).toList();
        } catch (Exception ex) {
            cuentasMovimiento = List.of();
        }

        List<OpcionAuxiliar> aux = new ArrayList<>();
        try {
            for (Tercero t : context.operaciones().listarTerceros()) {
                if (t.isActivo()) {
                    aux.add(new OpcionAuxiliar(t.getNit(), "Tercero", t.getRazonSocial()));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (InventarioCostoService.LoteInfo l : context.inventario().listarLotes()) {
                aux.add(new OpcionAuxiliar(l.codigo(), "Lote",
                        l.disponibles() + " licencia(s) · $" + l.costoUnitario().toPlainString()));
            }
        } catch (Exception ignored) {
        }
        try {
            for (InventarioCostoService.OrdenResumen o : context.inventario().listarOrdenes()) {
                if (!"CANCELADA".equalsIgnoreCase(o.estado())) {
                    aux.add(new OpcionAuxiliar(o.codigo(), "Orden " + o.estado(), o.descripcion()));
                }
            }
        } catch (Exception ignored) {
        }
        aux.sort(Comparator.comparing(OpcionAuxiliar::codigo));
        auxiliares = aux;

        try {
            centros = context.parametrizacion().listarCentros().stream()
                    .filter(ParametrizacionService.CentroCosto::activo).toList();
        } catch (Exception ex) {
            centros = List.of();
        }
    }

    private List<Cuenta> buscarCuentas(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        return cuentasMovimiento.stream()
                .filter(c -> q.isEmpty() || c.getCodigo().toLowerCase().contains(q)
                        || c.getNombre().toLowerCase().contains(q))
                .toList();
    }

    private List<OpcionAuxiliar> buscarAuxiliares(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        return auxiliares.stream()
                .filter(a -> q.isEmpty() || a.codigo().toLowerCase().contains(q)
                        || a.tipo().toLowerCase().contains(q)
                        || a.detalle().toLowerCase().contains(q))
                .toList();
    }

    private List<ParametrizacionService.CentroCosto> buscarCentros(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        return centros.stream()
                .filter(c -> q.isEmpty() || c.codigo().toLowerCase().contains(q)
                        || c.nombre().toLowerCase().contains(q))
                .toList();
    }

    private static StringConverter<Cuenta> conversorCuenta() {
        return new StringConverter<>() {
            @Override
            public String toString(Cuenta value) {
                return value == null ? "" : value.getCodigo() + " — " + value.getNombre();
            }

            @Override
            public Cuenta fromString(String value) {
                return null;
            }
        };
    }

    private static StringConverter<OpcionAuxiliar> conversorAuxiliar() {
        return new StringConverter<>() {
            @Override
            public String toString(OpcionAuxiliar value) {
                return value == null ? ""
                        : value.codigo() + " — " + value.tipo() + " · " + value.detalle();
            }

            @Override
            public OpcionAuxiliar fromString(String value) {
                return null;
            }
        };
    }

    private static StringConverter<ParametrizacionService.CentroCosto> conversorCentro() {
        return new StringConverter<>() {
            @Override
            public String toString(ParametrizacionService.CentroCosto value) {
                return value == null ? "" : value.codigo() + " — " + value.nombre();
            }

            @Override
            public ParametrizacionService.CentroCosto fromString(String value) {
                return null;
            }
        };
    }

    // ------------------------------------------------------------------
    // Tabla de líneas editable en el sitio
    // ------------------------------------------------------------------

    private void configurarTablaLineas() {
        TableColumn<LineaEditable, String> cCuenta = new TableColumn<>("Cuenta");
        cCuenta.setCellValueFactory(c -> c.getValue().codigo);
        cCuenta.setPrefWidth(120);
        columnaCatalogo(cCuenta, cuentasMovimiento, conversorCuenta(), Cuenta::getCodigo,
                this::buscarCuentas, LineaEditable::getCodigo, LineaEditable::setCodigo);

        TableColumn<LineaEditable, String> cAuxiliar = new TableColumn<>("Auxiliar");
        cAuxiliar.setCellValueFactory(c -> c.getValue().auxiliar);
        cAuxiliar.setPrefWidth(150);
        columnaCatalogo(cAuxiliar, auxiliares, conversorAuxiliar(), OpcionAuxiliar::codigo,
                this::buscarAuxiliares, LineaEditable::getAuxiliar, LineaEditable::setAuxiliar);

        TableColumn<LineaEditable, String> cCentro = new TableColumn<>("Centro");
        cCentro.setCellValueFactory(c -> c.getValue().centro);
        cCentro.setPrefWidth(90);
        columnaCatalogo(cCentro, centros, conversorCentro(),
                ParametrizacionService.CentroCosto::codigo, this::buscarCentros,
                LineaEditable::getCentro, LineaEditable::setCentro);

        TableColumn<LineaEditable, String> cDebe = new TableColumn<>("Debe");
        cDebe.setCellValueFactory(c -> c.getValue().debe);
        columnaImporte(cDebe, LineaEditable::getDebe, LineaEditable::setDebe);

        TableColumn<LineaEditable, String> cHaber = new TableColumn<>("Haber");
        cHaber.setCellValueFactory(c -> c.getValue().haber);
        columnaImporte(cHaber, LineaEditable::getHaber, LineaEditable::setHaber);

        tabla.getColumns().addAll(cCuenta, cAuxiliar, cCentro, cDebe, cHaber);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Agregue líneas con el formulario inferior."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setRowFactory(tv -> new TableRow<LineaEditable>() {
            private LineaEditable actual;
            private final javafx.beans.InvalidationListener repintar = obs -> pintar();

            {
                itemProperty().addListener((o, anterior, nuevo) -> {
                    if (anterior != null) {
                        anterior.invalidaProperty().removeListener(repintar);
                    }
                    actual = nuevo;
                    if (nuevo != null) {
                        nuevo.invalidaProperty().addListener(repintar);
                    }
                    pintar();
                });
            }

            private void pintar() {
                getStyleClass().remove("linea-error");
                if (actual != null && actual.invalidaProperty().get()) {
                    getStyleClass().add("linea-error");
                }
            }
        });
        tabla.setOnKeyPressed(this::tecladoTabla);
    }

    private <T> void columnaCatalogo(TableColumn<LineaEditable, String> columna, List<T> items,
            StringConverter<T> converter, Function<T, String> clave,
            Function<String, List<T>> buscador, Function<LineaEditable, String> lector,
            BiConsumer<LineaEditable, String> escritor) {
        columna.setCellFactory(c -> new CeldaCatalogo<>(items, converter, clave, buscador,
                lector, escritor));
    }

    private void columnaImporte(TableColumn<LineaEditable, String> columna,
            Function<LineaEditable, String> lector, BiConsumer<LineaEditable, String> escritor) {
        columna.setCellFactory(c -> new CeldaImporte(lector, escritor));
    }

    private interface CeldaEditableUI {
        void editar();

        void confirmar();
    }

    private final class CeldaCatalogo<T> extends TableCell<LineaEditable, String>
            implements CeldaEditableUI {
        private final List<T> items;
        private final StringConverter<T> converter;
        private final Function<T, String> clave;
        private final Function<String, List<T>> buscador;
        private final Function<LineaEditable, String> lector;
        private final BiConsumer<LineaEditable, String> escritor;
        private CatalogoCombo<T> combo;
        private boolean editando;

        CeldaCatalogo(List<T> items, StringConverter<T> converter, Function<T, String> clave,
                Function<String, List<T>> buscador, Function<LineaEditable, String> lector,
                BiConsumer<LineaEditable, String> escritor) {
            this.items = items;
            this.converter = converter;
            this.clave = clave;
            this.buscador = buscador;
            this.lector = lector;
            this.escritor = escritor;
            setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2) {
                    editar();
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (editando) {
                return;
            }
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                setText(item == null ? "" : item);
                setGraphic(null);
                String detalle = descripcionDe(item);
                setTooltip(detalle == null || detalle.equals(item) ? null : new Tooltip(detalle));
            }
        }

        private String descripcionDe(String codigo) {
            if (codigo == null || codigo.isBlank()) {
                return null;
            }
            return items.stream()
                    .filter(t -> clave.apply(t).equalsIgnoreCase(codigo.trim()))
                    .findFirst()
                    .map(converter::toString)
                    .orElse(null);
        }

        @Override
        public void editar() {
            if (editando || isEmpty() || getTableRow() == null
                    || getTableRow().getItem() == null) {
                return;
            }
            LineaEditable linea = getTableRow().getItem();
            combo = new CatalogoCombo<>(items, converter, clave, buscador);
            combo.seleccionarCodigo(lector.apply(linea));
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setMinWidth(Math.max(120, getWidth()));
            combo.setAlConfirmar(this::confirmarEdicion);
            combo.getEditor().setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelarEdicion();
                    e.consume();
                }
            });
            editando = true;
            edicionActiva = this;
            setText(null);
            setGraphic(combo);
            combo.getEditor().requestFocus();
            combo.getEditor().selectAll();
        }

        @Override
        public void confirmar() {
            confirmarEdicion();
        }

        private void confirmarEdicion() {
            if (!editando || combo == null || getTableRow() == null
                    || getTableRow().getItem() == null) {
                return;
            }
            escritor.accept(getTableRow().getItem(), combo.codigo());
            terminarEdicion();
        }

        private void cancelarEdicion() {
            terminarEdicion();
        }

        private void terminarEdicion() {
            editando = false;
            combo = null;
            if (edicionActiva == this) {
                edicionActiva = null;
            }
            updateItem(getItem(), isEmpty());
            if (getTableView() != null) {
                getTableView().requestFocus();
            }
        }
    }

    private final class CeldaImporte extends TableCell<LineaEditable, String>
            implements CeldaEditableUI {
        private final Function<LineaEditable, String> lector;
        private final BiConsumer<LineaEditable, String> escritor;
        private TextField campo;
        private boolean editando;

        CeldaImporte(Function<LineaEditable, String> lector,
                BiConsumer<LineaEditable, String> escritor) {
            this.lector = lector;
            this.escritor = escritor;
            setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2) {
                    editar();
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (editando) {
                return;
            }
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item == null ? "" : item);
                setGraphic(null);
            }
        }

        @Override
        public void editar() {
            if (editando || isEmpty() || getTableRow() == null
                    || getTableRow().getItem() == null) {
                return;
            }
            campo = new TextField(getItem() == null ? "" : getItem());
            campo.setMaxWidth(Double.MAX_VALUE);
            campo.setMinWidth(Math.max(80, getWidth()));
            editando = true;
            edicionActiva = this;
            setText(null);
            setGraphic(campo);
            campo.setOnAction(e -> confirmarEdicion());
            campo.focusedProperty().addListener((o, a, tieneFoco) -> {
                if (!tieneFoco) {
                    confirmarEdicion();
                }
            });
            campo.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelarEdicion();
                    e.consume();
                }
            });
            campo.requestFocus();
            campo.selectAll();
        }

        @Override
        public void confirmar() {
            confirmarEdicion();
        }

        private void confirmarEdicion() {
            if (!editando || campo == null || getTableRow() == null
                    || getTableRow().getItem() == null) {
                return;
            }
            escritor.accept(getTableRow().getItem(), campo.getText().trim());
            terminarEdicion();
        }

        private void cancelarEdicion() {
            terminarEdicion();
        }

        private void terminarEdicion() {
            editando = false;
            campo = null;
            if (edicionActiva == this) {
                edicionActiva = null;
            }
            updateItem(getItem(), isEmpty());
            if (getTableView() != null) {
                getTableView().requestFocus();
            }
        }
    }

    private void tecladoTabla(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isShiftDown()) {
            return;
        }
        if (e.getTarget() instanceof TextField) {
            return;
        }
        if (e.getCode() == KeyCode.DELETE) {
            LineaEditable sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) {
                lineas.remove(sel);
                actualizarTotales();
                e.consume();
            }
            return;
        }
        if (e.getCode() != KeyCode.F2 && e.getCode() != KeyCode.ENTER) {
            return;
        }
        int row = tabla.getSelectionModel().getSelectedIndex();
        int col = tabla.getFocusModel().getFocusedCell().getColumn();
        if (row < 0 || col < 0) {
            return;
        }
        TableColumn<LineaEditable, ?> column = tabla.getVisibleLeafColumn(col);
        if (column == null) {
            return;
        }
        for (javafx.scene.Node nodo : tabla.lookupAll(".table-cell")) {
            if (nodo instanceof TableCell<?, ?> cell && cell.getTableColumn() == column
                    && cell.getIndex() == row && cell instanceof CeldaEditableUI editable) {
                editable.editar();
                e.consume();
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Captura y validación de líneas
    // ------------------------------------------------------------------

    private void agregarLinea() {
        String c = cuentaCombo.codigo();
        String a = vacioANull(auxiliarCombo.codigo());
        String cc = vacioANull(centroCombo.codigo());
        String d = debeCampo.getText().trim();
        String h = haberCampo.getText().trim();
        if (c.isEmpty() && a == null && cc == null && d.isEmpty() && h.isEmpty()) {
            return;
        }
        lineas.add(new LineaEditable(c, a == null ? "" : a, cc == null ? "" : cc,
                d.isEmpty() ? "0" : d, h.isEmpty() ? "0" : h));
        cuentaCombo.limpiar();
        auxiliarCombo.limpiar();
        centroCombo.limpiar();
        debeCampo.clear();
        haberCampo.clear();
        actualizarTotales();
        cuentaCombo.getEditor().requestFocus();
    }

    private void mostrarCuadre() {
        BigDecimal d = suma(true);
        BigDecimal h = suma(false);
        BigDecimal dif = d.subtract(h);
        if (dif.signum() == 0) {
            UiDialogs.info("Cuadrado", "El asiento ya cuadra.");
            return;
        }
        String lado = dif.signum() > 0 ? "Haber" : "Debe";
        BigDecimal monto = dif.abs().setScale(2, RoundingMode.HALF_UP);

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Cuadrar asiento");
        dialog.setHeaderText("Se propone la línea que iguala el asiento");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == ButtonType.OK);

        Label aviso = new Label("Falta registrar en " + lado + " por $" + MONEDA.format(monto)
                + ". Complete cuenta y auxiliares:");
        CatalogoCombo<Cuenta> cta = new CatalogoCombo<>(cuentasMovimiento, conversorCuenta(),
                Cuenta::getCodigo, this::buscarCuentas);
        cta.getEditor().setPromptText("Buscar cuenta");
        CatalogoCombo<OpcionAuxiliar> aux = new CatalogoCombo<>(auxiliares, conversorAuxiliar(),
                OpcionAuxiliar::codigo, this::buscarAuxiliares);
        aux.getEditor().setPromptText("Tercero, lote u orden");
        CatalogoCombo<ParametrizacionService.CentroCosto> cen = new CatalogoCombo<>(centros,
                conversorCentro(), ParametrizacionService.CentroCosto::codigo, this::buscarCentros);
        cen.getEditor().setPromptText("Centro de costo");

        VBox contenido = new VBox(10, aviso,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Cuenta (" + lado + ")", cta),
                        ResponsiveLayouts.field("Auxiliar", aux),
                        ResponsiveLayouts.field("Centro", cen)));
        contenido.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(contenido);
        UiTheme.style(dialog);

        if (!dialog.showAndWait().orElse(false)) {
            return;
        }
        String cod = cta.codigo();
        if (cod.isEmpty()) {
            UiDialogs.error("Indique la cuenta de la línea de cuadre.");
            return;
        }
        String debe = "Debe".equals(lado) ? monto.toPlainString() : "0";
        String haber = "Haber".equals(lado) ? monto.toPlainString() : "0";
        String auxCod = vacioANull(aux.codigo());
        String cenCod = vacioANull(cen.codigo());
        lineas.add(new LineaEditable(cod, auxCod == null ? "" : auxCod,
                cenCod == null ? "" : cenCod, debe, haber));
        actualizarTotales();
    }

    private boolean lineasValidas() {
        List<String> problemas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            String motivo = lineas.get(i).motivoInvalidez();
            if (motivo != null) {
                problemas.add("Línea " + (i + 1) + ": " + motivo);
            }
        }
        if (!problemas.isEmpty()) {
            UiDialogs.error("Corrija las líneas marcadas en rojo:\n" + String.join("\n", problemas));
            return false;
        }
        return true;
    }

    private List<PartidaDetalle> aDetalles() {
        List<PartidaDetalle> lista = new ArrayList<>();
        int n = 0;
        for (LineaEditable l : lineas) {
            n++;
            String motivo = l.motivoInvalidez();
            if (motivo != null) {
                throw new DomainException("Línea " + n + ": " + motivo);
            }
            BigDecimal d = numeroONull(l.debe.get());
            BigDecimal h = numeroONull(l.haber.get());
            if (d == null || h == null) {
                throw new DomainException("Línea " + n + ": importe no numérico");
            }
            lista.add(PartidaDetalle.nueva(l.codigo.get().trim(), vacioANull(l.auxiliar.get()),
                    vacioANull(l.centro.get()), d, h));
        }
        return lista;
    }

    private BigDecimal suma(boolean ladoDebe) {
        BigDecimal t = BigDecimal.ZERO;
        for (LineaEditable l : lineas) {
            BigDecimal v = numeroONull(ladoDebe ? l.debe.get() : l.haber.get());
            if (v != null) {
                t = t.add(v);
            }
        }
        return t.setScale(2, RoundingMode.HALF_UP);
    }

    private void actualizarTotales() {
        BigDecimal d = suma(true);
        BigDecimal h = suma(false);
        totalDebe.setText("Debe: " + MONEDA.format(d));
        totalHaber.setText("Haber: " + MONEDA.format(h));
        BigDecimal dif = d.subtract(h);
        diferencia.setText("Diferencia: " + MONEDA.format(dif.abs()));
        diferencia.getStyleClass().removeAll("balance-error", "balance-ok");
        diferencia.getStyleClass().add(dif.signum() != 0 ? "balance-error" : "balance-ok");
        if (balanceBar != null) {
            balanceBar.getStyleClass().removeAll("balance-bar-error", "balance-bar-ok");
            balanceBar.getStyleClass().add(dif.signum() != 0 ? "balance-bar-error" : "balance-bar-ok");
        }
        if (cuadreLbl != null) {
            cuadreLbl.setText(dif.signum() == 0
                    ? "Cuadrado — el asiento puede contabilizarse."
                    : "Descuadre de $" + MONEDA.format(dif.abs()) + " ("
                            + (dif.signum() > 0 ? "falta Haber" : "falta Debe") + ")");
            cuadreLbl.getStyleClass().removeAll("balance-error", "balance-ok");
            cuadreLbl.getStyleClass().add(dif.signum() != 0 ? "balance-error" : "balance-ok");
        }
    }

    // ------------------------------------------------------------------
    // Acciones sobre la partida
    // ------------------------------------------------------------------

    private void marcarSucia() {
        sucia = true;
    }

    private void confirmarEdicionActiva() {
        CeldaEditableUI activa = edicionActiva;
        if (activa != null) {
            activa.confirmar();
        }
    }

    private void nuevaPartida() {
        partidaActualId = null;
        lineas.clear();
        concepto.clear();
        soporte.clear();
        claveEvento.clear();
        actualizarTotales();
        sucia = false;
    }

    private boolean asegurarPartidaGuardada() {
        confirmarEdicionActiva();
        if (partidaActualId != null && !sucia) {
            return true;
        }
        if (partidaActualId != null && sucia
                && !UiDialogs.confirmar("Cambios sin guardar",
                        "La partida tiene cambios sin guardar. ¿Guardar antes de continuar?")) {
            return false;
        }
        return guardarBorrador();
    }

    private boolean guardarBorrador() {
        confirmarEdicionActiva();
        try {
            if (fecha.getValue() == null) {
                UiDialogs.error("Seleccione la fecha contable.");
                return false;
            }
            if (lineas.isEmpty()) {
                UiDialogs.error("Agregue al menos una línea al asiento.");
                return false;
            }
            if (!lineasValidas()) {
                return false;
            }
            Partida p = context.partidas().guardarBorrador(
                    tipo.getValue(), fecha.getValue(), concepto.getText().trim(),
                    soporte.getText().trim(), claveEvento.getText().trim(),
                    aDetalles(), partidaActualId);
            partidaActualId = p.getId();
            sucia = false;
            UiDialogs.info("Borrador", "Guardado. Estado: " + p.getEstado()
                    + (p.estaEquilibrada() ? "" : " (descuadrado permitido en borrador)"));
            refrescarRecientes();
            return true;
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
            return false;
        }
    }

    private boolean asientoCuadrado() {
        if (suma(true).compareTo(suma(false)) == 0) {
            return true;
        }
        UiDialogs.error("El asiento está descuadrado por "
                + MONEDA.format(suma(true).subtract(suma(false)).abs())
                + ". Use «Cuadrar con cuenta» para proponer la línea faltante.");
        return false;
    }

    private void aprobarPartida() {
        if (!asegurarPartidaGuardada() || !asientoCuadrado()) {
            return;
        }
        try {
            Partida p = context.partidas().aprobar(partidaActualId);
            UiDialogs.info("Aprobada", "Partida aprobada. Diferencia debe ser 0.");
            refrescarRecientes();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void contabilizarPartida() {
        if (!asegurarPartidaGuardada() || !asientoCuadrado()) {
            return;
        }
        if (!UiDialogs.confirmar("Contabilizar",
                "Se asignará folio y la partida quedará inmutable. ¿Continuar?")) {
            return;
        }
        try {
            Partida p = context.partidas().contabilizar(partidaActualId);
            UiDialogs.info("Contabilizada", "Folio: " + p.getFolio());
            refrescarRecientes();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void anularPartida() {
        if (partidaActualId == null) {
            return;
        }
        String motivo = UiDialogs.pedirTexto("Anular", "Motivo de anulación");
        if (motivo == null || motivo.isBlank()) {
            return;
        }
        try {
            context.partidas().anular(partidaActualId, motivo);
            UiDialogs.info("Anulada", "Partida anulada sin afectar libros.");
            partidaActualId = null;
            lineas.clear();
            refrescarRecientes();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void revertirPartida() {
        Partida sel = recientes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            UiDialogs.error("Seleccione una partida contabilizada en la lista.");
            return;
        }
        String motivo = UiDialogs.pedirTexto("Reversión", "Motivo de la reversión");
        if (motivo == null || motivo.isBlank()) {
            return;
        }
        try {
            Partida rev = context.partidas().revertir(sel.getId(), fecha.getValue(), motivo);
            UiDialogs.info("Revertida", "Reversión " + rev.getFolio()
                    + ". El original permanece visible.");
            refrescarRecientes();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void duplicarPartida() {
        UUID id = null;
        Partida sel = recientes.getSelectionModel().getSelectedItem();
        if (sel != null) {
            id = sel.getId();
        } else if (partidaActualId != null) {
            id = partidaActualId;
        }
        if (id == null) {
            UiDialogs.error("Seleccione una partida de la lista o cargue una primero.");
            return;
        }
        try {
            Partida p = context.partidas().buscar(id)
                    .orElseThrow(() -> new DomainException("Partida no encontrada"));
            partidaActualId = null;
            tipo.setValue(p.getTipo());
            fecha.setValue(p.getFechaContable());
            concepto.setText(p.getConcepto());
            soporte.setText(p.getDocumentoSoporte() == null ? "" : p.getDocumentoSoporte());
            claveEvento.clear();
            lineas.clear();
            for (PartidaDetalle d : p.getDetalles()) {
                lineas.add(new LineaEditable(d.getCodigoCuenta(),
                        d.getAuxiliar() == null ? "" : d.getAuxiliar(),
                        d.getCentroCosto() == null ? "" : d.getCentroCosto(),
                        d.getDebe().toPlainString(), d.getHaber().toPlainString()));
            }
            actualizarTotales();
            sucia = false;
            UiDialogs.info("Duplicada", "Las líneas quedaron como borrador nuevo sin guardar. "
                    + "Revise y use «Guardar borrador».");
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void importarCsv() {
        if (fecha.getValue() == null) {
            UiDialogs.error("Seleccione la fecha contable antes de importar.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar asiento desde CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"));
        var archivo = chooser.showOpenDialog(getScene().getWindow());
        if (archivo == null) {
            return;
        }
        try {
            String contenido = Files.readString(archivo.toPath());
            Partida p = context.operaciones().importarBorradorCsv(contenido, fecha.getValue());
            cargarPartida(p);
            UiDialogs.info("CSV importado", "Borrador cargado con " + p.getDetalles().size()
                    + " líneas. Revise y use «Guardar borrador».");
            refrescarRecientes();
        } catch (Exception ex) {
            UiDialogs.error("No se pudo importar el CSV: " + ex.getMessage());
        }
    }

    private void cargarPartida(Partida p) {
        partidaActualId = p.getId();
        tipo.setValue(p.getTipo());
        fecha.setValue(p.getFechaContable());
        concepto.setText(p.getConcepto());
        soporte.setText(p.getDocumentoSoporte() == null ? "" : p.getDocumentoSoporte());
        claveEvento.setText(p.getEventoId() != null ? p.getEventoId().toString() : "");
        lineas.clear();
        for (PartidaDetalle d : p.getDetalles()) {
            lineas.add(new LineaEditable(d.getCodigoCuenta(),
                    d.getAuxiliar() == null ? "" : d.getAuxiliar(),
                    d.getCentroCosto() == null ? "" : d.getCentroCosto(),
                    d.getDebe().toPlainString(), d.getHaber().toPlainString()));
        }
        actualizarTotales();
        sucia = false;
    }

    private void mostrarEvidencia() {
        Partida sel = recientes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            UiDialogs.error("Seleccione una partida de la lista de recientes.");
            return;
        }
        try {
            PartidaService.CadenaEvidencia cadena = context.partidas().cadenaEvidencia(sel.getId());
            UiDialogs.mostrar("Cadena de evidencia — " + sel.getConcepto(), cadenaEvidencia(cadena, sel));
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Recientes con filtros
    // ------------------------------------------------------------------

    private void configurarRecientes() {
        filtroTipo.getItems().add("Todos");
        for (TipoPartida t : TipoPartida.values()) {
            filtroTipo.getItems().add(t.getEtiqueta());
        }
        filtroTipo.setValue("Todos");
        filtroEstado.setValue("Todos");
        filtroDesde.getEditor().setPromptText("Desde");
        filtroHasta.getEditor().setPromptText("Hasta");

        TableColumn<Partida, String> rFecha = new TableColumn<>("Fecha");
        rFecha.setPrefWidth(100);
        rFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFechaContable().toString()));
        TableColumn<Partida, String> rFolio = new TableColumn<>("Folio/Estado");
        rFolio.setPrefWidth(150);
        rFolio.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().getFolio() != null ? c.getValue().getFolio() : "(sin folio)")
                        + " / " + c.getValue().getEstado()));
        TableColumn<Partida, String> rConc = new TableColumn<>("Concepto");
        rConc.setPrefWidth(240);
        rConc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getConcepto()));
        TableColumn<Partida, String> rOrigen = new TableColumn<>("Origen");
        rOrigen.setPrefWidth(90);
        rOrigen.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getOrigen() != null ? c.getValue().getOrigen().name() : ""));
        recientes.getColumns().addAll(rFecha, rFolio, rConc, rOrigen);
        recientes.setMinHeight(100);
        recientes.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                Partida p = recientes.getSelectionModel().getSelectedItem();
                if (p != null) {
                    cargarPartida(p);
                }
            }
        });
        filtroEstado.valueProperty().addListener((o, a, b) -> refrescarRecientes());
        filtroTipo.valueProperty().addListener((o, a, b) -> refrescarRecientes());
        filtroDesde.valueProperty().addListener((o, a, b) -> refrescarRecientes());
        filtroHasta.valueProperty().addListener((o, a, b) -> refrescarRecientes());
    }

    private EstadoPartida estadoFiltro() {
        String v = filtroEstado.getValue();
        return (v == null || "Todos".equals(v)) ? null : EstadoPartida.valueOf(v);
    }

    private TipoPartida tipoFiltro() {
        String v = filtroTipo.getValue();
        if (v == null || "Todos".equals(v)) {
            return null;
        }
        return Arrays.stream(TipoPartida.values())
                .filter(t -> t.getEtiqueta().equals(v)).findFirst().orElse(null);
    }

    private void refrescarRecientes() {
        LocalDate desde = filtroDesde.getValue();
        LocalDate hasta = filtroHasta.getValue();
        List<Partida> items;
        try {
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                items = List.of();
            } else {
                items = context.partidas().listarRecientes(estadoFiltro(), tipoFiltro(),
                        desde, hasta, LIMITE_RECIENTES);
            }
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
            items = List.of();
        }
        UiTables.reemplazarFilas(recientes, items);
    }

    // ------------------------------------------------------------------
    // Inspector inferior: detalle de la partida seleccionada, cuadre del
    // asiento y acciones de documento en una franja bajo la tabla de líneas.
    // ------------------------------------------------------------------

    private Node inspectorInferior(Button comprobante, Button evidencia) {
        VBox insp = new VBox(10);
        insp.getStyleClass().addAll("surface-card", "journal-inspector");

        Label subtituloSel = new Label("Partida seleccionada");
        subtituloSel.getStyleClass().add("inspector-subheading");
        selFolio.getStyleClass().add("inspector-folio");
        selOrigen.getStyleClass().add("muted-caption");
        selConcepto.getStyleClass().add("inspector-concepto");
        selConcepto.setWrapText(true);
        inspectorVacio.getStyleClass().add("muted-caption");
        VBox seleccion = new VBox(4, subtituloSel, selFolio, selOrigen,
                selConcepto, inspectorVacio);
        seleccion.setMinWidth(0);
        seleccion.setPrefWidth(320);

        Label tituloCuadre = new Label("Cuadre del asiento");
        tituloCuadre.getStyleClass().add("inspector-subheading");
        cuadreLbl.setWrapText(false);
        cuadreLbl.getStyleClass().add("inspector-cuadre");
        VBox cuadre = new VBox(4, tituloCuadre, cuadreLbl);
        cuadre.setMinWidth(0);

        Label tituloAcciones = new Label("Documento");
        tituloAcciones.getStyleClass().add("inspector-subheading");
        VBox doc = new VBox(4, tituloAcciones, new HBox(8, comprobante, evidencia));
        doc.setMinWidth(0);

        // FlowPane: las zonas bajan de línea cuando el ancho no alcanza en
        // lugar de comprimir el texto aColumnas ilegibles.
        FlowPane fila = new FlowPane(24, 10, seleccion, cuadre, doc);
        fila.setAlignment(Pos.TOP_LEFT);
        fila.getStyleClass().add("journal-inspector-row");
        insp.getChildren().add(fila);
        return insp;
    }

    private void actualizarInspector(Partida p) {
        boolean vacio = p == null;
        inspectorVacio.setVisible(vacio);
        inspectorVacio.setManaged(vacio);
        for (Label l : List.of(selFolio, selOrigen, selConcepto)) {
            l.setVisible(!vacio);
            l.setManaged(!vacio);
        }
        if (vacio) {
            selFolio.setText("");
            selOrigen.setText("");
            selConcepto.setText("");
            return;
        }
        selFolio.setText((p.getFolio() != null ? p.getFolio() : "Borrador (sin folio)")
                + "  ·  " + p.getEstado());
        selOrigen.setText("Tipo: " + p.getTipo().getEtiqueta()
                + "   Origen: " + (p.getOrigen() != null ? p.getOrigen().name() : "—"));
        selConcepto.setText(p.getConcepto());
        selConcepto.setTooltip(new Tooltip(p.getConcepto()));
    }

    // ------------------------------------------------------------------
    // Período, atajos, comprobante y exportación
    // ------------------------------------------------------------------

    private void actualizarPeriodoLbl() {
        LocalDate f = fecha.getValue();
        periodoLbl.getStyleClass().removeAll("status-pill", "warning-pill", "closed-pill");
        if (f == null) {
            periodoLbl.setText("Sin fecha");
            return;
        }
        try {
            Periodo p = context.periodos().exigirPeriodoParaFecha(f);
            EstadoPeriodo est = p.getEstado();
            periodoLbl.setText(MESES[p.getMes()] + " " + p.getFechaInicio().getYear()
                    + " · " + etiquetaEstadoPeriodo(est));
            periodoLbl.getStyleClass().add(switch (est) {
                case ABIERTO -> "status-pill";
                case EN_REVISION -> "warning-pill";
                case CERRADO -> "closed-pill";
            });
        } catch (DomainException ex) {
            periodoLbl.setText("Sin período · " + f);
            periodoLbl.getStyleClass().add("warning-pill");
        } catch (Exception ex) {
            periodoLbl.setText("Error de período");
            periodoLbl.getStyleClass().add("warning-pill");
        }
    }

    private static String etiquetaEstadoPeriodo(EstadoPeriodo est) {
        return switch (est) {
            case ABIERTO -> "Abierto";
            case EN_REVISION -> "En revisión";
            case CERRADO -> "Cerrado";
        };
    }

    private void atajosVista(KeyEvent e) {
        if (!e.isControlDown() || e.isAltDown() || e.isShiftDown()) {
            return;
        }
        if (e.getCode() == KeyCode.G) {
            guardarBorrador();
            e.consume();
        } else if (e.getCode() == KeyCode.ENTER) {
            contabilizarPartida();
            e.consume();
        }
    }

    private Partida partidaParaComprobante() {
        confirmarEdicionActiva();
        Partida sel = recientes.getSelectionModel().getSelectedItem();
        if (sel != null) {
            return sel;
        }
        if (partidaActualId != null) {
            Optional<Partida> encontrada = context.partidas().buscar(partidaActualId);
            if (encontrada.isPresent()) {
                Partida p = encontrada.get();
                if (sucia) {
                    try {
                        p.cargarDetalles(aDetalles());
                    } catch (Exception ignored) {
                    }
                }
                return p;
            }
        }
        if (!lineas.isEmpty() && fecha.getValue() != null) {
            try {
                Periodo per = context.periodos().exigirPeriodoParaFecha(fecha.getValue());
                String conceptoTxt = concepto.getText().trim();
                Partida borrador = Partida.borrador(tipo.getValue(), fecha.getValue(), per.getId(),
                        conceptoTxt.isEmpty() ? "(sin concepto)" : conceptoTxt,
                        soporte.getText().trim(), null, null);
                borrador.cargarDetalles(aDetalles());
                return borrador;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void mostrarComprobanteSeleccionada() {
        Partida p;
        try {
            p = partidaParaComprobante();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
            return;
        }
        if (p == null) {
            UiDialogs.error("No hay partida para comprobante. Cargue una de «Recientes» "
                    + "o capture líneas en el formulario.");
            return;
        }
        mostrarComprobante(p);
    }

    private void mostrarComprobante(Partida p) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Comprobante de asiento");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes()
                .add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));

        Node doc = comprobanteNode(p);
        ScrollPane scroll = new ScrollPane(doc);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(730);
        scroll.setPrefHeight(540);
        scroll.getStyleClass().add("paper-scroll");

        Button imprimir = new Button("Imprimir");
        imprimir.setOnAction(e -> imprimirComprobante(doc));
        Button pdf = new Button("Guardar PDF");
        pdf.setOnAction(e -> exportarComprobantePdf(p));
        HBox accionesDoc = new HBox(8, imprimir, pdf);
        accionesDoc.setPadding(new Insets(10, 0, 0, 0));
        accionesDoc.setAlignment(Pos.CENTER_LEFT);

        VBox contenido = new VBox(8, scroll, accionesDoc);
        contenido.setPadding(new Insets(6));
        dialog.getDialogPane().setContent(contenido);
        UiTheme.style(dialog);
        dialog.showAndWait();
    }

    private void imprimirComprobante(Node doc) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiDialogs.error("No hay ninguna impresora disponible.");
            return;
        }
        if (!job.showPrintDialog(getScene().getWindow())) {
            return;
        }
        if (job.printPage(doc)) {
            job.endJob();
        } else {
            UiDialogs.error("No se pudo imprimir el comprobante.");
        }
    }

    private void exportarComprobantePdf(Partida p) {
        try {
            String folio = p.getFolio() != null ? p.getFolio() : "(borrador)";
            Optional<Empresa> empresa = context.periodos().empresa();
            String subtitulo = empresa.map(Empresa::getRazonSocial).orElse("Colibrí")
                    + " · " + folio + " · " + p.getFechaContable() + " · " + p.getEstado();
            List<String> encabezados = List.of("Cuenta", "Denominación", "Auxiliar",
                    "Centro", "Debe", "Haber");
            List<List<String>> filas = new ArrayList<>();
            BigDecimal td = BigDecimal.ZERO;
            BigDecimal th = BigDecimal.ZERO;
            for (PartidaDetalle d : p.getDetalles()) {
                td = td.add(d.getDebe());
                th = th.add(d.getHaber());
                filas.add(List.of(d.getCodigoCuenta(),
                        nombrePorCuenta.getOrDefault(d.getCodigoCuenta(), ""),
                        d.getAuxiliar() == null ? "" : d.getAuxiliar(),
                        d.getCentroCosto() == null ? "" : d.getCentroCosto(),
                        MONEDA.format(d.getDebe()), MONEDA.format(d.getHaber())));
            }
            List<String> total = new ArrayList<>(java.util.Collections.nCopies(encabezados.size(), ""));
            total.set(0, "TOTALES");
            total.set(4, MONEDA.format(td));
            total.set(5, MONEDA.format(th));
            filas.add(total);

            byte[] contenido = context.reports().exportarPdf(
                    "Comprobante de asiento " + folio, subtitulo, encabezados, filas);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar comprobante en PDF");
            chooser.setInitialFileName("comprobante-" + folio.replaceAll("[^A-Za-z0-9_-]", "") + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            var destino = chooser.showSaveDialog(getScene().getWindow());
            if (destino == null) {
                return;
            }
            Files.write(destino.toPath(), contenido);
            UiDialogs.info("PDF exportado", "Archivo guardado en " + destino);
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private Node comprobanteNode(Partida p) {
        VBox doc = new VBox(10);
        doc.setPadding(new Insets(26));
        doc.setPrefWidth(720);
        doc.setMinWidth(720);
        doc.getStyleClass().add("paper-doc");

        Optional<Empresa> empresa = context.periodos().empresa();
        empresa.ifPresent(e -> {
            Label rs = new Label(e.getRazonSocial());
            rs.getStyleClass().add("paper-title");
            Label datos = new Label("NIT: " + nvl(e.getNit()) + "   NRC: " + nvl(e.getNrc())
                    + "   Moneda: " + nvl(e.getMoneda()));
            datos.getStyleClass().add("paper-meta");
            doc.getChildren().addAll(rs, datos);
        });

        Label titulo = new Label("COMPROBANTE DE ASIENTO — "
                + p.getTipo().getEtiqueta().toUpperCase());
        titulo.getStyleClass().add("paper-heading");
        doc.getChildren().add(titulo);
        doc.getChildren().add(new Label("Folio: " + nvl(p.getFolio(), "(borrador)")
                + "    Fecha: " + p.getFechaContable()
                + "    Estado: " + p.getEstado()
                + "    Origen: " + p.getOrigen()));
        if (p.getDocumentoSoporte() != null && !p.getDocumentoSoporte().isBlank()) {
            doc.getChildren().add(new Label("Documento de soporte: " + p.getDocumentoSoporte()));
        }
        doc.getChildren().add(new Label("Concepto: " + p.getConcepto()));

        GridPane tablaDoc = new GridPane();
        tablaDoc.setHgap(10);
        tablaDoc.setVgap(3);
        tablaDoc.getColumnConstraints().addAll(
                restriccion(34), restriccion(24), restriccion(12), restriccion(15), restriccion(15));
        encabezadoDoc(tablaDoc, 0, "Cuenta");
        encabezadoDoc(tablaDoc, 1, "Auxiliar");
        encabezadoDoc(tablaDoc, 2, "Centro");
        encabezadoDoc(tablaDoc, 3, "Debe");
        encabezadoDoc(tablaDoc, 4, "Haber");
        tablaDoc.add(new Label(""), 0, 1, 5, 1);

        int fila = 2;
        BigDecimal td = BigDecimal.ZERO;
        BigDecimal th = BigDecimal.ZERO;
        for (PartidaDetalle d : p.getDetalles()) {
            String cuenta = d.getCodigoCuenta()
                    + (nombrePorCuenta.containsKey(d.getCodigoCuenta())
                            ? " " + nombrePorCuenta.get(d.getCodigoCuenta()) : "");
            celdaDoc(tablaDoc, 0, fila, cuenta);
            celdaDoc(tablaDoc, 1, fila, nvl(d.getAuxiliar()));
            celdaDoc(tablaDoc, 2, fila, nvl(d.getCentroCosto()));
            celdaDoc(tablaDoc, 3, fila, MONEDA.format(d.getDebe()));
            celdaDoc(tablaDoc, 4, fila, MONEDA.format(d.getHaber()));
            td = td.add(d.getDebe());
            th = th.add(d.getHaber());
            fila++;
        }
        Label t1 = new Label("TOTALES");
        t1.getStyleClass().add("paper-strong");
        tablaDoc.add(t1, 0, fila);
        tablaDoc.add(new Label(""), 1, fila);
        tablaDoc.add(new Label(""), 2, fila);
        Label tdL = new Label(MONEDA.format(td));
        tdL.getStyleClass().add("paper-strong");
        Label thL = new Label(MONEDA.format(th));
        thL.getStyleClass().add("paper-strong");
        tablaDoc.add(tdL, 3, fila);
        tablaDoc.add(thL, 4, fila);
        doc.getChildren().add(tablaDoc);

        HBox firmas = new HBox(40);
        firmas.getChildren().addAll(firmaDoc("Elaboró"), firmaDoc("Revisó"), firmaDoc("Contabilizó"));
        firmas.setPadding(new Insets(30, 0, 0, 0));
        doc.getChildren().add(firmas);
        return doc;
    }

    private static ColumnConstraints restriccion(double porcentaje) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(porcentaje);
        return cc;
    }

    private static void encabezadoDoc(GridPane grid, int col, String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("paper-strong");
        grid.add(l, col, 0);
    }

    private static void celdaDoc(GridPane grid, int col, int fila, String texto) {
        Label l = new Label(texto == null ? "" : texto);
        l.setWrapText(true);
        grid.add(l, col, fila);
    }

    private static Node firmaDoc(String rol) {
        javafx.scene.layout.Region linea = new javafx.scene.layout.Region();
        linea.setMinHeight(18);
        linea.getStyleClass().add("paper-line");
        VBox v = new VBox(4, new Label(rol), linea);
        v.setMinWidth(170);
        return v;
    }

    private static String nvl(String s) {
        return nvl(s, "—");
    }

    private static String nvl(String s, String defecto) {
        return s == null || s.isBlank() ? defecto : s;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal numeroONull(String texto) {
        if (texto == null || texto.isBlank()) {
            return BigDecimal.ZERO;
        }
        // Eliminar separadores de miles (coma) para tolerar el formato 1,000.00
        String limpio = texto.trim().replace(",", "");
        try {
            return new BigDecimal(limpio);
        } catch (Exception e) {
            return null;
        }
    }

    private javafx.scene.Node cadenaEvidencia(PartidaService.CadenaEvidencia cadena, Partida partida) {
        VBox contenido = new VBox(8);
        contenido.setPadding(new Insets(12));
        contenido.getChildren().addAll(
                filaEvidencia("Documento", cadena.documento()),
                filaEvidencia("Evento contable", cadena.evento()),
                filaEvidencia("Folio", cadena.folio()),
                filaEvidencia("Bitácora", cadena.bitacora()),
                filaEvidencia("Referencia", cadena.referencia() == null
                        || cadena.referencia().isBlank() ? "—" : cadena.referencia()));

        TableView<PartidaDetalle> detalles = new TableView<>(
                FXCollections.observableArrayList(partida.getDetalles()));
        TableColumn<PartidaDetalle, String> dCuenta = new TableColumn<>("Cuenta");
        dCuenta.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoCuenta()));
        TableColumn<PartidaDetalle, String> dDebe = new TableColumn<>("Debe");
        dDebe.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDebe().toPlainString()));
        TableColumn<PartidaDetalle, String> dHaber = new TableColumn<>("Haber");
        dHaber.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHaber().toPlainString()));
        detalles.getColumns().addAll(dCuenta, dDebe, dHaber);
        detalles.setPrefSize(460, 180);
        contenido.getChildren().add(new Label("Líneas del asiento"));
        contenido.getChildren().add(UiTables.conBusqueda(detalles, "líneas de evidencia", detalle ->
                detalle.getCodigoCuenta() + " " + detalle.getDebe() + " " + detalle.getHaber()));
        return contenido;
    }

    private static javafx.scene.Node filaEvidencia(String etiqueta, String valor) {
        Hyperlink enlace = new Hyperlink(valor == null || valor.isBlank() ? "—" : valor);
        enlace.setDisable(true);
        VBox fila = new VBox(0,
                new Label(etiqueta), enlace);
        return fila;
    }

    private void exportarDiario(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            UiDialogs.error("Seleccione un rango de fechas válido para el diario.");
            return;
        }
        try {
            List<String> encabezados = List.of("Fecha", "N.º de partida", "Código / referencia",
                    "Cuenta", "Concepto", "Debe (USD)", "Haber (USD)");
            List<List<String>> filas = new ArrayList<>();
            BigDecimal totalDebe = BigDecimal.ZERO;
            BigDecimal totalHaber = BigDecimal.ZERO;
            for (var linea : context.libros().diario(desde, hasta)) {
                totalDebe = totalDebe.add(linea.debe());
                totalHaber = totalHaber.add(linea.haber());
                filas.add(List.of(linea.fecha().toString(),
                        linea.folio() == null ? "" : linea.folio(),
                        linea.referencia() == null ? "" : linea.referencia(),
                        linea.cuenta() + " · " + linea.nombreCuenta(),
                        linea.concepto() == null ? "" : linea.concepto(),
                        linea.debe().toPlainString(), linea.haber().toPlainString()));
            }
            List<String> total = new ArrayList<>(java.util.Collections.nCopies(encabezados.size(), ""));
            total.set(0, "TOTALES");
            total.set(5, totalDebe.toPlainString());
            total.set(6, totalHaber.toPlainString());
            filas.add(total);

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exportar libro diario");
            chooser.setInitialFileName("libro-diario-" + desde + "-a-" + hasta + ".xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Libro de Excel", "*.xlsx"));
            var destino = chooser.showSaveDialog(getScene().getWindow());
            if (destino == null) {
                return;
            }
            String entidad = context.periodos().empresa()
                    .map(e -> e.getRazonSocial()).orElse("Colibrí");
            byte[] contenido = context.reports().exportarExcel("Libro diario",
                    entidad + " · " + desde + " a " + hasta, encabezados, filas);
            Files.write(destino.toPath(), contenido);
            UiDialogs.info("Diario exportado", "Archivo guardado en " + destino);
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private final class LineaEditable {
        final SimpleStringProperty codigo = new SimpleStringProperty();
        final SimpleStringProperty auxiliar = new SimpleStringProperty();
        final SimpleStringProperty centro = new SimpleStringProperty();
        final SimpleStringProperty debe = new SimpleStringProperty();
        final SimpleStringProperty haber = new SimpleStringProperty();
        final BooleanProperty invalida = new SimpleBooleanProperty();

        LineaEditable(String codigo, String auxiliar, String centro, String debe, String haber) {
            this.codigo.set(codigo == null ? "" : codigo);
            this.auxiliar.set(auxiliar == null ? "" : auxiliar);
            this.centro.set(centro == null ? "" : centro);
            this.debe.set(debe == null ? "" : debe);
            this.haber.set(haber == null ? "" : haber);
            Runnable reevaluar = () -> {
                invalida.set(motivoInvalidez() != null);
                marcarSucia();
            };
            this.codigo.addListener((o, a, b) -> reevaluar.run());
            this.auxiliar.addListener((o, a, b) -> reevaluar.run());
            this.centro.addListener((o, a, b) -> reevaluar.run());
            this.debe.addListener((o, a, b) -> reevaluar.run());
            this.haber.addListener((o, a, b) -> reevaluar.run());
            reevaluar.run();
        }

        String motivoInvalidez() {
            if (codigo.get() == null || codigo.get().isBlank()) {
                return "falta la cuenta";
            }
            BigDecimal d = numeroONull(debe.get());
            BigDecimal h = numeroONull(haber.get());
            if (d == null || h == null) {
                return "importe no numérico";
            }
            if (d.signum() < 0 || h.signum() < 0) {
                return "importe negativo";
            }
            boolean conDebe = d.signum() > 0;
            boolean conHaber = h.signum() > 0;
            if (conDebe && conHaber) {
                return "tiene Debe y Haber a la vez";
            }
            if (!conDebe && !conHaber) {
                return "no tiene importe en Debe ni en Haber";
            }
            return null;
        }

        String getCodigo() {
            return codigo.get();
        }

        void setCodigo(String valor) {
            codigo.set(valor == null ? "" : valor);
        }

        String getAuxiliar() {
            return auxiliar.get();
        }

        void setAuxiliar(String valor) {
            auxiliar.set(valor == null ? "" : valor);
        }

        String getCentro() {
            return centro.get();
        }

        void setCentro(String valor) {
            centro.set(valor == null ? "" : valor);
        }

        String getDebe() {
            return debe.get();
        }

        void setDebe(String valor) {
            debe.set(valor == null ? "" : valor);
        }

        String getHaber() {
            return haber.get();
        }

        void setHaber(String valor) {
            haber.set(valor == null ? "" : valor);
        }

        BooleanProperty invalidaProperty() {
            return invalida;
        }
    }

    private record OpcionAuxiliar(String codigo, String tipo, String detalle) {
    }
}
