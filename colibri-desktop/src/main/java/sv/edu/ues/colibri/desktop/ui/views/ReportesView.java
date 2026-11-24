package sv.edu.ues.colibri.desktop.ui.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.libros.LibroContableService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiFormatos;
import sv.edu.ues.colibri.desktop.ui.UiTheme;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.Ejercicio;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.reports.ReportService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vista de generación, consulta y exportación de reportes contables.
 *
 * Permite filtrar por rango de fechas, tipo de reporte y alcance de datos,
 * además de exportar los resultados en Excel, PDF o paquete ZIP.
 */
public class ReportesView extends VBox {

    public static final String DIARIO = "Libro diario";
    public static final String MAYOR = "Libro mayor";
    public static final String BALANZA = "Balanza de comprobación";
    public static final String CUENTA_T = "Cuenta T";
    public static final String HOJA_TRABAJO = "Hoja de trabajo";
    public static final String SITUACION = "Balance general / Estado de situación financiera";
    public static final String RESULTADOS = "Estado de resultados";
    public static final String CAMBIOS_PATRIMONIO = "Estado de cambios en el patrimonio";
    public static final String FLUJOS_EFECTIVO = "Estado de flujos de efectivo";
    public static final String LIBRO_IVA_COMPRAS = "Libro de compras IVA";
    public static final String LIBRO_IVA_CCF = "Libro de ventas CCF";
    public static final String LIBRO_IVA_FCF = "Libro de ventas consumidor";
    public static final String AUX_CLIENTES = "Auxiliar de clientes";
    public static final String AUX_PROVEEDORES = "Auxiliar de proveedores";
    public static final String AUX_INVENTARIO = "Auxiliar de inventario digital";
    public static final String AUX_ORDEN = "Auxiliar de órdenes de servicio";
    public static final String AUX_CC = "Gastos por centro de costo";
    private static final String LIBRO_COMPLETO = "Libro completo";
    private static final String CASO_D = "Caso D del informe";

    private static final List<String> LIBROS_PAQUETE = List.of(DIARIO, MAYOR, BALANZA,
            HOJA_TRABAJO, SITUACION, RESULTADOS, CAMBIOS_PATRIMONIO, FLUJOS_EFECTIVO);

    private final AppContext context;
    private final List<String> formatos;
    private final TableView<ObservableList<String>> tabla = new TableView<>();
    private final ComboBox<Cuenta> cuentaT = new ComboBox<>();
    private final Label totalRegistros = new Label();
    private List<String> encabezados = List.of();
    private List<List<String>> filas = List.of();
    private String tituloReporte = "";
    private String periodoReporte = "";
    private LocalDate ultimoDesde;
    private LocalDate ultimoHasta;

    /** Datos ya calculados de un reporte; no toca la interfaz. */
    private record DatosReporte(String titulo, String periodo, List<String> encabezados,
                                List<List<String>> filas) {
    }

    /**
     * Crea la vista con el catálogo completo de reportes disponibles.
     *
     * @param context contexto de aplicación con acceso a los servicios necesarios
     */
    public ReportesView(AppContext context) {
        this(context, List.of(
                DIARIO, MAYOR, BALANZA, CUENTA_T, HOJA_TRABAJO, SITUACION, RESULTADOS,
                CAMBIOS_PATRIMONIO, FLUJOS_EFECTIVO,
                LIBRO_IVA_COMPRAS, LIBRO_IVA_CCF, LIBRO_IVA_FCF,
                AUX_CLIENTES, AUX_PROVEEDORES, AUX_INVENTARIO, AUX_ORDEN, AUX_CC));
    }

    /**
     * Crea la vista con un subconjunto de formatos de reporte permitidos.
     *
     * @param context contexto de aplicación con acceso a los servicios necesarios
     * @param formatos tipos de reporte que se ofrecerán al usuario
     */
    public ReportesView(AppContext context, List<String> formatos) {
        this.context = context;
        this.formatos = List.copyOf(formatos);
        setSpacing(10);
        setPadding(new Insets(8, 0, 0, 0));

        LocalDate fechaBase = context.periodos().periodoAbierto().map(p -> p.getFechaInicio()).orElse(LocalDate.now().withDayOfMonth(1));
        DatePicker desde = new DatePicker(fechaBase);
        DatePicker hasta = new DatePicker(context.periodos().periodoAbierto().map(p -> p.getFechaFin()).orElse(LocalDate.now()));
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList(formatos));
        tipo.setValue(formatos.getFirst());
        ComboBox<String> alcance = new ComboBox<>(FXCollections.observableArrayList(LIBRO_COMPLETO, CASO_D));
        alcance.setValue(LIBRO_COMPLETO);

