package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vericrop.gui.dao.SimulationBatchDao;
import org.vericrop.gui.dao.SimulationDao;
import org.vericrop.gui.models.Simulation;
import org.vericrop.gui.models.SimulationBatch;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for maintenance operations on Batch and Simulation records.
 * Provides functionality to:
 * - Create timestamped backups of all records before deletion
 * - Delete all Batch and Simulation records (with safety gating)
 * - Log all operations for audit purposes
 * 
 * <h2>Safety Features</h2>
 * <ul>
 *   <li>Requires MAINTENANCE_MODE=true environment variable OR explicit confirmation token</li>
 *   <li>Creates backup before any deletion</li>
 *   <li>Operations are idempotent (safe to run multiple times)</li>
 *   <li>All operations are logged with counts</li>
 * </ul>
 */
@Service
public class MaintenanceService {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceService.class);
    
    private static final String BACKUP_DIR = "backups";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String CONFIRMATION_TOKEN = "CONFIRM_DELETE_ALL_RECORDS";
    
    private final SimulationDao simulationDao;
    private final SimulationBatchDao batchDao;
    private final ObjectMapper objectMapper;
    
    @Value("${maintenance.mode:false}")
    private boolean maintenanceModeEnabled;
    
    public MaintenanceService(DataSource dataSource) {
        this.simulationDao = new SimulationDao(dataSource);
        this.batchDao = new SimulationBatchDao(dataSource);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Result of a maintenance operation.
     */
    public static class MaintenanceResult {
        private final boolean success;
        private final String message;
        private final int simulationsDeleted;
        private final int batchesDeleted;
        private final String backupPath;
        private final Map<String, Object> details;
        
        private MaintenanceResult(boolean success, String message, int simulationsDeleted, 
                                   int batchesDeleted, String backupPath, Map<String, Object> details) {
            this.success = success;
            this.message = message;
            this.simulationsDeleted = simulationsDeleted;
            this.batchesDeleted = batchesDeleted;
            this.backupPath = backupPath;
            this.details = details;
        }
        
        public static MaintenanceResult success(int simulationsDeleted, int batchesDeleted, 
                                                 String backupPath, Map<String, Object> details) {
            return new MaintenanceResult(true, "Maintenance operation completed successfully", 
                                         simulationsDeleted, batchesDeleted, backupPath, details);
        }
        
        public static MaintenanceResult error(String message) {
            return new MaintenanceResult(false, message, 0, 0, null, new HashMap<>());
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSimulationsDeleted() { return simulationsDeleted; }
        public int getBatchesDeleted() { return batchesDeleted; }
        public String getBackupPath() { return backupPath; }
        public Map<String, Object> getDetails() { return details; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("success", success);
            map.put("message", message);
            map.put("simulations_deleted", simulationsDeleted);
            map.put("batches_deleted", batchesDeleted);
            map.put("backup_path", backupPath);
            map.put("timestamp", System.currentTimeMillis());
            map.putAll(details);
            return map;
        }
    }
    
    /**
     * Check if maintenance operations are allowed.
     * 
     * @param confirmationToken Token provided by caller for confirmation
     * @return true if operations are allowed
     */
    public boolean isMaintenanceAllowed(String confirmationToken) {
        // Allow if MAINTENANCE_MODE is enabled OR correct confirmation token provided
        if (maintenanceModeEnabled) {
            logger.info("Maintenance mode enabled via environment variable");
            return true;
        }
        if (CONFIRMATION_TOKEN.equals(confirmationToken)) {
            logger.info("Maintenance operation confirmed via token");
            return true;
        }
        return false;
    }
    
    /**
     * Delete all Batch and Simulation records with backup.
     * This operation:
     * 1. Validates that maintenance is allowed (via env var or token)
     * 2. Creates a timestamped backup of all records
     * 3. Deletes all simulation batches first (due to FK constraints)
     * 4. Deletes all simulations
     * 5. Returns a detailed result
     * 
     * @param confirmationToken Confirmation token (use "CONFIRM_DELETE_ALL_RECORDS")
     * @return MaintenanceResult with operation details
     */
    public MaintenanceResult deleteAllRecordsWithBackup(String confirmationToken) {
        logger.info("=== Starting maintenance operation: Delete all Batch and Simulation records ===");
        
        // Check authorization
        if (!isMaintenanceAllowed(confirmationToken)) {
            String msg = "Maintenance operation not allowed. Set MAINTENANCE_MODE=true or provide confirmation token.";
            logger.warn(msg);
            return MaintenanceResult.error(msg);
        }
        
        try {
            // Step 1: Get all records for backup
            logger.info("Step 1: Fetching all records for backup...");
            List<Simulation> simulations = simulationDao.findAll();
            List<SimulationBatch> batches = batchDao.findAll();
            
            logger.info("Found {} simulations and {} batches to backup and delete", 
                       simulations.size(), batches.size());
            
            // Step 2: Create backup
            logger.info("Step 2: Creating backup...");
            String backupPath = createBackup(simulations, batches);
            logger.info("Backup created at: {}", backupPath);
            
            // Step 3: Delete batches first (FK constraint)
            logger.info("Step 3: Deleting simulation batches...");
            int batchesDeleted = batchDao.deleteAll();
            logger.info("Deleted {} simulation batches", batchesDeleted);
            
            // Step 4: Delete simulations
            logger.info("Step 4: Deleting simulations...");
            int simulationsDeleted = simulationDao.deleteAll();
            logger.info("Deleted {} simulations", simulationsDeleted);
            
            // Build details
            Map<String, Object> details = new HashMap<>();
            details.put("simulations_backed_up", simulations.size());
            details.put("batches_backed_up", batches.size());
            details.put("operation", "delete_all_records");
            details.put("executed_at", LocalDateTime.now().toString());
            
            logger.info("=== Maintenance operation completed successfully ===");
            logger.info("Summary: {} simulations deleted, {} batches deleted, backup at {}", 
                       simulationsDeleted, batchesDeleted, backupPath);
            
            return MaintenanceResult.success(simulationsDeleted, batchesDeleted, backupPath, details);
            
        } catch (Exception e) {
            logger.error("Maintenance operation failed: {}", e.getMessage(), e);
            return MaintenanceResult.error("Maintenance operation failed: " + e.getMessage());
        }
    }
    
    /**
     * Create a timestamped backup of simulations and batches.
     * 
     * @param simulations List of simulations to backup
     * @param batches List of batches to backup
     * @return Path to the backup directory
     * @throws IOException If backup creation fails
     */
    public String createBackup(List<Simulation> simulations, List<SimulationBatch> batches) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        Path backupDir = Paths.get(BACKUP_DIR, timestamp);
        
        // Create backup directory
        Files.createDirectories(backupDir);
        logger.info("Created backup directory: {}", backupDir.toAbsolutePath());
        
        // Backup simulations
        Path simulationsFile = backupDir.resolve("simulations.json");
        Map<String, Object> simulationsBackup = new HashMap<>();
        simulationsBackup.put("count", simulations.size());
        simulationsBackup.put("backed_up_at", LocalDateTime.now().toString());
        simulationsBackup.put("records", simulations.stream()
            .map(this::simulationToMap)
            .toList());
        objectMapper.writeValue(simulationsFile.toFile(), simulationsBackup);
        logger.info("Backed up {} simulations to {}", simulations.size(), simulationsFile);
        
        // Backup batches
        Path batchesFile = backupDir.resolve("batches.json");
        Map<String, Object> batchesBackup = new HashMap<>();
        batchesBackup.put("count", batches.size());
        batchesBackup.put("backed_up_at", LocalDateTime.now().toString());
        batchesBackup.put("records", batches.stream()
            .map(this::batchToMap)
            .toList());
        objectMapper.writeValue(batchesFile.toFile(), batchesBackup);
        logger.info("Backed up {} batches to {}", batches.size(), batchesFile);
        
        // Create a manifest file
        Path manifestFile = backupDir.resolve("manifest.json");
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("backup_timestamp", timestamp);
        manifest.put("created_at", LocalDateTime.now().toString());
        manifest.put("simulations_count", simulations.size());
        manifest.put("batches_count", batches.size());
        manifest.put("files", List.of("simulations.json", "batches.json"));
        manifest.put("restore_instructions", 
            "To restore: Use SQL INSERT statements or a restore script. " +
            "See docs/MAINTENANCE.md for details.");
        objectMapper.writeValue(manifestFile.toFile(), manifest);
        
        return backupDir.toAbsolutePath().toString();
    }
    
    /**
     * Get the current record counts for simulations and batches.
     * 
     * @return Map with counts
     */
    public Map<String, Object> getRecordCounts() {
        Map<String, Object> counts = new HashMap<>();
        counts.put("simulations", simulationDao.findAll().size());
        counts.put("batches", batchDao.findAll().size());
        counts.put("timestamp", System.currentTimeMillis());
        return counts;
    }
    
    /**
     * List available backups.
     * 
     * @return List of backup directory names
     */
    public List<String> listBackups() {
        Path backupsPath = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupsPath)) {
            return List.of();
        }
        
        try {
            return Files.list(backupsPath)
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        } catch (IOException e) {
            logger.error("Error listing backups: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Check if maintenance mode is enabled via environment variable.
     */
    public boolean isMaintenanceModeEnabled() {
        return maintenanceModeEnabled;
    }
    
    /**
     * Get the confirmation token required for operations.
     * This is intentionally exposed for documentation purposes.
     */
    public String getConfirmationToken() {
        return CONFIRMATION_TOKEN;
    }
    
    private Map<String, Object> simulationToMap(Simulation simulation) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", simulation.getId() != null ? simulation.getId().toString() : null);
        map.put("title", simulation.getTitle());
        map.put("status", simulation.getStatus());
        map.put("owner_user_id", simulation.getOwnerUserId());
        map.put("supplier_user_id", simulation.getSupplierUserId());
        map.put("consumer_user_id", simulation.getConsumerUserId());
        map.put("owner_username", simulation.getOwnerUsername());
        map.put("supplier_username", simulation.getSupplierUsername());
        map.put("consumer_username", simulation.getConsumerUsername());
        map.put("simulation_token", simulation.getSimulationToken());
        map.put("started_at", simulation.getStartedAt() != null ? simulation.getStartedAt().toString() : null);
        map.put("ended_at", simulation.getEndedAt() != null ? simulation.getEndedAt().toString() : null);
        map.put("created_at", simulation.getCreatedAt() != null ? simulation.getCreatedAt().toString() : null);
        map.put("updated_at", simulation.getUpdatedAt() != null ? simulation.getUpdatedAt().toString() : null);
        return map;
    }
    
    private Map<String, Object> batchToMap(SimulationBatch batch) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", batch.getId() != null ? batch.getId().toString() : null);
        map.put("simulation_id", batch.getSimulationId() != null ? batch.getSimulationId().toString() : null);
        map.put("batch_index", batch.getBatchIndex());
        map.put("quantity", batch.getQuantity());
        map.put("status", batch.getStatus());
        map.put("quality_score", batch.getQualityScore());
        map.put("temperature", batch.getTemperature());
        map.put("humidity", batch.getHumidity());
        map.put("current_location", batch.getCurrentLocation());
        map.put("progress", batch.getProgress());
        map.put("created_at", batch.getCreatedAt() != null ? batch.getCreatedAt().toString() : null);
        map.put("updated_at", batch.getUpdatedAt() != null ? batch.getUpdatedAt().toString() : null);
        return map;
    }
}
