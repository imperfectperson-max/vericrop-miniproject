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
 * Integration test demonstrating the full orchestrator workflow.
 */
public class SimulationOrchestratorIntegrationTest {
    
    private SimulationOrchestrator orchestrator;
    private DeliverySimulator simulator;
    private MessageService messageService;
    private AlertService alertService;
    
    @BeforeEach
    public void setUp() {
        messageService = new MessageService(false);
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
    public void testFullOrchestrationWorkflow() throws InterruptedException {
        System.out.println("=== Testing Full Orchestration Workflow ===");
        
        // Create test coordinates (Farm to Warehouse)
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(42.3601, -71.0589, "Farm");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(42.3736, -71.1097, "Warehouse");
        
        // Test with three different scenarios
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.HUMID_ROUTE
        );
        
        System.out.println("\n1. Starting concurrent scenarios...");
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 10, 60.0, "FARMER_INTEGRATION_TEST", scenarios, 2000L);
        
        assertNotNull(batchIds);
        assertEquals(3, batchIds.size());
        System.out.println("✓ Started " + batchIds.size() + " scenarios");
        
        for (Map.Entry<Scenario, String> entry : batchIds.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue());
            assertTrue(entry.getValue().contains("FARMER_INTEGRATION_TEST"));
            assertTrue(entry.getValue().contains(entry.getKey().name()));
        }
        
        // Wait for simulations to start
        Thread.sleep(1000);
        
        System.out.println("\n2. Verifying simulations are running...");
        for (Map.Entry<Scenario, String> entry : batchIds.entrySet()) {
            DeliverySimulator.SimulationStatus status = simulator.getSimulationStatus(entry.getValue());
            assertTrue(status.isRunning(), entry.getKey() + " should be running");
            System.out.println("  - " + entry.getKey() + ": RUNNING " + 
                             "(" + status.getCurrentWaypoint() + "/" + status.getTotalWaypoints() + " waypoints)");
        }
        
        System.out.println("\n3. Getting active orchestrations...");
        List<Map<String, Object>> orchestrations = orchestrator.getActiveOrchestrations();
        assertEquals(1, orchestrations.size());
        System.out.println("✓ Found " + orchestrations.size() + " active orchestration");
        
        Map<String, Object> orch = orchestrations.get(0);
        assertNotNull(orch.get("orchestration_id"));
        assertTrue((Boolean) orch.get("running"));
        assertEquals(3, orch.get("scenario_count"));
        
        System.out.println("  - Orchestration ID: " + orch.get("orchestration_id"));
        System.out.println("  - Running: " + orch.get("running"));
        System.out.println("  - Scenario count: " + orch.get("scenario_count"));
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> scenarioStatuses = 
            (Map<String, Map<String, Object>>) orch.get("scenarios");
        
        System.out.println("\n4. Checking individual scenario statuses...");
        for (String scenarioName : scenarioStatuses.keySet()) {
            Map<String, Object> status = scenarioStatuses.get(scenarioName);
            System.out.println("  - " + scenarioName + ":");
            System.out.println("    * Batch ID: " + status.get("batch_id"));
            System.out.println("    * Running: " + status.get("running"));
            System.out.println("    * Progress: " + status.get("current_waypoint") + 
                             "/" + status.get("total_waypoints"));
            
            assertTrue((Boolean) status.get("running"));
        }
        
        System.out.println("\n5. Stopping all simulations...");
        orchestrator.stopAll();
        System.out.println("✓ Stop command issued");
        
        // Wait for cleanup
        Thread.sleep(500);
        
        System.out.println("\n6. Verifying all simulations stopped...");
        for (Map.Entry<Scenario, String> entry : batchIds.entrySet()) {
            DeliverySimulator.SimulationStatus status = simulator.getSimulationStatus(entry.getValue());
            assertFalse(status.isRunning(), entry.getKey() + " should be stopped");
            System.out.println("  - " + entry.getKey() + ": STOPPED");
        }
        
        orchestrations = orchestrator.getActiveOrchestrations();
        assertEquals(0, orchestrations.size());
        System.out.println("✓ No active orchestrations remaining");
        
        System.out.println("\n=== INTEGRATION TEST PASSED ===");
    }
    
    @Test
    public void testOrchestrationWithAllScenarios() throws InterruptedException {
        System.out.println("=== Testing All Available Scenarios ===");
        
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.7128, -74.0060, "New York");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(34.0522, -118.2437, "Los Angeles");
        
        // Test with all available scenarios
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.COLD_STORAGE,
            Scenario.HUMID_ROUTE,
            Scenario.EXTREME_DELAY
        );
        
        System.out.println("Starting orchestration with " + scenarios.size() + " scenarios...");
        Map<Scenario, String> batchIds = orchestrator.startConcurrentScenarios(
            origin, destination, 8, 65.0, "FARMER_ALL_SCENARIOS", scenarios, 1500L);
        
        assertNotNull(batchIds);
        assertEquals(5, batchIds.size());
        
        // Verify all scenario types are represented
        for (Scenario scenario : scenarios) {
            assertTrue(batchIds.containsKey(scenario), "Missing batch ID for " + scenario);
            System.out.println("  ✓ " + scenario.getDisplayName() + ": " + batchIds.get(scenario));
        }
        
        Thread.sleep(500);
        
        // Verify all are running
        int runningCount = 0;
        for (Map.Entry<Scenario, String> entry : batchIds.entrySet()) {
            if (simulator.getSimulationStatus(entry.getValue()).isRunning()) {
                runningCount++;
            }
        }
        
        assertEquals(5, runningCount, "All 5 scenarios should be running");
        System.out.println("✓ All " + runningCount + " scenarios are running concurrently");
        
        System.out.println("\n=== ALL SCENARIOS TEST PASSED ===");
    }
}
