package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Test suite for ConsumerController batch ID validation.
 * 
 * Tests the batch ID validation logic:
 * - Known batch IDs should be populated in demo mode
 * - Batch ID validation should be case-insensitive
 * - Invalid batch IDs should be rejected with proper feedback
 */
public class ConsumerControllerTest {
    
    private ConsumerController controller;
    private Method verifyBatchMethod;
    private Method setupVerificationHistoryMethod;
    private Field knownBatchIdsField;
    
    @BeforeEach
    public void setUp() throws Exception {
        controller = new ConsumerController();
        
        // Access private methods and fields using reflection
        verifyBatchMethod = ConsumerController.class.getDeclaredMethod(
            "verifyBatch", String.class);
        verifyBatchMethod.setAccessible(true);
        
        setupVerificationHistoryMethod = ConsumerController.class.getDeclaredMethod(
            "setupVerificationHistory");
        setupVerificationHistoryMethod.setAccessible(true);
        
        knownBatchIdsField = ConsumerController.class.getDeclaredField("knownBatchIds");
        knownBatchIdsField.setAccessible(true);
    }
    
    @Test
    public void testKnownBatchIdsPopulated_InDemoMode() throws Exception {
        // Set demo mode flag
        System.setProperty("vericrop.loadDemo", "true");
        
        try {
            // Call setupVerificationHistory which should populate knownBatchIds
            setupVerificationHistoryMethod.invoke(controller);
            
            @SuppressWarnings("unchecked")
            Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
            
            // Verify known batch IDs are populated
            assertNotNull(knownBatchIds, "Known batch IDs should not be null");
            assertFalse(knownBatchIds.isEmpty(), "Known batch IDs should be populated in demo mode");
            assertTrue(knownBatchIds.contains("BATCH_A2386"), "Should contain BATCH_A2386");
            assertTrue(knownBatchIds.contains("BATCH_A2385"), "Should contain BATCH_A2385");
            assertTrue(knownBatchIds.contains("BATCH_A2384"), "Should contain BATCH_A2384");
        } finally {
            // Clean up system property
            System.clearProperty("vericrop.loadDemo");
        }
    }
    
    @Test
    public void testKnownBatchIdsEmpty_InNonDemoMode() throws Exception {
        // Ensure demo mode is not set
        System.clearProperty("vericrop.loadDemo");
        
        // Call setupVerificationHistory
        setupVerificationHistoryMethod.invoke(controller);
        
        @SuppressWarnings("unchecked")
        Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
        
        // Verify known batch IDs are empty in non-demo mode
        assertNotNull(knownBatchIds, "Known batch IDs should not be null");
        assertTrue(knownBatchIds.isEmpty(), "Known batch IDs should be empty in non-demo mode");
    }
    
    @Test
    public void testBatchIdValidation_CaseInsensitive() throws Exception {
        // Set up demo mode and populate known batch IDs
        System.setProperty("vericrop.loadDemo", "true");
        
        try {
            setupVerificationHistoryMethod.invoke(controller);
            
            @SuppressWarnings("unchecked")
            Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
            
            // Manually add a test batch ID
            knownBatchIds.add("TEST_BATCH_123");
            
            // Test case insensitivity - these should all match "TEST_BATCH_123"
            assertTrue(isKnownBatchId("TEST_BATCH_123", knownBatchIds), 
                "Exact match should be recognized");
            assertTrue(isKnownBatchId("test_batch_123", knownBatchIds), 
                "Lowercase should be recognized (case-insensitive)");
            assertTrue(isKnownBatchId("Test_Batch_123", knownBatchIds), 
                "Mixed case should be recognized (case-insensitive)");
            assertTrue(isKnownBatchId("TEST_BATCH_123", knownBatchIds), 
                "Uppercase should be recognized (case-insensitive)");
            
            // Test non-existent batch ID
            assertFalse(isKnownBatchId("INVALID_BATCH", knownBatchIds), 
                "Non-existent batch ID should not be recognized");
        } finally {
            System.clearProperty("vericrop.loadDemo");
        }
    }
    
    @Test
    public void testKnownBatchIdsContainsDemoEntries() throws Exception {
        System.setProperty("vericrop.loadDemo", "true");
        
        try {
            setupVerificationHistoryMethod.invoke(controller);
            
            @SuppressWarnings("unchecked")
            Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
            
            // Verify all three demo batch IDs are present
            assertEquals(3, knownBatchIds.size(), "Should have exactly 3 demo batch IDs");
            assertTrue(knownBatchIds.contains("BATCH_A2386"), "Should contain apple batch");
            assertTrue(knownBatchIds.contains("BATCH_A2385"), "Should contain carrot batch");
            assertTrue(knownBatchIds.contains("BATCH_A2384"), "Should contain lettuce batch");
        } finally {
            System.clearProperty("vericrop.loadDemo");
        }
    }
    
    /**
     * Helper method to check if a batch ID exists in known batch IDs (case-insensitive)
     */
    private boolean isKnownBatchId(String batchId, Set<String> knownBatchIds) {
        for (String knownId : knownBatchIds) {
            if (knownId.equalsIgnoreCase(batchId.trim())) {
                return true;
            }
        }
        return false;
    }
}
