package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationAsyncService.
 * 
 * Tests verify:
 * - Task status tracking works correctly
 * - Status transitions are correct
 * - Task cleanup functions work
 */
class SimulationAsyncServiceTest {
    
    private SimulationAsyncService service;
    
    @BeforeEach
    void setUp() {
        service = new SimulationAsyncService();
    }
    
    // ==================== Task Status Tests ====================
    
    @Test
    void testSimulationTaskStatus_InitialState() {
        // Arrange & Act
        SimulationAsyncService.SimulationTaskStatus status = 
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123");
        
        // Assert
        assertEquals("SIM_TEST_123", status.getSimulationId());
        assertEquals(SimulationAsyncService.SimulationStatus.CREATING, status.getStatus());
        assertEquals("Simulation creation started", status.getMessage());
        assertEquals(0.0, status.getProgress());
        assertEquals("Initializing...", status.getCurrentLocation());
        assertTrue(status.getStartTime() > 0);
        assertTrue(status.getLastUpdateTime() > 0);
    }
    
    @Test
    void testSimulationTaskStatus_ToMap() {
        // Arrange
        SimulationAsyncService.SimulationTaskStatus status = 
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123");
        
        // Act
        Map<String, Object> map = status.toMap();
        
        // Assert
        assertEquals("SIM_TEST_123", map.get("simulation_id"));
        assertEquals("CREATING", map.get("status"));
        assertEquals("Simulation is being created in background", map.get("status_description"));
        assertEquals(0.0, map.get("progress"));
        assertNotNull(map.get("start_time"));
        assertNotNull(map.get("last_update_time"));
        assertNotNull(map.get("elapsed_ms"));
    }
    
    @Test
    void testSimulationStatus_Descriptions() {
        // Verify each status has a description
        assertEquals("Simulation is being created in background", 
                    SimulationAsyncService.SimulationStatus.CREATING.getDescription());
        assertEquals("Simulation is running", 
                    SimulationAsyncService.SimulationStatus.RUNNING.getDescription());
        assertEquals("Simulation completed successfully", 
                    SimulationAsyncService.SimulationStatus.COMPLETED.getDescription());
        assertEquals("Simulation failed", 
                    SimulationAsyncService.SimulationStatus.FAILED.getDescription());
    }
    
    // ==================== Task Management Tests ====================
    
    @Test
    void testHasTask_NotExists() {
        // Assert
        assertFalse(service.hasTask("NON_EXISTENT_SIM"));
    }
    
    @Test
    void testGetTaskStatus_NotExists() {
        // Assert
        assertNull(service.getTaskStatus("NON_EXISTENT_SIM"));
    }
    
    @Test
    void testRemoveTask() {
        // This tests the cleanup functionality
        // Since we can't easily inject a task without calling the async method,
        // we verify the method doesn't throw when called on non-existent task
        
        // Act & Assert (should not throw)
        service.removeTask("NON_EXISTENT_SIM");
    }
    
    @Test
    void testCleanupOldTasks_NoTasks() {
        // Arrange
        long maxAgeMs = 1000;
        
        // Act
        int removed = service.cleanupOldTasks(maxAgeMs);
        
        // Assert
        assertEquals(0, removed);
    }
    
    // ==================== Status Transition Tests ====================
    
    @Test
    void testTaskStatus_StatusTransitions() {
        // Test that all status values are valid
        for (SimulationAsyncService.SimulationStatus status : 
             SimulationAsyncService.SimulationStatus.values()) {
            assertNotNull(status.getDescription());
            assertFalse(status.getDescription().isEmpty());
        }
    }
    
    // ==================== Time Tracking Tests ====================
    
    @Test
    void testTaskStatus_TimeTracking() throws InterruptedException {
        // Arrange
        SimulationAsyncService.SimulationTaskStatus status = 
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123");
        long startTime = status.getStartTime();
        long initialUpdateTime = status.getLastUpdateTime();
        
        // Wait a tiny bit
        Thread.sleep(10);
        
        // Act - trigger an update
        // (Since we can't call the internal setter, just verify initial values)
        
        // Assert
        assertTrue(startTime > 0);
        assertTrue(initialUpdateTime >= startTime);
        
        // Verify elapsed_ms in map
        Map<String, Object> map = status.toMap();
        long elapsedMs = (Long) map.get("elapsed_ms");
        assertTrue(elapsedMs >= 0);
    }
    
    // ==================== Service Bean Tests ====================
    
    @Test
    void testServiceInstantiation() {
        // Verify service can be instantiated without errors
        SimulationAsyncService newService = new SimulationAsyncService();
        assertNotNull(newService);
    }
}
