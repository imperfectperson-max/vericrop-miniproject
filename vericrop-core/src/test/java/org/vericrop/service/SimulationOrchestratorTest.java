package org.vericrop.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.Scenario;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationOrchestrator.
 */
public class SimulationOrchestratorTest {
    
    private SimulationOrchestrator orchestrator;
    private DeliverySimulator simulator;
    private MessageService messageService;
    private AlertService alertService;
    
    @BeforeEach
    public void setUp() {
        messageService = new MessageService(false);  // In-memory for tests
        alertService = new AlertService();
        simulator = new DeliverySimulator(messageService, alertService);
        orchestrator = new SimulationOrchestrator(simulator, alertService, messageService);
    }
    
    @AfterEach
    public void tearDown() {
        orchestrator.shutdown();
        simulator.shutdown();
    }
    
    @Test
    public void testStartConcurrentScenarios_SingleScenario() {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL);
        
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 5, 60.0, "FARMER_TEST", scenarios, 1000L);
        
        assertNotNull(batchIds);
        assertEquals(1, batchIds.size());
        assertTrue(batchIds.containsKey(Scenario.NORMAL));
        
        String batchId = batchIds.get(Scenario.NORMAL);
        assertNotNull(batchId);
        assertTrue(batchId.startsWith("BATCH_FARMER_TEST_NORMAL_"));
    }
    
    @Test
    public void testStartConcurrentScenarios_MultipleScenarios() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.HUMID_ROUTE
        );
        
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 5, 60.0, "FARMER_A", scenarios, 1000L);
        
        assertNotNull(batchIds);
        assertEquals(3, batchIds.size());
        
        // Verify all scenarios have batch IDs
        assertTrue(batchIds.containsKey(Scenario.NORMAL));
        assertTrue(batchIds.containsKey(Scenario.HOT_TRANSPORT));
        assertTrue(batchIds.containsKey(Scenario.HUMID_ROUTE));
        
        // Verify batch IDs are unique and properly formatted
        String normalBatchId = batchIds.get(Scenario.NORMAL);
        String hotBatchId = batchIds.get(Scenario.HOT_TRANSPORT);
        String humidBatchId = batchIds.get(Scenario.HUMID_ROUTE);
        
        assertNotEquals(normalBatchId, hotBatchId);
        assertNotEquals(normalBatchId, humidBatchId);
        assertNotEquals(hotBatchId, humidBatchId);
        
        assertTrue(normalBatchId.contains("NORMAL"));
        assertTrue(hotBatchId.contains("HOT_TRANSPORT"));
        assertTrue(humidBatchId.contains("HUMID_ROUTE"));
        
        // Wait a bit for simulations to start
        Thread.sleep(500);
        
        // Verify simulations are running
        DeliverySimulator.SimulationStatus normalStatus = simulator.getSimulationStatus(normalBatchId);
        DeliverySimulator.SimulationStatus hotStatus = simulator.getSimulationStatus(hotBatchId);
        DeliverySimulator.SimulationStatus humidStatus = simulator.getSimulationStatus(humidBatchId);
        
        assertTrue(normalStatus.isRunning());
        assertTrue(hotStatus.isRunning());
        assertTrue(humidStatus.isRunning());
    }
    
    @Test
    public void testStartConcurrentScenarios_EmptyScenarioList() {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        List<Scenario> scenarios = Arrays.asList();
        
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.startConcurrentScenarios(
                origin, destination, 5, 60.0, "FARMER_TEST", scenarios, 1000L);
        });
    }
    
    @Test
    public void testStartConcurrentScenarios_NullScenarioList() {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.startConcurrentScenarios(
                origin, destination, 5, 60.0, "FARMER_TEST", null, 1000L);
        });
    }
    
    @Test
    public void testGetActiveOrchestrations() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL, Scenario.HOT_TRANSPORT);
        
        orchestrator.startConcurrentScenarios(
            origin, destination, 5, 60.0, "FARMER_A", scenarios, 1000L);
        
        // Wait for orchestrations to start
        Thread.sleep(500);
        
        List<Map<String, Object>> orchestrations = orchestrator.getActiveOrchestrations();
        
        assertNotNull(orchestrations);
        assertEquals(1, orchestrations.size());
        
        Map<String, Object> orch = orchestrations.get(0);
        assertTrue(orch.containsKey("orchestration_id"));
        assertTrue(orch.containsKey("start_time"));
        assertTrue(orch.containsKey("running"));
        assertTrue(orch.containsKey("scenario_count"));
        assertTrue(orch.containsKey("scenarios"));
        
        assertEquals(2, orch.get("scenario_count"));
        assertTrue((Boolean) orch.get("running"));
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> scenarioStatuses = 
            (Map<String, Map<String, Object>>) orch.get("scenarios");
        
        assertTrue(scenarioStatuses.containsKey("NORMAL"));
        assertTrue(scenarioStatuses.containsKey("HOT_TRANSPORT"));
    }
    
    @Test
    public void testStopAll() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL, Scenario.HOT_TRANSPORT);
        
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 5, 60.0, "FARMER_A", scenarios, 1000L);
        
        // Wait for simulations to start
        Thread.sleep(500);
        
        // Verify simulations are running
        assertTrue(simulator.getSimulationStatus(batchIds.get(Scenario.NORMAL)).isRunning());
        assertTrue(simulator.getSimulationStatus(batchIds.get(Scenario.HOT_TRANSPORT)).isRunning());
        
        // Stop all
        orchestrator.stopAll();
        
        // Wait a bit for cleanup
        Thread.sleep(200);
        
        // Verify simulations are stopped
        assertFalse(simulator.getSimulationStatus(batchIds.get(Scenario.NORMAL)).isRunning());
        assertFalse(simulator.getSimulationStatus(batchIds.get(Scenario.HOT_TRANSPORT)).isRunning());
        
        // Verify no active orchestrations
        List<Map<String, Object>> orchestrations = orchestrator.getActiveOrchestrations();
        assertEquals(0, orchestrations.size());
    }
    
    @Test
    public void testAllScenarios() {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(41.0, -73.0, "Destination");
        
        // Test all available scenarios
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.COLD_STORAGE,
            Scenario.HUMID_ROUTE,
            Scenario.EXTREME_DELAY
        );
        
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 5, 60.0, "FARMER_ALL", scenarios, 1000L);
        
        assertNotNull(batchIds);
        assertEquals(5, batchIds.size());
        
        // Verify all scenarios are present
        for (Scenario scenario : scenarios) {
            assertTrue(batchIds.containsKey(scenario), 
                      "Missing batch ID for scenario: " + scenario.name());
            assertTrue(batchIds.get(scenario).contains(scenario.name()),
                      "Batch ID doesn't contain scenario name: " + scenario.name());
        }
    }
}
