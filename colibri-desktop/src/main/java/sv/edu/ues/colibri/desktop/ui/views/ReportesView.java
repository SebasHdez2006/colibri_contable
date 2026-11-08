package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.libros.LibroContableService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.Cuenta;

import java.nio.file.Files;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista de generación, consulta y exportación de reportes contables.
 *
 * Permite filtrar por rango de fechas, tipo de reporte y alcance de datos,
 * además de exportar los resultados en Excel, PDF o paquete ZIP.
 */
public class ReportesView extends VBox {

    static final String DIARIO = "Libro diario";
    static final String MAYOR = "Libro mayor";
    static final String BALANZA = "Balanza de comprobación";
    static final String CUENTA_T = "Cuenta T";
    static final String HOJA_TRABAJO = "Hoja de trabajo";
    static final String SITUACION = "Balance general / Estado de situación financiera";
    static final String RESULTADOS = "Estado de resultados";
    static final String CAMBIOS_PATRIMONIO = "Estado de cambios en el patrimonio";
    static final String FLUJOS_EFECTIVO = "Estado de flujos de efectivo";
    static final String LIBRO_IVA_COMPRAS = "Libro de compras IVA";
    static final String LIBRO_IVA_CCF = "Libro de ventas CCF";
    static final String LIBRO_IVA_FCF = "Libro de ventas consumidor";
    static final String AUX_CLIENTES = "Auxiliar de clientes";
    static final String AUX_PROVEEDORES = "Auxiliar de proveedores";
    static final String AUX_INVENTARIO = "Auxiliar de inventario digital";
    static final String AUX_ORDEN = "Auxiliar de órdenes de servicio";
    static final String AUX_CC = "Gastos por centro de costo";
    private static final String LIBRO_COMPLETO = "Libro completo";
    private static final String CASO_D = "Caso D del informe";

