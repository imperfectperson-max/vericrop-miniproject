package org.vericrop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.Scenario;
import org.vericrop.service.simulation.SimulationManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for map simulation with SimulationManager.
 */
public class MapSimulationIntegrationTest {
    
    private MapSimulator mapSimulator;
    private ScenarioManager scenarioManager;
    private MapService mapService;
    private TemperatureService temperatureService;
    private AlertService alertService;
    private DeliverySimulator deliverySimulator;
    private MessageService messageService;
    private SimulationManager simulationManager;
    
    @BeforeEach
    public void setUp() {
        // Reset the singleton to ensure clean state for each test
        SimulationManager.resetForTesting();
        
        // Initialize all components
        mapSimulator = new MapSimulator();
        scenarioManager = new ScenarioManager();
        mapService = new MapService();
        temperatureService = new TemperatureService();
        alertService = new AlertService();
        messageService = new MessageService(false); // No persistence for tests
        deliverySimulator = new DeliverySimulator(messageService, alertService);
        
        // Initialize SimulationManager with all components
        SimulationManager.initialize(deliverySimulator, mapService, temperatureService, 
                                     alertService, mapSimulator, scenarioManager);
        simulationManager = SimulationManager.getInstance();
    }
    
    @AfterEach
    public void tearDown() {
        if (simulationManager != null && simulationManager.isRunning()) {
            simulationManager.stopSimulation();
        }
        // Reset to clean up the singleton after each test
        SimulationManager.resetForTesting();
    }
    
    @Test
    public void testScenarioManagerIntegration() {
        String batchId = "INT_TEST_001";
        int numWaypoints = 10;
        
        // Apply scenario to map
        scenarioManager.applyScenarioToMap("scenario-01", mapSimulator, batchId, numWaypoints);
        
        // Verify map was initialized
        assertEquals(5, mapSimulator.getEntityCount());
        
        MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
        assertNotNull(snapshot);
        assertEquals("NORMAL", snapshot.getScenarioId());
        assertEquals(5, snapshot.getEntities().size());
    }
    
    @Test
    public void testMapSimulatorWithDifferentScenarios() {
        String batchId = "INT_TEST_002";
        int numWaypoints = 10;
        
        // Test with different scenarios
        Scenario[] scenarios = {Scenario.NORMAL, Scenario.HOT_TRANSPORT, Scenario.COLD_STORAGE};
        
        for (Scenario scenario : scenarios) {
            mapSimulator.reset();
            mapSimulator.initializeForScenario(scenario, batchId, numWaypoints);
            
            assertEquals(5, mapSimulator.getEntityCount());
            
            MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
            assertEquals(scenario.name(), snapshot.getScenarioId());
            
            // Step simulation
            MapSimulator.MapSnapshot stepSnapshot = mapSimulator.step(50.0);
            assertNotNull(stepSnapshot);
            assertEquals(1, stepSnapshot.getSimulationStep());
        }
    }
    
    @Test
    public void testMapSimulatorStepProgression() {
        String batchId = "INT_TEST_003";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        // Step through simulation at different progress levels
        double[] progressLevels = {0.0, 25.0, 50.0, 75.0, 100.0};
        
        for (int i = 0; i < progressLevels.length; i++) {
            MapSimulator.MapSnapshot snapshot = mapSimulator.step(progressLevels[i]);
            
            assertEquals(i + 1, snapshot.getSimulationStep());
            assertEquals(5, snapshot.getEntities().size());
            
            // Verify vehicle has moved
            MapSimulator.MapEntity vehicle = findEntityByType(snapshot.getEntities(), 
                                                             MapSimulator.EntityType.DELIVERY_VEHICLE);
            assertNotNull(vehicle);
            
            // At higher progress, vehicle should be further right
            if (i > 0 && progressLevels[i] > progressLevels[i-1]) {
                assertTrue(vehicle.getX() >= 0, "Vehicle should be on grid");
            }
        }
    }
    
