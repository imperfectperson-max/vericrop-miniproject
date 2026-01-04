package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.LogisticsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import java.util.concurrent.Future;

public class LogisticsEventProducer {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;

    public LogisticsEventProducer() {
        this(KafkaConfig.TOPIC_LOGISTICS_TRACKING);
    }

    public LogisticsEventProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
    }

    public void sendLogisticsEvent(LogisticsEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getBatchId(), eventJson);

            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Error sending logistics event: " + exception.getMessage());
                } else {
                    System.out.println("📦 Logistics event sent: " + event.getBatchId() +
                            " to partition " + metadata.partition());
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Failed to serialize/send logistics event: " + e.getMessage());
        }
    }

    public void close() {
        producer.close();
    }
}