package sv.edu.ues.colibri.application.bugfix;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Test for Invariante 6: RBAC Security
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║ CRITICAL: This test MUST FAIL on unfixed code                            ║
 * ║ TEST FAILURE = SUCCESS (confirms SOPORTE_TECNICO accesses sensitive data) ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Encodes EXPECTED behavior: role SOPORTE_TECNICO must be DENIED access to
 * saldos, partidas, auxiliares, estados financieros y reportes contables.
 * Only soporte functions (respaldos, logs, gestión de usuarios) are allowed.
 *
 * Each probe invokes a sensitive service method as SOPORTE_TECNICO and records
 * whether it was blocked (DomainException) or wrongly allowed.
 *
 * Requirements: 1.6 - RBAC bug confirmation
 */
@DisplayName("RBAC Security - Bug Exploration Tests")
class RbacSecurityExploratoryTest {

    private static AppContext context;
    private static boolean disponible;
    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault("COLIBRI_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5434/colibri_test");
        String user = System.getenv().getOrDefault("COLIBRI_TEST_DB_USER", "colibri");
        String pass = System.getenv().getOrDefault("COLIBRI_TEST_DB_PASSWORD", "colibri");
        try {
            DriverManager.getConnection(url, user, pass).close();
            disponible = true;
            DatabaseConfig config = new DatabaseConfig(url, user, pass, user, pass);
            context = AppContext.iniciarConConfig(config);
            if (!context.catalogo().catalogoAprobado()) {
                context.auth().login(BootstrapService.USUARIO_DEFAULT,
                        BootstrapService.PASSWORD_DEFAULT.toCharArray());
                context.catalogo().aprobarCatalogoInicial();
            }
        } catch (Exception e) {
            disponible = false;
            System.err.println("Saltando RBAC: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.auth().logout();
            context.close();
        }
    }

    static boolean dbDisponible() {
        return disponible;
    }

