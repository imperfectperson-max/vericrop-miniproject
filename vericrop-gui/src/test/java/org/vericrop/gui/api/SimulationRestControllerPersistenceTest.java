package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.models.Simulation;
import org.vericrop.gui.models.SimulationBatch;
import org.vericrop.gui.services.SimulationAsyncService;
import org.vericrop.gui.services.SimulationPersistenceService;
import org.vericrop.gui.services.SimulationStateService;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MapSimulator;
import org.vericrop.service.ScenarioManager;
import org.vericrop.service.simulation.SimulationManager;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimulationRestController with supplier/consumer validation.
 * 
 * Tests verify:
 * - Starting simulation requires supplierUsername and consumerUsername
 * - Invalid supplier/consumer usernames return appropriate errors
 * - Simulation token is generated and returned
 * - Access control for simulation and report endpoints
 * - Batch creation and progress updates
 */
@ExtendWith(MockitoExtension.class)
class SimulationRestControllerPersistenceTest {
    
    @Mock
    private MapSimulator mapSimulator;
    
    @Mock
    private ScenarioManager scenarioManager;
    
    @Mock
    private DeliverySimulator deliverySimulator;
    
    @Mock
    private SimulationManager simulationManager;
    
    @Mock
    private SimulationAsyncService simulationAsyncService;
    
    @Mock
    private SimulationPersistenceService simulationPersistenceService;
    
    @Mock
    private SimulationStateService simulationStateService;
    
