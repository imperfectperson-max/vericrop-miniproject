package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.dto.Message;
import org.vericrop.service.MessageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for messaging between supply chain actors.
 * Provides endpoints for sending and retrieving messages.
 */
@RestController
@RequestMapping("/api/v1/messaging")
@CrossOrigin(origins = "*")
public class MessagingController {
    private static final Logger logger = LoggerFactory.getLogger(MessagingController.class);
    
    private final MessageService messageService;
    
    public MessagingController() {
        this.messageService = new MessageService(true);  // Enable persistence
        logger.info("MessagingController initialized");
    }
    
    /**
     * POST /api/v1/messaging/send
     * Send a message.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Message message) {
        try {
            Message sent = messageService.sendMessage(message);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message_id", sent.getMessageId());
            response.put("timestamp", sent.getTimestamp());
            
            logger.info("Message sent: {} from {} to {}", 
                       sent.getMessageId(), sent.getSenderRole(), sent.getRecipientRole());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid message: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to send message", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/messaging/inbox
     * Get messages for a recipient.
     */
    @GetMapping("/inbox")
    public ResponseEntity<Map<String, Object>> getInbox(
            @RequestParam String recipientRole,
            @RequestParam(required = false) String recipientId) {
        
        try {
            List<Message> messages = messageService.getMessagesForRecipient(recipientRole, recipientId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", messages.size());
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve inbox", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve messages");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/messaging/sent
     * Get messages sent by a sender.
     */
    @GetMapping("/sent")
    public ResponseEntity<Map<String, Object>> getSent(
            @RequestParam String senderRole,
            @RequestParam String senderId) {
        
        try {
            List<Message> messages = messageService.getMessagesBySender(senderRole, senderId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", messages.size());
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve sent messages", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve messages");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/messaging/batch/{batchId}
     * Get messages related to a batch.
     */
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<Map<String, Object>> getMessagesByBatch(@PathVariable String batchId) {
        try {
            List<Message> messages = messageService.getMessagesByBatch(batchId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch_id", batchId);
            response.put("count", messages.size());
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve batch messages", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve messages");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/v1/messaging/shipment/{shipmentId}
     * Get messages related to a shipment.
     */
    @GetMapping("/shipment/{shipmentId}")
    public ResponseEntity<Map<String, Object>> getMessagesByShipment(@PathVariable String shipmentId) {
        try {
            List<Message> messages = messageService.getMessagesByShipment(shipmentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("shipment_id", shipmentId);
            response.put("count", messages.size());
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve shipment messages", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve messages");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * PUT /api/v1/messaging/read/{messageId}
     * Mark a message as read.
     */
    @PutMapping("/read/{messageId}")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable String messageId) {
        try {
            messageService.markAsRead(messageId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message_id", messageId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to mark message as read", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to update message");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * DELETE /api/v1/messaging/{messageId}
     * Delete a message.
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, Object>> deleteMessage(@PathVariable String messageId) {
        try {
            boolean deleted = messageService.deleteMessage(messageId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message_id", messageId);
            
            if (!deleted) {
                response.put("error", "Message not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to delete message", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to delete message");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
