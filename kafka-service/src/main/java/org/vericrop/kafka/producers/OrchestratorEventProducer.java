package org.vericrop.kafka.producers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.ControllerCommandEvent;
import org.vericrop.kafka.events.OrchestratorCompletedEvent;
import org.vericrop.kafka.events.OrchestratorStartEvent;

/**
 * Producer for orchestrator events.
 */
public class OrchestratorEventProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorEventProducer.class);
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    
    public OrchestratorEventProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.objectMapper = new ObjectMapper();
        logger.info("OrchestratorEventProducer initialized");
    }
    
    /**
     * Publish orchestrator start event.
     */
    public void publishStartEvent(OrchestratorStartEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC_ORCHESTRATOR_START,
                    event.getOrchestrationId(),
                    json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish orchestrator start event: {}", event.getOrchestrationId(), exception);
                } else {
                    logger.info("Published orchestrator start event: {} to partition: {}, offset: {}",
                            event.getOrchestrationId(), metadata.partition(), metadata.offset());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error publishing orchestrator start event", e);
        }
    }
    
    /**
     * Publish controller command event.
     */
    public void publishControllerCommand(ControllerCommandEvent event) {
        try {
            String topic = getControllerCommandTopic(event.getControllerName());
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    event.getOrchestrationId(),
                    json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish controller command: {} to {}", event.getControllerName(), topic, exception);
                } else {
                    logger.info("Published controller command: {} to topic: {}, partition: {}, offset: {}",
                            event.getControllerName(), topic, metadata.partition(), metadata.offset());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error publishing controller command", e);
        }
    }
    
    /**
     * Publish orchestrator completed event.
     */
    public void publishCompletedEvent(OrchestratorCompletedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC_ORCHESTRATOR_COMPLETED,
                    event.getOrchestrationId(),
                    json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish orchestrator completed event: {}", event.getOrchestrationId(), exception);
                } else {
                    logger.info("Published orchestrator completed event: {} to partition: {}, offset: {}, success: {}",
                            event.getOrchestrationId(), metadata.partition(), metadata.offset(), event.isSuccess());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error publishing orchestrator completed event", e);
        }
    }
    
    /**
     * Get the command topic for a controller.
     */
    private String getControllerCommandTopic(String controllerName) {
        switch (controllerName.toLowerCase()) {
            case "scenarios":
                return KafkaConfig.TOPIC_CONTROLLER_SCENARIOS_COMMAND;
            case "delivery":
                return KafkaConfig.TOPIC_CONTROLLER_DELIVERY_COMMAND;
            case "map":
                return KafkaConfig.TOPIC_CONTROLLER_MAP_COMMAND;
            case "temperature":
                return KafkaConfig.TOPIC_CONTROLLER_TEMPERATURE_COMMAND;
            case "supplier_compliance":
                return KafkaConfig.TOPIC_CONTROLLER_SUPPLIER_COMPLIANCE_COMMAND;
            case "simulations":
                return KafkaConfig.TOPIC_CONTROLLER_SIMULATIONS_COMMAND;
            default:
                logger.warn("Unknown controller name: {}, using default topic", controllerName);
                return "vericrop.controller." + controllerName.toLowerCase() + ".command";
        }
    }
    
    /**
     * Flush and close the producer.
     */
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            logger.info("OrchestratorEventProducer closed");
        }
    }
}
