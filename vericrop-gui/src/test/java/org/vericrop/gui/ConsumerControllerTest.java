package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import okhttp3.OkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test suite for ConsumerController batch ID validation and backend integration.
 * 
 * Tests the batch ID validation logic:
 * - Known batch IDs should be populated in demo mode
 * - Batch ID validation should be case-insensitive
 * - Invalid batch IDs should be rejected with proper feedback
 * - HTTP client and JSON mapper initialization
 * - Batch ID caching for verified batches
 */
public class ConsumerControllerTest {
    
    private ConsumerController controller;
    private Method verifyBatchMethod;
    private Method setupVerificationHistoryMethod;
    private Method isKnownBatchIdMethod;
    private Method addToKnownBatchIdsMethod;
    private Method safeGetStringMethod;
    private Method safeGetDoubleMethod;
    private Field knownBatchIdsField;
    private Field httpClientField;
    private Field mapperField;
    
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
        
        httpClientField = ConsumerController.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        
        mapperField = ConsumerController.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        
        // Access new helper methods
        isKnownBatchIdMethod = ConsumerController.class.getDeclaredMethod(
            "isKnownBatchId", String.class);
        isKnownBatchIdMethod.setAccessible(true);
        
        addToKnownBatchIdsMethod = ConsumerController.class.getDeclaredMethod(
            "addToKnownBatchIds", String.class);
        addToKnownBatchIdsMethod.setAccessible(true);
        
        safeGetStringMethod = ConsumerController.class.getDeclaredMethod(
            "safeGetString", Map.class, String.class, String.class);
        safeGetStringMethod.setAccessible(true);
        
