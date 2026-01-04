package org.vericrop.kafka.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
import org.vericrop.kafka.KafkaConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Kafka Consumer Service for consuming evaluation requests and results.
 * Supports message handlers for processing consumed messages.
 */
public class KafkaConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running;
    private final String groupId;
    
    private Consumer<EvaluationRequest> evaluationRequestHandler;
    private Consumer<EvaluationResult> evaluationResultHandler;
    
    /**
     * Create consumer for evaluation requests.
     * 
     * @param groupId Consumer group ID
     * @param topics Topics to subscribe to
     */
    public KafkaConsumerService(String groupId, String... topics) {
        this.groupId = groupId;
        this.objectMapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        
        Properties props = KafkaConfig.getConsumerProperties(groupId);
        this.consumer = new KafkaConsumer<>(props);
        
        if (topics != null && topics.length > 0) {
            consumer.subscribe(Arrays.asList(topics));
            logger.info("Kafka consumer initialized for group {} subscribed to topics: {}", 
                groupId, Arrays.toString(topics));
        } else {
            logger.warn("Kafka consumer created but no topics subscribed");
        }
    }
    
    /**
     * Set handler for evaluation request messages.
     */
    public void setEvaluationRequestHandler(Consumer<EvaluationRequest> handler) {
        this.evaluationRequestHandler = handler;
    }
    
    /**
     * Set handler for evaluation result messages.
     */
    public void setEvaluationResultHandler(Consumer<EvaluationResult> handler) {
        this.evaluationResultHandler = handler;
    }
    
    /**
     * Start consuming messages from Kafka.
     * This method blocks until stop() is called.
     */
    public void startConsuming() {
        if (running.get()) {
            logger.warn("Consumer already running");
            return;
        }
        
        running.set(true);
        logger.info("Starting Kafka consumer for group {}", groupId);
        
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processMessage(record);
                    } catch (Exception e) {
                        logger.error("Error processing message from topic {} partition {} offset {}: {}",
                            record.topic(), record.partition(), record.offset(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in consumer loop: {}", e.getMessage());
        } finally {
            logger.info("Kafka consumer for group {} stopped", groupId);
        }
    }
    
    /**
     * Process a consumed message based on its topic.
     */
    private void processMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String value = record.value();
        
        logger.debug("Processing message from topic {} partition {} offset {}",
            topic, record.partition(), record.offset());
        
        try {
            if (KafkaProducerService.TOPIC_EVALUATION_REQUEST.equals(topic)) {
                EvaluationRequest request = objectMapper.readValue(value, EvaluationRequest.class);
                if (evaluationRequestHandler != null) {
                    evaluationRequestHandler.accept(request);
                    logger.info("Processed evaluation request for batch {}", request.getBatchId());
                } else {
                    logger.warn("No handler registered for evaluation requests");
                }
                
            } else if (KafkaProducerService.TOPIC_EVALUATION_RESULT.equals(topic)) {
                EvaluationResult result = objectMapper.readValue(value, EvaluationResult.class);
                if (evaluationResultHandler != null) {
                    evaluationResultHandler.accept(result);
                    logger.info("Processed evaluation result for batch {} (score: {})", 
                        result.getBatchId(), result.getQualityScore());
                } else {
                    logger.warn("No handler registered for evaluation results");
                }
                
            } else {
                logger.warn("Unknown topic: {}", topic);
            }
            
        } catch (Exception e) {
            logger.error("Failed to deserialize message from topic {}: {}", topic, e.getMessage());
        }
    }
    
    /**
     * Stop consuming messages.
     */
    public void stop() {
        logger.info("Stopping Kafka consumer for group {}", groupId);
        running.set(false);
    }
    
    /**
     * Close the consumer and release resources.
     */
    public void close() {
        stop();
        consumer.close();
        logger.info("Kafka consumer for group {} closed", groupId);
    }
    
    /**
     * Check if consumer is running.
     */
    public boolean isRunning() {
        return running.get();
    }
}
