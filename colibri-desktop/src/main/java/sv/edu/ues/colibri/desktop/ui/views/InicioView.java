package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.panel.PanelInicioService;
import sv.edu.ues.colibri.desktop.ui.layout.DosColumnas;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.Periodo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Tablero gerencial: estado contable del período, comparación contra el mes
 * anterior, precios vigentes, actividad comercial y alertas operativas.
 */
public class InicioView extends VBox {

    private final AppContext context;

    public InicioView(AppContext context, Runnable irPartidas) {
        this.context = context;
        setSpacing(16);
        setPadding(new javafx.geometry.Insets(16));

        Label titulo = new Label("Resumen contable");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label(
                "Una lectura breve del estado financiero y las prioridades del período.");
        subtitulo.getStyleClass().add("view-subtitle");

        Periodo periodo = context.periodos().periodoAbierto().orElse(null);
        long cuentas = context.catalogo().contarCuentas();
        boolean catalogoAprobado = context.catalogo().catalogoAprobado();
        var borradores = context.partidas().listarBorradores();

        PanelInicioService.ResumenPanel resumen = cargarResumen(periodo);

        Button aprobarCatalogo = new Button("Aprobar catálogo inicial");
        aprobarCatalogo.setDisable(catalogoAprobado);
        Label estadoCatalogo = detalle(catalogoAprobado ? "Aprobado para movimientos"
                : "Pendiente de aprobación", catalogoAprobado ? "status-pill" : "warning-pill");
        aprobarCatalogo.setOnAction(e -> {
            try {
                context.catalogo().aprobarCatalogoInicial();
                aprobarCatalogo.setDisable(true);
                estadoCatalogo.setText("Aprobado para movimientos");
                estadoCatalogo.getStyleClass().setAll("status-pill");
            } catch (Exception ex) {
                sv.edu.ues.colibri.desktop.ui.UiDialogs.error(ex.getMessage());
            }
        });

        Button ir = new Button("Ir a registros");
        ir.setOnAction(e -> irPartidas.run());
        HBox acciones = new HBox(10, aprobarCatalogo, ir);
        acciones.getStyleClass().add("dashboard-actions");

        VBox titulos = new VBox(3, titulo, subtitulo);
        HBox.setHgrow(titulos, Priority.ALWAYS);
        VBox periodoActual = periodoActual(periodo);
        HBox cabecera = new HBox(24, titulos, periodoActual);
        cabecera.setAlignment(Pos.TOP_LEFT);
        cabecera.getStyleClass().add("dashboard-header");

        getChildren().addAll(cabecera,
                panoramaFinanciero(resumen),
                new DosColumnas(controlOperativo(cuentas, borradores, estadoCatalogo, acciones),
                        panoramaComercial(resumen)),
                graficas(resumen),
                alertas(resumen),
                new DosColumnas(panelPlanes(resumen), panelClientes(resumen)),
                panelSaasCif(resumen),
                panelOrdenes(resumen),
                panelBorradores(borradores));
    }

    private PanelInicioService.ResumenPanel cargarResumen(Periodo periodo) {
        try {
            return context.panel().resumen(periodo);
        } catch (Exception ex) {
            return new PanelInicioService.ResumenPanel(null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, null, 0,
                    java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    java.util.List.of());
        }
    }

    /** Encabezado compacto que mantiene el período visible sin convertirlo en otra tarjeta métrica. */
    private VBox periodoActual(Periodo periodo) {
        Label etiqueta = new Label("PERÍODO ACTIVO");
        etiqueta.getStyleClass().add("dashboard-period-label");
        String nombre = periodo == null ? "Sin período abierto"
                : mes(periodo.getMes()) + " " + periodo.getFechaInicio().getYear();
        Label valor = new Label(nombre);
        valor.getStyleClass().add("dashboard-period-value");
        Label nota = new Label(periodo == null ? "Configure o reabra un período para capturar."
                : "Captura habilitada hasta " + periodo.getFechaFin());
        nota.getStyleClass().add("dashboard-period-note");
        nota.setWrapText(true);
        VBox bloque = new VBox(3, etiqueta, valor, nota);
        bloque.getStyleClass().add("dashboard-period");
        return bloque;
    }

