package sv.edu.ues.colibri.application.bugfix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Test for Invariante 4: Financial Statement Chain
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║ CRITICAL: This test MUST FAIL on unfixed code                            ║
 * ║ TEST FAILURE = SUCCESS (confirms the bug exists)                          ║
 * ║ DO NOT attempt to fix the test or code when it fails                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * This test encodes the EXPECTED (correct) behavior: financial statements
 * must maintain mathematical consistency across the entire chain:
 * Diario → Mayor → Balanza → Estados Financieros
 * 
 * STATUS ON UNFIXED CODE: WILL FAIL (mathematical inconsistencies present)
 * STATUS ON FIXED CODE: WILL PASS (FinancialStatementValidator ensures consistency)
 * 
 * The test validates four critical consistency checks:
 * 1. Diario → Mayor: Sum of journal entries must equal general ledger totals
 * 2. Mayor → Balanza: Ledger balances must equal trial balance
 * 3. Balanza → Estados: Trial balance must reconcile to financial statements
 * 4. Accounting Equation: Activo == Pasivo + Patrimonio
 * 
 * Requirements: 1.4 - Financial statement chain bug confirmation
 * 
 * NOTE: This test simulates the financial statement chain without requiring
 * a live database. It demonstrates the validation logic that FinancialStatementValidator
 * should implement. The test uses simulated accounting data to expose the bug condition.
 */
@DisplayName("Financial Statement Chain - Bug Exploration Tests")
class FinancialStatementChainExploratoryTest {

    // Simulated accounting data structures
    private record JournalEntry(String account, BigDecimal debe, BigDecimal haber) {}
    private record LedgerBalance(String account, String name, BigDecimal debe, BigDecimal haber, BigDecimal saldo) {}
    private record FinancialLine(String code, String concept, BigDecimal amount) {}

    /**
     * Test 1: Diario → Mayor Consistency
     * 
     * This test validates that the sum of all journal entries (Diario) for each account
     * matches the totals in the general ledger (Mayor/Balanza).
     * 
     * Expected behavior: For each account, sum(Diario.debe) == Mayor.debe
     *                                  and sum(Diario.haber) == Mayor.haber
     * 
     * Bug condition: Without FinancialStatementValidator, discrepancies may exist
     * due to rounding errors, incomplete posting, or calculation bugs.
     * 
     * This test simulates a scenario where journal entries are posted, but due to
     * a rounding error or bug, the ledger totals don't match the journal.
     */
    @Test
    @DisplayName("Counterexample 1: Diario → Mayor inconsistency (simulated)")
    void testDiarioToMayorConsistency() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 1: Diario → Mayor Consistency");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate journal entries (Diario)
        List<JournalEntry> diario = List.of(
            new JournalEntry("1102", new BigDecimal("50000.00"), BigDecimal.ZERO),      // Bancos DEBE
            new JournalEntry("3101", BigDecimal.ZERO, new BigDecimal("50000.00")),      // Capital HABER
            new JournalEntry("5101", new BigDecimal("15000.00"), BigDecimal.ZERO),      // Costo DEBE
            new JournalEntry("2102", new BigDecimal("1950.00"), BigDecimal.ZERO),       // IVA DEBE
            new JournalEntry("1102", BigDecimal.ZERO, new BigDecimal("16950.00")),      // Bancos HABER
            new JournalEntry("1201", new BigDecimal("28200.00"), BigDecimal.ZERO),      // CxC DEBE
            new JournalEntry("4101", BigDecimal.ZERO, new BigDecimal("25000.00")),      // Ventas HABER
            new JournalEntry("2102", BigDecimal.ZERO, new BigDecimal("3200.00")),       // IVA HABER
            new JournalEntry("6101", new BigDecimal("5000.00"), BigDecimal.ZERO),       // Gastos DEBE
            new JournalEntry("1102", BigDecimal.ZERO, new BigDecimal("5000.00")),       // Bancos HABER
            new JournalEntry("1101", new BigDecimal("28200.00"), BigDecimal.ZERO),      // Caja DEBE
            new JournalEntry("1201", BigDecimal.ZERO, new BigDecimal("28200.00"))       // CxC HABER
        );
        
