package org.vericrop.kafka.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
import org.vericrop.dto.ShipmentRecord;
import org.vericrop.kafka.KafkaConfig;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Kafka Producer Service for publishing evaluation requests and results.
 * Supports both Kafka and in-memory mode for testing.
 */
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    
    public static final String TOPIC_EVALUATION_REQUEST = "evaluation-requests";
    public static final String TOPIC_EVALUATION_RESULT = "evaluation-results";
    public static final String TOPIC_SHIPMENT_RECORD = "shipment-records";
    public static final String TOPIC_FRUIT_QUALITY = KafkaConfig.TOPIC_FRUIT_QUALITY;
    public static final String TOPIC_SUPPLYCHAIN_EVENTS = KafkaConfig.TOPIC_SUPPLYCHAIN_EVENTS;
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final boolean kafkaEnabled;
    
    /**
     * Create producer with default Kafka configuration.
     */
    public KafkaProducerService() {
        this(true);
    }
    
    /**
     * Create producer with optional Kafka support.
     * 
     * @param kafkaEnabled If false, uses in-memory message dispatcher
     */
    public KafkaProducerService(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
        this.objectMapper = new ObjectMapper();
        
        if (kafkaEnabled) {
            Properties props = KafkaConfig.getProducerProperties();
            this.producer = new KafkaProducer<>(props);
            logger.info("Kafka producer initialized with broker: {}", 
                props.getProperty("bootstrap.servers"));
        } else {
            this.producer = null;
            logger.info("Kafka producer running in in-memory mode (Kafka disabled)");
        }
    }
    
    /**
     * Send an evaluation request to Kafka.
     * 
     * @param request The evaluation request to send
     * @return true if message was sent successfully
     */
    public boolean sendEvaluationRequest(EvaluationRequest request) {
        if (request == null) {
            logger.error("Cannot send null evaluation request");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(request);
            
            if (kafkaEnabled && producer != null) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_EVALUATION_REQUEST,
                    request.getBatchId(),
                    json
                );
                
                Future<RecordMetadata> future = producer.send(record);
                RecordMetadata metadata = future.get();
                
                logger.info("Sent evaluation request for batch {} to topic {} partition {} offset {}",
                    request.getBatchId(), metadata.topic(), metadata.partition(), metadata.offset());
                    
                return true;
            } else {
                // In-memory mode - just log
                logger.info("In-memory mode: Would send evaluation request for batch {}", 
                    request.getBatchId());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send evaluation request for batch {}: {}", 
                request.getBatchId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Send an evaluation result to Kafka.
     * 
     * @param result The evaluation result to send
     * @return true if message was sent successfully
     */
    public boolean sendEvaluationResult(EvaluationResult result) {
        if (result == null) {
            logger.error("Cannot send null evaluation result");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(result);
            
            if (kafkaEnabled && producer != null) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_EVALUATION_RESULT,
                    result.getBatchId(),
                    json
                );
                
                Future<RecordMetadata> future = producer.send(record);
                RecordMetadata metadata = future.get();
                
                logger.info("Sent evaluation result for batch {} to topic {} partition {} offset {}",
                    result.getBatchId(), metadata.topic(), metadata.partition(), metadata.offset());
                    
                return true;
            } else {
                // In-memory mode - just log
                logger.info("In-memory mode: Would send evaluation result for batch {} (score: {})", 
                    result.getBatchId(), result.getQualityScore());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send evaluation result for batch {}: {}", 
                result.getBatchId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Send a shipment record to Kafka.
     * 
     * @param record The shipment record to send
     * @return true if message was sent successfully
     */
    public boolean sendShipmentRecord(ShipmentRecord record) {
        if (record == null) {
            logger.error("Cannot send null shipment record");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(record);
            
            if (kafkaEnabled && producer != null) {
                ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(
                    TOPIC_SHIPMENT_RECORD,
                    record.getShipmentId(),
                    json
                );
                
                Future<RecordMetadata> future = producer.send(kafkaRecord);
                RecordMetadata metadata = future.get();
                
                logger.info("Sent shipment record {} to topic {} partition {} offset {}",
                    record.getShipmentId(), metadata.topic(), metadata.partition(), metadata.offset());
                    
                return true;
            } else {
                // In-memory mode - just log
                logger.info("In-memory mode: Would send shipment record {} for batch {}", 
                    record.getShipmentId(), record.getBatchId());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send shipment record {}: {}", 
                record.getShipmentId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Flush any pending messages.
     */
    public void flush() {
        if (kafkaEnabled && producer != null) {
            producer.flush();
            logger.debug("Flushed pending messages");
        }
    }
    
    /**
     * Close the producer and release resources.
     */
    public void close() {
        if (kafkaEnabled && producer != null) {
            producer.close();
            logger.info("Kafka producer closed");
        }
    }
    
    /**
     * Send a fruit quality event to Kafka.
     * This is triggered after quality assessment is complete.
     * 
     * @param result The evaluation result containing quality metrics
     * @return true if message was sent successfully
     */
    public boolean sendFruitQualityEvent(EvaluationResult result) {
        if (result == null) {
            logger.error("Cannot send null fruit quality event");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(result);
            
            if (kafkaEnabled && producer != null) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_FRUIT_QUALITY,
                    result.getBatchId(),
                    json
                );
                
                Future<RecordMetadata> future = producer.send(record);
                RecordMetadata metadata = future.get();
                
                logger.info("Sent fruit quality event for batch {} to topic {} partition {} offset {}",
                    result.getBatchId(), metadata.topic(), metadata.partition(), metadata.offset());
                    
                return true;
            } else {
                // In-memory mode - just log
                logger.info("In-memory mode: Would send fruit quality event for batch {} (score: {})", 
                    result.getBatchId(), result.getQualityScore());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send fruit quality event for batch {}: {}", 
                result.getBatchId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Send a supply chain event to Kafka.
     * Used for tracking shipment status changes, transfers, deliveries, etc.
     * 
     * @param eventType The type of event (e.g., "TRANSFER", "DELIVERY", "QUALITY_CHECK")
     * @param eventData The event data as a map
     * @return true if message was sent successfully
     */
    public boolean sendSupplyChainEvent(String eventType, Object eventData) {
        if (eventType == null || eventData == null) {
            logger.error("Cannot send null supply chain event");
            return false;
        }
        
        try {
            String json = objectMapper.writeValueAsString(eventData);
            
            if (kafkaEnabled && producer != null) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_SUPPLYCHAIN_EVENTS,
                    eventType,
                    json
                );
                
                Future<RecordMetadata> future = producer.send(record);
                RecordMetadata metadata = future.get();
                
                logger.info("Sent supply chain event {} to topic {} partition {} offset {}",
                    eventType, metadata.topic(), metadata.partition(), metadata.offset());
                    
                return true;
            } else {
                // In-memory mode - just log
                logger.info("In-memory mode: Would send supply chain event {}", eventType);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to send supply chain event {}: {}", eventType, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if Kafka is enabled.
     */
    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }
}
