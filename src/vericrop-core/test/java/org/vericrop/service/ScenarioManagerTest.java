package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.ScenarioManager.ScenarioConfig;
import org.vericrop.service.models.Scenario;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScenarioManager.
 */
public class ScenarioManagerTest {
    
    private ScenarioManager scenarioManager;
    
    @BeforeEach
    public void setUp() {
        scenarioManager = new ScenarioManager();
    }
    
    @Test
    public void testInitialization() {
        assertNotNull(scenarioManager);
        assertNotNull(scenarioManager.getAvailableScenarios());
        assertFalse(scenarioManager.getAvailableScenarios().isEmpty());
    }
    
    @Test
    public void testGetScenarioById() {
        // Test JSON scenario IDs
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("scenario-01"));
        assertEquals(Scenario.COLD_STORAGE, scenarioManager.getScenario("scenario-02"));
        assertEquals(Scenario.HOT_TRANSPORT, scenarioManager.getScenario("scenario-03"));
        
        // Test backward compatibility
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("example-01"));
        assertEquals(Scenario.COLD_STORAGE, scenarioManager.getScenario("example-02"));
        assertEquals(Scenario.HOT_TRANSPORT, scenarioManager.getScenario("example-03"));
    }
    
    @Test
    public void testGetScenarioByEnumName() {
        // Test direct enum names
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("NORMAL"));
        assertEquals(Scenario.HOT_TRANSPORT, scenarioManager.getScenario("HOT_TRANSPORT"));
        assertEquals(Scenario.COLD_STORAGE, scenarioManager.getScenario("COLD_STORAGE"));
        assertEquals(Scenario.HUMID_ROUTE, scenarioManager.getScenario("HUMID_ROUTE"));
        assertEquals(Scenario.EXTREME_DELAY, scenarioManager.getScenario("EXTREME_DELAY"));
    }
    
    @Test
    public void testGetScenarioDefaultsToNormal() {
        // Unknown scenarios should default to NORMAL
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("UNKNOWN_SCENARIO"));
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario(null));
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario(""));
    }
    
    @Test
    public void testGetScenarioConfig() {
        ScenarioConfig config = scenarioManager.getScenarioConfig("scenario-01");
        
        assertNotNull(config);
        assertEquals(Scenario.NORMAL, config.getScenario());
        assertEquals("scenario-01", config.getScenarioId());
        assertNotNull(config.getDescription());
        assertFalse(config.getParameters().isEmpty());
        assertFalse(config.getInitialMapSetup().isEmpty());
    }
    
    @Test
    public void testScenarioConfigParameters() {
        ScenarioConfig config = scenarioManager.getScenarioConfig("scenario-03");
        
        Map<String, Object> params = config.getParameters();
        
        // Verify scenario-specific parameters
        assertTrue(params.containsKey("temperature_drift"));
        assertTrue(params.containsKey("humidity_drift"));
        assertTrue(params.containsKey("speed_multiplier"));
        assertTrue(params.containsKey("spoilage_rate"));
        
        // HOT_TRANSPORT should have positive temperature drift
        double tempDrift = (Double) params.get("temperature_drift");
        assertTrue(tempDrift > 0, "HOT_TRANSPORT should have positive temperature drift");
    }
    
    @Test
    public void testScenarioConfigInitialMapSetup() {
        ScenarioConfig normalConfig = scenarioManager.getScenarioConfig("scenario-01");
        ScenarioConfig hotConfig = scenarioManager.getScenarioConfig("scenario-03");
        ScenarioConfig coldConfig = scenarioManager.getScenarioConfig("scenario-02");
        
        // All should have basic setup
        assertTrue(normalConfig.getInitialMapSetup().containsKey("producer_count"));
        assertTrue(normalConfig.getInitialMapSetup().containsKey("consumer_count"));
        assertTrue(normalConfig.getInitialMapSetup().containsKey("warehouse_count"));
        
        // HOT_TRANSPORT should have critical temperature monitoring
        assertEquals("critical", hotConfig.getInitialMapSetup().get("temperature_monitoring"));
        
        // COLD_STORAGE should have strict temperature monitoring
        assertEquals("strict", coldConfig.getInitialMapSetup().get("temperature_monitoring"));
        assertTrue((Boolean) coldConfig.getInitialMapSetup().get("cold_chain_required"));
    }
    
    @Test
    public void testGetAvailableScenarios() {
        List<String> scenarios = scenarioManager.getAvailableScenarios();
        
        assertNotNull(scenarios);
        assertFalse(scenarios.isEmpty());
        
        // Check for key scenarios
        assertTrue(scenarios.contains("scenario-01") || scenarios.contains("SCENARIO-01"));
    }
    
    @Test
    public void testGetAvailableScenarioEnums() {
        List<Scenario> scenarios = scenarioManager.getAvailableScenarioEnums();
        
        assertNotNull(scenarios);
        assertEquals(5, scenarios.size()); // NORMAL, HOT_TRANSPORT, COLD_STORAGE, HUMID_ROUTE, EXTREME_DELAY
        
        assertTrue(scenarios.contains(Scenario.NORMAL));
        assertTrue(scenarios.contains(Scenario.HOT_TRANSPORT));
        assertTrue(scenarios.contains(Scenario.COLD_STORAGE));
        assertTrue(scenarios.contains(Scenario.HUMID_ROUTE));
        assertTrue(scenarios.contains(Scenario.EXTREME_DELAY));
    }
    
    @Test
    public void testApplyScenarioToMap() {
        MapSimulator mapSimulator = new MapSimulator();
        String batchId = "TEST_BATCH_001";
        int numWaypoints = 10;
        
        scenarioManager.applyScenarioToMap("scenario-01", mapSimulator, batchId, numWaypoints);
        
        // Verify map was initialized
        assertEquals(5, mapSimulator.getEntityCount());
        
        MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
        assertEquals("NORMAL", snapshot.getScenarioId());
    }
    
    @Test
    public void testGetDefaultScenarioId() {
        String defaultId = scenarioManager.getDefaultScenarioId();
        
        assertNotNull(defaultId);
        assertEquals("scenario-01", defaultId);
    }
    
    @Test
    public void testIsValidScenario() {
        // Valid scenarios
        assertTrue(scenarioManager.isValidScenario("scenario-01"));
        assertTrue(scenarioManager.isValidScenario("NORMAL"));
        assertTrue(scenarioManager.isValidScenario("HOT_TRANSPORT"));
        
        // Invalid scenarios
        assertFalse(scenarioManager.isValidScenario("INVALID_SCENARIO"));
        assertFalse(scenarioManager.isValidScenario(null));
        assertFalse(scenarioManager.isValidScenario(""));
    }
    
    @Test
    public void testGetScenarioInfo() {
        Map<String, Object> info = scenarioManager.getScenarioInfo("scenario-01");
        
        assertNotNull(info);
        assertTrue(info.containsKey("scenario_id"));
        assertTrue(info.containsKey("scenario_name"));
        assertTrue(info.containsKey("display_name"));
        assertTrue(info.containsKey("description"));
        assertTrue(info.containsKey("parameters"));
        assertTrue(info.containsKey("initial_map_setup"));
        
        assertEquals("scenario-01", info.get("scenario_id"));
        assertEquals("NORMAL", info.get("scenario_name"));
    }
    
    @Test
    public void testGetAllScenarioInfo() {
        List<Map<String, Object>> allInfo = scenarioManager.getAllScenarioInfo();
        
        assertNotNull(allInfo);
        assertFalse(allInfo.isEmpty());
        
        // Should contain scenario-01, scenario-02, scenario-03, example-01, example-02, example-03
        assertTrue(allInfo.size() >= 3);
    }
    
    @Test
    public void testScenarioConfigCaching() {
        ScenarioConfig config1 = scenarioManager.getScenarioConfig("scenario-01");
        ScenarioConfig config2 = scenarioManager.getScenarioConfig("scenario-01");
        
        // Should return same cached instance
        assertSame(config1, config2);
    }
    
    @Test
    public void testScenarioConfigCustomParameters() {
        ScenarioConfig config = scenarioManager.getScenarioConfig("scenario-01");
        
        // Add custom parameter
        config.setParameter("custom_param", "custom_value");
        
        assertEquals("custom_value", config.getParameters().get("custom_param"));
    }
    
    @Test
    public void testScenarioConfigCustomMapSetup() {
        ScenarioConfig config = scenarioManager.getScenarioConfig("scenario-01");
        
        // Add custom map setup
        config.setMapSetup("custom_setup", 123);
        
        assertEquals(123, config.getInitialMapSetup().get("custom_setup"));
    }
    
    @Test
    public void testAllScenariosHaveValidConfig() {
        List<Scenario> allScenarios = scenarioManager.getAvailableScenarioEnums();
        
        for (Scenario scenario : allScenarios) {
            ScenarioConfig config = scenarioManager.getScenarioConfig(scenario.name());
            
            assertNotNull(config);
            assertNotNull(config.getScenario());
            assertNotNull(config.getParameters());
            assertNotNull(config.getInitialMapSetup());
            
            // Verify all scenarios have required parameters
            assertTrue(config.getParameters().containsKey("temperature_drift"));
            assertTrue(config.getParameters().containsKey("speed_multiplier"));
            assertTrue(config.getParameters().containsKey("spoilage_rate"));
        }
    }
    
    @Test
    public void testCaseInsensitiveScenarioLookup() {
        // Test case insensitive lookup
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("normal"));
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("Normal"));
        assertEquals(Scenario.NORMAL, scenarioManager.getScenario("NORMAL"));
        
        assertEquals(Scenario.HOT_TRANSPORT, scenarioManager.getScenario("hot_transport"));
        assertEquals(Scenario.HOT_TRANSPORT, scenarioManager.getScenario("Hot_Transport"));
    }
}
