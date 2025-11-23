package org.vericrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.service.models.Alert;

/**
 * Helper interface for publishing alerts to Kafka.
 * This allows optional Kafka integration without creating a hard dependency on kafka-service.
 * 
 * Example implementation in GUI layer:
 * <pre>
 * KafkaAlertPublisher kafkaPublisher = (alert) -> {
 *     if (qualityAlertProducer != null && logisticsProducer != null) {
 *         switch (alert.getType()) {
 *             case TEMPERATURE_HIGH:
 *             case TEMPERATURE_LOW:
 *             case HUMIDITY_HIGH:
 *             case HUMIDITY_LOW:
 *                 QualityAlertEvent qae = new QualityAlertEvent(
 *                     alert.getBatchId(),
 *                     alert.getType().name(),
 *                     alert.getSeverity().name(),
 *                     alert.getMessage(),
 *                     alert.getCurrentValue(),
 *                     alert.getThresholdValue()
 *                 );
 *                 qae.setLocation(alert.getLocation());
 *                 qae.setTimestamp(alert.getTimestamp());
 *                 qualityAlertProducer.sendQualityAlert(qae);
 *                 break;
 *             case DELIVERY_DELAY:
 *                 LogisticsEvent le = new LogisticsEvent(
 *                     alert.getBatchId(),
 *                     "DELAYED",
 *                     0, 0,
 *                     alert.getLocation()
 *                 );
 *                 le.setTimestamp(alert.getTimestamp());
 *                 logisticsProducer.sendLogisticsEvent(le);
 *                 break;
 *         }
 *     }
 * };
 * 
 * alertService.addListener(kafkaPublisher::publish);
 * </pre>
 */
@FunctionalInterface
public interface KafkaAlertPublisher {
    
    /**
     * Publish an alert to Kafka.
     * Implementation should handle null producers gracefully.
     */
    void publish(Alert alert);
    
    /**
     * Create a no-op publisher that logs warnings.
     */
    static KafkaAlertPublisher noOp() {
        Logger logger = LoggerFactory.getLogger(KafkaAlertPublisher.class);
        return (alert) -> {
            logger.warn("Kafka not configured, alert not published: {}", alert);
        };
    }
}
