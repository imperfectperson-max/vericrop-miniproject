package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.kafka.coordination.SimulationCoordinationService;
import org.vericrop.kafka.events.SimulationStartEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for triggering coordinated simulations via HTTP.
 * Provides an HTTP endpoint to publish simulation start messages to Kafka.
 */
@RestController
@RequestMapping("/api/v1/simulation")
@CrossOrigin(origins = "*")
public class SimulationCoordinationController {
    private static final Logger logger = LoggerFactory.getLogger(SimulationCoordinationController.class);
    
    private final SimulationCoordinationService coordinationService;
    private final boolean coordinationEnabled;
    
    public SimulationCoordinationController() {
        // Check if simulation coordination is enabled via environment variable or system property
        String coordEnabledEnv = System.getenv("SIMULATION_COORDINATION_ENABLED");
        String coordEnabledProp = System.getProperty("simulation.coordination.enabled");
        
        this.coordinationEnabled = "true".equalsIgnoreCase(coordEnabledEnv) ||
                                   "true".equalsIgnoreCase(coordEnabledProp);
        
        if (coordinationEnabled) {
            // Check if Kafka is enabled
            String kafkaEnabled = System.getenv("KAFKA_ENABLED");
            boolean useKafka = kafkaEnabled == null || "true".equalsIgnoreCase(kafkaEnabled);
            this.coordinationService = new SimulationCoordinationService(useKafka);
            logger.info("SimulationCoordinationController initialized with coordination ENABLED");
        } else {
            this.coordinationService = null;
            logger.info("SimulationCoordinationController initialized with coordination DISABLED (feature flag off)");
        }
    }
    
    /**
     * POST /api/v1/simulation/trigger
     * Trigger coordinated simulations by publishing a start message to Kafka.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerSimulations(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        if (!coordinationEnabled) {
            response.put("success", false);
            response.put("error", "Simulation coordination is disabled. Set SIMULATION_COORDINATION_ENABLED=true to enable.");
            logger.warn("Simulation trigger attempt rejected - coordination feature is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        
        try {
            // Extract or generate runId
            String runId = request != null && request.containsKey("runId") ?
                    (String) request.get("runId") :
                    "RUN_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create and publish start event
            SimulationStartEvent event = new SimulationStartEvent(runId, "http-api");
            if (request != null) {
                event.setParameters(request);
            }
            
            boolean published = coordinationService.publishSimulationStart(event);
            
            if (published) {
                response.put("success", true);
                response.put("runId", runId);
                response.put("message", "Simulation start event published successfully");
                response.put("timestamp", event.getTimestamp());
                
                logger.info("Successfully triggered simulations with runId: {}", runId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Failed to publish simulation start event");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Failed to trigger simulations", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/v1/simulation/status
     * Check if simulation coordination is enabled.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("coordination_enabled", coordinationEnabled);
        
        if (coordinationEnabled) {
            response.put("kafka_enabled", coordinationService.isKafkaEnabled());
        }
        
        return ResponseEntity.ok(response);
    }
}
