package org.vericrop.kafka.consumers;

import org.vericrop.dto.TemperatureComplianceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Consumer for temperature compliance events from the temperature-compliance topic
 */
public class TemperatureComplianceEventConsumer {
    private static final String TOPIC = "temperature-compliance";
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private volatile boolean running = true;
    private final Consumer<TemperatureComplianceEvent> eventHandler;

    public TemperatureComplianceEventConsumer(String groupId, Consumer<TemperatureComplianceEvent> eventHandler) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", groupId);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("auto.offset.reset", "earliest");

        this.consumer = new KafkaConsumer<>(props);
        this.mapper = new ObjectMapper();
        this.eventHandler = eventHandler;

        // Subscribe to topic
        consumer.subscribe(Collections.singletonList(TOPIC));
        System.out.println("✅ Temperature compliance consumer subscribed to: " + TOPIC);
    }

    public void startConsuming() {
        System.out.println("🔄 Starting temperature compliance event consumer...");

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        TemperatureComplianceEvent event = mapper.readValue(record.value(), TemperatureComplianceEvent.class);
                        if (eventHandler != null) {
                            eventHandler.accept(event);
                        }
                        System.out.println("🌡️ Processed temperature event: " + event);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to process temperature compliance event: " + e.getMessage());
                    }
                }
            }
        } finally {
            consumer.close();
        }
    }

    public void stop() {
        running = false;
    }
}
