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
    private Method extractUserFriendlyErrorMessageMethod;
    
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
        
        // Access the private extractUserFriendlyErrorMessage method using reflection
        extractUserFriendlyErrorMessageMethod = ProducerController.class.getDeclaredMethod(
            "extractUserFriendlyErrorMessage", Throwable.class);
        extractUserFriendlyErrorMessageMethod.setAccessible(true);
    }
    
    // ========== Tests for extractUserFriendlyErrorMessage ==========
    
    @Test
    public void testExtractUserFriendlyErrorMessage_NullThrowable() throws Exception {
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, (Throwable) null);
        assertEquals("An unknown error occurred", result, "Should return default message for null throwable");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_SimpleMessage() throws Exception {
        RuntimeException ex = new RuntimeException("Connection refused");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("Connection refused", result, "Should return the simple message as-is");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_WithJavaExceptionPrefix() throws Exception {
        RuntimeException ex = new RuntimeException("java.lang.RuntimeException: Connection failed");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("Connection failed", result, "Should strip java.lang.RuntimeException prefix");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_WithIOExceptionPrefix() throws Exception {
        RuntimeException ex = new RuntimeException("java.io.IOException: Network unreachable");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("Network unreachable", result, "Should strip java.io.IOException prefix");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_EmptyMessage() throws Exception {
        RuntimeException ex = new RuntimeException("");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("An unexpected error occurred", result, "Should return default message for empty message");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_WithCause() throws Exception {
        Exception cause = new Exception("Root cause message");
        RuntimeException ex = new RuntimeException(null, cause);
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("Root cause message", result, "Should extract message from cause when main message is null");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_StripsRuntimeException() throws Exception {
        RuntimeException ex = new RuntimeException("RuntimeException: Something went wrong");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertFalse(result.contains("RuntimeException:"), "Should strip RuntimeException: from message");
        assertTrue(result.contains("Something went wrong"), "Should keep the actual error message");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_PreservesRuntimeExceptionInClassName() throws Exception {
        // Ensure class names like "RuntimeExceptionHandler" are not mangled
        RuntimeException ex = new RuntimeException("RuntimeExceptionHandler failed");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("RuntimeExceptionHandler failed", result, "Should preserve class names containing RuntimeException");
    }
    
    @Test
    public void testExtractUserFriendlyErrorMessage_CompletionException() throws Exception {
        RuntimeException ex = new RuntimeException("java.util.concurrent.CompletionException: Task failed");
        String result = (String) extractUserFriendlyErrorMessageMethod.invoke(controller, ex);
        assertEquals("Task failed", result, "Should strip CompletionException prefix");
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
        
        // 3 prime batches (quality_score > 0.8)
        Map<String, Object> batch1 = new HashMap<>();
        batch1.put("quality_score", 0.95);
        recentBatches.add(batch1);
        
        Map<String, Object> batch2 = new HashMap<>();
        batch2.put("quality_score", 0.85);
        recentBatches.add(batch2);
        
        Map<String, Object> batch3 = new HashMap<>();
        batch3.put("quality_score", 0.82);
        recentBatches.add(batch3);
        
        // 1 standard batch (quality_score > 0.6 and quality_score <= 0.8)
        Map<String, Object> batch4 = new HashMap<>();
        batch4.put("quality_score", 0.75);
        recentBatches.add(batch4);
        
        // 1 sub-standard batch (quality_score <= 0.6)
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
        
        // 2 prime batches (quality_score > 0.8)
        Map<String, Object> batch1 = new HashMap<>();
        batch1.put("quality_score", 0.95);
        recentBatches.add(batch1);
        
        Map<String, Object> batch2 = new HashMap<>();
        batch2.put("quality_score", 0.85);
        recentBatches.add(batch2);
        
        // 2 standard batches (quality_score > 0.6 and quality_score <= 0.8)
        Map<String, Object> batch3 = new HashMap<>();
        batch3.put("quality_score", 0.75);
        recentBatches.add(batch3);
        
        Map<String, Object> batch4 = new HashMap<>();
        batch4.put("quality_score", 0.65);
        recentBatches.add(batch4);
        
        // 1 sub-standard batch (quality_score <= 0.6)
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
    
    // ========== Tests for calculateQualityMetrics ==========
    // These tests verify parity with the ML service calculation logic in
    // docker/ml-service/app.py compute_quality_metrics()
    
    private Method calculateQualityMetricsMethod;
    
    private void setupCalculateQualityMetricsMethod() throws Exception {
        if (calculateQualityMetricsMethod == null) {
            calculateQualityMetricsMethod = ProducerController.class.getDeclaredMethod(
                "calculateQualityMetrics", String.class, double.class);
            calculateQualityMetricsMethod.setAccessible(true);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testCalculateQualityMetrics_FreshHighQuality() throws Exception {
        setupCalculateQualityMetricsMethod();
        
        // Test FRESH classification with quality score 0.85
        // quality_percent = 85
        // prime_rate = 80 + (85 * 0.2) = 97%
        // remainder = 3%
        // low_quality = 3 * 0.8 = 2.4%
        // rejection = 3 * 0.2 = 0.6%
        Map<String, Double> metrics = (Map<String, Double>) calculateQualityMetricsMethod.invoke(
            controller, "FRESH", 0.85);
        
        assertEquals(0.97, metrics.get("prime_rate"), 0.01, "Prime rate should be ~97%");
        assertEquals(0.024, metrics.get("low_quality_rate"), 0.01, "Low quality rate should be ~2.4%");
        assertEquals(0.006, metrics.get("rejection_rate"), 0.01, "Rejection rate should be ~0.6%");
        
        // Verify sum is 1.0
        double total = metrics.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, total, 0.001, "Rates should sum to 1.0");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testCalculateQualityMetrics_LowQualityClassification() throws Exception {
        setupCalculateQualityMetricsMethod();
        
        // Test LOW_QUALITY classification with quality score 0.60
        // quality_percent = 60
        // low_quality_rate = 80 + (60 * 0.2) = 92%
        // remainder = 8%
        // prime = 8 * 0.8 = 6.4%
        // rejection = 8 * 0.2 = 1.6%
        Map<String, Double> metrics = (Map<String, Double>) calculateQualityMetricsMethod.invoke(
            controller, "LOW_QUALITY", 0.60);
        
        assertEquals(0.92, metrics.get("low_quality_rate"), 0.01, "Low quality rate should be ~92%");
        assertEquals(0.064, metrics.get("prime_rate"), 0.01, "Prime rate should be ~6.4%");
        assertEquals(0.016, metrics.get("rejection_rate"), 0.01, "Rejection rate should be ~1.6%");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testCalculateQualityMetrics_RottenClassification() throws Exception {
        setupCalculateQualityMetricsMethod();
        
        // Test ROTTEN classification with quality score 0.30
        // quality_percent = 30
        // rejection_rate = 80 + (30 * 0.2) = 86%
        // remainder = 14%
        // low_quality = 14 * 0.8 = 11.2%
        // prime = 14 * 0.2 = 2.8%
        Map<String, Double> metrics = (Map<String, Double>) calculateQualityMetricsMethod.invoke(
            controller, "ROTTEN", 0.30);
        
        assertEquals(0.86, metrics.get("rejection_rate"), 0.01, "Rejection rate should be ~86%");
        assertEquals(0.112, metrics.get("low_quality_rate"), 0.01, "Low quality rate should be ~11.2%");
        assertEquals(0.028, metrics.get("prime_rate"), 0.01, "Prime rate should be ~2.8%");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testCalculateQualityMetrics_RatesSumToOne() throws Exception {
        setupCalculateQualityMetricsMethod();
        
        // Test various inputs to ensure rates always sum to 1.0
        String[][] testCases = {
            {"FRESH", "0.0"},
            {"FRESH", "0.5"},
            {"FRESH", "1.0"},
            {"LOW_QUALITY", "0.3"},
            {"ROTTEN", "0.2"},
            {"UNKNOWN", "0.75"},
        };
        
        for (String[] testCase : testCases) {
            String classification = testCase[0];
            double qualityScore = Double.parseDouble(testCase[1]);
            
            Map<String, Double> metrics = (Map<String, Double>) calculateQualityMetricsMethod.invoke(
                controller, classification, qualityScore);
            
            double total = metrics.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, total, 0.001, 
                String.format("Rates should sum to 1.0 for %s/%.1f", classification, qualityScore));
        }
    }
}