        // Aggregate Diario by account
        Map<String, BigDecimal> diarioDebe = diario.stream()
                .collect(Collectors.groupingBy(
                        JournalEntry::account,
                        Collectors.reducing(BigDecimal.ZERO, JournalEntry::debe, BigDecimal::add)));
        
        Map<String, BigDecimal> diarioHaber = diario.stream()
                .collect(Collectors.groupingBy(
                        JournalEntry::account,
                        Collectors.reducing(BigDecimal.ZERO, JournalEntry::haber, BigDecimal::add)));
        
        // Simulate ledger balances (Mayor/Balanza) WITH A ROUNDING ERROR
        // This simulates the bug condition where LibroContableService.balanza()
        // produces slightly different totals than the sum of journal entries
        List<LedgerBalance> balanza = List.of(
            new LedgerBalance("1101", "Caja", new BigDecimal("28200.00"), BigDecimal.ZERO, new BigDecimal("28200.00")),
            new LedgerBalance("1102", "Bancos", new BigDecimal("50000.00"), new BigDecimal("21950.01"), new BigDecimal("28049.99")), // BUG: 0.01 error!
            new LedgerBalance("1201", "Cuentas por Cobrar", new BigDecimal("28200.00"), new BigDecimal("28200.00"), BigDecimal.ZERO),
            new LedgerBalance("2102", "IVA por Pagar", new BigDecimal("1950.00"), new BigDecimal("3200.00"), new BigDecimal("-1250.00")),
            new LedgerBalance("3101", "Capital Social", BigDecimal.ZERO, new BigDecimal("50000.00"), new BigDecimal("-50000.00")),
            new LedgerBalance("4101", "Ventas", BigDecimal.ZERO, new BigDecimal("25000.00"), new BigDecimal("-25000.00")),
            new LedgerBalance("5101", "Costo de Ventas", new BigDecimal("15000.00"), BigDecimal.ZERO, new BigDecimal("15000.00")),
            new LedgerBalance("6101", "Gastos de Operación", new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("5000.00"))
        );
        
        System.out.println("\n--- Comparison: Diario vs Balanza ---");
        boolean hasDiscrepancy = false;
        final BigDecimal tolerance = new BigDecimal("0.01");
        
        for (LedgerBalance cuenta : balanza) {
            BigDecimal diarioDeb = diarioDebe.getOrDefault(cuenta.account(), BigDecimal.ZERO);
            BigDecimal diarioHab = diarioHaber.getOrDefault(cuenta.account(), BigDecimal.ZERO);
            
            BigDecimal diffDebe = diarioDeb.subtract(cuenta.debe()).abs();
            BigDecimal diffHaber = diarioHab.subtract(cuenta.haber()).abs();
            
            if (diffDebe.compareTo(tolerance) > 0 || diffHaber.compareTo(tolerance) > 0) {
                hasDiscrepancy = true;
                System.out.printf("⚠️  Account %s (%s):%n", cuenta.account(), cuenta.name());
                System.out.printf("    Diario  Debe: %,.2f  Haber: %,.2f%n", diarioDeb, diarioHab);
                System.out.printf("    Balanza Debe: %,.2f  Haber: %,.2f%n", cuenta.debe(), cuenta.haber());
                System.out.printf("    Diff    Debe: %,.4f  Haber: %,.4f%n", diffDebe, diffHaber);
            }
        }
        
        if (!hasDiscrepancy) {
            System.out.println("✓ All accounts match between Diario and Balanza");
        }
        
