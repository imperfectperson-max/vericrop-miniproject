package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vericrop.gui.dao.SimulationBatchDao;
import org.vericrop.gui.dao.SimulationDao;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.models.Simulation;
import org.vericrop.gui.models.SimulationBatch;
import org.vericrop.gui.models.User;

import javax.sql.DataSource;
import java.util.*;

/**
 * Service for managing simulations with supplier/consumer relationships.
 * Handles simulation creation, batch persistence, and access control.
 */
@Service
public class SimulationPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(SimulationPersistenceService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // Location constants for batch tracking
    public static final String LOCATION_STARTING = "Origin";
    public static final String LOCATION_DESTINATION = "Destination";
    
    private final SimulationDao simulationDao;
    private final SimulationBatchDao batchDao;
    private final UserDao userDao;
    
    public SimulationPersistenceService(DataSource dataSource) {
        this.simulationDao = new SimulationDao(dataSource);
        this.batchDao = new SimulationBatchDao(dataSource);
        this.userDao = new UserDao(dataSource);
    }
    
    /**
     * Start a new simulation with supplier and consumer selection.
     * Validates that both supplier and consumer usernames exist.
     * 
     * @param title Simulation title
     * @param ownerUserId Owner user ID
     * @param supplierUsername Username of the supplier
     * @param consumerUsername Username of the consumer
     * @param meta Optional metadata
     * @return SimulationStartResult containing the simulation or error details
     */
    public SimulationStartResult startSimulation(String title, Long ownerUserId, 
                                                  String supplierUsername, String consumerUsername,
                                                  JsonNode meta) {
        // Validate owner exists
        Optional<User> ownerOpt = userDao.findById(ownerUserId);
        if (ownerOpt.isEmpty()) {
            return SimulationStartResult.error("Owner user not found");
        }
        
        // Validate supplier username
        Optional<User> supplierOpt = userDao.findByUsername(supplierUsername);
        if (supplierOpt.isEmpty()) {
            return SimulationStartResult.error("Supplier user not found: " + supplierUsername);
        }
        
        // Validate consumer username
        Optional<User> consumerOpt = userDao.findByUsername(consumerUsername);
        if (consumerOpt.isEmpty()) {
            return SimulationStartResult.error("Consumer user not found: " + consumerUsername);
        }
        
        User supplier = supplierOpt.get();
        User consumer = consumerOpt.get();
        
        // Create simulation
        Simulation simulation = simulationDao.createSimulation(
            title, ownerUserId, supplier.getId(), consumer.getId(), meta);
        
        if (simulation == null) {
            return SimulationStartResult.error("Failed to create simulation in database");
        }
        
        // Set username fields for convenience
        simulation.setOwnerUsername(ownerOpt.get().getUsername());
        simulation.setSupplierUsername(supplier.getUsername());
        simulation.setConsumerUsername(consumer.getUsername());
        
        // Update status to running
        simulationDao.updateStatus(simulation.getId(), Simulation.STATUS_RUNNING);
        simulation.setStatus(Simulation.STATUS_RUNNING);
        
        logger.info("✅ Simulation started: {} (owner={}, supplier={}, consumer={})",
                   simulation.getId(), ownerOpt.get().getUsername(), 
                   supplierUsername, consumerUsername);
        
        return SimulationStartResult.success(simulation);
    }
    
    /**
     * Create a batch within a simulation.
     * Uses transactional handling to ensure the batch is fully created with correct status.
     * The status will be set to IN_TRANSIT only after successful transaction commit.
     * 
     * @param simulationId Simulation UUID
     * @param quantity Batch quantity
     * @param metadata Optional batch metadata
     * @return Created SimulationBatch or null if failed
     */
    public SimulationBatch createBatch(UUID simulationId, int quantity, JsonNode metadata) {
        long startTime = System.currentTimeMillis();
        logger.info("Creating batch for simulation {} with quantity {}", simulationId, quantity);
        
        // Get next batch index
        int batchIndex = batchDao.countBySimulationId(simulationId);
        
        // Use transactional batch creation to avoid stuck "in_transit" status
        SimulationBatch batch = batchDao.createBatchWithTransaction(
            simulationId, batchIndex, quantity, metadata, 
            SimulationBatch.STATUS_IN_TRANSIT, LOCATION_STARTING);
        
        if (batch != null) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("✅ Batch {} created successfully for simulation {} in {}ms (status: {})", 
                       batch.getId(), simulationId, elapsed, batch.getStatus());
        } else {
            logger.error("❌ Failed to create batch for simulation {} - transaction rolled back", simulationId);
        }
        
        return batch;
    }
    
    /**
     * Update batch progress during simulation.
     * Transitions batch status based on progress and logs the transition.
     * 
     * @param batchId Batch UUID
     * @param temperature Current temperature
     * @param humidity Current humidity
     * @param location Current location
     * @param progress Progress percentage
     */
    public void updateBatchProgress(UUID batchId, Double temperature, Double humidity, 
                                    String location, Double progress) {
        String newStatus = progress != null && progress >= 100.0 ? 
            SimulationBatch.STATUS_DELIVERED : SimulationBatch.STATUS_IN_TRANSIT;
        
        boolean updated = batchDao.updateBatchProgress(batchId, newStatus, temperature, humidity, location, progress);
        
        if (updated && SimulationBatch.STATUS_DELIVERED.equals(newStatus)) {
            logger.info("Batch {} completed delivery (progress: {}%)", batchId, progress);
        }
    }
    
    /**
     * Complete a simulation.
     * 
     * @param simulationId Simulation UUID
     */
    public void completeSimulation(UUID simulationId) {
        simulationDao.markCompleted(simulationId);
        logger.info("Simulation {} marked as completed", simulationId);
    }
    
    /**
     * Stop a simulation (manually stopped before completion).
     * 
     * @param simulationId Simulation UUID
     */
    public void stopSimulation(UUID simulationId) {
        simulationDao.updateStatus(simulationId, Simulation.STATUS_STOPPED);
        logger.info("Simulation {} stopped", simulationId);
    }
    
    /**
     * Fail a simulation.
     * 
     * @param simulationId Simulation UUID
     */
    public void failSimulation(UUID simulationId) {
        simulationDao.updateStatus(simulationId, Simulation.STATUS_FAILED);
        logger.info("Simulation {} failed", simulationId);
    }
    
    /**
     * Get a simulation by ID.
     * 
     * @param id Simulation UUID
     * @return Optional containing the Simulation if found
     */
    public Optional<Simulation> getSimulation(UUID id) {
        return simulationDao.findById(id);
    }
    
    /**
     * Get a simulation by token.
     * 
     * @param token Simulation token
     * @return Optional containing the Simulation if found
     */
    public Optional<Simulation> getSimulationByToken(String token) {
        return simulationDao.findByToken(token);
    }
    
    /**
     * Get all simulations accessible by a user.
     * 
     * @param userId User ID
     * @return List of accessible simulations
     */
    public List<Simulation> getSimulationsForUser(Long userId) {
        return simulationDao.findByUserAccess(userId);
    }
    
    /**
     * Get active simulations accessible by a user.
     * 
     * @param userId User ID
     * @return List of active accessible simulations
     */
    public List<Simulation> getActiveSimulationsForUser(Long userId) {
        return simulationDao.findActiveByUserAccess(userId);
    }
    
    /**
     * Get all batches for a simulation.
     * 
     * @param simulationId Simulation UUID
     * @return List of batches
     */
    public List<SimulationBatch> getBatches(UUID simulationId) {
        return batchDao.findBySimulationId(simulationId);
    }
    
    /**
     * Get a batch by ID.
     * 
     * @param batchId Batch UUID
     * @return Optional containing the batch if found
     */
    public Optional<SimulationBatch> getBatch(UUID batchId) {
        return batchDao.findById(batchId);
    }
    
    /**
     * Check if a user can access a simulation.
     * 
     * @param simulationId Simulation UUID
     * @param userId User ID
     * @return true if user can access
     */
    public boolean canUserAccess(UUID simulationId, Long userId) {
        return simulationDao.canUserAccess(simulationId, userId);
    }
    
    /**
     * Validate simulation token for multi-device access.
     * 
     * @param simulationId Simulation UUID
     * @param token Simulation token
     * @return true if token is valid
     */
    public boolean validateToken(UUID simulationId, String token) {
        return simulationDao.validateToken(simulationId, token);
    }
    
    /**
     * Generate simulation report data.
     * 
     * @param simulationId Simulation UUID
     * @return Report data as a map
     */
    public Map<String, Object> generateReport(UUID simulationId) {
        Map<String, Object> report = new HashMap<>();
        
        Optional<Simulation> simOpt = simulationDao.findById(simulationId);
        if (simOpt.isEmpty()) {
            report.put("error", "Simulation not found");
            return report;
        }
        
        Simulation simulation = simOpt.get();
        List<SimulationBatch> batches = batchDao.findBySimulationId(simulationId);
        
        // Simulation info
        report.put("simulation_id", simulation.getId().toString());
        report.put("title", simulation.getTitle());
        report.put("status", simulation.getStatus());
        report.put("started_at", simulation.getStartedAt().toString());
        if (simulation.getEndedAt() != null) {
            report.put("ended_at", simulation.getEndedAt().toString());
        }
        
        // User info
        report.put("owner_username", simulation.getOwnerUsername());
        report.put("supplier_username", simulation.getSupplierUsername());
        report.put("consumer_username", simulation.getConsumerUsername());
        
        // Batch summary
        report.put("total_batches", batches.size());
        report.put("batches", batches.stream().map(this::batchToMap).toList());
        
        // Calculate statistics
        Double avgQuality = batchDao.getAverageQualityScore(simulationId);
        if (avgQuality != null) {
            report.put("average_quality_score", avgQuality);
        }
        
        long deliveredCount = batches.stream()
            .filter(SimulationBatch::isDelivered)
            .count();
        report.put("delivered_count", deliveredCount);
        report.put("delivery_rate", batches.isEmpty() ? 0.0 : (double) deliveredCount / batches.size() * 100);
        
        return report;
    }
    
    private Map<String, Object> batchToMap(SimulationBatch batch) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", batch.getId().toString());
        map.put("batch_index", batch.getBatchIndex());
        map.put("quantity", batch.getQuantity());
        map.put("status", batch.getStatus());
        map.put("quality_score", batch.getQualityScore());
        map.put("temperature", batch.getTemperature());
        map.put("humidity", batch.getHumidity());
        map.put("current_location", batch.getCurrentLocation());
        map.put("progress", batch.getProgress());
        map.put("created_at", batch.getCreatedAt().toString());
        return map;
    }
    
    /**
     * Result of starting a simulation.
     */
    public static class SimulationStartResult {
        private final boolean success;
        private final Simulation simulation;
        private final String error;
        
        private SimulationStartResult(boolean success, Simulation simulation, String error) {
            this.success = success;
            this.simulation = simulation;
            this.error = error;
        }
        
        public static SimulationStartResult success(Simulation simulation) {
            return new SimulationStartResult(true, simulation, null);
        }
        
        public static SimulationStartResult error(String error) {
            return new SimulationStartResult(false, null, error);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public Simulation getSimulation() {
            return simulation;
        }
        
        public String getError() {
            return error;
        }
    }
}