    /** Presenta las tres cifras que orientan la lectura del período en un único bloque jerarquizado. */
    private VBox panoramaFinanciero(PanelInicioService.ResumenPanel resumen) {
        Label encabezado = new Label("Pulso financiero");
        encabezado.getStyleClass().add("section-heading");
        Label contexto = new Label("Cifras contabilizadas del período actual");
        contexto.getStyleClass().add("muted-caption");

        HBox cifras = new HBox();
        cifras.setAlignment(Pos.CENTER_LEFT);
        cifras.getChildren().addAll(
                cifraFinanciera("Ingresos", dinero(resumen.ingresosMes()),
                        variacionTexto(resumen.variacionIngresos(), resumen.ingresosMesAnterior()),
                        resumen.variacionIngresos().signum() < 0 ? "negative" : "positive"),
                divisorFinanciero(),
                cifraFinanciera("Utilidad", dinero(resumen.utilidadPeriodo()),
                        "Resultado de las cuentas 4–7", resumen.utilidadPeriodo().signum() < 0
                                ? "negative" : "positive"),
                divisorFinanciero(),
                cifraFinanciera("Cartera", dinero(resumen.carteraClientes()),
                        resumen.documentosPendientesCobro() + " DTE(s) por "
                                + dinero(resumen.dtesPendientes()),
                        resumen.documentosPendientesCobro() > 0 ? "attention" : "neutral"));
        VBox panel = new VBox(14, encabezado, contexto, cifras);
        panel.getStyleClass().add("dashboard-overview");
        return panel;
    }

    private VBox cifraFinanciera(String etiqueta, String valor, String nota, String tono) {
        Label titulo = new Label(etiqueta);
        titulo.getStyleClass().add("financial-label");
        Label importe = new Label(valor);
        importe.getStyleClass().addAll("financial-value", "financial-" + tono);
        importe.setWrapText(true);
        Label detalle = new Label(nota);
        detalle.getStyleClass().add("financial-note");
        detalle.setWrapText(true);
        VBox bloque = new VBox(5, titulo, importe, detalle);
        bloque.getStyleClass().add("financial-metric");
        HBox.setHgrow(bloque, Priority.ALWAYS);
        return bloque;
    }

    private Separator divisorFinanciero() {
        Separator divisor = new Separator(javafx.geometry.Orientation.VERTICAL);
        divisor.getStyleClass().add("financial-divider");
        return divisor;
    }

    private VBox controlOperativo(long cuentas, java.util.List<Partida> borradores,
                                  Label estadoCatalogo, HBox acciones) {
        Label encabezado = new Label("Control operativo");
        encabezado.getStyleClass().add("section-heading");
        VBox panel = new VBox(12, encabezado,
                filaOperativa("Catálogo de cuentas", cuentas + " cuentas configuradas", estadoCatalogo),
                filaOperativa("Borradores", borradores.isEmpty() ? "Sin partidas por revisar"
                        : borradores.size() + " pendiente(s) de revisión", detalle(borradores.isEmpty()
                        ? "Al día" : "Atención requerida", borradores.isEmpty() ? "status-pill" : "warning-pill")),
                acciones);
        panel.getStyleClass().addAll("surface-card", "operational-summary");
        return panel;
    }

    private HBox filaOperativa(String etiqueta, String valor, Label estado) {
        Label titulo = new Label(etiqueta);
        titulo.getStyleClass().add("operational-key");
        Label detalle = new Label(valor);
        detalle.getStyleClass().add("operational-value");
        VBox texto = new VBox(2, titulo, detalle);
        HBox.setHgrow(texto, Priority.ALWAYS);
        HBox fila = new HBox(12, texto, estado);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("operational-row");
        return fila;
    }

    private VBox panoramaComercial(PanelInicioService.ResumenPanel resumen) {
        Label encabezado = new Label("Actividad comercial");
        encabezado.getStyleClass().add("section-heading");
        PanelInicioService.ProductoTop producto = resumen.productoTop();
        String nombreProducto = producto == null ? "Aún no hay ventas registradas" : producto.nombre();
        String detalleProducto = producto == null ? "Las licencias entregadas aparecerán aquí."
                : producto.unidades() + " unidades · " + dinero(producto.ingresos());
        VBox panel = new VBox(10, encabezado,
                resumenComercial("Producto más vendido", nombreProducto, detalleProducto),
                resumenComercial("Usuarios SaaS", String.valueOf(resumen.usuariosSaasMes()),
                        "usuarios-mes facturables en el período"));
        panel.getStyleClass().addAll("surface-card", "commercial-summary");
        return panel;
    }

