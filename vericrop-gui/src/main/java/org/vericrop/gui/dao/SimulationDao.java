package org.vericrop.gui.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.Simulation;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for Simulation operations.
 * Handles all database operations related to simulations including creation,
 * retrieval, and access control checks.
 */
public class SimulationDao {
    private static final Logger logger = LoggerFactory.getLogger(SimulationDao.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final DataSource dataSource;
    
    public SimulationDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Generate a secure simulation token using cryptographically secure random bytes.
     * 
     * @return A 64-character hex string token
     */
    public static String generateSimulationToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : tokenBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Create a new simulation with supplier and consumer relationships.
     * 
     * @param title Simulation title
     * @param ownerUserId Owner user ID
     * @param supplierUserId Supplier user ID
     * @param consumerUserId Consumer user ID
     * @param meta Optional metadata as JsonNode
     * @return Created Simulation object with ID and token, or null if creation failed
     */
    public Simulation createSimulation(String title, Long ownerUserId, Long supplierUserId, 
                                       Long consumerUserId, JsonNode meta) {
        String simulationToken = generateSimulationToken();
        
        String sql = "INSERT INTO simulations (title, owner_user_id, supplier_user_id, consumer_user_id, " +
                     "simulation_token, status, meta) " +
                     "VALUES (?, ?, ?, ?, ?, 'created', ?) " +
                     "RETURNING id, started_at, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, title);
            stmt.setLong(2, ownerUserId);
            stmt.setLong(3, supplierUserId);
            stmt.setLong(4, consumerUserId);
            stmt.setString(5, simulationToken);
            
            if (meta != null) {
                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(OBJECT_MAPPER.writeValueAsString(meta));
                stmt.setObject(6, jsonObject);
            } else {
                stmt.setNull(6, Types.OTHER);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Simulation simulation = new Simulation(title, ownerUserId, supplierUserId, 
                                                          consumerUserId, simulationToken);
                    simulation.setId(UUID.fromString(rs.getString("id")));
                    simulation.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
                    simulation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    simulation.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    simulation.setMeta(meta);
                    
                    logger.info("✅ Simulation created: {} (token: {}...)", 
                               simulation.getId(), simulationToken.substring(0, 8));
                    return simulation;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create simulation: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error serializing simulation meta: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Find simulation by ID.
     * 
     * @param id Simulation UUID
     * @return Optional containing the Simulation if found
     */
    public Optional<Simulation> findById(UUID id) {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "WHERE s.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSimulation(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding simulation by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Find simulation by simulation token.
     * 
     * @param token Simulation token
     * @return Optional containing the Simulation if found
     */
    public Optional<Simulation> findByToken(String token) {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "WHERE s.simulation_token = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSimulation(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding simulation by token: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Find all simulations accessible by a user (as owner, supplier, or consumer).
     * 
     * @param userId User ID
     * @return List of simulations the user can access
     */
    public List<Simulation> findByUserAccess(Long userId) {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "WHERE s.owner_user_id = ? OR s.supplier_user_id = ? OR s.consumer_user_id = ? " +
                     "ORDER BY s.created_at DESC";
        
        List<Simulation> simulations = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    simulations.add(mapResultSetToSimulation(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding simulations by user access: {}", e.getMessage());
        }
        return simulations;
    }
    
    /**
     * Find all active (running) simulations accessible by a user.
     * 
     * @param userId User ID
     * @return List of active simulations the user can access
     */
    public List<Simulation> findActiveByUserAccess(Long userId) {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "WHERE (s.owner_user_id = ? OR s.supplier_user_id = ? OR s.consumer_user_id = ?) " +
                     "AND s.status IN ('created', 'running') " +
                     "ORDER BY s.created_at DESC";
        
        List<Simulation> simulations = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    simulations.add(mapResultSetToSimulation(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding active simulations by user access: {}", e.getMessage());
        }
        return simulations;
    }
    
    /**
     * Update simulation status.
     * 
     * @param id Simulation UUID
     * @param status New status
     * @return true if updated successfully
     */
    public boolean updateStatus(UUID id, String status) {
        String sql = "UPDATE simulations SET status = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setObject(2, id);
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                logger.info("Simulation {} status updated to: {}", id, status);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating simulation status: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Mark simulation as completed with end time.
     * 
     * @param id Simulation UUID
     * @return true if updated successfully
     */
    public boolean markCompleted(UUID id) {
        String sql = "UPDATE simulations SET status = 'completed', ended_at = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, id);
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                logger.info("Simulation {} marked as completed", id);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error marking simulation as completed: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Check if a user can access a simulation.
     * 
     * @param simulationId Simulation UUID
     * @param userId User ID to check
     * @return true if user can access the simulation
     */
    public boolean canUserAccess(UUID simulationId, Long userId) {
        String sql = "SELECT 1 FROM simulations WHERE id = ? " +
                     "AND (owner_user_id = ? OR supplier_user_id = ? OR consumer_user_id = ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            stmt.setLong(4, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking user access to simulation: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Validate that a simulation token matches a simulation ID.
     * Used for multi-device access authentication.
     * 
     * @param simulationId Simulation UUID
     * @param token Simulation token
     * @return true if the token matches the simulation
     */
    public boolean validateToken(UUID simulationId, String token) {
        String sql = "SELECT 1 FROM simulations WHERE id = ? AND simulation_token = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            stmt.setString(2, token);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error validating simulation token: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Find all simulations.
     * Used for maintenance operations (backup before delete).
     * 
     * @return List of all simulations
     */
    public List<Simulation> findAll() {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "ORDER BY s.created_at DESC";
        
        List<Simulation> simulations = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                simulations.add(mapResultSetToSimulation(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all simulations: {}", e.getMessage());
        }
        return simulations;
    }
    
    /**
     * Delete all simulations.
     * Used for maintenance operations.
     * Note: Related simulation_batches should be deleted first due to FK constraints.
     * 
     * @return Number of simulations deleted
     */
    public int deleteAll() {
        String sql = "DELETE FROM simulations";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int deleted = stmt.executeUpdate();
            logger.info("Deleted {} simulations", deleted);
            return deleted;
        } catch (SQLException e) {
            logger.error("Error deleting all simulations: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Map a ResultSet row to a Simulation object.
     */
    private Simulation mapResultSetToSimulation(ResultSet rs) throws SQLException {
        Simulation simulation = new Simulation();
        simulation.setId(UUID.fromString(rs.getString("id")));
        simulation.setTitle(rs.getString("title"));
        simulation.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
        
        Timestamp endedAt = rs.getTimestamp("ended_at");
        if (endedAt != null) {
            simulation.setEndedAt(endedAt.toLocalDateTime());
        }
        
        simulation.setStatus(rs.getString("status"));
        simulation.setOwnerUserId(rs.getLong("owner_user_id"));
        simulation.setSupplierUserId(rs.getLong("supplier_user_id"));
        simulation.setConsumerUserId(rs.getLong("consumer_user_id"));
        simulation.setSimulationToken(rs.getString("simulation_token"));
        
        // Parse meta JSON
        String metaStr = rs.getString("meta");
        if (metaStr != null) {
            try {
                simulation.setMeta(OBJECT_MAPPER.readTree(metaStr));
            } catch (Exception e) {
                logger.warn("Failed to parse simulation meta JSON: {}", e.getMessage());
            }
        }
        
        simulation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        simulation.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        
        // Map joined username fields if present
        try {
            simulation.setOwnerUsername(rs.getString("owner_username"));
            simulation.setSupplierUsername(rs.getString("supplier_username"));
            simulation.setConsumerUsername(rs.getString("consumer_username"));
        } catch (SQLException e) {
            // Columns may not be present in all queries
        }
        
        return simulation;
    }
    
    /**
     * Get total count of simulations.
     * 
     * @return Total number of simulations
     */
    public int getTotalCount() {
        String sql = "SELECT COUNT(*) FROM simulations";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error getting total simulation count: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get count of simulations by status.
     * 
     * @param status Status to filter by (e.g., "completed", "running")
     * @return Count of simulations with that status
     */
    public int getCountByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM simulations WHERE status = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting simulation count by status: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get recent simulations (limit to specified count).
     * 
     * @param limit Maximum number of simulations to return
     * @return List of recent simulations
     */
    public List<Simulation> getRecent(int limit) {
        String sql = "SELECT s.*, " +
                     "owner.username as owner_username, " +
                     "supplier.username as supplier_username, " +
                     "consumer.username as consumer_username " +
                     "FROM simulations s " +
                     "LEFT JOIN users owner ON s.owner_user_id = owner.id " +
                     "LEFT JOIN users supplier ON s.supplier_user_id = supplier.id " +
                     "LEFT JOIN users consumer ON s.consumer_user_id = consumer.id " +
                     "ORDER BY s.created_at DESC " +
                     "LIMIT ?";
        
        List<Simulation> simulations = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    simulations.add(mapResultSetToSimulation(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting recent simulations: {}", e.getMessage());
        }
        return simulations;
    }
}
