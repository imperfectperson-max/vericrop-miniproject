package org.vericrop.gui.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.gui.services.MaintenanceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for maintenance operations.
 * Provides admin-only endpoints to:
 * - Delete all Batch and Simulation records (with backup)
 * - View record counts
 * - List available backups
 * 
 * <h2>Security</h2>
 * <p>All destructive operations require either:</p>
 * <ul>
 *   <li>MAINTENANCE_MODE=true environment variable, OR</li>
 *   <li>Confirmation token: "CONFIRM_DELETE_ALL_RECORDS" in request body</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * <pre>
 * # Check record counts
 * GET /api/maintenance/counts
 * 
 * # Delete all records with backup (requires confirmation)
 * POST /api/maintenance/delete-all
 * Body: {"confirmation_token": "CONFIRM_DELETE_ALL_RECORDS"}
 * 
 * # List available backups
 * GET /api/maintenance/backups
 * </pre>
 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private static final Logger logger = LoggerFactory.getLogger(MaintenanceController.class);
    
    private final MaintenanceService maintenanceService;
    
    @Autowired
    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }
    
    /**
     * GET /api/maintenance/counts
     * Get current record counts for simulations and batches.
     * 
     * @return Record counts
     */
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Object>> getRecordCounts() {
        try {
            Map<String, Object> counts = maintenanceService.getRecordCounts();
            counts.put("maintenance_mode_enabled", maintenanceService.isMaintenanceModeEnabled());
            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            logger.error("Error getting record counts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get record counts: " + e.getMessage()));
        }
    }
    
    /**
     * POST /api/maintenance/delete-all
     * Delete all Batch and Simulation records with timestamped backup.
     * 
     * <p>Requires confirmation via one of:</p>
     * <ul>
     *   <li>MAINTENANCE_MODE=true environment variable</li>
     *   <li>confirmation_token: "CONFIRM_DELETE_ALL_RECORDS" in request body</li>
     * </ul>
     * 
     * <p>Before deletion, creates backup in backups/{timestamp}/ directory.</p>
     * 
     * @param request Request body with optional confirmation_token
     * @return Result of the delete operation
     */
    @PostMapping("/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAllRecords(@RequestBody(required = false) Map<String, Object> request) {
        try {
            logger.info("Received request to delete all Batch and Simulation records");
            
            // Extract confirmation token from request
            String confirmationToken = null;
            if (request != null) {
                confirmationToken = (String) request.get("confirmation_token");
            }
            
            // Execute maintenance operation
            MaintenanceService.MaintenanceResult result = 
                maintenanceService.deleteAllRecordsWithBackup(confirmationToken);
            
            if (result.isSuccess()) {
                logger.info("Delete operation completed successfully: {} simulations, {} batches deleted",
                           result.getSimulationsDeleted(), result.getBatchesDeleted());
                return ResponseEntity.ok(result.toMap());
            } else {
                logger.warn("Delete operation denied: {}", result.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result.toMap());
            }
            
        } catch (Exception e) {
            logger.error("Error during delete operation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Delete operation failed: " + e.getMessage()));
        }
    }
    
    /**
     * GET /api/maintenance/backups
     * List all available backups.
     * 
     * @return List of backup timestamps/directories
     */
    @GetMapping("/backups")
    public ResponseEntity<Map<String, Object>> listBackups() {
        try {
            List<String> backups = maintenanceService.listBackups();
            
            Map<String, Object> response = new HashMap<>();
            response.put("backups", backups);
            response.put("count", backups.size());
            response.put("backup_directory", "backups/");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error listing backups", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to list backups: " + e.getMessage()));
        }
    }
    
    /**
     * GET /api/maintenance/status
     * Get maintenance service status including whether maintenance mode is enabled.
     * 
     * @return Maintenance status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMaintenanceStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("service", "maintenance");
            status.put("status", "UP");
            status.put("maintenance_mode_enabled", maintenanceService.isMaintenanceModeEnabled());
            status.put("confirmation_token_required", !maintenanceService.isMaintenanceModeEnabled());
            status.put("confirmation_token_hint", "Use 'CONFIRM_DELETE_ALL_RECORDS' to confirm destructive operations");
            status.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting maintenance status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to get maintenance status: " + e.getMessage()));
        }
    }
    
    /**
     * POST /api/maintenance/dry-run
     * Perform a dry-run of the delete operation (backup only, no deletion).
     * Useful for testing the backup mechanism.
     * 
     * @return Result of the backup operation
     */
    @PostMapping("/dry-run")
    public ResponseEntity<Map<String, Object>> dryRun() {
        try {
            logger.info("Performing dry-run (backup only)...");
            
            Map<String, Object> counts = maintenanceService.getRecordCounts();
            
            Map<String, Object> response = new HashMap<>();
            response.put("dry_run", true);
            response.put("would_delete_simulations", counts.get("simulations"));
            response.put("would_delete_batches", counts.get("batches"));
            response.put("message", "Dry run completed. No records were deleted.");
            response.put("to_execute", "POST /api/maintenance/delete-all with confirmation_token");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during dry run", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Dry run failed: " + e.getMessage()));
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
