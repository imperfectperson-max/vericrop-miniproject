package org.vericrop.gui.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.Message;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Message operations.
 * Handles all database operations related to user messaging.
 */
public class MessageDao {
    private static final Logger logger = LoggerFactory.getLogger(MessageDao.class);
    private final DataSource dataSource;
    
    public MessageDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Send a new message from one user to another
     * @param senderId Sender's user ID
     * @param recipientId Recipient's user ID
     * @param subject Message subject
     * @param body Message body
     * @return Created Message object with ID, or null if creation failed
     */
    public Message sendMessage(Long senderId, Long recipientId, String subject, String body) {
        String sql = "INSERT INTO messages (sender_id, recipient_id, subject, body, sent_at, is_read) " +
                     "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE) " +
                     "RETURNING id, sent_at, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, senderId);
            stmt.setLong(2, recipientId);
            stmt.setString(3, subject);
            stmt.setString(4, body);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Message message = new Message(senderId, recipientId, subject, body);
                    message.setId(rs.getLong("id"));
                    message.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                    logger.info("✅ Message sent successfully from user {} to user {}", senderId, recipientId);
                    return message;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to send message: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Send a new message from one user to another using usernames.
     * This method looks up user IDs from usernames and then sends the message.
     * 
     * @param senderUsername Sender's username (required)
     * @param recipientUsername Recipient's username (required)
     * @param subject Message subject (required)
     * @param body Message body (required)
     * @return Created Message object with ID and usernames set, or null if creation failed or validation fails
     */
    public Message sendMessageByUsername(String senderUsername, String recipientUsername, String subject, String body) {
        // Validate required parameters
        if (senderUsername == null || senderUsername.trim().isEmpty()) {
            logger.warn("Cannot send message: senderUsername is null or empty");
            return null;
        }
        if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
            logger.warn("Cannot send message: recipientUsername is null or empty");
            return null;
        }
        if (subject == null || subject.trim().isEmpty()) {
            logger.warn("Cannot send message: subject is null or empty");
            return null;
        }
        if (body == null || body.trim().isEmpty()) {
            logger.warn("Cannot send message: body is null or empty");
            return null;
        }
        
        String lookupSql = "SELECT id FROM users WHERE username = ? AND status = 'active'";
        String insertSql = "INSERT INTO messages (sender_id, recipient_id, subject, body, sent_at, is_read) " +
                          "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE) " +
                          "RETURNING id, sent_at";
        
        try (Connection conn = dataSource.getConnection()) {
            // Look up sender ID
            Long senderId = null;
            try (PreparedStatement stmt = conn.prepareStatement(lookupSql)) {
                stmt.setString(1, senderUsername.trim());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        senderId = rs.getLong("id");
                    } else {
                        logger.warn("Sender not found: {}", senderUsername);
                        return null;
                    }
                }
            }
            
