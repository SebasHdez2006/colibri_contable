package sv.edu.ues.colibri.desktop.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guardia del sistema de diseño: los literales de color viven solo en
 * {@code tokens.css}, los componentes consumen tokens semánticos y las
 * vistas no usan estilos en línea.
 */
class DisenoTokensTest {

    private static final Path RECURSOS = Path.of(
            "src/main/resources/sv/edu/ues/colibri/desktop");
    private static final Path FUENTES = Path.of("src/main/java");

    private static final Pattern LITERAL_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}\\b|rgba?\\(");
    private static final Pattern TOKEN = Pattern.compile("-(?:color|clb|paint)-[a-z0-9-]+");
    private static final Pattern DEFINICION = Pattern.compile("(-(?:color|clb|paint)-[a-z0-9-]+)\\s*:");
    private static final Pattern ALIAS = Pattern.compile("-colibri-[a-z0-9-]+\\s*:\\s*([^;]+);");
    private static final Pattern SET_STYLE = Pattern.compile("\\.setStyle\\(");

    /** Excepción documentada: el zoom de ventana ajusta -fx-font-size en MainShell. */
    private static final Set<String> ARCHIVOS_CON_EXCEPCION = Set.of("MainShell.java");

    @Test
    void tokensCssDefineLaPaletaSemantica() throws IOException {
        String tokens = Files.readString(RECURSOS.resolve("tokens.css"));
        for (String requerido : List.of(
                "-color-bg-canvas", "-color-bg-surface", "-color-bg-raised",
                "-color-fg", "-color-fg-muted", "-color-on-primary",
                "-color-primary", "-color-interactive", "-color-focus-ring",
                "-color-success", "-color-warning", "-color-danger", "-color-note",
                "-color-bg-glass", "-color-scrim", "-paint-brand",
                "-fx-base", "-fx-accent")) {
            assertTrue(tokens.contains(requerido + ":"), "Falta el token " + requerido);
        }
        assertTrue(tokens.contains("prefers-color-scheme: light"),
                "tokens.css debe definir el tema claro alternativo");
    }

    @Test
    void colibriCssNoTieneLiteralesNiReferenciasRotas() throws IOException {
        String colibri = Files.readString(RECURSOS.resolve("colibri.css"));
        String tokens = Files.readString(RECURSOS.resolve("tokens.css"));

        Matcher literales = LITERAL_COLOR.matcher(colibri);
        if (literales.find()) {
            fail("colibri.css contiene un literal de color (" + literales.group()
                    + "); use tokens -color-* definidos en tokens.css");
        }

        Set<String> definidos = new HashSet<>();
        Matcher defs = DEFINICION.matcher(tokens);
        while (defs.find()) {
            definidos.add(defs.group(1));
        }

        Matcher usos = TOKEN.matcher(colibri);
        while (usos.find()) {
            String token = usos.group();
            assertTrue(definidos.contains(token),
                    "colibri.css referencia " + token + " que no está definido en tokens.css");
        }
    }

    @Test
    void aliasesColibriResuelvenATokensSemanticos() throws IOException {
        String colibri = Files.readString(RECURSOS.resolve("colibri.css"));
        Matcher aliases = ALIAS.matcher(colibri);
        int encontrados = 0;
        while (aliases.find()) {
            encontrados++;
            String rhs = aliases.group(1).trim();
            assertTrue(rhs.equals("transparent")
                            || rhs.startsWith("-color-") || rhs.startsWith("-clb-")
                            || rhs.startsWith("-paint-"),
                    "El alias " + aliases.group().trim() + " no apunta a un token semántico");
        }
        assertTrue(encontrados >= 30,
                "Se esperaban los alias -colibri-* de compatibilidad, encontrados: " + encontrados);
    }

    @Test
    void lasVistasNoUsanEstilosEnLinea() throws IOException {
        try (Stream<Path> archivos = Files.walk(FUENTES)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String nombre = archivo.getFileName().toString();
                if (ARCHIVOS_CON_EXCEPCION.contains(nombre)) {
                    continue;
                }
                String fuente = Files.readString(archivo);
                assertFalse(SET_STYLE.matcher(fuente).find(),
                        "setStyle() en línea en " + FUENTES.relativize(archivo)
                                + "; use clases CSS del sistema de diseño");
            }
        }
    }
}
