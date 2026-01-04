package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.Message;
import org.vericrop.service.models.Scenario;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multiple concurrent delivery simulations.
 * Manages thread pool execution, status monitoring, and event publishing.
 * Enhanced with Kafka integration for domain-specific event publishing.
 */
public class SimulationOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(SimulationOrchestrator.class);
    private static final int ORCHESTRATOR_THREAD_POOL_SIZE = 20;
    private static final long STATUS_UPDATE_INTERVAL_MS = 5000; // 5 seconds
    private static final int BATCH_ID_UUID_LENGTH = 8;
    
    private final DeliverySimulator deliverySimulator;
    private final AlertService alertService;
    private final MessageService messageService;
    private final ExecutorService executorService;
    private final ScheduledExecutorService statusMonitor;
    private final ConcurrentHashMap<String, OrchestrationState> activeOrchestrations;
    
    // Kafka event publishers (optional, can be null if Kafka is disabled)
    private Object scenarioEventProducer;
    private Object temperatureEventProducer;
    private Object supplierComplianceEventProducer;
    private Object logisticsEventProducer;
    
    /**
     * Tracks orchestration state for a multi-scenario execution.
     */
    private static class OrchestrationState {
        final String orchestrationId;
        final String farmerId;
        final Map<Scenario, String> scenarioBatchIds;
        final long startTime;
        volatile boolean running;
        
        OrchestrationState(String orchestrationId, String farmerId, Map<Scenario, String> scenarioBatchIds) {
            this.orchestrationId = orchestrationId;
            this.farmerId = farmerId;
            this.scenarioBatchIds = new ConcurrentHashMap<>(scenarioBatchIds);
            this.startTime = System.currentTimeMillis();
            this.running = true;
        }
    }
    
    /**
     * Constructor with required dependencies.
     */
    public SimulationOrchestrator(DeliverySimulator deliverySimulator, 
                                 AlertService alertService, 
                                 MessageService messageService) {
        this.deliverySimulator = deliverySimulator;
        this.alertService = alertService;
        this.messageService = messageService;
        this.executorService = Executors.newFixedThreadPool(ORCHESTRATOR_THREAD_POOL_SIZE);
        this.statusMonitor = Executors.newScheduledThreadPool(1);
        this.activeOrchestrations = new ConcurrentHashMap<>();
        
        // Start periodic status monitoring
        statusMonitor.scheduleAtFixedRate(
            this::publishStatusUpdates,
            STATUS_UPDATE_INTERVAL_MS,
            STATUS_UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("SimulationOrchestrator initialized with pool size: {}", ORCHESTRATOR_THREAD_POOL_SIZE);
    }
    
    /**
     * Set Kafka event producers for domain-specific event publishing.
     * All parameters are optional and can be null if Kafka is disabled.
     */
    public void setKafkaProducers(Object scenarioEventProducer,
                                 Object temperatureEventProducer,
                                 Object supplierComplianceEventProducer,
                                 Object logisticsEventProducer) {
        this.scenarioEventProducer = scenarioEventProducer;
        this.temperatureEventProducer = temperatureEventProducer;
        this.supplierComplianceEventProducer = supplierComplianceEventProducer;
        this.logisticsEventProducer = logisticsEventProducer;
        logger.info("Kafka producers configured for orchestrator");
    }
    
    /**
     * Publish a scenario event to Kafka if producer is configured.
     */
    private void publishScenarioEvent(String executionId, String eventType, String scenarioName,
                                     String farmerId, String batchId) {
        if (scenarioEventProducer != null) {
            try {
                // Use reflection to call sendScenarioEvent method
                Class<?> eventClass = Class.forName("org.vericrop.kafka.events.ScenarioEvent");
                Object event = eventClass.getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class)
                    .newInstance(executionId, eventType, scenarioName, farmerId, batchId);
                
                scenarioEventProducer.getClass()
                    .getMethod("sendScenarioEvent", eventClass)
                    .invoke(scenarioEventProducer, event);
                    
                logger.debug("Published scenario event: {} for batch: {}", eventType, batchId);
            } catch (Exception e) {
                logger.warn("Failed to publish scenario event (Kafka may be disabled): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Start concurrent simulations for multiple scenarios.
     * 
     * @param origin Starting location
     * @param destination Ending location
     * @param numWaypoints Number of waypoints in route
     * @param avgSpeedKmh Average speed in km/h
     * @param farmerId Farmer/supplier identifier
     * @param scenarios List of scenarios to simulate concurrently
     * @param updateIntervalMs Update interval for each simulation
     * @return Map of scenario to batch IDs
     */
    public Map<Scenario, String> startConcurrentScenarios(
            DeliverySimulator.GeoCoordinate origin,
            DeliverySimulator.GeoCoordinate destination,
            int numWaypoints,
            double avgSpeedKmh,
            String farmerId,
            List<Scenario> scenarios,
            long updateIntervalMs) {
        
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("At least one scenario is required");
        }
        
        String orchestrationId = "ORCH_" + UUID.randomUUID().toString().substring(0, 8);
        Map<Scenario, String> scenarioBatchIds = new ConcurrentHashMap<>();
        long startTime = System.currentTimeMillis();
        
        // Publish orchestration start event
        publishOrchestrationEvent(orchestrationId, "ORCHESTRATION_STARTED", 
            String.format("Starting %d concurrent scenarios for farmer %s", scenarios.size(), farmerId));
        
        // Submit simulation tasks for each scenario
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Scenario scenario : scenarios) {
            String batchId = generateBatchId(farmerId, scenario);
            scenarioBatchIds.put(scenario, batchId);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Starting simulation for scenario: {} (batchId: {})", 
                               scenario.getDisplayName(), batchId);
                    
                    // Publish scenario started event to Kafka
                    publishScenarioEvent(orchestrationId, "STARTED", scenario.name(), farmerId, batchId);
                    
                    // Generate route for this scenario
                    List<DeliverySimulator.RouteWaypoint> route = deliverySimulator.generateRoute(
                        origin, destination, numWaypoints, startTime, avgSpeedKmh, scenario);
                    
                    // Start simulation
                    deliverySimulator.startSimulation(batchId, farmerId, route, updateIntervalMs, scenario);
                    
                    logger.info("Simulation started successfully for scenario: {} (batchId: {})", 
                               scenario.getDisplayName(), batchId);
                    
                    // Publish scenario running event to Kafka
                    publishScenarioEvent(orchestrationId, "RUNNING", scenario.name(), farmerId, batchId);
                    
                } catch (Exception e) {
                    logger.error("Failed to start simulation for scenario: {} (batchId: {})", 
                                scenario.getDisplayName(), batchId, e);
                    
                    // Publish scenario failed event to Kafka
                    publishScenarioEvent(orchestrationId, "FAILED", scenario.name(), farmerId, batchId);
                    
                    publishOrchestrationEvent(orchestrationId, "SIMULATION_FAILED",
                        String.format("Failed to start %s: %s", scenario.getDisplayName(), e.getMessage()));
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Store orchestration state
        OrchestrationState state = new OrchestrationState(orchestrationId, farmerId, scenarioBatchIds);
        activeOrchestrations.put(orchestrationId, state);
        
        // Wait for all simulations to start (non-blocking via separate task)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((result, error) -> {
                if (error != null) {
                    logger.error("Some simulations failed to start for orchestration: {}", 
                                orchestrationId, error);
                    publishOrchestrationEvent(orchestrationId, "ORCHESTRATION_PARTIALLY_STARTED",
                        "Some simulations failed to start");
                } else {
                    logger.info("All simulations started for orchestration: {}", orchestrationId);
                    publishOrchestrationEvent(orchestrationId, "ORCHESTRATION_ALL_STARTED",
                        String.format("All %d simulations started successfully", scenarios.size()));
                }
            });
        
        return scenarioBatchIds;
    }
    
    /**
     * Stop all running simulations.
     */
    public void stopAll() {
        logger.info("Stopping all orchestrated simulations");
        
        for (OrchestrationState state : activeOrchestrations.values()) {
            state.running = false;
            for (String batchId : state.scenarioBatchIds.values()) {
                try {
                    deliverySimulator.stopSimulation(batchId);
                } catch (Exception e) {
                    logger.error("Failed to stop simulation: {}", batchId, e);
                }
            }
            
            publishOrchestrationEvent(state.orchestrationId, "ORCHESTRATION_STOPPED", 
                "All simulations stopped");
        }
        
        activeOrchestrations.clear();
        logger.info("All orchestrated simulations stopped");
    }
    
    /**
     * Get status of all active orchestrations.
     */
    public List<Map<String, Object>> getActiveOrchestrations() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (OrchestrationState state : activeOrchestrations.values()) {
            Map<String, Object> orchestration = new HashMap<>();
            orchestration.put("orchestration_id", state.orchestrationId);
            orchestration.put("start_time", state.startTime);
            orchestration.put("running", state.running);
            orchestration.put("scenario_count", state.scenarioBatchIds.size());
            
            // Get status for each scenario
            Map<String, Map<String, Object>> scenarioStatuses = new HashMap<>();
            for (Map.Entry<Scenario, String> entry : state.scenarioBatchIds.entrySet()) {
                DeliverySimulator.SimulationStatus status = 
                    deliverySimulator.getSimulationStatus(entry.getValue());
                
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("batch_id", entry.getValue());
                statusMap.put("running", status.isRunning());
                statusMap.put("current_waypoint", status.getCurrentWaypoint());
                statusMap.put("total_waypoints", status.getTotalWaypoints());
                
                scenarioStatuses.put(entry.getKey().name(), statusMap);
            }
            
            orchestration.put("scenarios", scenarioStatuses);
            result.add(orchestration);
        }
        
        return result;
    }
    
    /**
     * Publish periodic status updates for active orchestrations.
     */
    private void publishStatusUpdates() {
        try {
            for (OrchestrationState state : activeOrchestrations.values()) {
                if (!state.running) {
                    continue;
                }
                
                // Check if all simulations are complete
                boolean allComplete = state.scenarioBatchIds.values().stream()
                    .map(deliverySimulator::getSimulationStatus)
                    .noneMatch(DeliverySimulator.SimulationStatus::isRunning);
                
                if (allComplete) {
                    state.running = false;
                    publishOrchestrationEvent(state.orchestrationId, "ORCHESTRATION_COMPLETED",
                        String.format("All %d simulations completed", state.scenarioBatchIds.size()));
                    
                    // Publish scenario completed events to Kafka for each batch
                    for (Map.Entry<Scenario, String> entry : state.scenarioBatchIds.entrySet()) {
                        publishScenarioEvent(state.orchestrationId, "COMPLETED", 
                                           entry.getKey().name(), state.farmerId, entry.getValue());
                    }
                    
                    // Remove from active orchestrations after a delay
                    activeOrchestrations.remove(state.orchestrationId);
                } else {
                    // Publish status update
                    int running = (int) state.scenarioBatchIds.values().stream()
                        .map(deliverySimulator::getSimulationStatus)
                        .filter(DeliverySimulator.SimulationStatus::isRunning)
                        .count();
                    
                    publishOrchestrationEvent(state.orchestrationId, "ORCHESTRATION_STATUS",
                        String.format("%d/%d simulations still running", 
                                     running, state.scenarioBatchIds.size()));
                }
            }
        } catch (Exception e) {
            logger.error("Error publishing status updates", e);
        }
    }
    
    /**
     * Publish orchestration event to MessageService.
     */
    private void publishOrchestrationEvent(String orchestrationId, String eventType, String description) {
        if (messageService != null) {
            try {
                Message message = new Message(
                    "orchestrator",
                    "simulation_orchestrator",
                    "all",
                    null,
                    eventType,
                    description
                );
                message.setShipmentId(orchestrationId);
                messageService.sendMessage(message);
                
                logger.debug("Published orchestration event: {} - {}", eventType, description);
            } catch (Exception e) {
                logger.error("Failed to publish orchestration event", e);
            }
        }
    }
    
    /**
     * Generate a unique batch ID for a scenario.
     */
    private String generateBatchId(String farmerId, Scenario scenario) {
        return String.format("BATCH_%s_%s_%s", 
                           farmerId != null ? farmerId : "UNKNOWN",
                           scenario.name(),
                           UUID.randomUUID().toString().substring(0, BATCH_ID_UUID_LENGTH));
    }
    
    /**
     * Shutdown the orchestrator and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down SimulationOrchestrator");
        
        stopAll();
        
        statusMonitor.shutdown();
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!statusMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                statusMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            statusMonitor.shutdownNow();
            logger.warn("Shutdown interrupted");
        }
        
        logger.info("SimulationOrchestrator shut down");
    }
}
