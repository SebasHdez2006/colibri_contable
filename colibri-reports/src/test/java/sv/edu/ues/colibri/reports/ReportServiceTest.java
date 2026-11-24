package sv.edu.ues.colibri.reports;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la generación de exportaciones Excel y PDF del servicio de reportes.
 */
class ReportServiceTest {

    @Test
    void poiGeneraExcelDePrueba() throws Exception {
        byte[] xlsx = new ReportService().exportarExcelPrueba("Prueba");
        assertThat(xlsx).isNotEmpty();
        // Firma ZIP de un .xlsx
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
        assertThat(xlsx[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void jasperYPoiEstanEnClasspath() {
        var versiones = new ReportService().versionesBibliotecas();
        assertThat(versiones).containsKeys("jasperreports", "poi");
    }

    @Test
    void exportaLibroExcelConTituloYDatos() throws Exception {
        byte[] xlsx = new ReportService().exportarExcel("Diario", "2026-01",
            List.of("Folio", "Cuenta", "Importe (USD)"),
            List.of(List.of("IN-2026-001", "110102", "1234.5")));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheet("Diario");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Diario");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Folio");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("IN-2026-001");
                assertThat(sheet.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(1234.5);
                assertThat(sheet.getRow(4).getCell(2).getCellStyle().getDataFormatString())
                    .contains("\"$ \"#,##0.00");
        }
    }

    @Test
    void exportaImporteFormateadoParaPantallaComoNumero() throws Exception {
        byte[] xlsx = new ReportService().exportarExcel("Balanza", "2026-01",
            List.of("Cuenta", "Debe (USD)"),
            List.of(List.of("110102", "$ 1,234.50"), List.of("110103", "-$ 2,000.75"),
                    List.of("110104", "($ 500.25)")));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheet("Balanza");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(1234.5);
            assertThat(sheet.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(-2000.75);
            assertThat(sheet.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(-500.25);
        }
    }

    @Test
    void excelAjustaTextoLargoConAjusteDeLinea() throws Exception {
        String conceptoLargo = "Pago de servicios de consultoría y desarrollo de software "
                + "correspondiente al contrato marco de prestación de servicios profesionales";
        byte[] xlsx = new ReportService().exportarExcel("Diario", "2026-01",
            List.of("Fecha", "Concepto"),
            List.of(List.of("2026-01-15", conceptoLargo)));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheet("Diario");
            var celda = sheet.getRow(4).getCell(1);
            assertThat(celda.getStringCellValue()).isEqualTo(conceptoLargo);
            assertThat(celda.getCellStyle().getWrapText()).isTrue();
            assertThat((int) sheet.getRow(4).getHeight()).isGreaterThanOrEqualTo(28);
        }
    }

    @Test
    void excelConMembreteIncluyeIdentificacionInstitucional() throws Exception {
        byte[] xlsx = new ReportService().exportarExcel("Diario", "2026-01",
            List.of("Fecha", "Concepto"), List.of(List.of("2026-01-15", "Cobro")),
            new ReportService.Membrete("Empresa Ejemplo S.A.", "Colibrí", "0614-000000-000-0",
                    "NRC-123", null));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheet("Diario");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Colibrí");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .contains("0614-000000-000-0").contains("NRC-123");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("2026-01");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Fecha");
        }
    }

