package org.vericrop.kafka.consumers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.QualityAlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class QualityAlertConsumer {
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private final String topic;
    private volatile boolean running = true;

    public QualityAlertConsumer(String groupId) {
        this(KafkaConfig.TOPIC_QUALITY_ALERTS, groupId);
    }

    public QualityAlertConsumer(String topic, String groupId) {
        Properties props = KafkaConfig.getConsumerProperties(groupId);
        this.consumer = new KafkaConsumer<>(props);
        this.mapper = new ObjectMapper();
        this.topic = topic;

        consumer.subscribe(Collections.singletonList(topic));
        System.out.println("✅ Quality alert consumer subscribed to: " + topic);
    }

    public void startConsuming() {
        System.out.println("🔄 Starting quality alert consumer...");

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        QualityAlertEvent alert = mapper.readValue(record.value(), QualityAlertEvent.class);
                        processQualityAlert(alert, record);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to process quality alert: " + e.getMessage());
                    }
                }
            }
        } finally {
            consumer.close();
        }
    }

    private void processQualityAlert(QualityAlertEvent alert, ConsumerRecord<String, String> record) {
        System.out.println("🚨 Processing quality alert: " + alert);

        // Handle different alert types
        switch (alert.getAlertType()) {
            case "TEMPERATURE_BREACH":
                handleTemperatureBreach(alert);
                break;
            case "QUALITY_DROP":
                handleQualityDrop(alert);
                break;
            case "HUMIDITY_BREACH":
                handleHumidityBreach(alert);
                break;
            default:
                System.out.println("   ⚠️ Unknown alert type: " + alert.getAlertType());
        }

        // Send notifications based on severity
        switch (alert.getSeverity()) {
            case "CRITICAL":
                System.out.println("   🔴 CRITICAL ALERT - Immediate action required!");
                // sendSMSNotification(alert);
                // sendEmailAlert(alert);
                break;
            case "HIGH":
                System.out.println("   🟠 HIGH severity - Review needed");
                break;
            case "MEDIUM":
                System.out.println("   🟡 MEDIUM severity - Monitor closely");
                break;
            case "LOW":
                System.out.println("   🔵 LOW severity - Informational");
                break;
        }
    }

    private void handleTemperatureBreach(QualityAlertEvent alert) {
        System.out.println("   🌡️ Temperature breach detected: " +
                alert.getCurrentValue() + "°C (threshold: " +
                alert.getThresholdValue() + "°C)");
    }

    private void handleQualityDrop(QualityAlertEvent alert) {
        System.out.println("   📉 Quality drop detected for batch: " + alert.getBatchId());
    }

    private void handleHumidityBreach(QualityAlertEvent alert) {
        System.out.println("   💧 Humidity breach detected: " +
                alert.getCurrentValue() + "% (threshold: " +
                alert.getThresholdValue() + "%)");
    }

    public void stop() {
        running = false;
    }
}