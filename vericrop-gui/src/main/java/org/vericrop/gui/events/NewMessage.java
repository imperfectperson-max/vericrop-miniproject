package org.vericrop.gui.events;

/**
 * Event published when a new message is received.
 */
public class NewMessage extends DomainEvent {
    private final String messageId;
    private final String fromRole;
    private final String fromId;
    private final String toRole;
    private final String subject;
    private final String content;
    
    public NewMessage(String messageId, String fromRole, String fromId, String toRole, 
                     String subject, String content) {
        super();
        this.messageId = messageId;
        this.fromRole = fromRole;
        this.fromId = fromId;
        this.toRole = toRole;
        this.subject = subject;
        this.content = content;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getFromRole() {
        return fromRole;
    }
    
    public String getFromId() {
        return fromId;
    }
    
    public String getToRole() {
        return toRole;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public String getContent() {
        return content;
    }
    
    @Override
    public String toString() {
        return "NewMessage{" +
               "messageId='" + messageId + '\'' +
               ", fromRole='" + fromRole + '\'' +
               ", fromId='" + fromId + '\'' +
               ", toRole='" + toRole + '\'' +
               ", subject='" + subject + '\'' +
               ", " + super.toString() +
               '}';
    }
}
