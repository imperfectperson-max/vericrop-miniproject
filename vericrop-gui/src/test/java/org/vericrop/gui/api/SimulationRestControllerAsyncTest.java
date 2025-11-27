package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.services.SimulationAsyncService;
import org.vericrop.gui.services.SimulationPersistenceService;
import org.vericrop.gui.services.SimulationStateService;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MapSimulator;
import org.vericrop.service.ScenarioManager;
import org.vericrop.service.simulation.SimulationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for async simulation endpoints in SimulationRestController.
 * 
 * Tests verify:
 * - Async endpoint returns HTTP 202 Accepted immediately
 * - Simulation ID is returned for polling
 * - Status endpoint returns task status correctly
 * - Error handling for various failure scenarios
 */
@ExtendWith(MockitoExtension.class)
class SimulationRestControllerAsyncTest {
    
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
    
    // ==================== Async Start Tests ====================
    
    @Test
    void testStartSimulationAsync_ReturnsAccepted() {
        // Arrange
        Map<String, Object> request = createValidAsyncRequest();
        
        // Mock async service to return a completed future
        when(simulationAsyncService.createSimulationAsync(
            anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyInt(), anyDouble(), anyLong()
        )).thenReturn(CompletableFuture.completedFuture(
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123")
        ));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        
        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("accepted"));
        assertNotNull(response.getBody().get("simulation_id"));
        assertNotNull(response.getBody().get("status_url"));
        assertEquals("BATCH_001", response.getBody().get("batch_id"));
    }
    
    @Test
    void testStartSimulationAsync_ContainsSimulationId() {
        // Arrange
        Map<String, Object> request = createValidAsyncRequest();
        
        when(simulationAsyncService.createSimulationAsync(
            anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyInt(), anyDouble(), anyLong()
        )).thenReturn(CompletableFuture.completedFuture(
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123")
        ));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        
        // Assert
        String simulationId = (String) response.getBody().get("simulation_id");
        assertNotNull(simulationId);
        assertTrue(simulationId.startsWith("SIM_"));
    }
    
    @Test
    void testStartSimulationAsync_ContainsStatusUrl() {
        // Arrange
        Map<String, Object> request = createValidAsyncRequest();
        
        when(simulationAsyncService.createSimulationAsync(
            anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyInt(), anyDouble(), anyLong()
        )).thenReturn(CompletableFuture.completedFuture(
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123")
        ));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        
        // Assert
        String statusUrl = (String) response.getBody().get("status_url");
        assertNotNull(statusUrl);
        assertTrue(statusUrl.contains("/api/simulation/"));
        assertTrue(statusUrl.endsWith("/status"));
    }
    
    @Test
    void testStartSimulationAsync_WithCustomParameters() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("batch_id", "CUSTOM_BATCH");
        request.put("farmer_id", "CUSTOM_FARMER");
        request.put("origin_lat", 40.7128);
        request.put("origin_lon", -74.0060);
        request.put("origin_name", "New York");
        request.put("dest_lat", 34.0522);
        request.put("dest_lon", -118.2437);
        request.put("dest_name", "Los Angeles");
        request.put("num_waypoints", 30);
        request.put("avg_speed_kmh", 80.0);
        request.put("update_interval_ms", 5000);
        
        when(simulationAsyncService.createSimulationAsync(
            anyString(), eq("CUSTOM_BATCH"), eq("CUSTOM_FARMER"),
            eq(40.7128), eq(-74.0060), eq("New York"),
            eq(34.0522), eq(-118.2437), eq("Los Angeles"),
            eq(30), eq(80.0), eq(5000L)
        )).thenReturn(CompletableFuture.completedFuture(
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123")
        ));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        
        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("CUSTOM_BATCH", response.getBody().get("batch_id"));
        assertEquals("CUSTOM_FARMER", response.getBody().get("farmer_id"));
    }
    
    @Test
    void testStartSimulationAsync_WithDefaultParameters() {
        // Arrange - empty request should use defaults
        Map<String, Object> request = new HashMap<>();
        
        when(simulationAsyncService.createSimulationAsync(
            anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyInt(), anyDouble(), anyLong()
        )).thenReturn(CompletableFuture.completedFuture(
            new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123")
        ));
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        
        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        // Verify defaults were used
        verify(simulationAsyncService).createSimulationAsync(
            anyString(),
            argThat(s -> s.startsWith("BATCH_")),
            eq("FARMER_DEFAULT"),
            eq(42.3601), eq(-71.0589), eq("Sunny Valley Farm"),
            eq(42.3736), eq(-71.1097), eq("Metro Fresh Warehouse"),
            eq(20), eq(50.0), eq(10000L)
        );
    }
    
    // ==================== Status Endpoint Tests ====================
    
    @Test
    void testGetSimulationTaskStatus_Found() {
        // Arrange
        String simulationId = "SIM_TEST_123";
        SimulationAsyncService.SimulationTaskStatus taskStatus = 
            new SimulationAsyncService.SimulationTaskStatus(simulationId);
        
        when(simulationAsyncService.getTaskStatus(simulationId)).thenReturn(taskStatus);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getSimulationTaskStatus(simulationId);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(simulationId, response.getBody().get("simulation_id"));
        assertEquals("CREATING", response.getBody().get("status"));
    }
    
    @Test
    void testGetSimulationTaskStatus_NotFound() {
        // Arrange
        String simulationId = "NON_EXISTENT_SIM";
        when(simulationAsyncService.getTaskStatus(simulationId)).thenReturn(null);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getSimulationTaskStatus(simulationId);
        
        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("error"));
    }
    
    @Test
    void testGetSimulationTaskStatus_ContainsProgress() {
        // Arrange
        String simulationId = "SIM_TEST_123";
        SimulationAsyncService.SimulationTaskStatus taskStatus = 
            new SimulationAsyncService.SimulationTaskStatus(simulationId);
        
        when(simulationAsyncService.getTaskStatus(simulationId)).thenReturn(taskStatus);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getSimulationTaskStatus(simulationId);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("progress"));
        assertTrue(response.getBody().containsKey("current_location"));
        assertTrue(response.getBody().containsKey("status_description"));
    }
    
    // ==================== Response Time Verification ====================
    
    @Test
    void testStartSimulationAsync_ReturnsQuickly() {
        // This test verifies the async endpoint returns without waiting
        // for simulation creation to complete
        
        // Arrange
        Map<String, Object> request = createValidAsyncRequest();
        
        // Mock service to return a future that doesn't complete immediately
        // but the controller should still return right away
        CompletableFuture<SimulationAsyncService.SimulationTaskStatus> slowFuture = 
            new CompletableFuture<>();
        
        when(simulationAsyncService.createSimulationAsync(
            anyString(), anyString(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyDouble(), anyDouble(), anyString(),
            anyInt(), anyDouble(), anyLong()
        )).thenReturn(slowFuture);
        
        // Act & measure time
        long startTime = System.currentTimeMillis();
        ResponseEntity<Map<String, Object>> response = controller.startSimulationAsync(request);
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Assert - should return very quickly (under 100ms)
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(elapsedTime < 100, "Async endpoint should return quickly, took " + elapsedTime + "ms");
        
        // Clean up - complete the future
        slowFuture.complete(new SimulationAsyncService.SimulationTaskStatus("SIM_TEST_123"));
    }
    
    // ==================== Helper Methods ====================
    
    private Map<String, Object> createValidAsyncRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("batch_id", "BATCH_001");
        request.put("farmer_id", "FARMER_001");
        return request;
    }
}
