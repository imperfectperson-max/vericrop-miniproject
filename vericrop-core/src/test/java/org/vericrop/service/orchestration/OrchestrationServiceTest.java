package org.vericrop.service.orchestration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ExecutionStatus;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ScenarioExecutionStatus;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrchestrationService.
 * 
 * These tests use mocked Kafka and Airflow components to verify
 * concurrent scenario execution behavior.
 */
class OrchestrationServiceTest {
    
    private OrchestrationService orchestrationService;
    private MockKafkaPublisher mockKafkaPublisher;
    private MockAirflowTrigger mockAirflowTrigger;
    
    @BeforeEach
    void setUp() {
        mockKafkaPublisher = new MockKafkaPublisher();
        mockAirflowTrigger = new MockAirflowTrigger();
        orchestrationService = new OrchestrationService(mockKafkaPublisher, mockAirflowTrigger);
    }
    
    @AfterEach
    void tearDown() {
        if (orchestrationService != null) {
            orchestrationService.shutdown();
        }
    }
    
    @Test
    void testRunConcurrentScenarios_AllSuccess() {
        // Arrange
        Set<ScenarioGroup> scenarioGroups = EnumSet.allOf(ScenarioGroup.class);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-001",
                "user-123",
                scenarioGroups,
                new HashMap<>(),
                30000 // 30 second timeout
        );
        
        // Act
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        
        // Assert
        assertNotNull(result);
        assertEquals("req-001", result.getRequestId());
        assertTrue(result.isOverallSuccess(), "All scenarios should succeed");
        assertEquals(6, result.getScenarioStatuses().size());
        
        // Verify all scenarios completed successfully
        for (ScenarioExecutionStatus status : result.getScenarioStatuses().values()) {
            assertEquals(ExecutionStatus.SUCCESS, status.getStatus());
            assertNotNull(status.getTimestamp());
        }
        
        // Verify Kafka events were published
        assertEquals(6, mockKafkaPublisher.getPublishedEvents().size());
        
        // Verify Airflow DAGs were triggered
        assertEquals(6, mockAirflowTrigger.getTriggeredDags().size());
        
