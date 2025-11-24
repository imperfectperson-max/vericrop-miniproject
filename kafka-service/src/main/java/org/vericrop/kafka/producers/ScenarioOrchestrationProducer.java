package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.dto.ScenarioStartEvent;
import org.vericrop.dto.ScenarioCompleteEvent;
import org.vericrop.dto.ScenarioAggregateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Kafka producer for scenario orchestration events.
 * Publishes events to multiple topics:
 * - vericrop.scenarios.start: When scenarios begin
 * - vericrop.scenarios.complete: When individual scenarios complete
 * - vericrop.scenarios.aggregate: When all scenarios in a run complete
 */
public class ScenarioOrchestrationProducer {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioOrchestrationProducer.class);
    
    private static final String TOPIC_START = "vericrop.scenarios.start";
    private static final String TOPIC_COMPLETE = "vericrop.scenarios.complete";
    private static final String TOPIC_AGGREGATE = "vericrop.scenarios.aggregate";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    
    public ScenarioOrchestrationProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        logger.info("ScenarioOrchestrationProducer initialized");
    }
    
    /**
     * Send a scenario start event to Kafka.
     */
    public void sendScenarioStartEvent(ScenarioStartEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_START, 
                event.getRunId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send scenario start event: {}", event, exception);
                } else {
                    logger.debug("Scenario start event sent: {} to partition {}", 
                               event.getScenarioName(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send scenario start event: {}", event, e);
        }
    }
    
    /**
     * Send a scenario complete event to Kafka.
     */
    public void sendScenarioCompleteEvent(ScenarioCompleteEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_COMPLETE, 
                event.getRunId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send scenario complete event: {}", event, exception);
                } else {
                    logger.debug("Scenario complete event sent: {} to partition {}", 
                               event.getScenarioName(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send scenario complete event: {}", event, e);
        }
    }
    
    /**
     * Send a scenario aggregate event to Kafka.
     */
    public void sendScenarioAggregateEvent(ScenarioAggregateEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_AGGREGATE, 
                event.getRunId(), eventJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send scenario aggregate event: {}", event, exception);
                } else {
                    logger.info("Scenario aggregate event sent for run {} with status {} to partition {}", 
                               event.getRunId(), event.getStatus(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send scenario aggregate event: {}", event, e);
        }
    }
    
    /**
     * Flush all pending messages.
     */
    public void flush() {
        producer.flush();
    }
    
    /**
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("ScenarioOrchestrationProducer closed");
        }
    }
}
