package org.vericrop.gui.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.service.MapSimulator;
import org.vericrop.service.ScenarioManager;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for simulation and map state access.
 * Provides endpoints to retrieve current map simulation state and scenario information.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationRestController {
    private static final Logger logger = LoggerFactory.getLogger(SimulationRestController.class);
    
    private final MapSimulator mapSimulator;
    private final ScenarioManager scenarioManager;
    
    /**
     * Constructor with dependencies.
     */
    public SimulationRestController(MapSimulator mapSimulator, ScenarioManager scenarioManager) {
        this.mapSimulator = mapSimulator;
        this.scenarioManager = scenarioManager;
        logger.info("SimulationRestController initialized");
    }
    
    /**
     * Get current map state snapshot.
     * 
     * @return Map snapshot with entity positions and metadata
     */
    @GetMapping("/map")
    public ResponseEntity<Map<String, Object>> getMapSnapshot() {
        try {
            MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
            
            if (snapshot == null) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .body(createErrorResponse("No active simulation"));
            }
            
            Map<String, Object> response = snapshot.toMap();
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving map snapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve map snapshot: " + e.getMessage()));
        }
    }
    
    /**
     * Get information about all available scenarios.
     * 
     * @return List of scenarios with their configurations
     */
    @GetMapping("/scenarios")
    public ResponseEntity<Map<String, Object>> getScenarios() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("scenarios", scenarioManager.getAllScenarioInfo());
            response.put("default_scenario", scenarioManager.getDefaultScenarioId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving scenarios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve scenarios: " + e.getMessage()));
        }
    }
    
    /**
     * Get information about a specific scenario.
     * 
     * @param scenarioId Scenario ID
     * @return Scenario information
     */
    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<Map<String, Object>> getScenarioInfo(@PathVariable String scenarioId) {
        try {
            if (!scenarioManager.isValidScenario(scenarioId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Scenario not found: " + scenarioId));
            }
            
            Map<String, Object> info = scenarioManager.getScenarioInfo(scenarioId);
            return ResponseEntity.ok(info);
            
        } catch (Exception e) {
            logger.error("Error retrieving scenario info for {}", scenarioId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve scenario info: " + e.getMessage()));
        }
    }
    
    /**
     * Get current simulation status including map state.
     * 
     * @return Combined simulation status with map snapshot
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Get map snapshot
            MapSimulator.MapSnapshot snapshot = mapSimulator.getSnapshot();
            if (snapshot != null) {
                status.put("active", true);
                status.put("current_step", snapshot.getSimulationStep());
                status.put("scenario_id", snapshot.getScenarioId());
                status.put("entity_count", snapshot.getEntities().size());
                status.put("map_state", snapshot.toMap());
            } else {
                status.put("active", false);
                status.put("message", "No active simulation");
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving simulation status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve status: " + e.getMessage()));
        }
    }
    
    /**
     * Health check endpoint for the simulation API.
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "simulation-api");
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Create an error response map.
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
