package sv.edu.ues.colibri.reports;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignImage;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalImageAlignEnum;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.ScaleImageEnum;
import net.sf.jasperreports.engine.type.VerticalImageAlignEnum;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fachada de reportes. Las plantillas JRXML y libros contables completos se
 * implementan en la etapa 4; aquí se fijan JasperReports (PDF) y Apache POI (Excel).
 */
public class ReportService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * Identidad institucional que encabeza los documentos generados.
     *
     * @param razonSocial     razón social de la empresa
     * @param nombreComercial nombre comercial, si existe
     * @param nit             NIT de la empresa
     * @param nrc             NRC de la empresa
     * @param logoRuta        ruta del archivo de imagen del logo institucional
     */
    public record Membrete(String razonSocial, String nombreComercial, String nit, String nrc,
                           String logoRuta) {
        public static Membrete de(String razonSocial, String nombreComercial, String nit, String nrc,
                                  String logoRuta) {
            return new Membrete(razonSocial, nombreComercial, nit, nrc, logoRuta);
        }
    }

    /**
     * Contenido de una exportación: título visible, etiqueta de período,
     * carpeta del período dentro del libro, nombre base de los archivos y la
     * tabla a imprimir.
     */
    public record Exportacion(String titulo, String periodo, String carpetaPeriodo, String nombreBase,
                              List<String> encabezados, List<? extends List<String>> filas) {
        /**
         * Crea una exportación con nombres derivados del título y el período.
         */
        public Exportacion(String titulo, String periodo, List<String> encabezados,
                           List<? extends List<String>> filas) {
            this(titulo, periodo, null, null, encabezados, filas);
        }

        String carpeta() {
            return carpetaPeriodo == null || carpetaPeriodo.isBlank()
                    ? "Periodo" : nombreArchivoSeguro(carpetaPeriodo);
        }

        String base() {
            if (nombreBase != null && !nombreBase.isBlank()) {
                return nombreArchivoSeguro(nombreBase);
            }
            String periodoSeguro = periodo == null || periodo.isBlank()
                    ? "Periodo" : nombreArchivoSeguro(periodo);
            return nombreArchivoSeguro(titulo) + "_" + periodoSeguro;
        }
    }

    public String estadoModulo() {
        return "Disponibles: diario, movimientos por cuenta, hoja de trabajo y resumen del período; "
            + "exportación a PDF y Excel.";
    }

    public Map<String, String> versionesBibliotecas() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("jasperreports", JasperCompileManager.class.getPackage().getImplementationVersion());
        map.put("poi", Workbook.class.getPackage().getImplementationVersion());
        return map;
    }

    /**
     * Genera un .xlsx mínimo de humo (verifica que POI resuelve en runtime).
     */
    public byte[] exportarExcelPrueba(String tituloHoja) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(tituloHoja == null || tituloHoja.isBlank() ? "Colibri" : tituloHoja);
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Colibrí — exportación de prueba");
            row.createCell(1).setCellValue("Etapa 4");
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Exporta balanza: cada fila [codigo, nombre, debe, haber, saldo]. */
    public byte[] exportarBalanzaExcel(java.util.List<String[]> filas) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Balanza");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Cuenta");
            header.createCell(1).setCellValue("Nombre");
            header.createCell(2).setCellValue("Debe");
            header.createCell(3).setCellValue("Haber");
            header.createCell(4).setCellValue("Saldo");
            int r = 1;
            for (String[] f : filas) {
                var row = sheet.createRow(r++);
                for (int c = 0; c < f.length; c++) {
                    row.createCell(c).setCellValue(f[c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Nombre base de archivo de una exportación individual.
     * <p>
     * Cuando el rango cubre un mes calendario completo usa
     * {@code Libro_Año_Mes}; en cualquier otro caso usa
     * {@code Libro_FechaInicio_al_FechaFin_FechaExportacion_HoraExportacion}.
     *
     * @param titulo    nombre del libro o reporte
     * @param desde     fecha inicial del filtro
     * @param hasta     fecha final del filtro
     * @param exportado fecha-hora de la exportación
     * @return nombre base sanitizado, sin extensión
     */
    public static String nombreBaseLibro(String titulo, LocalDate desde, LocalDate hasta,
                                         LocalDateTime exportado) {
        String libro = nombreArchivoSeguro(titulo);
        LocalDateTime momento = exportado == null ? LocalDateTime.now() : exportado;
        if (esMesCalendarioCompleto(desde, hasta)) {
            return libro + "_" + desde.getYear() + "_"
                    + String.format(Locale.ROOT, "%02d", desde.getMonthValue());
        }
        return libro + "_" + (desde == null ? "sininicio" : desde.format(FORMATO_FECHA))
                + "_al_" + (hasta == null ? "sffin" : hasta.format(FORMATO_FECHA))
                + "_" + momento.format(FORMATO_FECHA) + "_" + momento.format(FORMATO_HORA);
    }

    /**
     * Nombre de la carpeta de período para exportaciones por rango.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return etiqueta de carpeta sanitizada
     */
    public static String carpetaPeriodo(LocalDate desde, LocalDate hasta) {
        if (esMesCalendarioCompleto(desde, hasta)) {
            String mes = java.time.Month.of(desde.getMonthValue())
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es-SV"));
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
            return nombreArchivoSeguro(mes + "_" + desde.getYear());
        }
        return "Del_" + (desde == null ? "inicio" : desde.format(FORMATO_FECHA))
                + "_al_" + (hasta == null ? "fin" : hasta.format(FORMATO_FECHA));
    }

    private static boolean esMesCalendarioCompleto(LocalDate desde, LocalDate hasta) {
        return desde != null && hasta != null
                && desde.getDayOfMonth() == 1
                && desde.getMonth() == hasta.getMonth()
                && desde.getYear() == hasta.getYear()
                && hasta.equals(desde.withDayOfMonth(desde.lengthOfMonth()));
    }

    public byte[] exportarExcel(String titulo, String periodo, List<String> encabezados,
                                List<? extends List<String>> filas) throws IOException {
        return exportarExcel(titulo, periodo, encabezados, filas, null);
    }

    public byte[] exportarExcel(String titulo, String periodo, List<String> encabezados,
                                List<? extends List<String>> filas, Membrete membrete)
            throws IOException {
        validarTabla(encabezados);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String nombreHoja = titulo == null || titulo.isBlank() ? "Reporte" : titulo;
            nombreHoja = nombreHoja.replaceAll("[\\\\/?*\\[\\]:]", " ");
            var sheet = wb.createSheet(nombreHoja.substring(0, Math.min(31, nombreHoja.length())));

            CellStyle tituloStyle = wb.createCellStyle();
            Font tituloFont = wb.createFont();
            tituloFont.setBold(true);
            tituloFont.setFontHeightInPoints((short) 15);
            tituloStyle.setFont(tituloFont);
            tituloStyle.setWrapText(true);

            CellStyle subtituloStyle = wb.createCellStyle();
            Font subtituloFont = wb.createFont();
            subtituloFont.setItalic(true);
            subtituloFont.setFontHeightInPoints((short) 10);
            subtituloStyle.setFont(subtituloFont);
            subtituloStyle.setWrapText(true);

            int filaPeriodo;
            if (membrete != null) {
                int fila = 0;
                String nombreVisible = membrete.nombreComercial() != null
                        && !membrete.nombreComercial().isBlank()
                        ? membrete.nombreComercial() : membrete.razonSocial();
                var filaTitulo = sheet.createRow(fila++);
                var celdaTitulo = filaTitulo.createCell(0);
                celdaTitulo.setCellValue(nombreVisible == null ? titulo : nombreVisible);
                celdaTitulo.setCellStyle(tituloStyle);
                if (encabezados.size() > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 0, encabezados.size() - 1));
                }
                String identificacion = "NIT: " + nvl(membrete.nit()) + "   NRC: " + nvl(membrete.nrc());
                if ((membrete.razonSocial() != null && !membrete.razonSocial().isBlank()
                        && !membrete.razonSocial().equals(nombreVisible))) {
                    identificacion = membrete.razonSocial() + "  ·  " + identificacion;
                }
                var filaId = sheet.createRow(fila++);
                var celdaId = filaId.createCell(0);
                celdaId.setCellValue(identificacion);
                celdaId.setCellStyle(subtituloStyle);
                if (encabezados.size() > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 0, encabezados.size() - 1));
                }
                insertarLogoExcel(sheet, wb, membrete.logoRuta(), encabezados.size());
                filaPeriodo = 3;
            } else {
                var filaTitulo = sheet.createRow(0);
                var celdaTitulo = filaTitulo.createCell(0);
                celdaTitulo.setCellValue(titulo);
                celdaTitulo.setCellStyle(tituloStyle);
                if (encabezados.size() > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, encabezados.size() - 1));
                }
                filaPeriodo = 1;
            }

            var periodoRow = sheet.createRow(filaPeriodo);
            periodoRow.createCell(0).setCellValue(periodo == null ? "" : periodo);

            CellStyle encabezadoStyle = wb.createCellStyle();
            Font encabezadoFont = wb.createFont();
            encabezadoFont.setBold(true);
            encabezadoStyle.setFont(encabezadoFont);
            encabezadoStyle.setWrapText(true);
            encabezadoStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            CellStyle textoStyle = wb.createCellStyle();
            textoStyle.setWrapText(true);
            textoStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            CellStyle importeStyle = wb.createCellStyle();
            importeStyle.setDataFormat(wb.createDataFormat()
                .getFormat("$#,##0.00;[Red]-$#,##0.00"));
            importeStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            int filaEncabezado = membrete != null ? 5 : 3;
            var header = sheet.createRow(filaEncabezado);
            for (int i = 0; i < encabezados.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(encabezados.get(i));
                cell.setCellStyle(encabezadoStyle);
            }
            header.setHeight((short) 30);

            int rowIndex = filaEncabezado + 1;
            for (List<String> fila : filas) {
                var row = sheet.createRow(rowIndex);
                int lineas = 1;
                for (int i = 0; i < encabezados.size(); i++) {
                    String valor = i < fila.size() ? fila.get(i) : "";
                    var cell = row.createCell(i);
                    if (esColumnaMonetaria(encabezados.get(i)) && !valor.isBlank()) {
                        try {
                            cell.setCellValue(new BigDecimal(valor).doubleValue());
                            cell.setCellStyle(importeStyle);
                            continue;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    cell.setCellValue(valor);
                    cell.setCellStyle(textoStyle);
                    lineas = Math.max(lineas, lineasEstimadas(valor, anchoColumnaExcel(encabezados.get(i))));
                }
                row.setHeight((short) Math.max(15, lineas * 14));
                rowIndex++;
            }
            if (!filas.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(filaEncabezado, rowIndex - 1, 0, encabezados.size() - 1));
            }
            sheet.createFreezePane(0, filaEncabezado + 1);
            for (int i = 0; i < encabezados.size(); i++) {
                int ancho = Math.min(autoSizeSeguro(sheet, i), 60 * 256);
                sheet.setColumnWidth(i, Math.max(ancho, 10 * 256));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int autoSizeSeguro(org.apache.poi.ss.usermodel.Sheet sheet, int columna) {
        try {
            sheet.autoSizeColumn(columna);
            return sheet.getColumnWidth(columna);
        } catch (RuntimeException ex) {
            return 24 * 256;
        }
    }

    private static int anchoColumnaExcel(String encabezado) {
        String nombre = encabezado == null ? "" : encabezado.toLowerCase(Locale.ROOT);
        if (nombre.contains("concepto") || nombre.contains("cuenta")) {
            return 48;
        }
        if (nombre.contains("fecha") || nombre.contains("folio") || nombre.contains("código")
                || nombre.contains("codigo") || nombre.contains("nit") || nombre.contains("tipo")) {
            return 14;
        }
        return nombre.contains("usd") || nombre.contains("importe") || nombre.contains("saldo")
                || nombre.contains("margen") || nombre.contains("costo") || nombre.contains("total")
                ? 16 : 28;
    }

    private static int lineasEstimadas(String texto, int anchoCaracteres) {
        if (texto == null || texto.isBlank()) {
            return 1;
        }
        int saltos = texto.split("\\R", -1).length;
        int mayor = 0;
        for (String parte : texto.split("\\R", -1)) {
            mayor = Math.max(mayor, (int) Math.ceil(parte.length() / (double) Math.max(8, anchoCaracteres)));
        }
        return Math.max(1, saltos + mayor - 1);
    }

    private void insertarLogoExcel(org.apache.poi.ss.usermodel.Sheet sheet, Workbook wb,
                                   String logoRuta, int columnas) {
        if (logoRuta == null || logoRuta.isBlank()) {
            return;
        }
        try {
            File archivo = new File(logoRuta);
            if (!archivo.isFile()) {
                return;
            }
            byte[] bytes = Files.readAllBytes(archivo.toPath());
            int tipo = logoRuta.toLowerCase(Locale.ROOT).endsWith(".png")
                    ? Workbook.PICTURE_TYPE_PNG : Workbook.PICTURE_TYPE_JPEG;
            int pictureIndex = wb.addPicture(bytes, tipo);
            BufferedImage imagen = ImageIO.read(archivo);
            if (imagen == null) {
                return;
            }
            int columnaInicial = Math.max(0, columnas - 2);
            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0,
                    (short) columnaInicial, 0, (short) (columnas - 1), 2);
            XSSFDrawing drawing = ((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).createDrawingPatriarch();
            drawing.createPicture(anchor, pictureIndex);
        } catch (Exception ignored) {
            // El membrete sin logo sigue siendo válido; nunca se bloquea la exportación.
        }
    }

    private static String nvl(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    /**
     * Crea una carpeta de exportación con una subcarpeta por libro contable.
     * Cada subcarpeta contiene el Excel correspondiente al período solicitado.
     */
    public Path exportarVariosExcel(Path carpetaDestino, String nombrePaquete,
                                    List<Exportacion> exportaciones) throws IOException {
        if (carpetaDestino == null || nombrePaquete == null || nombrePaquete.isBlank()) {
            throw new IllegalArgumentException("La carpeta y el nombre del paquete son obligatorios.");
        }
        if (exportaciones == null || exportaciones.isEmpty()) {
            throw new IllegalArgumentException("Debe incluir al menos un libro para exportar.");
        }

        Path paquete = carpetaDestino.resolve(nombreRuta(nombrePaquete));
        Files.createDirectories(paquete);
        for (Exportacion exportacion : exportaciones) {
            if (exportacion == null || exportacion.titulo() == null || exportacion.titulo().isBlank()) {
                throw new IllegalArgumentException("Cada exportación debe tener un título.");
            }
            String titulo = nombreRuta(exportacion.titulo());
            Path carpetaLibro = paquete.resolve(titulo);
            Files.createDirectories(carpetaLibro);
            Path carpetaPeriodo = carpetaLibro.resolve(exportacion.carpeta());
            Files.createDirectories(carpetaPeriodo);
            String nombreArchivo = exportacion.base() + ".xlsx";
            Files.write(carpetaPeriodo.resolve(nombreArchivo),
                    exportarExcel(exportacion.titulo(), exportacion.periodo(),
                            exportacion.encabezados(), exportacion.filas()));
        }
        return paquete;
    }

    /**
     * Crea la carpeta de exportación con la jerarquía
     * Paquete → Libro → Período → par PDF/XLSX. Cada período tiene su propia
     * carpeta con el Excel y el PDF del corte solicitado.
     */
    public Path exportarVarios(Path carpetaDestino, String nombrePaquete,
                               List<Exportacion> exportaciones) throws Exception {
        return exportarVarios(carpetaDestino, nombrePaquete, exportaciones, null, null);
    }

    /**
     * Igual que {@link #exportarVarios(Path, String, List)} pero admite un
     * membrete institucional y reporta el avance por cada archivo generado.
     *
     * @param progreso recibe mensajes "Libro · Período (n/total)" para la UI
     */
    public Path exportarVarios(Path carpetaDestino, String nombrePaquete,
                               List<Exportacion> exportaciones, Membrete membrete,
                               Consumer<String> progreso) throws Exception {
        Path paquete = prepararPaquete(carpetaDestino, nombrePaquete, exportaciones);
        int total = exportaciones.size() * 2;
        int indice = 0;
        for (Exportacion exportacion : exportaciones) {
            Path carpetaLibro = carpetaLibro(paquete, exportacion);
            Path carpetaPeriodo = carpetaLibro.resolve(exportacion.carpeta());
            Files.createDirectories(carpetaPeriodo);
            String base = exportacion.base();
            String etiqueta = exportacion.titulo() + " · " + exportacion.carpeta();
            if (progreso != null) {
                progreso.accept("Generando " + etiqueta + " (" + (++indice) + "/" + total + ")");
            }
            Files.write(carpetaPeriodo.resolve(base + ".xlsx"),
                    exportarExcel(exportacion.titulo(), exportacion.periodo(),
                            exportacion.encabezados(), exportacion.filas(), membrete));
            if (progreso != null) {
                progreso.accept("Generando " + etiqueta + " (" + (++indice) + "/" + total + ")");
            }
            Files.write(carpetaPeriodo.resolve(base + ".pdf"),
                    exportarPdf(exportacion.titulo(), exportacion.periodo(),
                            exportacion.encabezados(), exportacion.filas(), membrete));
        }
        return paquete;
    }

    private Path prepararPaquete(Path carpetaDestino, String nombrePaquete,
                                 List<Exportacion> exportaciones) throws IOException {
        if (carpetaDestino == null || nombrePaquete == null || nombrePaquete.isBlank()) {
            throw new IllegalArgumentException("La carpeta y el nombre del paquete son obligatorios.");
        }
        if (exportaciones == null || exportaciones.isEmpty()) {
            throw new IllegalArgumentException("Debe incluir al menos un libro para exportar.");
        }
        Path paquete = carpetaDestino.resolve(nombreRuta(nombrePaquete));
        Files.createDirectories(paquete);
        return paquete;
    }

    private Path carpetaLibro(Path paquete, Exportacion exportacion) throws IOException {
        if (exportacion == null || exportacion.titulo() == null || exportacion.titulo().isBlank()) {
            throw new IllegalArgumentException("Cada exportación debe tener un título.");
        }
        Path carpetaLibro = paquete.resolve(nombreRuta(exportacion.titulo()));
        Files.createDirectories(carpetaLibro);
        return carpetaLibro;
    }

    public byte[] exportarPdf(String titulo, String periodo, List<String> encabezados,
                              List<? extends List<String>> filas) throws Exception {
        return exportarPdf(titulo, periodo, encabezados, filas, null);
    }

    public byte[] exportarPdf(String titulo, String periodo, List<String> encabezados,
                              List<? extends List<String>> filas, Membrete membrete)
            throws Exception {
        validarTabla(encabezados);
        // La hoja de trabajo integra catorce columnas; un A4 apaisado la vuelve ilegible.
        // Los libros de hasta diez columnas conservan A4 y los de mayor ancho se emiten en A3.
        boolean formatoAncho = encabezados.size() > 10;
        int anchoPagina = formatoAncho ? 1191 : 842;
        int altoPagina = formatoAncho ? 842 : 595;
        int margen = 20;
        int anchoContenido = anchoPagina - margen * 2;
        int columnas = encabezados.size();
        // Anchos proporcionales: las columnas de texto amplio reciben más espacio.
        double[] pesos = new double[columnas];
        double sumaPesos = 0;
        for (int i = 0; i < columnas; i++) {
            String nombre = encabezados.get(i) == null ? ""
                    : encabezados.get(i).toLowerCase(Locale.ROOT);
            pesos[i] = nombre.contains("concepto") || nombre.contains("cuenta") ? 2.4
                    : nombre.contains("fecha") || nombre.contains("folio") || nombre.contains("código")
                    || nombre.contains("codigo") || nombre.contains("nit") || nombre.contains("tipo")
                    || nombre.contains("ref") ? 1.2
                    : nombre.contains("usd") || nombre.contains("importe") || nombre.contains("saldo")
                    || nombre.contains("margen") || nombre.contains("costo") || nombre.contains("total")
                    ? 1.1 : 1.6;
            sumaPesos += pesos[i];
        }
        int[] anchos = new int[columnas];
        int acumulado = 0;
        for (int i = 0; i < columnas; i++) {
            anchos[i] = i == columnas - 1
                    ? anchoContenido - acumulado
                    : (int) Math.round(anchoContenido * pesos[i] / sumaPesos);
            acumulado += anchos[i];
        }
        int[] posiciones = new int[columnas + 1];
        for (int i = 0; i < columnas; i++) {
            posiciones[i + 1] = posiciones[i] + anchos[i];
        }

        JasperDesign design = new JasperDesign();
        design.setName("colibri");
        design.setPageWidth(anchoPagina);
        design.setPageHeight(altoPagina);
        design.setColumnWidth(anchoContenido);
        design.setLeftMargin(margen);
        design.setRightMargin(margen);
        design.setTopMargin(margen);
        design.setBottomMargin(margen);

        JRDesignStyle tituloStyle = new JRDesignStyle();
        tituloStyle.setName("titulo");
        tituloStyle.setFontSize(15f);
        tituloStyle.setBold(true);
        design.addStyle(tituloStyle);

        JRDesignStyle datoStyle = new JRDesignStyle();
        datoStyle.setName("dato");
        datoStyle.setFontSize(8f);
        design.addStyle(datoStyle);

        JRDesignStyle encabezadoStyle = new JRDesignStyle();
        encabezadoStyle.setName("encabezado");
        encabezadoStyle.setFontSize(8f);
        encabezadoStyle.setBold(true);
        design.addStyle(encabezadoStyle);

        JRDesignStyle periodoStyle = new JRDesignStyle();
        periodoStyle.setName("periodo");
        periodoStyle.setFontSize(9f);
        design.addStyle(periodoStyle);

        JRDesignStyle identificacionStyle = new JRDesignStyle();
        identificacionStyle.setName("identificacion");
        identificacionStyle.setFontSize(8f);
        design.addStyle(identificacionStyle);

        int maxCharsPorColumna = Math.max(10, (int) (anchoContenido / (columnas * 5.4)));

        for (int i = 0; i < encabezados.size(); i++) {
            JRDesignField field = new JRDesignField();
            field.setName("f" + i);
            field.setValueClass(String.class);
            field.setValueClassName("java.lang.String");
            design.addField(field);
        }

        JRDesignBand titleBand = new JRDesignBand();
        int cursorY = 0;
        boolean conLogo = membrete != null && logoValido(membrete.logoRuta());
        int tituloX = conLogo ? 150 : 0;
        int tituloAncho = anchoContenido - (conLogo ? 150 : 0);
        if (conLogo) {
            JRDesignImage logo = new JRDesignImage(design);
            logo.setX(0);
            logo.setY(0);
            logo.setWidth(140);
            logo.setHeight(64);
            logo.setScaleImage(ScaleImageEnum.RETAIN_SHAPE);
            logo.setHorizontalImageAlign(HorizontalImageAlignEnum.LEFT);
            logo.setVerticalImageAlign(VerticalImageAlignEnum.TOP);
            logo.setExpression(new JRDesignExpression("$P{LOGO}"));
            logo.setEvaluationTime(net.sf.jasperreports.engine.type.EvaluationTimeEnum.REPORT);
            titleBand.addElement(logo);
        }
        if (membrete != null) {
            String nombreVisible = membrete.nombreComercial() != null
                    && !membrete.nombreComercial().isBlank()
                    ? membrete.nombreComercial() : membrete.razonSocial();
            List<String> lineasTitulo = envolverTexto(
                    nombreVisible == null || nombreVisible.isBlank() ? titulo : nombreVisible,
                    Math.max(20, tituloAncho / 8));
            JRDesignStaticText empresa = staticText(tituloX, cursorY, tituloAncho, 15 * lineasTitulo.size(),
                    String.join("\n", lineasTitulo));
            empresa.setStyle(tituloStyle);
            titleBand.addElement(empresa);
            cursorY += 15 * lineasTitulo.size() + 2;
            String razonAdicional = membrete.razonSocial() != null
                    && !membrete.razonSocial().isBlank()
                    && !membrete.razonSocial().equals(nombreVisible) ? membrete.razonSocial() : null;
            String identificacion = (razonAdicional == null ? "" : razonAdicional + "  ·  ")
                    + "NIT: " + nvl(membrete.nit()) + "   NRC: " + nvl(membrete.nrc());
            JRDesignStaticText idText = staticText(tituloX, cursorY, tituloAncho, 14,
                    envolverLinea(identificacion, Math.max(20, tituloAncho / 6)));
            idText.setStyle(identificacionStyle);
            titleBand.addElement(idText);
            cursorY += 16;
        } else {
            List<String> lineasTitulo = envolverTexto(titulo == null ? "" : titulo,
                    Math.max(20, anchoContenido / 9));
            JRDesignStaticText titleText = staticText(0, cursorY, anchoContenido, 15 * lineasTitulo.size(),
                    String.join("\n", lineasTitulo));
            titleText.setStyle(tituloStyle);
            titleBand.addElement(titleText);
            cursorY += 15 * lineasTitulo.size() + 2;
        }
        List<String> lineasPeriodo = envolverTexto(periodo == null ? "" : periodo,
                Math.max(20, anchoContenido / 7));
        JRDesignStaticText periodoText = staticText(0, cursorY, anchoContenido, 13 * lineasPeriodo.size(),
                String.join("\n", lineasPeriodo));
        periodoText.setStyle(periodoStyle);
        titleBand.addElement(periodoText);
        cursorY += 13 * lineasPeriodo.size() + 6;
        titleBand.setHeight(cursorY);
        design.setTitle(titleBand);

        JRDesignBand headerBand = new JRDesignBand();
        int alturaEncabezado = 0;
        for (int i = 0; i < encabezados.size(); i++) {
            String encabezado = envolverLinea(encabezados.get(i), Math.max(6, anchos[i] / 5));
            int lineas = encabezado.split("\\R", -1).length;
            int altura = 28 * Math.min(lineas, 2);
            JRDesignStaticText header = staticText(posiciones[i], 0, anchos[i], altura,
                    encabezado);
            header.setStyle(encabezadoStyle);
            header.setBackcolor(new Color(231, 239, 236));
            header.setMode(ModeEnum.OPAQUE);
            header.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            headerBand.addElement(header);
            alturaEncabezado = Math.max(alturaEncabezado, altura);
        }
        headerBand.setHeight(Math.max(30, alturaEncabezado));
        design.setColumnHeader(headerBand);

        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(18);
        for (int i = 0; i < encabezados.size(); i++) {
            JRDesignTextField field = new JRDesignTextField();
            field.setX(posiciones[i]);
            field.setY(0);
            field.setWidth(anchos[i]);
            field.setHeight(17);
            field.setStyle(datoStyle);
            field.setTextAdjust(net.sf.jasperreports.engine.type.TextAdjustEnum.STRETCH_HEIGHT);
            field.setExpression(new JRDesignExpression("$F{f" + i + "}"));
            detailBand.addElement(field);
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        List<Map<String, ?>> registros = new ArrayList<>();
        for (List<String> fila : filas) {
            Map<String, Object> registro = new HashMap<>();
            for (int i = 0; i < encabezados.size(); i++) {
                String valor = i < fila.size() ? fila.get(i) : "";
                int capacidad = Math.max(8, anchos[i] / 5 - 1);
                registro.put("f" + i, String.join("\n", envolverTexto(valor, capacidad)));
            }
            registros.add(registro);
        }
        Map<String, Object> parametros = new HashMap<>();
        if (membrete != null && logoValido(membrete.logoRuta())) {
            try {
                BufferedImage imagen = ImageIO.read(new File(membrete.logoRuta()));
                if (imagen != null) {
                    parametros.put("LOGO", imagen);
                }
            } catch (Exception ignored) {
                // PDF sin logo si la imagen no puede leerse.
            }
        }
        JasperReport reporte = JasperCompileManager.compileReport(design);
        JasperPrint impreso = JasperFillManager.fillReport(
            reporte, parametros, new JRMapCollectionDataSource(registros));
        return JasperExportManager.exportReportToPdf(impreso);
    }

    private static boolean logoValido(String logoRuta) {
        if (logoRuta == null || logoRuta.isBlank()) {
            return false;
        }
        String ruta = logoRuta.trim().toLowerCase(Locale.ROOT);
        return ruta.endsWith(".png") || ruta.endsWith(".jpg") || ruta.endsWith(".jpeg");
    }

    /**
     * Envuelve un texto en líneas de aproximadamente {@code maxCaracteres},
     * cortando en espacios cuando es posible para no partir palabras.
     */
    static List<String> envolverTexto(String texto, int maxCaracteres) {
        if (texto == null || texto.isBlank()) {
            return List.of("");
        }
        List<String> lineas = new ArrayList<>();
        for (String bloque : texto.split("\\R", -1)) {
            String resto = bloque;
            while (resto.length() > maxCaracteres) {
                int corte = resto.lastIndexOf(' ', maxCaracteres);
                if (corte < maxCaracteres / 3) {
                    corte = maxCaracteres;
                }
                lineas.add(resto.substring(0, corte).trim());
                resto = resto.substring(corte).trim();
            }
            lineas.add(resto);
        }
        return lineas;
    }

    private static String envolverLinea(String texto, int maxCaracteres) {
        return String.join("\n", envolverTexto(texto, maxCaracteres));
    }

    private void validarTabla(List<String> encabezados) {
        if (encabezados == null || encabezados.isEmpty()) {
            throw new IllegalArgumentException("El reporte debe incluir al menos una columna.");
        }
    }

    private String nombreRuta(String valor) {
        return nombreArchivoSeguro(valor);
    }

    /**
     * Sanitiza un texto para usarlo como nombre de archivo o carpeta:
     * quita caracteres prohibidos, colapsa espacios y subrayados.
     */
    public static String nombreArchivoSeguro(String valor) {
        if (valor == null) {
            return "";
        }
        String nombre = valor.trim().replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", "_").replaceAll("_+", "_");
        return nombre.replaceAll("^\\.+|\\.+$", "");
    }

    private boolean esColumnaMonetaria(String encabezado) {
        String nombre = encabezado.toLowerCase(java.util.Locale.ROOT);
        return nombre.contains("usd") || nombre.contains("importe") || nombre.contains("saldo")
                || nombre.contains("margen") || nombre.contains("costo") || nombre.contains("total");
    }

    private JRDesignStaticText staticText(int x, int y, int width, int height, String text) {
        JRDesignStaticText element = new JRDesignStaticText();
        element.setX(x);
        element.setY(y);
        element.setWidth(width);
        element.setHeight(height);
        element.setText(text == null ? "" : text);
        return element;
    }
}
