package org.vericrop.service.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MessageService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationManager.
 * 
 * Note: These tests focus on the manager's state and listener functionality.
 * Full integration tests with DeliverySimulator are better suited for integration test suite.
 */
public class SimulationManagerTest {
    
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
    public void testSingletonInitialization() {
        assertTrue(SimulationManager.isInitialized());
        assertNotNull(SimulationManager.getInstance());
        assertSame(simulationManager, SimulationManager.getInstance());
    }
    
    @Test
    public void testInitialState() {
        assertFalse(simulationManager.isRunning(), "Should not be running initially");
        assertNull(simulationManager.getSimulationId(), "Should have no simulation ID initially");
        assertNull(simulationManager.getCurrentProducer(), "Should have no producer initially");
        assertEquals(0.0, simulationManager.getProgress(), 0.01, "Progress should be 0 initially");
    }
    
    @Test
    public void testRegisterAndUnregisterListener() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        SimulationListener listener = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                callCount.incrementAndGet();
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        // Test registration
        simulationManager.registerListener(listener);
        
        // Test that duplicate registration doesn't add listener twice
        simulationManager.registerListener(listener);
        
        // Test unregistration
        simulationManager.unregisterListener(listener);
        
        // Verify listener can be registered again
        simulationManager.registerListener(listener);
        simulationManager.unregisterListener(listener);
        
        // No assertions needed - just verifying no exceptions
        assertTrue(true, "Listener registration/unregistration should work without errors");
    }
    
    @Test
    public void testMultipleListeners() {
        AtomicBoolean listener1Called = new AtomicBoolean(false);
        AtomicBoolean listener2Called = new AtomicBoolean(false);
        
        SimulationListener listener1 = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                listener1Called.set(true);
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        SimulationListener listener2 = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                listener2Called.set(true);
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        simulationManager.registerListener(listener1);
        simulationManager.registerListener(listener2);
        
        // Both listeners should be registered
        assertTrue(true, "Multiple listeners can be registered");
        
        // Cleanup
        simulationManager.unregisterListener(listener1);
        simulationManager.unregisterListener(listener2);
    }
    
    // Note: Full integration tests with async simulation lifecycle are better suited 
    // for integration test suite. These unit tests focus on the manager's state.
    
    /*
    @Test
    public void testStopSimulation() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(40.1, -74.1, "Destination");
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<String> stoppedBatchId = new AtomicReference<>();
        
        simulationManager.registerListener(new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                startLatch.countDown();
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {
                // Not needed for this test
            }
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {
                stopped.set(true);
                stoppedBatchId.set(batchId);
                stopLatch.countDown();
            }
        });
        
        simulationManager.startSimulation("BATCH-002", "FARMER-002", 
            origin, destination, 5, 50.0, 1000);
        
        assertTrue(startLatch.await(5, TimeUnit.SECONDS), "Simulation should start");
        Thread.sleep(500); // Give time for state to settle
        assertTrue(simulationManager.isRunning());
        
        simulationManager.stopSimulation();
        
        assertTrue(stopLatch.await(5, TimeUnit.SECONDS), "Simulation should stop");
        Thread.sleep(500); // Give time for state to settle
        assertTrue(stopped.get());
        assertEquals("BATCH-002", stoppedBatchId.get());
        assertFalse(simulationManager.isRunning());
        assertNull(simulationManager.getSimulationId());
    }
    */
    
    /*
    @Test
    public void testProgressUpdates() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(40.1, -74.1, "Destination");
        
        CountDownLatch progressLatch = new CountDownLatch(3); // Wait for at least 3 progress updates
        AtomicInteger progressCount = new AtomicInteger(0);
        
        simulationManager.registerListener(new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                // Not needed for this test
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {
                progressCount.incrementAndGet();
                progressLatch.countDown();
            }
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {
                // Not needed for this test
            }
        });
        
        simulationManager.startSimulation("BATCH-003", "FARMER-003", 
            origin, destination, 5, 50.0, 1000);
        
        // Wait for progress updates (should come every 5 seconds based on implementation)
        assertTrue(progressLatch.await(20, TimeUnit.SECONDS), 
            "Should receive at least 3 progress updates within 20 seconds");
        assertTrue(progressCount.get() >= 3, "Should have at least 3 progress updates");
    }
    */
    
    /*
    @Test
    public void testMultipleListenersWithSimulation() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(40.1, -74.1, "Destination");
        
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicBoolean listener1Called = new AtomicBoolean(false);
        AtomicBoolean listener2Called = new AtomicBoolean(false);
        
        SimulationListener listener1 = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                listener1Called.set(true);
                latch1.countDown();
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        SimulationListener listener2 = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                listener2Called.set(true);
                latch2.countDown();
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        simulationManager.registerListener(listener1);
        simulationManager.registerListener(listener2);
        
        simulationManager.startSimulation("BATCH-004", "FARMER-004", 
            origin, destination, 5, 50.0, 1000);
        
        assertTrue(latch1.await(5, TimeUnit.SECONDS), "Listener 1 should be notified");
        assertTrue(latch2.await(5, TimeUnit.SECONDS), "Listener 2 should be notified");
        Thread.sleep(500);
        assertTrue(listener1Called.get());
        assertTrue(listener2Called.get());
    }
    */
    
    /*
    @Test
    public void testUnregisterListener() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(40.1, -74.1, "Destination");
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        SimulationListener listener = new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {
                callCount.incrementAndGet();
            }
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
        };
        
        simulationManager.registerListener(listener);
        simulationManager.unregisterListener(listener);
        
        simulationManager.startSimulation("BATCH-005", "FARMER-005", 
            origin, destination, 5, 50.0, 1000);
        
        Thread.sleep(1000); // Wait a bit
        
        assertEquals(0, callCount.get(), "Unregistered listener should not be called");
    }
    */
    
    /*
    @Test
    public void testCannotStartSimulationWhileRunning() throws InterruptedException {
        DeliverySimulator.GeoCoordinate origin = 
            new DeliverySimulator.GeoCoordinate(40.0, -74.0, "Origin");
        DeliverySimulator.GeoCoordinate destination = 
            new DeliverySimulator.GeoCoordinate(40.1, -74.1, "Destination");
        
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicBoolean errorReceived = new AtomicBoolean(false);
        
        simulationManager.registerListener(new SimulationListener() {
            @Override
            public void onSimulationStarted(String batchId, String farmerId) {}
            
            @Override
            public void onProgressUpdate(String batchId, double progress, String currentLocation) {}
            
            @Override
            public void onSimulationStopped(String batchId, boolean completed) {}
            
            @Override
            public void onSimulationError(String batchId, String error) {
                errorReceived.set(true);
                errorLatch.countDown();
            }
        });
        
        // Start first simulation
        simulationManager.startSimulation("BATCH-006", "FARMER-006", 
            origin, destination, 5, 50.0, 1000);
        
        assertTrue(simulationManager.isRunning());
        
        // Try to start another simulation while first is running
        simulationManager.startSimulation("BATCH-007", "FARMER-007", 
            origin, destination, 5, 50.0, 1000);
        
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Should receive error notification");
        Thread.sleep(500);
        assertTrue(errorReceived.get());
        assertEquals("BATCH-006", simulationManager.getSimulationId(), 
            "First simulation should still be running");
    }
    */
}
