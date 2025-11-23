package org.vericrop.kafka.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.SimulationDoneEvent;
import org.vericrop.kafka.events.SimulationStartEvent;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Service for coordinating simulation start and completion events via Kafka.
 * Used to orchestrate multiple simulation controllers simultaneously.
 */
public class SimulationCoordinationService {
    private static final Logger logger = LoggerFactory.getLogger(SimulationCoordinationService.class);
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final boolean kafkaEnabled;
    
    /**
     * Create coordination service with default Kafka configuration.
     */
    public SimulationCoordinationService() {
        this(true);
    }
    
    /**
     * Create coordination service with optional Kafka support.
     * 
     * @param kafkaEnabled If false, uses in-memory mode for testing
     */
    public SimulationCoordinationService(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
        this.objectMapper = new ObjectMapper();
        
        if (kafkaEnabled) {
            Properties props = KafkaConfig.getProducerProperties();
            this.producer = new KafkaProducer<>(props);
            logger.info("SimulationCoordinationService initialized with Kafka enabled");
        } else {
            this.producer = null;
            logger.info("SimulationCoordinationService running in in-memory mode (Kafka disabled)");
        }
    }
    
    /**
     * Publish a simulation start event to trigger all coordinated simulations.
     * 
     * @param event The simulation start event
     * @return true if the message was published successfully
     */
    public boolean publishSimulationStart(SimulationStartEvent event) {
        if (event == null) {
            logger.error("Cannot publish null simulation start event");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(event);
            return sendMessage(KafkaConfig.TOPIC_SIMULATIONS_START, event.getRunId(), json,
                    "simulation start", event.getRunId());
        } catch (Exception e) {
            logger.error("Failed to publish simulation start event for runId {}: {}",
                    event.getRunId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Publish a simulation completion event.
     * 
     * @param event The simulation done event
     * @return true if the message was published successfully
     */
    public boolean publishSimulationDone(SimulationDoneEvent event) {
        if (event == null) {
            logger.error("Cannot publish null simulation done event");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(event);
            return sendMessage(KafkaConfig.TOPIC_SIMULATIONS_DONE, event.getRunId(), json,
                    "simulation done", event.getComponentName() + " for runId " + event.getRunId());
        } catch (Exception e) {
            logger.error("Failed to publish simulation done event for {} runId {}: {}",
                    event.getComponentName(), event.getRunId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Helper method to send a message to Kafka or log in in-memory mode.
     */
    private boolean sendMessage(String topic, String key, String json, String messageType, String id) throws Exception {
        if (kafkaEnabled && producer != null) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get();
            
            logger.info("Sent {} for {} to topic {} partition {} offset {}",
                    messageType, id, metadata.topic(), metadata.partition(), metadata.offset());
        } else {
            // In-memory mode - just log
            logger.info("In-memory mode: Would send {} for {} to topic {}", messageType, id, topic);
        }
        return true;
    }
    
    /**
     * Flush any pending messages.
     */
    public void flush() {
        if (kafkaEnabled && producer != null) {
            producer.flush();
            logger.debug("Flushed pending coordination messages");
        }
    }
    
    /**
     * Close the producer and release resources.
     */
    public void close() {
        if (kafkaEnabled && producer != null) {
            producer.close();
            logger.info("SimulationCoordinationService closed");
        }
    }
    
    /**
     * Check if Kafka is enabled.
     */
    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }
}
