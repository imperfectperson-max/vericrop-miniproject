package org.vericrop.kafka.producers;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.vericrop.dto.BatchUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;

/**
 * Producer for batch update events
 */
public class BatchUpdateEventProducer {
    private static final String TOPIC = "batch-updates";
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public BatchUpdateEventProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "1");
        props.put("retries", 3);
        props.put("max.in.flight.requests.per.connection", 1);

        this.producer = new KafkaProducer<>(props);
        this.objectMapper = new ObjectMapper();
    }

    public void sendBatchUpdateEvent(BatchUpdateEvent event) {
        try {
            String value = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.getBatchId(), value);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Error producing batch update event: " + exception.getMessage());
                } else {
                    System.out.println("Batch update event sent: " + event);
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to serialize batch update event: " + e.getMessage());
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
