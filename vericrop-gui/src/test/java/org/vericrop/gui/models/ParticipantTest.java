package org.vericrop.gui.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for Participant model and its business logic.
 * Tests participant online status, connection info, and data integrity.
 */
public class ParticipantTest {
    
    private Participant participant;
    
    @BeforeEach
    public void setUp() {
        participant = new Participant(1L, "instance-123", "Test User");
    }
    
    @Test
    public void testParticipantCreation() {
        assertNotNull(participant);
        assertEquals(1L, participant.getUserId());
        assertEquals("instance-123", participant.getInstanceId());
        assertEquals("Test User", participant.getDisplayName());
        assertEquals("active", participant.getStatus());
    }
    
    @Test
    public void testIsActive() {
        participant.setStatus("active");
        assertTrue(participant.isActive());
        
        participant.setStatus("inactive");
        assertFalse(participant.isActive());
        
        participant.setStatus("disconnected");
        assertFalse(participant.isActive());
    }
    
    @Test
    public void testIsOnline_RecentActivity() {
        // Set last seen to now (very recent)
        participant.setLastSeen(LocalDateTime.now());
        participant.setStatus("active");
        
        assertTrue(participant.isOnline(), "Participant with recent activity should be online");
    }
    
    @Test
    public void testIsOnline_OldActivity() {
        // Set last seen to 10 minutes ago (beyond 5-minute threshold)
        participant.setLastSeen(LocalDateTime.now().minusMinutes(10));
        participant.setStatus("active");
        
        assertFalse(participant.isOnline(), "Participant with old activity should not be online");
    }
    
    @Test
    public void testIsOnline_InactiveStatus() {
        // Even with recent activity, inactive status means not online
        participant.setLastSeen(LocalDateTime.now());
        participant.setStatus("inactive");
        
        assertFalse(participant.isOnline(), "Inactive participant should not be online");
    }
    
    @Test
    public void testUpdateLastSeen() {
        LocalDateTime beforeUpdate = participant.getLastSeen();
        
        // Wait a tiny bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        participant.updateLastSeen();
        LocalDateTime afterUpdate = participant.getLastSeen();
        
        assertNotNull(afterUpdate);
        if (beforeUpdate != null) {
            assertTrue(afterUpdate.isAfter(beforeUpdate), "Updated last seen should be more recent");
        }
    }
    
    @Test
    public void testConnectionInfo() {
        Map<String, Object> connectionInfo = new HashMap<>();
        connectionInfo.put("host", "localhost");
        connectionInfo.put("port", 8080);
        
        participant.setConnectionInfo(connectionInfo);
        
        assertEquals(connectionInfo, participant.getConnectionInfo());
        assertEquals("localhost", participant.getConnectionInfo().get("host"));
        assertEquals(8080, participant.getConnectionInfo().get("port"));
    }
    
    @Test
    public void testConnectionEndpoint() {
        // Test setting endpoint via helper method
        participant.setConnectionEndpoint("http://localhost:8080/api");
        
        assertEquals("http://localhost:8080/api", participant.getConnectionEndpoint());
        assertNotNull(participant.getConnectionInfo());
        assertTrue(participant.getConnectionInfo().containsKey("endpoint"));
    }
    
    @Test
    public void testConnectionEndpoint_NullInfo() {
        participant.setConnectionInfo(null);
        participant.setConnectionEndpoint("http://example.com");
        
        assertNotNull(participant.getConnectionInfo());
        assertEquals("http://example.com", participant.getConnectionEndpoint());
    }
    
    @Test
    public void testGuiVersion() {
        participant.setGuiVersion("1.0.0");
        assertEquals("1.0.0", participant.getGuiVersion());
    }
    
    @Test
    public void testDisplayNameAndUsername() {
        participant.setDisplayName("John Doe");
        participant.setUsername("johndoe");
        participant.setUserRole("FARMER");
        
        assertEquals("John Doe", participant.getDisplayName());
        assertEquals("johndoe", participant.getUsername());
        assertEquals("FARMER", participant.getUserRole());
    }
}
