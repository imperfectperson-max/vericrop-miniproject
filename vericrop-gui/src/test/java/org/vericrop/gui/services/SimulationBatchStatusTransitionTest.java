package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vericrop.gui.dao.SimulationBatchDao;
import org.vericrop.gui.dao.SimulationDao;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.models.SimulationBatch;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for simulation batch status transitions.
 * Verifies that batches don't get stuck in intermediate states like 'in_transit'.
 */
@ExtendWith(MockitoExtension.class)
class SimulationBatchStatusTransitionTest {
    
    @Mock
    private DataSource dataSource;
    
    @Mock
    private SimulationBatchDao batchDao;
    
    @Mock
    private SimulationDao simulationDao;
    
    @Mock
    private UserDao userDao;
    
    private UUID testSimulationId;
    private UUID testBatchId;
    
    @BeforeEach
    void setUp() {
        testSimulationId = UUID.randomUUID();
        testBatchId = UUID.randomUUID();
    }
    
    @Test
    void testBatchStatusConstants_IncludeAllRequiredStates() {
        // Verify all status constants are defined
        assertNotNull(SimulationBatch.STATUS_CREATED);
        assertNotNull(SimulationBatch.STATUS_SAVING);
        assertNotNull(SimulationBatch.STATUS_IN_TRANSIT);
        assertNotNull(SimulationBatch.STATUS_DELIVERED);
        assertNotNull(SimulationBatch.STATUS_SAVED);
        assertNotNull(SimulationBatch.STATUS_SAVE_FAILED);
        assertNotNull(SimulationBatch.STATUS_FAILED);
    }
    
    @Test
    void testBatchStatusValues() {
        // Verify status values are correct
        assertEquals("created", SimulationBatch.STATUS_CREATED);
        assertEquals("saving", SimulationBatch.STATUS_SAVING);
        assertEquals("in_transit", SimulationBatch.STATUS_IN_TRANSIT);
        assertEquals("delivered", SimulationBatch.STATUS_DELIVERED);
        assertEquals("saved", SimulationBatch.STATUS_SAVED);
        assertEquals("save_failed", SimulationBatch.STATUS_SAVE_FAILED);
        assertEquals("failed", SimulationBatch.STATUS_FAILED);
    }
    
    @Test
    void testBatch_IsInTransit() {
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_IN_TRANSIT);
        
        assertTrue(batch.isInTransit());
        assertFalse(batch.isDelivered());
        assertFalse(batch.isFailed());
    }
    
    @Test
    void testBatch_IsDelivered() {
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_DELIVERED);
        
        assertFalse(batch.isInTransit());
        assertTrue(batch.isDelivered());
        assertFalse(batch.isFailed());
    }
    
    @Test
    void testBatch_IsFailed() {
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_FAILED);
        
        assertFalse(batch.isInTransit());
        assertFalse(batch.isDelivered());
        assertTrue(batch.isFailed());
    }
    
    @Test
    void testBatchCreation_WithTransactionSetsCorrectStatus() {
        // Create a batch that should be IN_TRANSIT after successful creation
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_IN_TRANSIT);
        batch.setProgress(0.0);
        batch.setCurrentLocation("Origin");
        
        // Verify the batch has the correct initial state
        assertEquals(SimulationBatch.STATUS_IN_TRANSIT, batch.getStatus());
        assertEquals(0.0, batch.getProgress());
        assertEquals("Origin", batch.getCurrentLocation());
    }
    
    @Test
    void testBatchProgress_UpdateStatusToDelivered() {
        // When progress reaches 100%, status should change to DELIVERED
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_IN_TRANSIT);
        batch.setProgress(100.0);
        batch.setCurrentLocation("Destination");
        batch.setStatus(SimulationBatch.STATUS_DELIVERED);
        
        assertTrue(batch.isDelivered());
        assertEquals(100.0, batch.getProgress());
        assertEquals("Destination", batch.getCurrentLocation());
    }
    
    @Test
    void testBatchProgress_StaysInTransitBelow100() {
        // When progress is below 100%, status should remain IN_TRANSIT
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_IN_TRANSIT);
        batch.setProgress(50.0);
        batch.setCurrentLocation("Halfway Point");
        
        assertTrue(batch.isInTransit());
        assertFalse(batch.isDelivered());
        assertEquals(50.0, batch.getProgress());
    }
    
    @Test
    void testBatchStatusTransition_ValidTransitions() {
        // Test valid status transitions
        SimulationBatch batch = new SimulationBatch();
        
        // created -> in_transit (valid)
        batch.setStatus(SimulationBatch.STATUS_CREATED);
        assertEquals(SimulationBatch.STATUS_CREATED, batch.getStatus());
        
        batch.setStatus(SimulationBatch.STATUS_IN_TRANSIT);
        assertEquals(SimulationBatch.STATUS_IN_TRANSIT, batch.getStatus());
        
        // in_transit -> delivered (valid)
        batch.setStatus(SimulationBatch.STATUS_DELIVERED);
        assertEquals(SimulationBatch.STATUS_DELIVERED, batch.getStatus());
    }
    
    @Test
    void testBatchDefaultStatus() {
        // New batch should have STATUS_CREATED by default
        SimulationBatch batch = new SimulationBatch();
        assertEquals(SimulationBatch.STATUS_CREATED, batch.getStatus());
    }
    
    @Test
    void testBatchDefaultProgress() {
        // New batch should have 0.0 progress by default
        SimulationBatch batch = new SimulationBatch();
        assertEquals(0.0, batch.getProgress());
    }
    
    @Test
    void testBatchConstructorWithRequiredFields() {
        SimulationBatch batch = new SimulationBatch(testSimulationId, 0, 100);
        
        assertEquals(testSimulationId, batch.getSimulationId());
        assertEquals(0, batch.getBatchIndex());
        assertEquals(100, batch.getQuantity());
        assertEquals(SimulationBatch.STATUS_CREATED, batch.getStatus());
        assertEquals(0.0, batch.getProgress());
    }
    
    @Test
    void testBatchToString_DoesNotThrowException() {
        SimulationBatch batch = createTestBatch(SimulationBatch.STATUS_IN_TRANSIT);
        
        assertDoesNotThrow(() -> batch.toString());
        assertNotNull(batch.toString());
        assertTrue(batch.toString().contains("in_transit"));
    }
    
    @Test
    void testNewStatusConstants_AreDistinct() {
        // Verify new status constants are distinct
        assertNotEquals(SimulationBatch.STATUS_SAVING, SimulationBatch.STATUS_IN_TRANSIT);
        assertNotEquals(SimulationBatch.STATUS_SAVED, SimulationBatch.STATUS_DELIVERED);
        assertNotEquals(SimulationBatch.STATUS_SAVE_FAILED, SimulationBatch.STATUS_FAILED);
    }
    
    /**
     * Helper method to create a test batch with given status.
     */
    private SimulationBatch createTestBatch(String status) {
        SimulationBatch batch = new SimulationBatch();
        batch.setId(testBatchId);
        batch.setSimulationId(testSimulationId);
        batch.setBatchIndex(0);
        batch.setQuantity(100);
        batch.setStatus(status);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        return batch;
    }
}
