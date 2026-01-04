package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.SupplierComplianceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Kafka producer for supplier compliance events.
 * Topic: supplier-compliance-events
 */
public class SupplierComplianceEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(SupplierComplianceEventProducer.class);
    private static final String TOPIC = "supplier-compliance-events";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;
    
    public SupplierComplianceEventProducer() {
        this(TOPIC);
    }
    
    public SupplierComplianceEventProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
        logger.info("SupplierComplianceEventProducer initialized for topic: {}", topic);
    }
    
    /**
     * Send a supplier compliance event to Kafka.
     */
    public void sendSupplierComplianceEvent(SupplierComplianceEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getFarmerId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send supplier compliance event: {}", event, exception);
                } else {
                    logger.debug("Supplier compliance event sent: {} to partition {}", 
                               event.getEventType(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send supplier compliance event: {}", event, e);
        }
    }
    
    /**
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("SupplierComplianceEventProducer closed");
        }
    }
}
