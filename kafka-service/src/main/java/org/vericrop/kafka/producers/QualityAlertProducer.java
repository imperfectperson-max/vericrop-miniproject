package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.QualityAlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class QualityAlertProducer {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;

    public QualityAlertProducer() {
        this(KafkaConfig.TOPIC_QUALITY_ALERTS);
    }

    public QualityAlertProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
    }

    public void sendQualityAlert(QualityAlertEvent alert) {
        try {
            String alertJson = mapper.writeValueAsString(alert);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, alert.getBatchId(), alertJson);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Error sending quality alert: " + exception.getMessage());
                } else {
                    System.out.println("🚨 Quality alert sent: " + alert.getBatchId() +
                            " - " + alert.getAlertType());
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Failed to serialize/send quality alert: " + e.getMessage());
        }
    }

    public void close() {
        producer.close();
    }
}