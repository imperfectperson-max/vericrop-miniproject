package org.vericrop.service.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MessageService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for scenario ID tracking in SimulationManager.
 * Verifies that scenario IDs are correctly passed through the simulation lifecycle.
 */
public class SimulationManagerScenarioTest {
    
    private DeliverySimulator deliverySimulator;
    private MessageService messageService;
    private SimulationManager simulationManager;
    
    @BeforeEach
    public void setUp() {
        // Reset the singleton to ensure clean state for each test
        SimulationManager.resetForTesting();
        
        messageService = new MessageService(false);  // In-memory for tests
        deliverySimulator = new DeliverySimulator(messageService);
        SimulationManager.initialize(deliverySimulator);
        simulationManager = SimulationManager.getInstance();
    }
    
    @AfterEach
    public void tearDown() {
        try {
            if (simulationManager != null && simulationManager.isRunning()) {
                simulationManager.stopSimulation();
                // Give time for cleanup
                Thread.sleep(100);
            }
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
        if (deliverySimulator != null) {
            deliverySimulator.shutdown();
        }
        // Reset to clean up the singleton after each test
        SimulationManager.resetForTesting();
    }
    
    @Test
    public void testScenarioIdTracking() throws InterruptedException {
        final String testBatchId = "TEST_BATCH_" + System.currentTimeMillis();
        final String testFarmerId = "TEST_FARMER";
        final String testScenarioId = "example_1";
        
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<String> capturedScenarioId = new AtomicReference<>();
        
        // Register listener to capture scenario ID
        SimulationListener listener = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId, String scenarioId) {
                if (batchId.equals(testBatchId)) {
                    capturedScenarioId.set(scenarioId);
                    startLatch.countDown();
                }
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {
                // Not needed for this test
            }
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {
                // Not needed for this test
            }
        };
        
        simulationManager.registerListener(listener);
        
        // Start simulation with scenario ID
        DeliverySimulator.GeoCoordinate origin = new DeliverySimulator.GeoCoordinate(0.0, 0.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = new DeliverySimulator.GeoCoordinate(1.0, 1.0, "Destination");
        
        simulationManager.startSimulation(
            testBatchId, 
            testFarmerId, 
            origin, 
            destination, 
            5, 
            60.0, 
            1000L,
            null, // scenario (optional)
            testScenarioId
        );
        
        // Wait for simulation to start
        assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Simulation should start within 5 seconds");
        
        // Verify scenario ID was passed to listener
        assertEquals(testScenarioId, capturedScenarioId.get(), "Scenario ID should be passed to listener");
        
        // Verify scenario ID is accessible via getter
        assertEquals(testScenarioId, simulationManager.getScenarioId(), "Scenario ID should be accessible via getter");
        
        // Stop simulation
        simulationManager.stopSimulation();
    }
    
    @Test
    public void testDefaultScenarioId() throws InterruptedException {
        final String testBatchId = "TEST_BATCH_DEFAULT_" + System.currentTimeMillis();
        final String testFarmerId = "TEST_FARMER";
        
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<String> capturedScenarioId = new AtomicReference<>();
        
        // Register listener to capture scenario ID
        SimulationListener listener = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId, String scenarioId) {
                if (batchId.equals(testBatchId)) {
                    capturedScenarioId.set(scenarioId);
                    startLatch.countDown();
                }
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {
                // Not needed for this test
            }
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {
                // Not needed for this test
            }
        };
        
        simulationManager.registerListener(listener);
        
        // Start simulation without scenario ID (should default to "NORMAL")
        DeliverySimulator.GeoCoordinate origin = new DeliverySimulator.GeoCoordinate(0.0, 0.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = new DeliverySimulator.GeoCoordinate(1.0, 1.0, "Destination");
        
        simulationManager.startSimulation(
            testBatchId, 
            testFarmerId, 
            origin, 
            destination, 
            5, 
            60.0, 
            1000L
        );
        
        // Wait for simulation to start
        assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Simulation should start within 5 seconds");
        
        // Verify default scenario ID is "NORMAL"
        assertEquals("NORMAL", capturedScenarioId.get(), "Default scenario ID should be NORMAL");
        assertEquals("NORMAL", simulationManager.getScenarioId(), "Default scenario ID should be NORMAL via getter");
        
        // Stop simulation
        simulationManager.stopSimulation();
    }
}
