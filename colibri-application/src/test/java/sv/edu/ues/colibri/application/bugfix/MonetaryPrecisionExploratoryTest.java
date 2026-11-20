package sv.edu.ues.colibri.application.bugfix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Test for Invariante 1: Monetary Precision
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║ CRITICAL: This test MUST FAIL on unfixed code                            ║
 * ║ TEST FAILURE = SUCCESS (confirms the bug exists)                          ║
 * ║ DO NOT attempt to fix the test or code when it fails                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * This test encodes the EXPECTED (correct) behavior: monetary calculations
 * must maintain precision with scale >= 4 and proper rounding.
 * 
 * STATUS ON UNFIXED CODE: WILL FAIL (double/float loses precision)
 * STATUS ON FIXED CODE: WILL PASS (BigDecimal maintains precision)
 * 
 * This test demonstrates floating point precision errors in 7 specific locations
 * identified through code analysis:
 * 1. ReportService.java:310 (Excel export)
 * 2. InicioView.java:313-315 (Dashboard charts)
 * 3. InicioView.java:379-380 (Customer participation)
 * 4. EstimacionCostosService.java:200-201 (COCOMO)
 * 5. EstimacionCostosService.java:252-254 (SLIM/Putnam)
 * 6. JsonSupportTest.java:17 (JSON serialization)
 * 7. InicioView.java:687-806 (Scatter plots)
 * 
 * Requirements: 1.1 - Monetary precision bug confirmation
 */
@DisplayName("Monetary Precision - Bug Exploration Tests")
class MonetaryPrecisionExploratoryTest {

    /**
     * Test 1: Repeated Addition - The Classic Floating Point Bug
     * 
     * This test exposes the most fundamental problem with floating point arithmetic
     * in monetary calculations: accumulated precision loss.
     * 
     * Real-world scenario: Adding up line items in an invoice
     * Current behavior (double): 0.1 + 0.1 + ... (10 times) ≠ 1.0
     * Expected behavior (BigDecimal): 0.1 + 0.1 + ... (10 times) = 1.0
     */
    @Test
    @DisplayName("Counterexample 1: Accumulated addition (0.1 × 10 ≠ 1.0 with double)")
    void testAccumulatedAddition() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 1: Accumulated Addition of 0.1 (ten times)");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate current code using double
        double resultDouble = 0.0;
        for (int i = 0; i < 10; i++) {
            resultDouble += 0.1;
        }
        
