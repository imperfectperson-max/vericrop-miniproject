package org.vericrop.gui.services.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.vericrop.kafka.coordination.SimulationCoordinationService;
import org.vericrop.kafka.events.SimulationDoneEvent;
import org.vericrop.kafka.events.SimulationStartEvent;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.DeliverySimulator.GeoCoordinate;
import org.vericrop.service.MessageService;
import org.vericrop.service.models.Scenario;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that coordinates simulation components in response to Kafka start events.
 * Manages delivery, temperature, quality, and scenario simulations.
 */
@Service
public class CoordinatedSimulationService {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatedSimulationService.class);
    
    private final SimulationCoordinationService coordinationService;
    private final DeliverySimulator deliverySimulator;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final boolean coordinationEnabled;
    
    // Track active simulation runs
    private final ConcurrentHashMap<String, SimulationRun> activeRuns;
    
    private static class SimulationRun {
        final String runId;
        final long startTime;
        boolean deliveryComplete = false;
        boolean qualityComplete = false;
        boolean temperatureComplete = false;
        boolean scenarioComplete = false;
        
        SimulationRun(String runId) {
            this.runId = runId;
            this.startTime = System.currentTimeMillis();
        }
        
        boolean isComplete() {
            return deliveryComplete && qualityComplete && temperatureComplete && scenarioComplete;
        }
    }
    
    public CoordinatedSimulationService() {
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
        this.activeRuns = new ConcurrentHashMap<>();
        
        // Check if coordination is enabled
        String coordEnabledEnv = System.getenv("SIMULATION_COORDINATION_ENABLED");
        String coordEnabledProp = System.getProperty("simulation.coordination.enabled");
        
        this.coordinationEnabled = "true".equalsIgnoreCase(coordEnabledEnv) ||
                                   "true".equalsIgnoreCase(coordEnabledProp);
        
        if (coordinationEnabled) {
            String kafkaEnabled = System.getenv("KAFKA_ENABLED");
            boolean useKafka = kafkaEnabled == null || "true".equalsIgnoreCase(kafkaEnabled);
            
            this.coordinationService = new SimulationCoordinationService(useKafka);
            this.messageService = new MessageService(useKafka);
            this.deliverySimulator = new DeliverySimulator(messageService);
            
            logger.info("CoordinatedSimulationService initialized with coordination ENABLED");
        } else {
            this.coordinationService = null;
            this.messageService = null;
            this.deliverySimulator = null;
            
            logger.info("CoordinatedSimulationService initialized with coordination DISABLED (feature flag off)");
        }
    }
    
    @PostConstruct
    public void init() {
        if (coordinationEnabled) {
            logger.info("CoordinatedSimulationService ready to process simulation start events");
        }
    }
    
    /**
     * Listen for simulation start events and trigger all simulation components.
     */
    @KafkaListener(
        topics = "simulations.start",
        groupId = "vericrop-simulation-coordinators",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSimulationStart(ConsumerRecord<String, String> record) {
        if (!coordinationEnabled) {
            logger.debug("Received simulation start event but coordination is disabled");
            return;
        }
        
        try {
            SimulationStartEvent event = objectMapper.readValue(record.value(), SimulationStartEvent.class);
            logger.info("🚀 Received simulation start event: runId={}, source={}", 
                       event.getRunId(), event.getTriggerSource());
            
            // Create simulation run tracker
            SimulationRun run = new SimulationRun(event.getRunId());
            activeRuns.put(event.getRunId(), run);
            
            // Start all simulation components asynchronously
            startDeliverySimulation(event, run);
            startQualitySimulation(event, run);
            startTemperatureSimulation(event, run);
            startScenarioSimulation(event, run);
            
        } catch (Exception e) {
            logger.error("Failed to process simulation start event", e);
        }
    }
    
    /**
     * Start delivery simulation component.
     */
    private void startDeliverySimulation(SimulationStartEvent event, SimulationRun run) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting delivery simulation for runId: {}", event.getRunId());
                
                // Extract parameters or use defaults
                Map<String, Object> params = event.getParameters();
                String batchId = params != null && params.containsKey("batchId") ?
                        (String) params.get("batchId") : "BATCH_" + event.getRunId();
                
                // Generate a simple route
                GeoCoordinate origin = new GeoCoordinate(37.7749, -122.4194, "San Francisco Farm");
                GeoCoordinate destination = new GeoCoordinate(34.0522, -118.2437, "Los Angeles Distribution");
                
                var route = deliverySimulator.generateRoute(origin, destination, 5, 
                                                           System.currentTimeMillis(), 60.0);
                
                // Simulate delivery (simplified - in real system would run full simulation)
                deliverySimulator.startSimulation(batchId, route, 1000);
                
                // Sleep to simulate delivery time
                Thread.sleep(2000);
                
                deliverySimulator.stopSimulation(batchId);
                
                run.deliveryComplete = true;
                logger.info("✅ Delivery simulation completed for runId: {}", event.getRunId());
                
                publishCompletionEvent(event.getRunId(), "delivery", "success", null);
                checkAllComplete(run);
                
            } catch (Exception e) {
                logger.error("Delivery simulation failed for runId: {}", event.getRunId(), e);
                publishCompletionEvent(event.getRunId(), "delivery", "failed", e.getMessage());
            }
        });
    }
    
    /**
     * Start quality decay simulation component.
     */
    private void startQualitySimulation(SimulationStartEvent event, SimulationRun run) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting quality simulation for runId: {}", event.getRunId());
                
                // Simulate quality decay calculation
                double initialQuality = 100.0;
                double temperature = 5.0 + random.nextDouble() * 5.0;
                double humidity = 75.0 + random.nextDouble() * 10.0;
                double hours = 2.0;
                
                // Simulate processing time
                Thread.sleep(1500);
                
                double finalQuality = initialQuality - (hours * 2.0);
                logger.info("Quality simulation: {} -> {} (temp={}°C, humidity={}%)", 
                           initialQuality, finalQuality, temperature, humidity);
                
                run.qualityComplete = true;
                logger.info("✅ Quality simulation completed for runId: {}", event.getRunId());
                
                publishCompletionEvent(event.getRunId(), "quality", "success", null);
                checkAllComplete(run);
                
            } catch (Exception e) {
                logger.error("Quality simulation failed for runId: {}", event.getRunId(), e);
                publishCompletionEvent(event.getRunId(), "quality", "failed", e.getMessage());
            }
        });
    }
    
    /**
     * Start temperature monitoring simulation component.
     */
    private void startTemperatureSimulation(SimulationStartEvent event, SimulationRun run) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting temperature simulation for runId: {}", event.getRunId());
                
                // Simulate temperature monitoring
                for (int i = 0; i < 5; i++) {
                    double temp = 4.0 + random.nextDouble() * 4.0;
                    logger.debug("Temperature reading {}: {}°C", i + 1, temp);
                    Thread.sleep(300);
                }
                
                run.temperatureComplete = true;
                logger.info("✅ Temperature simulation completed for runId: {}", event.getRunId());
                
                publishCompletionEvent(event.getRunId(), "temperature", "success", null);
                checkAllComplete(run);
                
            } catch (Exception e) {
                logger.error("Temperature simulation failed for runId: {}", event.getRunId(), e);
                publishCompletionEvent(event.getRunId(), "temperature", "failed", e.getMessage());
            }
        });
    }
    
    /**
     * Start scenario simulation component.
     */
    private void startScenarioSimulation(SimulationStartEvent event, SimulationRun run) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting scenario simulation for runId: {}", event.getRunId());
                
                // Randomly select a scenario
                Scenario[] scenarios = Scenario.values();
                Scenario selectedScenario = scenarios[random.nextInt(scenarios.length)];
                
                logger.info("Simulating scenario: {} for runId: {}", 
                           selectedScenario.getDisplayName(), event.getRunId());
                
                // Simulate scenario execution
                Thread.sleep(1800);
                
                run.scenarioComplete = true;
                logger.info("✅ Scenario simulation completed for runId: {} (scenario: {})", 
                           event.getRunId(), selectedScenario.getDisplayName());
                
                publishCompletionEvent(event.getRunId(), "scenario", "success", null);
                checkAllComplete(run);
                
            } catch (Exception e) {
                logger.error("Scenario simulation failed for runId: {}", event.getRunId(), e);
                publishCompletionEvent(event.getRunId(), "scenario", "failed", e.getMessage());
            }
        });
    }
    
    /**
     * Publish a completion event to Kafka.
     */
    private void publishCompletionEvent(String runId, String componentName, String status, String error) {
        if (coordinationService == null) {
            return;
        }
        
        try {
            SimulationDoneEvent doneEvent = new SimulationDoneEvent(runId, componentName, status);
            if (error != null) {
                doneEvent.setError(error);
            }
            coordinationService.publishSimulationDone(doneEvent);
        } catch (Exception e) {
            logger.error("Failed to publish completion event for {} in runId {}", componentName, runId, e);
        }
    }
    
    /**
     * Check if all components have completed and log overall status.
     */
    private void checkAllComplete(SimulationRun run) {
        if (run.isComplete()) {
            long duration = System.currentTimeMillis() - run.startTime;
            logger.info("🎉 All simulation components completed for runId: {} in {}ms", 
                       run.runId, duration);
            activeRuns.remove(run.runId);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (coordinationService != null) {
            coordinationService.close();
        }
        logger.info("CoordinatedSimulationService cleanup complete");
    }
}
