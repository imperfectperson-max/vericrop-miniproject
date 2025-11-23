package org.vericrop.gui.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.Participant;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for Participant operations.
 * Handles all database operations related to participant tracking.
 */
public class ParticipantDao {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantDao.class);
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    public ParticipantDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Register or update a participant (upsert)
     * @param participant Participant to register
     * @return Registered Participant with ID, or null if registration failed
     */
    public Participant registerParticipant(Participant participant) {
        String sql = "INSERT INTO participants (user_id, instance_id, display_name, connection_info, gui_version, status, last_seen) " +
                     "VALUES (?, ?, ?, ?::jsonb, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (instance_id) DO UPDATE " +
                     "SET display_name = EXCLUDED.display_name, " +
                     "    connection_info = EXCLUDED.connection_info, " +
                     "    gui_version = EXCLUDED.gui_version, " +
                     "    status = EXCLUDED.status, " +
                     "    last_seen = CURRENT_TIMESTAMP " +
                     "RETURNING id, created_at, updated_at, last_seen";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, participant.getUserId());
            stmt.setString(2, participant.getInstanceId());
            stmt.setString(3, participant.getDisplayName());
            stmt.setString(4, objectMapper.writeValueAsString(participant.getConnectionInfo()));
            stmt.setString(5, participant.getGuiVersion());
            stmt.setString(6, participant.getStatus());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    participant.setId(rs.getLong("id"));
                    participant.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    participant.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    participant.setLastSeen(rs.getTimestamp("last_seen").toLocalDateTime());
                    logger.info("✅ Participant registered: {} (instance: {})", 
                               participant.getDisplayName(), participant.getInstanceId());
                    return participant;
                }
            }
        } catch (SQLException | JsonProcessingException e) {
            logger.error("Failed to register participant: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get all active participants (excluding current instance if provided)
     * @param excludeInstanceId Optional instance ID to exclude from results
     * @return List of active participants
     */
    public List<Participant> getActiveParticipants(String excludeInstanceId) {
        String sql = "SELECT p.id, p.user_id, p.instance_id, p.display_name, p.connection_info, " +
                     "p.gui_version, p.status, p.last_seen, p.created_at, p.updated_at, " +
                     "u.username, u.role " +
                     "FROM participants p " +
                     "JOIN users u ON p.user_id = u.id " +
                     "WHERE p.status = 'active' " +
                     (excludeInstanceId != null ? "AND p.instance_id != ? " : "") +
                     "ORDER BY p.last_seen DESC";
        
        List<Participant> participants = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (excludeInstanceId != null) {
                stmt.setString(1, excludeInstanceId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    participants.add(mapResultSetToParticipant(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting active participants: {}", e.getMessage());
        }
        return participants;
    }
    
    /**
     * Get participant by ID
     * @param id Participant ID
     * @return Optional containing the Participant if found
     */
    public Optional<Participant> findById(Long id) {
        String sql = "SELECT p.id, p.user_id, p.instance_id, p.display_name, p.connection_info, " +
                     "p.gui_version, p.status, p.last_seen, p.created_at, p.updated_at, " +
                     "u.username, u.role " +
                     "FROM participants p " +
                     "JOIN users u ON p.user_id = u.id " +
                     "WHERE p.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToParticipant(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding participant by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Get participant by instance ID
     * @param instanceId Instance ID
     * @return Optional containing the Participant if found
     */
    public Optional<Participant> findByInstanceId(String instanceId) {
        String sql = "SELECT p.id, p.user_id, p.instance_id, p.display_name, p.connection_info, " +
                     "p.gui_version, p.status, p.last_seen, p.created_at, p.updated_at, " +
                     "u.username, u.role " +
                     "FROM participants p " +
                     "JOIN users u ON p.user_id = u.id " +
                     "WHERE p.instance_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, instanceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToParticipant(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding participant by instance ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Update participant last seen timestamp
     * @param instanceId Instance ID
     * @return true if successful
     */
    public boolean updateLastSeen(String instanceId) {
        String sql = "UPDATE participants SET last_seen = CURRENT_TIMESTAMP WHERE instance_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, instanceId);
            int rows = stmt.executeUpdate();
            
            if (rows > 0) {
                logger.debug("Participant {} last seen updated", instanceId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating last seen: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Update participant status
     * @param instanceId Instance ID
     * @param status New status
     * @return true if successful
     */
    public boolean updateStatus(String instanceId, String status) {
        String sql = "UPDATE participants SET status = ? WHERE instance_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setString(2, instanceId);
            int rows = stmt.executeUpdate();
            
            if (rows > 0) {
                logger.info("Participant {} status updated to {}", instanceId, status);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating participant status: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Mark inactive participants (last seen beyond threshold) as disconnected
     * @return Number of participants marked as disconnected
     */
    public int markInactiveParticipants() {
        String sql = "UPDATE participants SET status = 'disconnected' " +
                     "WHERE status = 'active' AND last_seen < (CURRENT_TIMESTAMP - INTERVAL '" + 
                     org.vericrop.gui.models.Participant.ONLINE_THRESHOLD_MINUTES + " minutes')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                logger.info("Marked {} inactive participants as disconnected", rows);
            }
            return rows;
        } catch (SQLException e) {
            logger.error("Error marking inactive participants: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Delete participant by instance ID
     * @param instanceId Instance ID
     * @return true if successful
     */
    public boolean deleteParticipant(String instanceId) {
        String sql = "DELETE FROM participants WHERE instance_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, instanceId);
            int rows = stmt.executeUpdate();
            
            if (rows > 0) {
                logger.info("Participant {} deleted", instanceId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error deleting participant: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Map a ResultSet row to a Participant object
     */
    private Participant mapResultSetToParticipant(ResultSet rs) throws SQLException {
        Participant participant = new Participant();
        participant.setId(rs.getLong("id"));
        participant.setUserId(rs.getLong("user_id"));
        participant.setInstanceId(rs.getString("instance_id"));
        participant.setDisplayName(rs.getString("display_name"));
        participant.setGuiVersion(rs.getString("gui_version"));
        participant.setStatus(rs.getString("status"));
        participant.setLastSeen(rs.getTimestamp("last_seen").toLocalDateTime());
        participant.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        participant.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        participant.setUsername(rs.getString("username"));
        participant.setUserRole(rs.getString("role"));
        
        // Parse connectionInfo JSON
        PGobject connectionInfoJson = (PGobject) rs.getObject("connection_info");
        if (connectionInfoJson != null && connectionInfoJson.getValue() != null) {
            try {
                Map<String, Object> connectionInfo = objectMapper.readValue(
                    connectionInfoJson.getValue(),
                    new TypeReference<Map<String, Object>>() {}
                );
                participant.setConnectionInfo(connectionInfo);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse connection_info JSON: {}", e.getMessage());
                participant.setConnectionInfo(new HashMap<>());
            }
        }
        
        return participant;
    }
}
