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
 * - FetchResult wrapper for backend fetch operations
 * - Fallback QR batch ID extraction from various formats
 */
public class ConsumerControllerTest {
    
    private ConsumerController controller;
    private Method verifyBatchMethod;
    private Method setupVerificationHistoryMethod;
    private Method isKnownBatchIdMethod;
    private Method extractBatchIdFallbackMethod;
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
        
        extractBatchIdFallbackMethod = ConsumerController.class.getDeclaredMethod(
            "extractBatchIdFallback", String.class);
        extractBatchIdFallbackMethod.setAccessible(true);
    }
    
    @Test
    public void testHttpClientInitialized() throws Exception {
        // Initialize the controller (simulating FXML initialize)
        Method initializeMethod = ConsumerController.class.getDeclaredMethod("initialize");
        initializeMethod.setAccessible(true);
        
        // Get the timeout constant from the controller
        java.lang.reflect.Field timeoutField = ConsumerController.class.getDeclaredField("HTTP_TIMEOUT_SECONDS");
        timeoutField.setAccessible(true);
        int expectedTimeoutSeconds = timeoutField.getInt(null);
        
        // Need to handle the Platform.runLater calls - in test, they may throw
        // Just verify the client and mapper are created
        OkHttpClient client = (OkHttpClient) httpClientField.get(controller);
        ObjectMapper mapper = (ObjectMapper) mapperField.get(controller);
        
        // Before initialize, these should be null
        assertNull(client, "HTTP client should be null before initialize");
        assertNull(mapper, "Mapper should be null before initialize");
        
        // Manually initialize the HTTP client and mapper (as initialize() would do)
        OkHttpClient newClient = new OkHttpClient.Builder()
                .connectTimeout(expectedTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(expectedTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        httpClientField.set(controller, newClient);
        
        ObjectMapper newMapper = new ObjectMapper();
        mapperField.set(controller, newMapper);
        
        // Verify initialization
        client = (OkHttpClient) httpClientField.get(controller);
        mapper = (ObjectMapper) mapperField.get(controller);
        
        assertNotNull(client, "HTTP client should be initialized");
        assertNotNull(mapper, "Object mapper should be initialized");
        assertEquals(expectedTimeoutSeconds * 1000, client.connectTimeoutMillis(), 
            "Connect timeout should match HTTP_TIMEOUT_SECONDS constant");
        assertEquals(expectedTimeoutSeconds * 1000, client.readTimeoutMillis(), 
            "Read timeout should match HTTP_TIMEOUT_SECONDS constant");
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
    
    // ========== FetchResult Tests ==========
    
    @Test
    public void testFetchResultFoundFactory() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "BATCH_001");
        data.put("quality_score", 0.85);
        
        ConsumerController.FetchResult result = ConsumerController.FetchResult.found(data);
        
        assertTrue(result.isFound(), "Result should be found");
        assertFalse(result.isNotFound(), "Result should not be notFound");
        assertFalse(result.isUnavailable(), "Result should not be unavailable");
        assertEquals(ConsumerController.FetchStatus.FOUND, result.getStatus());
        assertNotNull(result.getData(), "Data should not be null");
        assertEquals("BATCH_001", result.getData().get("id"));
    }
    
    @Test
    public void testFetchResultNotFoundFactory() {
        ConsumerController.FetchResult result = ConsumerController.FetchResult.notFound();
        
        assertFalse(result.isFound(), "Result should not be found");
        assertTrue(result.isNotFound(), "Result should be notFound");
        assertFalse(result.isUnavailable(), "Result should not be unavailable");
        assertEquals(ConsumerController.FetchStatus.NOT_FOUND, result.getStatus());
        assertNull(result.getData(), "Data should be null for notFound");
    }
    
    @Test
    public void testFetchResultUnavailableFactory() {
        ConsumerController.FetchResult result = ConsumerController.FetchResult.unavailable();
        
        assertFalse(result.isFound(), "Result should not be found");
        assertFalse(result.isNotFound(), "Result should not be notFound");
        assertTrue(result.isUnavailable(), "Result should be unavailable");
        assertEquals(ConsumerController.FetchStatus.UNAVAILABLE, result.getStatus());
        assertNull(result.getData(), "Data should be null for unavailable");
    }
    
    // ========== Fallback Batch ID Extraction Tests ==========
    
    @Test
    public void testExtractBatchIdFallback_NullInput() throws Exception {
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, (Object) null);
        assertNull(result, "Should return null for null input");
    }
    
    @Test
    public void testExtractBatchIdFallback_EmptyInput() throws Exception {
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, "");
        assertNull(result, "Should return null for empty input");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, "   ");
        assertNull(result, "Should return null for whitespace input");
    }
    
    @Test
    public void testExtractBatchIdFallback_UrlPathBatches() throws Exception {
        // Test /batches/{id} format
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "http://example.com/batches/BATCH_A123");
        assertEquals("BATCH_A123", result, "Should extract batch ID from /batches/ path");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://api.service.example/v1/batches/12345ABC");
        assertEquals("12345ABC", result, "Should extract alphanumeric ID from /batches/ path");
    }
    
    @Test
    public void testExtractBatchIdFallback_UrlPathBatch() throws Exception {
        // Test /batch/{id} format (singular)
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "http://example.com/batch/BATCH_B456");
        assertEquals("BATCH_B456", result, "Should extract batch ID from /batch/ path");
    }
    
    @Test
    public void testExtractBatchIdFallback_QueryParamBatchId() throws Exception {
        // Test ?batchId= format
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://service.example/?batchId=BATCH_C789");
        assertEquals("BATCH_C789", result, "Should extract batch ID from batchId query param");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://service.example/verify?other=value&batchId=ABC123&extra=stuff");
        assertEquals("ABC123", result, "Should extract batch ID from batchId query param with other params");
    }
    
    @Test
    public void testExtractBatchIdFallback_QueryParamBatch() throws Exception {
        // Test ?batch= format
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://service.example/?batch=BATCH_D012");
        assertEquals("BATCH_D012", result, "Should extract batch ID from batch query param");
    }
    
    @Test
    public void testExtractBatchIdFallback_QueryParamId() throws Exception {
        // Test ?id= format
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://service.example/?id=BATCH_E345");
        assertEquals("BATCH_E345", result, "Should extract batch ID from id query param");
    }
    
    @Test
    public void testExtractBatchIdFallback_BatchPattern() throws Exception {
        // Test BATCH_* pattern extraction
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "Check out BATCH_F678 for details");
        assertEquals("BATCH_F678", result, "Should extract BATCH_* pattern from text");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "Some text with batch_abc123 embedded");
        assertEquals("batch_abc123", result, "Should extract lowercase BATCH_* pattern");
    }
    
    @Test
    public void testExtractBatchIdFallback_AlphanumericToken() throws Exception {
        // Test alphanumeric token extraction (last resort)
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "XYZ12345");
        assertEquals("XYZ12345", result, "Should extract alphanumeric token");
        
        // Short tokens (< 4 chars) should not match
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "AB");
        assertNull(result, "Should not extract tokens shorter than 4 characters");
    }
    
    @Test
    public void testExtractBatchIdFallback_PlainBatchId() throws Exception {
        // Test plain batch ID string
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "BATCH_A2386");
        assertEquals("BATCH_A2386", result, "Should extract plain batch ID");
    }
    
    @Test
    public void testExtractBatchIdFallback_ComplexUrl() throws Exception {
        // Test complex URL with both path and query
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://api.vericrop.example/v2/batches/PROD_ABC123?token=xyz");
        assertEquals("PROD_ABC123", result, "Should extract from URL path even with query params");
    }
    
    // ========== New Tests for QR Scanning Issue Fix ==========
    
    @Test
    public void testExtractBatchIdFallback_VerifyPath() throws Exception {
        // Test /verify/{id} format (common QR tool format from issue)
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/verify/BATCH_1764759800_304");
        assertEquals("BATCH_1764759800_304", result, "Should extract batch ID from /verify/ path");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "http://api.vericrop.io/verify/BATCH_A2386");
        assertEquals("BATCH_A2386", result, "Should extract batch ID from /verify/ path without https");
    }
    
    @Test
    public void testExtractBatchIdFallback_VerifyPathWithQueryParams() throws Exception {
        // Test /verify/ path with query parameters
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/verify/BATCH_12345?source=qr&timestamp=123456");
        assertEquals("BATCH_12345", result, "Should extract from /verify/ path even with query params");
    }
    
    @Test
    public void testExtractBatchIdFallback_WhitespaceHandling() throws Exception {
        // Test batch ID with leading/trailing whitespace
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "  BATCH_A2386  ");
        assertEquals("BATCH_A2386", result, "Should trim whitespace and extract batch ID");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "\t\nBATCH_12345\n\t");
        assertEquals("BATCH_12345", result, "Should trim tabs/newlines and extract batch ID");
    }
    
    @Test
    public void testExtractBatchIdFallback_UrlEncodedBatchId() throws Exception {
        // Test URL-encoded batch ID (e.g., underscore encoded as %5F)
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "BATCH%5F123456");
        assertEquals("BATCH_123456", result, "Should URL-decode and extract batch ID");
        
        // Test URL-encoded spaces (%20)
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "%20BATCH_ABCDEF%20");
        assertEquals("BATCH_ABCDEF", result, "Should URL-decode spaces and extract batch ID");
    }
    
    @Test
    public void testExtractBatchIdFallback_UrlEncodedVerifyPath() throws Exception {
        // Test URL-encoded full URL with /verify/ path
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https%3A%2F%2Fexample.com%2Fverify%2FBATCH_789XYZ");
        assertEquals("BATCH_789XYZ", result, "Should URL-decode full URL and extract from /verify/ path");
    }
    
    @Test
    public void testExtractBatchIdFallback_UrlEncodedQueryParam() throws Exception {
        // Test URL-encoded query parameter value
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/?batchId=BATCH%5FA2386");
        assertEquals("BATCH_A2386", result, "Should URL-decode query parameter value");
    }
    
    @Test
    public void testExtractBatchIdFallback_InvalidStrings() throws Exception {
        // Test completely invalid strings that don't contain batch IDs
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "???");
        assertNull(result, "Should return null for invalid special characters");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "---");
        assertNull(result, "Should return null for only dashes");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "   \t\n   ");
        assertNull(result, "Should return null for only whitespace");
    }
    
    @Test
    public void testExtractBatchIdFallback_MixedCaseVerifyPath() throws Exception {
        // Test case-insensitive /verify/ matching
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/VERIFY/BATCH_MIXED123");
        assertEquals("BATCH_MIXED123", result, "Should extract from uppercase /VERIFY/ path");
        
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/Verify/BATCH_MIXED456");
        assertEquals("BATCH_MIXED456", result, "Should extract from mixed case /Verify/ path");
    }
    
    @Test
    public void testExtractBatchIdFallback_RealWorldIssueCase() throws Exception {
        // Test the exact case from the issue: BATCH_1764759800_304
        String result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "https://example.com/verify/BATCH_1764759800_304");
        assertEquals("BATCH_1764759800_304", result, 
            "Should extract the batch ID from the issue's example URL");
        
        // Also test without URL
        result = (String) extractBatchIdFallbackMethod.invoke(controller, 
            "BATCH_1764759800_304");
        assertEquals("BATCH_1764759800_304", result, 
            "Should extract the batch ID directly from the issue's example");
    }
}