    private VBox resumenComercial(String etiqueta, String valor, String nota) {
        Label titulo = new Label(etiqueta);
        titulo.getStyleClass().add("operational-key");
        Label principal = new Label(valor);
        principal.getStyleClass().add("commercial-value");
        principal.setWrapText(true);
        Label detalle = new Label(nota);
        detalle.getStyleClass().add("financial-note");
        detalle.setWrapText(true);
        VBox bloque = new VBox(2, titulo, principal, detalle);
        bloque.getStyleClass().add("commercial-row");
        return bloque;
    }

    private javafx.scene.Node graficas(PanelInicioService.ResumenPanel resumen) {
        VBox barras = graficaBarras(resumen.serieMensual());
        VBox torta = graficaClientes(resumen.clientesTop());
        return new DosColumnas(barras, torta);
    }

    /**
     * Muestra la tendencia reciente sin dejar que un cierre extraordinario
     * aplaste visualmente los demás meses. El importe excepcional permanece
     * visible como hito y en el detalle numérico, nunca se omite ni altera.
     */
    private VBox graficaBarras(List<PanelInicioService.PuntoMes> serie) {
        Label heading = new Label("Ingresos vs. egresos · tendencia reciente");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");
        if (serie.isEmpty()) {
            card.getChildren().add(detalle("Aún no hay partidas contabilizadas en los últimos meses.",
                    "status-pill"));
            return card;
        }
        SerieVisible serieVisible = separarHito(serie);
        javafx.scene.chart.CategoryAxis ejeX = new javafx.scene.chart.CategoryAxis();
        javafx.scene.chart.NumberAxis ejeY = new javafx.scene.chart.NumberAxis();
        ejeY.setLabel("USD");
        ejeY.setForceZeroInRange(true);
        ejeY.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number valor) {
                return dineroCompacto(BigDecimal.valueOf(valor.doubleValue()));
            }

