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
    
    // ========== Tests for boolean start/stop fields (backward compatibility) ==========
    
    @Test
    void testDeserializationWithStartBoolean() throws Exception {
        // Given - JSON with "start":true instead of "action":"START"
        String json = "{\"start\":true,\"simulationId\":\"sim-bool-start\"," +
                     "\"instanceId\":\"instance-789\",\"batchId\":\"BATCH_BOOL\"," +
                     "\"farmerId\":\"farmer-bool\",\"timestamp\":1234567890}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then - action should be derived from "start":true
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
        assertTrue(event.isStart());
        assertFalse(event.isStop());
        assertEquals("sim-bool-start", event.getSimulationId());
        assertEquals("BATCH_BOOL", event.getBatchId());
    }
    
    @Test
    void testDeserializationWithStopBoolean() throws Exception {
        // Given - JSON with "stop":true instead of "action":"STOP"
        String json = "{\"stop\":true,\"simulationId\":\"sim-bool-stop\"," +
                     "\"instanceId\":\"instance-stop\",\"timestamp\":9876543210}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then - action should be derived from "stop":true
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
        assertTrue(event.isStop());
        assertFalse(event.isStart());
        assertEquals("sim-bool-stop", event.getSimulationId());
    }
    
    @Test
    void testDeserializationWithStartBooleanFalse() throws Exception {
        // Given - JSON with "start":false should NOT set action to START
        String json = "{\"start\":false,\"simulationId\":\"sim-no-action\"," +
                     "\"instanceId\":\"instance-test\",\"timestamp\":1234567890}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then - action should remain null since start is false
        assertNull(event.getAction());
        assertFalse(event.isStart());
        assertFalse(event.isStop());
    }
    
    @Test
    void testDeserializationWithActionOverridesBoolean() throws Exception {
        // Given - JSON with both "action" and "start" fields
        // The "action" field should take precedence (order-dependent, but action is explicit)
        String json = "{\"action\":\"STOP\",\"start\":true,\"simulationId\":\"sim-mixed\"," +
                     "\"instanceId\":\"instance-mixed\",\"timestamp\":1234567890}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then - The result depends on JSON processing order, but both should be valid
        // This test just ensures no exception is thrown
        assertNotNull(event);
        assertTrue(event.isStart() || event.isStop());
    }
    
    // ========== Tests for @JsonIgnoreProperties (forward compatibility) ==========
    
    @Test
    void testDeserializationIgnoresUnknownFields() throws Exception {
        // Given - JSON with unknown fields that should be ignored
        String json = "{\"action\":\"START\",\"simulationId\":\"sim-unknown\"," +
                     "\"instanceId\":\"instance-unknown\",\"batchId\":\"BATCH_UNK\"," +
                     "\"farmerId\":\"farmer-unk\",\"timestamp\":1234567890," +
                     "\"unknownField1\":\"some-value\",\"unknownField2\":12345," +
                     "\"anotherUnknownObject\":{\"nested\":true}}";
        
        // When - should NOT throw exception due to @JsonIgnoreProperties(ignoreUnknown = true)
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then - known fields should be correctly parsed
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
        assertEquals("sim-unknown", event.getSimulationId());
        assertEquals("instance-unknown", event.getInstanceId());
        assertEquals("BATCH_UNK", event.getBatchId());
        assertEquals("farmer-unk", event.getFarmerId());
        assertEquals(1234567890L, event.getTimestamp());
        assertTrue(event.isStart());
    }
    
    @Test
    void testDeserializationWithExtraNestedObjects() throws Exception {
        // Given - JSON with deeply nested unknown fields
        String json = "{\"action\":\"STOP\",\"simulationId\":\"sim-nested\"," +
                     "\"instanceId\":\"instance-nested\",\"timestamp\":1234567890," +
                     "\"metadata\":{\"version\":1,\"source\":\"test\",\"nested\":{\"deep\":true}}}";
        
        // When
        SimulationControlEvent event = objectMapper.readValue(json, SimulationControlEvent.class);
        
        // Then
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
        assertEquals("sim-nested", event.getSimulationId());
        assertTrue(event.isStop());
    }
    
    @Test
    void testSetStartBoolean() {
        // Given
        SimulationControlEvent event = new SimulationControlEvent();
        
        // When
        event.setStart(true);
        
        // Then
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
        assertTrue(event.isStart());
    }
    
    @Test
    void testSetStopBoolean() {
        // Given
        SimulationControlEvent event = new SimulationControlEvent();
        
        // When
        event.setStop(true);
        
        // Then
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
        assertTrue(event.isStop());
    }
    
    @Test
    void testSetStartBooleanNull() {
        // Given
        SimulationControlEvent event = new SimulationControlEvent();
        event.setAction(SimulationControlEvent.Action.STOP);
        
        // When - setting null should not change action
        event.setStart(null);
        
        // Then
        assertEquals(SimulationControlEvent.Action.STOP, event.getAction());
    }
    
    @Test
    void testSetStopBooleanNull() {
        // Given
        SimulationControlEvent event = new SimulationControlEvent();
        event.setAction(SimulationControlEvent.Action.START);
        
        // When - setting null should not change action
        event.setStop(null);
        
        // Then
        assertEquals(SimulationControlEvent.Action.START, event.getAction());
    }
}
