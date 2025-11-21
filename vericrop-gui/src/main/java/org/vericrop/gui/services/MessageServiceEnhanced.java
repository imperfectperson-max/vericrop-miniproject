package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.dao.MessageDao;
import org.vericrop.gui.models.Message;
import org.vericrop.gui.models.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced MessageService that wraps MessageDao and provides additional functionality.
 * Handles message operations for all user types with role-based features.
 */
public class MessageServiceEnhanced {
    private static final Logger logger = LoggerFactory.getLogger(MessageServiceEnhanced.class);
    private static MessageServiceEnhanced instance;
    
    private MessageDao messageDao;
    private User currentUser;
    
    private MessageServiceEnhanced() {
        // Private constructor for singleton
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized MessageServiceEnhanced getInstance() {
        if (instance == null) {
            instance = new MessageServiceEnhanced();
        }
        return instance;
    }
    
    /**
     * Initialize with MessageDao
     */
    public void initialize(MessageDao messageDao) {
        this.messageDao = messageDao;
        logger.info("MessageServiceEnhanced initialized");
    }
    
    /**
     * Set current logged-in user
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        logger.info("Current user set for messaging: {}", user != null ? user.getUsername() : "null");
    }
    
    /**
     * Send a message
     */
    public Message sendMessage(Long recipientId, String subject, String body) {
        if (currentUser == null || currentUser.getId() == null) {
            logger.error("Cannot send message: No current user set");
            return null;
        }
        
        if (messageDao == null) {
            logger.error("MessageDao not initialized");
            return null;
        }
        
        return messageDao.sendMessage(currentUser.getId(), recipientId, subject, body);
    }
    
    /**
     * Get inbox messages for current user
     */
    public List<Message> getInbox() {
        if (currentUser == null || currentUser.getId() == null) {
            logger.warn("Cannot get inbox: No current user set");
            return List.of();
        }
        
        if (messageDao == null) {
            logger.error("MessageDao not initialized");
            return List.of();
        }
        
        return messageDao.getInboxMessages(currentUser.getId());
    }
    
    /**
     * Get sent messages for current user
     */
    public List<Message> getSentMessages() {
        if (currentUser == null || currentUser.getId() == null) {
            logger.warn("Cannot get sent messages: No current user set");
            return List.of();
        }
        
        if (messageDao == null) {
            logger.error("MessageDao not initialized");
            return List.of();
        }
        
        return messageDao.getSentMessages(currentUser.getId());
    }
    
    /**
     * Get unread message count for current user
     */
    public int getUnreadCount() {
        if (currentUser == null || currentUser.getId() == null) {
            return 0;
        }
        
        if (messageDao == null) {
            return 0;
        }
        
        return messageDao.getUnreadCount(currentUser.getId());
    }
    
    /**
     * Mark message as read
     */
    public boolean markAsRead(Long messageId) {
        if (messageDao == null) {
            return false;
        }
        
        return messageDao.markAsRead(messageId);
    }
    
    /**
     * Delete a message (soft delete)
     */
    public boolean deleteMessage(Long messageId) {
        if (messageDao == null || currentUser == null || currentUser.getId() == null) {
            return false;
        }
        
        return messageDao.deleteMessage(messageId, currentUser.getId());
    }
    
    /**
     * Get a specific message by ID
     */
    public Message getMessage(Long messageId) {
        if (messageDao == null) {
            return null;
        }
        
        return messageDao.findById(messageId).orElse(null);
    }
    
    /**
     * Get messages between two users (conversation)
     */
    public List<Message> getConversation(Long otherUserId) {
        if (currentUser == null || currentUser.getId() == null) {
            return List.of();
        }
        
        if (messageDao == null) {
            return List.of();
        }
        
        List<Message> sent = messageDao.getSentMessages(currentUser.getId()).stream()
            .filter(msg -> msg.getRecipientId().equals(otherUserId))
            .collect(Collectors.toList());
            
        List<Message> received = messageDao.getInboxMessages(currentUser.getId()).stream()
            .filter(msg -> msg.getSenderId().equals(otherUserId))
            .collect(Collectors.toList());
        
        sent.addAll(received);
        sent.sort((m1, m2) -> m1.getSentAt().compareTo(m2.getSentAt()));
        
        return sent;
    }
    
    /**
     * Check if service is initialized
     */
    public boolean isInitialized() {
        return messageDao != null;
    }
    
    /**
     * Check if user is logged in
     */
    public boolean isUserLoggedIn() {
        return currentUser != null && currentUser.getId() != null;
    }
    
    /**
     * Get current user
     */
    public User getCurrentUser() {
        return currentUser;
    }
}
