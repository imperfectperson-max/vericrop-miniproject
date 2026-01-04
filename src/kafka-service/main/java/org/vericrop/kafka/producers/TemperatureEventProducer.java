package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.TemperatureEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Kafka producer for temperature monitoring events.
 * Topic: temperature-events
 */
public class TemperatureEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureEventProducer.class);
    private static final String TOPIC = "temperature-events";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;
    
    public TemperatureEventProducer() {
        this(TOPIC);
    }
    
    public TemperatureEventProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
        logger.info("TemperatureEventProducer initialized for topic: {}", topic);
    }
    
    /**
     * Send a temperature event to Kafka.
     */
    public void sendTemperatureEvent(TemperatureEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getBatchId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send temperature event: {}", event, exception);
                } else {
                    logger.debug("Temperature event sent: {} to partition {}", 
                               event.getEventType(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send temperature event: {}", event, e);
        }
    }
    
    /**
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("TemperatureEventProducer closed");
        }
    }
}