        cuentaT.setItems(FXCollections.observableArrayList(context.catalogo().listar().stream()
                .filter(Cuenta::isAdmiteMovimiento).toList()));
        cuentaT.setConverter(new StringConverter<>() {
            @Override
            public String toString(Cuenta cuenta) {
                return cuenta == null ? "" : cuenta.getCodigo() + " · " + cuenta.getNombre();
            }

            @Override
            public Cuenta fromString(String texto) {
                return cuentaT.getItems().stream()
                        .filter(cuenta -> toString(cuenta).equals(texto)).findFirst().orElse(null);
            }
        });
        cuentaT.setDisable(true);
        tipo.valueProperty().addListener((observable, anterior, seleccionado) ->
                cuentaT.setDisable(!CUENTA_T.equals(seleccionado)));

        Button generar = new Button("Generar reporte");
        Button excel = new Button("Exportar Excel");
        Button pdf = new Button("Exportar PDF");
        Button zip = new Button("Exportar paquete ZIP");
        Button varios = new Button("Exportar libros × períodos");
        varios.setTooltip(new Tooltip("Genera una carpeta con una subcarpeta por libro y dentro "
                + "un Excel y un PDF por cada período seleccionado."));
        excel.setDisable(true);
        pdf.setDisable(true);
        generar.setOnAction(e -> {
            try {
                if (desde.getValue() == null || hasta.getValue() == null
                        || desde.getValue().isAfter(hasta.getValue())) {
                    throw new IllegalArgumentException("Seleccione un rango de fechas válido.");
                }
                if (CUENTA_T.equals(tipo.getValue()) && cuentaT.getValue() == null) {
                    throw new IllegalArgumentException("Seleccione una cuenta para generar su cuenta T.");
                }
                DatosReporte datos = datosReporte(tipo.getValue(), desde.getValue(), hasta.getValue(),
                        cuentaT.getValue(), CASO_D.equals(alcance.getValue()));
                ultimoDesde = desde.getValue();
                ultimoHasta = hasta.getValue();
                aplicar(datos);
                excel.setDisable(false);
                pdf.setDisable(false);
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        excel.setOnAction(e -> exportar(false));
        pdf.setOnAction(e -> exportar(true));
        zip.setOnAction(e -> exportarZip(desde.getValue(), hasta.getValue(), CASO_D.equals(alcance.getValue())));
        varios.setOnAction(e -> exportarLibreriaPeriodos(
                () -> {
                    if (ultimoDesde != null) {
                        try {
                            aplicar(datosReporte(tipo.getValue(), ultimoDesde, ultimoHasta,
                                    cuentaT.getValue(), CASO_D.equals(alcance.getValue())));
                        } catch (Exception ignored) {
                            // La vista previa se reconstruye al volver al reporte.
                        }
                    }
                }));

        tabla.setPlaceholder(new Label("No hay movimientos para mostrar."));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinHeight(260);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        var filtros = ResponsiveLayouts.row(ResponsiveLayouts.field("Formato", tipo),
                ResponsiveLayouts.field("Alcance", alcance),
                ResponsiveLayouts.field("Cuenta", cuentaT),
                ResponsiveLayouts.field("Desde", desde),
                ResponsiveLayouts.field("Hasta / corte", hasta), generar, excel, pdf, zip, varios);
        filtros.getStyleClass().add("toolbar");
        totalRegistros.getStyleClass().add("section-note");
        Label titulo = new Label("Reportes");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label("Consulte y exporte libros, estados financieros y auxiliares del período.");
        subtitulo.getStyleClass().add("view-subtitle");
        getChildren().addAll(titulo, subtitulo, filtros, totalRegistros, tabla);
    }

    private void aplicar(DatosReporte datos) {
        tituloReporte = datos.titulo();
        periodoReporte = datos.periodo();
        encabezados = datos.encabezados();
        filas = datos.filas();

        tabla.getColumns().clear();
        for (int index = 0; index < encabezados.size(); index++) {
            final int columna = index;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(encabezados.get(index));
            column.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get(columna)));
            column.setPrefWidth(anchoColumna(index));
            column.setMinWidth(80);
            tabla.getColumns().add(column);
        }
        ObservableList<ObservableList<String>> datosTabla = FXCollections.observableArrayList();
        filas.forEach(fila -> datosTabla.add(FXCollections.observableArrayList(fila)));
        tabla.setItems(datosTabla);
        totalRegistros.setText(filas.size() + " filas · " + periodoReporte);
    }

    /** Ancho estimado de la columna según el texto más largo de sus celdas. */
    private double anchoColumna(int index) {
        int max = encabezados.get(index).length();
        for (List<String> fila : filas) {
            if (index < fila.size()) {
                max = Math.max(max, fila.get(index).length());
            }
        }
        return Math.min(380, Math.max(90, max * 7.4 + 30));
    }

