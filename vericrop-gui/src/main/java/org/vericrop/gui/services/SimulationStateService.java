package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service for managing shared simulation state across multiple instances.
 * Uses Kafka for event sourcing and state synchronization.
 * 
 * Features:
 * - Subscribes to Kafka topic for state events on startup
 * - Rebuilds in-memory state from events
 * - Publishes state changes to Kafka
 * - Provides callback for SSE broadcasting
 * - Ensures idempotency using event offsets
 */
@Service
public class SimulationStateService {
    private static final Logger logger = LoggerFactory.getLogger(SimulationStateService.class);
    
    private static final String TOPIC_NAME = "vericrop-simulation-state";
    private static final String CONSUMER_GROUP = "vericrop-state-consumer";
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private static final int MAX_EVENT_HISTORY = 1000;
    
    @Value("${kafka.bootstrap.servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.enabled:true}")
    private boolean kafkaEnabled;
    
    private final ObjectMapper objectMapper;
    private final Map<String, Object> currentState;
    private final java.util.Deque<StateEvent> eventHistory; // Using Deque for efficient removal
    private final AtomicBoolean running;
    
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private ExecutorService consumerExecutor;
    private Consumer<Map<String, Object>> stateBroadcastCallback;
    private final Map<Integer, Long> lastProcessedOffsets = new ConcurrentHashMap<>(); // Per-partition offsets
    
    public SimulationStateService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.currentState = new ConcurrentHashMap<>();
        this.eventHistory = new java.util.concurrent.ConcurrentLinkedDeque<>();
        this.running = new AtomicBoolean(false);
        
