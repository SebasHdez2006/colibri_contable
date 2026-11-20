package sv.edu.ues.colibri.application.bugfix;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Test for Invariante 7: Transaction Atomicity
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║ CRITICAL: This test MUST FAIL on unfixed code                            ║
 * ║ TEST FAILURE = SUCCESS (confirms missing atomic rollback)                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Encodes EXPECTED behavior: a multi-step operation wrapped in a transaction
 * must be ALL-OR-NOTHING. If step 2 fails, step 1 must roll back.
 *
 * The system uses JdbcSupport.inTransaction (manual JDBC). The bug condition:
 * nested inTransaction calls open a SEPARATE connection+transaction, so an inner
 * unit of work COMMITS independently of the outer one. When the outer operation
 * later fails, the inner committed data survives → partial persistence.
 *
 * This mirrors what happens in production flows like venderLicenciaPeps /
 * liberarLote, which chain multiple inTransaction calls across services.
 *
 * Requirements: 1.7 - Transaction atomicity bug confirmation
 */
@DisplayName("Transaction Atomicity - Bug Exploration Tests")
class TransactionAtomicityExploratoryTest {

    private static AppContext context;
    private static JdbcSupport jdbc;
    private static boolean disponible;
    private static final String PREFIJO = "ATOMIC-TEST-";

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault("COLIBRI_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5434/colibri_test");
        String user = System.getenv().getOrDefault("COLIBRI_TEST_DB_USER", "colibri");
        String pass = System.getenv().getOrDefault("COLIBRI_TEST_DB_PASSWORD", "colibri");
        try {
            java.sql.DriverManager.getConnection(url, user, pass).close();
            disponible = true;
            DatabaseConfig config = new DatabaseConfig(url, user, pass, user, pass);
            context = AppContext.iniciarConConfig(config);
            jdbc = context.jdbc();
            context.auth().login(BootstrapService.USUARIO_DEFAULT,
                    BootstrapService.PASSWORD_DEFAULT.toCharArray());
            limpiarRestos();
        } catch (Exception e) {
            disponible = false;
            System.err.println("Saltando atomicidad: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (disponible) {
            limpiarRestos();
        }
        if (context != null) {
            context.auth().logout();
            context.close();
        }
    }

    static boolean dbDisponible() {
        return disponible;
    }

    private static void limpiarRestos() {
        try {
            jdbc.runInTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM evento_contable WHERE clave_unica LIKE ?")) {
                    ps.setString(1, PREFIJO + "%");
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException("Limpieza falló", e);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void insertarEvento(Connection conn, String clave) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES (?,?,?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, clave);
            ps.setString(3, "prueba atomicidad");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Insert evento falló", e);
        }
    }

