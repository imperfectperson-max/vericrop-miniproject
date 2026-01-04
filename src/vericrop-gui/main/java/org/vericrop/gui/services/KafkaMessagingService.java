package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.models.BatchRecord;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Messaging Service for publishing events to Apache Kafka.
 * 
 * Features:
 * - Apache Kafka client producer with configurable settings
 * - Idempotent producer with acks=all for reliability
 * - Environment-driven configuration
 * - Typed batch and event publishing methods
 * - Async and sync publishing options
 */
public class KafkaMessagingService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingService.class);
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final ConfigService config;
    private final boolean enabled;

    /**
     * Constructor with configuration from ConfigService
     */
    public KafkaMessagingService(ConfigService config) {
        this.config = config;
        this.enabled = config.isKafkaEnabled();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        if (enabled) {
            Properties props = createProducerProperties();
            this.producer = new KafkaProducer<>(props);
            logger.info("✅ KafkaMessagingService initialized and connected to {}", 
                    config.getKafkaBootstrapServers());
        } else {
            this.producer = null;
            logger.info("⚠️  KafkaMessagingService initialized but DISABLED (kafka.enabled=false)");
        }
    }

    /**
     * Create Kafka producer properties from configuration
     */
    private Properties createProducerProperties() {
        Properties props = new Properties();
        
        // Bootstrap servers
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        
        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, config.getKafkaAcks());  // all, 1, or 0
        props.put(ProducerConfig.RETRIES_CONFIG, config.getKafkaRetries());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.getKafkaIdempotence());
        
        // Performance tuning
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getKafkaBatchSize());
        props.put(ProducerConfig.LINGER_MS_CONFIG, config.getKafkaLingerMs());
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getKafkaBufferMemory());
        
        // Compression for better throughput
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        // Max in-flight requests (1 for strict ordering with idempotence)
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Client ID for tracking
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "vericrop-gui-producer");
        
        logger.debug("Kafka producer properties configured: bootstrap={}, acks={}, retries={}, idempotence={}",
                config.getKafkaBootstrapServers(), config.getKafkaAcks(), 
                config.getKafkaRetries(), config.getKafkaIdempotence());
        
        return props;
    }

    /**
     * Send a batch record to Kafka (async)
     * @param batchRecord The batch record to send
     * @return CompletableFuture with record metadata
     */
    public CompletableFuture<RecordMetadata> sendBatch(BatchRecord batchRecord) {
        String topic = config.getKafkaTopicBatchEvents();
        return sendEvent(topic, batchRecord.getBatchId(), batchRecord);
    }

    /**
     * Send a batch record to Kafka (sync)
     * @param batchRecord The batch record to send
     * @return RecordMetadata
     */
    public RecordMetadata sendBatchSync(BatchRecord batchRecord) throws ExecutionException, InterruptedException {
        return sendBatch(batchRecord).get();
    }

    /**
     * Send a generic event to a specific topic (async)
     * @param topic Kafka topic
     * @param key Message key (used for partitioning and idempotency)
     * @param payload Event payload (will be serialized to JSON)
     * @return CompletableFuture with record metadata
     */
    public <T> CompletableFuture<RecordMetadata> sendEvent(String topic, String key, T payload) {
        if (!enabled) {
            logger.warn("Kafka is disabled. Event not sent to topic '{}': {}", topic, payload);
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Serialize payload to JSON
            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            // Generate idempotency key if not provided
            String messageKey = key != null ? key : UUID.randomUUID().toString();
            
            // Create producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, messageKey, jsonPayload);
            
            // Send asynchronously
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send message to topic '{}': {}", topic, exception.getMessage(), exception);
                    future.completeExceptionally(exception);
                } else {
                    logger.debug("Message sent successfully to topic '{}' partition {} offset {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                    future.complete(metadata);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            logger.error("Error serializing or sending message to topic '{}': {}", topic, e.getMessage(), e);
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Send a generic event to a specific topic (sync)
     * @param topic Kafka topic
     * @param key Message key
     * @param payload Event payload
     * @return RecordMetadata
     */
    public <T> RecordMetadata sendEventSync(String topic, String key, T payload) 
            throws ExecutionException, InterruptedException {
        return sendEvent(topic, key, payload).get();
    }

    /**
     * Send event to batch-events topic
     */
    public CompletableFuture<RecordMetadata> sendBatchEvent(String batchId, Object event) {
        return sendEvent(config.getKafkaTopicBatchEvents(), batchId, event);
    }

    /**
     * Send event to quality-alerts topic
     */
    public CompletableFuture<RecordMetadata> sendQualityAlert(String batchId, Object alert) {
        return sendEvent(config.getKafkaTopicQualityAlerts(), batchId, alert);
    }

    /**
     * Send event to logistics-events topic
     */
    public CompletableFuture<RecordMetadata> sendLogisticsEvent(String batchId, Object event) {
        return sendEvent(config.getKafkaTopicLogisticsEvents(), batchId, event);
    }

    /**
     * Send event to blockchain-events topic
     */
    public CompletableFuture<RecordMetadata> sendBlockchainEvent(String transactionId, Object event) {
        return sendEvent(config.getKafkaTopicBlockchainEvents(), transactionId, event);
    }

    /**
     * Flush all pending messages
     */
    public void flush() {
        if (enabled && producer != null) {
            logger.debug("Flushing Kafka producer...");
            producer.flush();
            logger.debug("Kafka producer flushed successfully");
        }
    }

    /**
     * Check if Kafka is enabled and producer is ready
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Test Kafka connection by sending a test message
     */
    public boolean testConnection() {
        if (!enabled) {
            logger.warn("Kafka is disabled, connection test skipped");
            return false;
        }
        
        try {
            String testTopic = config.getKafkaTopicBatchEvents();
            String testMessage = "{\"test\": \"connection\", \"timestamp\": " + System.currentTimeMillis() + "}";
            
            Future<RecordMetadata> future = producer.send(
                    new ProducerRecord<>(testTopic, "test-key", testMessage));
            
            // Wait up to 5 seconds for the test message
            future.get(5, TimeUnit.SECONDS);
            logger.info("✅ Kafka connection test successful");
            return true;
            
        } catch (Exception e) {
            logger.error("❌ Kafka connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Close Kafka producer and release resources
     */
    public void shutdown() {
        if (producer != null) {
            logger.info("Shutting down Kafka producer...");
            producer.close();
            logger.info("Kafka producer shutdown complete");
        }
    }
}
