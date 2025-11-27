package org.vericrop.gui.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.SimulationBatch;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for SimulationBatch operations.
 * Handles all database operations related to simulation batches including creation,
 * retrieval, and updates during simulation runtime.
 */
public class SimulationBatchDao {
    private static final Logger logger = LoggerFactory.getLogger(SimulationBatchDao.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final DataSource dataSource;
    
    public SimulationBatchDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Create a new simulation batch.
     * 
     * @param simulationId Simulation UUID
     * @param batchIndex Sequential batch index within simulation
     * @param quantity Batch quantity
     * @param metadata Optional metadata as JsonNode
     * @return Created SimulationBatch object with ID, or null if creation failed
     */
    public SimulationBatch createBatch(UUID simulationId, int batchIndex, int quantity, JsonNode metadata) {
        String sql = "INSERT INTO simulation_batches (simulation_id, batch_index, quantity, status, metadata) " +
                     "VALUES (?, ?, ?, 'created', ?) " +
                     "RETURNING id, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            stmt.setInt(2, batchIndex);
            stmt.setInt(3, quantity);
            
            if (metadata != null) {
                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(OBJECT_MAPPER.writeValueAsString(metadata));
                stmt.setObject(4, jsonObject);
            } else {
                stmt.setNull(4, Types.OTHER);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SimulationBatch batch = new SimulationBatch(simulationId, batchIndex, quantity);
                    batch.setId(UUID.fromString(rs.getString("id")));
                    batch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    batch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    batch.setMetadata(metadata);
                    
                    logger.debug("Batch created: {} for simulation {}", batch.getId(), simulationId);
                    return batch;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to create simulation batch: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error serializing batch metadata: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Find batch by ID.
     * 
     * @param id Batch UUID
     * @return Optional containing the SimulationBatch if found
     */
    public Optional<SimulationBatch> findById(UUID id) {
        String sql = "SELECT * FROM simulation_batches WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding batch by ID: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Find all batches for a simulation.
     * 
     * @param simulationId Simulation UUID
     * @return List of batches for the simulation, ordered by batch_index
     */
    public List<SimulationBatch> findBySimulationId(UUID simulationId) {
        String sql = "SELECT * FROM simulation_batches WHERE simulation_id = ? ORDER BY batch_index";
        
        List<SimulationBatch> batches = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapResultSetToBatch(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding batches by simulation ID: {}", e.getMessage());
        }
        return batches;
    }
    
    /**
     * Find batch by simulation ID and batch index.
     * 
     * @param simulationId Simulation UUID
     * @param batchIndex Batch index
     * @return Optional containing the SimulationBatch if found
     */
    public Optional<SimulationBatch> findBySimulationAndIndex(UUID simulationId, int batchIndex) {
        String sql = "SELECT * FROM simulation_batches WHERE simulation_id = ? AND batch_index = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            stmt.setInt(2, batchIndex);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding batch by simulation and index: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Update batch status and environmental data during simulation.
     * 
     * @param id Batch UUID
     * @param status New status
     * @param temperature Current temperature
     * @param humidity Current humidity
     * @param currentLocation Current location name
     * @param progress Progress percentage (0-100)
     * @return true if updated successfully
     */
    public boolean updateBatchProgress(UUID id, String status, Double temperature, 
                                       Double humidity, String currentLocation, Double progress) {
        String sql = "UPDATE simulation_batches SET status = ?, temperature = ?, humidity = ?, " +
                     "current_location = ?, progress = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setObject(2, temperature, Types.DOUBLE);
            stmt.setObject(3, humidity, Types.DOUBLE);
            stmt.setString(4, currentLocation);
            stmt.setObject(5, progress, Types.DOUBLE);
            stmt.setObject(6, id);
            
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                logger.debug("Batch {} updated: status={}, progress={}%", id, status, progress);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating batch progress: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Update batch quality score.
     * 
     * @param id Batch UUID
     * @param qualityScore Quality score (0.0-1.0)
     * @return true if updated successfully
     */
    public boolean updateQualityScore(UUID id, Double qualityScore) {
        String sql = "UPDATE simulation_batches SET quality_score = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, qualityScore, Types.DOUBLE);
            stmt.setObject(2, id);
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("Error updating batch quality score: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Update batch metadata.
     * 
     * @param id Batch UUID
     * @param metadata New metadata
     * @return true if updated successfully
     */
    public boolean updateMetadata(UUID id, JsonNode metadata) {
        String sql = "UPDATE simulation_batches SET metadata = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (metadata != null) {
                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(OBJECT_MAPPER.writeValueAsString(metadata));
                stmt.setObject(1, jsonObject);
            } else {
                stmt.setNull(1, Types.OTHER);
            }
            stmt.setObject(2, id);
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Error updating batch metadata: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Mark batch as delivered.
     * 
     * @param id Batch UUID
     * @return true if updated successfully
     */
    public boolean markDelivered(UUID id) {
        return updateBatchProgress(id, SimulationBatch.STATUS_DELIVERED, null, null, "Destination", 100.0);
    }
    
    /**
     * Get the latest batch for a simulation.
     * 
     * @param simulationId Simulation UUID
     * @return Optional containing the latest batch if any exists
     */
    public Optional<SimulationBatch> findLatestBySimulation(UUID simulationId) {
        String sql = "SELECT * FROM simulation_batches WHERE simulation_id = ? " +
                     "ORDER BY batch_index DESC LIMIT 1";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding latest batch: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Count batches for a simulation.
     * 
     * @param simulationId Simulation UUID
     * @return Number of batches
     */
    public int countBySimulationId(UUID simulationId) {
        String sql = "SELECT COUNT(*) FROM simulation_batches WHERE simulation_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting batches: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Get average quality score for a simulation's batches.
     * 
     * @param simulationId Simulation UUID
     * @return Average quality score, or null if no scores available
     */
    public Double getAverageQualityScore(UUID simulationId) {
        String sql = "SELECT AVG(quality_score) FROM simulation_batches " +
                     "WHERE simulation_id = ? AND quality_score IS NOT NULL";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double avg = rs.getDouble(1);
                    return rs.wasNull() ? null : avg;
                }
            }
        } catch (SQLException e) {
            logger.error("Error calculating average quality score: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Delete all batches for a simulation.
     * 
     * @param simulationId Simulation UUID
     * @return Number of batches deleted
     */
    public int deleteBySimulationId(UUID simulationId) {
        String sql = "DELETE FROM simulation_batches WHERE simulation_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, simulationId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting batches: {}", e.getMessage());
        }
        return 0;
    }
    
    /**
     * Map a ResultSet row to a SimulationBatch object.
     */
    private SimulationBatch mapResultSetToBatch(ResultSet rs) throws SQLException {
        SimulationBatch batch = new SimulationBatch();
        batch.setId(UUID.fromString(rs.getString("id")));
        batch.setSimulationId(UUID.fromString(rs.getString("simulation_id")));
        batch.setBatchIndex(rs.getInt("batch_index"));
        batch.setQuantity(rs.getInt("quantity"));
        batch.setStatus(rs.getString("status"));
        
        double qualityScore = rs.getDouble("quality_score");
        batch.setQualityScore(rs.wasNull() ? null : qualityScore);
        
        double temperature = rs.getDouble("temperature");
        batch.setTemperature(rs.wasNull() ? null : temperature);
        
        double humidity = rs.getDouble("humidity");
        batch.setHumidity(rs.wasNull() ? null : humidity);
        
        batch.setCurrentLocation(rs.getString("current_location"));
        
        double progress = rs.getDouble("progress");
        batch.setProgress(rs.wasNull() ? null : progress);
        
        // Parse metadata JSON
        String metadataStr = rs.getString("metadata");
        if (metadataStr != null) {
            try {
                batch.setMetadata(OBJECT_MAPPER.readTree(metadataStr));
            } catch (Exception e) {
                logger.warn("Failed to parse batch metadata JSON: {}", e.getMessage());
            }
        }
        
        batch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        batch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        
        return batch;
    }
}
