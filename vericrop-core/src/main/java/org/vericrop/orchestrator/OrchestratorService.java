package org.vericrop.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vericrop.kafka.KafkaPublisher;

import java.time.Instant;
import java.util.*;

/**
 * Service for orchestrating coordinated runs of multiple subsystems.
 * Publishes start messages to Kafka topics for each subsystem.
 */
@Service
public class OrchestratorService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);
    
    // Kafka topic names for each subsystem
    public static final String TOPIC_SCENARIOS = "vericrop.scenarios.start";
    public static final String TOPIC_DELIVERY = "vericrop.delivery.start";
    public static final String TOPIC_MAP = "vericrop.map.start";
    public static final String TOPIC_TEMPERATURE = "vericrop.temperature.start";
    public static final String TOPIC_SUPPLIER_COMPLIANCE = "vericrop.supplier_compliance.start";
    public static final String TOPIC_SIMULATIONS = "vericrop.simulations.start";
    
    private final KafkaPublisher kafkaPublisher;
    
    @Autowired
    public OrchestratorService(KafkaPublisher kafkaPublisher) {
        this.kafkaPublisher = kafkaPublisher;
    }
    
    /**
     * Run the orchestrator for the specified subsystems.
     * 
     * @param subsystems List of subsystem names to run (e.g., "scenarios", "delivery")
     *                   If null or empty, runs all subsystems by default.
     * @param runId Unique identifier for this orchestration run
     * @return Map containing results of the orchestration
     */
    public Map<String, Object> runOrchestration(List<String> subsystems, String runId) {
        logger.info("Starting orchestration run: {}", runId);
        
        // Default to all subsystems if none specified
        if (subsystems == null || subsystems.isEmpty()) {
            subsystems = Arrays.asList("scenarios", "delivery", "map", "temperature", "supplier-compliance", "simulations");
            logger.info("No subsystems specified, running all: {}", subsystems);
        }
        
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("runId", runId);
        results.put("timestamp", Instant.now().toString());
        results.put("subsystems", subsystems);
        
        List<String> successfulPublishes = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        
        // Publish start messages to Kafka topics for each subsystem
        for (String subsystem : subsystems) {
            try {
                String topic = getTopicForSubsystem(subsystem);
                if (topic == null) {
                    logger.warn("Unknown subsystem: {}, skipping", subsystem);
                    Map<String, String> error = new HashMap<>();
                    error.put("subsystem", subsystem);
                    error.put("error", "Unknown subsystem");
                    errors.add(error);
                    continue;
                }
                
                Map<String, Object> message = createStartMessage(subsystem, runId);
                kafkaPublisher.publish(topic, runId, message);
                
                successfulPublishes.add(subsystem);
                logger.info("Published start message for subsystem '{}' to topic '{}'", subsystem, topic);
                
            } catch (Exception e) {
                logger.error("Failed to publish start message for subsystem '{}': {}", subsystem, e.getMessage());
                Map<String, String> error = new HashMap<>();
                error.put("subsystem", subsystem);
                error.put("error", e.getMessage());
                errors.add(error);
            }
        }
        
        results.put("successful", successfulPublishes);
        results.put("errors", errors);
        results.put("status", errors.isEmpty() ? "success" : "partial");
        
        logger.info("Orchestration run {} completed: {} successful, {} errors", 
                    runId, successfulPublishes.size(), errors.size());
        
        return results;
    }
    
    /**
     * Get the Kafka topic name for a given subsystem.
     * 
     * @param subsystem The subsystem name
     * @return The corresponding Kafka topic name, or null if unknown
     */
    private String getTopicForSubsystem(String subsystem) {
        switch (subsystem.toLowerCase()) {
            case "scenarios":
                return TOPIC_SCENARIOS;
            case "delivery":
                return TOPIC_DELIVERY;
            case "map":
                return TOPIC_MAP;
            case "temperature":
                return TOPIC_TEMPERATURE;
            case "supplier-compliance":
            case "supplier_compliance":
                return TOPIC_SUPPLIER_COMPLIANCE;
            case "simulations":
                return TOPIC_SIMULATIONS;
            default:
                return null;
        }
    }
    
    /**
     * Create a start message for a subsystem.
     * 
     * @param subsystem The subsystem name
     * @param runId The orchestration run ID
     * @return The message as a map
     */
    private Map<String, Object> createStartMessage(String subsystem, String runId) {
        Map<String, Object> message = new HashMap<>();
        message.put("subsystem", subsystem);
        message.put("runId", runId);
        message.put("timestamp", Instant.now().toString());
        message.put("action", "start");
        return message;
    }
}
