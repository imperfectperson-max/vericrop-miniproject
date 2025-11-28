package org.vericrop.kafka.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstanceHeartbeatEvent.
 */
class InstanceHeartbeatEventTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void testAliveHeartbeat() {
        // Given
        String instanceId = "instance-123";
        
        // When
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId);
        
        // Then
        assertEquals(instanceId, event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertTrue(event.isAlive());
        assertFalse(event.isShutdown());
        assertTrue(event.getTimestamp() > 0);
    }
    
    @Test
    void testShutdownHeartbeat() {
        // Given
        String instanceId = "instance-456";
        
        // When
        InstanceHeartbeatEvent event = InstanceHeartbeatEvent.shutdown(instanceId);
        
        // Then
        assertEquals(instanceId, event.getInstanceId());
        assertEquals("SHUTDOWN", event.getStatus());
        assertFalse(event.isAlive());
        assertTrue(event.isShutdown());
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent("instance-789");
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"instanceId\":\"instance-789\""));
        assertTrue(json.contains("\"status\":\"ALIVE\""));
    }
    
    @Test
    void testJsonDeserialization() throws Exception {
        // Given
        String json = "{\"instanceId\":\"instance-xyz\",\"status\":\"ALIVE\",\"timestamp\":1234567890}";
        
        // When
        InstanceHeartbeatEvent event = objectMapper.readValue(json, InstanceHeartbeatEvent.class);
        
        // Then
        assertEquals("instance-xyz", event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals(1234567890L, event.getTimestamp());
    }
    
    @Test
    void testToString() {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent("instance-abc");
        
        // When
        String str = event.toString();
        
        // Then
        assertNotNull(str);
        assertTrue(str.contains("instance-abc"));
        assertTrue(str.contains("ALIVE"));
    }
    
    @Test
    void testDefaultConstructor() {
        // When
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent();
        
        // Then
        assertEquals("ALIVE", event.getStatus());
        assertTrue(event.getTimestamp() > 0);
    }
    
    @Test
    void testSetters() {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent();
        
        // When
        event.setInstanceId("test-instance");
        event.setStatus("SHUTDOWN");
        event.setTimestamp(9876543210L);
        
        // Then
        assertEquals("test-instance", event.getInstanceId());
        assertEquals("SHUTDOWN", event.getStatus());
        assertEquals(9876543210L, event.getTimestamp());
    }
}
