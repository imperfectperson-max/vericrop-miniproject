package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.ScenarioEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Kafka producer for scenario execution events.
 * Topic: scenario-events
 */
public class ScenarioEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioEventProducer.class);
    private static final String TOPIC = "scenario-events";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;
    
    public ScenarioEventProducer() {
        this(TOPIC);
    }
    
    public ScenarioEventProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
        logger.info("ScenarioEventProducer initialized for topic: {}", topic);
    }
    
    /**
     * Send a scenario event to Kafka.
     */
    public void sendScenarioEvent(ScenarioEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getExecutionId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send scenario event: {}", event, exception);
                } else {
                    logger.debug("Scenario event sent: {} to partition {}", 
                               event.getEventType(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send scenario event: {}", event, e);
        }
    }
    
    /**
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("ScenarioEventProducer closed");
        }
    }
}
