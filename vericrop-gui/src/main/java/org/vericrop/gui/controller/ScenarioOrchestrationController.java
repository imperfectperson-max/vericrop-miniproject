package org.vericrop.gui.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.dto.ScenarioRunRequest;
import org.vericrop.dto.ScenarioRunResult;
import org.vericrop.kafka.producers.ScenarioOrchestrationProducer;
import org.vericrop.service.*;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for scenario orchestration.
 * Provides endpoints to trigger and monitor concurrent scenario execution.
 */
@RestController
@RequestMapping("/scenarios")
@CrossOrigin(origins = "*")
@Tag(name = "Scenario Orchestration", description = "Concurrent scenario execution and monitoring API")
public class ScenarioOrchestrationController {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioOrchestrationController.class);
    
    private final ScenarioOrchestrator scenarioOrchestrator;
    private final ScenarioOrchestrationProducer kafkaProducer;
    
    @Autowired(required = false)
    public ScenarioOrchestrationController(
            SimulationOrchestrator simulationOrchestrator,
            DeliverySimulator deliverySimulator,
            TemperatureService temperatureService,
            MapService mapService,
            SupplierComplianceService supplierComplianceService) {
        
        // Initialize orchestrator with required services
        MessageService messageService = new MessageService();
        AlertService alertService = new AlertService();
        
        this.scenarioOrchestrator = new ScenarioOrchestrator(
            simulationOrchestrator != null ? simulationOrchestrator : createDefaultSimulationOrchestrator(messageService, alertService),
            deliverySimulator != null ? deliverySimulator : new DeliverySimulator(messageService, alertService),
            temperatureService != null ? temperatureService : new TemperatureService(),
            mapService != null ? mapService : new MapService(),
            supplierComplianceService != null ? supplierComplianceService : new SupplierComplianceService()
        );
        
        // Initialize Kafka producer
        ScenarioOrchestrationProducer producer = null;
        try {
            producer = new ScenarioOrchestrationProducer();
            this.scenarioOrchestrator.setScenarioEventPublisher(producer);
            logger.info("Kafka producer initialized for scenario orchestration");
        } catch (Exception e) {
            logger.warn("Kafka producer not available, continuing without Kafka: {}", e.getMessage());
        }
        this.kafkaProducer = producer;
        
        logger.info("ScenarioOrchestrationController initialized");
    }
    
    /**
     * POST /scenarios/orchestrate
     * Trigger concurrent execution of multiple scenarios.
     */
    @Operation(
        summary = "Orchestrate concurrent scenario execution",
        description = "Triggers concurrent execution of enabled scenarios (delivery, map, temperature, supplierCompliance, simulations). Returns immediately with 202 Accepted and provides runId for status tracking."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Orchestration accepted and started",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = "{\"run_id\":\"123e4567-e89b-12d3-a456-426614174000\",\"status\":\"ACCEPTED\",\"message\":\"Scenario orchestration started\",\"status_location\":\"/scenarios/status/123e4567-e89b-12d3-a456-426614174000\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid request - no scenarios enabled"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/orchestrate")
    public ResponseEntity<ScenarioRunResult> orchestrateScenarios(
            @Parameter(description = "Scenario run request with enabled scenario flags")
            @RequestBody ScenarioRunRequest request) {
        
        logger.info("Received scenario orchestration request: {}", request);
        
        try {
            // Validate request
            if (!request.hasAnyScenarioEnabled()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Start orchestration (returns immediately with ACCEPTED status)
            ScenarioRunResult result = scenarioOrchestrator.runScenariosConcurrently(request);
            
            // Build response with status location header
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", "/scenarios/status/" + result.getRunId());
            
            logger.info("Orchestration started with runId: {}", result.getRunId());
            
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .headers(headers)
                    .body(result);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid orchestration request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error starting scenario orchestration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /scenarios/status/{runId}
     * Get the status of a scenario orchestration run.
     */
    @Operation(
        summary = "Get scenario run status",
        description = "Retrieves the current status and results of a scenario orchestration run."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Run ID not found")
    })
    @GetMapping("/status/{runId}")
    public ResponseEntity<ScenarioRunResult> getRunStatus(
            @Parameter(description = "Unique run identifier")
            @PathVariable String runId) {
        
        logger.debug("Status request for runId: {}", runId);
        
        try {
            ScenarioRunResult result = scenarioOrchestrator.getRunStatus(runId);
            
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error retrieving run status for: {}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /scenarios/runs
     * Get all active orchestration runs.
     */
    @Operation(
        summary = "Get all active runs",
        description = "Retrieves all currently active scenario orchestration runs."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Runs retrieved successfully")
    })
    @GetMapping("/runs")
    public ResponseEntity<Map<String, ScenarioRunResult>> getActiveRuns() {
        
        logger.debug("Active runs request");
        
        try {
            Map<String, ScenarioRunResult> activeRuns = scenarioOrchestrator.getActiveRuns();
            return ResponseEntity.ok(activeRuns);
            
        } catch (Exception e) {
            logger.error("Error retrieving active runs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Helper method to create default SimulationOrchestrator if not provided.
     */
    private SimulationOrchestrator createDefaultSimulationOrchestrator(MessageService messageService, 
                                                                       AlertService alertService) {
        try {
            DeliverySimulator deliverySimulator = new DeliverySimulator(messageService, alertService);
            return new SimulationOrchestrator(deliverySimulator, alertService, messageService);
        } catch (Exception e) {
            logger.error("Failed to create default SimulationOrchestrator", e);
            throw new RuntimeException("Cannot initialize orchestration controller", e);
        }
    }
    
    /**
     * Cleanup resources on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up ScenarioOrchestrationController resources");
        
        if (scenarioOrchestrator != null) {
            scenarioOrchestrator.shutdown();
        }
        
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
    }
}
