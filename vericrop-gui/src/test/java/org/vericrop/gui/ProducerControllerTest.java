package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.*;

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
    private Method computeKpisFromRecentBatchesMethod;
    private Method computeDistributionFromRecentBatchesMethod;
    
    @BeforeEach
    public void setUp() throws Exception {
        controller = new ProducerController();
        
        // Access the private calculateRates method using reflection
        calculateRatesMethod = ProducerController.class.getDeclaredMethod(
            "calculateRates", int.class, int.class);
        calculateRatesMethod.setAccessible(true);
        
        // Access the private computeKpisFromRecentBatches method using reflection
        computeKpisFromRecentBatchesMethod = ProducerController.class.getDeclaredMethod(
            "computeKpisFromRecentBatches", List.class);
        computeKpisFromRecentBatchesMethod.setAccessible(true);
        
        // Access the private computeDistributionFromRecentBatches method using reflection
        computeDistributionFromRecentBatchesMethod = ProducerController.class.getDeclaredMethod(
            "computeDistributionFromRecentBatches", List.class);
        computeDistributionFromRecentBatchesMethod.setAccessible(true);
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
    
    // ========== Tests for computeKpisFromRecentBatches ==========
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeKpisFromRecentBatches_WithBatches() throws Exception {
        // Create test batches with different quality scores
        List<Map<String, Object>> recentBatches = new ArrayList<>();
        
        // 3 prime batches (quality > 0.8)
        Map<String, Object> batch1 = new HashMap<>();
        batch1.put("quality_score", 0.95);
        recentBatches.add(batch1);
        
        Map<String, Object> batch2 = new HashMap<>();
        batch2.put("quality_score", 0.85);
        recentBatches.add(batch2);
        
        Map<String, Object> batch3 = new HashMap<>();
        batch3.put("quality_score", 0.82);
        recentBatches.add(batch3);
        
        // 1 standard batch (0.6 < quality <= 0.8)
        Map<String, Object> batch4 = new HashMap<>();
        batch4.put("quality_score", 0.75);
        recentBatches.add(batch4);
        
        // 1 sub-standard batch (quality <= 0.6)
        Map<String, Object> batch5 = new HashMap<>();
        batch5.put("quality_score", 0.50);
        recentBatches.add(batch5);
        
        Map<String, Object> kpis = (Map<String, Object>) computeKpisFromRecentBatchesMethod.invoke(controller, recentBatches);
        
        // Verify total batches
        assertEquals(5, kpis.get("total_batches_today"), "Total batches should be 5");
        
        // Verify average quality: (95 + 85 + 82 + 75 + 50) / 5 = 77.4%
        Object avgQuality = kpis.get("average_quality");
        assertNotNull(avgQuality, "Average quality should not be null");
        assertEquals(77.4, ((Number) avgQuality).doubleValue(), 0.1, "Average quality should be ~77.4%");
        
        // Verify prime percentage: 3/5 = 60%
        Object primePct = kpis.get("prime_percentage");
        assertNotNull(primePct, "Prime percentage should not be null");
        assertEquals(60.0, ((Number) primePct).doubleValue(), 0.1, "Prime percentage should be 60%");
        
        // Verify rejection rate: 1/5 = 20%
        Object rejRate = kpis.get("rejection_rate");
        assertNotNull(rejRate, "Rejection rate should not be null");
        assertEquals(20.0, ((Number) rejRate).doubleValue(), 0.1, "Rejection rate should be 20%");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeKpisFromRecentBatches_EmptyList() throws Exception {
        List<Map<String, Object>> recentBatches = new ArrayList<>();
        
        Map<String, Object> kpis = (Map<String, Object>) computeKpisFromRecentBatchesMethod.invoke(controller, recentBatches);
        
        assertEquals(0, kpis.get("total_batches_today"), "Total batches should be 0 for empty list");
        assertEquals(0, kpis.get("average_quality"), "Average quality should be 0 for empty list");
        assertEquals(0, kpis.get("prime_percentage"), "Prime percentage should be 0 for empty list");
        assertEquals(0, kpis.get("rejection_rate"), "Rejection rate should be 0 for empty list");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeKpisFromRecentBatches_NullList() throws Exception {
        Map<String, Object> kpis = (Map<String, Object>) computeKpisFromRecentBatchesMethod.invoke(controller, (Object) null);
        
        assertEquals(0, kpis.get("total_batches_today"), "Total batches should be 0 for null list");
        assertEquals(0, kpis.get("average_quality"), "Average quality should be 0 for null list");
        assertEquals(0, kpis.get("prime_percentage"), "Prime percentage should be 0 for null list");
        assertEquals(0, kpis.get("rejection_rate"), "Rejection rate should be 0 for null list");
    }
    
    // ========== Tests for computeDistributionFromRecentBatches ==========
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeDistributionFromRecentBatches_WithBatches() throws Exception {
        // Create test batches with different quality scores
        List<Map<String, Object>> recentBatches = new ArrayList<>();
        
        // 2 prime batches (quality > 0.8)
        Map<String, Object> batch1 = new HashMap<>();
        batch1.put("quality_score", 0.95);
        recentBatches.add(batch1);
        
        Map<String, Object> batch2 = new HashMap<>();
        batch2.put("quality_score", 0.85);
        recentBatches.add(batch2);
        
        // 2 standard batches (0.6 < quality <= 0.8)
        Map<String, Object> batch3 = new HashMap<>();
        batch3.put("quality_score", 0.75);
        recentBatches.add(batch3);
        
        Map<String, Object> batch4 = new HashMap<>();
        batch4.put("quality_score", 0.65);
        recentBatches.add(batch4);
        
        // 1 sub-standard batch (quality <= 0.6)
        Map<String, Object> batch5 = new HashMap<>();
        batch5.put("quality_score", 0.50);
        recentBatches.add(batch5);
        
        Map<String, Object> distribution = (Map<String, Object>) computeDistributionFromRecentBatchesMethod.invoke(controller, recentBatches);
        
        // Verify prime distribution: 2/5 = 40%
        Object prime = distribution.get("prime");
        assertNotNull(prime, "Prime should not be null");
        assertEquals(40.0, ((Number) prime).doubleValue(), 0.1, "Prime should be 40%");
        
        // Verify standard distribution: 2/5 = 40%
        Object standard = distribution.get("standard");
        assertNotNull(standard, "Standard should not be null");
        assertEquals(40.0, ((Number) standard).doubleValue(), 0.1, "Standard should be 40%");
        
        // Verify sub_standard distribution: 1/5 = 20%
        Object subStandard = distribution.get("sub_standard");
        assertNotNull(subStandard, "Sub-standard should not be null");
        assertEquals(20.0, ((Number) subStandard).doubleValue(), 0.1, "Sub-standard should be 20%");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeDistributionFromRecentBatches_EmptyList() throws Exception {
        List<Map<String, Object>> recentBatches = new ArrayList<>();
        
        Map<String, Object> distribution = (Map<String, Object>) computeDistributionFromRecentBatchesMethod.invoke(controller, recentBatches);
        
        assertEquals(0.0, distribution.get("prime"), "Prime should be 0 for empty list");
        assertEquals(0.0, distribution.get("standard"), "Standard should be 0 for empty list");
        assertEquals(0.0, distribution.get("sub_standard"), "Sub-standard should be 0 for empty list");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeDistributionFromRecentBatches_NullList() throws Exception {
        Map<String, Object> distribution = (Map<String, Object>) computeDistributionFromRecentBatchesMethod.invoke(controller, (Object) null);
        
        assertEquals(0.0, distribution.get("prime"), "Prime should be 0 for null list");
        assertEquals(0.0, distribution.get("standard"), "Standard should be 0 for null list");
        assertEquals(0.0, distribution.get("sub_standard"), "Sub-standard should be 0 for null list");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testComputeDistributionFromRecentBatches_DistributionSumsTo100() throws Exception {
        // Create test batches
        List<Map<String, Object>> recentBatches = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> batch = new HashMap<>();
            batch.put("quality_score", 0.1 + (i * 0.09)); // Scores from 0.1 to 0.91
            recentBatches.add(batch);
        }
        
        Map<String, Object> distribution = (Map<String, Object>) computeDistributionFromRecentBatchesMethod.invoke(controller, recentBatches);
        
        double sum = ((Number) distribution.get("prime")).doubleValue() +
                     ((Number) distribution.get("standard")).doubleValue() +
                     ((Number) distribution.get("sub_standard")).doubleValue();
        
        assertEquals(100.0, sum, 0.1, "Distribution should sum to 100%");
    }
}