    /**
     * Test 1: SOPORTE_TECNICO must be blocked from every sensitive read.
     *
     * For each probe we login as soporte and invoke the method. A correct system
     * throws DomainException ("soporte técnico no accede..."). If the call
     * returns normally, the role breached the security boundary — bug confirmed.
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Counterexample 1: SOPORTE_TECNICO reads financial data")
    void testSoporteTecnicoBlockedFromSensitiveReads() {
        System.out.println("\n=== TEST 1: SOPORTE_TECNICO acceso a datos sensibles ===");

        context.auth().login(BootstrapService.USUARIO_SOPORTE,
                BootstrapService.PASSWORD_SOPORTE.toCharArray());

        List<String> breaches = new ArrayList<>();
        List<String> blocked = new ArrayList<>();

        // Each entry: probe name + invocation. All must throw DomainException.
        record Probe(String nombre, Runnable llamada) {}
        List<Probe> probes = List.of(
                new Probe("libros.diario", () -> context.libros().diario(DESDE, HASTA)),
                new Probe("libros.balanza", () -> context.libros().balanza(DESDE, HASTA)),
                new Probe("libros.cuentaT", () -> context.libros().cuentaT("110102", DESDE, HASTA)),
                new Probe("libros.saldoCuentaHasta", () -> context.libros().saldoCuentaHasta("110102", HASTA)),
                new Probe("libros.estadoResultados", () -> context.libros().estadoResultados(DESDE, HASTA)),
                new Probe("libros.estadoSituacion", () -> context.libros().estadoSituacion(HASTA)),
                new Probe("libros.estadoFlujosEfectivo", () -> context.libros().estadoFlujosEfectivo(DESDE, HASTA)),
                new Probe("libros.estadoCambiosPatrimonio", () -> context.libros().estadoCambiosPatrimonio(DESDE, HASTA)),
                new Probe("libros.resumenEstados", () -> context.libros().resumenEstados(DESDE, HASTA)),
                new Probe("libros.auxiliar", () -> context.libros().auxiliar("CLIENTES", DESDE, HASTA)),
                new Probe("libros.libroIva", () -> context.libros().libroIva(DESDE, HASTA, "VENTAS")),
                new Probe("partidas.listarRecientes", () -> context.partidas().listarRecientes(10)),
                new Probe("partidas.listarBorradores", () -> context.partidas().listarBorradores()),
                new Probe("operaciones.listarDocumentos", () -> context.operaciones().listarDocumentos()),
                new Probe("operaciones.listarTerceros", () -> context.operaciones().listarTerceros()),
                new Probe("inventario.listarLotes", () -> context.inventario().listarLotes()),
                new Probe("inventario.kardexDeLote", () -> context.inventario().kardexDeLote(UUID.randomUUID())),
                new Probe("panel.resumen", () -> context.panel().resumen(null)),
                new Probe("catalogo.listar", () -> context.catalogo().listar()),
                new Probe("planilla.tasasLaboralesVigentes", () -> context.planilla().tasasLaboralesVigentes()),
                new Probe("parametrizacion.exigirParametrosFiscales", () -> context.parametrizacion().exigirParametrosFiscales()),
                new Probe("cierre.listarIntangibles", () -> context.cierre().listarIntangibles())
        );

        for (Probe p : probes) {
            try {
                p.llamada().run();
                breaches.add(p.nombre());
                System.out.println("  ✗ BREACH: " + p.nombre() + " → ACCESO PERMITIDO");
            } catch (DomainException e) {
                blocked.add(p.nombre());
                System.out.println("  ✓ BLOCKED: " + p.nombre() + " → " + e.getMessage());
            } catch (RuntimeException e) {
                // Non-domain exceptions (NPE etc.) still mean the guard did not fire
                // cleanly; treat as blocked only if message mentions soporte/rol.
                String msg = String.valueOf(e.getMessage()).toLowerCase();
                if (msg.contains("soporte") || msg.contains("contador") || msg.contains("sesión")
                        || msg.contains("sesion") || msg.contains("acceso")) {
                    blocked.add(p.nombre());
                    System.out.println("  ✓ BLOCKED: " + p.nombre() + " → " + e.getMessage());
                } else {
                    breaches.add(p.nombre() + " (ex: " + e.getClass().getSimpleName() + ")");
                    System.out.println("  ? UNEXPECTED: " + p.nombre() + " → " + e);
                }
            }
        }

        context.auth().logout();

        System.out.println("\nResumen: " + blocked.size() + " bloqueados, "
                + breaches.size() + " BREACHES");
        if (!breaches.isEmpty()) {
            System.out.println("Brechas detectadas: " + breaches);
        }

        // WILL FAIL if any sensitive method is reachable by SOPORTE_TECNICO.
        assertTrue(breaches.isEmpty(),
                "RBAC BUG: SOPORTE_TECNICO accedió a " + breaches.size()
                + " métodos sensibles sin restricción: " + breaches +
                ". Tras @RequireRole/RoleAccess checks este test pasará.");
    }

    /**
     * Test 2: Control — CONTADOR must retain access (preservation baseline).
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Control: CONTADOR retains authorized access")
    void testContadorRetainsAccess() {
        System.out.println("\n=== TEST 2: CONTADOR acceso autorizado (control) ===");

        context.auth().login(BootstrapService.USUARIO_DEFAULT,
                BootstrapService.PASSWORD_DEFAULT.toCharArray());
        try {
            assertDoesNotThrow(() -> context.libros().balanza(DESDE, HASTA));
            assertDoesNotThrow(() -> context.libros().diario(DESDE, HASTA));
            assertDoesNotThrow(() -> context.partidas().listarRecientes(5));
            assertDoesNotThrow(() -> context.catalogo().listar());
            System.out.println("  ✓ CONTADOR accede correctamente a datos financieros");
        } finally {
            context.auth().logout();
        }
    }

    /**
     * Test 3: SOPORTE_TECNICO keeps its legitimate functions (soporte tools).
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Control: SOPORTE_TECNICO keeps soporte functions")
    void testSoporteKeepsLegitimateFunctions() {
        System.out.println("\n=== TEST 3: SOPORTE_TECNICO funciones legítimas ===");

        context.auth().login(BootstrapService.USUARIO_SOPORTE,
                BootstrapService.PASSWORD_SOPORTE.toCharArray());
        try {
            // These are soporte-scoped and MUST remain accessible.
            assertDoesNotThrow(() -> context.soporte().entorno());
            assertDoesNotThrow(() -> context.soporte().listarLogs("INFO"));
            assertDoesNotThrow(() -> context.auth().listarUsuarios());
            System.out.println("  ✓ SOPORTE_TECNICO conserva funciones de soporte");
        } finally {
            context.auth().logout();
        }
    }

    /**
     * SUMMARY report
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Summary: RBAC exploration report")
    void testSummaryReport() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          RBAC SECURITY BUG EXPLORATION COMPLETE              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Probes: saldos, partidas, auxiliares, estados financieros,");
        System.out.println("        reportes, inventario, panel, catálogo, planilla.");
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("  1. Audit public service methods missing auth.exigirContador()");
        System.out.println("  2. Add RoleAccess checks / @RequireRole equivalents");
        System.out.println("  3. Create AccessDeniedException for security violations");
        System.out.println("  4. Re-run this suite - all tests should PASS");
        System.out.println();
        System.out.println("REQUIREMENT VALIDATED: 1.6 - RBAC enforcement checked");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}
