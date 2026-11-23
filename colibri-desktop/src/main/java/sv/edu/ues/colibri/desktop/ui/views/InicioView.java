package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.application.panel.PanelInicioService;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiFormatos;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.desktop.ui.UiTheme;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.Periodo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
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
                sv.edu.ues.colibri.desktop.ui.UiDialogs.errorDe(ex);
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

        // Bento: la gráfica abarca una sola fila a todo lo ancho; debajo
        // van los tres KPI y la tarjeta de período; al pie, «Por hacer» y
        // clientes a media anchura y «Costos y suscripciones» a ventana
        // completa.
        GridPane bento = new GridPane();
        bento.getStyleClass().add("bento-grid");
        bento.setHgap(16);
        bento.setVgap(16);
        ColumnConstraints ancha = new ColumnConstraints();
        ancha.setPercentWidth(46);
        ColumnConstraints angosta = new ColumnConstraints();
        angosta.setPercentWidth(27);
        bento.getColumnConstraints().addAll(ancha, angosta, angosta);
        // Los KPI respiran dentro de su tarjeta; el resto crece con el contenido.
        RowConstraints filaKpi = new RowConstraints();
        filaKpi.setMinHeight(130);
        bento.getRowConstraints().addAll(new RowConstraints(), filaKpi,
                new RowConstraints(), new RowConstraints(), new RowConstraints(),
                new RowConstraints(), new RowConstraints());

        // La gráfica de barras abarca todo el ancho disponible.
        bento.add(centrarEnGrilla(graficaBarras(periodo), 1600), 0, 0, 3, 1);
        bento.add(cifraFinanciera("Ingresos", dinero(resumen.ingresosMes()),
                variacionTexto(resumen.variacionIngresos(), resumen.ingresosMesAnterior()),
                resumen.variacionIngresos().signum() < 0 ? "negative" : "positive"), 0, 1);
        bento.add(cifraFinanciera("Utilidad", dinero(resumen.utilidadPeriodo()),
                "Resultado de las cuentas 4–7",
                resumen.utilidadPeriodo().signum() < 0 ? "negative" : "positive"), 1, 1);
        bento.add(cifraFinanciera("Cartera", dinero(resumen.carteraClientes()),
                resumen.documentosPendientesCobro() + " DTE(s) por "
                        + dinero(resumen.dtesPendientes()),
                resumen.documentosPendientesCobro() > 0 ? "attention" : "neutral"), 2, 1);
        bento.add(porHacer(cuentas, borradores, estadoCatalogo, acciones, resumen), 0, 2);
        // El período ya se muestra en la cabecera. Liberar esa tercera tarjeta
        // evita truncar los nombres y montos de clientes en ventanas normales.
        bento.add(graficaClientes(resumen), 1, 2, 2, 1);
        bento.add(tablaOrdenes(resumen), 0, 3, 3, 1);
        bento.add(costos(resumen), 0, 4, 3, 1);
        // Las dos dispersiones comparten la fila a partes iguales y crecen
        // con el espacio disponible.
        HBox dispersores = new HBox(16, graficaDispercion(resumen.serieMensual()),
                graficaCostos());
        dispersores.setAlignment(Pos.TOP_LEFT);
        dispersores.setFillHeight(false);
        HBox.setHgrow(dispersores.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(dispersores.getChildren().get(1), Priority.ALWAYS);
        bento.add(dispersores, 0, 5, 3, 1);
        bento.add(tablaEstructuraCostos(), 0, 6, 3, 1);

        getChildren().addAll(cabecera, bento);
    }

    private PanelInicioService.ResumenPanel cargarResumen(Periodo periodo) {
        try {
            return context.panel().resumen(periodo);
        } catch (Exception ex) {
            // Sin traza aquí un fallo del panel se vería como tableros en cero;
            // el log local permite diagnosticar qué consulta falló.
            sv.edu.ues.colibri.desktop.AppLog.error(
                    "No se pudo calcular el resumen del panel de inicio", ex);
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

    /** Tile KPI del Bento: cifra con su nota, presentada como tarjeta. */
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
        bloque.setAlignment(Pos.CENTER_LEFT);
        bloque.getStyleClass().addAll("financial-metric", "surface-card");
        return bloque;
    }

    private Label encabezado(String texto) {
        Label h = new Label(texto);
        h.getStyleClass().add("section-heading");
        return h;
    }

    /** Tile "Por hacer": catálogo, borradores, alertas y acciones del período. */
    private VBox porHacer(long cuentas, java.util.List<Partida> borradores,
                          Label estadoCatalogo, HBox acciones,
                          PanelInicioService.ResumenPanel resumen) {
        VBox tile = new VBox(12);
        tile.getStyleClass().addAll("surface-card", "por-hacer");
        tile.getChildren().add(encabezado("Por hacer"));
        tile.getChildren().add(filaOperativa("Catálogo de cuentas",
                cuentas + " cuentas configuradas", estadoCatalogo));
        tile.getChildren().add(filaOperativa("Borradores", borradores.isEmpty()
                ? "Sin partidas por revisar" : borradores.size() + " pendiente(s) de revisión",
                detalle(borradores.isEmpty() ? "Al día" : "Atención requerida",
                        borradores.isEmpty() ? "status-pill" : "warning-pill")));
        if (resumen.alertas().isEmpty()) {
            tile.getChildren().add(detalle("Sin alertas operativas.", "status-pill"));
        } else {
            // Máximo tres alertas visibles para no desbordar el tile;
            // el resto queda contado en una nota final.
            int mostradas = 0;
            for (PanelInicioService.Alerta alerta : resumen.alertas()) {
                if (mostradas++ == 3) {
                    break;
                }
                String estilo = "ALTA".equals(alerta.severidad()) || "AVISO".equals(alerta.severidad())
                        ? "warning-pill" : "status-pill";
                tile.getChildren().add(detalle("· " + alerta.mensaje(), estilo));
            }
            int restantes = resumen.alertas().size() - mostradas;
            if (restantes > 0) {
                tile.getChildren().add(detalle("…y " + restantes
                        + " alerta(s) más en Registros.", "status-pill"));
            }
        }
        tile.getChildren().add(acciones);
        VBox.setVgrow(tile, Priority.ALWAYS);
        return tile;
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

    /**
     * Muestra la tendencia reciente sin dejar que un cierre extraordinario
     * aplaste visualmente los demás meses. El importe excepcional permanece
     * visible como hito y en el detalle numérico, nunca se omite ni altera.
     * Su propósito es responder «¿los meses mostrados cerraron en superávit o
     * en déficit?»: la lectura analítica encabeza el gráfico antes de las barras.
     */
    /** Centra una tarjeta de gráfica dentro de su celda del bento. */
    private javafx.scene.Node centrarEnGrilla(VBox card, double maxAncho) {
        card.setMaxWidth(maxAncho);
        HBox envoltura = new HBox(card);
        envoltura.setAlignment(Pos.CENTER);
        return envoltura;
    }

    /**
     * Gráfica de barras con selectores de rango «desde año X hasta año Y»:
     * al cambiar cualquiera de los dos combos se vuelve a consultar la serie
     * mensual del rango completo y se repinta la tarjeta.
     */
    private VBox graficaBarras(Periodo periodo) {
        Label heading = new Label("Ingresos vs. egresos · superávit o déficit por mes");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");

        int anioHasta = periodo != null ? periodo.getFechaFin().getYear()
                : LocalDate.now().getYear();
        int anioDesde = periodo != null ? periodo.getFechaInicio().minusMonths(5).getYear()
                : anioHasta;
        List<Integer> anios = aniosGrafica(anioDesde, anioHasta);
        ComboBox<Integer> desde = comboAnios(anios, anioDesde);
        ComboBox<Integer> hasta = comboAnios(anios, anioHasta);
        HBox rango = new HBox(8, new Label("Desde"), desde, new Label("hasta"), hasta);
        rango.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(rango);

        VBox contenido = new VBox(8);
        contenido.setFillWidth(true);
        card.getChildren().add(contenido);

        // Si el usuario invierte el rango se intercambian los valores para
        // que la consulta siempre sea válida; la bandera evita recursión.
        final boolean[] ajustando = {false};
        Runnable repintar = () -> {
            if (ajustando[0]) {
                return;
            }
            int d = desde.getValue() == null ? anioHasta : desde.getValue();
            int h = hasta.getValue() == null ? anioHasta : hasta.getValue();
            if (d > h) {
                ajustando[0] = true;
                desde.setValue(h);
                hasta.setValue(d);
                ajustando[0] = false;
            }
            pintarBarras(contenido, Math.min(d, h), Math.max(d, h));
        };
        desde.valueProperty().addListener((o, a, b) -> repintar.run());
        hasta.valueProperty().addListener((o, a, b) -> repintar.run());
        repintar.run();
        return card;
    }

    /** Años disponibles para los selectores: los que tienen movimientos más el rango por defecto. */
    private List<Integer> aniosGrafica(int defectoDesde, int defectoHasta) {
        TreeSet<Integer> anios;
        try {
            anios = new TreeSet<>(context.panel().aniosConMovimientos());
        } catch (Exception ex) {
            sv.edu.ues.colibri.desktop.AppLog.error(
                    "No se pudieron listar los años con movimientos", ex);
            anios = new TreeSet<>();
        }
        anios.add(defectoDesde);
        anios.add(defectoHasta);
        return List.copyOf(anios);
    }

    private ComboBox<Integer> comboAnios(List<Integer> anios, int valor) {
        ComboBox<Integer> combo = new ComboBox<>(FXCollections.observableArrayList(anios));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer anio) {
                return anio == null ? "" : String.valueOf(anio);
            }

            @Override
            public Integer fromString(String texto) {
                try {
                    return Integer.valueOf(texto.trim());
                } catch (RuntimeException ex) {
                    return null;
                }
            }
        });
        combo.setValue(valor);
        combo.setMinWidth(90);
        UiCatalogos.ayuda(combo, "Año del rango de comparación de la gráfica.");
        return combo;
    }

    /**
     * Ancho del combo según su opción más larga, más el espacio de la flecha
     * y los paddings del control, para que la selección no se trunque.
     */
    private static double anchoCombo(List<String> opciones) {
        Font letra = Font.font("Segoe UI", 14);
        double texto = 0;
        for (String opcion : opciones) {
            javafx.scene.text.Text medida = new javafx.scene.text.Text(opcion);
            medida.setFont(letra);
            texto = Math.max(texto, medida.getBoundsInLocal().getWidth());
        }
        return texto + 72;
    }

    /** Reconstruye el contenido de la gráfica de barras para el rango elegido. */
    private void pintarBarras(VBox contenido, int anioDesde, int anioHasta) {
        contenido.getChildren().clear();
        List<PanelInicioService.PuntoMes> serie;
        try {
            serie = context.panel().serieMensualEntre(LocalDate.of(anioDesde, 1, 1),
                    LocalDate.of(anioHasta, 12, 31));
        } catch (Exception ex) {
            sv.edu.ues.colibri.desktop.AppLog.error(
                    "No se pudo cargar la serie mensual del rango elegido", ex);
            serie = List.of();
        }
        boolean sinDatos = serie.isEmpty() || serie.stream().allMatch(p ->
                p.ingresos().signum() == 0 && p.egresos().signum() == 0);
        if (sinDatos) {
            contenido.getChildren().add(detalle(
                    "Aún no hay partidas contabilizadas en el rango elegido.", "status-pill"));
            return;
        }
        contenido.getChildren().add(lecturaTendencia(serie));
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
        chart.setMinHeight(220);
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

        // El diagrama ocupa ~55 % del ancho y el historial mensual ~45 %: las
        // fichas por mes necesitan ese espacio para no envolver cada cifra en
        // tres líneas, y el detalle mensual vive en un panel con scroll para
        // que un rango de varios años no estire la tarjeta.
        GridPane cuerpo = new GridPane();
        cuerpo.setHgap(16);
        ColumnConstraints colGrafica = new ColumnConstraints();
        colGrafica.setPercentWidth(55);
        ColumnConstraints colMeses = new ColumnConstraints();
        colMeses.setPercentWidth(45);
        cuerpo.getColumnConstraints().addAll(colGrafica, colMeses);
        VBox izquierda = new VBox(8);
        izquierda.setFillWidth(true);
        izquierda.getChildren().add(chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        if (serieVisible.hito() != null) {
            PanelInicioService.PuntoMes hito = serieVisible.hito();
            Label aviso = new Label("Hito " + hito.etiqueta() + ": ingresos "
                    + dinero(hito.ingresos()) + " y egresos " + dinero(hito.egresos())
                    + ". Se muestra aparte para conservar legible la tendencia.");
            aviso.setWrapText(true);
            aviso.getStyleClass().add("chart-outlier-note");
            izquierda.getChildren().add(aviso);
        }
        ScrollPane mensual = new ScrollPane(resumenMensual(serie));
        mensual.setFitToWidth(true);
        mensual.setPrefHeight(310);
        mensual.setMinHeight(220);
        mensual.getStyleClass().add("chart-months-pane");
        cuerpo.add(izquierda, 0, 0);
        cuerpo.add(mensual, 1, 0);
        GridPane.setHgrow(izquierda, Priority.ALWAYS);
        GridPane.setHgrow(mensual, Priority.ALWAYS);
        contenido.getChildren().add(cuerpo);
    }

    /** Ranking de clientes del período más la actividad comercial resumida. */
    private VBox graficaClientes(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Clientes principales · período");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card", "customer-ranking");
        List<PanelInicioService.ClienteTop> clientes = resumen.clientesTop();
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
        PanelInicioService.ProductoTop producto = resumen.productoTop();
        card.getChildren().add(resumenComercial("Producto más vendido",
                producto == null ? "Aún no hay ventas registradas" : producto.nombre(),
                producto == null ? "Las licencias entregadas aparecerán aquí."
                        : producto.unidades() + " unidades · " + dinero(producto.ingresos())));
        card.getChildren().add(resumenComercial("Usuarios SaaS",
                String.valueOf(resumen.usuariosSaasMes()),
                "usuarios-mes facturables en el período"));
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

    private VBox resumenMensual(List<PanelInicioService.PuntoMes> serie) {
        VBox detalle = new VBox(8);
        detalle.getStyleClass().add("chart-monthly-summary");
        for (PanelInicioService.PuntoMes punto : serie) {
            BigDecimal resultado = punto.ingresos().subtract(punto.egresos());
            String textoCompleto = punto.etiqueta() + "  ·  Ingresos: " + dinero(punto.ingresos())
                    + "  ·  Egresos: " + dinero(punto.egresos()) + "  ·  Resultado: "
                    + dinero(resultado);
            Label mes = new Label(textoCompleto);
            // El signo del resultado tiñe la ficha: superávit, déficit o punto
            // de equilibrio se distinguen sin leer las cifras una por una.
            mes.getStyleClass().add("chart-monthly-item");
            if (resultado.signum() > 0) {
                mes.getStyleClass().add("chart-monthly-item--superavit");
            } else if (resultado.signum() < 0) {
                mes.getStyleClass().add("chart-monthly-item--deficit");
            }
            mes.setWrapText(true);
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

    /** Contenido del tile de costos: tarifas por plan y comparativo SaaS/CIF. */
    private VBox costos(PanelInicioService.ResumenPanel resumen) {
        VBox tile = new VBox(10);
        tile.getStyleClass().addAll("surface-card", "costos-tile");
        tile.getChildren().add(encabezado("Costos y suscripciones"));
        tile.getChildren().add(contenidoPlanes(resumen));
        VBox indicadoresCostos = new VBox(6);
        indicadoresCostos.getStyleClass().add("cost-summary-list");
        boolean haySaasOCif = false;
        for (PanelInicioService.SaasPlan plan : resumen.saasPorPlan()) {
            indicadoresCostos.getChildren().add(filaCosto(plan.plan(), plan.usuariosMes()
                    + " usuario(s)-mes", "Uso del período"));
            haySaasOCif = true;
        }
        for (PanelInicioService.CifComparativo cif : resumen.cif()) {
            indicadoresCostos.getChildren().add(filaCosto("CIF " + cif.centro(),
                    "Aplicado " + dinero(cif.aplicado()) + " · Presupuesto "
                            + dinero(cif.presupuestado()),
                    "Variación " + dinero(cif.variacion())));
            haySaasOCif = true;
        }
        if (!haySaasOCif) {
            indicadoresCostos.getChildren().add(detalle("Sin datos de SaaS o CIF para el período.", "status-pill"));
        }
        tile.getChildren().add(indicadoresCostos);
        VBox.setVgrow(tile, Priority.ALWAYS);
        return tile;
    }

    /** Tabla de tarifas vigentes por plan, o estado vacío. */
    private javafx.scene.Node contenidoPlanes(PanelInicioService.ResumenPanel resumen) {
        if (resumen.planes().isEmpty()) {
            return estadoVacio("No hay planes vigentes para mostrar.",
                    "Cree o active un plan desde Inventario → Servicios / Accesos.");
        }
        TableView<PanelInicioService.PlanTarifa> tabla = new TableView<>();
        // El resumen es una tabla de consulta, no una banda de datos: cada
        // fila reserva espacio suficiente para leer código, plan y tarifa.
        tabla.setFixedCellSize(48);
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
        tabla.getColumns().get(0).setPrefWidth(180);
        tabla.getColumns().get(1).setPrefWidth(340);
        tabla.getColumns().get(2).setPrefWidth(180);
        tarifa.setPrefWidth(140);
        facturacion.setPrefWidth(190);
        tabla.getItems().setAll(resumen.planes());
        tabla.setMinHeight(180);
        tabla.setPrefHeight(altoTabla(resumen.planes().size()));
        return UiTables.conBusqueda(tabla, "planes y tarifas", plan -> plan.codigo() + " "
                + plan.nombre() + " " + plan.modalidad() + " " + plan.tipoFacturacion());
    }

    /**
     * Tarjeta dedicada a las órdenes de servicio: presupuesto contra costo real.
     * Antes vivía dentro del resumen de costos y suscripciones, donde quedaba
     * sepultada al final de una tarjeta con dos tablas más.
     */
    private VBox tablaOrdenes(PanelInicioService.ResumenPanel resumen) {
        Label heading = new Label("Órdenes de servicio · presupuesto vs. costo real");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");
        List<PanelInicioService.OrdenComparativo> ordenes = resumen.ordenes();
        if (ordenes.isEmpty()) {
            card.getChildren().add(estadoVacio("Sin órdenes de servicio registradas.",
                    "Las órdenes se crean en Inventario → Costos → Medida."));
            return card;
        }
        long enCurso = ordenes.stream()
                .filter(o -> "ABIERTA".equals(o.estado()) || "EN_PROCESO".equals(o.estado()))
                .count();
        Label resumenOrdenes = new Label(ordenes.size() + " orden(es) · " + enCurso
                + " en curso · " + (ordenes.size() - enCurso) + " finalizada(s)");
        resumenOrdenes.getStyleClass().add("customer-total");
        card.getChildren().add(resumenOrdenes);
        card.getChildren().add(tablaOrdenesInterior(ordenes));
        return card;
    }

    private javafx.scene.Node tablaOrdenesInterior(
            List<PanelInicioService.OrdenComparativo> ordenes) {
        TableView<PanelInicioService.OrdenComparativo> tabla = new TableView<>();
        tabla.setPlaceholder(new Label("Sin órdenes de servicio registradas."));
        tabla.getStyleClass().add("dashboard-data-table");
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
                c.getValue().presupuesto().signum() == 0 ? "—"
                        : c.getValue().variacionPct() + "%"));
        variacion.setMaxWidth(90);
        tabla.getColumns().addAll(java.util.List.of(presupuesto, real, variacion));
        agregarColumna(tabla, "Estado", PanelInicioService.OrdenComparativo::estado);
        tabla.getItems().setAll(ordenes);
        tabla.setPrefHeight(Math.max(150, Math.min(280, 52 + ordenes.size() * 44)));
        return UiTables.conBusqueda(tabla, "órdenes de servicio", orden -> orden.codigo() + " "
                + orden.descripcion() + " " + orden.estado() + " " + orden.presupuesto()
                + " " + orden.costoReal());
    }


    /**
     * Lectura gerencial de la tendencia: resultado acumulado del rango y conteo
     * de meses por signo. Responde de un vistazo la pregunta del gráfico.
     */
    private Label lecturaTendencia(List<PanelInicioService.PuntoMes> serie) {
        BigDecimal resultado = serie.stream()
                .map(p -> p.ingresos().subtract(p.egresos()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long superavit = serie.stream()
                .filter(p -> p.ingresos().subtract(p.egresos()).signum() > 0).count();
        long deficit = serie.stream()
                .filter(p -> p.ingresos().subtract(p.egresos()).signum() < 0).count();
        long equilibrio = serie.size() - superavit - deficit;
        String texto = "Resultado acumulado " + dinero(resultado) + " · " + superavit
                + " superávit · " + deficit + " déficit · " + equilibrio + " en equilibrio";
        return detalle(texto, resultado.signum() < 0 ? "warning-pill" : "status-pill");
    }

    /** Zona de la dispersión según la razón egresos/ingresos del mes. */
    private enum Zona { SUPERAVIT, TOLERADO, DEFICIT }

    /** Fracción de tolerancia alrededor de la diagonal de equilibrio. */
    private static final double TOLERANCIA_DISPERSION = 0.10;

    /**
     * Espejo de tokens.css para el lienzo: Canvas no consume variables CSS,
     * así que la paleta se elige según el tema activo y se repinta al cambiar.
     */
    private record Paleta(Color eje, Color marco, Color grid, Color diagonal,
                          Color banda, Color bandaLinea, Color exito, Color neutro,
                          Color fallo, Color texto) {
        static final Paleta OSCURO = new Paleta(
                Color.web("#8FA7BE"), Color.web("#5F7D9A"), Color.web("#8FA7BE", 0.14),
                Color.web("#A594FF"), Color.web("#FFB84D", 0.10), Color.web("#FFB84D", 0.60),
                Color.web("#C5F200"), Color.web("#00C2FF"), Color.web("#FF7A8C"),
                Color.web("#8FA7BE"));
        static final Paleta CLARO = new Paleta(
                Color.web("#475569"), Color.web("#94A3B8"), Color.web("#0F172A", 0.10),
                Color.web("#5B4FC4"), Color.web("#865900", 0.10), Color.web("#865900", 0.55),
                Color.web("#157A4E"), Color.web("#0077A3"), Color.web("#D1435B"),
                Color.web("#475569"));
    }

    /**
     * Dispersión mensual ingresos (X) vs. egresos (Y). La diagonal y = x marca
     * el punto de equilibrio y la banda ±10 % la tolerancia aceptable: cada
     * punto es un mes y su color indica si cerró en superávit, dentro de
     * tolerancia o en déficit. Se dibuja en Canvas porque JavaFX no permite
     * líneas de referencia ni bandas sobre un ScatterChart.
     */
    private VBox graficaDispercion(List<PanelInicioService.PuntoMes> serie) {
        Label heading = new Label("Dispersión · ingresos vs. egresos por mes");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");
        if (serie.isEmpty()) {
            card.getChildren().add(detalle("Aún no hay partidas contabilizadas en los últimos meses.",
                    "status-pill"));
            return card;
        }
        // Canvas no es redimensionable por el layout: se envuelve en un
        // StackPane que le presta su geometría y liga el búfer al tamaño real.
        // El alto sigue al ancho con tope: el lienzo puede ser más ancho que
        // alto porque el área de datos interna sigue siendo un cuadrado
        // centrado, así la diagonal de equilibrio sale a 45° sin desbordar
        // la tarjeta en pantallas anchas.
        javafx.scene.layout.StackPane area = new javafx.scene.layout.StackPane();
        area.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(area.getWidth(), 440), area.widthProperty()));
        area.setMinHeight(240);
        Canvas lienzo = new Canvas();
        area.getChildren().add(lienzo);
        lienzo.widthProperty().bind(area.widthProperty());
        lienzo.heightProperty().bind(area.heightProperty());
        area.getStyleClass().add("dashboard-scatter");
        card.getChildren().add(area);
        card.getChildren().add(leyendaDispercion());
        card.getChildren().add(resumenDispercion(serie));
        List<PuntoTrazado> trazados = new ArrayList<>();
        Runnable repintar = () -> repintarDispercion(lienzo, aPuntosDispercion(serie),
                "Ingresos (USD)", "Egresos (USD)", trazados);
        lienzo.widthProperty().addListener((o, a, b) -> repintar.run());
        lienzo.heightProperty().addListener((o, a, b) -> repintar.run());
        // El tema puede cambiar con el lienzo ya montado: se repinta cuando la
        // raíz de la escena alterna sus clases de tema.
        lienzo.sceneProperty().addListener((o, a, s) -> {
            if (s != null && s.getRoot() != null) {
                s.getRoot().getStyleClass().addListener(
                        (javafx.beans.InvalidationListener) obs -> {
                            if (lienzo.getScene() != null) {
                                repintar.run();
                            }
                        });
            }
        });
        activarTooltipsDispercion(lienzo, trazados);
        repintar.run();
        return card;
    }

    private javafx.scene.Node leyendaDispercion() {
        FlowPane leyenda = new FlowPane(16, 6);
        leyenda.getStyleClass().add("scatter-leyenda");
        leyenda.getChildren().addAll(
                itemLeyenda("linea", "Equilibrio y = x"),
                itemLeyenda("banda", "Tolerancia ±10 %"),
                itemLeyenda("exito", "Superávit"),
                itemLeyenda("neutro", "Dentro de tolerancia"),
                itemLeyenda("fallo", "Déficit"));
        return leyenda;
    }

    private HBox itemLeyenda(String tono, String texto) {
        boolean linea = "linea".equals(tono) || "banda".equals(tono);
        Rectangle muestra = new Rectangle(linea ? 16 : 11, linea ? ("banda".equals(tono) ? 6 : 3) : 11);
        muestra.setArcWidth(linea ? 2 : 5.5);
        muestra.setArcHeight(linea ? 2 : 5.5);
        muestra.getStyleClass().add("scatter-sw--" + tono);
        Label l = new Label(texto);
        l.getStyleClass().add("scatter-leyenda-texto");
        HBox item = new HBox(6, muestra, l);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("scatter-leyenda-item");
        return item;
    }

    /** Conteo interpretativo de meses por zona para el pie de la gráfica. */
    private Label resumenDispercion(List<PanelInicioService.PuntoMes> serie) {
        long superavit = serie.stream().filter(p -> zonaDispercion(p) == Zona.SUPERAVIT).count();
        long tolerado = serie.stream().filter(p -> zonaDispercion(p) == Zona.TOLERADO).count();
        long deficit = serie.stream().filter(p -> zonaDispercion(p) == Zona.DEFICIT).count();
        String texto = superavit + " mes(es) superávit · " + tolerado
                + " dentro de tolerancia · " + deficit + " con egresos desbordados";
        return detalle(texto, deficit > 0 ? "warning-pill" : "status-pill");
    }

    private Zona zonaDispercion(PanelInicioService.PuntoMes punto) {
        double ingresos = punto.ingresos().doubleValue();
        double egresos = punto.egresos().doubleValue();
        if (ingresos <= 0) {
            return egresos > 0 ? Zona.DEFICIT : Zona.TOLERADO;
        }
        double razon = egresos / ingresos;
        if (razon < 1 - TOLERANCIA_DISPERSION) {
            return Zona.SUPERAVIT;
        }
        if (razon > 1 + TOLERANCIA_DISPERSION) {
            return Zona.DEFICIT;
        }
        return Zona.TOLERADO;
    }

    /**
     * Zona de la dispersión de costos según la razón costo real/presupuesto:
     * bajo presupuesto es favorable (ahorro), sobrecosto es desfavorable.
     */
    private Zona zonaCostos(InventarioCostoService.PuntoCostoPresupuesto punto) {
        double presupuesto = punto.presupuesto().doubleValue();
        double real = punto.costoReal().doubleValue();
        if (presupuesto <= 0) {
            return real > 0 ? Zona.DEFICIT : Zona.TOLERADO;
        }
        double razon = real / presupuesto;
        if (razon < 1 - TOLERANCIA_DISPERSION) {
            return Zona.SUPERAVIT;
        }
        if (razon > 1 + TOLERANCIA_DISPERSION) {
            return Zona.DEFICIT;
        }
        return Zona.TOLERADO;
    }

    /**
     * Dispersión «presupuesto vs. costo real»: un punto por proyecto, lote de
     * licencias o plan SaaS según la línea elegida en el selector. Misma
     * lectura que la dispersión de ingresos: la diagonal y = x es el costo
     * exactamente presupuestado y la banda ±10 % la tolerancia aceptable.
     */
    private VBox graficaCostos() {
        Label heading = new Label("Dispersión · costos: presupuesto vs. costo real");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");
        card.setMinWidth(320);

        List<String> lineasCosto = List.of("Proyectos a la medida", "Licencias digitales",
                "Acceso SaaS");
        ComboBox<String> linea = new ComboBox<>(FXCollections.observableArrayList(lineasCosto));
        linea.setValue("Proyectos a la medida");
        double anchoLinea = anchoCombo(lineasCosto);
        linea.setPrefWidth(anchoLinea);
        linea.setMinWidth(anchoLinea);
        // El ComboBox de JavaFX suma la altura de su flecha interna y queda
        // más alto que un TextField; se fija la altura estándar de control.
        linea.setPrefHeight(34);
        UiCatalogos.ayuda(linea, "Línea de costo a comparar: proyecto a la medida, "
                + "licencias digitales o acceso SaaS.");
        HBox selector = new HBox(8, new Label("Ver costos de"), linea);
        selector.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(selector);

        Label vacio = detalle("No hay objetos con datos de costo en esta línea.",
                "status-pill");
        vacio.setVisible(false);
        vacio.setManaged(false);

        // Mismo lienzo acotado que la dispersión de ingresos/egresos: el
        // área de datos interna es un cuadrado centrado, así la diagonal
        // presupuesto = costo real sale a 45° sin desbordar la tarjeta.
        javafx.scene.layout.StackPane area = new javafx.scene.layout.StackPane();
        area.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(area.getWidth(), 440), area.widthProperty()));
        area.setMinHeight(240);
        Canvas lienzo = new Canvas();
        area.getChildren().add(lienzo);
        lienzo.widthProperty().bind(area.widthProperty());
        lienzo.heightProperty().bind(area.heightProperty());
        area.getStyleClass().add("dashboard-scatter");

        FlowPane leyenda = leyendaCostos();
        Label resumen = detalle("", "status-pill");
        card.getChildren().addAll(vacio, area, leyenda, resumen);

        AtomicReference<List<InventarioCostoService.PuntoCostoPresupuesto>> puntos =
                new AtomicReference<>(List.of());
        List<PuntoTrazado> trazados = new ArrayList<>();
        Runnable repintar = () -> repintarDispercion(lienzo, aPuntosCosto(puntos.get()),
                "Presupuesto (USD)", "Costo real (USD)", trazados);
        lienzo.widthProperty().addListener((o, a, b) -> repintar.run());
        lienzo.heightProperty().addListener((o, a, b) -> repintar.run());
        lienzo.sceneProperty().addListener((o, a, s) -> {
            if (s != null && s.getRoot() != null) {
                s.getRoot().getStyleClass().addListener(
                        (javafx.beans.InvalidationListener) obs -> {
                            if (lienzo.getScene() != null) {
                                repintar.run();
                            }
                        });
            }
        });
        activarTooltipsDispercion(lienzo, trazados);

        Runnable cargar = () -> {
            List<InventarioCostoService.PuntoCostoPresupuesto> datos =
                    cargarCostos(linea.getValue());
            puntos.set(datos);
            boolean hay = !datos.isEmpty();
            vacio.setVisible(!hay);
            vacio.setManaged(!hay);
            area.setVisible(hay);
            area.setManaged(hay);
            leyenda.setVisible(hay);
            leyenda.setManaged(hay);
            resumen.setText(resumenCostos(datos));
            resumen.getStyleClass().setAll(haySobrecosto(datos)
                    ? "warning-pill" : "status-pill");
            resumen.setVisible(hay);
            resumen.setManaged(hay);
            repintar.run();
        };
        linea.valueProperty().addListener((o, a, b) -> cargar.run());
        cargar.run();
        return card;
    }

    private FlowPane leyendaCostos() {
        FlowPane leyenda = new FlowPane(16, 6);
        leyenda.getStyleClass().add("scatter-leyenda");
        leyenda.getChildren().addAll(
                itemLeyenda("linea", "Presupuesto exacto y = x"),
                itemLeyenda("banda", "Tolerancia ±10 %"),
                itemLeyenda("exito", "Bajo presupuesto"),
                itemLeyenda("neutro", "En tolerancia"),
                itemLeyenda("fallo", "Sobrecosto"));
        return leyenda;
    }

    /** Consulta los puntos presupuesto vs. costo real de la línea elegida. */
    private List<InventarioCostoService.PuntoCostoPresupuesto> cargarCostos(String etiqueta) {
        String linea = switch (etiqueta) {
            case "Licencias digitales" -> "LICENCIA";
            case "Acceso SaaS" -> "ACCESO";
            default -> "PROYECTO";
        };
        try {
            return context.inventario().presupuestoVsCostoReal(linea);
        } catch (Exception ex) {
            sv.edu.ues.colibri.desktop.AppLog.error(
                    "No se pudieron cargar los costos de " + linea, ex);
            return List.of();
        }
    }

    /** Conteo interpretativo de objetos por zona para el pie de la gráfica. */
    private String resumenCostos(List<InventarioCostoService.PuntoCostoPresupuesto> puntos) {
        long ahorro = puntos.stream().filter(p -> zonaCostos(p) == Zona.SUPERAVIT).count();
        long tolerado = puntos.stream().filter(p -> zonaCostos(p) == Zona.TOLERADO).count();
        long sobre = puntos.stream().filter(p -> zonaCostos(p) == Zona.DEFICIT).count();
        List<BigDecimal> variaciones = puntos.stream()
                .filter(p -> p.presupuesto().signum() > 0)
                .map(p -> p.costoReal().subtract(p.presupuesto())
                        .divide(p.presupuesto(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP))
                .toList();
        BigDecimal media = variaciones.isEmpty() ? BigDecimal.ZERO
                : variaciones.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(variaciones.size()), 1, RoundingMode.HALF_UP);
        return ahorro + " bajo presupuesto · " + tolerado + " en tolerancia · " + sobre
                + " con sobrecosto · variación media " + media + "%";
    }

    private boolean haySobrecosto(List<InventarioCostoService.PuntoCostoPresupuesto> puntos) {
        return puntos.stream().anyMatch(p -> zonaCostos(p) == Zona.DEFICIT);
    }

    /**
     * Tabla de estructura de costos: una fila por proyecto, lote de licencias
     * o plan SaaS con su precio unitario y el costo desagregado por rubro.
     */
    private VBox tablaEstructuraCostos() {
        Label heading = new Label("Estructura de costos · precio unitario vs. costo por objeto");
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(8, heading);
        card.getStyleClass().addAll("surface-card", "dashboard-chart-card");
        Label nota = detalle("Precio unitario y costo por rubro de cada proyecto, licencia "
                + "y acceso. En licencias el costo del lote se reconoce íntegro al liberarse "
                + "(sin desglose MOD/CIF); en accesos SaaS el costo es el asignado por "
                + "usuario-mes.", "section-note");
        card.getChildren().add(nota);
        List<InventarioCostoService.EstructuraCostoInfo> filas = cargarEstructura();
        if (filas.isEmpty()) {
            card.getChildren().add(estadoVacio("Sin objetos de costo registrados.",
                    "Los proyectos, licencias y accesos se crean en Costos e Inventario."));
            return card;
        }
        TableView<InventarioCostoService.EstructuraCostoInfo> tabla = new TableView<>();
        tabla.setPlaceholder(new Label("Sin objetos de costo para mostrar."));
        tabla.getStyleClass().add("dashboard-data-table");
        agregarColumna(tabla, "Objeto", InventarioCostoService.EstructuraCostoInfo::codigo);
        agregarColumna(tabla, "Detalle", InventarioCostoService.EstructuraCostoInfo::detalle);
        TableColumn<InventarioCostoService.EstructuraCostoInfo, String> tipo =
                new TableColumn<>("Tipo");
        tipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                switch (c.getValue().tipo()) {
                    case "PROYECTO" -> "Proyecto";
                    case "LICENCIA" -> "Licencia";
                    default -> "Acceso";
                }));
        tabla.getColumns().add(tipo);
        agregarColumnaDinero(tabla, "Precio unitario",
                InventarioCostoService.EstructuraCostoInfo::precioUnitario);
        agregarColumnaDinero(tabla, "Materia prima",
                InventarioCostoService.EstructuraCostoInfo::materiaPrima);
        agregarColumnaDinero(tabla, "Mano de obra",
                InventarioCostoService.EstructuraCostoInfo::manoObra);
        agregarColumnaDinero(tabla, "CIF",
                InventarioCostoService.EstructuraCostoInfo::cif);
        agregarColumnaDinero(tabla, "Costo total",
                InventarioCostoService.EstructuraCostoInfo::costoTotal);
        tabla.getItems().setAll(filas);
        tabla.setPrefHeight(altoTabla(filas.size()));
        card.getChildren().add(UiTables.conBusqueda(tabla, "objetos de costo",
                f -> f.codigo() + " " + f.detalle() + " " + f.tipo()));
        return card;
    }

    private List<InventarioCostoService.EstructuraCostoInfo> cargarEstructura() {
        try {
            return context.inventario().estructuraCostos();
        } catch (Exception ex) {
            sv.edu.ues.colibri.desktop.AppLog.error(
                    "No se pudo cargar la estructura de costos", ex);
            return List.of();
        }
    }

    /** Columna monetaria: «—» cuando el objeto aún no tiene precio registrado. */
    private <T> void agregarColumnaDinero(TableView<T> tabla, String titulo,
            Function<T, BigDecimal> valor) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> {
            BigDecimal monto = valor.apply(c.getValue());
            return new javafx.beans.property.SimpleStringProperty(
                    monto == null ? "—" : dinero(monto));
        });
        tabla.getColumns().add(columna);
    }

    /** Punto genérico de dispersión: coordenadas, zona, rótulo y detalle para tooltip. */
    private record PuntoDispercion(double x, double y, Zona zona, String rotulo,
            String detalle) {
    }

    /** Posición en pantalla de un punto trazado, para detectar el hover. */
    private record PuntoTrazado(PuntoDispercion punto, double px, double py) {
    }

    private List<PuntoDispercion> aPuntosDispercion(List<PanelInicioService.PuntoMes> serie) {
        boolean multiAnio = serie.stream().map(PanelInicioService.PuntoMes::anio)
                .distinct().count() > 1;
        List<PuntoDispercion> puntos = new ArrayList<>();
        for (PanelInicioService.PuntoMes punto : serie) {
            String rotulo = mesCorto(punto.mes())
                    + (multiAnio ? " " + (punto.anio() % 100) : "");
            String detalle = mes(punto.mes()) + " " + punto.anio()
                    + " · Ingresos " + dinero(punto.ingresos())
                    + " · Egresos " + dinero(punto.egresos());
            puntos.add(new PuntoDispercion(punto.ingresos().doubleValue(),
                    punto.egresos().doubleValue(), zonaDispercion(punto), rotulo, detalle));
        }
        return puntos;
    }

    private List<PuntoDispercion> aPuntosCosto(
            List<InventarioCostoService.PuntoCostoPresupuesto> puntos) {
        return puntos.stream().map(p -> {
            String detalle = p.etiqueta() + " · Presupuesto " + dinero(p.presupuesto())
                    + " · Costo real " + dinero(p.costoReal());
            if (p.presupuesto().signum() > 0) {
                BigDecimal variacion = p.costoReal().subtract(p.presupuesto())
                        .divide(p.presupuesto(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
                detalle += " · Variación " + variacion + "%";
            }
            return new PuntoDispercion(p.presupuesto().doubleValue(),
                    p.costoReal().doubleValue(), zonaCostos(p), p.etiqueta(), detalle);
        }).toList();
    }

    private void repintarDispercion(Canvas lienzo, List<PuntoDispercion> puntos,
            String rotuloX, String rotuloY, List<PuntoTrazado> trazados) {
        trazados.clear();
        double w = lienzo.getWidth();
        double h = lienzo.getHeight();
        if (w < 80 || h < 80) {
            return;
        }
        GraphicsContext g = lienzo.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        Paleta p = UiTheme.temaOscuro() ? Paleta.OSCURO : Paleta.CLARO;

        double izq = 58, der = 18, sup = 30, inf = 40;
        double pw = w - izq - der;
        double ph = h - sup - inf;
        double lado = Math.min(pw, ph);
        if (lado <= 30) {
            return;
        }
        // El área de trazado es un cuadrado centrado: con la misma escala en
        // X e Y, la diagonal de equilibrio se dibuja a 45° y la banda de
        // tolerancia queda simétrica aunque el lienzo no sea cuadrado.
        double ox = izq + (pw - lado) / 2;
        double oy = sup + (ph - lado) / 2;

        double maxV = 0;
        for (PuntoDispercion punto : puntos) {
            maxV = Math.max(maxV, Math.max(punto.x(), punto.y()));
        }
        double bruto = techoLindo(maxV * 1.10);
        final double techo = bruto <= 0 ? 1 : bruto;

        // Escalas idénticas en X e Y: un dólar ocupa el mismo ancho en ambos
        // ejes, así la diagonal y = x es la referencia visual verdadera.
        java.util.function.DoubleUnaryOperator aX = v -> ox + (v / techo) * lado;
        java.util.function.DoubleUnaryOperator aY = v -> oy + lado - (v / techo) * lado;

        // Rejilla y marcas redondas.
        g.setFont(Font.font(11));
        double paso = pasoLindo(techo / 6.0);
        g.setStroke(p.grid());
        g.setLineWidth(1);
        for (double v = 0; v <= techo + paso * 0.001; v += paso) {
            double px = aX.applyAsDouble(v);
            double py = aY.applyAsDouble(v);
            g.beginPath();
            g.moveTo(px, oy);
            g.lineTo(px, oy + lado);
            g.stroke();
            g.beginPath();
            g.moveTo(ox, py);
            g.lineTo(ox + lado, py);
            g.stroke();
            String txt = dineroCompacto(BigDecimal.valueOf(v));
            double tw = anchoTexto(g, txt);
            g.setFill(p.eje());
            g.fillText(txt, px - tw / 2, oy + lado + 16);
            g.fillText(txt, ox - tw - 8, py + 4);
        }
        g.setStroke(p.marco());
        g.strokeRect(ox, oy, lado, lado);

        // Banda de tolerancia ±10 % alrededor de la diagonal.
        double limiteSup = techo / (1 + TOLERANCIA_DISPERSION);
        java.util.List<double[]> banda = new java.util.ArrayList<>();
        int muestras = 90;
        for (int i = 0; i <= muestras; i++) {
            double xd = limiteSup * i / muestras;
            banda.add(new double[]{aX.applyAsDouble(xd),
                    aY.applyAsDouble(xd * (1 + TOLERANCIA_DISPERSION))});
        }
        for (int i = muestras; i >= 0; i--) {
            double xd = limiteSup * i / muestras;
            banda.add(new double[]{aX.applyAsDouble(xd),
                    aY.applyAsDouble(xd * (1 - TOLERANCIA_DISPERSION))});
        }
        g.setFill(p.banda());
        g.beginPath();
        g.moveTo(banda.get(0)[0], banda.get(0)[1]);
        for (double[] pt : banda) {
            g.lineTo(pt[0], pt[1]);
        }
        g.closePath();
        g.fill();
        g.setStroke(p.bandaLinea());
        g.setLineWidth(1.4);
        g.setLineDashes(7, 5);
        dibujarRecta(g, aX, aY, limiteSup, 1 + TOLERANCIA_DISPERSION, p.bandaLinea());
        dibujarRecta(g, aX, aY, techo, 1 - TOLERANCIA_DISPERSION, p.bandaLinea());
        g.setLineDashes();

        // Diagonal de equilibrio y = x.
        g.setStroke(p.diagonal());
        g.setLineWidth(2);
        g.beginPath();
        g.moveTo(aX.applyAsDouble(0), aY.applyAsDouble(0));
        g.lineTo(aX.applyAsDouble(techo), aY.applyAsDouble(techo));
        g.stroke();

        // Puntos con su rótulo.
        int idx = 0;
        for (PuntoDispercion punto : puntos) {
            double px = aX.applyAsDouble(punto.x());
            double py = aY.applyAsDouble(punto.y());
            trazados.add(new PuntoTrazado(punto, px, py));
            Color relleno = switch (punto.zona()) {
                case SUPERAVIT -> p.exito();
                case TOLERADO -> p.neutro();
                case DEFICIT -> p.fallo();
            };
            g.setFill(relleno);
            g.fillOval(px - 5.5, py - 5.5, 11, 11);
            g.setStroke(p.marco());
            g.setLineWidth(1);
            g.strokeOval(px - 5.5, py - 5.5, 11, 11);
            String txt = punto.rotulo();
            g.setFont(Font.font(10));
            double tw = anchoTexto(g, txt);
            double lx = px + 9;
            if (lx + tw > ox + lado - 2) {
                lx = px - 9 - tw;
            }
            double ly = py - 9 + (idx % 2 == 0 ? 0 : 12);
            if (ly < oy + 10) {
                ly = py + 14;
            }
            g.setFill(p.texto());
            g.fillText(txt, lx, ly);
            idx++;
        }
        g.setFont(Font.font(11));

        // Títulos de ejes.
        g.setFill(p.eje());
        g.fillText(rotuloX, ox + lado - anchoTexto(g, rotuloX), h - 6);
        g.save();
        g.translate(16, oy + lado / 2);
        g.rotate(-90);
        g.fillText(rotuloY, -anchoTexto(g, rotuloY) / 2, 0);
        g.restore();
    }

    /**
     * Tooltip al pasar el cursor sobre un punto de la dispersión: muestra el
     * nombre del mes u objeto junto con sus valores. Las posiciones se leen
     * de la última pintada, así el hover siempre coincide con lo visible.
     */
    private void activarTooltipsDispercion(Canvas lienzo, List<PuntoTrazado> trazados) {
        Tooltip tip = new Tooltip();
        lienzo.setOnMouseMoved(e -> {
            PuntoTrazado cerca = null;
            double mejor = 14 * 14;
            for (PuntoTrazado t : trazados) {
                double dx = e.getX() - t.px();
                double dy = e.getY() - t.py();
                double d2 = dx * dx + dy * dy;
                if (d2 <= mejor) {
                    mejor = d2;
                    cerca = t;
                }
            }
            if (cerca == null || lienzo.getScene() == null
                    || lienzo.getScene().getWindow() == null) {
                tip.hide();
                return;
            }
            tip.setText(cerca.punto().detalle());
            tip.show(lienzo.getScene().getWindow(),
                    e.getScreenX() + 14, e.getScreenY() + 14);
        });
        lienzo.setOnMouseExited(e -> tip.hide());
    }

    /** Traza una recta y = factor · x en coordenadas de datos dentro del lienzo. */
    private void dibujarRecta(GraphicsContext g,
                              java.util.function.DoubleUnaryOperator aX,
                              java.util.function.DoubleUnaryOperator aY,
                              double xFin, double factor, Color color) {
        g.setStroke(color);
        g.beginPath();
        int n = 60;
        for (int i = 0; i <= n; i++) {
            double xd = xFin * i / n;
            double px = aX.applyAsDouble(xd);
            double py = aY.applyAsDouble(xd * factor);
            if (i == 0) {
                g.moveTo(px, py);
            } else {
                g.lineTo(px, py);
            }
        }
        g.stroke();
    }

    /** Techo «lindo» (1-2-5) no menor que el valor pedido. */
    private double techoLindo(double valor) {
        if (valor <= 0) {
            return 1;
        }
        double exp = Math.floor(Math.log10(valor));
        double base = Math.pow(10, exp);
        double[] pasos = {1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10};
        for (double paso : pasos) {
            if (valor <= base * paso) {
                return base * paso;
            }
        }
        return base * 10;
    }

    /** Paso «lindo» (1-2-5) no menor que el objetivo pedido. */
    private double pasoLindo(double objetivo) {
        if (objetivo <= 0) {
            return 1;
        }
        double exp = Math.floor(Math.log10(objetivo));
        double base = Math.pow(10, exp);
        double[] pasos = {1, 2, 2.5, 5, 10};
        for (double paso : pasos) {
            if (objetivo <= base * paso) {
                return base * paso;
            }
        }
        return base * 10;
    }

    private double anchoTexto(GraphicsContext g, String texto) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(texto);
        t.setFont(g.getFont());
        return t.getBoundsInLocal().getWidth();
    }

    private String mesCorto(int numero) {
        String nombre = Month.of(numero).getDisplayName(TextStyle.SHORT,
                Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }

    /** Dato de costo con tres niveles tipográficos, nunca texto suelto bajo una tabla. */
    private VBox filaCosto(String titulo, String valor, String nota) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("cost-summary-title");
        Label importe = new Label(valor);
        importe.getStyleClass().add("cost-summary-value");
        importe.setWrapText(true);
        Label detalle = new Label(nota);
        detalle.getStyleClass().add("cost-summary-note");
        detalle.setWrapText(true);
        VBox fila = new VBox(2, etiqueta, importe, detalle);
        fila.getStyleClass().add("cost-summary-row");
        return fila;
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
        // Encabezado + filas cómodas y margen para la barra horizontal. Un
        // mínimo de 180 px evita una cuadrícula aplastada con pocos planes.
        return Math.max(180, Math.min(332, 52 + filas * 48 + 20));
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
        return UiFormatos.dinero(monto);
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