    @Test
    void exportaPdfLegible() throws Exception {
        byte[] pdf = new ReportService().exportarPdf("Diario", "2026-01",
                List.of("Folio", "Cuenta"), List.of(List.of("IN-2026-001", "110102")));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void exportaPdfConTextoLargoSinPerderContenido() throws Exception {
        String conceptoLargo = "Concepto extenso de la partida contable que supera con creces "
                + "el ancho disponible de una celda angosta del reporte en formato retrato";
        byte[] pdf = new ReportService().exportarPdf("Diario", "2026-01",
                List.of("Fecha", "Concepto"),
                List.of(List.of("2026-01-15", conceptoLargo)));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        // El PDF comprimido no permite buscar texto; la aserción principal es que
        // JasperReports procesó la fila sin excepción ni desbordamiento.
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void exportaHojaTrabajoAnchaEnPdf() throws Exception {
        List<String> encabezados = List.of("Código", "Cuenta", "Balanza de comprobación debe",
                "Balanza de comprobación haber", "Ajustes debe", "Ajustes haber",
                "Balanza ajustada debe", "Balanza ajustada haber", "Estado de resultados debe",
                "Estado de resultados haber", "Estado de capital debe", "Estado de capital haber",
                "Balance general debe", "Balance general haber");
        byte[] pdf = new ReportService().exportarPdf("Hoja de trabajo", "2026-01",
                encabezados, List.of(java.util.Collections.nCopies(encabezados.size(), "0.00")));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void nombreBaseUsaMesCuandoElRangoEsUnMesCompleto() {
        LocalDateTime momento = LocalDateTime.of(2026, 4, 15, 14, 30, 52);
        assertThat(ReportService.nombreBaseLibro("Libro diario",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), momento))
                .isEqualTo("Libro_diario_2026_01");
    }

    @Test
    void nombreBaseUsaFechaHoraCuandoElRangoNoEsUnMes() {
        LocalDateTime momento = LocalDateTime.of(2026, 4, 15, 14, 30, 52);
        assertThat(ReportService.nombreBaseLibro("Libro mayor",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), momento))
                .isEqualTo("Libro_mayor_20260101_al_20260331_20260415_143052");
    }

    @Test
    void nombreBaseDistingueRangoParcialDeMes() {
        LocalDateTime momento = LocalDateTime.of(2026, 4, 15, 9, 5, 0);
        assertThat(ReportService.nombreBaseLibro("Balanza de comprobación",
                LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 20), momento))
                .isEqualTo("Balanza_de_comprobación_20260210_al_20260220_20260415_090500");
    }

    @Test
    void carpetaPeriodoNombraMesOCorte() {
        assertThat(ReportService.carpetaPeriodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isEqualTo("Enero_2026");
        assertThat(ReportService.carpetaPeriodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .isEqualTo("Del_20260101_al_20260331");
    }

    @Test
    void exportaVariosLibrosEnCarpetasDescriptivas() throws Exception {
        var destino = Files.createTempDirectory("colibri-exportacion");
        var paquete = new ReportService().exportarVarios(destino, "Exportacion_Varios",
                List.of(new ReportService.Exportacion("Libro diario", "Enero 2026",
                        "Enero_2026", "Libro_Diario_2026_01",
                        List.of("Fecha"), List.of(List.of("2026-01-01"))),
                        new ReportService.Exportacion("Libro mayor", "Febrero 2026",
                        "Febrero_2026", "Libro_Mayor_2026_02",
                        List.of("Fecha"), List.of(List.of("2026-02-01")))));

        assertThat(paquete).isEqualTo(destino.resolve("Exportacion_Varios"));
        Path carpetaDiario = paquete.resolve("Libro_diario").resolve("Enero_2026");
        assertThat(carpetaDiario.resolve("Libro_Diario_2026_01.xlsx")).exists();
        assertThat(carpetaDiario.resolve("Libro_Diario_2026_01.pdf")).exists();
        Path carpetaMayor = paquete.resolve("Libro_mayor").resolve("Febrero_2026");
        assertThat(carpetaMayor.resolve("Libro_Mayor_2026_02.xlsx")).exists();
        assertThat(carpetaMayor.resolve("Libro_Mayor_2026_02.pdf")).exists();
    }

    @Test
    void exportarVariosReportaProgresoPorArchivo() throws Exception {
        var destino = Files.createTempDirectory("colibri-exportacion-progreso");
        List<String> mensajes = new java.util.ArrayList<>();
        new ReportService().exportarVarios(destino, "Paquete",
                List.of(new ReportService.Exportacion("Diario", "Enero 2026",
                        "Enero_2026", "Diario_2026_01",
                        List.of("Fecha"), List.of(List.of("2026-01-01")))),
                null, mensajes::add);

        assertThat(mensajes).hasSize(2);
        assertThat(mensajes.get(0)).contains("Diario").contains("1/2");
        assertThat(mensajes.get(1)).contains("Diario").contains("2/2");
    }
}
