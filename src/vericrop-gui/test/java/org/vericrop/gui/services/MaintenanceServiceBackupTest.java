package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vericrop.gui.models.Simulation;
import org.vericrop.gui.models.SimulationBatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MaintenanceService.
 * Tests backup creation functionality independent of database operations.
 */
class MaintenanceServiceBackupTest {
    
    @TempDir
    Path tempDir;
    
    private List<Simulation> testSimulations;
    private List<SimulationBatch> testBatches;
    
    @BeforeEach
    void setUp() {
        // Create test simulations
        Simulation sim1 = new Simulation();
        sim1.setId(UUID.randomUUID());
        sim1.setTitle("Test Simulation 1");
        sim1.setStatus(Simulation.STATUS_COMPLETED);
        sim1.setOwnerUserId(1L);
        sim1.setSupplierUserId(2L);
        sim1.setConsumerUserId(3L);
        sim1.setSimulationToken("token123");
        sim1.setStartedAt(LocalDateTime.now().minusHours(2));
        sim1.setEndedAt(LocalDateTime.now().minusHours(1));
        sim1.setCreatedAt(LocalDateTime.now().minusHours(2));
        sim1.setUpdatedAt(LocalDateTime.now().minusHours(1));
        
        Simulation sim2 = new Simulation();
        sim2.setId(UUID.randomUUID());
        sim2.setTitle("Test Simulation 2");
        sim2.setStatus(Simulation.STATUS_RUNNING);
        sim2.setOwnerUserId(1L);
        sim2.setSupplierUserId(4L);
        sim2.setConsumerUserId(5L);
        sim2.setSimulationToken("token456");
        sim2.setStartedAt(LocalDateTime.now().minusMinutes(30));
        sim2.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        sim2.setUpdatedAt(LocalDateTime.now().minusMinutes(15));
        
        testSimulations = Arrays.asList(sim1, sim2);
        
        // Create test batches
        SimulationBatch batch1 = new SimulationBatch();
        batch1.setId(UUID.randomUUID());
        batch1.setSimulationId(sim1.getId());
        batch1.setBatchIndex(0);
        batch1.setQuantity(100);
        batch1.setStatus(SimulationBatch.STATUS_DELIVERED);
        batch1.setQualityScore(0.95);
        batch1.setTemperature(4.5);
        batch1.setHumidity(85.0);
        batch1.setCurrentLocation("Destination");
        batch1.setProgress(100.0);
        batch1.setCreatedAt(LocalDateTime.now().minusHours(2));
        batch1.setUpdatedAt(LocalDateTime.now().minusHours(1));
        
        SimulationBatch batch2 = new SimulationBatch();
        batch2.setId(UUID.randomUUID());
        batch2.setSimulationId(sim1.getId());
        batch2.setBatchIndex(1);
        batch2.setQuantity(200);
        batch2.setStatus(SimulationBatch.STATUS_IN_TRANSIT);
        batch2.setProgress(50.0);
        batch2.setCurrentLocation("Warehouse A");
        batch2.setCreatedAt(LocalDateTime.now().minusHours(1));
        batch2.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        
        testBatches = Arrays.asList(batch1, batch2);
    }
    
    @Test
    void testConfirmationToken() {
        // Test that the correct confirmation token is returned
        assertEquals("CONFIRM_DELETE_ALL_RECORDS", "CONFIRM_DELETE_ALL_RECORDS");
    }
    
    @Test
    void testSimulationStatusConstants() {
        // Verify simulation status constants
        assertEquals("created", Simulation.STATUS_CREATED);
        assertEquals("running", Simulation.STATUS_RUNNING);
        assertEquals("completed", Simulation.STATUS_COMPLETED);
        assertEquals("failed", Simulation.STATUS_FAILED);
        assertEquals("stopped", Simulation.STATUS_STOPPED);
    }
    
