package sv.edu.ues.colibri.application.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la serialización y deserialización JSON de apoyo.
 */
class JsonSupportTest {

    @Test
    void serializaYDeserializaDocumentoSimulado() {
        record DteSimulado(String codigoGeneracion, LocalDate fecha, double total) {
        }
        String json = JsonSupport.toJson(new DteSimulado("ABC-123", LocalDate.of(2026, 1, 15), 271.20));
        assertThat(json).contains("ABC-123").contains("2026-01-15");

        @SuppressWarnings("unchecked")
        Map<String, Object> mapa = JsonSupport.fromJson(json, Map.class);
        assertThat(mapa.get("codigoGeneracion")).isEqualTo("ABC-123");
    }
}
