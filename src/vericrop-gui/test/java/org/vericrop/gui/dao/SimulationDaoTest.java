package org.vericrop.gui.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.gui.models.Simulation;
import org.vericrop.gui.models.SimulationBatch;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimulationDao and SimulationBatchDao.
 * Tests verify database operations for simulation and batch persistence.
 */
class SimulationDaoTest {
    
    private DataSource mockDataSource;
    private Connection mockConnection;
    private PreparedStatement mockStatement;
    private ResultSet mockResultSet;
    
    private SimulationDao simulationDao;
    private SimulationBatchDao batchDao;
    
    @BeforeEach
    void setUp() throws SQLException {
        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        
        simulationDao = new SimulationDao(mockDataSource);
        batchDao = new SimulationBatchDao(mockDataSource);
    }
    
    // ==================== Token Generation Tests ====================
    
    @Test
    void testGenerateSimulationToken_IsNotNull() {
        String token = SimulationDao.generateSimulationToken();
        assertNotNull(token);
    }
    
    @Test
    void testGenerateSimulationToken_Is64Characters() {
        String token = SimulationDao.generateSimulationToken();
        assertEquals(64, token.length(), "Token should be 64 hex characters (32 bytes)");
    }
    
    @Test
    void testGenerateSimulationToken_IsHexadecimal() {
        String token = SimulationDao.generateSimulationToken();
        assertTrue(token.matches("[0-9a-f]{64}"), "Token should only contain hex characters");
    }
    
    @Test
    void testGenerateSimulationToken_IsUnique() {
        String token1 = SimulationDao.generateSimulationToken();
        String token2 = SimulationDao.generateSimulationToken();
        assertNotEquals(token1, token2, "Generated tokens should be unique");
    }
    
    // ==================== Simulation CRUD Tests ====================
    
