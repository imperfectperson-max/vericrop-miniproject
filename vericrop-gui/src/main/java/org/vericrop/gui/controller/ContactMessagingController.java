package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.app.ApplicationContext;
import org.vericrop.gui.dao.MessageDao;
import org.vericrop.gui.dao.ParticipantDao;
import org.vericrop.gui.models.Message;
import org.vericrop.gui.models.Participant;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for contact-based messaging.
 * Provides endpoints for sending and retrieving messages between participants.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContactMessagingController {
    private static final Logger logger = LoggerFactory.getLogger(ContactMessagingController.class);
    
    private final MessageDao messageDao;
    private final ParticipantDao participantDao;
    
    public ContactMessagingController() {
        ApplicationContext appContext = ApplicationContext.getInstance();
        this.messageDao = appContext.getMessageDao();
        this.participantDao = appContext.getParticipantDao();
        logger.info("ContactMessagingController initialized");
    }
    
    /**
     * POST /api/messages
     * Send a message to a contact
     * Payload: { to_contact_id, subject, body }
     */
    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> payload) {
        try {
            // Extract required fields
            Long toContactId = payload.get("to_contact_id") != null 
                ? ((Number) payload.get("to_contact_id")).longValue() 
                : null;
            String subject = (String) payload.get("subject");
            String body = (String) payload.get("body");
            Long fromUserId = payload.get("from_user_id") != null 
                ? ((Number) payload.get("from_user_id")).longValue() 
                : null;
            
            // Validate required fields
            if (toContactId == null || body == null || fromUserId == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Missing required fields: to_contact_id, body, from_user_id");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Get the contact/participant to get their user ID
            Optional<Participant> toParticipant = participantDao.findById(toContactId);
            if (toParticipant.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Contact not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Long recipientUserId = toParticipant.get().getUserId();
            
            // Set default subject if not provided
            if (subject == null || subject.trim().isEmpty()) {
                subject = "Message from " + fromUserId;
            }
            
            // Send the message using MessageDao
            Message sentMessage = messageDao.sendMessage(fromUserId, recipientUserId, subject, body);
            
            if (sentMessage == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Failed to send message");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
            // TODO: If the contact has connection info, deliver the message in real-time
            // For now, messages are stored in database and retrieved via polling
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message_id", sentMessage.getId());
            response.put("sent_at", sentMessage.getSentAt());
            
            logger.info("Message sent from user {} to contact {} (user {})", 
                       fromUserId, toContactId, recipientUserId);
            
            return ResponseEntity.ok(response);
            
        } catch (ClassCastException e) {
            logger.warn("Invalid message payload format: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Invalid payload format");
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
     * GET /api/messages?contact_id={contactId}
     * Get messages to/from a specific contact
     * Note: Requires user_id param to identify current user
     */
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(name = "contact_id", required = false) Long contactId,
            @RequestParam(name = "user_id") Long userId) {
        
        try {
            List<Message> messages = new ArrayList<>();
            
            if (contactId != null) {
                // Get messages with specific contact
                Optional<Participant> contact = participantDao.findById(contactId);
                if (contact.isEmpty()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Contact not found");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                
                Long contactUserId = contact.get().getUserId();
                
                // Get messages sent to the contact
                List<Message> sentMessages = messageDao.getSentMessages(userId).stream()
                    .filter(m -> m.getRecipientId().equals(contactUserId))
                    .collect(Collectors.toList());
                
                // Get messages received from the contact
                List<Message> receivedMessages = messageDao.getInboxMessages(userId).stream()
                    .filter(m -> m.getSenderId().equals(contactUserId))
                    .collect(Collectors.toList());
                
                // Combine and sort by timestamp
                messages.addAll(sentMessages);
                messages.addAll(receivedMessages);
                messages.sort((m1, m2) -> m2.getSentAt().compareTo(m1.getSentAt()));
                
            } else {
                // Get all inbox messages if no contact specified
                messages = messageDao.getInboxMessages(userId);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", messages.size());
            response.put("messages", messages);
            
            logger.debug("Retrieved {} messages for user {}", messages.size(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve messages", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve messages");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * PUT /api/messages/{messageId}/read
     * Mark a message as read
     */
    @PutMapping("/messages/{messageId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long messageId) {
        try {
            boolean marked = messageDao.markAsRead(messageId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", marked);
            
            if (!marked) {
                response.put("error", "Message not found or already read");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to mark message as read", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to mark message as read");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
