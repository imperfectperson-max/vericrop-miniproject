package org.vericrop.kafka.consumers;

import org.vericrop.dto.MapSimulationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Consumer for map simulation events from the map-simulation topic
 */
public class MapSimulationEventConsumer {
    private static final String TOPIC = "map-simulation";
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private volatile boolean running = true;
    private final Consumer<MapSimulationEvent> eventHandler;

    public MapSimulationEventConsumer(String groupId, Consumer<MapSimulationEvent> eventHandler) {
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
        System.out.println("✅ Map simulation consumer subscribed to: " + TOPIC);
    }

    public void startConsuming() {
        System.out.println("🔄 Starting map simulation event consumer...");

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        MapSimulationEvent event = mapper.readValue(record.value(), MapSimulationEvent.class);
                        if (eventHandler != null) {
                            eventHandler.accept(event);
                        }
                        System.out.println("🗺️ Processed map event: " + event);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to process map simulation event: " + e.getMessage());
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