            // Look up recipient ID
            Long recipientId = null;
            try (PreparedStatement stmt = conn.prepareStatement(lookupSql)) {
                stmt.setString(1, recipientUsername.trim());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        recipientId = rs.getLong("id");
                    } else {
                        logger.warn("Recipient not found: {}", recipientUsername);
                        return null;
                    }
                }
            }
            
            // Send the message
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setLong(1, senderId);
                stmt.setLong(2, recipientId);
                stmt.setString(3, subject.trim());
                stmt.setString(4, body.trim());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Message message = new Message(senderId, recipientId, subject.trim(), body.trim());
                        message.setId(rs.getLong("id"));
                        message.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                        message.setSenderUsername(senderUsername.trim());
                        message.setRecipientUsername(recipientUsername.trim());
                        logger.info("✅ Message sent successfully from {} to {}", senderUsername, recipientUsername);
                        return message;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to send message by username: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get conversation thread between two users identified by username.
     * Returns all messages exchanged between sender and recipient, sorted by sent time.
     * 
     * @param username1 First user's username
     * @param username2 Second user's username
     * @return List of messages in the conversation thread
     */
    public List<Message> getConversationByUsername(String username1, String username2) {
        String sql = "SELECT m.id, m.sender_id, m.recipient_id, m.subject, m.body, m.sent_at, " +
                    "m.read_at, m.is_read, m.deleted_by_sender, m.deleted_by_recipient, " +
                    "s.username as sender_username, s.full_name as sender_fullname, " +
                    "r.username as recipient_username, r.full_name as recipient_fullname " +
                    "FROM messages m " +
                    "JOIN users s ON m.sender_id = s.id " +
                    "JOIN users r ON m.recipient_id = r.id " +
                    "WHERE (s.username = ? AND r.username = ?) OR (s.username = ? AND r.username = ?) " +
                    "ORDER BY m.sent_at ASC";
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username1);
            stmt.setString(2, username2);
            stmt.setString(3, username2);
            stmt.setString(4, username1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message();
                    message.setId(rs.getLong("id"));
                    message.setSenderId(rs.getLong("sender_id"));
                    message.setRecipientId(rs.getLong("recipient_id"));
                    message.setSubject(rs.getString("subject"));
                    message.setBody(rs.getString("body"));
                    message.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                    message.setRead(rs.getBoolean("is_read"));
                    message.setDeletedBySender(rs.getBoolean("deleted_by_sender"));
                    message.setDeletedByRecipient(rs.getBoolean("deleted_by_recipient"));
                    message.setSenderUsername(rs.getString("sender_username"));
                    message.setRecipientUsername(rs.getString("recipient_username"));
                    
                    Timestamp readAt = rs.getTimestamp("read_at");
                    if (readAt != null) {
                        message.setReadAt(readAt.toLocalDateTime());
                    }
                    
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting conversation by username: {}", e.getMessage());
        }
        return messages;
    }
    public List<Message> getInboxMessages(Long userId) {
        String sql = "SELECT m.id, m.sender_id, m.recipient_id, m.subject, m.body, m.sent_at, " +
                     "m.read_at, m.is_read, m.deleted_by_sender, m.deleted_by_recipient, " +
                     "u.username as sender_username " +
                     "FROM messages m " +
                     "JOIN users u ON m.sender_id = u.id " +
                     "WHERE m.recipient_id = ? AND m.deleted_by_recipient = FALSE " +
                     "ORDER BY m.sent_at DESC";
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs, true));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting inbox messages: {}", e.getMessage());
        }
        return messages;
    }
    
    /**
     * Get sent messages for a user (messages sent)
     * @param userId User ID
     * @return List of messages sent by the user
     */
    public List<Message> getSentMessages(Long userId) {
        String sql = "SELECT m.id, m.sender_id, m.recipient_id, m.subject, m.body, m.sent_at, " +
                     "m.read_at, m.is_read, m.deleted_by_sender, m.deleted_by_recipient, " +
                     "u.username as recipient_username " +
                     "FROM messages m " +
                     "JOIN users u ON m.recipient_id = u.id " +
                     "WHERE m.sender_id = ? AND m.deleted_by_sender = FALSE " +
                     "ORDER BY m.sent_at DESC";
        
        List<Message> messages = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs, false));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting sent messages: {}", e.getMessage());
        }
        return messages;
    }
    
    /**
     * Get unread message count for a user
     * @param userId User ID
     * @return Number of unread messages
     */
    public int getUnreadCount(Long userId) {
        String sql = "SELECT COUNT(*) FROM messages " +
                     "WHERE recipient_id = ? AND is_read = FALSE AND deleted_by_recipient = FALSE";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting unread count: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Find message by ID with full details
     * @param messageId Message ID
     * @return Optional containing the Message if found
     */
    public Optional<Message> findById(Long messageId) {
        String sql = "SELECT m.id, m.sender_id, m.recipient_id, m.subject, m.body, m.sent_at, " +
                     "m.read_at, m.is_read, m.deleted_by_sender, m.deleted_by_recipient, " +
                     "s.username as sender_username, r.username as recipient_username " +
                     "FROM messages m " +
                     "JOIN users s ON m.sender_id = s.id " +
                     "JOIN users r ON m.recipient_id = r.id " +
                     "WHERE m.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, messageId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Message message = mapResultSetToMessage(rs, true);
                    message.setRecipientUsername(rs.getString("recipient_username"));
                    return Optional.of(message);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding message by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Mark a message as read
     * @param messageId Message ID
     * @return true if successful
     */
    public boolean markAsRead(Long messageId) {
        String sql = "UPDATE messages SET is_read = TRUE, read_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ? AND is_read = FALSE";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, messageId);
            int rows = stmt.executeUpdate();
            
            if (rows > 0) {
                logger.debug("Message {} marked as read", messageId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error marking message as read: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Mark a message as unread
     * @param messageId Message ID
     * @return true if successful
     */
    public boolean markAsUnread(Long messageId) {
        String sql = "UPDATE messages SET is_read = FALSE, read_at = NULL WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, messageId);
            int rows = stmt.executeUpdate();
            
            if (rows > 0) {
                logger.debug("Message {} marked as unread", messageId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error marking message as unread: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Delete a message (soft delete for recipient)
     * @param messageId Message ID
     * @param userId User ID (to determine if sender or recipient)
     * @return true if successful
     */
    public boolean deleteMessage(Long messageId, Long userId) {
        // First check if user is sender or recipient
        String checkSql = "SELECT sender_id, recipient_id FROM messages WHERE id = ?";
        String updateSql = null;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setLong(1, messageId);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    Long senderId = rs.getLong("sender_id");
                    Long recipientId = rs.getLong("recipient_id");
                    
                    if (userId.equals(senderId)) {
                        updateSql = "UPDATE messages SET deleted_by_sender = TRUE WHERE id = ?";
                    } else if (userId.equals(recipientId)) {
                        updateSql = "UPDATE messages SET deleted_by_recipient = TRUE WHERE id = ?";
                    } else {
                        // Security event: unauthorized deletion attempt
                        logger.warn("SECURITY: User {} attempted to delete message {} they don't own (sender: {}, recipient: {})", 
                                   userId, messageId, senderId, recipientId);
                        return false;
                    }
                }
            }
            
            if (updateSql != null) {
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setLong(1, messageId);
                    int rows = updateStmt.executeUpdate();
                    
                    if (rows > 0) {
                        logger.debug("Message {} deleted by user {}", messageId, userId);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error deleting message: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Map a ResultSet row to a Message object
     * @param rs ResultSet
     * @param isInbox true if this is an inbox message (has sender_username), false if sent message (has recipient_username)
     */
    private Message mapResultSetToMessage(ResultSet rs, boolean isInbox) throws SQLException {
        Message message = new Message();
        message.setId(rs.getLong("id"));
        message.setSenderId(rs.getLong("sender_id"));
        message.setRecipientId(rs.getLong("recipient_id"));
        message.setSubject(rs.getString("subject"));
        message.setBody(rs.getString("body"));
        message.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
        message.setRead(rs.getBoolean("is_read"));
        message.setDeletedBySender(rs.getBoolean("deleted_by_sender"));
        message.setDeletedByRecipient(rs.getBoolean("deleted_by_recipient"));
        
        Timestamp readAt = rs.getTimestamp("read_at");
        if (readAt != null) {
            message.setReadAt(readAt.toLocalDateTime());
        }
        
        if (isInbox) {
            message.setSenderUsername(rs.getString("sender_username"));
        } else {
            message.setRecipientUsername(rs.getString("recipient_username"));
        }
        
        return message;
    }
}