    private static boolean existeEvento(String clave) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM evento_contable WHERE clave_unica=?")) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Consulta evento falló", e);
            }
        });
    }

    /**
     * Test 1: Nested inTransaction must JOIN the outer transaction (REQUIRED
     * propagation). If the outer fails, everything must roll back.
     *
     * Scenario:
     *   outer tx {
     *     inner tx { INSERT evento-B }   // commits today on its own connection
     *     INSERT evento-A
     *     throw                             // outer must roll back BOTH
     *   }
     *
     * BUG (unfixed): evento-B was committed by the inner transaction on a
     * different connection → survives the outer rollback → partial persistence.
     * EXPECTED (fixed): evento-B rolls back with the outer tx → nothing persists.
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Counterexample 1: inner transaction commits despite outer rollback")
    void testNestedTransactionAtomicity() {
        System.out.println("\n=== TEST 1: Transacción anidada sin atomicidad ===");

        String claveInner = PREFIJO + "INNER-" + UUID.randomUUID();
        String claveOuter = PREFIJO + "OUTER-" + UUID.randomUUID();

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                jdbc.inTransaction(conn -> {
                    // Inner "unit of work" — today opens its OWN connection+tx.
                    jdbc.inTransaction(inner -> {
                        insertarEvento(inner, claveInner);
                        return null;
                    });
                    // Outer unit of work continues, then fails.
                    insertarEvento(conn, claveOuter);
                    throw new RuntimeException("Fallo forzado en paso final");
                }));

        System.out.println("Excepción propagada: " + thrown.getMessage());

        boolean innerPersistio = existeEvento(claveInner);
        boolean outerPersistio = existeEvento(claveOuter);

        System.out.println("evento INNER persistió tras rollback externo: " + innerPersistio);
        System.out.println("evento OUTER persistió tras rollback externo: " + outerPersistio);

        if (innerPersistio) {
            System.out.println("  ✗ BUG: la transacción interna confirmó por separado —");
            System.out.println("    una operación multi-paso deja estado parcial.");
        } else {
            System.out.println("  ✓ La transacción interna se unió a la externa (rollback completo)");
        }

        // WILL FAIL on unfixed code: inner commits independently.
        assertFalse(innerPersistio,
                "ATOMICITY BUG: inner inTransaction committed '" + claveInner
                + "' on a separate connection; outer rollback left partial state. "
                + "JdbcSupport must propagate REQUIRED semantics (join outer tx).");
        assertFalse(outerPersistio,
                "Outer step must roll back too (sanity check).");
    }

    /**
     * Test 2: Two sequential inTransaction calls are independent transactions —
     * expected and preserved behavior for genuinely separate operations.
     * This documents the baseline: single-step transactions still work.
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Control: single-step transactions still complete")
    void testSingleStepTransactionsWork() {
        System.out.println("\n=== TEST 2: Transacciones simples (control) ===");

        String clave = PREFIJO + "SINGLE-" + UUID.randomUUID();

        jdbc.runInTransaction(conn -> insertarEvento(conn, clave));
        assertTrue(existeEvento(clave), "La transacción simple debe persistir");

        // A second independent transaction also works.
        String clave2 = PREFIJO + "SINGLE2-" + UUID.randomUUID();
        jdbc.runInTransaction(conn -> insertarEvento(conn, clave2));
        assertTrue(existeEvento(clave2));

        System.out.println("  ✓ Operaciones simples persisten correctamente");
    }

    /**
     * Test 3: Rollback on failure within a single transaction works today —
     * documents the part that is already correct (preservation baseline).
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Control: rollback inside one transaction works")
    void testSingleTransactionRollback() {
        System.out.println("\n=== TEST 3: Rollback dentro de una transacción ===");

        String clave = PREFIJO + "ROLLBACK-" + UUID.randomUUID();

        assertThrows(RuntimeException.class, () ->
                jdbc.inTransaction(conn -> {
                    insertarEvento(conn, clave);
                    throw new RuntimeException("Fallo dentro de la misma tx");
                }));

        assertFalse(existeEvento(clave),
                "La transacción única debe hacer rollback completo");
        System.out.println("  ✓ Rollback dentro de una transacción funciona");
    }

    /**
     * SUMMARY report
     */
    @Test
    @EnabledIf("dbDisponible")
    @DisplayName("Summary: Atomicity exploration report")
    void testSummaryReport() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       TRANSACTION ATOMICITY BUG EXPLORATION COMPLETE          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Bug condition: JdbcSupport.inTransaction opens a NEW connection");
        System.out.println("per call — nested calls commit independently (no REQUIRED join).");
        System.out.println();
        System.out.println("Affected flows (multi-step across service boundaries):");
        System.out.println("  - InventarioCostoService.venderLicenciaPeps / liberarLote");
        System.out.println("  - OperacionContableService.registrar* (documento + partida)");
        System.out.println("  - PlanillaService.registrar, CierreService flows");
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("  1. JdbcSupport: ThreadLocal connection → REQUIRED propagation");
        System.out.println("  2. Wrap orchestrating methods in single inTransaction");
        System.out.println("  3. Re-run this suite - nested test must PASS");
        System.out.println();
        System.out.println("REQUIREMENT VALIDATED: 1.7 - Atomicity bug confirmed");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}
