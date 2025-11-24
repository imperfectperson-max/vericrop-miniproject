package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.*;
import org.vericrop.service.models.Scenario;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates concurrent execution of multiple scenarios (delivery, map, temperature, 
 * supplier compliance, simulations).
 * 
 * This orchestrator:
 * - Accepts ScenarioRunRequest with scenario flags
 * - Triggers enabled scenarios concurrently using CompletableFuture
 * - Collects results and statuses from all scenarios
 * - Publishes aggregate results to Kafka
 */
public class ScenarioOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioOrchestrator.class);
    private static final int THREAD_POOL_SIZE = 10;
    
    private final SimulationOrchestrator simulationOrchestrator;
    private final DeliverySimulator deliverySimulator;
    private final TemperatureService temperatureService;
    private final MapService mapService;
    private final SupplierComplianceService supplierComplianceService;
    private final ExecutorService executorService;
    
    // Kafka event publishing (optional, can be null)
    private Object scenarioEventPublisher;
    
    // Track active runs
    private final ConcurrentHashMap<String, ScenarioRunResult> activeRuns;
    
    public ScenarioOrchestrator(SimulationOrchestrator simulationOrchestrator,
                               DeliverySimulator deliverySimulator,
                               TemperatureService temperatureService,
                               MapService mapService,
                               SupplierComplianceService supplierComplianceService) {
        this.simulationOrchestrator = simulationOrchestrator;
        this.deliverySimulator = deliverySimulator;
        this.temperatureService = temperatureService;
        this.mapService = mapService;
        this.supplierComplianceService = supplierComplianceService;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.activeRuns = new ConcurrentHashMap<>();
        
        logger.info("ScenarioOrchestrator initialized with thread pool size: {}", THREAD_POOL_SIZE);
    }
    
    /**
     * Set the Kafka event publisher for scenario events.
     * This is optional and can be null if Kafka is not available.
     */
    public void setScenarioEventPublisher(Object publisher) {
        this.scenarioEventPublisher = publisher;
        logger.info("Kafka scenario event publisher configured");
    }
    
    /**
     * Main orchestration method: triggers concurrent execution of enabled scenarios.
     * Returns immediately with ACCEPTED status and runId.
     * Actual execution happens asynchronously.
     */
    public ScenarioRunResult runScenariosConcurrently(ScenarioRunRequest request) {
        // Validate request
        if (request == null || !request.hasAnyScenarioEnabled()) {
            throw new IllegalArgumentException("At least one scenario must be enabled");
        }
        
        // Generate unique run ID
        String runId = UUID.randomUUID().toString();
        logger.info("Starting concurrent scenario run: {} with {} scenarios enabled",
                   runId, request.getEnabledScenarioCount());
        
        // Create initial result with ACCEPTED status
        ScenarioRunResult result = new ScenarioRunResult(runId, "ACCEPTED",
                "Scenario orchestration started");
        activeRuns.put(runId, result);
        
        // Start async orchestration
        CompletableFuture.runAsync(() -> executeScenarios(runId, request), executorService);
        
        return result;
    }
    
    /**
     * Execute all enabled scenarios concurrently.
     */
    private void executeScenarios(String runId, ScenarioRunRequest request) {
        ScenarioRunResult result = activeRuns.get(runId);
        result.setStatus("RUNNING");
        
        logger.info("Executing scenarios for run: {}", runId);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Delivery scenario
        if (request.isDeliveryScenario()) {
            futures.add(executeDeliveryScenario(runId, request, result));
        }
        
        // Map scenario
        if (request.isMapScenario()) {
            futures.add(executeMapScenario(runId, request, result));
        }
        
        // Temperature scenario
        if (request.isTemperatureScenario()) {
            futures.add(executeTemperatureScenario(runId, request, result));
        }
        
        // Supplier compliance scenario
        if (request.isSupplierComplianceScenario()) {
            futures.add(executeSupplierComplianceScenario(runId, request, result));
        }
        
        // Simulations scenario (uses existing SimulationOrchestrator)
        if (request.isSimulationsScenario()) {
            futures.add(executeSimulationsScenario(runId, request, result));
        }
        
        // Wait for all scenarios to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, error) -> {
                result.setEndTime(System.currentTimeMillis());
                
                if (error != null) {
                    logger.error("Error during scenario execution for run: {}", runId, error);
                    result.setStatus("FAILED");
                    result.setMessage("One or more scenarios failed: " + error.getMessage());
                } else if (result.hasAnyFailure()) {
                    result.setStatus("PARTIAL_FAILURE");
                    result.setMessage("Some scenarios completed with failures");
                } else {
                    result.setStatus("COMPLETED");
                    result.setMessage("All scenarios completed successfully");
                }
                
                logger.info("Scenario run {} completed with status: {}", runId, result.getStatus());
                
                // Publish aggregate event to Kafka
                publishAggregateEvent(runId, request, result);
            });
    }
    
    /**
     * Execute delivery scenario.
     */
    private CompletableFuture<Void> executeDeliveryScenario(String runId, ScenarioRunRequest request, 
                                                             ScenarioRunResult result) {
        return CompletableFuture.runAsync(() -> {
            ScenarioRunResult.ScenarioStatus status = new ScenarioRunResult.ScenarioStatus("STARTED", 
                "Delivery scenario started");
            result.addScenarioStatus("delivery", status);
            
            publishScenarioStartEvent(runId, "delivery", request);
            
            try {
                logger.info("Executing delivery scenario for run: {}", runId);
                
                // Use existing delivery simulator
                String batchId = request.getBatchId() != null ? request.getBatchId() :
                               "BATCH_" + runId.substring(0, 8);
                
                // Get or create route using parameters from request
                Map<String, Object> params = request.getScenarioParameters();
                
                // For now, we'll just verify the simulator is available
                // In a full implementation, this would start actual deliveries
                if (deliverySimulator != null) {
                    status.setStatus("COMPLETED");
                    status.setMessage("Delivery scenario validated successfully");
                    status.getResults().put("batch_id", batchId);
                    status.getResults().put("simulator_ready", true);
                } else {
                    throw new RuntimeException("Delivery simulator not available");
                }
                
                logger.info("Delivery scenario completed for run: {}", runId);
                publishScenarioCompleteEvent(runId, "delivery", request, true, "Success");
                
            } catch (Exception e) {
                logger.error("Delivery scenario failed for run: {}", runId, e);
                status.setStatus("FAILED");
                status.setMessage("Delivery scenario failed: " + e.getMessage());
                publishScenarioCompleteEvent(runId, "delivery", request, false, e.getMessage());
            } finally {
                status.setEndTime(System.currentTimeMillis());
            }
        }, executorService);
    }
    
    /**
     * Execute map scenario.
     */
    private CompletableFuture<Void> executeMapScenario(String runId, ScenarioRunRequest request, 
                                                        ScenarioRunResult result) {
        return CompletableFuture.runAsync(() -> {
            ScenarioRunResult.ScenarioStatus status = new ScenarioRunResult.ScenarioStatus("STARTED", 
                "Map scenario started");
            result.addScenarioStatus("map", status);
            
            publishScenarioStartEvent(runId, "map", request);
            
            try {
                logger.info("Executing map scenario for run: {}", runId);
                
                // Verify map service is available
                if (mapService != null) {
                    status.setStatus("COMPLETED");
                    status.setMessage("Map scenario completed successfully");
                    status.getResults().put("map_service_ready", true);
                } else {
                    throw new RuntimeException("Map service not available");
                }
                
                logger.info("Map scenario completed for run: {}", runId);
                publishScenarioCompleteEvent(runId, "map", request, true, "Success");
                
            } catch (Exception e) {
                logger.error("Map scenario failed for run: {}", runId, e);
                status.setStatus("FAILED");
                status.setMessage("Map scenario failed: " + e.getMessage());
                publishScenarioCompleteEvent(runId, "map", request, false, e.getMessage());
            } finally {
                status.setEndTime(System.currentTimeMillis());
            }
        }, executorService);
    }
    
    /**
     * Execute temperature scenario.
     */
    private CompletableFuture<Void> executeTemperatureScenario(String runId, ScenarioRunRequest request, 
                                                                ScenarioRunResult result) {
        return CompletableFuture.runAsync(() -> {
            ScenarioRunResult.ScenarioStatus status = new ScenarioRunResult.ScenarioStatus("STARTED", 
                "Temperature scenario started");
            result.addScenarioStatus("temperature", status);
            
            publishScenarioStartEvent(runId, "temperature", request);
            
            try {
                logger.info("Executing temperature scenario for run: {}", runId);
                
                // Verify temperature service is available
                if (temperatureService != null) {
                    String batchId = request.getBatchId() != null ? request.getBatchId() :
                                   "BATCH_" + runId.substring(0, 8);
                    
                    // Start temperature monitoring
                    temperatureService.startMonitoring(batchId);
                    
                    status.setStatus("COMPLETED");
                    status.setMessage("Temperature monitoring started successfully");
                    status.getResults().put("batch_id", batchId);
                    status.getResults().put("monitoring_active", true);
                } else {
                    throw new RuntimeException("Temperature service not available");
                }
                
                logger.info("Temperature scenario completed for run: {}", runId);
                publishScenarioCompleteEvent(runId, "temperature", request, true, "Success");
                
            } catch (Exception e) {
                logger.error("Temperature scenario failed for run: {}", runId, e);
                status.setStatus("FAILED");
                status.setMessage("Temperature scenario failed: " + e.getMessage());
                publishScenarioCompleteEvent(runId, "temperature", request, false, e.getMessage());
            } finally {
                status.setEndTime(System.currentTimeMillis());
            }
        }, executorService);
    }
    
    /**
     * Execute supplier compliance scenario.
     */
    private CompletableFuture<Void> executeSupplierComplianceScenario(String runId, ScenarioRunRequest request, 
                                                                       ScenarioRunResult result) {
        return CompletableFuture.runAsync(() -> {
            ScenarioRunResult.ScenarioStatus status = new ScenarioRunResult.ScenarioStatus("STARTED", 
                "Supplier compliance scenario started");
            result.addScenarioStatus("supplierCompliance", status);
            
            publishScenarioStartEvent(runId, "supplierCompliance", request);
            
            try {
                logger.info("Executing supplier compliance scenario for run: {}", runId);
                
                // Verify supplier compliance service is available and check farmer
                if (supplierComplianceService != null && request.getFarmerId() != null) {
                    SupplierComplianceService.SupplierComplianceData complianceData = 
                        supplierComplianceService.getComplianceData(request.getFarmerId());
                    
                    status.setStatus("COMPLETED");
                    status.setMessage("Supplier compliance check completed");
                    status.getResults().put("farmer_id", request.getFarmerId());
                    
                    if (complianceData != null) {
                        status.getResults().put("compliant", complianceData.isCompliant());
                        status.getResults().put("compliance_status", complianceData.getComplianceStatus());
                    }
                } else {
                    throw new RuntimeException("Supplier compliance service not available or farmer ID missing");
                }
                
                logger.info("Supplier compliance scenario completed for run: {}", runId);
                publishScenarioCompleteEvent(runId, "supplierCompliance", request, true, "Success");
                
            } catch (Exception e) {
                logger.error("Supplier compliance scenario failed for run: {}", runId, e);
                status.setStatus("FAILED");
                status.setMessage("Supplier compliance scenario failed: " + e.getMessage());
                publishScenarioCompleteEvent(runId, "supplierCompliance", request, false, e.getMessage());
            } finally {
                status.setEndTime(System.currentTimeMillis());
            }
        }, executorService);
    }
    
    /**
     * Execute simulations scenario using existing SimulationOrchestrator.
     */
    private CompletableFuture<Void> executeSimulationsScenario(String runId, ScenarioRunRequest request, 
                                                                ScenarioRunResult result) {
        return CompletableFuture.runAsync(() -> {
            ScenarioRunResult.ScenarioStatus status = new ScenarioRunResult.ScenarioStatus("STARTED", 
                "Simulations scenario started");
            result.addScenarioStatus("simulations", status);
            
            publishScenarioStartEvent(runId, "simulations", request);
            
            try {
                logger.info("Executing simulations scenario for run: {}", runId);
                
                // Reuse existing SimulationOrchestrator for concurrent scenario simulations
                if (simulationOrchestrator != null) {
                    // Get parameters from request
                    Map<String, Object> params = request.getScenarioParameters();
                    
                    // Default route for demonstration
                    DeliverySimulator.GeoCoordinate origin = new DeliverySimulator.GeoCoordinate(
                        42.3601, -71.0589, "Origin Farm");
                    DeliverySimulator.GeoCoordinate destination = new DeliverySimulator.GeoCoordinate(
                        42.3736, -71.1097, "Destination Warehouse");
                    
                    // Run multiple scenarios concurrently
                    List<Scenario> scenarios = Arrays.asList(
                        Scenario.NORMAL, Scenario.HOT_TRANSPORT, Scenario.EXTREME_DELAY
                    );
                    
                    Map<Scenario, String> batchIds = simulationOrchestrator.startConcurrentScenarios(
                        origin, destination, 10, 50.0, 
                        request.getFarmerId() != null ? request.getFarmerId() : "UNKNOWN",
                        scenarios, 5000L
                    );
                    
                    status.setStatus("COMPLETED");
                    status.setMessage("Simulations started successfully");
                    status.getResults().put("scenario_count", batchIds.size());
                    status.getResults().put("batch_ids", new ArrayList<>(batchIds.values()));
                } else {
                    throw new RuntimeException("Simulation orchestrator not available");
                }
                
                logger.info("Simulations scenario completed for run: {}", runId);
                publishScenarioCompleteEvent(runId, "simulations", request, true, "Success");
                
            } catch (Exception e) {
                logger.error("Simulations scenario failed for run: {}", runId, e);
                status.setStatus("FAILED");
                status.setMessage("Simulations scenario failed: " + e.getMessage());
                publishScenarioCompleteEvent(runId, "simulations", request, false, e.getMessage());
            } finally {
                status.setEndTime(System.currentTimeMillis());
            }
        }, executorService);
    }
    
    /**
     * Publish scenario start event to Kafka.
     */
    private void publishScenarioStartEvent(String runId, String scenarioName, ScenarioRunRequest request) {
        if (scenarioEventPublisher != null) {
            try {
                ScenarioStartEvent event = new ScenarioStartEvent(runId, scenarioName,
                    request.getFarmerId(), request.getBatchId());
                event.setParameters(request.getScenarioParameters());
                
                // Use reflection to call sendScenarioStartEvent method
                scenarioEventPublisher.getClass()
                    .getMethod("sendScenarioStartEvent", ScenarioStartEvent.class)
                    .invoke(scenarioEventPublisher, event);
                    
                logger.debug("Published scenario start event: {} for run: {}", scenarioName, runId);
            } catch (Exception e) {
                logger.warn("Failed to publish scenario start event: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Publish scenario complete event to Kafka.
     */
    private void publishScenarioCompleteEvent(String runId, String scenarioName, ScenarioRunRequest request,
                                             boolean success, String message) {
        if (scenarioEventPublisher != null) {
            try {
                ScenarioCompleteEvent event = new ScenarioCompleteEvent(runId, scenarioName,
                    request.getFarmerId(), request.getBatchId(), success, message);
                
                // Use reflection to call sendScenarioCompleteEvent method
                scenarioEventPublisher.getClass()
                    .getMethod("sendScenarioCompleteEvent", ScenarioCompleteEvent.class)
                    .invoke(scenarioEventPublisher, event);
                    
                logger.debug("Published scenario complete event: {} for run: {}", scenarioName, runId);
            } catch (Exception e) {
                logger.warn("Failed to publish scenario complete event: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Publish aggregate result event to Kafka.
     */
    private void publishAggregateEvent(String runId, ScenarioRunRequest request, ScenarioRunResult result) {
        if (scenarioEventPublisher != null) {
            try {
                ScenarioAggregateEvent event = new ScenarioAggregateEvent(runId,
                    request.getFarmerId(), request.getBatchId(), result.getStatus(), result.getMessage());
                event.setStartTime(result.getStartTime());
                event.setEndTime(result.getEndTime() != null ? result.getEndTime() : System.currentTimeMillis());
                event.setTotalScenarios(result.getScenarioStatuses().size());
                
                // Count successes and failures
                int successCount = 0;
                int failureCount = 0;
                Map<String, ScenarioAggregateEvent.ScenarioSummary> summaries = new HashMap<>();
                
                for (Map.Entry<String, ScenarioRunResult.ScenarioStatus> entry : result.getScenarioStatuses().entrySet()) {
                    ScenarioRunResult.ScenarioStatus status = entry.getValue();
                    boolean success = "COMPLETED".equals(status.getStatus());
                    
                    if (success) successCount++;
                    else failureCount++;
                    
                    long duration = status.getEndTime() != null ? 
                        (status.getEndTime() - status.getStartTime()) : 0;
                    
                    summaries.put(entry.getKey(), new ScenarioAggregateEvent.ScenarioSummary(
                        entry.getKey(), success, status.getMessage(), duration));
                }
                
                event.setSuccessfulScenarios(successCount);
                event.setFailedScenarios(failureCount);
                event.setScenarioSummaries(summaries);
                event.setAggregateResults(result.getAggregateResults());
                
                // Use reflection to call sendScenarioAggregateEvent method
                scenarioEventPublisher.getClass()
                    .getMethod("sendScenarioAggregateEvent", ScenarioAggregateEvent.class)
                    .invoke(scenarioEventPublisher, event);
                    
                logger.info("Published aggregate event for run: {}", runId);
            } catch (Exception e) {
                logger.warn("Failed to publish aggregate event: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get status of a scenario run.
     */
    public ScenarioRunResult getRunStatus(String runId) {
        return activeRuns.get(runId);
    }
    
    /**
     * Get all active runs.
     */
    public Map<String, ScenarioRunResult> getActiveRuns() {
        return new HashMap<>(activeRuns);
    }
    
    /**
     * Shutdown the orchestrator.
     */
    public void shutdown() {
        logger.info("Shutting down ScenarioOrchestrator");
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        
        logger.info("ScenarioOrchestrator shut down");
    }
}
