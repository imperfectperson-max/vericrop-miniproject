package org.vericrop.gui.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vericrop.service.orchestration.AirflowHttpDagTrigger;
import org.vericrop.service.orchestration.KafkaOrchestrationPublisher;
import org.vericrop.service.orchestration.OrchestrationService;

import java.util.Properties;

/**
 * Spring configuration for OrchestrationService and related components.
 */
@Configuration
public class OrchestrationConfig {
    
    /**
     * Creates the OrchestrationService bean with Kafka and Airflow integration.
     */
    @Bean
    public OrchestrationService orchestrationService() {
        // Create Kafka publisher
        KafkaOrchestrationPublisher kafkaPublisher = createKafkaPublisher();
        
        // Create Airflow DAG trigger
        AirflowHttpDagTrigger airflowTrigger = createAirflowTrigger();
        
        // Create and return orchestration service
        return new OrchestrationService(kafkaPublisher, airflowTrigger);
    }
    
    /**
     * Creates a Kafka publisher with appropriate configuration.
     */
    private KafkaOrchestrationPublisher createKafkaPublisher() {
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
        
        // Timeout settings for resilience
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        
        return new KafkaOrchestrationPublisher(props);
    }
    
    /**
     * Creates an Airflow DAG trigger with configuration from environment.
     */
    private AirflowHttpDagTrigger createAirflowTrigger() {
        // AirflowHttpDagTrigger reads configuration from environment variables
        return new AirflowHttpDagTrigger();
    }
}
