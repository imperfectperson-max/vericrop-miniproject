package org.vericrop.gui.models;

import java.time.LocalDateTime;

/**
 * Message model representing a user-to-user message in the VeriCrop system.
 * Supports inbox, sent items, and read/unread tracking.
 */
public class Message {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String senderUsername;
    private String recipientUsername;
    private String subject;
    private String body;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private boolean isRead;
    private boolean deletedBySender;
    private boolean deletedByRecipient;
    
    // Default constructor
    public Message() {
        this.isRead = false;
        this.deletedBySender = false;
        this.deletedByRecipient = false;
    }
    
    // Constructor with required fields
    public Message(Long senderId, Long recipientId, String subject, String body) {
        this();
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.subject = subject;
        this.body = body;
        this.sentAt = LocalDateTime.now();
    }
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getSenderId() {
        return senderId;
    }
    
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }
    
    public Long getRecipientId() {
        return recipientId;
    }
    
    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }
    
    public String getSenderUsername() {
        return senderUsername;
    }
    
    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }
    
    public String getRecipientUsername() {
        return recipientUsername;
    }
    
    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public LocalDateTime getSentAt() {
        return sentAt;
    }
    
    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
    
    public LocalDateTime getReadAt() {
        return readAt;
    }
    
    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
    
    public boolean isRead() {
        return isRead;
    }
    
    public void setRead(boolean read) {
        isRead = read;
    }
    
    public boolean isDeletedBySender() {
        return deletedBySender;
    }
    
    public void setDeletedBySender(boolean deletedBySender) {
        this.deletedBySender = deletedBySender;
    }
    
    public boolean isDeletedByRecipient() {
        return deletedByRecipient;
    }
    
    public void setDeletedByRecipient(boolean deletedByRecipient) {
        this.deletedByRecipient = deletedByRecipient;
    }
    
    /**
     * Mark the message as read
     */
    public void markAsRead() {
        this.isRead = true;
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
    
    /**
     * Get a preview of the message body (first 100 characters)
     */
    public String getBodyPreview() {
        if (body == null) {
            return "";
        }
        if (body.length() <= 100) {
            return body;
        }
        return body.substring(0, 97) + "...";
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", recipientId=" + recipientId +
                ", senderUsername='" + senderUsername + '\'' +
                ", recipientUsername='" + recipientUsername + '\'' +
                ", subject='" + subject + '\'' +
                ", sentAt=" + sentAt +
                ", isRead=" + isRead +
                '}';
    }
}
