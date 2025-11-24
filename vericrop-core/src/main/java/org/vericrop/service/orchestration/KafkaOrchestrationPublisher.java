package org.vericrop.service.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Kafka-based implementation of the OrchestrationService.KafkaEventPublisher interface.
 * 
 * Publishes scenario orchestration events to Kafka topics for distributed processing.
 */
public class KafkaOrchestrationPublisher implements OrchestrationService.KafkaEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(KafkaOrchestrationPublisher.class);
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    
    /**
     * Creates a Kafka publisher with the specified producer properties.
     * 
     * @param producerProperties Kafka producer configuration
     */
    public KafkaOrchestrationPublisher(Properties producerProperties) {
        this.producer = new KafkaProducer<>(producerProperties);
        this.objectMapper = new ObjectMapper();
        
        logger.info("KafkaOrchestrationPublisher initialized");
    }
    
    @Override
    public void publish(String topic, Map<String, Object> eventData) throws Exception {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }
        
        if (eventData == null) {
            throw new IllegalArgumentException("Event data cannot be null");
        }
        
        try {
            // Serialize event data to JSON
            String eventJson = objectMapper.writeValueAsString(eventData);
            
            // Extract key for partitioning (use requestId if available)
            String key = eventData.containsKey("requestId") ? 
                    eventData.get("requestId").toString() : 
                    null;
            
            // Create producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, eventJson);
            
            // Send asynchronously and log result
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send event to topic: {}", topic, exception);
                } else {
                    logger.debug("Event sent to topic: {} partition: {} offset: {}",
                            topic, metadata.partition(), metadata.offset());
                }
            });
            
            // Don't wait for completion - fire and forget for performance
            // If needed, can call future.get() with timeout for synchronous sending
            
        } catch (Exception e) {
            logger.error("Error publishing event to topic: {}", topic, e);
            throw e;
        }
    }
    
    /**
     * Closes the Kafka producer and releases resources.
     */
    public void close() {
        logger.info("Closing KafkaOrchestrationPublisher");
        if (producer != null) {
            producer.close();
        }
    }
}
