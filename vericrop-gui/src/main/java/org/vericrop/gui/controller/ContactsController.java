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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for contacts/participants management.
 * Provides endpoints for discovering and interacting with other GUI participants.
 */
@RestController
@RequestMapping("/api/contacts")
@CrossOrigin(origins = "*")
public class ContactsController {
    private static final Logger logger = LoggerFactory.getLogger(ContactsController.class);
    
    private final ParticipantDao participantDao;
    private final MessageDao messageDao;
    
    public ContactsController() {
        ApplicationContext appContext = ApplicationContext.getInstance();
        this.participantDao = appContext.getParticipantDao();
        this.messageDao = appContext.getMessageDao();
        logger.info("ContactsController initialized");
    }
    
    /**
     * GET /api/contacts
     * List all active contacts/participants
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listContacts(
            @RequestParam(required = false) String excludeInstanceId) {
        
        try {
            List<Participant> participants = participantDao.getActiveParticipants(excludeInstanceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", participants.size());
            response.put("contacts", participants);
            
            logger.debug("Retrieved {} active contacts", participants.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve contacts", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve contacts");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/contacts/{id}
     * Get contact details by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getContact(@PathVariable Long id) {
        try {
            Optional<Participant> participant = participantDao.findById(id);
            
            if (participant.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Contact not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("contact", participant.get());
            
            logger.debug("Retrieved contact details for ID: {}", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve contact details", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve contact details");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * POST /api/contacts/register
     * Register or update current participant
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerParticipant(@RequestBody Participant participant) {
        try {
            if (participant.getUserId() == null || participant.getInstanceId() == null || 
                participant.getDisplayName() == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Missing required fields: userId, instanceId, displayName");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            Participant registered = participantDao.registerParticipant(participant);
            
            if (registered == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Failed to register participant");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("participant", registered);
            
            logger.info("Participant registered: {} (instance: {})", 
                       registered.getDisplayName(), registered.getInstanceId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to register participant", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * PUT /api/contacts/heartbeat/{instanceId}
     * Update last seen timestamp for a participant
     */
    @PutMapping("/heartbeat/{instanceId}")
    public ResponseEntity<Map<String, Object>> updateHeartbeat(@PathVariable String instanceId) {
        try {
            boolean updated = participantDao.updateLastSeen(instanceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", updated);
            
            if (!updated) {
                response.put("error", "Participant not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update heartbeat", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to update heartbeat");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * PUT /api/contacts/status/{instanceId}
     * Update participant status
     */
    @PutMapping("/status/{instanceId}")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String instanceId,
            @RequestParam String status) {
        
        try {
            boolean updated = participantDao.updateStatus(instanceId, status);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", updated);
            
            if (!updated) {
                response.put("error", "Participant not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to update status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
