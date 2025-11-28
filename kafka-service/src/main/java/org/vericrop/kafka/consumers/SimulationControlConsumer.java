package org.vericrop.kafka.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.SimulationControlEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Kafka consumer for simulation control events.
 * Used by LogisticsController and ConsumerController to receive simulation
 * START/STOP events from ProducerController across different application instances.
 */
public class SimulationControlConsumer {
    
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;
    private final Consumer<SimulationControlEvent> eventHandler;
    private final String groupId;
    
    /**
     * Create a new simulation control consumer with a custom event handler.
     * 
     * @param groupId Consumer group ID (should be unique per instance for broadcast)
     * @param eventHandler Handler function called for each received event
     */
    public SimulationControlConsumer(String groupId, Consumer<SimulationControlEvent> eventHandler) {
        this.groupId = groupId;
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties(groupId));
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        this.eventHandler = eventHandler;
    }
    
    /**
     * Start consuming simulation control events.
     * This method blocks until stop() is called.
     */
    public void startConsuming() {
        running.set(true);
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC_SIMULATION_CONTROL));
        
        System.out.println("🎧 SimulationControlConsumer started for group: " + groupId);
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        SimulationControlEvent event = objectMapper.readValue(
                            record.value(), SimulationControlEvent.class);
                        
                        System.out.println("📩 Received simulation control event: " + event.getAction() + 
                                         " for simulation " + event.getSimulationId());
                        
                        // Call the event handler
                        if (eventHandler != null) {
                            eventHandler.accept(event);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error parsing simulation control event: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("❌ Error consuming simulation control events: " + e.getMessage());
                    // Wait a bit before retrying
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        System.out.println("🛑 SimulationControlConsumer stopped for group: " + groupId);
    }
    
    /**
     * Stop consuming events.
     */
    public void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }
    
    /**
     * Close the consumer and release resources.
     */
    public void close() {
        stop();
        if (consumer != null) {
            consumer.close();
        }
    }
    
    /**
     * Check if the consumer is currently running.
     * 
     * @return true if consuming, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
}
