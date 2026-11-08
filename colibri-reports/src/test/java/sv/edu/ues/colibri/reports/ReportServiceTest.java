package sv.edu.ues.colibri.reports;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
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
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("IN-2026-001");
                assertThat(sheet.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(1234.5);
                assertThat(sheet.getRow(4).getCell(2).getCellStyle().getDataFormatString())
                    .contains("$#,##0.00");
        }
    }

    @Test
    void exportaPdfLegible() throws Exception {
        byte[] pdf = new ReportService().exportarPdf("Diario", "2026-01",
                List.of("Folio", "Cuenta"), List.of(List.of("IN-2026-001", "110102")));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
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
    void exportaVariosLibrosEnCarpetasDescriptivas() throws Exception {
        var destino = Files.createTempDirectory("colibri-exportacion");
        var paquete = new ReportService().exportarVariosExcel(destino, "Exportacion_Varios",
                List.of(new ReportService.Exportacion("Libro diario", "2026-01-01 a 2026-01-31",
                        List.of("Fecha"), List.of(List.of("2026-01-01")))));

        assertThat(paquete).isEqualTo(destino.resolve("Exportacion_Varios"));
        assertThat(paquete.resolve("Libro_diario")
                .resolve("Libro_diario_2026-01-01_a_2026-01-31.xlsx")).exists();
    }
}