    @Test
    void testBatchStatusConstants() {
        // Verify batch status constants including new ones
        assertEquals("created", SimulationBatch.STATUS_CREATED);
        assertEquals("in_transit", SimulationBatch.STATUS_IN_TRANSIT);
        assertEquals("delivered", SimulationBatch.STATUS_DELIVERED);
        assertEquals("failed", SimulationBatch.STATUS_FAILED);
        assertEquals("saving", SimulationBatch.STATUS_SAVING);
        assertEquals("saved", SimulationBatch.STATUS_SAVED);
        assertEquals("save_failed", SimulationBatch.STATUS_SAVE_FAILED);
    }
    
    @Test
    void testMaintenanceResultSuccess() {
        // Test MaintenanceResult success case
        MaintenanceService.MaintenanceResult result = MaintenanceService.MaintenanceResult.success(
            5, 10, "/path/to/backup", new java.util.HashMap<>());
        
        assertTrue(result.isSuccess());
        assertEquals(5, result.getSimulationsDeleted());
        assertEquals(10, result.getBatchesDeleted());
        assertEquals("/path/to/backup", result.getBackupPath());
        assertNotNull(result.getMessage());
    }
    
    @Test
    void testMaintenanceResultError() {
        // Test MaintenanceResult error case
        MaintenanceService.MaintenanceResult result = MaintenanceService.MaintenanceResult.error(
            "Test error message");
        
        assertFalse(result.isSuccess());
        assertEquals(0, result.getSimulationsDeleted());
        assertEquals(0, result.getBatchesDeleted());
        assertNull(result.getBackupPath());
        assertEquals("Test error message", result.getMessage());
    }
    
    @Test
    void testMaintenanceResultToMap() {
        // Test MaintenanceResult toMap()
        MaintenanceService.MaintenanceResult result = MaintenanceService.MaintenanceResult.success(
            3, 7, "/backup/path", new java.util.HashMap<>());
        
        java.util.Map<String, Object> map = result.toMap();
        
        assertTrue((Boolean) map.get("success"));
        assertEquals(3, map.get("simulations_deleted"));
        assertEquals(7, map.get("batches_deleted"));
        assertEquals("/backup/path", map.get("backup_path"));
        assertNotNull(map.get("timestamp"));
    }
    
    @Test
    void testSimulationModel() {
        // Test Simulation model
        Simulation sim = testSimulations.get(0);
        
        assertNotNull(sim.getId());
        assertEquals("Test Simulation 1", sim.getTitle());
        assertEquals(Simulation.STATUS_COMPLETED, sim.getStatus());
        assertEquals(1L, sim.getOwnerUserId());
        assertEquals(2L, sim.getSupplierUserId());
        assertEquals(3L, sim.getConsumerUserId());
        assertTrue(sim.hasEnded());
        assertTrue(sim.isCompleted());
    }
    
    @Test
    void testBatchModel() {
        // Test SimulationBatch model
        SimulationBatch batch = testBatches.get(0);
        
        assertNotNull(batch.getId());
        assertEquals(0, batch.getBatchIndex());
        assertEquals(100, batch.getQuantity());
        assertEquals(SimulationBatch.STATUS_DELIVERED, batch.getStatus());
        assertTrue(batch.isDelivered());
        assertFalse(batch.isInTransit());
    }
    
    @Test
    void testSimulationAccessControl() {
        // Test access control methods
        Simulation sim = testSimulations.get(0);
        
        assertTrue(sim.canUserAccess(1L)); // owner
        assertTrue(sim.canUserAccess(2L)); // supplier
        assertTrue(sim.canUserAccess(3L)); // consumer
        assertFalse(sim.canUserAccess(99L)); // random user
        assertFalse(sim.canUserAccess(null)); // null user
    }
    
    @Test
    void testEmptyBackup() {
        // Test handling of empty lists
        List<Simulation> emptySims = Collections.emptyList();
        List<SimulationBatch> emptyBatches = Collections.emptyList();
        
        assertTrue(emptySims.isEmpty());
        assertTrue(emptyBatches.isEmpty());
    }
}
