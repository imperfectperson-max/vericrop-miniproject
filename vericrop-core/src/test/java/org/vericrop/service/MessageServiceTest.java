package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageService (in-memory mode).
 */
public class MessageServiceTest {
    
    private MessageService service;
    
    @BeforeEach
    public void setUp() {
        // Use in-memory mode for tests (no file persistence)
        service = new MessageService(false);
    }
    
    @AfterEach
    public void tearDown() {
        service.clearAll();
    }
    
    @Test
    public void testSendMessage() {
        Message message = new Message(
            "farmer",
            "farmer_001",
            "supplier",
            "supplier_001",
            "Batch Ready",
            "Batch ABC123 is ready for pickup"
        );
        
        Message sent = service.sendMessage(message);
        
        assertNotNull(sent);
        assertNotNull(sent.getMessageId());
        assertEquals("farmer", sent.getSenderRole());
        assertEquals("supplier", sent.getRecipientRole());
        assertEquals("sent", sent.getStatus());
    }
    
    @Test
    public void testSendMessage_NullMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.sendMessage(null);
        });
    }
    
    @Test
    public void testSendMessage_MissingSender() {
        Message message = new Message();
        message.setRecipientRole("supplier");
        message.setSubject("Test");
        message.setContent("Test content");
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.sendMessage(message);
        });
    }
    
    @Test
    public void testGetMessagesForRecipient() {
        // Send messages to different recipients
        Message msg1 = new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Subject 1", "Content 1"
        );
        service.sendMessage(msg1);
        
        Message msg2 = new Message(
            "supplier", "supplier_001", "logistics", "logistics_001",
            "Subject 2", "Content 2"
        );
        service.sendMessage(msg2);
        
        Message msg3 = new Message(
            "farmer", "farmer_002", "supplier", "supplier_001",
            "Subject 3", "Content 3"
        );
        service.sendMessage(msg3);
        
        // Get messages for supplier_001
        List<Message> messages = service.getMessagesForRecipient("supplier", "supplier_001");
        
        assertEquals(2, messages.size());
        assertTrue(messages.stream().anyMatch(m -> m.getSubject().equals("Subject 1")));
        assertTrue(messages.stream().anyMatch(m -> m.getSubject().equals("Subject 3")));
    }
    
    @Test
    public void testGetMessagesForRecipient_Broadcast() {
        // Send a broadcast message (null recipientId)
        Message broadcast = new Message(
            "logistics", "logistics_001", "all", null,
            "Broadcast", "Message to all"
        );
        service.sendMessage(broadcast);
        
        // All roles should receive it
        List<Message> farmerMessages = service.getMessagesForRecipient("farmer", "farmer_001");
        List<Message> supplierMessages = service.getMessagesForRecipient("supplier", "supplier_001");
        
        assertEquals(1, farmerMessages.size());
        assertEquals(1, supplierMessages.size());
        assertEquals("Broadcast", farmerMessages.get(0).getSubject());
    }
    
    @Test
    public void testGetMessagesBySender() {
        Message msg1 = new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Subject 1", "Content 1"
        );
        service.sendMessage(msg1);
        
        Message msg2 = new Message(
            "farmer", "farmer_001", "logistics", "logistics_001",
            "Subject 2", "Content 2"
        );
        service.sendMessage(msg2);
        
        Message msg3 = new Message(
            "supplier", "supplier_001", "farmer", "farmer_001",
            "Subject 3", "Content 3"
        );
        service.sendMessage(msg3);
        
        List<Message> messages = service.getMessagesBySender("farmer", "farmer_001");
        
        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(m -> m.getSenderRole().equals("farmer")));
        assertTrue(messages.stream().allMatch(m -> m.getSenderId().equals("farmer_001")));
    }
    
    @Test
    public void testGetMessagesByBatch() {
        Message msg1 = new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Batch Update", "Content"
        );
        msg1.setBatchId("BATCH_001");
        service.sendMessage(msg1);
        
        Message msg2 = new Message(
            "supplier", "supplier_001", "logistics", "logistics_001",
            "Shipment Info", "Content"
        );
        msg2.setBatchId("BATCH_001");
        service.sendMessage(msg2);
        
        Message msg3 = new Message(
            "farmer", "farmer_002", "supplier", "supplier_002",
            "Other Batch", "Content"
        );
        msg3.setBatchId("BATCH_002");
        service.sendMessage(msg3);
        
        List<Message> messages = service.getMessagesByBatch("BATCH_001");
        
        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(m -> "BATCH_001".equals(m.getBatchId())));
    }
    
    @Test
    public void testGetMessagesByShipment() {
        Message msg1 = new Message(
            "logistics", "logistics_001", "supplier", "supplier_001",
            "In Transit", "Content"
        );
        msg1.setShipmentId("SHIP_001");
        service.sendMessage(msg1);
        
        Message msg2 = new Message(
            "logistics", "logistics_001", "consumer", "consumer_001",
            "Delivered", "Content"
        );
        msg2.setShipmentId("SHIP_001");
        service.sendMessage(msg2);
        
        List<Message> messages = service.getMessagesByShipment("SHIP_001");
        
        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(m -> "SHIP_001".equals(m.getShipmentId())));
    }
    
    @Test
    public void testMarkAsRead() {
        Message message = new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Test", "Content"
        );
        Message sent = service.sendMessage(message);
        
        assertEquals("sent", sent.getStatus());
        
        service.markAsRead(sent.getMessageId());
        
        List<Message> messages = service.getAllMessages();
        Message updated = messages.stream()
            .filter(m -> m.getMessageId().equals(sent.getMessageId()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(updated);
        assertEquals("read", updated.getStatus());
    }
    
    @Test
    public void testDeleteMessage() {
        Message message = new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Test", "Content"
        );
        Message sent = service.sendMessage(message);
        
        assertEquals(1, service.getAllMessages().size());
        
        boolean deleted = service.deleteMessage(sent.getMessageId());
        
        assertTrue(deleted);
        assertEquals(0, service.getAllMessages().size());
    }
    
    @Test
    public void testDeleteMessage_NotFound() {
        boolean deleted = service.deleteMessage("non_existent_id");
        assertFalse(deleted);
    }
    
    @Test
    public void testClearAll() {
        service.sendMessage(new Message(
            "farmer", "farmer_001", "supplier", "supplier_001",
            "Test 1", "Content"
        ));
        service.sendMessage(new Message(
            "supplier", "supplier_001", "logistics", "logistics_001",
            "Test 2", "Content"
        ));
        
        assertEquals(2, service.getAllMessages().size());
        
        service.clearAll();
        
        assertEquals(0, service.getAllMessages().size());
    }
}
