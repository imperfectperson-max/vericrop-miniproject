package org.vericrop.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Message entity for communication between supply chain actors.
 * Supports farmer, supplier, logistics, and consumer roles.
 */
public class Message {
    private String messageId;
    private String senderRole;      // farmer, supplier, logistics, consumer
    private String senderId;
    private String recipientRole;   // farmer, supplier, logistics, consumer, or "all"
    private String recipientId;     // specific recipient or null for broadcast
    private String subject;
    private String content;
    private long timestamp;
    private String status;          // sent, delivered, read
    private String batchId;         // optional: related batch/shipment
    private String shipmentId;      // optional: related shipment
    
    public Message() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toEpochMilli();
        this.status = "sent";
    }
    
    public Message(String senderRole, String senderId, String recipientRole, 
                   String recipientId, String subject, String content) {
        this();
        this.senderRole = senderRole;
        this.senderId = senderId;
        this.recipientRole = recipientRole;
        this.recipientId = recipientId;
        this.subject = subject;
        this.content = content;
    }
    
    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getSenderRole() {
        return senderRole;
    }
    
    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getRecipientRole() {
        return recipientRole;
    }
    
    public void setRecipientRole(String recipientRole) {
        this.recipientRole = recipientRole;
    }
    
    public String getRecipientId() {
        return recipientId;
    }
    
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getShipmentId() {
        return shipmentId;
    }
    
    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", senderRole='" + senderRole + '\'' +
                ", senderId='" + senderId + '\'' +
                ", recipientRole='" + recipientRole + '\'' +
                ", subject='" + subject + '\'' +
                ", timestamp=" + timestamp +
                ", status='" + status + '\'' +
                '}';
    }
}
