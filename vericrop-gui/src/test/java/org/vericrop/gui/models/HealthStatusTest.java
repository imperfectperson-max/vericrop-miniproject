package org.vericrop.gui.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthStatus - verifies JSON deserialization with unknown fields.
 * 
 * Tests the fix for Jackson warnings when ML service health endpoint returns
 * unknown fields like "mode" that are not mapped in the HealthStatus class.
 */
class HealthStatusTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testHealthStatusIgnoresUnknownFields() throws Exception {
        // JSON with known and unknown fields (simulates ML service response)
        String json = """
            {
              "status": "ok",
              "time": 1700000000,
              "model_loaded": true,
              "model_accuracy": "95.5%",
              "classes_loaded": 3,
              "mode": "inference",
              "version": "1.2.3",
              "unknown_field": "should be ignored"
            }
            """;
        
        // This should NOT throw an exception due to @JsonIgnoreProperties(ignoreUnknown = true)
        HealthStatus status = objectMapper.readValue(json, HealthStatus.class);
        
        assertNotNull(status, "HealthStatus should be parsed successfully");
        assertEquals("ok", status.getStatus());
        assertEquals(1700000000L, status.getTime());
        assertTrue(status.getModelLoaded());
        assertEquals("95.5%", status.getModelAccuracy());
        assertEquals(3, status.getClassesLoaded());
        assertTrue(status.isHealthy(), "Status 'ok' should indicate healthy service");
    }
    
    @Test
    void testHealthStatusWithModeField() throws Exception {
        // Specifically test the "mode" field that was causing warnings
        String json = """
            {
              "status": "ok",
              "mode": "production"
            }
            """;
        
        // This should NOT throw an exception
        HealthStatus status = objectMapper.readValue(json, HealthStatus.class);
        
        assertNotNull(status, "HealthStatus should be parsed even with mode field");
        assertEquals("ok", status.getStatus());
    }
    
    @Test
    void testHealthStatusMinimalFields() throws Exception {
        // Test with only required fields
        String json = """
            {
              "status": "ok"
            }
            """;
        
        HealthStatus status = objectMapper.readValue(json, HealthStatus.class);
        
        assertNotNull(status);
        assertEquals("ok", status.getStatus());
        assertNull(status.getTime());
        assertNull(status.getModelLoaded());
        assertNull(status.getModelAccuracy());
        assertNull(status.getClassesLoaded());
        assertTrue(status.isHealthy());
    }
    
    @Test
    void testHealthStatusUnhealthyStatus() throws Exception {
        String json = """
            {
              "status": "error",
              "model_loaded": false
            }
            """;
        
        HealthStatus status = objectMapper.readValue(json, HealthStatus.class);
        
        assertNotNull(status);
        assertEquals("error", status.getStatus());
        assertFalse(status.isHealthy(), "Status 'error' should indicate unhealthy service");
    }
    
    @Test
    void testHealthStatusNullStatus() throws Exception {
        String json = """
            {
              "model_loaded": true
            }
            """;
        
        HealthStatus status = objectMapper.readValue(json, HealthStatus.class);
        
        assertNotNull(status);
        assertNull(status.getStatus());
        assertFalse(status.isHealthy(), "Null status should indicate unhealthy service");
    }
    
    @Test
    void testHealthStatusToString() {
        HealthStatus status = new HealthStatus();
        status.setStatus("ok");
        status.setModelLoaded(true);
        status.setModelAccuracy("92%");
        status.setClassesLoaded(5);
        
        String result = status.toString();
        
        assertTrue(result.contains("status='ok'"));
        assertTrue(result.contains("modelLoaded=true"));
        assertTrue(result.contains("modelAccuracy='92%'"));
        assertTrue(result.contains("classesLoaded=5"));
    }
    
    @Test
    void testHealthStatusConstructorWithStatus() {
        HealthStatus status = new HealthStatus("healthy");
        
        assertEquals("healthy", status.getStatus());
    }
}
