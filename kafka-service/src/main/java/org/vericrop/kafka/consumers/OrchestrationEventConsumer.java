package org.vericrop.kafka.consumers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.OrchestrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Kafka consumer for orchestration completion events.
 * Listens to all orchestration topics for COMPLETED events to update orchestration state.
 */
public class OrchestrationEventConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationEventConsumer.class);
    private static final String[] TOPICS = {
        "vericrop.orchestration.scenarios",
        "vericrop.orchestration.delivery",
        "vericrop.orchestration.map",
        "vericrop.orchestration.temperature",
        "vericrop.orchestration.supplierCompliance",
        "vericrop.orchestration.simulations"
    };
    
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private final Consumer<OrchestrationEvent> eventHandler;
    private volatile boolean running = true;
    
    public OrchestrationEventConsumer(Consumer<OrchestrationEvent> eventHandler) {
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties("orchestration-consumer-group"));
        this.mapper = new ObjectMapper();
        this.eventHandler = eventHandler;
        this.consumer.subscribe(Arrays.asList(TOPICS));
        logger.info("OrchestrationEventConsumer subscribed to topics: {}", Arrays.toString(TOPICS));
    }
    
    @Override
    public void run() {
        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        // Extract correlation ID from header
                        String correlationId = extractCorrelationId(record);
                        
                        // Deserialize event
                        OrchestrationEvent event = mapper.readValue(record.value(), OrchestrationEvent.class);
                        
                        logger.debug("Received orchestration event: {} from topic: {}", 
                                   event, record.topic());
                        
                        // Only process COMPLETED events to update orchestration state
                        if ("COMPLETED".equals(event.getEventType()) && eventHandler != null) {
                            eventHandler.accept(event);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error processing orchestration event from topic {}: {}", 
                                   record.topic(), e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in orchestration consumer poll loop", e);
        } finally {
            consumer.close();
            logger.info("OrchestrationEventConsumer stopped");
        }
    }
    
    /**
     * Extract correlation ID from message headers
     */
    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("X-Correlation-Id");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
    
    /**
     * Stop the consumer
     */
    public void stop() {
        running = false;
    }
}