    private final AppContext context;
    private final TableView<ObservableList<String>> tabla = new TableView<>();
    private final ComboBox<Cuenta> cuentaT = new ComboBox<>();
    private final Label totalRegistros = new Label();
    private List<String> encabezados = List.of();
    private List<List<String>> filas = List.of();
    private String tituloReporte = "";
    private String periodoReporte = "";

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
                cargarReporte(tipo.getValue(), desde.getValue(), hasta.getValue(), cuentaT.getValue(),
                    CASO_D.equals(alcance.getValue()));
                excel.setDisable(false);
                pdf.setDisable(false);
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });
        excel.setOnAction(e -> exportar(false));
        pdf.setOnAction(e -> exportar(true));
        zip.setOnAction(e -> exportarZip(desde.getValue(), hasta.getValue(), CASO_D.equals(alcance.getValue())));

        tabla.setPlaceholder(new Label("No hay movimientos para mostrar."));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinHeight(260);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        var filtros = ResponsiveLayouts.row(ResponsiveLayouts.field("Formato", tipo),
                ResponsiveLayouts.field("Alcance", alcance),
                ResponsiveLayouts.field("Cuenta", cuentaT),
                ResponsiveLayouts.field("Desde", desde),
                ResponsiveLayouts.field("Hasta / corte", hasta), generar, excel, pdf, zip);
        filtros.getStyleClass().add("toolbar");
        totalRegistros.getStyleClass().add("section-note");
        getChildren().addAll(filtros, totalRegistros, tabla);
    }

    private void cargarReporte(String tipo, LocalDate desde, LocalDate hasta, Cuenta cuenta,
                               boolean soloCasoD) {
    tituloReporte = tipo;
    periodoReporte = tipo.equals(SITUACION) || tipo.equals(MAYOR)
        ? "Al " + hasta : desde + " a " + hasta;
    if (soloCasoD) {
        periodoReporte += " · Caso D";
    }
    if (DIARIO.equals(tipo)) {
        encabezados = List.of("Fecha", "N.º de partida (PDA)", "Código / ref.", "Cuenta",
            "Concepto", "Debe (USD)", "Haber (USD)");
            filas = context.libros().diario(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.fecha().toString(), nvl(l.folio()), nvl(l.referencia()),
                    l.cuenta() + " · " + l.nombreCuenta(), l.concepto(),
                            l.debe().toPlainString(), l.haber().toPlainString())).toList();
                filas = agregarTotal(filas, 5, 6, "TOTALES");
    } else if (MAYOR.equals(tipo)) {
        encabezados = List.of("Código", "Cuenta", "Debe acumulado (USD)",
            "Haber acumulado (USD)", "Saldo deudor (USD)", "Saldo acreedor (USD)");
        filas = context.libros().libroMayor(hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), l.debeAcumulado().toPlainString(),
                l.haberAcumulado().toPlainString(), l.saldoDeudor().toPlainString(),
                l.saldoAcreedor().toPlainString())).toList();
        filas = agregarTotal(filas, 2, 5, "TOTALES");
    } else if (BALANZA.equals(tipo)) {
        encabezados = List.of("Código", "Cuenta", "Debe (USD)", "Haber (USD)");
            filas = context.libros().balanza(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), l.debe().toPlainString(),
                l.haber().toPlainString())).toList();
        filas = agregarTotal(filas, 2, 3, "TOTALES");
    } else if (CUENTA_T.equals(tipo)) {
        cargarCuentaT(cuenta, desde, hasta, soloCasoD);
    } else if (HOJA_TRABAJO.equals(tipo)) {
        encabezados = List.of("Código", "Cuenta", "Balanza de comprobación debe",
            "Balanza de comprobación haber", "Ajustes debe", "Ajustes haber",
            "Balanza ajustada debe", "Balanza ajustada haber", "Estado de resultados debe",
            "Estado de resultados haber", "Estado de capital debe", "Estado de capital haber",
            "Balance general debe", "Balance general haber");
        filas = context.libros().hojaTrabajo(desde, hasta, soloCasoD).stream()
            .map(l -> List.of(l.cuenta(), l.nombre(), l.debe().toPlainString(),
                l.haber().toPlainString(), l.ajusteDebe().toPlainString(),
                l.ajusteHaber().toPlainString(), l.ajustadaDebe().toPlainString(),
                l.ajustadaHaber().toPlainString(), l.resultadosDebe().toPlainString(),
                l.resultadosHaber().toPlainString(), l.capitalDebe().toPlainString(),
                l.capitalHaber().toPlainString(), l.situacionDebe().toPlainString(),
                l.situacionHaber().toPlainString())).toList();
        filas = agregarTotal(filas, 2, 13, "TOTALES");
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
                .map(l -> List.of(l.codigo(), l.concepto(), l.saldoInicial().toPlainString(),
                        l.movimientos().toPlainString(), l.resultadoPeriodo().toPlainString(),
                        l.saldoFinal().toPlainString())).toList();
    } else if (FLUJOS_EFECTIVO.equals(tipo)) {
        encabezados = List.of("Folio", "Concepto", "Flujo neto (USD)");
        filas = context.libros().estadoFlujosEfectivo(desde, hasta, soloCasoD).stream()
                .map(l -> List.of(nvl(l.codigo()), l.concepto(), l.importe().toPlainString())).toList();
    } else if (LIBRO_IVA_COMPRAS.equals(tipo) || LIBRO_IVA_CCF.equals(tipo) || LIBRO_IVA_FCF.equals(tipo)) {
            String clase = LIBRO_IVA_COMPRAS.equals(tipo) ? "COMPRAS"
                    : LIBRO_IVA_CCF.equals(tipo) ? "VENTAS_CCF" : "VENTAS_FCF";
            encabezados = List.of("Fecha", "Tipo", "NIT", "Tercero", "Código", "Base", "IVA", "Total");
            filas = context.libros().libroIva(desde, hasta, clase).stream()
                    .map(l -> List.of(l.fecha().toString(), l.tipo(), nvl(l.nit()), nvl(l.tercero()),
                            nvl(l.codigo()), l.base().toPlainString(), l.iva().toPlainString(),
                            l.total().toPlainString())).toList();
            filas = agregarTotal(filas, 5, 7, "TOTALES");
        } else {
            String aux = switch (tipo) {
                case AUX_CLIENTES -> "CLIENTES";
                case AUX_PROVEEDORES -> "PROVEEDORES";
                case AUX_INVENTARIO -> "INVENTARIO";
                case AUX_ORDEN -> "ORDEN";
                default -> "CENTRO_COSTO";
            };
            encabezados = List.of("Fecha", "Folio", "Cuenta", "Dimensión", "Concepto", "Debe", "Haber");
            filas = context.libros().auxiliar(aux, desde, hasta).stream()
                    .map(l -> List.of(l.fecha().toString(), nvl(l.folio()), l.cuenta(), nvl(l.dimension()),
                            nvl(l.concepto()), l.debe().toPlainString(), l.haber().toPlainString())).toList();
            filas = agregarTotal(filas, 5, 6, "TOTALES");
        }

        tabla.getColumns().clear();
        for (int index = 0; index < encabezados.size(); index++) {
            final int columna = index;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(encabezados.get(index));
            column.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().get(columna)));
            column.setPrefWidth(Math.max(110, 800.0 / encabezados.size()));
            column.setMinWidth(90);
            tabla.getColumns().add(column);
        }
        ObservableList<ObservableList<String>> datos = FXCollections.observableArrayList();
        filas.forEach(fila -> datos.add(FXCollections.observableArrayList(fila)));
        tabla.setItems(datos);
        totalRegistros.setText(filas.size() + " filas · " + periodoReporte);
    }

    private void cargarCuentaT(Cuenta cuenta, LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        encabezados = List.of("Fecha / tipo", "N.º de partida", "Referencia", "Concepto",
                "Debe (USD)", "Haber (USD)");
        List<List<String>> lineas = new ArrayList<>();
        BigDecimal saldoInicial = desde.equals(LocalDate.MIN) ? BigDecimal.ZERO
                : context.libros().saldoCuentaHasta(cuenta.getCodigo(), desde.minusDays(1), soloCasoD);
        lineas.add(List.of("Saldo inicial", "", "", "Saldo al " + desde.minusDays(1),
                saldoInicial.max(BigDecimal.ZERO).toPlainString(),
                saldoInicial.negate().max(BigDecimal.ZERO).toPlainString()));
        BigDecimal totalDebe = BigDecimal.ZERO;
        BigDecimal totalHaber = BigDecimal.ZERO;
        for (LibroContableService.LineaCuentaT movimiento
                : context.libros().cuentaT(cuenta.getCodigo(), desde, hasta, soloCasoD)) {
            totalDebe = totalDebe.add(movimiento.debe());
            totalHaber = totalHaber.add(movimiento.haber());
            lineas.add(List.of(movimiento.fecha().toString(), nvl(movimiento.folio()),
                    nvl(movimiento.referencia()), movimiento.concepto(),
                    movimiento.debe().toPlainString(), movimiento.haber().toPlainString()));
        }
        lineas.add(List.of("Totales del período", "", "", "", totalDebe.toPlainString(),
                totalHaber.toPlainString()));
        BigDecimal saldoFinal = saldoInicial.add(totalDebe).subtract(totalHaber);
        lineas.add(List.of("Saldo final", "", "", "Saldo al " + hasta,
                saldoFinal.max(BigDecimal.ZERO).toPlainString(),
                saldoFinal.negate().max(BigDecimal.ZERO).toPlainString()));
        filas = lineas;
        tituloReporte = "Cuenta T " + cuenta.getCodigo() + " · " + cuenta.getNombre();
    }

    private List<List<String>> agregarTotal(List<List<String>> origen, int primeraColumnaImporte,
                                            int ultimaColumnaImporte, String etiqueta) {
        BigDecimal[] totales = new BigDecimal[ultimaColumnaImporte - primeraColumnaImporte + 1];
        java.util.Arrays.fill(totales, BigDecimal.ZERO);
        for (List<String> fila : origen) {
            for (int columna = primeraColumnaImporte; columna <= ultimaColumnaImporte; columna++) {
                if (columna < fila.size()) {
                    try {
                        totales[columna - primeraColumnaImporte] = totales[columna - primeraColumnaImporte]
                                .add(new BigDecimal(fila.get(columna)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        List<String> total = new ArrayList<>(java.util.Collections.nCopies(encabezados.size(), ""));
        total.set(0, etiqueta);
        for (int columna = primeraColumnaImporte; columna <= ultimaColumnaImporte; columna++) {
            total.set(columna, totales[columna - primeraColumnaImporte].toPlainString());
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
            String importe = linea.importe().toPlainString();
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

    private void exportar(boolean comoPdf) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(comoPdf ? "Exportar reporte PDF" : "Exportar reporte Excel");
        String extension = comoPdf ? "pdf" : "xlsx";
        chooser.setInitialFileName(tituloReporte.toLowerCase().replace(' ', '-')
                + "-" + periodoReporte.replace(" ", "") + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                comoPdf ? "Documento PDF" : "Libro de Excel", "*." + extension));
        var destino = chooser.showSaveDialog(getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            String entidad = context.periodos().empresa()
                .map(e -> e.getRazonSocial()).orElse("Colibrí");
            String periodoConEntidad = entidad + " · " + periodoReporte;
            byte[] contenido = comoPdf
                ? context.reports().exportarPdf(tituloReporte, periodoConEntidad, encabezados, filas)
                : context.reports().exportarExcel(tituloReporte, periodoConEntidad, encabezados, filas);
            Files.write(destino.toPath(), contenido);
            UiDialogs.info("Reporte exportado", "Archivo guardado en " + destino);
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void exportarZip(LocalDate desde, LocalDate hasta, boolean soloCasoD) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Paquete de reportes");
        chooser.setInitialFileName("Reportes_Contables_Colibri.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        var destino = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            String entidad = context.periodos().empresa().map(e -> e.getRazonSocial()).orElse("Colibrí");
            String periodo = entidad + " · " + desde + " a " + hasta;
            List<String> libros = List.of(DIARIO, MAYOR, BALANZA, HOJA_TRABAJO, SITUACION, RESULTADOS,
                    CAMBIOS_PATRIMONIO, FLUJOS_EFECTIVO);
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                    Files.newOutputStream(destino.toPath()))) {
                for (String libro : libros) {
                    cargarReporte(libro, desde, hasta, null, soloCasoD);
                    byte[] pdf = context.reports().exportarPdf(libro, periodo, encabezados, filas);
                    byte[] xlsx = context.reports().exportarExcel(libro, periodo, encabezados, filas);
                    String carpeta = desde.getYear() + "/";
                    String base = libro.replace(" ", "") + "-" + desde.toString().replace("-", "")
                            + "-" + hasta.toString().replace("-", "");
                    zip.putNextEntry(new java.util.zip.ZipEntry(carpeta + base + ".pdf"));
                    zip.write(pdf);
                    zip.closeEntry();
                    zip.putNextEntry(new java.util.zip.ZipEntry(carpeta + base + ".xlsx"));
                    zip.write(xlsx);
                    zip.closeEntry();
                }
            }
            UiDialogs.info("Paquete ZIP", "Se generó " + destino.getName());
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }
}