        // Verify duration is reasonable
        assertTrue(result.getDurationMs() < 30000, "Execution should complete within timeout");
    }
    
    @Test
    void testRunConcurrentScenarios_SingleScenario() {
        // Arrange
        Set<ScenarioGroup> scenarioGroups = EnumSet.of(ScenarioGroup.SCENARIOS);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-002",
                "user-123",
                scenarioGroups,
                new HashMap<>(),
                10000
        );
        
        // Act
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.isOverallSuccess());
        assertEquals(1, result.getScenarioStatuses().size());
        assertTrue(result.getScenarioStatuses().containsKey(ScenarioGroup.SCENARIOS));
    }
    
    @Test
    void testRunConcurrentScenarios_KafkaFailure() {
        // Arrange
        mockKafkaPublisher.setFailureMode(true);
        
        Set<ScenarioGroup> scenarioGroups = EnumSet.of(ScenarioGroup.DELIVERY);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-003",
                "user-123",
                scenarioGroups,
                new HashMap<>(),
                10000
        );
        
        // Act
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        
        // Assert
        assertNotNull(result);
        // Partial success because Airflow still works
        assertFalse(result.isOverallSuccess());
        
        ScenarioExecutionStatus status = result.getScenarioStatuses().get(ScenarioGroup.DELIVERY);
        assertEquals(ExecutionStatus.PARTIAL, status.getStatus());
    }
    
    @Test
    void testRunConcurrentScenarios_AirflowFailure() {
        // Arrange
        mockAirflowTrigger.setFailureMode(true);
        
        Set<ScenarioGroup> scenarioGroups = EnumSet.of(ScenarioGroup.TEMPERATURE);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-004",
                "user-123",
                scenarioGroups,
                new HashMap<>(),
                10000
        );
        
        // Act
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        
        // Assert
        assertNotNull(result);
        assertFalse(result.isOverallSuccess());
        
        ScenarioExecutionStatus status = result.getScenarioStatuses().get(ScenarioGroup.TEMPERATURE);
        assertEquals(ExecutionStatus.PARTIAL, status.getStatus());
    }
    
    @Test
    void testRunConcurrentScenarios_NullRequest() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.runConcurrentScenarios(null);
        });
    }
    
    @Test
    void testRunConcurrentScenarios_EmptyScenarioGroups() {
        // Arrange
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-005",
                "user-123",
                Collections.emptySet(),
                new HashMap<>(),
                10000
        );
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.runConcurrentScenarios(request);
        });
    }
    
    @Test
    void testRunConcurrentScenarios_WithContext() {
        // Arrange
        Map<String, Object> context = new HashMap<>();
        context.put("batchId", "BATCH-001");
        context.put("farmerId", "FARM-123");
        context.put("origin", "Farm Location");
        
        Set<ScenarioGroup> scenarioGroups = EnumSet.of(ScenarioGroup.MAP, ScenarioGroup.DELIVERY);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-006",
                "user-123",
                scenarioGroups,
                context,
                10000
        );
        
        // Act
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        
        // Assert
        assertTrue(result.isOverallSuccess());
        
        // Verify context was passed to Kafka events
        List<Map<String, Object>> events = mockKafkaPublisher.getPublishedEvents();
        assertEquals(2, events.size());
        
        for (Map<String, Object> event : events) {
            assertTrue(event.containsKey("context"));
            @SuppressWarnings("unchecked")
            Map<String, Object> eventContext = (Map<String, Object>) event.get("context");
            assertEquals("BATCH-001", eventContext.get("batchId"));
        }
    }
    
    @Test
    void testConcurrentExecution() throws InterruptedException {
        // Arrange - add delay to verify parallel execution
        mockKafkaPublisher.setDelay(100); // 100ms delay per publish
        mockAirflowTrigger.setDelay(100); // 100ms delay per trigger
        
        Set<ScenarioGroup> scenarioGroups = EnumSet.allOf(ScenarioGroup.class);
        ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
                "req-007",
                "user-123",
                scenarioGroups,
                new HashMap<>(),
                30000
        );
        
        // Act
        long startTime = System.currentTimeMillis();
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);
        long duration = System.currentTimeMillis() - startTime;
        
        // Assert
        assertTrue(result.isOverallSuccess());
        
        // If executed sequentially, would take 6 * 200ms = 1200ms
        // With parallelism, should be much faster (< 500ms with overhead)
        assertTrue(duration < 1000, "Concurrent execution should be faster than sequential: " + duration + "ms");
    }
    
    // ===== Mock Implementations =====
    
    /**
     * Mock Kafka publisher for testing.
     */
    static class MockKafkaPublisher implements OrchestrationService.KafkaEventPublisher {
        private final List<Map<String, Object>> publishedEvents = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger publishCount = new AtomicInteger(0);
        private boolean failureMode = false;
        private long delay = 0;
        
        @Override
        public void publish(String topic, Map<String, Object> eventData) throws Exception {
            if (delay > 0) {
                Thread.sleep(delay);
            }
            
            if (failureMode) {
                throw new RuntimeException("Simulated Kafka failure");
            }
            
            publishedEvents.add(new HashMap<>(eventData));
            publishCount.incrementAndGet();
        }
        
        public List<Map<String, Object>> getPublishedEvents() {
            return new ArrayList<>(publishedEvents);
        }
        
        public int getPublishCount() {
            return publishCount.get();
        }
        
        public void setFailureMode(boolean failureMode) {
            this.failureMode = failureMode;
        }
        
        public void setDelay(long delayMs) {
            this.delay = delayMs;
        }
    }
    
    /**
     * Mock Airflow DAG trigger for testing.
     */
    static class MockAirflowTrigger implements OrchestrationService.AirflowDagTrigger {
        private final List<String> triggeredDags = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger triggerCount = new AtomicInteger(0);
        private boolean failureMode = false;
        private long delay = 0;
        
        @Override
        public boolean triggerDag(String dagName, Map<String, Object> dagConf) throws Exception {
            if (delay > 0) {
                Thread.sleep(delay);
            }
            
            if (failureMode) {
                return false;
            }
            
            triggeredDags.add(dagName);
            triggerCount.incrementAndGet();
            return true;
        }
        
        public List<String> getTriggeredDags() {
            return new ArrayList<>(triggeredDags);
        }
        
        public int getTriggerCount() {
            return triggerCount.get();
        }
        
        public void setFailureMode(boolean failureMode) {
            this.failureMode = failureMode;
        }
        
        public void setDelay(long delayMs) {
            this.delay = delayMs;
        }
    }
}
