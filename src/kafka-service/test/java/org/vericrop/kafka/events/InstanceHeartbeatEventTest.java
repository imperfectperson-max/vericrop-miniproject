package org.vericrop.kafka.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.vericrop.kafka.events.InstanceHeartbeatEvent.Role;

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
        assertEquals("UNKNOWN", event.getRole()); // Default role
        assertEquals(Role.UNKNOWN, event.getRoleEnum());
        assertTrue(event.isAlive());
        assertFalse(event.isShutdown());
        assertTrue(event.getTimestamp() > 0);
    }
    
    @Test
    void testAliveHeartbeatWithRole() {
        // Given
        String instanceId = "instance-producer";
        
        // When
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId, Role.PRODUCER);
        
        // Then
        assertEquals(instanceId, event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals("PRODUCER", event.getRole());
        assertEquals(Role.PRODUCER, event.getRoleEnum());
        assertTrue(event.isAlive());
    }
    
    @Test
    void testAliveHeartbeatWithFullDetails() {
        // Given
        String instanceId = "instance-logistics";
        String host = "localhost";
        int port = 8080;
        
        // When
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId, Role.LOGISTICS, host, port);
        
        // Then
        assertEquals(instanceId, event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals("LOGISTICS", event.getRole());
        assertEquals(Role.LOGISTICS, event.getRoleEnum());
        assertEquals(host, event.getHost());
        assertEquals(port, event.getPort());
        assertTrue(event.isAlive());
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
    void testShutdownHeartbeatWithRole() {
        // Given
        String instanceId = "instance-consumer";
        
        // When
        InstanceHeartbeatEvent event = InstanceHeartbeatEvent.shutdown(instanceId, Role.CONSUMER);
        
        // Then
        assertEquals(instanceId, event.getInstanceId());
        assertEquals("SHUTDOWN", event.getStatus());
        assertEquals("CONSUMER", event.getRole());
        assertEquals(Role.CONSUMER, event.getRoleEnum());
        assertFalse(event.isAlive());
        assertTrue(event.isShutdown());
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent("instance-789", Role.PRODUCER);
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"instanceId\":\"instance-789\""));
        assertTrue(json.contains("\"status\":\"ALIVE\""));
        assertTrue(json.contains("\"role\":\"PRODUCER\""));
    }
    
    @Test
    void testJsonSerializationWithHostAndPort() throws Exception {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent("instance-789", Role.LOGISTICS, "192.168.1.100", 9090);
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"instanceId\":\"instance-789\""));
        assertTrue(json.contains("\"role\":\"LOGISTICS\""));
        assertTrue(json.contains("\"host\":\"192.168.1.100\""));
        assertTrue(json.contains("\"port\":9090"));
    }
    
    @Test
    void testJsonDeserialization() throws Exception {
        // Given - JSON without role field
        String json = "{\"instanceId\":\"instance-xyz\",\"status\":\"ALIVE\",\"timestamp\":1234567890}";
        
        // When
        InstanceHeartbeatEvent event = objectMapper.readValue(json, InstanceHeartbeatEvent.class);
        
        // Then
        assertEquals("instance-xyz", event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals(1234567890L, event.getTimestamp());
        // When role is not specified in JSON, it should be set to UNKNOWN by default constructor
        assertEquals("UNKNOWN", event.getRole());
        assertEquals(Role.UNKNOWN, event.getRoleEnum());
    }
    
    @Test
    void testJsonDeserializationWithRole() throws Exception {
        // Given
        String json = "{\"instanceId\":\"instance-xyz\",\"status\":\"ALIVE\",\"role\":\"CONSUMER\",\"timestamp\":1234567890}";
        
        // When
        InstanceHeartbeatEvent event = objectMapper.readValue(json, InstanceHeartbeatEvent.class);
        
        // Then
        assertEquals("instance-xyz", event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals("CONSUMER", event.getRole());
        assertEquals(Role.CONSUMER, event.getRoleEnum());
    }
    
    @Test
    void testJsonDeserializationWithHostAndPort() throws Exception {
        // Given
        String json = "{\"instanceId\":\"instance-xyz\",\"status\":\"ALIVE\",\"role\":\"PRODUCER\",\"host\":\"10.0.0.1\",\"port\":8888,\"timestamp\":1234567890}";
        
        // When
        InstanceHeartbeatEvent event = objectMapper.readValue(json, InstanceHeartbeatEvent.class);
        
        // Then
        assertEquals("instance-xyz", event.getInstanceId());
        assertEquals("PRODUCER", event.getRole());
        assertEquals("10.0.0.1", event.getHost());
        assertEquals(8888, event.getPort());
    }
    
    @Test
    void testJsonDeserializationWithUnknownFields() throws Exception {
        // Given - JSON with extra "alive" and "shutdown" fields that should be ignored
        String json = "{\"alive\":true,\"shutdown\":false,\"instanceId\":\"496e12f7-7d50-4082-bce8-05f349421eb1\",\"timestamp\":1764336517507,\"status\":\"ALIVE\"}";
        
        // When
        InstanceHeartbeatEvent event = objectMapper.readValue(json, InstanceHeartbeatEvent.class);
        
        // Then - Known fields should be parsed correctly
        assertEquals("496e12f7-7d50-4082-bce8-05f349421eb1", event.getInstanceId());
        assertEquals("ALIVE", event.getStatus());
        assertEquals(1764336517507L, event.getTimestamp());
        assertTrue(event.isAlive());
        assertFalse(event.isShutdown());
    }
    
    @Test
    void testToString() {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent("instance-abc", Role.PRODUCER);
        
        // When
        String str = event.toString();
        
        // Then
        assertNotNull(str);
        assertTrue(str.contains("instance-abc"));
        assertTrue(str.contains("ALIVE"));
        assertTrue(str.contains("PRODUCER"));
    }
    
    @Test
    void testDefaultConstructor() {
        // When
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent();
        
        // Then
        assertEquals("ALIVE", event.getStatus());
        assertEquals("UNKNOWN", event.getRole());
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
        event.setRole("LOGISTICS");
        event.setHost("example.com");
        event.setPort(3000);
        
        // Then
        assertEquals("test-instance", event.getInstanceId());
        assertEquals("SHUTDOWN", event.getStatus());
        assertEquals(9876543210L, event.getTimestamp());
        assertEquals("LOGISTICS", event.getRole());
        assertEquals(Role.LOGISTICS, event.getRoleEnum());
        assertEquals("example.com", event.getHost());
        assertEquals(3000, event.getPort());
    }
    
    @Test
    void testGetRoleEnumWithInvalidRole() {
        // Given
        InstanceHeartbeatEvent event = new InstanceHeartbeatEvent();
        event.setRole("INVALID_ROLE");
        
        // When
        Role role = event.getRoleEnum();
        
        // Then - Should return UNKNOWN for invalid role
        assertEquals(Role.UNKNOWN, role);
    }
    
    @Test
    void testRoleEnumValues() {
        // Verify all expected role values exist
        assertEquals("PRODUCER", Role.PRODUCER.name());
        assertEquals("LOGISTICS", Role.LOGISTICS.name());
        assertEquals("CONSUMER", Role.CONSUMER.name());
        assertEquals("UNKNOWN", Role.UNKNOWN.name());
    }
}