            @Override
            public Number fromString(String texto) {
                return 0;
            }
        });
        javafx.scene.chart.BarChart<String, Number> chart =
                new javafx.scene.chart.BarChart<>(ejeX, ejeY);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setCategoryGap(22);
        chart.setBarGap(3);
        chart.setPrefHeight(250);
        chart.getStyleClass().addAll("dashboard-chart", "dashboard-income-chart");
        javafx.scene.chart.XYChart.Series<String, Number> ingresos =
                new javafx.scene.chart.XYChart.Series<>();
        ingresos.setName("Ingresos");
        javafx.scene.chart.XYChart.Series<String, Number> egresos =
                new javafx.scene.chart.XYChart.Series<>();
        egresos.setName("Egresos");
        for (PanelInicioService.PuntoMes p : serieVisible.mesesEnGrafica()) {
            String etiqueta = p.etiqueta();
            ingresos.getData().add(new javafx.scene.chart.XYChart.Data<>(etiqueta,
                    p.ingresos().doubleValue()));
            egresos.getData().add(new javafx.scene.chart.XYChart.Data<>(etiqueta,
                    p.egresos().doubleValue()));
        }
        chart.getData().addAll(ingresos, egresos);
        card.getChildren().add(chart);
        if (serieVisible.hito() != null) {
            PanelInicioService.PuntoMes hito = serieVisible.hito();
            Label aviso = new Label("Hito " + hito.etiqueta() + ": ingresos "
                    + dinero(hito.ingresos()) + " y egresos " + dinero(hito.egresos())
                    + ". Se muestra aparte para conservar legible la tendencia reciente.");
            aviso.setWrapText(true);
            aviso.getStyleClass().add("chart-outlier-note");
            card.getChildren().add(aviso);
        }
        card.getChildren().add(resumenMensual(serie));
        return card;
    }

    /** Las barras ordenadas reemplazan el pastel: evitan etiquetas superpuestas y conservan importes exactos. */
    private VBox graficaClientes(List<PanelInicioService.ClienteTop> clientes) {
        Label heading = new Label("Facturación por cliente · período");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card", "customer-ranking");
        if (clientes.isEmpty()) {
            card.getChildren().add(estadoVacio("Aún no hay clientes facturados en este período.",
                    "Los clientes aparecerán al contabilizar CCF o facturas."));
            return card;
        }
        BigDecimal total = clientes.stream().map(PanelInicioService.ClienteTop::facturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Label totalFacturado = new Label("Total facturado: " + dinero(total));
        totalFacturado.getStyleClass().add("customer-total");
        card.getChildren().add(totalFacturado);
        int posicion = 0;
        for (PanelInicioService.ClienteTop cliente : clientes) {
            posicion++;
            card.getChildren().add(barraCliente(posicion, cliente, total));
        }
        return card;
    }

    private HBox barraCliente(int posicion, PanelInicioService.ClienteTop cliente, BigDecimal total) {
        Label numero = new Label(String.valueOf(posicion));
        numero.getStyleClass().add("customer-rank");
        Label nombre = new Label(cliente.nombre());
        nombre.setWrapText(true);
        nombre.getStyleClass().add("customer-name");
        Label monto = new Label(dinero(cliente.facturado()));
        monto.getStyleClass().add("customer-amount");
        HBox cabecera = new HBox(8, numero, nombre, monto);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nombre, Priority.ALWAYS);

        double participacion = total.signum() == 0 ? 0
                : cliente.facturado().divide(total, 6, RoundingMode.HALF_UP).doubleValue();
        ProgressBar barra = new ProgressBar(participacion);
        barra.setMaxWidth(Double.MAX_VALUE);
        barra.getStyleClass().add("customer-progress");
        VBox detalle = new VBox(4, cabecera, barra);
        HBox.setHgrow(detalle, Priority.ALWAYS);
        HBox fila = new HBox(detalle);
        fila.getStyleClass().add("customer-ranking-row");
        return fila;
    }

    private FlowPane resumenMensual(List<PanelInicioService.PuntoMes> serie) {
        FlowPane detalle = new FlowPane(8, 8);
        detalle.getStyleClass().add("chart-monthly-summary");
        for (PanelInicioService.PuntoMes punto : serie) {
            BigDecimal resultado = punto.ingresos().subtract(punto.egresos());
            String textoCompleto = punto.etiqueta() + "  ·  I " + dinero(punto.ingresos())
                    + "  ·  E " + dinero(punto.egresos()) + "  ·  R " + dinero(resultado);
            Label mes = new Label(textoCompleto);
            mes.getStyleClass().add("chart-monthly-item");
            mes.setTextOverrun(OverrunStyle.ELLIPSIS);
            mes.setMaxWidth(Double.MAX_VALUE);
            mes.setTooltip(new Tooltip(textoCompleto));
            detalle.getChildren().add(mes);
        }
        return detalle;
    }

    private SerieVisible separarHito(List<PanelInicioService.PuntoMes> serie) {
        if (serie.size() < 3) {
            return new SerieVisible(List.copyOf(serie), null);
        }
        List<PanelInicioService.PuntoMes> ordenados = new ArrayList<>(serie);
        ordenados.sort(Comparator.comparing(this::volumenMes).reversed());
        BigDecimal mayor = volumenMes(ordenados.getFirst());
        BigDecimal segundo = volumenMes(ordenados.get(1));
        if (segundo.signum() == 0 || mayor.compareTo(segundo.multiply(BigDecimal.valueOf(4))) <= 0) {
            return new SerieVisible(List.copyOf(serie), null);
        }
        PanelInicioService.PuntoMes hito = ordenados.getFirst();
        List<PanelInicioService.PuntoMes> recientes = new ArrayList<>(serie);
        recientes.remove(hito);
        return new SerieVisible(List.copyOf(recientes), hito);
    }

    private BigDecimal volumenMes(PanelInicioService.PuntoMes punto) {
        return punto.ingresos().abs().max(punto.egresos().abs());
    }

    private record SerieVisible(List<PanelInicioService.PuntoMes> mesesEnGrafica,
                                PanelInicioService.PuntoMes hito) {
    }

    private VBox alertas(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Alertas operativas");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().add("surface-card");
        if (resumen.alertas().isEmpty()) {
            card.getChildren().add(detalle("Sin alertas. Las operaciones del período están al día.",
                    "status-pill"));
            return card;
        }
        for (PanelInicioService.Alerta alerta : resumen.alertas()) {
            String estilo = switch (alerta.severidad()) {
                case "ALTA" -> "warning-pill";
                case "AVISO" -> "warning-pill";
                default -> "status-pill";
            };
            card.getChildren().add(detalle("· " + alerta.mensaje(), estilo));
        }
        return card;
    }

    private VBox panelPlanes(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Precios vigentes por plan");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-data-card");
        if (resumen.planes().isEmpty()) {
            card.getChildren().add(estadoVacio("No hay planes vigentes para mostrar.",
                    "Cree o active un plan desde Inventario → Servicios / Accesos."));
            return card;
        }
        TableView<PanelInicioService.PlanTarifa> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setFixedCellSize(34);
        tabla.getStyleClass().add("dashboard-data-table");
        agregarColumna(tabla, "Código", PanelInicioService.PlanTarifa::codigo);
        agregarColumna(tabla, "Plan", PanelInicioService.PlanTarifa::nombre);
        agregarColumna(tabla, "Modalidad", PanelInicioService.PlanTarifa::modalidad);
        TableColumn<PanelInicioService.PlanTarifa, String> tarifa = new TableColumn<>("Tarifa");
        tarifa.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                dinero(c.getValue().tarifaBase())));
        TableColumn<PanelInicioService.PlanTarifa, String> facturacion = new TableColumn<>("Facturación");
        facturacion.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().tipoFacturacion()));
        tabla.getColumns().addAll(java.util.List.of(tarifa, facturacion));
        tabla.getItems().setAll(resumen.planes());
        tabla.setPrefHeight(altoTabla(resumen.planes().size()));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        card.getChildren().add(tabla);
        return card;
    }

    private VBox panelClientes(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Clientes principales del período");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-data-card");
        if (resumen.clientesTop().isEmpty()) {
            card.getChildren().add(estadoVacio("No hay facturación de clientes en este período.",
                    "Los clientes se mostrarán cuando se contabilicen CCF o facturas."));
            return card;
        }
        TableView<PanelInicioService.ClienteTop> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setFixedCellSize(34);
        tabla.getStyleClass().add("dashboard-data-table");
        agregarColumna(tabla, "Cliente", PanelInicioService.ClienteTop::nombre);
        TableColumn<PanelInicioService.ClienteTop, String> facturado = new TableColumn<>("Facturado");
        facturado.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                dinero(c.getValue().facturado())));
        TableColumn<PanelInicioService.ClienteTop, String> docs = new TableColumn<>("Docs");
        docs.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().documentos())));
        docs.setMaxWidth(80);
        tabla.getColumns().addAll(java.util.List.of(facturado, docs));
        tabla.getItems().setAll(resumen.clientesTop());
        tabla.setPrefHeight(altoTabla(resumen.clientesTop().size()));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        card.getChildren().add(tabla);
        return card;
    }

    private VBox panelOrdenes(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Órdenes de servicio · estimado vs. real");
        heading.getStyleClass().add("section-heading");
        TableView<PanelInicioService.OrdenComparativo> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Sin órdenes de servicio registradas."));
        agregarColumna(tabla, "Orden", PanelInicioService.OrdenComparativo::codigo);
        agregarColumna(tabla, "Descripción", PanelInicioService.OrdenComparativo::descripcion);
        TableColumn<PanelInicioService.OrdenComparativo, String> presupuesto =
                new TableColumn<>("Presupuesto");
        presupuesto.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                dinero(c.getValue().presupuesto())));
        TableColumn<PanelInicioService.OrdenComparativo, String> real = new TableColumn<>("Costo real");
        real.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                dinero(c.getValue().costoReal())));
        TableColumn<PanelInicioService.OrdenComparativo, String> variacion = new TableColumn<>("Var. %");
        variacion.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().variacionPct() + "%"));
        variacion.setMaxWidth(90);
        agregarColumna(tabla, "Estado", PanelInicioService.OrdenComparativo::estado);
        tabla.getColumns().addAll(java.util.List.of(presupuesto, real, variacion));
        tabla.getItems().setAll(resumen.ordenes());
        tabla.setPrefHeight(170);
        VBox card = new VBox(8, heading, tabla);
        card.getStyleClass().add("surface-card");
        return card;
    }

    private VBox panelSaasCif(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("SaaS por plan y CIF del período");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().add("surface-card");
        for (PanelInicioService.SaasPlan plan : resumen.saasPorPlan()) {
            card.getChildren().add(new Label(plan.plan() + ": " + plan.usuariosMes() + " usuario(s)-mes"));
        }
        for (PanelInicioService.CifComparativo cif : resumen.cif()) {
            card.getChildren().add(new Label("CIF " + cif.centro() + " · aplicado "
                    + dinero(cif.aplicado()) + " / presupuestado " + dinero(cif.presupuestado())
                    + " · variación " + dinero(cif.variacion())));
        }
        if (resumen.saasPorPlan().isEmpty() && resumen.cif().isEmpty()) {
            card.getChildren().add(detalle("Sin datos de SaaS o CIF para el período.", "status-pill"));
        }
        return card;
    }

    private VBox panelBorradores(java.util.List<Partida> borradores) {
        Label pendientesTitulo = new Label("Borradores por revisar");
        pendientesTitulo.getStyleClass().add("section-heading");
        ListView<String> lista = new ListView<>();
        lista.setPlaceholder(new Label("No hay borradores pendientes. El libro diario está al día."));
        for (Partida p : borradores) {
            lista.getItems().add("%s  ·  %s  ·  Debe $%s / Haber $%s".formatted(
                    p.getFechaContable(), p.getConcepto(), p.totalDebe(), p.totalHaber()));
        }
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox pendientes = new VBox(10, pendientesTitulo,
                new Label("Los borradores pueden estar descuadrados; una partida solo se contabiliza "
                        + "cuando Debe y Haber coinciden exactamente."),
                lista);
        pendientes.getStyleClass().add("surface-card");
        VBox.setVgrow(pendientes, Priority.ALWAYS);
        return pendientes;
    }

    /**
     * Las filas del panel son records, por lo que usan accesores como {@code codigo()}
     * y no getters JavaBean. Una fábrica explícita evita las advertencias de reflexión
     * de {@link javafx.scene.control.cell.PropertyValueFactory} y mantiene visibles los datos.
     */
    private <T> void agregarColumna(TableView<T> tabla, String titulo, Function<T, String> valor) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                valor.apply(c.getValue())));
        tabla.getColumns().add(columna);
    }

    private VBox estadoVacio(String titulo, String explicacion) {
        Label mensaje = new Label(titulo);
        mensaje.getStyleClass().add("empty-state-title");
        Label detalle = new Label(explicacion);
        detalle.setWrapText(true);
        detalle.getStyleClass().add("empty-state-note");
        VBox estado = new VBox(4, mensaje, detalle);
        estado.getStyleClass().add("dashboard-empty-state");
        return estado;
    }

    private double altoTabla(int filas) {
        // Encabezado (40 px) + filas visibles; evita tarjetas altas y vacías.
        return Math.max(108, Math.min(244, 42 + filas * 34 + 8));
    }

    private Label detalle(String texto, String estilo) {
        Label label = new Label(texto);
        label.getStyleClass().add(estilo);
        label.setWrapText(true);
        return label;
    }

    private String variacionTexto(BigDecimal variacion, BigDecimal anterior) {
        String signo = variacion.signum() > 0 ? "+" : "";
        return "%s%s%% vs. mes anterior (%s)".formatted(signo, variacion, dinero(anterior));
    }

    private String dinero(BigDecimal monto) {
        return "$%,.2f".formatted(monto);
    }

    private String dineroCompacto(BigDecimal monto) {
        BigDecimal absoluto = monto.abs();
        if (absoluto.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return "$" + monto.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP) + "M";
        }
        if (absoluto.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return "$" + monto.divide(BigDecimal.valueOf(1_000), 0, RoundingMode.HALF_UP) + "k";
        }
        return "$" + monto.setScale(0, RoundingMode.HALF_UP);
    }

    private String mes(int numero) {
        String nombre = Month.of(numero).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }
}