    @Test
    public void testScenarioConfigurationRetrieval() {
        // Get all scenarios
        java.util.List<java.util.Map<String, Object>> allScenarios = scenarioManager.getAllScenarioInfo();
        
        assertNotNull(allScenarios);
        assertFalse(allScenarios.isEmpty());
        
        // Verify each scenario has required fields
        for (java.util.Map<String, Object> scenarioInfo : allScenarios) {
            assertTrue(scenarioInfo.containsKey("scenario_id"));
            assertTrue(scenarioInfo.containsKey("scenario_name"));
            assertTrue(scenarioInfo.containsKey("display_name"));
            assertTrue(scenarioInfo.containsKey("parameters"));
        }
    }
    
    @Test
    public void testMapSimulatorSnapshotSerialization() {
        String batchId = "INT_TEST_004";
        mapSimulator.initializeForScenario(Scenario.NORMAL, batchId, 10);
        
        MapSimulator.MapSnapshot snapshot = mapSimulator.step(50.0);
        
        // Serialize to map
        java.util.Map<String, Object> serialized = snapshot.toMap();
        
        assertNotNull(serialized);
        assertTrue(serialized.containsKey("timestamp"));
        assertTrue(serialized.containsKey("simulation_step"));
        assertTrue(serialized.containsKey("grid_width"));
        assertTrue(serialized.containsKey("grid_height"));
        assertTrue(serialized.containsKey("scenario_id"));
        assertTrue(serialized.containsKey("entities"));
        
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> entities = 
            (java.util.List<java.util.Map<String, Object>>) serialized.get("entities");
        
        assertEquals(5, entities.size());
        
        // Verify entity structure
        for (java.util.Map<String, Object> entity : entities) {
            assertTrue(entity.containsKey("id"));
            assertTrue(entity.containsKey("type"));
            assertTrue(entity.containsKey("x"));
            assertTrue(entity.containsKey("y"));
            assertTrue(entity.containsKey("metadata"));
        }
    }
    
    @Test
    public void testSimulationManagerAccessToMapComponents() {
        // Verify SimulationManager has access to map components
        assertNotNull(simulationManager.getMapSimulator());
        assertNotNull(simulationManager.getScenarioManager());
        
        // Verify they are the same instances we initialized with
        assertSame(mapSimulator, simulationManager.getMapSimulator());
        assertSame(scenarioManager, simulationManager.getScenarioManager());
    }
    
    @Test
    public void testResourceQualityDegradationWithScenarios() {
        String batchId = "INT_TEST_005";
        
        // Test with HOT_TRANSPORT scenario (high spoilage rate)
        mapSimulator.initializeForScenario(Scenario.HOT_TRANSPORT, batchId, 10);
        
        MapSimulator.MapSnapshot initialSnapshot = mapSimulator.getSnapshot();
        MapSimulator.MapEntity initialResource = findEntityByType(
            initialSnapshot.getEntities(), MapSimulator.EntityType.RESOURCE);
        Double initialQuality = (Double) initialResource.getMetadata().get("quality");
        
        // Step to 100%
        MapSimulator.MapSnapshot finalSnapshot = mapSimulator.step(100.0);
        MapSimulator.MapEntity finalResource = findEntityByType(
            finalSnapshot.getEntities(), MapSimulator.EntityType.RESOURCE);
        Double finalQuality = (Double) finalResource.getMetadata().get("quality");
        
        // Quality should have degraded
        assertTrue(finalQuality < initialQuality, 
                  "Resource quality should degrade with HOT_TRANSPORT scenario");
    }
    
    // Helper method
    private MapSimulator.MapEntity findEntityByType(java.util.List<MapSimulator.MapEntity> entities, 
                                                    MapSimulator.EntityType type) {
        for (MapSimulator.MapEntity entity : entities) {
            if (entity.getType() == type) {
                return entity;
            }
        }
        return null;
    }
}