        // Initialize default state
        initializeDefaultState();
    }
    
    /**
     * Initialize default simulation state.
     */
    private void initializeDefaultState() {
        currentState.put("simulation_active", false);
        currentState.put("current_step", 0);
        currentState.put("scenario_id", null);
        currentState.put("participants", new ConcurrentHashMap<String, Object>());
        currentState.put("batches", new ArrayList<>());
        currentState.put("temperature", 4.0);
        currentState.put("humidity", 85.0);
        currentState.put("location", "Origin");
        currentState.put("quality_score", 100.0);
        currentState.put("alerts", new ArrayList<>());
        currentState.put("last_updated", System.currentTimeMillis());
        currentState.put("version", 1);
    }
    
    @PostConstruct
    public void initialize() {
        if (!kafkaEnabled) {
            logger.info("Kafka is disabled. SimulationStateService running in local-only mode.");
            return;
        }
        
        try {
            initializeKafkaProducer();
            initializeKafkaConsumer();
            startConsumerThread();
            logger.info("✅ SimulationStateService initialized with Kafka support");
        } catch (Exception e) {
            logger.warn("Failed to initialize Kafka: {}. Running in local-only mode.", e.getMessage());
        }
    }
    
    private void initializeKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "vericrop-state-producer-" + UUID.randomUUID().toString().substring(0, 8));
        
        this.producer = new KafkaProducer<>(props);
        logger.debug("Kafka producer initialized for state events");
    }
    
    private void initializeKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP + "-" + UUID.randomUUID().toString().substring(0, 8));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "vericrop-state-consumer-" + UUID.randomUUID().toString().substring(0, 8));
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(TOPIC_NAME));
        logger.debug("Kafka consumer initialized and subscribed to {}", TOPIC_NAME);
    }
    
    private void startConsumerThread() {
        running.set(true);
        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "simulation-state-consumer");
            t.setDaemon(true);
            return t;
        });
        
        consumerExecutor.submit(() -> {
            try {
                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        processStateEvent(record);
                    }
                    
                    if (!records.isEmpty()) {
                        consumer.commitSync();
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    logger.error("Error in state consumer thread: {}", e.getMessage(), e);
                }
            }
        });
        
        logger.info("State consumer thread started");
    }
    
    /**
     * Process a state event from Kafka.
     */
    private void processStateEvent(ConsumerRecord<String, String> record) {
        try {
            int partition = record.partition();
            long offset = record.offset();
            
            // Skip already processed events (idempotency - per partition)
            Long lastOffset = lastProcessedOffsets.get(partition);
            if (lastOffset != null && offset <= lastOffset) {
                logger.trace("Skipping already processed event at partition {} offset {}", partition, offset);
                return;
            }
            
            StateEvent event = objectMapper.readValue(record.value(), StateEvent.class);
            
            // Apply event to state
            applyEventToState(event);
            
            // Track for history (efficient removal from deque)
            eventHistory.addLast(event);
            while (eventHistory.size() > MAX_EVENT_HISTORY) {
                eventHistory.removeFirst(); // Efficient O(1) removal from deque
            }
            
            lastProcessedOffsets.put(partition, offset);
            
            logger.debug("Processed state event: {} from partition {} offset {}", event.getEventType(), partition, offset);
            
        } catch (Exception e) {
            logger.error("Error processing state event at offset {}: {}", record.offset(), e.getMessage());
        }
    }
    
    /**
     * Apply a state event to the current state.
     */
    @SuppressWarnings("unchecked")
    private void applyEventToState(StateEvent event) {
        synchronized (currentState) {
            String eventType = event.getEventType();
            Map<String, Object> data = event.getData();
            
            switch (eventType) {
                case "SIMULATION_STARTED":
                    currentState.put("simulation_active", true);
                    currentState.put("scenario_id", data.get("scenario_id"));
                    currentState.put("current_step", 0);
                    currentState.put("started_at", event.getTimestamp());
                    break;
                    
                case "SIMULATION_STOPPED":
                    currentState.put("simulation_active", false);
                    currentState.put("ended_at", event.getTimestamp());
                    break;
                    
                case "STEP_UPDATE":
                    currentState.put("current_step", data.get("step"));
                    currentState.put("temperature", data.getOrDefault("temperature", currentState.get("temperature")));
                    currentState.put("humidity", data.getOrDefault("humidity", currentState.get("humidity")));
                    currentState.put("location", data.getOrDefault("location", currentState.get("location")));
                    currentState.put("quality_score", data.getOrDefault("quality_score", currentState.get("quality_score")));
                    break;
                    
                case "PARTICIPANT_JOINED":
                    Map<String, Object> participants = (Map<String, Object>) currentState.get("participants");
                    participants.put((String) data.get("user_id"), data);
                    break;
                    
                case "PARTICIPANT_LEFT":
                    Map<String, Object> participantsRemove = (Map<String, Object>) currentState.get("participants");
                    participantsRemove.remove(data.get("user_id"));
                    break;
                    
                case "ALERT_ADDED":
                    List<Object> alerts = (List<Object>) currentState.get("alerts");
                    alerts.add(data);
                    break;
                    
                case "BATCH_ADDED":
                    List<Object> batches = (List<Object>) currentState.get("batches");
                    batches.add(data);
                    break;
                    
                case "STATE_UPDATE":
                    // Generic state update - merge data into current state
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        currentState.put(entry.getKey(), entry.getValue());
                    }
                    break;
                    
                default:
                    logger.warn("Unknown event type: {}", eventType);
            }
            
            // Update metadata
            currentState.put("last_updated", event.getTimestamp());
            currentState.put("version", (int) currentState.getOrDefault("version", 0) + 1);
            
            // Broadcast to SSE clients
            if (stateBroadcastCallback != null) {
                Map<String, Object> broadcastState = new HashMap<>(currentState);
                broadcastState.put("event_type", eventType);
                broadcastState.put("source_role", event.getRole());
                stateBroadcastCallback.accept(broadcastState);
            }
        }
    }
    
    /**
     * Get current simulation state.
     */
    public Map<String, Object> getCurrentState() {
        synchronized (currentState) {
            return new HashMap<>(currentState);
        }
    }
    
    /**
     * Apply a state update and publish to Kafka.
     * 
     * @param eventType Type of state event
     * @param role Role of the user making the update (farmer, supplier, admin, consumer)
     * @param data Event data
     * @return Updated state
     */
    public Map<String, Object> applyStateUpdate(String eventType, String role, Map<String, Object> data) {
        StateEvent event = new StateEvent(eventType, role, data, System.currentTimeMillis());
        
        // Publish to Kafka if enabled
        if (kafkaEnabled && producer != null) {
            publishEvent(event);
        }
        
        // Apply locally
        applyEventToState(event);
        
        return getCurrentState();
    }
    
    /**
     * Publish state event to Kafka.
     */
    private void publishEvent(StateEvent event) {
        try {
            String key = event.getEventType() + "-" + System.currentTimeMillis();
            String value = objectMapper.writeValueAsString(event);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, key, value);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish state event: {}", exception.getMessage());
                } else {
                    logger.debug("State event published to partition {} offset {}", 
                               metadata.partition(), metadata.offset());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error publishing state event: {}", e.getMessage());
        }
    }
    
    /**
     * Set callback for broadcasting state to SSE clients.
     */
    public void setStateBroadcastCallback(Consumer<Map<String, Object>> callback) {
        this.stateBroadcastCallback = callback;
    }
    
    /**
     * Check if Kafka is enabled.
     */
    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down SimulationStateService...");
        running.set(false);
        
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    consumerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                consumerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                logger.warn("Error closing consumer: {}", e.getMessage());
            }
        }
        
        if (producer != null) {
            producer.close();
        }
        
        logger.info("SimulationStateService shutdown complete");
    }
    
    /**
     * State event DTO for Kafka serialization.
     */
    public static class StateEvent {
        private String eventType;
        private String role;
        private Map<String, Object> data;
        private long timestamp;
        
        public StateEvent() {}
        
        public StateEvent(String eventType, String role, Map<String, Object> data, long timestamp) {
            this.eventType = eventType;
            this.role = role;
            this.data = data;
            this.timestamp = timestamp;
        }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
