package org.vericrop.service;

import org.vericrop.dto.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for managing messages between supply chain actors.
 * Provides in-memory storage with optional file persistence.
 */
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private static final String MESSAGES_FILE = "ledger/messages.jsonl";
    
    private final List<Message> messages;
    private final ObjectMapper mapper;
    private final boolean persistEnabled;
    
    public MessageService() {
        this(true);
    }
    
    public MessageService(boolean persistEnabled) {
        this.messages = new ArrayList<>();
        this.mapper = new ObjectMapper();
        this.persistEnabled = persistEnabled;
        
        if (persistEnabled) {
            loadMessages();
        }
        
        logger.info("MessageService initialized (persistence: {})", persistEnabled);
    }
    
    /**
     * Send a message.
     */
    public Message sendMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        // Validate required fields
        if (message.getSenderRole() == null || message.getSenderId() == null) {
            throw new IllegalArgumentException("Sender role and ID are required");
        }
        
        if (message.getRecipientRole() == null) {
            throw new IllegalArgumentException("Recipient role is required");
        }
        
        // Set status if not set
        if (message.getStatus() == null) {
            message.setStatus("sent");
        }
        
        messages.add(message);
        
        if (persistEnabled) {
            persistMessage(message);
        }
        
        logger.info("Message sent: {} from {} to {}", 
                message.getMessageId(), message.getSenderRole(), message.getRecipientRole());
        
        return message;
    }
    
    /**
     * Get messages for a specific recipient.
     */
    public List<Message> getMessagesForRecipient(String recipientRole, String recipientId) {
        return messages.stream()
                .filter(m -> {
                    // Match role and either specific ID or broadcast (null recipientId)
                    boolean roleMatches = m.getRecipientRole().equals(recipientRole) || 
                                         m.getRecipientRole().equals("all");
                    boolean idMatches = m.getRecipientId() == null || 
                                       m.getRecipientId().equals(recipientId);
                    return roleMatches && idMatches;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get messages sent by a specific sender.
     */
    public List<Message> getMessagesBySender(String senderRole, String senderId) {
        return messages.stream()
                .filter(m -> m.getSenderRole().equals(senderRole) && 
                           m.getSenderId().equals(senderId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get messages related to a batch.
     */
    public List<Message> getMessagesByBatch(String batchId) {
        return messages.stream()
                .filter(m -> batchId.equals(m.getBatchId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get messages related to a shipment.
     */
    public List<Message> getMessagesByShipment(String shipmentId) {
        return messages.stream()
                .filter(m -> shipmentId.equals(m.getShipmentId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get all messages (for admin/debugging).
     */
    public List<Message> getAllMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * Mark a message as read.
     */
    public void markAsRead(String messageId) {
        messages.stream()
                .filter(m -> m.getMessageId().equals(messageId))
                .findFirst()
                .ifPresent(m -> {
                    m.setStatus("read");
                    if (persistEnabled) {
                        // Re-persist all messages (simple approach)
                        rewriteMessagesFile();
                    }
                });
    }
    
    /**
     * Delete a message.
     */
    public boolean deleteMessage(String messageId) {
        boolean removed = messages.removeIf(m -> m.getMessageId().equals(messageId));
        
        if (removed && persistEnabled) {
            rewriteMessagesFile();
        }
        
        return removed;
    }
    
    /**
     * Clear all messages (for testing).
     */
    public void clearAll() {
        messages.clear();
        if (persistEnabled) {
            try {
                Path path = Paths.get(MESSAGES_FILE);
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                logger.error("Failed to delete messages file", e);
            }
        }
    }
    
    /**
     * Persist a single message to file.
     */
    private void persistMessage(Message message) {
        try {
            Path path = Paths.get(MESSAGES_FILE);
            Files.createDirectories(path.getParent());
            
            String json = mapper.writeValueAsString(message) + "\n";
            Files.write(path, json.getBytes(), 
                       StandardOpenOption.CREATE, 
                       StandardOpenOption.APPEND);
                       
        } catch (IOException e) {
            logger.error("Failed to persist message", e);
        }
    }
    
    /**
     * Load messages from file.
     */
    private void loadMessages() {
        try {
            Path path = Paths.get(MESSAGES_FILE);
            if (!Files.exists(path)) {
                logger.info("Messages file not found, starting fresh");
                return;
            }
            
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                try {
                    Message message = mapper.readValue(line, Message.class);
                    messages.add(message);
                } catch (IOException e) {
                    logger.warn("Failed to parse message line: {}", line, e);
                }
            }
            
            logger.info("Loaded {} messages from file", messages.size());
            
        } catch (IOException e) {
            logger.error("Failed to load messages", e);
        }
    }
    
    /**
     * Rewrite entire messages file (for updates/deletes).
     */
    private void rewriteMessagesFile() {
        try {
            Path path = Paths.get(MESSAGES_FILE);
            Files.createDirectories(path.getParent());
            
            // Write all messages
            List<String> lines = messages.stream()
                    .map(m -> {
                        try {
                            return mapper.writeValueAsString(m);
                        } catch (IOException e) {
                            logger.error("Failed to serialize message", e);
                            return null;
                        }
                    })
                    .filter(line -> line != null)
                    .collect(Collectors.toList());
            
            Files.write(path, lines);
            
        } catch (IOException e) {
            logger.error("Failed to rewrite messages file", e);
        }
    }
}
