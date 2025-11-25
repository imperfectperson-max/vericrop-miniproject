package org.vericrop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.ScenarioLoader.ScenarioDefinition;
import org.vericrop.service.ScenarioLoader.TemperatureRange;
import org.vericrop.service.models.Scenario;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScenarioLoader - verifies JSON deserialization with unknown fields.
 * 
 * Tests the fix for Jackson warnings when scenario JSON files contain unknown fields
 * like "spikes" array or "duration_minutes" in snake_case.
 */
class ScenarioLoaderTest {
    
    private ScenarioLoader scenarioLoader;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        scenarioLoader = new ScenarioLoader();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testScenarioDefinitionIgnoresUnknownFields() throws Exception {
        // JSON with unknown fields (spikes array, snake_case duration_minutes)
        String json = """
            {
              "id": "test-scenario-01",
              "description": "Test scenario with unknown fields",
              "target": {
                "min": 2.0,
                "max": 5.0
              },
              "duration_minutes": 30,
              "spikes": [
                {
                  "at_minute": 12,
                  "duration_minutes": 2,
                  "temperature": 10.0
                }
              ],
              "unknown_field": "should be ignored"
            }
            """;
        
        // This should NOT throw an exception due to @JsonIgnoreProperties(ignoreUnknown = true)
        ScenarioDefinition def = objectMapper.readValue(json, ScenarioDefinition.class);
        
        assertNotNull(def, "ScenarioDefinition should be parsed successfully");
        assertEquals("test-scenario-01", def.getId());
        assertEquals("Test scenario with unknown fields", def.getDescription());
        assertEquals(30, def.getDurationMinutes(), "duration_minutes should be mapped correctly");
        assertNotNull(def.getTarget(), "Target temperature range should be parsed");
        assertEquals(2.0, def.getTarget().getMin(), 0.001);
        assertEquals(5.0, def.getTarget().getMax(), 0.001);
    }
    
    @Test
    void testTemperatureRangeIgnoresUnknownFields() throws Exception {
        // JSON with unknown fields
        String json = """
            {
              "min": 1.5,
              "max": 4.5,
              "optimal": 3.0,
              "unknown_field": "should be ignored"
            }
            """;
        
        // This should NOT throw an exception due to @JsonIgnoreProperties(ignoreUnknown = true)
        TemperatureRange range = objectMapper.readValue(json, TemperatureRange.class);
        
        assertNotNull(range, "TemperatureRange should be parsed successfully");
        assertEquals(1.5, range.getMin(), 0.001);
        assertEquals(4.5, range.getMax(), 0.001);
    }
    
    @Test
    void testScenarioLoaderInitialization() {
        // ScenarioLoader should initialize without errors even if scenario files have unknown fields
        assertNotNull(scenarioLoader, "ScenarioLoader should be initialized");
        
        // Get all scenarios - should not throw
        Map<String, ScenarioDefinition> scenarios = scenarioLoader.getAllScenarios();
        assertNotNull(scenarios, "Scenarios map should not be null");
    }
    
    @Test
    void testMapToScenarioEnumWithNull() {
        // Should return NORMAL for null input
        Scenario result = scenarioLoader.mapToScenarioEnum(null);
        assertEquals(Scenario.NORMAL, result, "Should return NORMAL for null scenario ID");
    }
    
    @Test
    void testMapToScenarioEnumWithEmptyString() {
        // Should return NORMAL for empty string
        Scenario result = scenarioLoader.mapToScenarioEnum("");
        assertEquals(Scenario.NORMAL, result, "Should return NORMAL for empty scenario ID");
    }
    
    @Test
    void testMapToScenarioEnumWithUnknownId() {
        // Should return NORMAL for unknown scenario ID
        Scenario result = scenarioLoader.mapToScenarioEnum("unknown-scenario-xyz");
        assertEquals(Scenario.NORMAL, result, "Should return NORMAL for unknown scenario ID");
    }
    
    @Test
    void testGetScenarioDefinitionReturnsNullForUnknown() {
        // Should return null for unknown scenario ID
        ScenarioDefinition def = scenarioLoader.getScenarioDefinition("non-existent-scenario");
        assertNull(def, "Should return null for non-existent scenario");
    }
    
    @Test
    void testScenarioDefinitionDurationMinutesMapping() throws Exception {
        // Verify that duration_minutes (snake_case) is mapped to durationMinutes (camelCase)
        String json = """
            {
              "id": "test-duration",
              "description": "Test duration mapping",
              "target": {
                "min": 0.0,
                "max": 10.0
              },
              "duration_minutes": 45
            }
            """;
        
        ScenarioDefinition def = objectMapper.readValue(json, ScenarioDefinition.class);
        
        assertEquals(45, def.getDurationMinutes(), "duration_minutes should be mapped to durationMinutes");
    }
}
