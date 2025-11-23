package org.vericrop.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Generic Kafka publisher utility for publishing JSON messages to topics.
 * Reusable across services and controllers.
 */
@Component
public class KafkaPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisher.class);
    
    @Value("${kafka.bootstrap.servers:localhost:9092}")
    private String bootstrapServers;
    
    private KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    
    public KafkaPublisher() {
        this.objectMapper = new ObjectMapper();
    }
    
    @PostConstruct
    public void initialize() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Safe producer settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        
        // Timeout settings for resilience
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);  // 10 seconds
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);  // 30 seconds
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);  // 5 seconds for metadata fetch
        
        this.producer = new KafkaProducer<>(props);
        logger.info("KafkaPublisher initialized with bootstrap servers: {}", bootstrapServers);
    }
    
    /**
     * Publish a JSON message to the specified topic.
     *
     * @param topic The Kafka topic to publish to
     * @param key The message key (optional, can be null)
     * @param message The message object to serialize as JSON
     * @return RecordMetadata containing partition and offset information
     * @throws JsonProcessingException if message serialization fails
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws java.util.concurrent.ExecutionException if the send operation fails
     */
    public RecordMetadata publish(String topic, String key, Object message) 
            throws JsonProcessingException, InterruptedException, java.util.concurrent.ExecutionException {
        String jsonMessage = objectMapper.writeValueAsString(message);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, jsonMessage);
        
        Future<RecordMetadata> future = producer.send(record);
        RecordMetadata metadata = future.get();
        
        logger.info("Published message to topic '{}' [partition={}, offset={}]", 
                    topic, metadata.partition(), metadata.offset());
        
        return metadata;
    }
    
    /**
     * Publish a raw JSON string to the specified topic.
     *
     * @param topic The Kafka topic to publish to
     * @param key The message key (optional, can be null)
     * @param jsonMessage The JSON message string
     * @return RecordMetadata containing partition and offset information
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws java.util.concurrent.ExecutionException if the send operation fails
     */
    public RecordMetadata publishRaw(String topic, String key, String jsonMessage) 
            throws InterruptedException, java.util.concurrent.ExecutionException {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, jsonMessage);
        
        Future<RecordMetadata> future = producer.send(record);
        RecordMetadata metadata = future.get();
        
        logger.info("Published raw message to topic '{}' [partition={}, offset={}]", 
                    topic, metadata.partition(), metadata.offset());
        
        return metadata;
    }
    
    /**
     * Flush any pending messages.
     */
    public void flush() {
        if (producer != null) {
            producer.flush();
            logger.debug("Flushed pending messages");
        }
    }
    
    @PreDestroy
    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("KafkaPublisher closed");
        }
    }
}
