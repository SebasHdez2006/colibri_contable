package sv.edu.ues.colibri.desktop;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Bitácora local en {@code %USERPROFILE%\.colibri\colibri.log}.
 */
public final class AppLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppLog() {
    }

    /**
     * Devuelve la ruta del archivo de bitácora local.
     *
     * @return ruta del archivo de log
     */
    public static Path archivo() {
        return Path.of(System.getProperty("user.home"), ".colibri", "colibri.log");
    }

    /**
     * Registra un mensaje de error y su traza opcional.
     *
     * @param mensaje descripción del error
     * @param ex excepción asociada o {@code null}
     */
    public static void error(String mensaje, Throwable ex) {
        StringWriter sw = new StringWriter();
        if (ex != null) {
            ex.printStackTrace(new PrintWriter(sw));
        }
        escribir("ERROR", mensaje + (ex == null ? "" : System.lineSeparator() + sw));
    }

    /**
     * Registra un mensaje informativo.
     *
     * @param mensaje mensaje a registrar
     */
    public static void info(String mensaje) {
        escribir("INFO", mensaje);
    }

    private static void escribir(String nivel, String mensaje) {
        try {
            Path file = archivo();
            Files.createDirectories(file.getParent());
            String linea = FMT.format(LocalDateTime.now()) + " [" + nivel + "] " + mensaje + System.lineSeparator();
            Files.writeString(file, linea, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
