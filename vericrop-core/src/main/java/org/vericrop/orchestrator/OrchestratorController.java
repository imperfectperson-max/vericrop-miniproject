package org.vericrop.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for orchestrating coordinated runs of multiple subsystems.
 * Provides an API endpoint to trigger subsystem runs via Kafka messages.
 */
@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorController.class);
    
    private final OrchestratorService orchestratorService;
    
    @Autowired
    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }
    
    /**
     * Trigger a coordinated run of subsystems.
     * 
     * POST /api/orchestrator/run
     * 
     * Optional JSON payload:
     * {
     *   "subsystems": ["scenarios", "delivery", "map", "temperature", "supplier-compliance", "simulations"],
     *   "runId": "optional-custom-run-id"
     * }
     * 
     * If no payload or empty subsystems list is provided, all subsystems are triggered.
     * If runId is not provided, a UUID is generated automatically.
     * 
     * @param request Optional request body with subsystems list and runId
     * @return ResponseEntity with orchestration results
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> request) {
        try {
            // Extract subsystems from request, or use null to trigger all
            List<String> subsystems = null;
            String runId = UUID.randomUUID().toString();
            
            if (request != null) {
                if (request.containsKey("subsystems")) {
                    Object subsystemsObj = request.get("subsystems");
                    if (subsystemsObj instanceof List) {
                        subsystems = (List<String>) subsystemsObj;
                    } else {
                        logger.warn("Invalid subsystems value, expected List but got: {}", 
                                    subsystemsObj != null ? subsystemsObj.getClass().getName() : "null");
                    }
                }
                if (request.containsKey("runId")) {
                    Object runIdObj = request.get("runId");
                    if (runIdObj instanceof String) {
                        runId = (String) runIdObj;
                    } else {
                        logger.warn("Invalid runId value, expected String but got: {}", 
                                    runIdObj != null ? runIdObj.getClass().getName() : "null");
                    }
                }
            }
            
            logger.info("Received orchestration request for runId: {}, subsystems: {}", runId, subsystems);
            
            Map<String, Object> result = orchestratorService.runOrchestration(subsystems, runId);
            
            // Return 202 Accepted to indicate async processing has been triggered
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
            
        } catch (Exception e) {
            logger.error("Error during orchestration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Health check endpoint for the orchestrator.
     * 
     * GET /api/orchestrator/health
     * 
     * @return Simple health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "orchestrator"
        ));
    }
}