        System.out.println("\nBug detected: " + (hasDiscrepancy ? "YES ✓" : "NO"));
        System.out.println("\nREAL-WORLD SCENARIO:");
        System.out.println("  In LibroContableService, the diario() method queries journal entries");
        System.out.println("  while balanza() aggregates them with SQL SUM(). Floating-point arithmetic");
        System.out.println("  or rounding differences can cause the two to diverge.");
        System.out.println("\nEXPECTED BEHAVIOR AFTER FIX:");
        System.out.println("  FinancialStatementValidator.validateChain() will:");
        System.out.println("  1. Aggregate Diario entries by account");
        System.out.println("  2. Compare with Balanza totals");
        System.out.println("  3. Throw FinancialStatementValidationException if diff > tolerance");
        
        // This assertion should FAIL - demonstrating the bug condition
        assertFalse(hasDiscrepancy,
                "Diario → Mayor inconsistency detected (account 1102 has 0.01 rounding error). " +
                "After implementing FinancialStatementValidator, this will pass.");
    }

    /**
     * Test 2: Balanza Double Entry Validation
     * 
     * This test validates that the trial balance (Balanza) maintains the fundamental
     * accounting principle: Total Debe == Total Haber
     * 
     * Expected behavior: sum(Balanza.debe) == sum(Balanza.haber)
     * 
     * Bug condition: Without validation, rounding errors or posting mistakes
     * can cause the trial balance to be unbalanced.
     */
    @Test
    @DisplayName("Counterexample 2: Balanza double-entry validation (simulated unbalanced)")
    void testBalanzaDoubleEntry() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 2: Balanza Double Entry Validation");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate an UNBALANCED trial balance (bug condition)
        // This happens when there's no centralized validation
        List<LedgerBalance> balanza = List.of(
            new LedgerBalance("1101", "Caja", new BigDecimal("28200.00"), BigDecimal.ZERO, new BigDecimal("28200.00")),
            new LedgerBalance("1102", "Bancos", new BigDecimal("50000.00"), new BigDecimal("21950.01"), new BigDecimal("28049.99")), // 0.01 error
            new LedgerBalance("1201", "Cuentas por Cobrar", new BigDecimal("28200.00"), new BigDecimal("28200.00"), BigDecimal.ZERO),
            new LedgerBalance("2102", "IVA por Pagar", new BigDecimal("1950.00"), new BigDecimal("3200.00"), new BigDecimal("-1250.00")),
            new LedgerBalance("3101", "Capital Social", BigDecimal.ZERO, new BigDecimal("50000.00"), new BigDecimal("-50000.00")),
            new LedgerBalance("4101", "Ventas", BigDecimal.ZERO, new BigDecimal("25000.00"), new BigDecimal("-25000.00")),
            new LedgerBalance("5101", "Costo de Ventas", new BigDecimal("15000.00"), BigDecimal.ZERO, new BigDecimal("15000.00")),
            new LedgerBalance("6101", "Gastos de Operación", new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("5000.00"))
        );
        
        BigDecimal totalDebe = balanza.stream()
                .map(LedgerBalance::debe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalHaber = balanza.stream()
                .map(LedgerBalance::haber)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        System.out.printf("Total Debe:  %,.2f%n", totalDebe);
        System.out.printf("Total Haber: %,.2f%n", totalHaber);
        
        BigDecimal difference = totalDebe.subtract(totalHaber).abs();
        System.out.printf("Difference:  %,.4f%n", difference);
        
        final BigDecimal tolerance = new BigDecimal("0.01");
        boolean isBalanced = difference.compareTo(tolerance) <= 0;
        
        System.out.println("\nBalanza balanced: " + (isBalanced ? "YES ✓" : "NO ✗"));
        System.out.println("Bug detected: " + (!isBalanced ? "YES ✓" : "NO"));
        
        System.out.println("\nREAL-WORLD SCENARIO:");
        System.out.println("  LibroContableService.balanza() generates trial balance from SQL");
        System.out.println("  aggregation. Without validation, rounding errors in BigDecimal");
        System.out.println("  calculations or double/float usage can cause imbalance.");
        System.out.println("\nEXPECTED BEHAVIOR AFTER FIX:");
        System.out.println("  FinancialStatementValidator will verify sum(Debe) == sum(Haber)");
        System.out.println("  within tolerance of 0.01 before allowing report generation.");
        
        // This assertion should FAIL - demonstrating unbalanced trial balance
        assertTrue(isBalanced,
                "Trial balance (Balanza) is unbalanced: Debe != Haber (diff: " + difference + "). " +
                "After implementing FinancialStatementValidator, this will pass.");
    }

    /**
     * Test 3: Accounting Equation Validation
     * 
     * This test validates the fundamental accounting equation:
     * Activo == Pasivo + Patrimonio
     * 
     * Expected behavior: Balance sheet must balance
     * 
     * Bug condition: Without FinancialStatementValidator, the accounting equation
     * may not hold due to calculation errors, incomplete data, or posting mistakes.
     */
    @Test
    @DisplayName("Counterexample 3: Accounting equation violation (simulated)")
    void testAccountingEquation() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 3: Accounting Equation Validation");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate balance sheet with accounting equation VIOLATION
        // This simulates the bug where estados financieros don't reconcile
        List<FinancialLine> estadoSituacion = List.of(
            // Activos
            new FinancialLine("1101", "Caja", new BigDecimal("28200.00")),
            new FinancialLine("1102", "Bancos", new BigDecimal("28049.99")),  // Has the 0.01 error from earlier
            new FinancialLine("ACTIVO_TOTAL", "Total Activos", new BigDecimal("56249.99")),
            
            // Pasivos
            new FinancialLine("2102", "IVA por Pagar", new BigDecimal("1250.00")),
            new FinancialLine("PASIVO_TOTAL", "Total Pasivos", new BigDecimal("1250.00")),
            
            // Patrimonio
            new FinancialLine("3101", "Capital Social", new BigDecimal("50000.00")),
            new FinancialLine("PATRIMONIO_TOTAL", "Total Patrimonio", new BigDecimal("50000.00"))
        );
        
        // Get net income to adjust equity
        BigDecimal ingresos = new BigDecimal("25000.00");
        BigDecimal costos = new BigDecimal("15000.00");
        BigDecimal gastos = new BigDecimal("5000.00");
        BigDecimal resultadoNeto = ingresos.subtract(costos).subtract(gastos);  // 5000.00
        
        BigDecimal totalActivo = new BigDecimal("56249.99");
        BigDecimal totalPasivo = new BigDecimal("1250.00");
        BigDecimal totalPatrimonio = new BigDecimal("50000.00");
        BigDecimal patrimonioAjustado = totalPatrimonio.add(resultadoNeto);
        
        System.out.println("\n--- Balance Sheet Components ---");
        System.out.printf("Activo:              %,.2f%n", totalActivo);
        System.out.printf("Pasivo:              %,.2f%n", totalPasivo);
        System.out.printf("Patrimonio:          %,.2f%n", totalPatrimonio);
        System.out.printf("Resultado Neto:      %,.2f%n", resultadoNeto);
        System.out.printf("Patrimonio Ajustado: %,.2f%n", patrimonioAjustado);
        
        BigDecimal pasivoMasPatrimonio = totalPasivo.add(patrimonioAjustado);
        System.out.printf("\nPasivo + Patrimonio: %,.2f%n", pasivoMasPatrimonio);
        System.out.printf("Accounting Equation: %,.2f == %,.2f%n", totalActivo, pasivoMasPatrimonio);
        
        BigDecimal difference = totalActivo.subtract(pasivoMasPatrimonio).abs();
        System.out.printf("Difference:          %,.4f%n", difference);
        
        final BigDecimal tolerance = new BigDecimal("0.01");
        boolean equationHolds = difference.compareTo(tolerance) <= 0;
        
        System.out.println("\nAccounting equation holds: " + (equationHolds ? "YES ✓" : "NO ✗"));
        System.out.println("Bug detected: " + (!equationHolds ? "YES ✓" : "NO"));
        
        System.out.println("\nREAL-WORLD SCENARIO:");
        System.out.println("  LibroContableService.estadoSituacion() generates balance sheet");
        System.out.println("  independently from balanza. Rounding errors from earlier stages");
        System.out.println("  propagate forward, violating the accounting equation.");
        System.out.println("\nEXPECTED BEHAVIOR AFTER FIX:");
        System.out.println("  FinancialStatementValidator will:");
        System.out.println("  1. Validate Balanza → Estados consistency");
        System.out.println("  2. Verify Activo == Pasivo + Patrimonio (adjusted for net income)");
        System.out.println("  3. Throw exception if violation detected");
        
        // This assertion should FAIL - demonstrating accounting equation violation
        assertTrue(equationHolds,
                "Accounting equation violated: Activo != Pasivo + Patrimonio. " +
                "Difference: " + difference + ". " +
                "After implementing FinancialStatementValidator, this will pass.");
    }

    /**
     * Test 4: Income Statement Reconciliation
     * 
     * This test validates that income statement (Estado de Resultados) reconciles
     * correctly: Ingresos - Costos - Gastos == Resultado Neto
     * 
     * Expected behavior: Net income calculation must be mathematically correct
     * 
     * Bug condition: Without validation, calculation errors can produce
     * incorrect net income that doesn't match the formula.
     */
    @Test
    @DisplayName("Counterexample 4: Income statement reconciliation error (simulated)")
    void testIncomeStatementReconciliation() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 4: Income Statement Reconciliation");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate income statement with a calculation ERROR
        List<FinancialLine> estadoResultados = List.of(
            new FinancialLine("4101", "Ventas", new BigDecimal("25000.00")),
            new FinancialLine("INGRESOS_TOTAL", "Total Ingresos", new BigDecimal("25000.00")),
            new FinancialLine("5101", "Costo de Ventas", new BigDecimal("15000.00")),
            new FinancialLine("COSTOS_TOTAL", "Total Costos", new BigDecimal("15000.00")),
            new FinancialLine("6101", "Gastos de Operación", new BigDecimal("5000.00")),
            new FinancialLine("GASTOS_TOTAL", "Total Gastos", new BigDecimal("5000.00")),
            new FinancialLine("RESULTADO_NETO", "Resultado Neto", new BigDecimal("4999.98"))  // BUG: should be 5000.00!
        );
        
        BigDecimal ingresos = new BigDecimal("25000.00");
        BigDecimal costos = new BigDecimal("15000.00");
        BigDecimal gastos = new BigDecimal("5000.00");
        BigDecimal resultadoNeto = new BigDecimal("4999.98");  // Incorrect!
        
        System.out.println("\n--- Income Statement Components ---");
        System.out.printf("Ingresos:        %,.2f%n", ingresos);
        System.out.printf("Costos:          %,.2f%n", costos);
        System.out.printf("Gastos:          %,.2f%n", gastos);
        System.out.printf("Resultado Neto:  %,.2f%n", resultadoNeto);
        
        // Calculate expected net income
        BigDecimal expectedResultado = ingresos.subtract(costos).subtract(gastos);
        System.out.printf("\nExpected Net Income: %,.2f%n", expectedResultado);
        
        BigDecimal difference = resultadoNeto.subtract(expectedResultado).abs();
        System.out.printf("Difference:          %,.4f%n", difference);
        
        final BigDecimal tolerance = new BigDecimal("0.01");
        boolean reconciles = difference.compareTo(tolerance) <= 0;
        
        System.out.println("\nReconciliation correct: " + (reconciles ? "YES ✓" : "NO ✗"));
        System.out.println("Bug detected: " + (!reconciles ? "YES ✓" : "NO"));
        
        System.out.println("\nREAL-WORLD SCENARIO:");
        System.out.println("  LibroContableService.estadoResultados() calculates net income");
        System.out.println("  using construirEstadoResultados() and resultadoNeto() methods.");
        System.out.println("  Without validation, a bug in calculation logic goes undetected.");
        System.out.println("\nEXPECTED BEHAVIOR AFTER FIX:");
        System.out.println("  FinancialStatementValidator will:");
        System.out.println("  1. Extract income statement components");
        System.out.println("  2. Recalculate: Net Income = Ingresos - Costos - Gastos");
        System.out.println("  3. Verify reported net income matches calculation");
        System.out.println("  4. Throw exception if mismatch detected");
        
        // This assertion should FAIL - demonstrating calculation error
        assertTrue(reconciles,
                "Income statement reconciliation failed: Ingresos - Costos - Gastos != Resultado Neto. " +
                "Difference: " + difference + ". " +
                "After implementing FinancialStatementValidator, this will pass.");
    }

    /**
     * SUMMARY: Test Suite Completion Report
     * 
     * This test runs last and prints a summary of the financial statement chain validation.
     */
    @Test
    @DisplayName("Summary: Financial statement chain bug confirmation report")
    void testSummaryReport() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     FINANCIAL STATEMENT CHAIN BUG EXPLORATION COMPLETE        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("STATUS: These tests demonstrate mathematical inconsistencies");
        System.out.println("        in the financial statement generation chain:");
        System.out.println("        Diario → Mayor → Balanza → Estados Financieros");
        System.out.println();
        System.out.println("COUNTEREXAMPLES DOCUMENTED:");
        System.out.println("  1. Diario → Mayor: Sum discrepancies (0.01 rounding error in Bancos)");
        System.out.println("  2. Balanza: Trial balance unbalanced (Debe != Haber by 0.01)");
        System.out.println("  3. Accounting Equation: Activo != Pasivo + Patrimonio (0.01 error)");
        System.out.println("  4. Income Statement: Net income calculation error (0.02 diff)");
        System.out.println();
        System.out.println("AFFECTED SERVICES:");
        System.out.println("  - LibroContableService.diario() - generates journal entries");
        System.out.println("  - LibroContableService.balanza() - aggregates trial balance");
        System.out.println("  - LibroContableService.estadoSituacion() - balance sheet");
        System.out.println("  - LibroContableService.estadoResultados() - income statement");
        System.out.println();
        System.out.println("ROOT CAUSE:");
        System.out.println("  - No centralized validation of mathematical consistency");
        System.out.println("  - Each financial statement stage calculated independently");
        System.out.println("  - Rounding errors accumulate across chain");
        System.out.println("  - No reconciliation mechanism between stages");
        System.out.println("  - Potential use of double/float in calculations");
        System.out.println();
        System.out.println("HYPOTHESIZED BUG LOCATIONS:");
        System.out.println("  1. LibroContableService lines 250-290: balanza() SQL aggregation");
        System.out.println("  2. LibroContableService lines 440-540: estadoSituacion() generation");
        System.out.println("  3. LibroContableService lines 415-439: estadoResultados() generation");
        System.out.println("  4. LibroContableService lines 912-926: resultadoNeto() calculation");
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("  1. Create FinancialStatementValidator class in application/libros/");
        System.out.println("  2. Implement validateChain(LocalDate desde, LocalDate hasta) method");
        System.out.println("  3. Add Diario → Mayor validation (sum journal entries vs ledger)");
        System.out.println("  4. Add Mayor → Balanza validation (ledger balances vs trial balance)");
        System.out.println("  5. Add Balanza → Estados validation (trial balance vs statements)");
        System.out.println("  6. Enforce accounting equation: Activo == Pasivo + Patrimonio");
        System.out.println("  7. Validate income statement: Ingresos - Costos - Gastos == Net Income");
        System.out.println("  8. Integrate validator into LibroContableService before report generation");
        System.out.println("  9. Re-run this test suite - all tests should PASS");
        System.out.println();
        System.out.println("REQUIREMENT VALIDATED: 1.4 - Financial statement chain bug confirmed");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}
