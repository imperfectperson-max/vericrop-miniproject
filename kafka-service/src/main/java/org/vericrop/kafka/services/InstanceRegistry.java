package org.vericrop.kafka.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.InstanceHeartbeatEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for tracking running application instances using Kafka.
 * Each instance sends periodic heartbeats to the instance-registry topic.
 * The service maintains a count of active instances based on recent heartbeats.
 * 
 * This is used to enforce the requirement that simulations should only start
 * across all controllers when at least 3 instances are running.
 */
public class InstanceRegistry implements AutoCloseable {
    
    /** Default heartbeat interval in milliseconds */
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // 5 seconds
    
    /** Time after which an instance is considered dead if no heartbeat received */
    private static final long INSTANCE_TIMEOUT_MS = 15000; // 15 seconds
    
    /** Minimum number of instances required for coordinated simulation start */
    private static final int MIN_INSTANCES_FOR_COORDINATION = 3;
    
    private final String instanceId;
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> instanceLastSeen;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    /**
     * Create a new instance registry with a random instance ID.
     */
    public InstanceRegistry() {
        this(UUID.randomUUID().toString());
    }
    
    /**
     * Create a new instance registry with a specific instance ID.
     * 
     * @param instanceId Unique identifier for this instance
     */
    public InstanceRegistry(String instanceId) {
        this.instanceId = instanceId;
        this.objectMapper = new ObjectMapper();
        this.instanceLastSeen = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Initialize Kafka producer and consumer
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties(
            "instance-registry-" + instanceId));
    }
    
    /**
     * Start the instance registry.
     * Begins sending heartbeats and consuming heartbeats from other instances.
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }
        
        System.out.println("🔄 Starting InstanceRegistry for instance: " + instanceId);
        
        // Start heartbeat sender
        scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            0,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // Start heartbeat consumer in background thread
        scheduler.submit(this::consumeHeartbeats);
        
        // Start cleanup task to remove stale instances
        scheduler.scheduleAtFixedRate(
            this::cleanupStaleInstances,
            INSTANCE_TIMEOUT_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("✅ InstanceRegistry started for instance: " + instanceId);
    }
    
    /**
     * Send a heartbeat event to indicate this instance is alive.
     */
    private void sendHeartbeat() {
        try {
            InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId);
            String json = objectMapper.writeValueAsString(event);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_INSTANCE_REGISTRY,
                instanceId,
                json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Failed to send heartbeat: " + exception.getMessage());
                }
            });
            
            // Also update our own timestamp
            instanceLastSeen.put(instanceId, System.currentTimeMillis());
            
        } catch (Exception e) {
            System.err.println("❌ Error sending heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Consume heartbeat events from other instances.
     */
    private void consumeHeartbeats() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC_INSTANCE_REGISTRY));
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        InstanceHeartbeatEvent event = objectMapper.readValue(
                            record.value(), InstanceHeartbeatEvent.class);
                        
                        if (event.isAlive()) {
                            instanceLastSeen.put(event.getInstanceId(), event.getTimestamp());
                        } else if (event.isShutdown()) {
                            instanceLastSeen.remove(event.getInstanceId());
                            System.out.println("📤 Instance shutdown: " + event.getInstanceId());
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error parsing heartbeat event: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("❌ Error consuming heartbeats: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Remove instances that haven't sent a heartbeat recently.
     * Note: The current instance is never removed as stale because its heartbeat
     * is refreshed in sendHeartbeat() before this cleanup runs.
     */
    private void cleanupStaleInstances() {
        long now = System.currentTimeMillis();
        instanceLastSeen.entrySet().removeIf(entry -> {
            // Never remove ourselves - our heartbeat should always be fresh
            if (entry.getKey().equals(instanceId)) {
                return false;
            }
            boolean isStale = (now - entry.getValue()) > INSTANCE_TIMEOUT_MS;
            if (isStale) {
                System.out.println("🗑️ Removing stale instance: " + entry.getKey());
            }
            return isStale;
        });
    }
    
    /**
     * Get the number of active instances (including this one).
     * 
     * @return Count of active instances
     */
    public int getActiveInstanceCount() {
        cleanupStaleInstances();
        return instanceLastSeen.size();
    }
    
    /**
     * Check if enough instances are running for coordinated simulation start.
     * 
     * @return true if at least MIN_INSTANCES_FOR_COORDINATION instances are active
     */
    public boolean hasEnoughInstances() {
        return getActiveInstanceCount() >= MIN_INSTANCES_FOR_COORDINATION;
    }
    
    /**
     * Get the minimum number of instances required for coordinated simulation.
     * 
     * @return Minimum instance count
     */
    public int getMinInstancesRequired() {
        return MIN_INSTANCES_FOR_COORDINATION;
    }
    
    /**
     * Get this instance's unique ID.
     * 
     * @return Instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Send a shutdown heartbeat and stop the registry.
     */
    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return; // Already stopped
        }
        
        System.out.println("🛑 Stopping InstanceRegistry for instance: " + instanceId);
        
        // Send shutdown heartbeat
        try {
            InstanceHeartbeatEvent shutdownEvent = InstanceHeartbeatEvent.shutdown(instanceId);
            String json = objectMapper.writeValueAsString(shutdownEvent);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_INSTANCE_REGISTRY,
                instanceId,
                json
            );
            
            producer.send(record).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("⚠️ Could not send shutdown heartbeat: " + e.getMessage());
        }
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close Kafka clients
        if (consumer != null) {
            consumer.wakeup();
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
        
        System.out.println("✅ InstanceRegistry stopped for instance: " + instanceId);
    }
}
