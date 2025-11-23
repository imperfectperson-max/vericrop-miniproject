package org.vericrop.kafka.producers;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.vericrop.kafka.events.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka producer for order events.
 * Publishes events when orders are placed or updated.
 */
@Service
public class OrderEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(OrderEventProducer.class);
    private static final String TOPIC = "order-events";
    
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public OrderEventProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Send order event to Kafka
     * 
     * @param event The order event
     */
    public void sendEvent(OrderEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getOrderId(), event);
            logger.info("✅ Sent OrderEvent to topic {}: orderId={}, batchId={}", 
                       TOPIC, event.getOrderId(), event.getBatchId());
        } catch (Exception e) {
            logger.error("❌ Failed to send OrderEvent to Kafka", e);
            throw new RuntimeException("Failed to publish order event", e);
        }
    }
}
