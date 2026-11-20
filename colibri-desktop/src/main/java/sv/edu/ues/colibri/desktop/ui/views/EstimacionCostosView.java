package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        UiCatalogos.ayuda(parametro1, "Parámetro del método seleccionado; la etiqueta se actualiza al cambiar la técnica. "
                + "CFP = casos de uso funcionales (COSMIC); KLOC = miles de líneas de código; "
                + "Σ/Π = suma y producto de factores; Ck = productividad (SLIM/Putnam).");
        UiCatalogos.ayuda(parametro2, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro3, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro4, "Parámetro del método seleccionado. En Juicio de expertos "
                + "ingrese los 3 pesos separados por coma (optimista,probable,pesimista).");
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
                var r = motor.costearEsfuerzo(metodo.getValue(), horas, new BigDecimal(tarifa.getText()),
                        new BigDecimal(cif.getText()), new BigDecimal(margen.getText()));
                resultado.setText("Método: %s · Esfuerzo: %s h · Directo: $%s · CIF: $%s · "
                        .formatted(r.metodo(), r.esfuerzoHoras(), r.costoDirecto(), r.cif())
                        + "Costo total: $%s · Precio unitario: $%s · Margen: $%s%n%s"
                        .formatted(r.costoTotal(), r.precioUnitario(), r.margen(), r.recomendacion()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
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
        return texto.isEmpty() ? BigDecimal.ZERO : new BigDecimal(texto);
    }

    private static List<BigDecimal> pesos(String texto) {
        List<BigDecimal> lista = new ArrayList<>();
        if (texto != null) {
            for (String parte : texto.split("[,;\\s]+")) {
                if (!parte.isBlank()) {
                    lista.add(new BigDecimal(parte.trim()));
                }
            }
        }
        return lista;
    }

    private static void configurarParametros(EstimacionCostosService.Metodo metodo, VBox contenedor,
                                             TextField p1, TextField p2, TextField p3, TextField p4) {
        String l1;
        String l2;
        String l3 = null;
        String l4 = null;
        String v1;
        String v2;
        String v3 = "";
        String v4 = "";
        switch (metodo) {
            case JUICIO_EXPERTOS -> { l1 = "Optimista"; l2 = "Probable"; l3 = "Pesimista"; l4 = "Pesos (o,p,pe)"; v1 = "80"; v2 = "100"; v3 = "130"; v4 = "1,1,1"; }
            case ANALOGIA -> { l1 = "Esfuerzo comparable"; l2 = "Tamaño comparable"; l3 = "Tamaño objetivo"; l4 = "Factor de ajuste"; v1 = "100"; v2 = "100"; v3 = "100"; v4 = "1"; }
            case PUNTOS_FUNCION -> { l1 = "Puntos ajustados"; l2 = "Horas por punto"; v1 = "100"; v2 = "1.2"; }
            case COSMIC -> { l1 = "CFP"; l2 = "Horas por CFP"; v1 = "100"; v2 = "1.1"; }
            case COCOMO -> { l1 = "KLOC"; l2 = "Σ factores de escala"; l3 = "Π multiplicadores de esfuerzo"; l4 = "Horas por mes"; v1 = "10"; v2 = "0"; v3 = "1"; v4 = "160"; }
            case DESGASTE_TRABAJO -> { l1 = "Horas productivas"; l2 = "Desgaste %"; v1 = "100"; v2 = "15"; }
            case LINEAS_CODIGO -> { l1 = "KLOC"; l2 = "KLOC por persona-mes"; v1 = "100"; v2 = "1.3333333333"; }
            case TRES_PUNTOS -> { l1 = "Optimista"; l2 = "Probable"; l3 = "Pesimista"; v1 = "80"; v2 = "100"; v3 = "130"; }
            case SLIM_PUTNAM -> { l1 = "Tamaño"; l2 = "Productividad Ck"; l3 = "Duración (años)"; v1 = "100"; v2 = "10"; v3 = "1"; }
            default -> throw new IllegalStateException("Método no soportado: " + metodo);
        }
        p1.setText(v1); p2.setText(v2); p3.setText(v3); p4.setText(v4);
        contenedor.getChildren().clear();
        if (l4 != null) {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2), ResponsiveLayouts.field(l3, p3), ResponsiveLayouts.field(l4, p4)));
        } else if (l3 != null) {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2), ResponsiveLayouts.field(l3, p3)));
        } else {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2)));
        }
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
                BigDecimal mat = new BigDecimal(materiales.getText());
                BigDecimal manoObra = new BigDecimal(mod.getText());
                int cantidad = Integer.parseInt(unidades.getText());
                var directo = motor.calcularDirecto(mat, manoObra, cantidad);
                var estandar = motor.analizarEstandar(mat, manoObra, new BigDecimal(cifEstandar.getText()),
                        new BigDecimal(real.getText()), cantidad);
                var abc = motor.calcularAbc(mat, manoObra, java.util.List.of(
                        new CosteoGerencialService.ActividadAbc("Calidad", new BigDecimal(calidad.getText())),
                        new CosteoGerencialService.ActividadAbc("Infraestructura", new BigDecimal(infraestructura.getText()))),
                        cantidad);
                salida.setText("Directo: $%s total / $%s por unidad · Estándar: $%s total / variación $%s · "
                        .formatted(directo.total(), directo.unitario(), estandar.totalEstandar(), estandar.variacion())
                        + "ABC: CIF aplicado $%s, total $%s / $%s por unidad."
                        .formatted(abc.cifPorActividades(), abc.total(), abc.unitario()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
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
                salida.setText("Orden %s · Estimado $%s · Real $%s · Variación $%s (%s%%) · "
                        .formatted(r.codigo(), r.costoEstimado(), r.costoReal(), r.variacionAbsoluta(),
                                r.variacionPorcentual())
                        + "Índice de ejecución %s%% · Ahorro $%s · Sobrecosto $%s · Utilidad esperada $%s · "
                        .formatted(r.indiceEjecucion(), r.ahorroPresupuestario(), r.sobrecosto(),
                                r.utilidadEsperada())
                        + "Utilidad real $%s · Rentabilidad sobre venta %s%%\n%s"
                        .formatted(r.utilidadReal(), r.rentabilidadSobreVenta(), r.diagnostico()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });
        return ResponsiveLayouts.sectionCard("Comparación de costos estimados y reales",
                "La estimación toma el presupuesto de la orden y la compara con el costo real registrado en las partidas.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Orden", orden), comparar), salida);
    }

    private VBox comparacionPromedioHistoricos(EstimacionCostosService motor) {
        TextField base = new TextField("100");
        base.setPromptText("Valor base común");
        UiCatalogos.ayuda(base, "Valor base que cada método interpreta según su técnica "
                + "(horas, puntos de función, CFP, KLOC, tamaño, etc.).");
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa en USD.");
        TextField cifPct = new TextField("20");
        cifPct.setPromptText("%");
        UiCatalogos.ayuda(cifPct, "Porcentaje de costos indirectos de fabricación sobre la mano de obra.");
        TextField margenPct = new TextField("30");
        margenPct.setPromptText("%");
        UiCatalogos.ayuda(margenPct, "Margen de utilidad objetivo sobre el costo total, en %.");

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
        porMetodo.setWrapText(false);
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

        Label detalle = new Label("El promedio se calcula sobre los 9 métodos implementados; "
                + "los históricos suman los consumos reales de las órdenes contabilizadas. La desviación "
                + "estándar y el coeficiente de variación muestran cuánto difieren los métodos entre sí.");
        detalle.setWrapText(true);
        detalle.setMaxWidth(Double.MAX_VALUE);
        detalle.getStyleClass().add("section-note");

        Button comparar = new Button("Comparar estimación promedio con históricos");
        comparar.setOnAction(evento -> {
            try {
                BigDecimal b = new BigDecimal(base.getText().trim());
                BigDecimal t = new BigDecimal(tarifa.getText().trim());
                BigDecimal c = new BigDecimal(cifPct.getText().trim());
                BigDecimal m = new BigDecimal(margenPct.getText().trim());
                List<EstimacionCostosService.Resultado> resultados = new ArrayList<>();
                StringBuilder porMetodoTexto = new StringBuilder();
                for (EstimacionCostosService.Metodo met : EstimacionCostosService.Metodo.values()) {
                    var r = motor.estimar(met, b, t, c, m);
                    resultados.add(r);
                    porMetodoTexto.append(String.format("%-22s $%s%n", met.etiqueta(), r.costoTotal()));
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
                UiDialogs.error(ex.getMessage());
            }
        });
        return ResponsiveLayouts.sectionCard(
                "Estimación promedio de los métodos vs. costos históricos",
                "Promedia el costo estimado por los 9 métodos de estimación y lo cruza con los "
                        + "consumos reales registrados en las órdenes de servicio.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Base de estimación", base),
                        ResponsiveLayouts.field("Tarifa/hora", tarifa),
                        ResponsiveLayouts.field("CIF %", cifPct), ResponsiveLayouts.field("Margen %", margenPct)),
                comparar, estadisticas, porMetodo, tabla, detalle);
    }

    private record FilaComparacion(String rubro, String estimado, String historico, String brecha,
                                   String brechaPorcentual) {
    }

    private static BigDecimal promedio(List<EstimacionCostosService.Resultado> resultados,
                                       Function<EstimacionCostosService.Resultado, BigDecimal> selector) {
        BigDecimal suma = resultados.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(resultados.size()), 2, RoundingMode.HALF_UP);
    }

    private static String dinero(BigDecimal valor) {
        return "$" + valor.setScale(2, RoundingMode.HALF_UP);
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
        return (porcentaje.signum() > 0 ? "+" : "") + porcentaje + "%";
    }

    private static String porcentaje(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
