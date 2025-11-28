package org.vericrop.kafka.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationControlEvent.
 */
class SimulationControlEventTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void testStartEvent() {
        // Given
        String simulationId = "sim-123";
        String instanceId = "instance-456";
        String batchId = "BATCH_001";
        String farmerId = "farmer-789";
        
        // When
        SimulationControlEvent event = SimulationControlEvent.start(
            simulationId, instanceId, batchId, farmerId);
        
        // Then
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
        assertEquals(simulationId, event.getSimulationId());
        assertEquals(instanceId, event.getInstanceId());
        assertEquals(batchId, event.getBatchId());
        assertEquals(farmerId, event.getFarmerId());
        assertTrue(event.isStart());
        assertFalse(event.isStop());
        assertTrue(event.getTimestamp() > 0);
    }
    
    @Test
    void testStopEvent() {
        // Given
        String simulationId = "sim-123";
        String instanceId = "instance-456";
        
        // When
        SimulationControlEvent event = SimulationControlEvent.stop(simulationId, instanceId);
        
        // Then
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
        assertEquals(simulationId, event.getSimulationId());
        assertEquals(instanceId, event.getInstanceId());
        assertFalse(event.isStart());
        assertTrue(event.isStop());
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        // Given
        SimulationControlEvent event = SimulationControlEvent.start(
            "sim-123", "instance-456", "BATCH_001", "farmer-789");
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"action\":\"START\""));
        assertTrue(json.contains("\"simulationId\":\"sim-123\""));
        assertTrue(json.contains("\"batchId\":\"BATCH_001\""));
    }
    
    @Test
    void testJsonDeserialization() throws Exception {
        // Given
        String json = "{\"action\":\"START\",\"simulationId\":\"sim-123\"," +
                     "\"instanceId\":\"instance-456\",\"batchId\":\"BATCH_001\"," +
                     "\"farmerId\":\"farmer-789\",\"timestamp\":1234567890}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
        assertEquals("sim-123", event.getSimulationId());
        assertEquals("instance-456", event.getInstanceId());
        assertEquals("BATCH_001", event.getBatchId());
        assertEquals("farmer-789", event.getFarmerId());
        assertEquals(1234567890L, event.getTimestamp());
    }
    
    @Test
    void testToString() {
        // Given
        SimulationControlEvent event = SimulationControlEvent.start(
            "sim-123", "instance-456", "BATCH_001", "farmer-789");
        
        // When
        String str = event.toString();
        
        // Then
        assertNotNull(str);
        assertTrue(str.contains("START"));
        assertTrue(str.contains("sim-123"));
        assertTrue(str.contains("BATCH_001"));
    }
    
    @Test
    void testDefaultConstructor() {
        // When
        SimulationControlEvent event = new SimulationControlEvent();
        
        // Then
        assertTrue(event.getTimestamp() > 0);
    }
    
    @Test
    void testSetters() {
        // Given
        SimulationControlEvent event = new SimulationControlEvent();
        
        // When
        event.setAction(SimulationControlEvent.Action.STOP);
        event.setSimulationId("sim-xyz");
        event.setInstanceId("instance-abc");
        event.setBatchId("BATCH_XYZ");
        event.setFarmerId("farmer-def");
        event.setTimestamp(9876543210L);
        
        // Then
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
        assertEquals("sim-xyz", event.getSimulationId());
        assertEquals("instance-abc", event.getInstanceId());
        assertEquals("BATCH_XYZ", event.getBatchId());
        assertEquals("farmer-def", event.getFarmerId());
        assertEquals(9876543210L, event.getTimestamp());
    }
}
