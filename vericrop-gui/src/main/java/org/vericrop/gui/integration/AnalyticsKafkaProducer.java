package org.vericrop.gui.integration;

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

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer specifically for analytics events.
 * Publishes to vericrop.analytics.requests topic.
 */
public class AnalyticsKafkaProducer {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsKafkaProducer.class);
    private static final String ANALYTICS_TOPIC = "vericrop.analytics.requests";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    
    /**
     * Constructor with configuration
     */
    public AnalyticsKafkaProducer(ConfigService config) {
        this.enabled = config.isKafkaEnabled();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        if (enabled) {
            Properties props = createProducerProperties(config);
            this.producer = new KafkaProducer<>(props);
            logger.info("✅ AnalyticsKafkaProducer initialized and connected to {}", 
                    config.getKafkaBootstrapServers());
        } else {
            this.producer = null;
            logger.info("⚠️ AnalyticsKafkaProducer initialized but DISABLED (kafka.enabled=false)");
        }
    }
    
    /**
     * Create Kafka producer properties
     */
    private Properties createProducerProperties(ConfigService config) {
        Properties props = new Properties();
        
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Performance tuning
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Client ID
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "vericrop-analytics-producer");
        
        return props;
    }
    
    /**
     * Publish an analytics request event to Kafka
     * 
     * @param jobId The job ID
     * @param datasetId The dataset ID
     * @param parameters Job parameters
     * @param timestamp Event timestamp
     * @return CompletableFuture with record metadata
     */
    public CompletableFuture<RecordMetadata> publishAnalyticsRequest(
            String jobId, String datasetId, Object parameters, long timestamp) {
        
        if (!enabled) {
            logger.warn("Kafka is disabled. Analytics request not published: jobId={}", jobId);
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Build event payload
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("job_id", jobId);
            event.put("dataset_id", datasetId);
            event.put("parameters", parameters);
            event.put("timestamp", timestamp);
            
            String jsonPayload = objectMapper.writeValueAsString(event);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    ANALYTICS_TOPIC, jobId, jsonPayload);
            
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish analytics request: {}", exception.getMessage(), exception);
                    future.completeExceptionally(exception);
                } else {
                    logger.info("✅ Published analytics request to Kafka: jobId={}, partition={}, offset={}", 
                            jobId, metadata.partition(), metadata.offset());
                    future.complete(metadata);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            logger.error("Error publishing analytics request: {}", e.getMessage(), e);
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * Check if Kafka is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Flush all pending messages
     */
    public void flush() {
        if (enabled && producer != null) {
            producer.flush();
        }
    }
    
    /**
     * Close producer and release resources
     */
    public void shutdown() {
        if (producer != null) {
            logger.info("Shutting down AnalyticsKafkaProducer...");
            producer.close();
            logger.info("AnalyticsKafkaProducer shutdown complete");
        }
    }
}