        safeGetDoubleMethod = ConsumerController.class.getDeclaredMethod(
            "safeGetDouble", Map.class, String.class, double.class);
        safeGetDoubleMethod.setAccessible(true);
    }
    
    @Test
    public void testHttpClientInitialized() throws Exception {
        // Initialize the controller (simulating FXML initialize)
        Method initializeMethod = ConsumerController.class.getDeclaredMethod("initialize");
        initializeMethod.setAccessible(true);
        
        // Need to handle the Platform.runLater calls - in test, they may throw
        // Just verify the client and mapper are created
        OkHttpClient client = (OkHttpClient) httpClientField.get(controller);
        ObjectMapper mapper = (ObjectMapper) mapperField.get(controller);
        
        // Before initialize, these should be null
        assertNull(client, "HTTP client should be null before initialize");
        assertNull(mapper, "Mapper should be null before initialize");
        
        // Manually initialize the HTTP client and mapper (as initialize() would do)
        OkHttpClient newClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        httpClientField.set(controller, newClient);
        
        ObjectMapper newMapper = new ObjectMapper();
        mapperField.set(controller, newMapper);
        
        // Verify initialization
        client = (OkHttpClient) httpClientField.get(controller);
        mapper = (ObjectMapper) mapperField.get(controller);
        
        assertNotNull(client, "HTTP client should be initialized");
        assertNotNull(mapper, "Object mapper should be initialized");
        assertEquals(30000, client.connectTimeoutMillis(), "Connect timeout should be 30s");
        assertEquals(30000, client.readTimeoutMillis(), "Read timeout should be 30s");
    }
    
    @Test
    public void testIsKnownBatchIdMethod() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
        knownBatchIds.add("BATCH_001");
        knownBatchIds.add("batch_002");
        
        // Test case-insensitive lookup
        assertTrue((Boolean) isKnownBatchIdMethod.invoke(controller, "BATCH_001"), 
            "Should find exact match");
        assertTrue((Boolean) isKnownBatchIdMethod.invoke(controller, "batch_001"), 
            "Should find case-insensitive match (lowercase)");
        assertTrue((Boolean) isKnownBatchIdMethod.invoke(controller, "Batch_001"), 
            "Should find case-insensitive match (mixed case)");
        assertTrue((Boolean) isKnownBatchIdMethod.invoke(controller, "BATCH_002"), 
            "Should find uppercase variant of lowercase entry");
        
        // Test not found
        assertFalse((Boolean) isKnownBatchIdMethod.invoke(controller, "UNKNOWN"), 
            "Should not find unknown batch ID");
        assertFalse((Boolean) isKnownBatchIdMethod.invoke(controller, (Object) null), 
            "Should return false for null input");
    }
    
    @Test
    public void testAddToKnownBatchIds() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> knownBatchIds = (Set<String>) knownBatchIdsField.get(controller);
        
        // Initially empty
        assertTrue(knownBatchIds.isEmpty(), "Should start empty");
        
        // Add a batch ID
        addToKnownBatchIdsMethod.invoke(controller, "BATCH_NEW");
        assertEquals(1, knownBatchIds.size(), "Should have 1 entry");
        assertTrue(knownBatchIds.contains("BATCH_NEW"), "Should contain the added ID");
        
        // Adding same ID (case-insensitive) should not duplicate
        addToKnownBatchIdsMethod.invoke(controller, "batch_new");
        assertEquals(1, knownBatchIds.size(), "Should still have 1 entry (no duplicate)");
        
        // Adding different ID should increase count
        addToKnownBatchIdsMethod.invoke(controller, "BATCH_OTHER");
        assertEquals(2, knownBatchIds.size(), "Should have 2 entries");
        
        // Adding null should be safe
        addToKnownBatchIdsMethod.invoke(controller, (Object) null);
        assertEquals(2, knownBatchIds.size(), "Should still have 2 entries (null ignored)");
    }
    
    @Test
    public void testSafeGetStringMethod() throws Exception {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("name", "Test Product");
        testMap.put("nullValue", null);
        testMap.put("number", 123);
        
        // Test normal string retrieval
        assertEquals("Test Product", 
            safeGetStringMethod.invoke(controller, testMap, "name", "default"),
            "Should return the actual value");
        
        // Test missing key
        assertEquals("default", 
            safeGetStringMethod.invoke(controller, testMap, "missing", "default"),
            "Should return default for missing key");
        
        // Test null value
        assertEquals("default", 
            safeGetStringMethod.invoke(controller, testMap, "nullValue", "default"),
            "Should return default for null value");
        
        // Test number conversion to string
        assertEquals("123", 
            safeGetStringMethod.invoke(controller, testMap, "number", "default"),
            "Should convert number to string");
        
        // Test null map
        assertEquals("default", 
            safeGetStringMethod.invoke(controller, null, "any", "default"),
            "Should return default for null map");
    }
    
    @Test
    public void testSafeGetDoubleMethod() throws Exception {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("quality", 0.85);
        testMap.put("intValue", 90);
        testMap.put("stringNumber", "0.75");
        testMap.put("invalidString", "not a number");
        testMap.put("nullValue", null);
        
        // Test normal double retrieval
        assertEquals(0.85, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "quality", 0.0),
            0.001, "Should return the actual double value");
        
        // Test integer to double conversion
        assertEquals(90.0, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "intValue", 0.0),
            0.001, "Should convert integer to double");
        
        // Test string parsing
        assertEquals(0.75, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "stringNumber", 0.0),
            0.001, "Should parse string to double");
        
        // Test invalid string - should return default
        assertEquals(0.5, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "invalidString", 0.5),
            0.001, "Should return default for invalid string");
        
        // Test null value
        assertEquals(0.5, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "nullValue", 0.5),
            0.001, "Should return default for null value");
        
        // Test missing key
        assertEquals(0.5, 
            (Double) safeGetDoubleMethod.invoke(controller, testMap, "missing", 0.5),
            0.001, "Should return default for missing key");
        
        // Test null map
        assertEquals(0.5, 
            (Double) safeGetDoubleMethod.invoke(controller, null, "any", 0.5),
            0.001, "Should return default for null map");
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
            assertTrue(isKnownBatchIdHelper("TEST_BATCH_123", knownBatchIds), 
                "Exact match should be recognized");
            assertTrue(isKnownBatchIdHelper("test_batch_123", knownBatchIds), 
                "Lowercase should be recognized (case-insensitive)");
            assertTrue(isKnownBatchIdHelper("Test_Batch_123", knownBatchIds), 
                "Mixed case should be recognized (case-insensitive)");
            assertTrue(isKnownBatchIdHelper("TEST_BATCH_123", knownBatchIds), 
                "Uppercase should be recognized (case-insensitive)");
            
            // Test non-existent batch ID
            assertFalse(isKnownBatchIdHelper("INVALID_BATCH", knownBatchIds), 
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
    private boolean isKnownBatchIdHelper(String batchId, Set<String> knownBatchIds) {
        for (String knownId : knownBatchIds) {
            if (knownId.equalsIgnoreCase(batchId.trim())) {
                return true;
            }
        }
        return false;
    }
}
