package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for orchestrating concurrent scenario executions across all domains:
 * scenarios, delivery, map, temperature, supplier_compliance, and simulations.
 * 
 * This controller wires relationships between domains and enables parallel execution
 * of multiple scenarios triggered from a single request.
 */
public class ScenarioController {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioController.class);
    
    private final SimulationOrchestrator simulationOrchestrator;
    private final DeliverySimulator deliverySimulator;
    private final TemperatureService temperatureService;
    private final MapService mapService;
    private final SupplierComplianceService supplierComplianceService;
    private final MessageService messageService;
    
    private final Map<String, ScenarioExecution> activeExecutions;
    
    /**
     * Represents an active scenario execution with all domain states.
     */
    public static class ScenarioExecution {
        private final String executionId;
        private final String farmerId;
        private final Map<Scenario, String> scenarioBatchIds;
        private final long startTime;
        private volatile boolean running;
        
        public ScenarioExecution(String executionId, String farmerId, Map<Scenario, String> scenarioBatchIds) {
            this.executionId = executionId;
            this.farmerId = farmerId;
            this.scenarioBatchIds = new ConcurrentHashMap<>(scenarioBatchIds);
            this.startTime = System.currentTimeMillis();
            this.running = true;
        }
        
        public String getExecutionId() { return executionId; }
        public String getFarmerId() { return farmerId; }
        public Map<Scenario, String> getScenarioBatchIds() { return scenarioBatchIds; }
        public long getStartTime() { return startTime; }
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
    }
    
    /**
     * Result of a scenario execution including status from all domains.
     */
    public static class ScenarioExecutionResult {
        private final String executionId;
        private final Map<Scenario, String> scenarioBatchIds;
        private final Map<String, Object> deliveryResults;
        private final Map<String, Object> temperatureResults;
        private final Map<String, Object> mapResults;
        private final Map<String, Object> supplierResults;
        private final Map<String, Object> simulationResults;
        private final boolean success;
        private final String message;
        
        public ScenarioExecutionResult(String executionId, Map<Scenario, String> scenarioBatchIds,
                                      boolean success, String message) {
            this.executionId = executionId;
            this.scenarioBatchIds = scenarioBatchIds;
            this.deliveryResults = new HashMap<>();
            this.temperatureResults = new HashMap<>();
            this.mapResults = new HashMap<>();
            this.supplierResults = new HashMap<>();
            this.simulationResults = new HashMap<>();
            this.success = success;
            this.message = message;
        }
        
        public String getExecutionId() { return executionId; }
        public Map<Scenario, String> getScenarioBatchIds() { return scenarioBatchIds; }
        public Map<String, Object> getDeliveryResults() { return deliveryResults; }
        public Map<String, Object> getTemperatureResults() { return temperatureResults; }
        public Map<String, Object> getMapResults() { return mapResults; }
        public Map<String, Object> getSupplierResults() { return supplierResults; }
        public Map<String, Object> getSimulationResults() { return simulationResults; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    /**
     * Constructor with required domain services.
     */
    public ScenarioController(SimulationOrchestrator simulationOrchestrator,
                             DeliverySimulator deliverySimulator,
                             TemperatureService temperatureService,
                             MapService mapService,
                             SupplierComplianceService supplierComplianceService,
                             MessageService messageService) {
        this.simulationOrchestrator = simulationOrchestrator;
        this.deliverySimulator = deliverySimulator;
        this.temperatureService = temperatureService;
        this.mapService = mapService;
        this.supplierComplianceService = supplierComplianceService;
        this.messageService = messageService;
        this.activeExecutions = new ConcurrentHashMap<>();
        
        logger.info("ScenarioController initialized with all domain services");
    }
    
    /**
     * Execute multiple scenarios concurrently across all domains.
     * This is the primary orchestration method that triggers parallel execution.
     * 
     * @param origin Starting location
     * @param destination Ending location
     * @param numWaypoints Number of waypoints in each route
     * @param avgSpeedKmh Average speed in km/h
     * @param farmerId Farmer/supplier identifier
     * @param scenarios List of scenarios to execute concurrently
     * @param updateIntervalMs Update interval for simulations
     * @return CompletableFuture with execution result
     */
    public CompletableFuture<ScenarioExecutionResult> executeScenarios(
            GeoCoordinate origin,
            GeoCoordinate destination,
            int numWaypoints,
            double avgSpeedKmh,
            String farmerId,
            List<Scenario> scenarios,
            long updateIntervalMs) {
        
        String executionId = "EXEC_" + UUID.randomUUID().toString().substring(0, 8);
        logger.info("Starting concurrent scenario execution: {} with {} scenarios for farmer: {}",
                   executionId, scenarios.size(), farmerId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Phase 1: Initialize all domain services for each scenario
                Map<Scenario, String> scenarioBatchIds = new HashMap<>();
                for (Scenario scenario : scenarios) {
                    String batchId = generateBatchId(farmerId, scenario);
                    scenarioBatchIds.put(scenario, batchId);
                    
                    // Initialize temperature monitoring
                    temperatureService.startMonitoring(batchId);
                    logger.debug("Initialized temperature monitoring for batch: {}", batchId);
                }
                
                // Phase 2: Generate routes using MapService for all scenarios concurrently
                List<CompletableFuture<Void>> routeFutures = new ArrayList<>();
                for (Map.Entry<Scenario, String> entry : scenarioBatchIds.entrySet()) {
                    Scenario scenario = entry.getKey();
                    String batchId = entry.getValue();
                    
                    CompletableFuture<Void> routeFuture = CompletableFuture.runAsync(() -> {
                        List<RouteWaypoint> route = mapService.generateRoute(
                            batchId, origin, destination, numWaypoints, 
                            System.currentTimeMillis(), avgSpeedKmh, scenario);
                        
                        // Record temperature readings from route
                        temperatureService.recordRoute(batchId, route);
                        logger.debug("Generated route and recorded temperatures for batch: {}", batchId);
                    });
                    
                    routeFutures.add(routeFuture);
                }
                
                // Wait for all routes to be generated
                CompletableFuture.allOf(routeFutures.toArray(new CompletableFuture[0])).join();
                logger.info("All routes generated for execution: {}", executionId);
                
                // Phase 3: Start concurrent simulations via orchestrator
                // Convert models.GeoCoordinate to DeliverySimulator.GeoCoordinate
                DeliverySimulator.GeoCoordinate simOrigin = DeliverySimulator.GeoCoordinate.fromModel(origin);
                DeliverySimulator.GeoCoordinate simDestination = DeliverySimulator.GeoCoordinate.fromModel(destination);
                
                Map<Scenario, String> orchestratedBatchIds = simulationOrchestrator.startConcurrentScenarios(
                    simOrigin, simDestination, numWaypoints, avgSpeedKmh, farmerId, scenarios, updateIntervalMs);
                
                // Store execution state
                ScenarioExecution execution = new ScenarioExecution(executionId, farmerId, orchestratedBatchIds);
                activeExecutions.put(executionId, execution);
                
                // Phase 4: Collect results from all domains
                ScenarioExecutionResult result = new ScenarioExecutionResult(
                    executionId, orchestratedBatchIds, true,
                    String.format("Successfully started %d concurrent scenarios", scenarios.size()));
                
                // Populate domain-specific results
                populateDomainResults(result, orchestratedBatchIds, farmerId);
                
                logger.info("Scenario execution {} started successfully with {} scenarios",
                           executionId, scenarios.size());
                
                return result;
                
            } catch (Exception e) {
                logger.error("Failed to execute scenarios for execution: {}", executionId, e);
                return new ScenarioExecutionResult(executionId, Collections.emptyMap(), false,
                                                  "Failed to start scenarios: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get status of an execution across all domains.
     */
    public Map<String, Object> getExecutionStatus(String executionId) {
        ScenarioExecution execution = activeExecutions.get(executionId);
        if (execution == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("execution_id", execution.getExecutionId());
        status.put("farmer_id", execution.getFarmerId());
        status.put("start_time", execution.getStartTime());
        status.put("running", execution.isRunning());
        status.put("scenario_count", execution.getScenarioBatchIds().size());
        
        // Get status from each domain
        Map<String, Object> domainStatuses = new HashMap<>();
        
        for (Map.Entry<Scenario, String> entry : execution.getScenarioBatchIds().entrySet()) {
            String batchId = entry.getValue();
            Map<String, Object> batchStatus = new HashMap<>();
            
            // Delivery status
            DeliverySimulator.SimulationStatus simStatus = deliverySimulator.getSimulationStatus(batchId);
            batchStatus.put("delivery_running", simStatus.isRunning());
            batchStatus.put("current_waypoint", simStatus.getCurrentWaypoint());
            batchStatus.put("total_waypoints", simStatus.getTotalWaypoints());
            
            // Temperature status
            TemperatureService.TemperatureMonitoring tempStatus = temperatureService.getMonitoring(batchId);
            if (tempStatus != null) {
                batchStatus.put("temperature_compliant", tempStatus.isCompliant());
                batchStatus.put("temperature_violations", tempStatus.getViolationCount());
            }
            
            // Map status
            MapService.RouteMetadata routeMetadata = mapService.getRouteMetadata(batchId);
            if (routeMetadata != null) {
                batchStatus.put("route_distance", routeMetadata.getTotalDistance());
                batchStatus.put("route_duration", routeMetadata.getEstimatedDuration());
            }
            
            domainStatuses.put(entry.getKey().name(), batchStatus);
        }
        
        status.put("domain_statuses", domainStatuses);
        
        // Supplier compliance
        SupplierComplianceService.SupplierComplianceData complianceData = 
            supplierComplianceService.getComplianceData(execution.getFarmerId());
        if (complianceData != null) {
            Map<String, Object> supplierStatus = new HashMap<>();
            supplierStatus.put("compliant", complianceData.isCompliant());
            supplierStatus.put("compliance_status", complianceData.getComplianceStatus());
            supplierStatus.put("success_rate", complianceData.getSuccessRate());
            status.put("supplier_compliance", supplierStatus);
        }
        
        return status;
    }
    
    /**
     * Stop an execution and cleanup resources across all domains.
     */
    public void stopExecution(String executionId) {
        ScenarioExecution execution = activeExecutions.get(executionId);
        if (execution == null) {
            logger.warn("No active execution found: {}", executionId);
            return;
        }
        
        execution.setRunning(false);
        
        // Stop simulations
        for (String batchId : execution.getScenarioBatchIds().values()) {
            deliverySimulator.stopSimulation(batchId);
            temperatureService.stopMonitoring(batchId);
            mapService.clearRoute(batchId);
        }
        
        activeExecutions.remove(executionId);
        logger.info("Stopped execution: {} and cleaned up all domain resources", executionId);
    }
    
    /**
     * Get all active executions.
     */
    public Map<String, ScenarioExecution> getActiveExecutions() {
        return new ConcurrentHashMap<>(activeExecutions);
    }
    
    /**
     * Populate domain-specific results in the execution result.
     */
    private void populateDomainResults(ScenarioExecutionResult result, 
                                      Map<Scenario, String> batchIds, String farmerId) {
        // Delivery results
        result.getDeliveryResults().put("batch_count", batchIds.size());
        result.getDeliveryResults().put("batch_ids", new ArrayList<>(batchIds.values()));
        
        // Temperature results
        result.getTemperatureResults().put("monitoring_active", batchIds.size());
        result.getTemperatureResults().put("batch_ids", new ArrayList<>(batchIds.values()));
        
        // Map results
        result.getMapResults().put("routes_generated", batchIds.size());
        result.getMapResults().put("batch_ids", new ArrayList<>(batchIds.values()));
        
        // Supplier results
        SupplierComplianceService.SupplierComplianceData complianceData = 
            supplierComplianceService.getComplianceData(farmerId);
        if (complianceData != null) {
            result.getSupplierResults().put("farmer_id", farmerId);
            result.getSupplierResults().put("compliant", complianceData.isCompliant());
            result.getSupplierResults().put("compliance_status", complianceData.getComplianceStatus());
        }
        
        // Simulation results
        result.getSimulationResults().put("scenarios_started", batchIds.size());
        result.getSimulationResults().put("orchestration_active", true);
    }
    
    /**
     * Generate a unique batch ID for a scenario.
     */
    private String generateBatchId(String farmerId, Scenario scenario) {
        return String.format("BATCH_%s_%s_%s", 
                           farmerId != null ? farmerId : "UNKNOWN",
                           scenario.name(),
                           UUID.randomUUID().toString().substring(0, 8));
    }
}
