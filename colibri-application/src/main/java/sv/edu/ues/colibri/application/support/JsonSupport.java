package sv.edu.ues.colibri.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utilidad centralizada para serialización y deserialización JSON.
 *
 * Se usa para documentos simulados y estructuras auxiliares de la aplicación.
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonSupport() {
    }

    /**
     * Obtiene el {@link ObjectMapper} configurado para la aplicación.
     *
     * @return instancia compartida de Jackson
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializa un objeto a JSON.
     *
     * @param value objeto a serializar
     * @return representación JSON
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar JSON", e);
        }
    }

    /**
     * Deserializa una cadena JSON a un tipo concreto.
     *
     * @param json contenido JSON
     * @param type clase destino
     * @param <T> tipo de resultado
     * @return objeto reconstruido desde JSON
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer JSON", e);
        }
    }
}
