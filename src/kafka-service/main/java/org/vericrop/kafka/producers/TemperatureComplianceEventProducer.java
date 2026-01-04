package org.vericrop.kafka.producers;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.vericrop.dto.TemperatureComplianceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;

/**
 * Producer for temperature compliance events
 */
public class TemperatureComplianceEventProducer {
    private static final String TOPIC = "temperature-compliance";
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public TemperatureComplianceEventProducer() {
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

    public void sendTemperatureComplianceEvent(TemperatureComplianceEvent event) {
        try {
            String value = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.getBatchId(), value);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Error producing temperature compliance event: " + exception.getMessage());
                } else {
                    System.out.println("Temperature compliance event sent: " + event);
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to serialize temperature compliance event: " + e.getMessage());
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
