package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.OrchestrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

/**
 * Kafka producer for orchestration events.
 * Handles sending events with correlation IDs for distributed tracing.
 * 
 * Topics:
 * - vericrop.orchestration.scenarios
 * - vericrop.orchestration.delivery  
 * - vericrop.orchestration.map
 * - vericrop.orchestration.temperature
 * - vericrop.orchestration.supplierCompliance
 * - vericrop.orchestration.simulations
 */
public class OrchestrationEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationEventProducer.class);
    private static final String TOPIC_PREFIX = "vericrop.orchestration.";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    
    public OrchestrationEventProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        logger.info("OrchestrationEventProducer initialized");
    }
    
    /**
     * Send an orchestration event to the appropriate topic based on step type.
     */
    public void sendOrchestrationEvent(OrchestrationEvent event) {
        try {
            String topic = TOPIC_PREFIX + event.getStepType();
            String eventJson = mapper.writeValueAsString(event);
            
            // Create record with correlation ID in header for distributed tracing
            ProducerRecord<String, String> record = new ProducerRecord<>(
                topic, 
                null,
                event.getBatchId(), 
                eventJson
            );
            
            // Add correlation ID to message headers
            record.headers().add(new RecordHeader(
                "X-Correlation-Id", 
                event.getCorrelationId().getBytes(StandardCharsets.UTF_8)
            ));
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send orchestration event: {}", event, exception);
                } else {
                    logger.debug("Orchestration event sent: {} to topic {} partition {}", 
                               event.getEventType(), metadata.topic(), metadata.partition());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize/send orchestration event: {}", event, e);
        }
    }
    
    /**
     * Send orchestration event for scenarios
     */
    public void sendScenariosEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "scenarios", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Send orchestration event for delivery
     */
    public void sendDeliveryEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "delivery", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Send orchestration event for map
     */
    public void sendMapEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "map", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Send orchestration event for temperature
     */
    public void sendTemperatureEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "temperature", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Send orchestration event for supplier compliance
     */
    public void sendSupplierComplianceEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "supplierCompliance", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Send orchestration event for simulations
     */
    public void sendSimulationsEvent(String correlationId, String batchId, String farmerId, String eventType) {
        OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "simulations", eventType);
        sendOrchestrationEvent(event);
    }
    
    /**
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("OrchestrationEventProducer closed");
        }
    }
}
