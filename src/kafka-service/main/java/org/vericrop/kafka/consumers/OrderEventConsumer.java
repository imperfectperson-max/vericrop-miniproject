package org.vericrop.kafka.consumers;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.vericrop.kafka.events.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer for order events.
 * Processes orders and handles quality disclosure to buyers.
 */
@Service
public class OrderEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    
    /**
     * Listen to order events and process them.
     * When an order is placed, this consumer:
     * 1. Validates the order
     * 2. Discloses batch quality to the buyer for price negotiation
     * 3. Updates order status
     * 4. Triggers delivery workflow if accepted
     * 
     * @param event The order event
     */
    @KafkaListener(topics = "order-events", groupId = "vericrop-order-processors")
    public void consumeOrderEvent(OrderEvent event) {
        logger.info("📦 Received OrderEvent: orderId={}, batchId={}, buyer={}", 
                   event.getOrderId(), event.getBatchId(), event.getBuyer());
        
        try {
            processOrder(event);
        } catch (Exception e) {
            logger.error("❌ Failed to process order event: {}", event.getOrderId(), e);
        }
    }
    
    /**
     * Process the order and disclose quality information
     */
    private void processOrder(OrderEvent event) {
        // Log order details
        logger.info("Processing order: orderId={}, batchId={}, quantity={}, buyer={}", 
                   event.getOrderId(), event.getBatchId(), event.getQuantityOrdered(), event.getBuyer());
        
        // Check if quality has been disclosed
        if (!event.getQualityDisclosed()) {
            logger.info("⚠️ Quality not yet disclosed for order {}. Quality disclosure required for price negotiation.", 
                       event.getOrderId());
            // In a real system, this would trigger a quality disclosure workflow
            // For now, we just log it
        } else {
            logger.info("✅ Quality disclosed for order {}: score={}, primeRate={}, rejectionRate={}", 
                       event.getOrderId(), event.getQualityScore(), event.getPrimeRate(), event.getRejectionRate());
            
            // Calculate final price based on quality
            double adjustedPrice = calculatePriceAdjustment(event);
            logger.info("💰 Adjusted price for order {}: original={}, adjusted={}", 
                       event.getOrderId(), event.getTotalPrice(), adjustedPrice);
        }
        
        // Update order status
        event.setStatus("processed");
        logger.info("✅ Order processed successfully: {}", event.getOrderId());
    }
    
    // Price adjustment constants
    private static final double PRIME_BONUS_MULTIPLIER = 0.2;  // 20% bonus for prime quality
    private static final double REJECTION_PENALTY_MULTIPLIER = 0.3;  // 30% penalty for rejections
    
    /**
     * Calculate price adjustment based on quality metrics
     * Business logic:
     * - Prime quality items command a premium (up to 20% increase)
     * - Rejected items reduce the price (up to 30% decrease)
     */
    private double calculatePriceAdjustment(OrderEvent event) {
        if (event.getPricePerUnit() == null || event.getQuantityOrdered() == null) {
            return event.getTotalPrice();
        }
        
        // Apply quality-based pricing
        double primeBonus = event.getPrimeRate() != null ? 
            event.getPrimeRate() * PRIME_BONUS_MULTIPLIER : 0.0;
        double rejectionPenalty = event.getRejectionRate() != null ? 
            event.getRejectionRate() * REJECTION_PENALTY_MULTIPLIER : 0.0;
        
        double adjustmentFactor = 1.0 + primeBonus - rejectionPenalty;
        double adjustedPricePerUnit = event.getPricePerUnit() * adjustmentFactor;
        
        return adjustedPricePerUnit * event.getQuantityOrdered();
    }
}
