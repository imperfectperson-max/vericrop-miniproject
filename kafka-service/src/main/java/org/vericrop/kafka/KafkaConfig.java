package org.vericrop.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import java.util.Properties;

public class KafkaConfig {

    // Kafka Topics
    public static final String TOPIC_LOGISTICS_TRACKING = "logistics-tracking";
    public static final String TOPIC_QUALITY_ALERTS = "quality-alerts";
    public static final String TOPIC_BLOCKCHAIN_EVENTS = "blockchain-events";
    public static final String TOPIC_ENVIRONMENTAL_DATA = "environmental-data";
    public static final String TOPIC_CONSUMER_VERIFICATIONS = "consumer-verifications";
    
    // Extended simulation topics
    public static final String TOPIC_BATCH_UPDATES = "batch-updates";
    public static final String TOPIC_TEMPERATURE_COMPLIANCE = "temperature-compliance";
    public static final String TOPIC_MAP_SIMULATION = "map-simulation";

    public static Properties getProducerProperties() {
        Properties props = new Properties();
        
        // Use environment variable for bootstrap servers with fallback
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = System.getProperty("kafka.bootstrap.servers", "localhost:9092");
        }
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

        return props;
    }

    public static Properties getConsumerProperties(String groupId) {
        Properties props = new Properties();
        
        // Use environment variable for bootstrap servers with fallback
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = System.getProperty("kafka.bootstrap.servers", "localhost:9092");
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Start from earliest for development
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // Timeout settings for resilience
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);  // 10 seconds
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);  // 15 seconds

        return props;
    }

    public static Properties getByteArrayProducerProperties() {
        Properties props = getProducerProperties();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return props;
    }

    public static Properties getByteArrayConsumerProperties(String groupId) {
        Properties props = getConsumerProperties(groupId);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return props;
    }
}