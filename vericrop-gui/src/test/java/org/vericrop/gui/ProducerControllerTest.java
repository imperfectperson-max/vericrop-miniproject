package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

/**
 * Test suite for ProducerController rate calculation helper.
 * 
 * Tests the canonical formulas:
 * - prime_rate = prime_count / total_count
 * - rejection_rate = rejected_count / total_count
 * - total_count = prime_count + rejected_count
 * - Edge case: total_count == 0 => rates = 0.0
 */
public class ProducerControllerTest {
    
    private ProducerController controller;
    private Method calculateRatesMethod;
    
    @BeforeEach
    public void setUp() throws Exception {
        controller = new ProducerController();
        
        // Access the private calculateRates method using reflection
        calculateRatesMethod = ProducerController.class.getDeclaredMethod(
            "calculateRates", int.class, int.class);
        calculateRatesMethod.setAccessible(true);
    }
    
    @Test
    public void testCalculateRates_NormalCounts() throws Exception {
        // Test with normal counts: 8 prime, 2 rejected
        // Expected: prime_rate = 80%, rejection_rate = 20%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 8, 2);
        
        assertEquals(80.0, rates[0], 0.01, "Prime rate should be 80%");
        assertEquals(20.0, rates[1], 0.01, "Rejection rate should be 20%");
    }
    
    @Test
    public void testCalculateRates_AllPrime() throws Exception {
        // Test with all prime: 10 prime, 0 rejected
        // Expected: prime_rate = 100%, rejection_rate = 0%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 10, 0);
        
        assertEquals(100.0, rates[0], 0.01, "Prime rate should be 100%");
        assertEquals(0.0, rates[1], 0.01, "Rejection rate should be 0%");
    }
    
    @Test
    public void testCalculateRates_AllRejected() throws Exception {
        // Test with all rejected: 0 prime, 5 rejected
        // Expected: prime_rate = 0%, rejection_rate = 100%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 0, 5);
        
        assertEquals(0.0, rates[0], 0.01, "Prime rate should be 0%");
        assertEquals(100.0, rates[1], 0.01, "Rejection rate should be 100%");
    }
    
    @Test
    public void testCalculateRates_ZeroCount() throws Exception {
        // Test edge case: 0 prime, 0 rejected
        // Expected: both rates should be 0.0 (not NaN or Inf)
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 0, 0);
        
        assertEquals(0.0, rates[0], 0.01, "Prime rate should be 0% for zero count");
        assertEquals(0.0, rates[1], 0.01, "Rejection rate should be 0% for zero count");
        
        // Verify not NaN or Infinite
        assertFalse(Double.isNaN(rates[0]), "Prime rate should not be NaN");
        assertFalse(Double.isNaN(rates[1]), "Rejection rate should not be NaN");
        assertFalse(Double.isInfinite(rates[0]), "Prime rate should not be Infinite");
        assertFalse(Double.isInfinite(rates[1]), "Rejection rate should not be Infinite");
    }
    
    @Test
    public void testCalculateRates_EqualCounts() throws Exception {
        // Test with equal counts: 5 prime, 5 rejected
        // Expected: prime_rate = 50%, rejection_rate = 50%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 5, 5);
        
        assertEquals(50.0, rates[0], 0.01, "Prime rate should be 50%");
        assertEquals(50.0, rates[1], 0.01, "Rejection rate should be 50%");
    }
    
    @Test
    public void testCalculateRates_LargeCounts() throws Exception {
        // Test with large counts: 625 prime, 375 rejected (total = 1000)
        // Expected: prime_rate = 62.5%, rejection_rate = 37.5%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 625, 375);
        
        assertEquals(62.5, rates[0], 0.01, "Prime rate should be 62.5%");
        assertEquals(37.5, rates[1], 0.01, "Rejection rate should be 37.5%");
    }
    
    @Test
    public void testCalculateRates_SmallPercentage() throws Exception {
        // Test with small rejection percentage: 99 prime, 1 rejected
        // Expected: prime_rate = 99%, rejection_rate = 1%
        double[] rates = (double[]) calculateRatesMethod.invoke(controller, 99, 1);
        
        assertEquals(99.0, rates[0], 0.01, "Prime rate should be 99%");
        assertEquals(1.0, rates[1], 0.01, "Rejection rate should be 1%");
    }
    
    @Test
    public void testCalculateRates_RatesSumTo100() throws Exception {
        // Test that rates always sum to 100% (within floating point tolerance)
        int[][] testCases = {
            {8, 2},     // 80% + 20%
            {5, 5},     // 50% + 50%
            {7, 3},     // 70% + 30%
            {100, 1},   // ~99.01% + ~0.99%
            {1, 1}      // 50% + 50%
        };
        
        for (int[] testCase : testCases) {
            double[] rates = (double[]) calculateRatesMethod.invoke(
                controller, testCase[0], testCase[1]);
            double sum = rates[0] + rates[1];
            assertEquals(100.0, sum, 0.01, 
                String.format("Rates should sum to 100%% for prime=%d, rejected=%d", 
                    testCase[0], testCase[1]));
        }
    }
}