    private SimulationRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new SimulationRestController(
            mapSimulator, scenarioManager, deliverySimulator, 
            simulationManager, simulationAsyncService, simulationPersistenceService,
            simulationStateService);
    }
    
    // ==================== Start Simulation Tests ====================
    
    @Test
    void testStartSimulation_RequiresSupplierUsername() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("consumerUsername", "consumer1");
        // Missing supplierUsername
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("supplierUsername"));
    }
    
    @Test
    void testStartSimulation_RequiresConsumerUsername() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("supplierUsername", "supplier1");
        // Missing consumerUsername
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("consumerUsername"));
    }
    
    @Test
    void testStartSimulation_RejectsEmptySupplierUsername() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("supplierUsername", "   ");
        request.put("consumerUsername", "consumer1");
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
    }
    
    @Test
    void testStartSimulation_SupplierNotFound() {
        // Arrange
        Map<String, Object> request = createValidStartRequest();
        when(scenarioManager.isValidScenario(anyString())).thenReturn(true);
        when(simulationPersistenceService.startSimulation(
            anyString(), anyLong(), eq("nonexistent_supplier"), anyString(), any()))
            .thenReturn(SimulationPersistenceService.SimulationStartResult.error(
                "Supplier user not found: nonexistent_supplier"));
        
        request.put("supplierUsername", "nonexistent_supplier");
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Supplier"));
    }
    
    @Test
    void testStartSimulation_ConsumerNotFound() {
        // Arrange
        Map<String, Object> request = createValidStartRequest();
        when(scenarioManager.isValidScenario(anyString())).thenReturn(true);
        when(simulationPersistenceService.startSimulation(
            anyString(), anyLong(), anyString(), eq("nonexistent_consumer"), any()))
            .thenReturn(SimulationPersistenceService.SimulationStartResult.error(
                "Consumer user not found: nonexistent_consumer"));
        
        request.put("consumerUsername", "nonexistent_consumer");
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Consumer"));
    }
    
    @Test
    void testStartSimulation_Success() {
        // Arrange
        Map<String, Object> request = createValidStartRequest();
        Simulation mockSimulation = createMockSimulation();
        
        when(scenarioManager.isValidScenario(anyString())).thenReturn(true);
        when(scenarioManager.getScenarioInfo(anyString())).thenReturn(new HashMap<>());
        when(simulationPersistenceService.startSimulation(
            anyString(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(SimulationPersistenceService.SimulationStartResult.success(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("simulation_id"));
        assertNotNull(response.getBody().get("simulation_token"));
        assertEquals("supplier1", response.getBody().get("supplier_username"));
        assertEquals("consumer1", response.getBody().get("consumer_username"));
    }
    
    @Test
    void testStartSimulation_ReturnsToken() {
        // Arrange
        Map<String, Object> request = createValidStartRequest();
        Simulation mockSimulation = createMockSimulation();
        
        when(scenarioManager.isValidScenario(anyString())).thenReturn(true);
        when(scenarioManager.getScenarioInfo(anyString())).thenReturn(new HashMap<>());
        when(simulationPersistenceService.startSimulation(
            anyString(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(SimulationPersistenceService.SimulationStartResult.success(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulation(request);
        
        // Assert
        String token = (String) response.getBody().get("simulation_token");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
    
    // ==================== Get Simulation Tests ====================
    
    @Test
    void testGetSimulation_Found() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Simulation mockSimulation = createMockSimulation();
        mockSimulation.setId(simId);
        
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), null, null);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(simId.toString(), response.getBody().get("id"));
    }
    
    @Test
    void testGetSimulation_NotFound() {
        // Arrange
        UUID simId = UUID.randomUUID();
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.empty());
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), null, null);
        
        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
    }
    
    @Test
    void testGetSimulation_AccessDenied() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Simulation mockSimulation = createMockSimulation();
        mockSimulation.setId(simId);
        mockSimulation.setOwnerUserId(1L);
        mockSimulation.setSupplierUserId(2L);
        mockSimulation.setConsumerUserId(3L);
        
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act - User 99 is not owner, supplier, or consumer
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), 99L, null);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Access denied"));
    }
    
    @Test
    void testGetSimulation_OwnerCanAccess() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Simulation mockSimulation = createMockSimulation();
        mockSimulation.setId(simId);
        mockSimulation.setOwnerUserId(1L);
        
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), 1L, null);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testGetSimulation_SupplierCanAccess() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Simulation mockSimulation = createMockSimulation();
        mockSimulation.setId(simId);
        mockSimulation.setSupplierUserId(2L);
        
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), 2L, null);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testGetSimulation_ConsumerCanAccess() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Simulation mockSimulation = createMockSimulation();
        mockSimulation.setId(simId);
        mockSimulation.setConsumerUserId(3L);
        
        when(simulationPersistenceService.getSimulation(simId))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulation(simId.toString(), 3L, null);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    // ==================== Get Simulation by Token Tests ====================
    
    @Test
    void testGetSimulationByToken_Found() {
        // Arrange
        String token = "abc123token";
        Simulation mockSimulation = createMockSimulation();
        
        when(simulationPersistenceService.getSimulationByToken(token))
            .thenReturn(Optional.of(mockSimulation));
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulationByToken(token);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }
    
    @Test
    void testGetSimulationByToken_NotFound() {
        // Arrange
        String token = "invalidtoken";
        when(simulationPersistenceService.getSimulationByToken(token))
            .thenReturn(Optional.empty());
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulationByToken(token);
        
        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
    }
    
    // ==================== Report Tests ====================
    
    @Test
    void testGetSimulationReport_AccessDenied() {
        // Arrange
        UUID simId = UUID.randomUUID();
        when(simulationPersistenceService.canUserAccess(simId, 99L)).thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulationReport(simId.toString(), 99L, null);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
    
    @Test
    void testGetSimulationReport_Success() {
        // Arrange
        UUID simId = UUID.randomUUID();
        Map<String, Object> mockReport = new HashMap<>();
        mockReport.put("simulation_id", simId.toString());
        mockReport.put("total_batches", 5);
        
        when(simulationPersistenceService.canUserAccess(simId, 1L)).thenReturn(true);
        when(simulationPersistenceService.generateReport(simId)).thenReturn(mockReport);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulationReport(simId.toString(), 1L, null);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("total_batches"));
    }
    
    // ==================== Batch Tests ====================
    
    @Test
    void testCreateBatch_Success() {
        // Arrange
        UUID simId = UUID.randomUUID();
        SimulationBatch mockBatch = new SimulationBatch(simId, 0, 100);
        mockBatch.setId(UUID.randomUUID());
        mockBatch.setCreatedAt(LocalDateTime.now());
        
        Map<String, Object> request = new HashMap<>();
        request.put("quantity", 100);
        
        when(simulationPersistenceService.createBatch(eq(simId), eq(100), any()))
            .thenReturn(mockBatch);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.createBatch(simId.toString(), request);
        
        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
    
    @Test
    void testGetSimulationBatches_AccessDenied() {
        // Arrange
        UUID simId = UUID.randomUUID();
        when(simulationPersistenceService.canUserAccess(simId, 99L)).thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.getSimulationBatches(simId.toString(), 99L, null);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
    
    // ==================== Token Validation Tests ====================
    
    @Test
    void testValidateToken_Valid() {
        // Arrange
        UUID simId = UUID.randomUUID();
        String token = "validtoken";
        when(simulationPersistenceService.validateToken(simId, token)).thenReturn(true);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.validateToken(simId.toString(), token);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("valid"));
    }
    
    @Test
    void testValidateToken_Invalid() {
        // Arrange
        UUID simId = UUID.randomUUID();
        String token = "invalidtoken";
        when(simulationPersistenceService.validateToken(simId, token)).thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = 
            controller.validateToken(simId.toString(), token);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("valid"));
    }
    
    // ==================== Helper Methods ====================
    
    private Map<String, Object> createValidStartRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("supplierUsername", "supplier1");
        request.put("consumerUsername", "consumer1");
        request.put("title", "Test Simulation");
        return request;
    }
    
    private Simulation createMockSimulation() {
        Simulation sim = new Simulation();
        sim.setId(UUID.randomUUID());
        sim.setTitle("Test Simulation");
        sim.setStatus(Simulation.STATUS_RUNNING);
        sim.setOwnerUserId(1L);
        sim.setSupplierUserId(2L);
        sim.setConsumerUserId(3L);
        sim.setSimulationToken("abc123tokenxyz456");
        sim.setOwnerUsername("owner1");
        sim.setSupplierUsername("supplier1");
        sim.setConsumerUsername("consumer1");
        sim.setStartedAt(LocalDateTime.now());
        sim.setCreatedAt(LocalDateTime.now());
        return sim;
    }
}
