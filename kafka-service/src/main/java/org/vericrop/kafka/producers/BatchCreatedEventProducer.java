package org.vericrop.kafka.producers;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.vericrop.kafka.events.BatchCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka producer for batch created events.
 * Publishes events when new batches are created in the supply chain.
 */
@Service
public class BatchCreatedEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(BatchCreatedEventProducer.class);
    private static final String TOPIC = "batch-created-events";
    
    private final KafkaTemplate<String, BatchCreatedEvent> kafkaTemplate;
    
    public BatchCreatedEventProducer(KafkaTemplate<String, BatchCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Send batch created event to Kafka
     * 
     * @param event The batch created event
     */
    public void sendEvent(BatchCreatedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getBatchId(), event);
            logger.info("✅ Sent BatchCreatedEvent to topic {}: batchId={}", TOPIC, event.getBatchId());
        } catch (Exception e) {
            logger.error("❌ Failed to send BatchCreatedEvent to Kafka", e);
            throw new RuntimeException("Failed to publish batch created event", e);
        }
    }
}
