package org.vericrop.gui.dao;

import org.junit.jupiter.api.Test;
import org.vericrop.gui.models.Message;
import org.vericrop.gui.models.User;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageDao and UserDao messaging enhancements.
 * Tests model classes and utility methods without requiring database connection.
 */
class MessagingEnhancementsTest {
    
    @Test
    void testMessageModelWithUsernames() {
        // Create a message with usernames
        Message message = new Message();
        message.setId(1L);
        message.setSenderId(100L);
        message.setRecipientId(200L);
        message.setSubject("Test Subject");
        message.setBody("Test message body");
        message.setSentAt(LocalDateTime.now());
        message.setSenderUsername("producer_demo");
        message.setRecipientUsername("consumer_demo");
        
        // Verify all fields
        assertEquals(1L, message.getId());
        assertEquals(100L, message.getSenderId());
        assertEquals(200L, message.getRecipientId());
        assertEquals("Test Subject", message.getSubject());
        assertEquals("Test message body", message.getBody());
        assertEquals("producer_demo", message.getSenderUsername());
        assertEquals("consumer_demo", message.getRecipientUsername());
        assertFalse(message.isRead());
        assertFalse(message.isDeletedBySender());
        assertFalse(message.isDeletedByRecipient());
    }
    
    @Test
    void testMessageConstructorWithIds() {
        Message message = new Message(10L, 20L, "Subject", "Body");
        
        assertEquals(10L, message.getSenderId());
        assertEquals(20L, message.getRecipientId());
        assertEquals("Subject", message.getSubject());
        assertEquals("Body", message.getBody());
        assertNotNull(message.getSentAt());
        assertFalse(message.isRead());
    }
    
    @Test
    void testMessageMarkAsRead() {
        Message message = new Message(1L, 2L, "Test", "Body");
        
        assertFalse(message.isRead());
        assertNull(message.getReadAt());
        
        message.markAsRead();
        
        assertTrue(message.isRead());
        assertNotNull(message.getReadAt());
    }
    
    @Test
    void testMessageBodyPreview() {
        // Test short body
        Message shortMessage = new Message(1L, 2L, "Test", "Short body");
        assertEquals("Short body", shortMessage.getBodyPreview());
        
        // Test long body (over 100 characters)
        String longBody = "This is a very long message body that exceeds one hundred characters and should be truncated with ellipsis at the end of the preview text.";
        Message longMessage = new Message(1L, 2L, "Test", longBody);
        
        String preview = longMessage.getBodyPreview();
        // Preview is 97 chars + "..." = exactly 100 chars
        assertEquals(100, preview.length());
        assertTrue(preview.endsWith("..."));
        
        // Test null body
        Message nullMessage = new Message();
        assertEquals("", nullMessage.getBodyPreview());
    }
    
    @Test
    void testUserModelWithRole() {
        User user = new User("test_user", "test@example.com", "Test User", "PRODUCER");
        
        assertEquals("test_user", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", user.getFullName());
        assertEquals("PRODUCER", user.getRole());
        assertEquals("active", user.getStatus());
    }
    
    @Test
    void testUserHasRole() {
        User user = new User("test_user", "test@example.com", "Test User", "ADMIN");
        
        assertTrue(user.hasRole("ADMIN"));
        assertTrue(user.hasRole("admin")); // Case insensitive
        assertTrue(user.hasRole("Admin"));
        assertFalse(user.hasRole("PRODUCER"));
        assertFalse(user.hasRole("LOGISTICS"));
    }
    
    @Test
    void testUserIsActive() {
        User activeUser = new User("test_user", "test@example.com", "Test User", "PRODUCER");
        assertTrue(activeUser.isActive());
        
        User inactiveUser = new User("test_user", "test@example.com", "Test User", "PRODUCER");
        inactiveUser.setStatus("inactive");
        assertFalse(inactiveUser.isActive());
    }
    
    @Test
    void testUserLocked() {
        User user = new User("test_user", "test@example.com", "Test User", "PRODUCER");
        
        // Initially not locked
        assertFalse(user.isLocked());
        
        // Set lock until future time
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        assertTrue(user.isLocked());
        
        // Set lock until past time
        user.setLockedUntil(LocalDateTime.now().minusHours(1));
        assertFalse(user.isLocked());
    }
    
    @Test
    void testMessageToString() {
        Message message = new Message(10L, 20L, "Test Subject", "Test Body");
        message.setId(1L);
        message.setSenderUsername("sender");
        message.setRecipientUsername("recipient");
        
        String toString = message.toString();
        
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("senderId=10"));
        assertTrue(toString.contains("recipientId=20"));
        assertTrue(toString.contains("senderUsername='sender'"));
        assertTrue(toString.contains("recipientUsername='recipient'"));
        assertTrue(toString.contains("subject='Test Subject'"));
    }
    
    @Test
    void testUserToString() {
        User user = new User("test_user", "test@example.com", "Test User", "PRODUCER");
        user.setId(1L);
        
        String toString = user.toString();
        
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("username='test_user'"));
        assertTrue(toString.contains("email='test@example.com'"));
        assertTrue(toString.contains("fullName='Test User'"));
        assertTrue(toString.contains("role='PRODUCER'"));
        assertTrue(toString.contains("status='active'"));
    }
}