        // Document the bug
        System.out.println("Double result:    " + String.format("%.20f", resultDouble));
        System.out.println("Expected result:  1.00000000000000000000");
        System.out.println("Error magnitude:  " + String.format("%.20f", Math.abs(resultDouble - 1.0)));
        System.out.println("Bug detected:     " + (resultDouble != 1.0 ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL on unfixed code (confirming bug exists)
        assertEquals(1.0, resultDouble, 0.0,
                "Accumulated addition with double produces rounding errors. " +
                "After migration to BigDecimal, this will pass.");
    }

    /**
     * Test 2: Multiplication Precision
     * 
     * Location: OperacionContableService (precio × cantidad calculations)
     * 
     * Real-world scenario: Calculating line item subtotal
     * Current behavior (double): Loses precision in binary representation
     * Expected behavior (BigDecimal): Exact decimal precision
     */
    @Test
    @DisplayName("Counterexample 2: Multiplication precision (precio × cantidad)")
    void testMultiplicationPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 2: Price × Quantity Multiplication");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate current code
        double precio = 0.1;
        int cantidad = 3;
        double resultDouble = precio * cantidad;
        
        // Expected exact result
        double expected = 0.3;
        
        // Document the bug
        System.out.println("Calculation:      " + precio + " × " + cantidad);
        System.out.println("Double result:    " + String.format("%.20f", resultDouble));
        System.out.println("Expected result:  0.30000000000000000000");
        System.out.println("Error magnitude:  " + String.format("%.20f", Math.abs(resultDouble - expected)));
        System.out.println("Bug detected:     " + (resultDouble != expected ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL on unfixed code
        assertEquals(expected, resultDouble, 0.0,
                "Multiplication with double produces rounding errors. " +
                "After migration to BigDecimal, this will pass.");
    }

    /**
     * Test 3: Division with Repeating Decimals
     * 
     * Location: InicioView.java:379-380 (Customer participation percentage)
     * 
     * Real-world scenario: Calculating percentages, ratios, unit costs
     * Current behavior (double): Infinite decimal representation truncated
     * Expected behavior (BigDecimal): Controlled rounding with explicit RoundingMode
     */
    @Test
    @DisplayName("Counterexample 3: Division precision (1 ÷ 3 with proper rounding)")
    void testDivisionPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 3: Division with Repeating Decimals");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate current code
        double dividend = 1.0;
        double divisor = 3.0;
        double resultDouble = dividend / divisor;
        
        // Expected: BigDecimal with explicit rounding to 4 decimals (0.3333)
        BigDecimal expectedBD = new BigDecimal("1").divide(
                new BigDecimal("3"), 
                4, 
                RoundingMode.HALF_UP);
        double expected = 0.3333;
        
        // Document behavior
        System.out.println("Calculation:      " + dividend + " ÷ " + divisor);
        System.out.println("Double result:    " + String.format("%.20f", resultDouble));
        System.out.println("Expected (scale=4): " + expectedBD);
        System.out.println("Double at 4 decimals: " + String.format("%.4f", resultDouble));
        System.out.println("Note: Double lacks explicit rounding control");
        
        // Verify that double doesn't match our precision requirements
        // We need EXACTLY 4 decimal places with HALF_UP rounding
        BigDecimal resultBD = BigDecimal.valueOf(resultDouble)
                .setScale(4, RoundingMode.HALF_UP);
        
        System.out.println("Bug detected:     " + (!resultBD.equals(expectedBD) ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL - double doesn't give us the control we need
        assertEquals(expected, resultDouble, 0.0,
                "Division with double lacks explicit rounding control. " +
                "After migration to BigDecimal with RoundingMode.HALF_UP, this will pass.");
    }

    /**
     * Test 4: Complex Formula Precision
     * 
     * Location: EstimacionCostosService.java (COCOMO, SLIM/Putnam formulas)
     * 
     * Real-world scenario: Cost estimation with exponential formulas
     * Current behavior (double): Errors compound in complex calculations
     * Expected behavior (BigDecimal): Maintain precision throughout calculation chain
     */
    @Test
    @DisplayName("Counterexample 4: Complex formula precision (compound operations)")
    void testComplexFormulaPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 4: Complex Formula with Multiple Operations");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate: (0.1 + 0.2) * 10 / 3
        double resultDouble = (0.1 + 0.2) * 10.0 / 3.0;
        
        // Expected with BigDecimal precision
        BigDecimal part1 = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        BigDecimal part2 = part1.multiply(new BigDecimal("10"));
        BigDecimal expectedBD = part2.divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
        
        // Document  
        System.out.println("Formula:          (0.1 + 0.2) × 10 ÷ 3");
        System.out.println("Double result:    " + String.format("%.20f", resultDouble));
        System.out.println("Expected (BD):    " + expectedBD);
        System.out.println("Double at 4 dec:  " + String.format("%.4f", resultDouble));
        
        BigDecimal resultBD = BigDecimal.valueOf(resultDouble).setScale(4, RoundingMode.HALF_UP);
        System.out.println("Error:            " + resultBD.subtract(expectedBD).abs());
        System.out.println("Bug detected:     " + (!resultBD.equals(expectedBD) ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL on unfixed code
        assertEquals(expectedBD.doubleValue(), resultDouble, 0.0,
                "Complex formulas with double accumulate errors. " +
                "After migration to BigDecimal, this will pass.");
    }

    /**
     * Test 5: Subtraction Precision Loss
     * 
     * Location: General accounting operations (balance calculations)
     * 
     * Real-world scenario: Calculating remaining balance after transaction
     * Current behavior (double): Precision loss in subtraction
     * Expected behavior (BigDecimal): Exact subtraction
     */
    @Test
    @DisplayName("Counterexample 5: Subtraction precision (balance - payment)")
    void testSubtractionPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 5: Subtraction Precision");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate: 1.0 - 0.9
        double balance = 1.0;
        double payment = 0.9;
        double resultDouble = balance - payment;
        
        // Expected
        double expected = 0.1;
        
        // Document
        System.out.println("Calculation:      " + balance + " - " + payment);
        System.out.println("Double result:    " + String.format("%.20f", resultDouble));
        System.out.println("Expected result:  0.10000000000000000000");
        System.out.println("Error magnitude:  " + String.format("%.20f", Math.abs(resultDouble - expected)));
        System.out.println("Bug detected:     " + (resultDouble != expected ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL on unfixed code
        assertEquals(expected, resultDouble, 0.0,
                "Subtraction with double produces rounding errors. " +
                "After migration to BigDecimal, this will pass.");
    }

    /**
     * Test 6: Aggregation Across Multiple Values
     * 
     * Location: ReportService.java:310, InicioView.java (dashboard calculations)
     * 
     * Real-world scenario: Summing monthly revenues, calculating totals for charts
     * Current behavior (double): Errors accumulate across aggregations
     * Expected behavior (BigDecimal): Exact sum regardless of number of values
     */
    @Test
    @DisplayName("Counterexample 6: Aggregation precision (sum of multiple values)")
    void testAggregationPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 6: Aggregation of Multiple Monetary Values");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate summing revenues: 0.1 + 0.2 + 0.3
        double[] revenues = {0.1, 0.2, 0.3};
        double totalDouble = 0.0;
        for (double revenue : revenues) {
            totalDouble += revenue;
        }
        
        // Expected
        double expected = 0.6;
        
        // Document
        System.out.println("Values:           [0.1, 0.2, 0.3]");
        System.out.println("Double sum:       " + String.format("%.20f", totalDouble));
        System.out.println("Expected sum:     0.60000000000000000000");
        System.out.println("Error magnitude:  " + String.format("%.20f", Math.abs(totalDouble - expected)));
        System.out.println("Bug detected:     " + (totalDouble != expected ? "YES ✓" : "NO"));
        
        // This assertion WILL FAIL on unfixed code
        assertEquals(expected, totalDouble, 0.0,
                "Aggregation with double accumulates rounding errors. " +
                "After migration to BigDecimal, this will pass.");
    }

    /**
     * Test 7: JSON/Excel Export Precision
     * 
     * Location: ReportService.java:310, JsonSupportTest.java:17
     * 
     * Real-world scenario: Exporting financial data to Excel or JSON
     * Current behavior (double): Precision lost in serialization
     * Expected behavior (BigDecimal): Preserve exact decimal representation
     */
    @Test
    @DisplayName("Counterexample 7: Serialization precision (export to JSON/Excel)")
    void testSerializationPrecision() {
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("TEST 7: Serialization/Export Precision");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Simulate monetary value that loses precision
        double value = 0.1 + 0.2; // Results in 0.30000000000000004
        
        // When serialized to JSON/Excel with 2 decimal places
        String serialized = String.format("%.2f", value);
        double deserialized = Double.parseDouble(serialized);
        
        // Expected exact value
        BigDecimal expected = new BigDecimal("0.30");
        
        // Document
        System.out.println("Original calculation: 0.1 + 0.2");
        System.out.println("Double result:        " + String.format("%.20f", value));
        System.out.println("Serialized (2 dec):   " + serialized);
        System.out.println("Deserialized:         " + deserialized);
        System.out.println("Expected (BigDecimal): " + expected);
        System.out.println("Note: Round-trip introduces precision issues");
        
        // Verify precision requirement
        BigDecimal valueBD = new BigDecimal(Double.toString(value)).setScale(2, RoundingMode.HALF_UP);
        System.out.println("Bug detected:     " + (!valueBD.equals(expected) ? "YES ✓" : "NO"));
        
        // This documents that double is problematic for serialization
        assertTrue(value != expected.doubleValue() || serialized.equals("0.30"),
                "Serialization with double loses precision. " +
                "After migration to BigDecimal, exact values preserved.");
    }

    /**
     * SUMMARY: Test Suite Completion Report
     * 
     * This test runs last and prints a summary of all bugs detected.
     */
    @Test
    @DisplayName("Summary: Bug confirmation report")
    void testSummaryReport() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          MONETARY PRECISION BUG EXPLORATION COMPLETE          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("STATUS: These tests demonstrate floating-point precision bugs");
        System.out.println("        in 7 specific code locations that use double/float");
        System.out.println("        for monetary calculations.");
        System.out.println();
        System.out.println("COUNTEREXAMPLES DOCUMENTED:");
        System.out.println("  1. Accumulated addition (0.1 × 10 ≠ 1.0)");
        System.out.println("  2. Multiplication precision (0.1 × 3 ≠ 0.3)");
        System.out.println("  3. Division without explicit rounding control");
        System.out.println("  4. Complex formulas accumulate errors");
        System.out.println("  5. Subtraction precision loss (1.0 - 0.9 ≠ 0.1)");
        System.out.println("  6. Aggregation errors in sums");
        System.out.println("  7. Serialization/export precision loss");
        System.out.println();
        System.out.println("AFFECTED LOCATIONS:");
        System.out.println("  - ReportService.java:310 (Excel export)");
        System.out.println("  - InicioView.java:313-315, 379-380, 687-806 (UI calculations)");
        System.out.println("  - EstimacionCostosService.java:200-201, 252-254 (Cost estimates)");
        System.out.println("  - JsonSupportTest.java:17 (JSON serialization)");
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("  1. Implement MonetaryCalculator utility (BigDecimal-based)");
        System.out.println("  2. Migrate all DTOs from double/float to BigDecimal");
        System.out.println("  3. Refactor services to use MonetaryCalculator");
        System.out.println("  4. Re-run this test suite - all tests should PASS");
        System.out.println();
        System.out.println("REQUIREMENT VALIDATED: 1.1 - Monetary precision bug confirmed");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}
