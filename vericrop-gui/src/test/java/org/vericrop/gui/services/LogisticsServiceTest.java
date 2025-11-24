package org.vericrop.gui.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for LogisticsService
 */
class LogisticsServiceTest {
    
    private LogisticsService service;
    
    @BeforeEach
    void setUp() {
        service = new LogisticsService();
    }
    
    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }
    
    @Test
    void testStartMapSimulation() {
        String batchId = "TEST_BATCH_001";
        Duration duration = Duration.ofSeconds(10);
        
        // Start simulation
        assertDoesNotThrow(() -> {
            service.startMapSimulation(batchId, duration);
        });
        
        // Verify simulation is active
        assertTrue(service.isSimulationActive(batchId), 
            "Simulation should be active after starting");
        
        // Wait a moment for simulation to start
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Stop simulation
        service.stopSimulation(batchId);
        
        // Verify simulation is stopped
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertFalse(service.isSimulationActive(batchId), 
            "Simulation should not be active after stopping");
    }
    
    @Test
    void testMultipleSimulations() {
        String batchId1 = "TEST_BATCH_001";
        String batchId2 = "TEST_BATCH_002";
        Duration duration = Duration.ofSeconds(10);
        
        // Start two simulations
        service.startMapSimulation(batchId1, duration);
        service.startMapSimulation(batchId2, duration);
        
        // Both should be active
        assertTrue(service.isSimulationActive(batchId1));
        assertTrue(service.isSimulationActive(batchId2));
        
        // Stop one
        service.stopSimulation(batchId1);
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Only batchId1 should be stopped
        assertFalse(service.isSimulationActive(batchId1));
        assertTrue(service.isSimulationActive(batchId2));
        
        // Stop the other
        service.stopSimulation(batchId2);
    }
    
    @Test
    void testStopNonExistentSimulation() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            service.stopSimulation("NON_EXISTENT_BATCH");
        });
    }
    
    @Test
    void testShutdown() {
        String batchId = "TEST_BATCH_001";
        service.startMapSimulation(batchId, Duration.ofSeconds(10));
        
        // Shutdown should stop all simulations
        assertDoesNotThrow(() -> {
            service.shutdown();
        });
        
        // After shutdown, simulation should not be active
        assertFalse(service.isSimulationActive(batchId));
    }
}
