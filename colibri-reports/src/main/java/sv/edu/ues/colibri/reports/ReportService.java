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
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fachada de reportes. Las plantillas JRXML y libros contables completos se
 * implementan en la etapa 4; aquí se fijan JasperReports (PDF) y Apache POI (Excel).
 */
public class ReportService {

    public record Exportacion(String titulo, String periodo, List<String> encabezados,
                              List<? extends List<String>> filas) {
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

    public byte[] exportarExcel(String titulo, String periodo, List<String> encabezados,
                                List<? extends List<String>> filas) throws IOException {
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
            var tituloRow = sheet.createRow(0);
            tituloRow.createCell(0).setCellValue(titulo);
            tituloRow.getCell(0).setCellStyle(tituloStyle);
            var periodoRow = sheet.createRow(1);
            periodoRow.createCell(0).setCellValue(periodo == null ? "" : periodo);

            CellStyle encabezadoStyle = wb.createCellStyle();
            Font encabezadoFont = wb.createFont();
            encabezadoFont.setBold(true);
            encabezadoStyle.setFont(encabezadoFont);
                CellStyle importeStyle = wb.createCellStyle();
                importeStyle.setDataFormat(wb.createDataFormat()
                    .getFormat("$#,##0.00;[Red]-$#,##0.00"));
            var header = sheet.createRow(3);
            for (int i = 0; i < encabezados.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(encabezados.get(i));
                cell.setCellStyle(encabezadoStyle);
            }

            int rowIndex = 4;
            for (List<String> fila : filas) {
                var row = sheet.createRow(rowIndex++);
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
                }
            }
            if (!filas.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(3, rowIndex - 1, 0, encabezados.size() - 1));
            }
            sheet.createFreezePane(0, 4);
            for (int i = 0; i < encabezados.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i), 45 * 256));
            }
            wb.write(out);
            return out.toByteArray();
        }
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
            String periodo = nombreRuta(exportacion.periodo());
            Path carpetaLibro = paquete.resolve(titulo);
            Files.createDirectories(carpetaLibro);
            String nombreArchivo = titulo + "_" + (periodo.isBlank() ? "Periodo" : periodo) + ".xlsx";
            Files.write(carpetaLibro.resolve(nombreArchivo),
                    exportarExcel(exportacion.titulo(), exportacion.periodo(),
                            exportacion.encabezados(), exportacion.filas()));
        }
        return paquete;
    }

    public byte[] exportarPdf(String titulo, String periodo, List<String> encabezados,
                              List<? extends List<String>> filas) throws Exception {
        validarTabla(encabezados);
        // La hoja de trabajo integra catorce columnas; un A4 apaisado la vuelve ilegible.
        // Los libros de hasta diez columnas conservan A4 y los de mayor ancho se emiten en A3.
        boolean formatoAncho = encabezados.size() > 10;
        int anchoPagina = formatoAncho ? 1191 : 842;
        int altoPagina = formatoAncho ? 842 : 595;
        int anchoContenido = anchoPagina - 40;
        int anchoColumna = anchoContenido / encabezados.size();
        JasperDesign design = new JasperDesign();
        design.setName("colibri");
        design.setPageWidth(anchoPagina);
        design.setPageHeight(altoPagina);
        design.setColumnWidth(anchoContenido);
        design.setLeftMargin(20);
        design.setRightMargin(20);
        design.setTopMargin(20);
        design.setBottomMargin(20);

        JRDesignStyle tituloStyle = new JRDesignStyle();
        tituloStyle.setName("titulo");
        tituloStyle.setFontSize(16f);
        tituloStyle.setBold(true);
        design.addStyle(tituloStyle);

        for (int i = 0; i < encabezados.size(); i++) {
            JRDesignField field = new JRDesignField();
            field.setName("f" + i);
            field.setValueClass(String.class);
            design.addField(field);
        }

        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(52);
        JRDesignStaticText titleText = staticText(0, 0, anchoContenido, 26, titulo);
        titleText.setStyle(tituloStyle);
        titleBand.addElement(titleText);
        titleBand.addElement(staticText(0, 29, anchoContenido, 18, periodo));
        design.setTitle(titleBand);

        JRDesignBand headerBand = new JRDesignBand();
        headerBand.setHeight(24);
        for (int i = 0; i < encabezados.size(); i++) {
            JRDesignStaticText header = staticText(i * anchoColumna, 0, anchoColumna, 22,
                    encabezados.get(i));
            header.setBackcolor(new Color(231, 239, 236));
            header.setMode(net.sf.jasperreports.engine.type.ModeEnum.OPAQUE);
            header.setBold(true);
            headerBand.addElement(header);
        }
        design.setColumnHeader(headerBand);

        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(20);
        for (int i = 0; i < encabezados.size(); i++) {
            JRDesignTextField field = new JRDesignTextField();
            field.setX(i * anchoColumna);
            field.setY(0);
            field.setWidth(anchoColumna);
            field.setHeight(19);
            field.setExpression(new JRDesignExpression("$F{f" + i + "}"));
            detailBand.addElement(field);
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        List<Map<String, ?>> registros = new ArrayList<>();
        for (List<String> fila : filas) {
            Map<String, Object> registro = new HashMap<>();
            for (int i = 0; i < encabezados.size(); i++) {
                registro.put("f" + i, i < fila.size() ? fila.get(i) : "");
            }
            registros.add(registro);
        }
        JasperReport reporte = JasperCompileManager.compileReport(design);
        JasperPrint impreso = JasperFillManager.fillReport(
            reporte, new HashMap<>(), new JRMapCollectionDataSource(registros));
        return JasperExportManager.exportReportToPdf(impreso);
    }

    private void validarTabla(List<String> encabezados) {
        if (encabezados == null || encabezados.isEmpty()) {
            throw new IllegalArgumentException("El reporte debe incluir al menos una columna.");
        }
    }

    private String nombreRuta(String valor) {
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
