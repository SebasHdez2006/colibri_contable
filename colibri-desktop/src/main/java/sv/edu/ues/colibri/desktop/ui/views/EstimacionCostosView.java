package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.costos.CosteoGerencialService;
import sv.edu.ues.colibri.application.costos.EstimacionCostosService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCampos;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiFormatos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Pantalla gerencial para estimar costos, contrastar métodos y comparar
 * presupuestos contra el costo real registrado en las órdenes.
 */
public final class EstimacionCostosView extends VBox {

    private static final DecimalFormat FORMATO_NUMERO = formatoNumero("#,##0.00");

    private final AppContext context;

    /**
     * Crea la vista de estimación y análisis de costos.
     *
     * @param context contexto de aplicación con acceso a los registros de costos
     */
    public EstimacionCostosView(AppContext context) {
        this.context = Objects.requireNonNull(context, "El contexto de aplicación es obligatorio.");
        EstimacionCostosService motor = new EstimacionCostosService();
        ComboBox<EstimacionCostosService.Metodo> metodo = new ComboBox<>(
                FXCollections.observableArrayList(EstimacionCostosService.Metodo.values()));
        metodo.setValue(EstimacionCostosService.Metodo.TRES_PUNTOS);
        UiCatalogos.ayuda(metodo, "Técnica de estimación; las etiquetas de los parámetros cambian según el método.");
        TextField parametro1 = new TextField();
        TextField parametro2 = new TextField();
        TextField parametro3 = new TextField();
        TextField parametro4 = new TextField();
        UiCatalogos.ayuda(parametro1, "Parámetro del método; la etiqueta cambia con la técnica. "
                + "CFP=casos de uso, KLOC=miles de líneas, Ck=productividad.");
        UiCatalogos.ayuda(parametro2, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro3, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro4, "Parámetro del método. En Juicio de expertos use 3 pesos "
                + "separados por coma.");
        VBox parametros = new VBox(8);
        configurarParametros(metodo.getValue(), parametros, parametro1, parametro2, parametro3, parametro4);
        metodo.valueProperty().addListener((observable, anterior, actual) ->
                configurarParametros(actual, parametros, parametro1, parametro2, parametro3, parametro4));
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa en USD.");
        TextField cif = new TextField("20");
        cif.setPromptText("%");
        UiCatalogos.ayuda(cif, "Porcentaje de costos indirectos de fabricación sobre la mano de obra.");
        TextField margen = new TextField("30");
        margen.setPromptText("%");
        UiCatalogos.ayuda(margen, "Margen de utilidad objetivo sobre el costo total, en %.");
        Label resultado = mensajeResultado("Seleccione una técnica y complete sus insumos específicos.");
        Button calcular = new Button("Calcular costo y precio unitario");
        calcular.setOnAction(evento -> {
            try {
                BigDecimal p1 = valor(parametro1);
                BigDecimal p2 = valor(parametro2);
                BigDecimal p3 = valor(parametro3);
                BigDecimal horas = switch (metodo.getValue()) {
                    case JUICIO_EXPERTOS -> motor.juicioExpertos(p1, p2, p3, pesos(parametro4.getText()));
                    case ANALOGIA -> motor.analogia(p1, p2, p3, valor(parametro4));
                    case PUNTOS_FUNCION -> motor.puntosFuncion(p1, p2);
                    case COSMIC -> motor.cosmic(p1, p2);
                    case COCOMO -> motor.cocomo(p1, p2, p3, valor(parametro4));
                    case DESGASTE_TRABAJO -> motor.desgasteTrabajo(p1, p2);
                    case LINEAS_CODIGO -> motor.lineasCodigo(p1, p2);
                    case TRES_PUNTOS -> motor.tresPuntos(p1, p2, p3);
                    case SLIM_PUTNAM -> motor.slimPutnam(p1, p2, p3);
                };
                var r = motor.costearEsfuerzo(metodo.getValue(), horas,
                        UiCampos.decimal(tarifa.getText(), "tarifa"),
                        UiCampos.decimal(cif.getText(), "tasa de CIF"),
                        UiCampos.decimal(margen.getText(), "margen"));
                resultado.setText("Método: %s%nEsfuerzo: %s h · Directo: %s · CIF: %s%n"
                        + "Costo total: %s · Precio unitario: %s · Margen: %s%n%s"
                        .formatted(r.metodo(), numero(r.esfuerzoHoras()), dinero(r.costoDirecto()),
                                dinero(r.cif()), dinero(r.costoTotal()), dinero(r.precioUnitario()),
                                dinero(r.margen()), r.recomendacion()));
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        TextFlow politicas = new TextFlow();
        politicas.setLineSpacing(6);
        politicas.getStyleClass().add("polizas-texto");
        agregarPolitica(politicas, "INVENTARIO Y VALORIZACIÓN",
                "Las licencias y productos digitales se liberan en lotes y se valoran con PEPS "
                + "(primero en entrar, primero en salir), que es la política contable oficial de "
                + "Colibrí. El costo unitario se fija al liberar el lote y cada salida toma "
                + "automáticamente la licencia disponible más antigua; promedio y UEPS (último en "
                + "entrar, primero en salir) se conservan únicamente como análisis comparativo y no "
                + "generan asientos.");
        agregarPolitica(politicas, "MANO DE OBRA Y COSTOS INDIRECTOS",
                "La mano de obra directa (MOD) se registra por horas y tarifa en la orden de "
                + "trabajo o en el plan facturado. Los costos indirectos de fabricación (CIF) se "
                + "presupuestan por centro de costo, se aplican a las órdenes según la base real "
                + "consumida y la variación entre lo real y lo aplicado se cierra contra costo de "
                + "proyectos.");
        agregarPolitica(politicas, "ACUMULACIÓN POR TIPO DE SERVICIO",
                "Las órdenes de trabajo (software a la medida) acumulan MOD, recursos directos y "
                + "CIF hasta su reconocimiento como costo. Los planes SaaS acumulan costos por "
                + "proceso y por usuario-mes. Los productos de licencia usan el costo fijo del "
                + "lote. Los costos estándar y ABC quedan reservados al análisis gerencial y nunca "
                + "reemplazan al costo real contabilizado.");
        agregarPolitica(politicas, "DOCUMENTACIÓN Y TRIBUTOS",
                "Toda venta debe documentar canal, plan, precio neto, IVA y costo reconocido; los "
                + "documentos electrónicos (CCF, consumidor final y notas de débito/crédito) se "
                + "emiten desde el módulo de registros y alimentan los libros de IVA. Las "
                + "retenciones de renta e IVA aplican según la parametrización fiscal vigente.");
        agregarPolitica(politicas, "TRAZABILIDAD",
                "Cada estimación, cédula, aplicación de CIF y ajuste de cierre queda asociada a un "
                + "período contable abierto, un centro de costo y un folio de partida, de modo que "
                + "cualquier cifra pueda reconstruirse hasta su documento soporte.");
        VBox politicasCard = ResponsiveLayouts.sectionCard(
                "Políticas de costos y ventas", politicas);
        Label titulo = new Label("Estimación y políticas");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label("Técnicas de estimación de esfuerzo, costo total y precio unitario.");
        subtitulo.getStyleClass().add("view-subtitle");
        VBox estimacion = ResponsiveLayouts.sectionCard(
                "Estimación de costos de software y planeación de distribución/venta",
                "Calcule el esfuerzo, el costo total y un precio unitario recomendado.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Método", metodo),
                        ResponsiveLayouts.field("Tarifa/hora", tarifa), ResponsiveLayouts.field("CIF %", cif),
                        ResponsiveLayouts.field("Margen %", margen)),
                parametros, calcular, resultado);
        getChildren().addAll(titulo, subtitulo, estimacion, comparacionPromedioHistoricos(motor),
                calculadoraGerencial(), comparacionEstimadoReal(),
                politicasCard,
                catalogoInstitucional());
    }

    /**
     * Catálogo documental del Anexo M del informe técnico: las 122 políticas,
     * procesos y procedimientos de la empresa, con filtro por texto.
     */
    private VBox catalogoInstitucional() {
        List<InstrumentosInstitucionales.Instrumento> instrumentos =
                InstrumentosInstitucionales.anexoM();
        long politicas = instrumentos.stream()
                .filter(i -> "Política".equals(i.tipo())).count();
        long procesos = instrumentos.stream()
                .filter(i -> "Proceso".equals(i.tipo())).count();
        long procedimientos = instrumentos.size() - politicas - procesos;
        Label resumen = new Label(instrumentos.size() + " instrumentos · "
                + politicas + " políticas · " + procesos + " procesos · "
                + procedimientos + " procedimientos. Fuente: informe técnico, Anexo M.");
        resumen.getStyleClass().add("section-note");
        resumen.setWrapText(true);

        TextField buscar = new TextField();
        buscar.setPromptText("Filtrar por nombre, tipo o descripción");
        buscar.setMaxWidth(Double.MAX_VALUE);

        ObservableList<InstrumentosInstitucionales.Instrumento> visibles =
                FXCollections.observableArrayList(instrumentos);
        TableView<InstrumentosInstitucionales.Instrumento> tabla =
                new TableView<>(visibles);
        tabla.setPlaceholder(new Label("Sin instrumentos para el filtro."));
        tabla.setMinHeight(280);
        tabla.setPrefHeight(320);
        // Sin la columna de descripción las filas son más bajas; el nombre
        // envuelve en dos líneas cuando el ancho no alcanza para una sola.
        tabla.setFixedCellSize(52);

        // Panel de detalle bajo la tabla: se llena al pulsar «Ver» en una fila.
        VBox detalleInstrumento = new VBox(8);
        detalleInstrumento.getStyleClass().add("detalle-instrumento");
        detalleInstrumento.setVisible(false);
        detalleInstrumento.setManaged(false);

        TableColumn<InstrumentosInstitucionales.Instrumento, String> cNum =
                new TableColumn<>("N.º");
        cNum.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().numero())));
        cNum.setPrefWidth(56);
        cNum.setMinWidth(48);
        cNum.setSortable(false);

        TableColumn<InstrumentosInstitucionales.Instrumento, String> cNom =
                new TableColumn<>("Instrumento");
        cNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        cNom.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Label texto = new Label();

            {
                texto.setWrapText(true);
                texto.prefWidthProperty().bind(col.widthProperty().subtract(14));
                texto.minWidthProperty().bind(col.widthProperty().subtract(14));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                texto.setText(item);
                setGraphic(texto);
                setText(null);
            }
        });
        cNom.setMinWidth(320);
        cNom.setSortable(false);

        TableColumn<InstrumentosInstitucionales.Instrumento, String> cTip =
                new TableColumn<>("Tipo");
        cTip.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tipo()));
        cTip.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Label chip = new Label();

            {
                chip.getStyleClass().add("instrumento-tipo");
            }

            @Override
            protected void updateItem(String tipo, boolean vacio) {
                super.updateItem(tipo, vacio);
                if (vacio || tipo == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                chip.setText(tipo);
                String clase = switch (tipo) {
                    case "Política" -> "politica";
                    case "Proceso" -> "proceso";
                    default -> "procedimiento";
                };
                chip.getStyleClass().setAll("instrumento-tipo",
                        "instrumento-" + clase);
                setGraphic(chip);
            }
        });
        cTip.setPrefWidth(120);
        cTip.setMinWidth(100);
        cTip.setSortable(false);

        TableColumn<InstrumentosInstitucionales.Instrumento, Void> cDetalle =
                new TableColumn<>("Descripción");
        cDetalle.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button boton = new Button("Ver");

            {
                boton.setTooltip(new javafx.scene.control.Tooltip(
                        "Ver la descripción completa del instrumento"));
                boton.setOnAction(evento -> {
                    var fila = getTableRow();
                    if (fila != null && fila.getItem() != null) {
                        mostrarDescripcion(fila.getItem(), detalleInstrumento);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : boton);
            }
        });
        cDetalle.setPrefWidth(96);
        cDetalle.setMinWidth(88);
        cDetalle.setSortable(false);
        cDetalle.setResizable(false);

        tabla.getColumns().addAll(cNum, cNom, cTip, cDetalle);
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        // La columna del nombre absorbe el ancho sobrante de la tabla para
        // que los instrumentos largos se lean completos; el resto de
        // columnas conservan su ancho fijo.
        cNom.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(320.0, tabla.getWidth() - 300.0),
                tabla.widthProperty()));

        buscar.textProperty().addListener((obs, anterior, nuevo) -> {
            String consulta = normalizar(nuevo);
            visibles.setAll(instrumentos.stream()
                    .filter(i -> consulta.isEmpty()
                            || normalizar(i.nombre() + " " + i.tipo() + " " + i.seccion()
                                    + " " + i.descripcion())
                                    .contains(consulta))
                    .toList());
        });

        return ResponsiveLayouts.sectionCard(
                "Políticas, procesos y procedimientos",
                "Catálogo de referencia del marco institucional: lineamientos internos "
                        + "de la empresa, no movimientos contables.",
                resumen, buscar, tabla, detalleInstrumento);
    }

    /**
     * Muestra la descripción completa del instrumento elegido en el panel
     * inferior de la tabla; el botón «Ocultar» lo repliega.
     */
    private void mostrarDescripcion(InstrumentosInstitucionales.Instrumento instrumento,
            VBox detalle) {
        detalle.getChildren().clear();
        Label meta = new Label("N.º " + instrumento.numero() + " · " + instrumento.tipo()
                + " · " + instrumento.seccion());
        meta.getStyleClass().add("section-note");
        Label nombre = new Label(instrumento.nombre());
        nombre.setWrapText(true);
        nombre.getStyleClass().add("poliza-heading");
        Text cuerpo = new Text(instrumento.descripcion());
        cuerpo.getStyleClass().add("poliza-body");
        TextFlow descripcion = new TextFlow(cuerpo);
        descripcion.setLineSpacing(6);
        descripcion.getStyleClass().add("poliza-texto");
        Button cerrar = new Button("Ocultar descripción");
        cerrar.setOnAction(evento -> {
            detalle.setVisible(false);
            detalle.setManaged(false);
        });
        detalle.getChildren().addAll(meta, nombre, descripcion, cerrar);
        detalle.setVisible(true);
        detalle.setManaged(true);
    }

    /**
     * Añade un bloque "TÍTULO + párrafo" al flujo de políticas; el salto de
     * línea dentro del texto mantiene cada tema separado y legible.
     */
    private static void agregarPolitica(TextFlow flujo, String titulo, String cuerpo) {
        Text encabezado = new Text(titulo + "\n");
        encabezado.getStyleClass().add("poliza-heading");
        Text parrafo = new Text(cuerpo + "\n\n");
        parrafo.getStyleClass().add("poliza-body");
        flujo.getChildren().addAll(encabezado, parrafo);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).trim();
    }

    private static BigDecimal valor(TextField campo) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El valor debe ser numérico: «" + texto + "».");
        }
    }

    private static List<BigDecimal> pesos(String texto) {
        List<BigDecimal> lista = new ArrayList<>();
        if (texto != null) {
            for (String parte : texto.split("[,;\\s]+")) {
                if (!parte.isBlank()) {
                    try {
                        lista.add(new BigDecimal(parte.trim()));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(
                                "Los pesos deben ser numéricos: «" + parte.trim() + "».");
                    }
                }
            }
        }
        return lista;
    }

    /** Parámetro de entrada de una técnica de estimación. */
    private record ParametroMetodo(String etiqueta, String valor, boolean esPesos) {
    }

    /**
     * Insumos y valores sugeridos de cada método, expresados en la unidad que
     * su técnica requiere (horas, puntos de función, CFP, KLOC o tamaño). Los
     * valores describen un proyecto de referencia coherente para que la
     * comparación promedie estimaciones de magnitudes comparables.
     */
    private static List<ParametroMetodo> parametrosMetodo(EstimacionCostosService.Metodo metodo) {
        return switch (metodo) {
            case JUICIO_EXPERTOS -> List.of(
                    new ParametroMetodo("Optimista (h)", "200", false),
                    new ParametroMetodo("Probable (h)", "250", false),
                    new ParametroMetodo("Pesimista (h)", "350", false),
                    new ParametroMetodo("Pesos (o,p,pe)", "1,1,1", true));
            case ANALOGIA -> List.of(
                    new ParametroMetodo("Esfuerzo comparable (h)", "250", false),
                    new ParametroMetodo("Tamaño comparable", "100", false),
                    new ParametroMetodo("Tamaño objetivo", "100", false),
                    new ParametroMetodo("Factor de ajuste", "1", false));
            case PUNTOS_FUNCION -> List.of(
                    new ParametroMetodo("Puntos ajustados", "150", false),
                    new ParametroMetodo("Horas por punto", "5", false));
            case COSMIC -> List.of(
                    new ParametroMetodo("CFP", "400", false),
                    new ParametroMetodo("Horas por CFP", "2", false));
            case COCOMO -> List.of(
                    new ParametroMetodo("KLOC", "5", false),
                    new ParametroMetodo("Σ factores de escala", "0", false),
                    new ParametroMetodo("Π multiplicadores de esfuerzo", "1", false),
                    new ParametroMetodo("Horas por mes", "160", false));
            case DESGASTE_TRABAJO -> List.of(
                    new ParametroMetodo("Horas productivas", "250", false),
                    new ParametroMetodo("Desgaste %", "15", false));
            case LINEAS_CODIGO -> List.of(
                    new ParametroMetodo("KLOC", "5", false),
                    new ParametroMetodo("KLOC por persona-mes", "0.5", false));
            case TRES_PUNTOS -> List.of(
                    new ParametroMetodo("Optimista (h)", "200", false),
                    new ParametroMetodo("Probable (h)", "250", false),
                    new ParametroMetodo("Pesimista (h)", "350", false));
            case SLIM_PUTNAM -> List.of(
                    new ParametroMetodo("Tamaño (KLOC)", "25", false),
                    new ParametroMetodo("Productividad Ck", "12", false),
                    new ParametroMetodo("Duración (años)", "2", false));
        };
    }

    private static void configurarParametros(EstimacionCostosService.Metodo metodo, VBox contenedor,
                                             TextField p1, TextField p2, TextField p3, TextField p4) {
        List<ParametroMetodo> spec = parametrosMetodo(metodo);
        TextField[] campos = {p1, p2, p3, p4};
        for (int i = 0; i < campos.length; i++) {
            campos[i].setText(i < spec.size() ? spec.get(i).valor() : "");
        }
        contenedor.getChildren().clear();
        List<Node> fila = new ArrayList<>();
        for (int i = 0; i < spec.size(); i++) {
            fila.add(ResponsiveLayouts.field(spec.get(i).etiqueta(), campos[i]));
        }
        contenedor.getChildren().add(ResponsiveLayouts.row(fila.toArray(new Node[0])));
    }

    private VBox calculadoraGerencial() {
        CosteoGerencialService motor = new CosteoGerencialService();
        TextField materiales = new TextField("400");
        materiales.setPromptText("USD");
        UiCatalogos.ayuda(materiales, "Materiales y recursos directos consumidos, en USD.");
        TextField mod = new TextField("600");
        mod.setPromptText("USD");
        UiCatalogos.ayuda(mod, "MOD: mano de obra directa (horas × tarifa), en USD.");
        TextField cifEstandar = new TextField("200");
        cifEstandar.setPromptText("USD");
        UiCatalogos.ayuda(cifEstandar, "Costos indirectos de fabricación presupuestados, en USD.");
        TextField real = new TextField("1250");
        real.setPromptText("USD");
        UiCatalogos.ayuda(real, "Costo real total registrado, en USD; sirve para calcular la variación.");
        TextField unidades = new TextField("100");
        unidades.setPromptText("Unidades");
        UiCatalogos.ayuda(unidades, "Cantidad de unidades producidas o vendidas.");
        TextField calidad = new TextField("80");
        calidad.setPromptText("USD");
        UiCatalogos.ayuda(calidad, "Costo de la actividad de calidad para el cálculo ABC.");
        TextField infraestructura = new TextField("120");
        infraestructura.setPromptText("USD");
        UiCatalogos.ayuda(infraestructura, "Costo de la actividad de infraestructura para el cálculo ABC.");
        Label salida = mensajeResultado("Ingrese los importes para comparar el costo por método.");
        Button calcular = new Button("Comparar métodos de costeo");
        calcular.setOnAction(evento -> {
            try {
                BigDecimal mat = UiCampos.decimal(materiales.getText(), "materiales");
                BigDecimal manoObra = UiCampos.decimal(mod.getText(), "MOD");
                int cantidad = UiCampos.entero(unidades.getText(), "unidades");
                var directo = motor.calcularDirecto(mat, manoObra, cantidad);
                var estandar = motor.analizarEstandar(mat, manoObra,
                        UiCampos.decimal(cifEstandar.getText(), "CIF estándar"),
                        UiCampos.decimal(real.getText(), "costo real"), cantidad);
                var abc = motor.calcularAbc(mat, manoObra, java.util.List.of(
                        new CosteoGerencialService.ActividadAbc("Calidad",
                                UiCampos.decimal(calidad.getText(), "actividad Calidad")),
                        new CosteoGerencialService.ActividadAbc("Infraestructura",
                                UiCampos.decimal(infraestructura.getText(), "actividad Infraestructura"))),
                        cantidad);
                salida.setText("Directo: %s (%s/unidad) · Estándar: %s (var. %s)%n"
                        + "ABC: CIF %s · Total %s (%s/unidad)"
                        .formatted(dinero(directo.total()), dinero(directo.unitario()),
                                dinero(estandar.totalEstandar()), dinero(estandar.variacion()),
                                dinero(abc.cifPorActividades()), dinero(abc.total()),
                                dinero(abc.unitario())));
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        return ResponsiveLayouts.sectionCard("Costos directos, estándar y basado en actividades (ABC)",
                "Compare los métodos antes de fijar el precio.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Materiales directos", materiales),
                        ResponsiveLayouts.field("MOD", mod), ResponsiveLayouts.field("CIF estándar", cifEstandar),
                        ResponsiveLayouts.field("Costo real", real), ResponsiveLayouts.field("Unidades", unidades)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("ABC · Calidad", calidad),
                        ResponsiveLayouts.field("ABC · Infraestructura", infraestructura), calcular), salida);
    }

    private VBox comparacionEstimadoReal() {
        TextField orden = new TextField("OS2026001");
        orden.setPromptText("Ej. OS2026001");
        UiCatalogos.ayuda(orden, "Código de la orden de servicio a comparar.");
        Label salida = mensajeResultado("Consulte una orden para comparar el presupuesto contra el costo real acumulado.");
        Button comparar = new Button("Comparar estimado y real");
        comparar.setOnAction(evento -> {
            try {
                var r = context.inventario().analizarDesviacionOrden(orden.getText().trim());
                salida.setText("Orden %s · Estimado %s · Real %s%n"
                        + "Variación %s (%s%%) · Índice %s%% · Ahorro %s · Sobrecosto %s%n"
                        + "Utilidad esperada %s · Utilidad real %s · Rentabilidad %s%%%n%s"
                        .formatted(r.codigo(), dinero(r.costoEstimado()), dinero(r.costoReal()),
                                dinero(r.variacionAbsoluta()), numero(r.variacionPorcentual()),
                                numero(r.indiceEjecucion()), dinero(r.ahorroPresupuestario()),
                                dinero(r.sobrecosto()), dinero(r.utilidadEsperada()),
                                dinero(r.utilidadReal()), numero(r.rentabilidadSobreVenta()),
                                r.diagnostico()));
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        return ResponsiveLayouts.sectionCard("Comparación de costos estimados y reales",
                "La estimación toma el presupuesto de la orden y la compara con el costo real registrado en las partidas.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Orden", orden), comparar), salida);
    }

    private VBox comparacionPromedioHistoricos(EstimacionCostosService motor) {
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa en USD.");
        TextField cifPct = new TextField("20");
        cifPct.setPromptText("%");
        UiCatalogos.ayuda(cifPct, "Porcentaje de costos indirectos de fabricación sobre la mano de obra.");
        TextField margenPct = new TextField("30");
        margenPct.setPromptText("%");
        UiCatalogos.ayuda(margenPct, "Margen de utilidad objetivo sobre el costo total, en %.");

        // Un apartado por método: cada técnica recibe sus propios insumos en
        // la unidad que le corresponde (horas, puntos de función, CFP, KLOC o
        // tamaño), en lugar de forzar una misma base a métodos distintos.
        List<PanelMetodo> paneles = new ArrayList<>();
        FlowPane metodos = new FlowPane(14, 14);
        metodos.setAlignment(Pos.TOP_LEFT);
        metodos.setPrefWrapLength(780);
        metodos.setMaxWidth(Double.MAX_VALUE);
        metodos.widthProperty().addListener((observable, anterior, actual) -> {
            if (actual.doubleValue() > 0) {
                metodos.setPrefWrapLength(Math.max(320, actual.doubleValue() - 4));
            }
        });
        metodos.getStyleClass().add("method-grid");
        for (EstimacionCostosService.Metodo metodo : EstimacionCostosService.Metodo.values()) {
            PanelMetodo panel = new PanelMetodo(metodo);
            paneles.add(panel);
            metodos.getChildren().add(panel.tarjeta());
        }

        TableView<FilaComparacion> tabla = new TableView<>();
        tabla.setPlaceholder(new Label("Consulte para comparar la estimación promedio con los costos históricos."));
        // Hay cuatro rubros comparables; el alto se ajusta a sus filas y deja
        // espacio para leer los resultados sin convertir la tabla vacía en un
        // bloque desproporcionado.
        tabla.setMinHeight(250);
        tabla.setPrefHeight(280);
        tabla.setMaxHeight(300);
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<FilaComparacion, String> cRubro = new TableColumn<>("Rubro");
        cRubro.setPrefWidth(280);
        cRubro.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rubro()));
        TableColumn<FilaComparacion, String> cEstimado = new TableColumn<>("Estimación promedio (USD)");
        cEstimado.setPrefWidth(185);
        cEstimado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().estimado()));
        TableColumn<FilaComparacion, String> cHistorico = new TableColumn<>("Costos históricos (USD)");
        cHistorico.setPrefWidth(185);
        cHistorico.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().historico()));
        TableColumn<FilaComparacion, String> cBrecha = new TableColumn<>("Brecha histórica (USD)");
        cBrecha.setPrefWidth(180);
        cBrecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().brecha()));
        TableColumn<FilaComparacion, String> cBrechaPct = new TableColumn<>("Brecha %");
        cBrechaPct.setPrefWidth(125);
        cBrechaPct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().brechaPorcentual()));
        tabla.getColumns().addAll(cRubro, cEstimado, cHistorico, cBrecha, cBrechaPct);

        javafx.scene.control.TextArea porMetodo = new javafx.scene.control.TextArea();
        porMetodo.setEditable(false);
        porMetodo.setWrapText(true);
        porMetodo.setPrefRowCount(10);
        porMetodo.setPromptText("El costo total estimado por cada método aparecerá aquí.");
        porMetodo.setAccessibleText("Costo total estimado por cada método de estimación");
        porMetodo.getStyleClass().add("estimation-method-output");

        Label promedioValor = valorEstadistica();
        Label medianaValor = valorEstadistica();
        Label rangoValor = valorEstadistica();
        Label desviacionValor = valorEstadistica();
        Label variabilidadValor = valorEstadistica();
        Label varianzaValor = valorEstadistica();
        FlowPane estadisticas = ResponsiveLayouts.row(
                estadistica("Promedio", promedioValor, "Costo total medio"),
                estadistica("Mediana", medianaValor, "Punto central"),
                estadistica("Rango", rangoValor, "Máximo − mínimo"),
                estadistica("Desv. estándar", desviacionValor, "Dispersión muestral"),
                estadistica("Variabilidad", variabilidadValor, "Coeficiente de variación"),
                estadistica("Varianza", varianzaValor, "Dispersión al cuadrado"));
        estadisticas.setAccessibleText("Estadísticos de las estimaciones de costo total");

        Label detalle = new Label("Cada método usa sus insumos en su unidad; el promedio "
                + "se cruza con los costos reales de las órdenes.");
        detalle.setWrapText(true);
        detalle.setMaxWidth(Double.MAX_VALUE);
        detalle.getStyleClass().add("section-note");

        Button comparar = new Button("Comparar estimación promedio con históricos");
        comparar.setOnAction(evento -> {
            try {
                BigDecimal t = UiCampos.decimal(tarifa.getText(), "tarifa");
                BigDecimal c = UiCampos.decimal(cifPct.getText(), "porcentaje de CIF");
                BigDecimal m = UiCampos.decimal(margenPct.getText(), "margen");
                // Primero se validan y capturan las horas de todos los métodos para que
                // un insumo inválido no deje resultados a medias en las tarjetas.
                List<BigDecimal> horasPorMetodo = new ArrayList<>(paneles.size());
                for (PanelMetodo panel : paneles) {
                    horasPorMetodo.add(panel.horas(motor));
                }
                List<EstimacionCostosService.Resultado> resultados = new ArrayList<>(paneles.size());
                StringBuilder porMetodoTexto = new StringBuilder();
                for (int i = 0; i < paneles.size(); i++) {
                    PanelMetodo panel = paneles.get(i);
                    var r = motor.costearEsfuerzo(panel.metodo(), horasPorMetodo.get(i), t, c, m);
                    resultados.add(r);
                    panel.mostrarResultado(r);
                    porMetodoTexto.append(String.format("%-24s %12s h → %s%n",
                            panel.metodo().etiqueta(), numero(r.esfuerzoHoras()),
                            dinero(r.costoTotal())));
                }
                var estadistica = motor.resumirCostos(resultados);
                var historicos = context.inventario().costosHistoricos();
                BigDecimal promedioDirecto = promedio(resultados, EstimacionCostosService.Resultado::costoDirecto);
                BigDecimal promedioCif = promedio(resultados, EstimacionCostosService.Resultado::cif);
                BigDecimal promedioTotal = promedio(resultados, EstimacionCostosService.Resultado::costoTotal);
                tabla.setItems(FXCollections.observableArrayList(List.of(
                        new FilaComparacion("MOD · Mano de obra directa",
                                dinero(promedioDirecto), dinero(historicos.mod()),
                                brecha(historicos.mod(), promedioDirecto),
                                brechaPorcentual(historicos.mod(), promedioDirecto)),
                        new FilaComparacion("Servicios y recursos directos", "—", dinero(historicos.servicios()),
                                "—", "—"),
                        new FilaComparacion("CIF · Costos indirectos de fabricación",
                                dinero(promedioCif), dinero(historicos.cif()),
                                brecha(historicos.cif(), promedioCif),
                                brechaPorcentual(historicos.cif(), promedioCif)),
                        new FilaComparacion("Costo total", dinero(promedioTotal), dinero(historicos.total()),
                                brecha(historicos.total(), promedioTotal),
                                brechaPorcentual(historicos.total(), promedioTotal)))));
                porMetodo.setText("Costo total estimado por método (USD):\n" + porMetodoTexto);
                promedioValor.setText(dinero(estadistica.promedio()));
                medianaValor.setText(dinero(estadistica.mediana()));
                rangoValor.setText(dinero(estadistica.rango()));
                desviacionValor.setText(dinero(estadistica.desviacionEstandarMuestral()));
                variabilidadValor.setText(porcentaje(estadistica.coeficienteVariacion()));
                varianzaValor.setText(dinero(estadistica.varianzaMuestral()));
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        return ResponsiveLayouts.sectionCard(
                "Estimación promedio de los métodos vs. costos históricos",
                "Capture los insumos de cada técnica en su unidad; se promedian los costos "
                        + "y se cruzan con los reales.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Tarifa/hora", tarifa),
                        ResponsiveLayouts.field("CIF %", cifPct), ResponsiveLayouts.field("Margen %", margenPct)),
                metodos, comparar, estadisticas, porMetodo, tabla, detalle);
    }

    /**
     * Apartado de un método de estimación: reúne sus insumos propios y el costo
     * total calculado, de modo que cada técnica use la unidad que le corresponde.
     */
    private static final class PanelMetodo {
        private final EstimacionCostosService.Metodo metodo;
        private final List<TextField> campos = new ArrayList<>();
        private final VBox tarjeta = new VBox(8);
        private final Label resultado = new Label("Costo total: —");

        PanelMetodo(EstimacionCostosService.Metodo metodo) {
            this.metodo = metodo;
            tarjeta.getStyleClass().add("method-card");
            Label titulo = new Label(metodo.etiqueta());
            titulo.getStyleClass().add("method-title");
            tarjeta.getChildren().add(titulo);
            List<ParametroMetodo> spec = parametrosMetodo(metodo);
            for (ParametroMetodo parametro : spec) {
                TextField campo = new TextField(parametro.valor());
                campo.setPromptText(parametro.etiqueta());
                UiCatalogos.ayuda(campo, parametro.etiqueta());
                campos.add(campo);
                Label etiqueta = new Label(parametro.etiqueta());
                etiqueta.getStyleClass().add("data-label");
                VBox field = new VBox(3, etiqueta, campo);
                field.getStyleClass().add("method-field");
                tarjeta.getChildren().add(field);
            }
            resultado.getStyleClass().add("method-result");
            resultado.setWrapText(true);
            resultado.setMaxWidth(Double.MAX_VALUE);
            tarjeta.getChildren().add(resultado);
        }

        VBox tarjeta() {
            return tarjeta;
        }

        EstimacionCostosService.Metodo metodo() {
            return metodo;
        }

        /** Esfuerzo en horas que produce el método con los insumos capturados. */
        BigDecimal horas(EstimacionCostosService motor) {
            List<ParametroMetodo> spec = parametrosMetodo(metodo);
            BigDecimal[] valores = new BigDecimal[campos.size()];
            List<BigDecimal> pesos = null;
            for (int i = 0; i < spec.size(); i++) {
                if (spec.get(i).esPesos()) {
                    pesos = pesos(campos.get(i).getText());
                } else {
                    valores[i] = valor(campos.get(i));
                }
            }
            return switch (metodo) {
                case JUICIO_EXPERTOS -> motor.juicioExpertos(valores[0], valores[1], valores[2], pesos);
                case ANALOGIA -> motor.analogia(valores[0], valores[1], valores[2], valores[3]);
                case PUNTOS_FUNCION -> motor.puntosFuncion(valores[0], valores[1]);
                case COSMIC -> motor.cosmic(valores[0], valores[1]);
                case COCOMO -> motor.cocomo(valores[0], valores[1], valores[2], valores[3]);
                case DESGASTE_TRABAJO -> motor.desgasteTrabajo(valores[0], valores[1]);
                case LINEAS_CODIGO -> motor.lineasCodigo(valores[0], valores[1]);
                case TRES_PUNTOS -> motor.tresPuntos(valores[0], valores[1], valores[2]);
                case SLIM_PUTNAM -> motor.slimPutnam(valores[0], valores[1], valores[2]);
            };
        }

        void mostrarResultado(EstimacionCostosService.Resultado resultado) {
            this.resultado.setText("Costo total: %s · %s h".formatted(
                    dinero(resultado.costoTotal()), numero(resultado.esfuerzoHoras())));
        }
    }

    private record FilaComparacion(String rubro, String estimado, String historico, String brecha,
                                   String brechaPorcentual) {
    }

    private static BigDecimal promedio(List<EstimacionCostosService.Resultado> resultados,
                                       Function<EstimacionCostosService.Resultado, BigDecimal> selector) {
        BigDecimal suma = resultados.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(resultados.size()), 2, RoundingMode.HALF_UP);
    }

    private static DecimalFormat formatoNumero(String patron) {
        DecimalFormat formato = new DecimalFormat(patron);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    }

    private static String dinero(BigDecimal valor) {
        return UiFormatos.dinero(valor);
    }

    private static String numero(BigDecimal valor) {
        return FORMATO_NUMERO.format(valor);
    }

    private static VBox estadistica(String etiqueta, Label valor, String detalle) {
        Label titulo = new Label(etiqueta);
        titulo.getStyleClass().add("metric-label");
        Label nota = new Label(detalle);
        nota.getStyleClass().add("muted-caption");
        nota.setWrapText(true);
        VBox tarjeta = new VBox(5, titulo, valor, nota);
        tarjeta.getStyleClass().add("metric-card");
        tarjeta.setPrefWidth(175);
        return tarjeta;
    }

    private static Label valorEstadistica() {
        Label valor = new Label("—");
        valor.getStyleClass().add("metric-value");
        return valor;
    }

    /** Mensaje de cálculo con ancho disponible y alto que crece con el texto. */
    private static Label mensajeResultado(String texto) {
        Label mensaje = new Label(texto);
        mensaje.setWrapText(true);
        mensaje.setMaxWidth(Double.MAX_VALUE);
        mensaje.getStyleClass().addAll("result-panel", "result-message");
        return mensaje;
    }

    /** La brecha positiva indica que el costo histórico supera el estimado promedio. */
    private static String brecha(BigDecimal historico, BigDecimal estimado) {
        BigDecimal diferencia = historico.subtract(estimado).setScale(2, RoundingMode.HALF_UP);
        return (diferencia.signum() > 0 ? "+" : "") + dinero(diferencia);
    }

    private static String brechaPorcentual(BigDecimal historico, BigDecimal estimado) {
        if (estimado.signum() == 0) {
            return "—";
        }
        BigDecimal porcentaje = historico.subtract(estimado).multiply(BigDecimal.valueOf(100))
                .divide(estimado, 2, RoundingMode.HALF_UP);
        return (porcentaje.signum() > 0 ? "+" : "") + numero(porcentaje) + "%";
    }

    private static String porcentaje(BigDecimal valor) {
        return numero(valor) + "%";
    }
}
