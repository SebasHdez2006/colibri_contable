package sv.edu.ues.colibri.desktop.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.desktop.ui.layout.MasterDetail;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo de la navegación del contador. Usa exclusivamente la base
 * colibri_test y se omite cuando no hay PostgreSQL o JavaFX disponibles.
 */
class NavegacionModulosTest {

    private static AppContext context;
    private static boolean disponible;

    @BeforeAll
    static void prepararEntorno() {
        String url = System.getenv().getOrDefault("COLIBRI_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5434/colibri_test");
        String usuario = System.getenv().getOrDefault("COLIBRI_TEST_DB_USER", "colibri");
        String clave = System.getenv().getOrDefault("COLIBRI_TEST_DB_PASSWORD", "colibri");
        try {
            DriverManager.getConnection(url, usuario, clave).close();
            context = AppContext.iniciarConConfig(new DatabaseConfig(url, usuario, clave, usuario, clave));
            context.auth().login(BootstrapService.USUARIO_DEFAULT,
                    BootstrapService.PASSWORD_DEFAULT.toCharArray());
            if (!context.catalogo().catalogoAprobado()) {
                context.catalogo().aprobarCatalogoInicial();
            }
            disponible = iniciarJavaFx();
        } catch (Exception ex) {
            disponible = false;
            System.err.println("Saltando navegación de escritorio: " + ex.getMessage());
        }
    }

    @AfterAll
    static void cerrarEntorno() {
        if (context != null) {
            context.close();
        }
    }

    static boolean disponible() {
        return disponible;
    }

    @Test
    @EnabledIf("disponible")
    void contadorPuedeAbrirTodasLasSeccionesSinPestanas() {
        AtomicBoolean cerroSesion = new AtomicBoolean();
        AtomicReference<MainShell> shell = new AtomicReference<>();
        enJavaFx(() -> shell.set(new MainShell(context, () -> cerroSesion.set(true))));

        Map<String, List<String>> rutas = new LinkedHashMap<>();
        rutas.put("nav-temas-contables", List.of("Empresa y fundamentos", "Registros y estados",
                "Costos y control", "Estimación y comercialización"));
        rutas.put("nav-parametrizacion", List.of("Catálogo de cuentas", "Mapeo de módulos", "Períodos contables",
                "Parámetros fiscales", "Políticas y secuenciales", "Centros de costo"));
        rutas.put("nav-registros", List.of("Documentos", "Libro diario", "Plantillas",
                "Planilla", "Ajustes"));
        rutas.put("nav-inventario", List.of("Digitales", "Kárdex", "Servicios / Accesos"));
        rutas.put("nav-costos", List.of("A la medida", "Licencias descargables", "Suscripción SaaS",
                "Procesos y CIF", "Estimación y políticas"));
        rutas.put("nav-reportes", List.of("Obligatorios", "Auxiliares", "Societarios"));

        enJavaFx(() -> {
            MainShell s = shell.get();
            assertEquals(List.of("nav-inicio", "nav-temas-contables", "nav-empresa", "nav-parametrizacion",
                    "nav-registros", "nav-inventario", "nav-costos", "nav-reportes"),
                    s.menu().idsRaiz(),
                    "El menú debe respetar el orden de módulos");

            s.menu().boton("nav-inicio").orElseThrow().fire();
            assertTrue(encontrar(s.seccionActual(), Label.class).stream()
                    .anyMatch(label -> "Resumen contable".equals(label.getText())));

            s.menu().boton("nav-empresa").orElseThrow().fire();
            assertEquals("nav-empresa", s.itemActual().id());

            for (Map.Entry<String, List<String>> ruta : rutas.entrySet()) {
                NavItem grupo = s.menu().item(ruta.getKey()).orElseThrow();
                assertEquals(ruta.getValue(), grupo.hijos().stream().map(NavItem::etiqueta).toList(),
                        "Las secciones de " + ruta.getKey() + " deben estar disponibles");
                s.menu().boton(ruta.getKey()).orElseThrow().fire();
                assertEquals(grupo.hijos().getFirst().id(), s.itemActual().id(),
                        "Abrir el grupo muestra su primera sección");
                for (NavItem hijo : grupo.hijos()) {
                    Button boton = s.menu().boton(hijo.id()).orElseThrow();
                    boton.fire();
                    assertEquals(hijo.id(), s.itemActual().id());
                    Node vista = s.seccionActual();
                    assertNotNull(vista, "La sección " + hijo.etiqueta() + " debe cargar contenido");
                    assertFalse(vista.getStyleClass().contains("module-load-error"),
                            "La sección " + hijo.etiqueta() + " no debe fallar al cargar");
                    assertTrue(encontrar(s, TabPane.class).isEmpty(), "No debe haber TabPane en " + hijo.id());
                    if ("Estimación y políticas".equals(hijo.etiqueta())) {
                        assertTrue(encontrar(vista, Label.class).stream().anyMatch(label ->
                                        label.getText().contains("Estimación de costos de software")),
                                "Estimación y políticas debe mostrar su interfaz, no quedar vacía");
                    }
                }
            }
            validarTemasContables(s);
            assertFalse(cerroSesion.get(), "Un fallo de módulo no debe cerrar la sesión del contador");
        });
    }

