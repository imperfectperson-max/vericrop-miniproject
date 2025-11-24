package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.vericrop.dto.SimulationEvent;
import org.vericrop.service.SimulationService;
import org.vericrop.service.TemperatureService;
import org.vericrop.service.simulation.SimulationManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test for LogisticsRestController
 */
@ExtendWith(MockitoExtension.class)
class LogisticsRestControllerTest {
    
    @Mock
    private SimulationService simulationService;
    
    @Mock
    private TemperatureService temperatureService;
    
    @Mock
    private SimulationManager simulationManager;
    
    private LogisticsRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new LogisticsRestController(
            simulationService,
            temperatureService,
            simulationManager
        );
    }
    
    @Test
    void testHealthEndpoint() {
        ResponseEntity<Map<String, Object>> response = controller.health();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("logistics-api", response.getBody().get("service"));
        assertTrue(response.getBody().containsKey("timestamp"));
    }
    
    @Test
    void testGetTemperatureHistory_NoActiveSimulation() {
        String shipmentId = "TEST_BATCH_001";
        
        // Mock: no active simulation
        when(simulationService.getHistoricalTemperatureReadings(shipmentId))
            .thenReturn(Collections.emptyList());
        when(simulationService.isSimulationRunning(shipmentId)).thenReturn(false);
        
        ResponseEntity<Map<String, Object>> response = controller.getTemperatureHistory(shipmentId, 100);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(shipmentId, body.get("shipment_id"));
        assertFalse((Boolean) body.get("simulation_active"));
        
        @SuppressWarnings("unchecked")
        List<Object> readings = (List<Object>) body.get("readings");
        assertNotNull(readings);
        assertTrue(readings.isEmpty());
    }
    
    @Test
    void testGetTemperatureHistory_WithHistoricalData() {
        String shipmentId = "TEST_BATCH_002";
        
        // Create mock historical readings
        List<SimulationEvent> mockEvents = new ArrayList<>();
        SimulationEvent event1 = new SimulationEvent(
            shipmentId,
            System.currentTimeMillis() - 60000,
            40.7128,
            -74.0060,
            "Location 1",
            4.5,
            65.0,
            "In Transit",
            3600000,
            25.0,
            1,
            4,
            "FARMER_001",
            SimulationEvent.EventType.TEMPERATURE_UPDATE
        );
        
        SimulationEvent event2 = new SimulationEvent(
            shipmentId,
            System.currentTimeMillis() - 30000,
            40.7589,
            -73.9851,
            "Location 2",
            4.8,
            66.0,
            "In Transit",
            1800000,
            50.0,
            2,
            4,
            "FARMER_001",
            SimulationEvent.EventType.TEMPERATURE_UPDATE
        );
        
        mockEvents.add(event1);
        mockEvents.add(event2);
        
        when(simulationService.getHistoricalTemperatureReadings(shipmentId))
            .thenReturn(mockEvents);
        when(simulationService.isSimulationRunning(shipmentId)).thenReturn(true);
        
        ResponseEntity<Map<String, Object>> response = controller.getTemperatureHistory(shipmentId, 100);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(shipmentId, body.get("shipment_id"));
        assertTrue((Boolean) body.get("simulation_active"));
        assertEquals(2, body.get("count"));
        
        @SuppressWarnings("unchecked")
        List<Object> readings = (List<Object>) body.get("readings");
        assertNotNull(readings);
        assertEquals(2, readings.size());
    }
    
    @Test
    void testGetTemperatureHistory_WithLimit() {
        String shipmentId = "TEST_BATCH_003";
        
        // Create more events than the limit
        List<SimulationEvent> mockEvents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SimulationEvent event = new SimulationEvent(
                shipmentId,
                System.currentTimeMillis() - (i * 10000),
                40.7128 + i * 0.01,
                -74.0060 + i * 0.01,
                "Location " + i,
                4.5 + i * 0.1,
                65.0,
                "In Transit",
                3600000,
                i * 10.0,
                i,
                10,
                "FARMER_001",
                SimulationEvent.EventType.TEMPERATURE_UPDATE
            );
            mockEvents.add(event);
        }
        
        when(simulationService.getHistoricalTemperatureReadings(shipmentId))
            .thenReturn(mockEvents);
        when(simulationService.isSimulationRunning(shipmentId)).thenReturn(false);
        
        // Request with limit of 5
        ResponseEntity<Map<String, Object>> response = controller.getTemperatureHistory(shipmentId, 5);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        @SuppressWarnings("unchecked")
        List<Object> readings = (List<Object>) body.get("readings");
        assertNotNull(readings);
        assertEquals(5, readings.size(), "Should respect the limit parameter");
    }
    
    @Test
    void testStreamTemperature_CreatesEmitter() {
        String shipmentId = "TEST_BATCH_004";
        
        // Mock: no active simulation
        when(simulationService.getHistoricalTemperatureReadings(shipmentId))
            .thenReturn(Collections.emptyList());
        when(simulationService.isSimulationRunning(shipmentId)).thenReturn(false);
        
        SseEmitter emitter = controller.streamTemperature(shipmentId);
        
        assertNotNull(emitter);
        assertNotNull(emitter.getTimeout());
        
        // Give async task time to execute
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify service was called
        verify(simulationService).getHistoricalTemperatureReadings(shipmentId);
        verify(simulationService).isSimulationRunning(shipmentId);
    }
    
    @Test
    void testStreamTemperature_WithActiveSimulation() {
        String shipmentId = "TEST_BATCH_005";
        
        // Mock: active simulation
        when(simulationService.getHistoricalTemperatureReadings(shipmentId))
            .thenReturn(Collections.emptyList());
        when(simulationService.isSimulationRunning(shipmentId)).thenReturn(true);
        when(simulationService.subscribeToTemperatureUpdates(eq(shipmentId), any()))
            .thenReturn(true);
        
        SseEmitter emitter = controller.streamTemperature(shipmentId);
        
        assertNotNull(emitter);
        
        // Give async task time to execute
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify subscription was attempted
        verify(simulationService).subscribeToTemperatureUpdates(eq(shipmentId), any());
    }
    
    @Test
    void testGetActiveShipments_ReturnsEmptyList() {
        // SimulationManager is static and may not be initialized in test environment
        // Test should handle gracefully
        
        ResponseEntity<Map<String, Object>> response = controller.getActiveShipments();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("shipments"));
        assertTrue(body.containsKey("count"));
        assertTrue(body.containsKey("timestamp"));
        
        @SuppressWarnings("unchecked")
        List<Object> shipments = (List<Object>) body.get("shipments");
        assertNotNull(shipments);
        // In test environment without running simulation, should be empty
    }
}
