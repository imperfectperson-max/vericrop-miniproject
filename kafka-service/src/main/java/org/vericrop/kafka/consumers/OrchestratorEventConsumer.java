package org.vericrop.kafka.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.ControllerStatusEvent;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Consumer for orchestrator events (controller status updates).
 */
public class OrchestratorEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorEventConsumer.class);
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;
    private final Consumer<ControllerStatusEvent> statusHandler;
    
    public OrchestratorEventConsumer(String groupId, Consumer<ControllerStatusEvent> statusHandler) {
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties(groupId));
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        this.statusHandler = statusHandler;
        
        // Subscribe to all controller status topics
        List<String> topics = Arrays.asList(
                KafkaConfig.TOPIC_CONTROLLER_SCENARIOS_STATUS,
                KafkaConfig.TOPIC_CONTROLLER_DELIVERY_STATUS,
                KafkaConfig.TOPIC_CONTROLLER_MAP_STATUS,
                KafkaConfig.TOPIC_CONTROLLER_TEMPERATURE_STATUS,
                KafkaConfig.TOPIC_CONTROLLER_SUPPLIER_COMPLIANCE_STATUS,
                KafkaConfig.TOPIC_CONTROLLER_SIMULATIONS_STATUS
        );
        
        consumer.subscribe(topics);
        logger.info("OrchestratorEventConsumer initialized with group: {}, topics: {}", groupId, topics);
    }
    
    /**
     * Start consuming controller status events.
     */
    public void start() {
        running.set(true);
        logger.info("Starting OrchestratorEventConsumer");
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        ControllerStatusEvent event = objectMapper.readValue(record.value(), ControllerStatusEvent.class);
                        logger.debug("Received controller status: {} from topic: {}", event, record.topic());
                        
                        if (statusHandler != null) {
                            statusHandler.accept(event);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error processing controller status event from topic: {}", record.topic(), e);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error polling for controller status events", e);
                // Continue processing unless stopped
            }
        }
        
        logger.info("OrchestratorEventConsumer stopped");
    }
    
    /**
     * Stop the consumer.
     */
    public void stop() {
        running.set(false);
        logger.info("Stopping OrchestratorEventConsumer");
    }
    
    /**
     * Close the consumer.
     */
    public void close() {
        running.set(false);
        if (consumer != null) {
            consumer.close();
            logger.info("OrchestratorEventConsumer closed");
        }
    }
}
