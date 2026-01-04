package org.vericrop.kafka.producers;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.vericrop.dto.MapSimulationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;

/**
 * Producer for map simulation events
 */
public class MapSimulationEventProducer {
    private static final String TOPIC = "map-simulation";
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public MapSimulationEventProducer() {
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

    public void sendMapSimulationEvent(MapSimulationEvent event) {
        try {
            String value = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.getBatchId(), value);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Error producing map simulation event: " + exception.getMessage());
                } else {
                    System.out.println("Map simulation event sent: " + event);
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to serialize map simulation event: " + e.getMessage());
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