    @Test
    void testCreateSimulation_Success() throws SQLException {
        // Arrange
        UUID expectedId = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("id")).thenReturn(expectedId.toString());
        when(mockResultSet.getTimestamp("started_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("created_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("updated_at")).thenReturn(now);
        
        // Act
        Simulation result = simulationDao.createSimulation(
            "Test Simulation", 1L, 2L, 3L, null);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedId, result.getId());
        assertEquals("Test Simulation", result.getTitle());
        assertEquals(1L, result.getOwnerUserId());
        assertEquals(2L, result.getSupplierUserId());
        assertEquals(3L, result.getConsumerUserId());
        assertNotNull(result.getSimulationToken());
        assertEquals(64, result.getSimulationToken().length());
    }
    
    @Test
    void testFindById_Found() throws SQLException {
        // Arrange
        UUID simId = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("id")).thenReturn(simId.toString());
        when(mockResultSet.getString("title")).thenReturn("Test Simulation");
        when(mockResultSet.getTimestamp("started_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("ended_at")).thenReturn(null);
        when(mockResultSet.getString("status")).thenReturn("running");
        when(mockResultSet.getLong("owner_user_id")).thenReturn(1L);
        when(mockResultSet.getLong("supplier_user_id")).thenReturn(2L);
        when(mockResultSet.getLong("consumer_user_id")).thenReturn(3L);
        when(mockResultSet.getString("simulation_token")).thenReturn("abc123");
        when(mockResultSet.getString("meta")).thenReturn(null);
        when(mockResultSet.getTimestamp("created_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("updated_at")).thenReturn(now);
        
        // Act
        Optional<Simulation> result = simulationDao.findById(simId);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(simId, result.get().getId());
        assertEquals("Test Simulation", result.get().getTitle());
        assertEquals("running", result.get().getStatus());
    }
    
    @Test
    void testFindById_NotFound() throws SQLException {
        // Arrange
        when(mockResultSet.next()).thenReturn(false);
        
        // Act
        Optional<Simulation> result = simulationDao.findById(UUID.randomUUID());
        
        // Assert
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testCanUserAccess_OwnerCanAccess() throws SQLException {
        // Arrange
        when(mockResultSet.next()).thenReturn(true);
        
        // Act
        boolean result = simulationDao.canUserAccess(UUID.randomUUID(), 1L);
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testCanUserAccess_NonParticipantCannotAccess() throws SQLException {
        // Arrange
        when(mockResultSet.next()).thenReturn(false);
        
        // Act
        boolean result = simulationDao.canUserAccess(UUID.randomUUID(), 999L);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    void testValidateToken_Valid() throws SQLException {
        // Arrange
        when(mockResultSet.next()).thenReturn(true);
        
        // Act
        boolean result = simulationDao.validateToken(UUID.randomUUID(), "validtoken");
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testValidateToken_Invalid() throws SQLException {
        // Arrange
        when(mockResultSet.next()).thenReturn(false);
        
        // Act
        boolean result = simulationDao.validateToken(UUID.randomUUID(), "invalidtoken");
        
        // Assert
        assertFalse(result);
    }
    
    // ==================== Batch CRUD Tests ====================
    
    @Test
    void testCreateBatch_Success() throws SQLException {
        // Arrange
        UUID simId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("id")).thenReturn(batchId.toString());
        when(mockResultSet.getTimestamp("created_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("updated_at")).thenReturn(now);
        
        // Act
        SimulationBatch result = batchDao.createBatch(simId, 0, 100, null);
        
        // Assert
        assertNotNull(result);
        assertEquals(batchId, result.getId());
        assertEquals(simId, result.getSimulationId());
        assertEquals(0, result.getBatchIndex());
        assertEquals(100, result.getQuantity());
    }
    
    @Test
    void testFindBySimulationId_ReturnsBatches() throws SQLException {
        // Arrange
        UUID simId = UUID.randomUUID();
        UUID batchId1 = UUID.randomUUID();
        UUID batchId2 = UUID.randomUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString("id")).thenReturn(batchId1.toString(), batchId2.toString());
        when(mockResultSet.getString("simulation_id")).thenReturn(simId.toString());
        when(mockResultSet.getInt("batch_index")).thenReturn(0, 1);
        when(mockResultSet.getInt("quantity")).thenReturn(100, 150);
        when(mockResultSet.getString("status")).thenReturn("created", "in_transit");
        when(mockResultSet.getDouble("quality_score")).thenReturn(0.0);
        when(mockResultSet.wasNull()).thenReturn(true);
        when(mockResultSet.getDouble("temperature")).thenReturn(0.0);
        when(mockResultSet.getDouble("humidity")).thenReturn(0.0);
        when(mockResultSet.getString("current_location")).thenReturn(null);
        when(mockResultSet.getDouble("progress")).thenReturn(0.0);
        when(mockResultSet.getString("metadata")).thenReturn(null);
        when(mockResultSet.getTimestamp("created_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("updated_at")).thenReturn(now);
        
        // Act
        List<SimulationBatch> result = batchDao.findBySimulationId(simId);
        
        // Assert
        assertEquals(2, result.size());
    }
    
    @Test
    void testUpdateBatchProgress_Success() throws SQLException {
        // Arrange
        when(mockStatement.executeUpdate()).thenReturn(1);
        
        // Act
        boolean result = batchDao.updateBatchProgress(
            UUID.randomUUID(), "in_transit", 4.5, 65.0, "Highway Mile 50", 50.0);
        
        // Assert
        assertTrue(result);
    }
    
    // ==================== Simulation Model Tests ====================
    
    @Test
    void testSimulation_CanUserAccess_Owner() {
        Simulation sim = new Simulation();
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        
        assertTrue(sim.canUserAccess(1L));
    }
    
    @Test
    void testSimulation_CanUserAccess_Supplier() {
        Simulation sim = new Simulation();
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        
        assertTrue(sim.canUserAccess(2L));
    }
    
    @Test
    void testSimulation_CanUserAccess_Consumer() {
        Simulation sim = new Simulation();
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        
        assertTrue(sim.canUserAccess(3L));
    }
    
    @Test
    void testSimulation_CanUserAccess_NonParticipant() {
        Simulation sim = new Simulation();
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        
        assertFalse(sim.canUserAccess(99L));
    }
    
    @Test
    void testSimulation_CanUserAccess_NullUserId() {
        Simulation sim = new Simulation();
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        
        assertFalse(sim.canUserAccess(null));
    }
    
    @Test
    void testSimulation_IsRunning() {
        Simulation sim = new Simulation();
        sim.setStatus(Simulation.STATUS_RUNNING);
        
        assertTrue(sim.isRunning());
        assertFalse(sim.isCompleted());
        assertFalse(sim.hasEnded());
    }
    
    @Test
    void testSimulation_IsCompleted() {
        Simulation sim = new Simulation();
        sim.setStatus(Simulation.STATUS_COMPLETED);
        
        assertFalse(sim.isRunning());
        assertTrue(sim.isCompleted());
        assertTrue(sim.hasEnded());
    }
    
    // ==================== SimulationBatch Model Tests ====================
    
    @Test
    void testSimulationBatch_StatusConstants() {
        assertEquals("created", SimulationBatch.STATUS_CREATED);
        assertEquals("in_transit", SimulationBatch.STATUS_IN_TRANSIT);
        assertEquals("delivered", SimulationBatch.STATUS_DELIVERED);
        assertEquals("failed", SimulationBatch.STATUS_FAILED);
    }
    
    @Test
    void testSimulationBatch_IsDelivered() {
        SimulationBatch batch = new SimulationBatch();
        batch.setStatus(SimulationBatch.STATUS_DELIVERED);
        
        assertTrue(batch.isDelivered());
        assertFalse(batch.isInTransit());
        assertFalse(batch.isFailed());
    }
}