    private DatosReporte datosReporte(String tipo, LocalDate desde, LocalDate hasta, Cuenta cuenta,
                                      boolean soloCasoD) {
        String titulo = tipo;
        String periodo = tipo.equals(SITUACION) || tipo.equals(MAYOR)
                ? "Al " + hasta : desde + " a " + hasta;
        if (soloCasoD) {
            periodo += " · Caso D";
        }
        List<String> encabezados;
        List<List<String>> filas;
        if (DIARIO.equals(tipo)) {
            encabezados = List.of("Fecha", "N.º de partida (PDA)", "Código / ref.", "Cuenta",
                "Concepto", "Debe (USD)", "Haber (USD)");
            filas = context.libros().diario(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.fecha().toString(), nvl(l.folio()), nvl(l.referencia()),
                    l.cuenta() + " · " + l.nombreCuenta(), l.concepto(),
                            dinero(l.debe()), dinero(l.haber()))).toList();
                filas = agregarTotal(filas, encabezados, 5, 6, "TOTALES");
        } else if (MAYOR.equals(tipo)) {
            encabezados = List.of("Código", "Cuenta", "Debe acumulado (USD)",
                "Haber acumulado (USD)", "Saldo deudor (USD)", "Saldo acreedor (USD)");
        filas = context.libros().libroMayor(hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), dinero(l.debeAcumulado()),
                dinero(l.haberAcumulado()), dinero(l.saldoDeudor()),
                dinero(l.saldoAcreedor()))).toList();
        filas = agregarTotal(filas, encabezados, 2, 5, "TOTALES");
    } else if (BALANZA.equals(tipo)) {
        encabezados = List.of("Código", "Cuenta", "Debe (USD)", "Haber (USD)");
            filas = context.libros().balanza(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), dinero(l.debe()),
                dinero(l.haber()))).toList();
        filas = agregarTotal(filas, encabezados, 2, 3, "TOTALES");
    } else if (CUENTA_T.equals(tipo)) {
        DatosReporte cuentaT = cargarCuentaT(cuenta, desde, hasta, soloCasoD);
        return new DatosReporte(cuentaT.titulo(), periodo, cuentaT.encabezados(), cuentaT.filas());
    } else if (HOJA_TRABAJO.equals(tipo)) {
        encabezados = List.of("Código", "Cuenta", "Balanza de comprobación debe",
            "Balanza de comprobación haber", "Ajustes debe", "Ajustes haber",
            "Balanza ajustada debe", "Balanza ajustada haber", "Estado de resultados debe",
            "Estado de resultados haber", "Estado de capital debe", "Estado de capital haber",
            "Balance general debe", "Balance general haber");
        filas = context.libros().hojaTrabajo(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), dinero(l.debe()),
                dinero(l.haber()), dinero(l.ajusteDebe()),
                dinero(l.ajusteHaber()), dinero(l.ajustadaDebe()),
                dinero(l.ajustadaHaber()), dinero(l.resultadosDebe()),
                dinero(l.resultadosHaber()), dinero(l.capitalDebe()),
                dinero(l.capitalHaber()), dinero(l.situacionDebe()),
                dinero(l.situacionHaber()))).toList();
        filas = agregarTotal(filas, encabezados, 2, 13, "TOTALES");
    } else if (SITUACION.equals(tipo)) {
        encabezados = encabezadosEstado();
        filas = filasEstado(context.libros().estadoSituacion(hasta, soloCasoD));
    } else if (RESULTADOS.equals(tipo)) {
        encabezados = encabezadosEstado();
        filas = filasEstado(context.libros().estadoResultados(desde, hasta, soloCasoD));
    } else if (CAMBIOS_PATRIMONIO.equals(tipo)) {
        encabezados = List.of("Código", "Concepto", "Saldo inicial (USD)",
                "Movimientos patrimoniales (USD)", "Resultado del período (USD)",
                "Saldo final (USD)");
        filas = context.libros().estadoCambiosPatrimonio(desde, hasta, soloCasoD).stream()
                .map(l -> List.of(l.codigo(), l.concepto(), dinero(l.saldoInicial()),
                        dinero(l.movimientos()), dinero(l.resultadoPeriodo()),
                        dinero(l.saldoFinal()))).toList();
    } else if (FLUJOS_EFECTIVO.equals(tipo)) {
        encabezados = List.of("Folio", "Concepto", "Flujo neto (USD)");
        filas = context.libros().estadoFlujosEfectivo(desde, hasta, soloCasoD).stream()
                .map(l -> List.of(nvl(l.codigo()), l.concepto(), dinero(l.importe()))).toList();
    } else if (LIBRO_IVA_COMPRAS.equals(tipo) || LIBRO_IVA_CCF.equals(tipo) || LIBRO_IVA_FCF.equals(tipo)) {
            String clase = LIBRO_IVA_COMPRAS.equals(tipo) ? "COMPRAS"
                    : LIBRO_IVA_CCF.equals(tipo) ? "VENTAS_CCF" : "VENTAS_FCF";
            encabezados = List.of("Fecha", "Tipo", "NIT", "Tercero", "Código", "Base", "IVA", "Total");
            filas = context.libros().libroIva(desde, hasta, clase).stream()
                    .map(l -> List.of(l.fecha().toString(), l.tipo(), nvl(l.nit()), nvl(l.tercero()),
                            nvl(l.codigo()), dinero(l.base()), dinero(l.iva()),
                            dinero(l.total()))).toList();
            filas = agregarTotal(filas, encabezados, 5, 7, "TOTALES");
        } else {
            String aux = switch (tipo) {
                case AUX_CLIENTES -> "CLIENTES";
                case AUX_PROVEEDORES -> "PROVEEDORES";
                case AUX_INVENTARIO -> "INVENTARIO";
                case AUX_ORDEN -> "ORDEN";
                default -> "CENTRO_COSTO";
            };
            encabezados = List.of("Fecha", "Folio", "Cuenta", "Dimensión", "Concepto",
                    "Debe", "Haber", "Saldo acumulado (USD)");
            filas = context.libros().auxiliar(aux, desde, hasta).stream()
                    .map(l -> List.of(l.fecha().toString(), nvl(l.folio()), l.cuenta(), nvl(l.dimension()),
                            nvl(l.concepto()), dinero(l.debe()), dinero(l.haber()),
                            dinero(l.saldo()))).toList();
            filas = agregarTotal(filas, encabezados, 5, 6, "TOTALES");
        }
        return new DatosReporte(titulo, periodo, encabezados, filas);
    }

    private DatosReporte cargarCuentaT(Cuenta cuenta, LocalDate desde, LocalDate hasta,
                                       boolean soloCasoD) {
        List<String> encabezados = List.of("Fecha / tipo", "N.º de partida", "Referencia", "Concepto",
                "Debe (USD)", "Haber (USD)");
        List<List<String>> lineas = new ArrayList<>();
        BigDecimal saldoInicial = desde.equals(LocalDate.MIN) ? BigDecimal.ZERO
                : context.libros().saldoCuentaHasta(cuenta.getCodigo(), desde.minusDays(1), soloCasoD);
        lineas.add(List.of("Saldo inicial", "", "", "Saldo al " + desde.minusDays(1),
                dinero(saldoInicial.max(BigDecimal.ZERO)),
                dinero(saldoInicial.negate().max(BigDecimal.ZERO))));
        BigDecimal totalDebe = BigDecimal.ZERO;
        BigDecimal totalHaber = BigDecimal.ZERO;
        for (LibroContableService.LineaCuentaT movimiento
                : context.libros().cuentaT(cuenta.getCodigo(), desde, hasta, soloCasoD)) {
            totalDebe = totalDebe.add(movimiento.debe());
            totalHaber = totalHaber.add(movimiento.haber());
            lineas.add(List.of(movimiento.fecha().toString(), nvl(movimiento.folio()),
                    nvl(movimiento.referencia()), movimiento.concepto(),
                    dinero(movimiento.debe()), dinero(movimiento.haber())));
        }
        lineas.add(List.of("Totales del período", "", "", "", dinero(totalDebe),
                dinero(totalHaber)));
        BigDecimal saldoFinal = saldoInicial.add(totalDebe).subtract(totalHaber);
        lineas.add(List.of("Saldo final", "", "", "Saldo al " + hasta,
                dinero(saldoFinal.max(BigDecimal.ZERO)),
                dinero(saldoFinal.negate().max(BigDecimal.ZERO))));
        return new DatosReporte("Cuenta T " + cuenta.getCodigo() + " · " + cuenta.getNombre(),
                "", encabezados, lineas);
    }

    private List<List<String>> agregarTotal(List<List<String>> origen, List<String> encabezados,
                                            int primeraColumnaImporte,
                                            int ultimaColumnaImporte, String etiqueta) {
        BigDecimal[] totales = new BigDecimal[ultimaColumnaImporte - primeraColumnaImporte + 1];
        java.util.Arrays.fill(totales, BigDecimal.ZERO);
        for (List<String> fila : origen) {
            for (int columna = primeraColumnaImporte; columna <= ultimaColumnaImporte; columna++) {
                if (columna < fila.size()) {
                    try {
                        totales[columna - primeraColumnaImporte] = totales[columna - primeraColumnaImporte]
                                .add(importeDe(fila.get(columna)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        List<String> total = new ArrayList<>(java.util.Collections.nCopies(encabezados.size(), ""));
        total.set(0, etiqueta);
        for (int columna = primeraColumnaImporte; columna <= ultimaColumnaImporte; columna++) {
            total.set(columna, dinero(totales[columna - primeraColumnaImporte]));
        }
        List<List<String>> resultado = new ArrayList<>(origen);
        resultado.add(total);
        return resultado;
    }

    private List<String> encabezadosEstado() {
        return List.of("Código", "Concepto / cuenta", "Detalle", "Parcial (USD)",
                "Subtotal / total (USD)");
    }

    private List<List<String>> filasEstado(List<LibroContableService.LineaEstado> origen) {
        return origen.stream().map(linea -> {
            String importe = dinero(linea.importe());
            if (linea.subtotal()) {
                return List.of(nvl(linea.codigo()), linea.concepto(), "", "", importe);
            }
            if (linea.nivel() == 0) {
                return List.of(nvl(linea.codigo()), linea.concepto(), "", "", "");
            }
            return List.of(nvl(linea.codigo()), "", linea.concepto(), importe, "");
        }).toList();
    }

    private String nvl(String valor) {
        return valor == null ? "" : valor;
    }

    private static String dinero(BigDecimal monto) {
        return UiFormatos.dinero(monto);
    }

    /**
     * Reconstruye el importe numérico de una celda ya formateada para
     * pantalla («$ 640,023.92»), para los totales que vuelven a sumarse.
     */
    private static BigDecimal importeDe(String texto) {
        return new BigDecimal(texto.replace("$", "").replace(",", "").replace(" ", "").trim());
    }

    private ReportService.Membrete membrete() {
        return context.periodos().empresa()
                .map(e -> new ReportService.Membrete(e.getRazonSocial(), e.getNombreComercial(),
                        e.getNit(), e.getNrc(), e.getLogoRuta()))
                .orElse(null);
    }

    private void exportar(boolean comoPdf) {
        if (ultimoDesde == null || ultimoHasta == null) {
            UiDialogs.error("Genere primero el reporte que desea exportar.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(comoPdf ? "Exportar reporte PDF" : "Exportar reporte Excel");
        String extension = comoPdf ? "pdf" : "xlsx";
        String nombreBase = ReportService.nombreBaseLibro(tituloReporte, ultimoDesde, ultimoHasta,
                LocalDateTime.now());
        chooser.setInitialFileName(nombreBase + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                comoPdf ? "Documento PDF" : "Libro de Excel", "*." + extension));
        var destino = chooser.showSaveDialog(getScene().getWindow());
        if (destino == null) {
            return;
        }
        ReportService.Membrete membrete = membrete();
        Path archivo = destino.toPath();
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Generando " + nombreBase + "." + extension);
                String entidad = context.periodos().empresa()
                        .map(e -> e.getRazonSocial()).orElse("Colibrí");
                String periodoConEntidad = entidad + " · " + periodoReporte;
                byte[] contenido = comoPdf
                    ? context.reports().exportarPdf(tituloReporte, periodoConEntidad, encabezados, filas,
                            membrete)
                    : context.reports().exportarExcel(tituloReporte, periodoConEntidad, encabezados, filas,
                            membrete);
                Files.write(archivo, contenido);
                return null;
            }
        };
        conProgreso(comoPdf ? "Exportar PDF" : "Exportar Excel",
                "Generando " + tituloReporte + " para " + periodoReporte,
                tarea, () -> UiDialogs.info("Reporte exportado",
                        "Archivo guardado en " + archivo.getFileName()));
    }

    private void exportarZip(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            UiDialogs.error("Seleccione un rango de fechas válido para el paquete.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Paquete de reportes");
        String momento = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombrePaquete = "Reportes_" + ReportService.carpetaPeriodo(desde, hasta)
                + "_" + momento + ".zip";
        chooser.setInitialFileName(nombrePaquete);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        var destino = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (destino == null) {
            return;
        }
        Path archivo = destino.toPath();
        ReportService.Membrete membrete = membrete();
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String entidad = context.periodos().empresa()
                        .map(e -> e.getRazonSocial()).orElse("Colibrí");
                String periodo = entidad + " · " + desde + " a " + hasta;
                String paqueteNombre = nombrePaquete.replaceFirst("\\.zip$", "");
                String carpetaPeriodo = ReportService.carpetaPeriodo(desde, hasta);
                LocalDateTime exportado = LocalDateTime.now();
                try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                        Files.newOutputStream(archivo))) {
                    int indice = 0;
                    for (String libro : LIBROS_PAQUETE) {
                        indice++;
                        updateMessage("Generando " + libro + " (" + indice + "/" + LIBROS_PAQUETE.size() + ")");
                        DatosReporte datos = datosReporte(libro, desde, hasta, null, soloCasoD);
                        String base = ReportService.nombreBaseLibro(libro, desde, hasta, exportado);
                        String carpeta = paqueteNombre + "/" + ReportService.nombreArchivoSeguro(libro)
                                + "/" + carpetaPeriodo + "/";
                        zip.putNextEntry(new java.util.zip.ZipEntry(carpeta + base + ".pdf"));
                        zip.write(context.reports().exportarPdf(libro, periodo, datos.encabezados(),
                                datos.filas(), membrete));
                        zip.closeEntry();
                        zip.putNextEntry(new java.util.zip.ZipEntry(carpeta + base + ".xlsx"));
                        zip.write(context.reports().exportarExcel(libro, periodo, datos.encabezados(),
                                datos.filas(), membrete));
                        zip.closeEntry();
                    }
                }
                return null;
            }
        };
        conProgreso("Paquete de reportes",
                "Generando " + LIBROS_PAQUETE.size() + " libros en PDF y Excel",
                tarea, () -> UiDialogs.info("Paquete ZIP",
                        "Se generó " + archivo.getFileName()));
    }

    /**
     * Muestra un diálogo modal de avance mientras la tarea de exportación corre
     * fuera del hilo de interfaz. El mensaje de la tarea se refleja en la etiqueta.
     * El cuadro se descarta para cualquiera de los estados terminales de la tarea,
     * incluso si un controlador de éxito o fallo no llega a ejecutarse.
     */
    private void conProgreso(String titulo, String detalle, Task<?> tarea, Runnable alTerminar) {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(detalle);
        dialogo.getDialogPane().getButtonTypes().clear();
        dialogo.setOnCloseRequest(e -> {
            if (!tarea.isDone()) {
                e.consume();
            }
        });

        ProgressBar barra = new ProgressBar();
        barra.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Label estado = new Label("Iniciando exportación");
        estado.setWrapText(true);
        VBox contenido = new VBox(10, barra, estado);
        contenido.setPadding(new Insets(16));
        dialogo.getDialogPane().setContent(contenido);
        UiTheme.style(dialogo);

        // Los eventos de Task normalmente se despachan en el hilo JavaFX, pero
        // centralizar el cierre también cubre una cancelación o una transición
        // terminal ocurrida justo cuando se está mostrando el diálogo.
        AtomicBoolean cerrado = new AtomicBoolean();
        Runnable cerrarDialogo = () -> {
            if (!cerrado.compareAndSet(false, true)) {
                return;
            }
            Runnable cerrar = () -> {
                if (!dialogo.isShowing()) {
                    return;
                }
                // Dialog.close() se niega a cerrar cuando no hay botón de
                // cancelar ni resultado (requestPermissionToClose), y este
                // diálogo no tiene ninguno: la ventana quedaba abierta para
                // siempre. Se oculta la ventana interna directamente.
                var ventana = dialogo.getDialogPane().getScene() == null ? null
                        : dialogo.getDialogPane().getScene().getWindow();
                if (ventana != null) {
                    ventana.hide();
                }
            };
            if (Platform.isFxApplicationThread()) {
                cerrar.run();
            } else {
                Platform.runLater(cerrar);
            }
        };

        tarea.messageProperty().addListener((obs, anterior, mensaje) -> {
            if (mensaje != null && !mensaje.isBlank()) {
                estado.setText(mensaje);
            }
        });
        tarea.stateProperty().addListener((obs, anterior, actual) -> {
            if (actual == Worker.State.SUCCEEDED || actual == Worker.State.FAILED
                    || actual == Worker.State.CANCELLED) {
                cerrarDialogo.run();
            }
        });
        tarea.setOnSucceeded(e -> {
            cerrarDialogo.run();
            alTerminar.run();
        });
        tarea.setOnFailed(e -> {
            cerrarDialogo.run();
            Throwable causa = tarea.getException();
            UiDialogs.error(causa == null ? "La exportación falló."
                    : causa.getMessage() == null ? causa.getClass().getSimpleName()
                    : causa.getMessage());
        });
        tarea.setOnCancelled(e -> cerrarDialogo.run());
        dialogo.show();
        Thread hilo = new Thread(tarea, "colibri-exportacion");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Exporta los libros de esta vista en una carpeta por libro, con un archivo
     * por cada período contable seleccionado. La «Cuenta T» queda fuera porque
     * requiere elegir una cuenta específica y se exporta de forma individual.
     */
    private void exportarLibreriaPeriodos(Runnable restaurarVista) {
        List<Periodo> periodos;
        try {
            periodos = new ArrayList<>();
            for (Ejercicio ejercicio : context.periodos().listarEjercicios()) {
                periodos.addAll(context.periodos().listarPeriodos(ejercicio));
            }
            periodos.sort(Comparator.comparing(Periodo::getFechaInicio).reversed());
        } catch (Exception ex) {
            UiDialogs.error("No se pudieron cargar los períodos: " + ex.getMessage());
            return;
        }
        if (periodos.isEmpty()) {
            UiDialogs.error("No hay períodos configurados para exportar.");
            return;
        }

        List<String> libros = formatos.stream().filter(t -> !CUENTA_T.equals(t)).toList();
        if (libros.isEmpty()) {
            UiDialogs.error("Esta vista no tiene libros exportables en lote.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Exportar libros por período");
        dialog.setHeaderText("Seleccione los libros y los períodos a exportar.\n"
                + "El rango de fechas filtra los períodos incluidos.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        LocalDate minimo = periodos.get(periodos.size() - 1).getFechaInicio();
        LocalDate maximo = periodos.get(0).getFechaFin();
        DatePicker rangoDesde = new DatePicker(minimo);
        DatePicker rangoHasta = new DatePicker(maximo);
        rangoDesde.setPromptText("Desde");
        rangoHasta.setPromptText("Hasta");
        VBox panelRango = new VBox(6, new Label("Rango de fechas (filtra los períodos)"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Desde", rangoDesde),
                        ResponsiveLayouts.field("Hasta", rangoHasta)));
        panelRango.getStyleClass().add("form-card");

        VBox panelLibros = new VBox(4, new Label("Libros"));
        panelLibros.getStyleClass().add("form-card");
        List<CheckBox> checksLibros = new ArrayList<>();
        for (String libro : libros) {
            CheckBox check = new CheckBox(libro);
            check.setSelected(true);
            checksLibros.add(check);
            panelLibros.getChildren().add(check);
        }
        FlowPane accionesLibros = new FlowPane();
        accionesLibros.setHgap(6);
        Button todosLibros = new Button("Todos");
        todosLibros.setAccessibleHelp("Seleccionar todos los libros");
        todosLibros.setOnAction(e -> marcarTodos(checksLibros));
        Button ningunoLibros = new Button("Ninguno");
        ningunoLibros.setAccessibleHelp("Quitar la selección de todos los libros");
        ningunoLibros.setOnAction(e -> desmarcarTodos(checksLibros));
        accionesLibros.getChildren().addAll(todosLibros, ningunoLibros);
        panelLibros.getChildren().add(accionesLibros);
        panelLibros.getChildren().add(0, new Label(libros.size() + " disponibles · la Cuenta T se "
                + "exporta de forma individual"));

        VBox panelPeriodos = new VBox(4);
        panelPeriodos.getStyleClass().add("form-card");
        List<CheckBox> checksPeriodos = new ArrayList<>();
        List<Periodo> periodoDe = new ArrayList<>();
        Label contadorPeriodos = new Label();
        FlowPane accionesPeriodos = new FlowPane();
        accionesPeriodos.setHgap(6);

        Runnable reconstruirPeriodos = () -> {
            checksPeriodos.clear();
            periodoDe.clear();
            panelPeriodos.getChildren().clear();
            panelPeriodos.getChildren().add(new Label("Períodos"));
            LocalDate desde = rangoDesde.getValue();
            LocalDate hasta = rangoHasta.getValue();
            int incluidos = 0;
            for (Periodo p : periodos) {
                boolean enRango = desde == null || hasta == null
                        || (!p.getFechaFin().isBefore(desde) && !p.getFechaInicio().isAfter(hasta));
                if (!enRango) {
                    continue;
                }
                incluidos++;
                CheckBox check = new CheckBox(etiquetaPeriodo(p));
                check.setSelected(true);
                checksPeriodos.add(check);
                periodoDe.add(p);
                panelPeriodos.getChildren().add(check);
            }
            contadorPeriodos.setText(incluidos + " de " + periodos.size()
                    + " períodos en el rango seleccionado");
            accionesPeriodos.getChildren().setAll(botonesPeriodo(checksPeriodos));
            panelPeriodos.getChildren().addAll(contadorPeriodos, accionesPeriodos);
        };
        reconstruirPeriodos.run();
        rangoDesde.valueProperty().addListener((o, a, b) -> reconstruirPeriodos.run());
        rangoHasta.valueProperty().addListener((o, a, b) -> reconstruirPeriodos.run());

        VBox contenido = new VBox(10, panelRango, panelLibros, panelPeriodos);
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(580, 460);
        dialog.getDialogPane().setContent(scroll);
        UiTheme.style(dialog);

        dialog.setResultConverter(bt -> bt == ButtonType.OK ? ButtonType.OK : null);
        if (dialog.showAndWait().isEmpty()) {
            return;
        }
        List<String> librosSel = new ArrayList<>();
        for (int i = 0; i < checksLibros.size(); i++) {
            if (checksLibros.get(i).isSelected()) {
                librosSel.add(libros.get(i));
            }
        }
        List<Periodo> periodosSel = new ArrayList<>();
        for (int i = 0; i < checksPeriodos.size(); i++) {
            if (checksPeriodos.get(i).isSelected()) {
                periodosSel.add(periodoDe.get(i));
            }
        }
        if (librosSel.isEmpty() || periodosSel.isEmpty()) {
            UiDialogs.error("Debe seleccionar al menos un libro y un período.");
            return;
        }
        LocalDate desdeFiltro = rangoDesde.getValue();
        LocalDate hastaFiltro = rangoHasta.getValue();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta raíz de la exportación");
        chooser.setInitialDirectory(Path.of(System.getProperty("user.home")).toFile());
        var destino = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (destino == null) {
            return;
        }

        Path raiz = destino.toPath();
        ReportService.Membrete membrete = membrete();
        LocalDateTime exportado = LocalDateTime.now();
        String momento = exportado.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String paqueteNombre = "Exportacion_Del_"
                + (desdeFiltro == null ? "inicio" : desdeFiltro.format(DateTimeFormatter.BASIC_ISO_DATE))
                + "_al_"
                + (hastaFiltro == null ? "fin" : hastaFiltro.format(DateTimeFormatter.BASIC_ISO_DATE))
                + "_" + momento;

        Task<Path> tarea = new Task<>() {
            @Override
            protected Path call() throws Exception {
                List<ReportService.Exportacion> exportaciones = new ArrayList<>();
                int combinaciones = librosSel.size() * periodosSel.size();
                int indice = 0;
                for (String libro : librosSel) {
                    for (Periodo p : periodosSel) {
                        indice++;
                        updateMessage("Calculando " + libro + " · " + etiquetaPeriodo(p)
                                + " (" + indice + "/" + combinaciones + ")");
                        DatosReporte datos = datosReporte(libro, p.getFechaInicio(), p.getFechaFin(),
                                null, false);
                        exportaciones.add(new ReportService.Exportacion(datos.titulo(),
                                etiquetaPeriodoArchivo(p),
                                ReportService.carpetaPeriodo(p.getFechaInicio(), p.getFechaFin()),
                                ReportService.nombreBaseLibro(libro, p.getFechaInicio(), p.getFechaFin(),
                                        exportado),
                                datos.encabezados(), datos.filas()));
                    }
                }
                return context.reports().exportarVarios(raiz, paqueteNombre, exportaciones, membrete,
                        this::updateMessage);
            }
        };
        conProgreso("Exportar libros por período",
                "Generando " + (librosSel.size() * periodosSel.size())
                        + " libros (Excel y PDF) en " + paqueteNombre,
                tarea, () -> {
                    restaurarVista.run();
                    UiDialogs.info("Libros exportados",
                            "Se generaron " + (librosSel.size() * periodosSel.size())
                                    + " libros (Excel y PDF) en " + paqueteNombre);
                });
    }

    private static List<Button> botonesPeriodo(List<CheckBox> checks) {
        Button todos = new Button("Todos");
        todos.setAccessibleHelp("Seleccionar todos los períodos del rango");
        todos.setOnAction(e -> marcarTodos(checks));
        Button ninguno = new Button("Ninguno");
        ninguno.setAccessibleHelp("Quitar la selección de todos los períodos");
        ninguno.setOnAction(e -> desmarcarTodos(checks));
        return List.of(todos, ninguno);
    }

    private static void marcarTodos(List<CheckBox> checks) {
        checks.forEach(c -> c.setSelected(true));
    }

    private static void desmarcarTodos(List<CheckBox> checks) {
        checks.forEach(c -> c.setSelected(false));
    }

    private static String etiquetaPeriodo(Periodo p) {
        String mes = Month.of(p.getMes())
                .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-SV"));
        mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        String estado = switch (p.getEstado()) {
            case ABIERTO -> "Abierto";
            case EN_REVISION -> "En revisión";
            case CERRADO -> "Cerrado";
        };
        return mes + " " + p.getFechaInicio().getYear() + " · " + estado;
    }

    private static String etiquetaPeriodoArchivo(Periodo p) {
        String mes = Month.of(p.getMes())
                .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1)
                + "_" + p.getFechaInicio().getYear();
    }
}