    @Test
    @EnabledIf("disponible")
    void elMenuEsUnAcordeonYSeContraeAIconos() {
        AtomicReference<MainShell> shell = new AtomicReference<>();
        enJavaFx(() -> shell.set(new MainShell(context, () -> { })));
        enJavaFx(() -> {
            MainShell s = shell.get();
            s.ir("nav-registros-libro-diario");
            assertEquals("nav-registros-libro-diario", s.itemActual().id());
            assertTrue(hijosVisibles(s, "nav-registros-libro-diario"));
            s.ir("nav-costos-procesos-y-cif");
            assertFalse(hijosVisibles(s, "nav-registros-libro-diario"),
                    "Solo un grupo permanece expandido");
            s.menu().setColapsado(true);
            assertFalse(hijosVisibles(s, "nav-costos-procesos-y-cif"));
            Hyperlink modulo = encontrar(s, Hyperlink.class).stream()
                    .filter(enlace -> "Costos".equals(enlace.getText()))
                    .findFirst().orElseThrow();
            modulo.fire();
            javafx.stage.Popup flyout = s.migasFlyout();
            assertNotNull(flyout, "El breadcrumb debe ofrecer las secciones del módulo");
            assertTrue(s.menu().isColapsado(),
                    "El flyout permite cambiar de sección sin expandir el menú");
            // La prueba no monta el shell en una ventana, así que el popup no
            // llega a mostrarse; sus opciones existen y navegan igual.
            Node contenidoFlyout = (Node) flyout.getContent().get(0);
            Button destino = encontrar(contenidoFlyout, Button.class).stream()
                    .filter(b -> "popup-nav-costos-a-la-medida".equals(b.getId()))
                    .findFirst().orElseThrow();
            destino.fire();
            assertEquals("nav-costos-a-la-medida", s.itemActual().id(),
                    "Elegir una sección del flyout del breadcrumb debe navegar a ella");
        });
    }

    private static boolean hijosVisibles(MainShell shell, String idHijo) {
        return shell.menu().boton(idHijo).orElseThrow().getParent().getParent().isVisible();
    }

    private static void validarTemasContables(MainShell shell) {
        Map<String, List<String>> esperados = new LinkedHashMap<>();
        esperados.put("Empresa y fundamentos", List.of("La contabilidad y empresa",
                "Clasificación de las empresas según actividad económica",
                "Principios contables generalmente aceptados", "Diseño de un sistema contable",
                "Introducción al concepto de partida doble"));
        esperados.put("Registros y estados", List.of("Métodos de registros mercantiles", "La cuenta T",
                "Estados financieros y sus elementos", "Asientos de ajuste", "Hoja de trabajo"));
        esperados.put("Costos y control", List.of("Costos para la toma de decisiones",
                "Generalidades de la contabilidad de costos", "Elementos del costo de un producto",
                "Representación de cómo se acumulan los costos en cada tipo de empresa",
                "Contabilización y control de los materiales", "Manejo de inventarios: UEPS, PEPS y Costo Promedio",
                "Contabilización y control de la mano de obra", "Políticas de trabajo y manejo de recurso humano",
                "Contabilización y control de los costos indirectos de fabricación",
                "CIF específicos de los sistemas informáticos"));
        esperados.put("Estimación y comercialización", List.of("Métodos de estimación de costos",
                "Cálculos del precio unitario", "Planeación de distribución y venta de software",
                "Sistemas de costos por órdenes de trabajo", "Sistemas de costos por proceso",
                "Sistemas de costos estándar y directos", "Costos basados en actividades"));

        for (Map.Entry<String, List<String>> grupo : esperados.entrySet()) {
            shell.ir("nav-temas-contables-" + NavItem.slug(grupo.getKey()));
            MasterDetail<?> maestro = encontrar(shell.seccionActual(), MasterDetail.class).getFirst();
            ListView<?> lista = maestro.lista();
            assertEquals(grupo.getValue(), lista.getItems().stream().map(t -> ((sv.edu.ues.colibri.desktop.ui.views
                    .TemasContablesView.Tema) t).titulo()).toList());
            for (int i = 0; i < lista.getItems().size(); i++) {
                lista.getSelectionModel().select(i);
                String titulo = grupo.getValue().get(i);
                assertTrue(encontrar(maestro, Label.class).stream().anyMatch(label -> titulo.equals(label.getText())),
                        "El tema " + titulo + " debe mostrar su título");
            }
        }
    }

    private static <T extends Node> List<T> encontrar(Node raiz, Class<T> tipo) {
        java.util.ArrayList<T> encontrados = new java.util.ArrayList<>();
        recorrer(raiz, tipo, encontrados);
        return encontrados;
    }

    private static <T extends Node> void recorrer(Node nodo, Class<T> tipo, List<T> encontrados) {
        if (tipo.isInstance(nodo)) {
            encontrados.add(tipo.cast(nodo));
        }
        if (nodo instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null) {
            recorrer(scroll.getContent(), tipo, encontrados);
        }
        if (nodo instanceof Parent padre) {
            padre.getChildrenUnmodifiable().forEach(hijo -> recorrer(hijo, tipo, encontrados));
        }
    }

    private static boolean iniciarJavaFx() {
        try {
            CountDownLatch listo = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                listo.countDown();
            });
            return listo.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException yaIniciado) {
            return true;
        } catch (Exception ex) {
            System.err.println("JavaFX no disponible para navegación: " + ex.getMessage());
            return false;
        }
    }

    private static void enJavaFx(Accion accion) {
        if (Platform.isFxApplicationThread()) {
            ejecutar(accion);
            return;
        }
        CountDownLatch finalizado = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                accion.ejecutar();
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                finalizado.countDown();
            }
        });
        try {
            assertTrue(finalizado.await(60, TimeUnit.SECONDS), "La operación JavaFX excedió 60 segundos");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("La prueba JavaFX fue interrumpida", ex);
        }
        if (error.get() instanceof AssertionError assertion) {
            throw assertion;
        }
        if (error.get() != null) {
            throw new AssertionError("Falló la navegación JavaFX", error.get());
        }
    }

    private static void ejecutar(Accion accion) {
        try {
            accion.ejecutar();
        } catch (Exception ex) {
            throw new AssertionError("Falló la navegación JavaFX", ex);
        }
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }
}
