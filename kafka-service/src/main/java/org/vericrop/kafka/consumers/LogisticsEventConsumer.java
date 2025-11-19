package org.vericrop.kafka.consumers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.LogisticsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class LogisticsEventConsumer {
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private final String topic;
    private volatile boolean running = true;

    public LogisticsEventConsumer(String groupId) {
        this(KafkaConfig.TOPIC_LOGISTICS_TRACKING, groupId);
    }

    public LogisticsEventConsumer(String topic, String groupId) {
        Properties props = KafkaConfig.getConsumerProperties(groupId);
        this.consumer = new KafkaConsumer<>(props);
        this.mapper = new ObjectMapper();
        this.topic = topic;

        // Subscribe to topic
        consumer.subscribe(Collections.singletonList(topic));
        System.out.println("✅ Logistics consumer subscribed to: " + topic);
    }

    public void startConsuming() {
        System.out.println("🔄 Starting logistics event consumer...");

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        LogisticsEvent event = mapper.readValue(record.value(), LogisticsEvent.class);
                        processLogisticsEvent(event, record);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to process logistics event: " + e.getMessage());
                    }
                }
            }
        } finally {
            consumer.close();
        }
    }

    private void processLogisticsEvent(LogisticsEvent event, ConsumerRecord<String, String> record) {
        System.out.println("📦 Received logistics update: " + event);

        // Here you would:
        // 1. Update the logistics dashboard in real-time
        // 2. Store in database for analytics
        // 3. Trigger notifications if needed
        // 4. Update blockchain with transit events

        // Example processing:
        switch (event.getStatus()) {
            case "IN_TRANSIT":
                System.out.println("   🚚 Batch " + event.getBatchId() + " is in transit");
                break;
            case "AT_WAREHOUSE":
                System.out.println("   🏭 Batch " + event.getBatchId() + " arrived at warehouse");
                break;
            case "DELIVERED":
                System.out.println("   ✅ Batch " + event.getBatchId() + " delivered successfully");
                break;
        }

        // Check temperature thresholds
        if (event.getTemperature() > 8.0) {
            System.out.println("   ⚠️ Temperature alert: " + event.getTemperature() + "°C");
        }
    }

    public void stop() {
        running = false;
    }
}