package org.vericrop.service.orchestration.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vericrop.service.orchestration.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrchestrationService with real Kafka and Airflow.
 * 
 * These tests are disabled by default and require infrastructure to be running:
 * - Kafka broker (localhost:9092 or KAFKA_BOOTSTRAP_SERVERS)
 * - Airflow webserver (localhost:8080 or AIRFLOW_BASE_URL)
 * 
 * To run these tests:
 * 1. Start Kafka using docker-compose:
 *    docker-compose -f docker-compose-kafka.yml up -d
 * 
 * 2. Start Airflow (optional, gracefully degrades if not available):
 *    cd airflow && docker-compose up -d
 * 
 * 3. Run tests:
 *    ./gradlew :vericrop-core:test --tests "*OrchestrationIntegrationTest"
 * 
 * Alternatively, use Testcontainers to automatically start Kafka:
 * Add testcontainers dependency to build.gradle and uncomment @Testcontainers annotation
 */
@Disabled("Integration test - requires Kafka and Airflow infrastructure")
public class OrchestrationIntegrationTest {
    
    /**
     * Tests orchestration with real Kafka and Airflow infrastructure.
     */
    @Test
    void testOrchestrationWithRealInfrastructure() {
        // Setup - Create real Kafka publisher and Airflow trigger
        KafkaOrchestrationPublisher kafkaPublisher = createRealKafkaPublisher();
        AirflowHttpDagTrigger airflowTrigger = createRealAirflowTrigger();
        
        OrchestrationService orchestrationService = new OrchestrationService(
                kafkaPublisher,
                airflowTrigger
        );
        
        try {
            // Create request with all scenario groups
            Map<String, Object> context = new HashMap<>();
            context.put("testId", "integration-test-" + System.currentTimeMillis());
            context.put("batchId", "BATCH-INT-001");
            
            ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                    "int-test-req-001",
                    "integration-test-user",
                    EnumSet.allOf(ScenarioGroup.class),
                    context,
                    60000  // 60 second timeout for integration test
            );
            
            // Execute scenarios
            ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
            
            // Assertions
            assertNotNull(result);
            assertNotNull(result.getRequestId());
            assertEquals(6, result.getScenarioStatuses().size());
            
            // Log results
            System.out.println("Integration Test Results:");
            System.out.println("Overall Success: " + result.isOverallSuccess());
            System.out.println("Message: " + result.getMessage());
            System.out.println("Duration: " + result.getDurationMs() + "ms");
            System.out.println("\nPer-Scenario Status:");
            
            result.getScenarioStatuses().forEach((group, status) -> {
                System.out.println(String.format("  %s: %s - %s",
                        group.name(),
                        status.getStatus(),
                        status.getMessage()));
                
                if (status.getErrorDetails() != null) {
                    System.out.println("    Error: " + status.getErrorDetails());
                }
            });
            
            // Verify at least Kafka events were published (even if Airflow fails)
            // In a real integration test, you would consume from Kafka topics to verify
            
        } finally {
            // Cleanup
            orchestrationService.shutdown();
            kafkaPublisher.close();
            airflowTrigger.close();
        }
    }
    
    /**
     * Tests orchestration with only Kafka (Airflow optional).
     */
    @Test
    void testOrchestrationWithKafkaOnly() {
        // This test demonstrates that orchestration works even without Airflow
        KafkaOrchestrationPublisher kafkaPublisher = createRealKafkaPublisher();
        
        OrchestrationService orchestrationService = new OrchestrationService(
                kafkaPublisher,
                null  // No Airflow trigger
        );
        
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("testMode", "kafka-only");
            
            ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                    "kafka-only-req-001",
                    "test-user",
                    EnumSet.of(ScenarioGroup.SCENARIOS, ScenarioGroup.DELIVERY),
                    context,
                    30000
            );
            
            ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
            
            // Should succeed even without Airflow
            assertNotNull(result);
            assertEquals(2, result.getScenarioStatuses().size());
            
            System.out.println("Kafka-Only Test Results:");
            System.out.println("Success: " + result.isOverallSuccess());
            System.out.println("Duration: " + result.getDurationMs() + "ms");
            
        } finally {
            orchestrationService.shutdown();
            kafkaPublisher.close();
        }
    }
    
    /**
     * Tests partial failure scenarios with real infrastructure.
     */
    @Test
    void testPartialFailureWithRealInfrastructure() {
        // Use invalid Kafka configuration to simulate failure
        KafkaOrchestrationPublisher kafkaPublisher = createInvalidKafkaPublisher();
        AirflowHttpDagTrigger airflowTrigger = createRealAirflowTrigger();
        
        OrchestrationService orchestrationService = new OrchestrationService(
                kafkaPublisher,
                airflowTrigger
        );
        
        try {
            ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                    "partial-failure-req-001",
                    "test-user",
                    EnumSet.of(ScenarioGroup.TEMPERATURE),
                    new HashMap<>(),
                    30000
            );
            
            ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
            
            // Should handle Kafka failures gracefully
            assertNotNull(result);
            
            System.out.println("Partial Failure Test Results:");
            System.out.println("Success: " + result.isOverallSuccess());
            System.out.println("Status: " + result.getScenarioStatuses().get(ScenarioGroup.TEMPERATURE).getStatus());
            
        } finally {
            orchestrationService.shutdown();
            kafkaPublisher.close();
            airflowTrigger.close();
        }
    }
    
    // Helper methods
    
    private KafkaOrchestrationPublisher createRealKafkaPublisher() {
        Properties props = new Properties();
        
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = "localhost:9092";
        }
        
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("request.timeout.ms", 10000);
        props.put("delivery.timeout.ms", 30000);
        
        return new KafkaOrchestrationPublisher(props);
    }
    
    private KafkaOrchestrationPublisher createInvalidKafkaPublisher() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "invalid-host:9999");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("max.block.ms", 5000);  // Fail fast
        
        return new KafkaOrchestrationPublisher(props);
    }
    
    private AirflowHttpDagTrigger createRealAirflowTrigger() {
        // Uses environment variables or defaults
        return new AirflowHttpDagTrigger();
    }
}

/*
 * TESTCONTAINERS EXAMPLE (Optional)
 * 
 * To use Testcontainers for automatic Kafka setup, add to build.gradle:
 * 
 * testImplementation 'org.testcontainers:testcontainers:1.19.0'
 * testImplementation 'org.testcontainers:kafka:1.19.0'
 * testImplementation 'org.testcontainers:junit-jupiter:1.19.0'
 * 
 * Then modify the test class:
 * 
 * @Testcontainers
 * public class OrchestrationIntegrationTest {
 *     
 *     @Container
 *     static KafkaContainer kafka = new KafkaContainer(
 *         DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
 *     );
 *     
 *     @BeforeAll
 *     static void setup() {
 *         System.setProperty("KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers());
 *     }
 *     
 *     // ... rest of tests
 * }
 */
