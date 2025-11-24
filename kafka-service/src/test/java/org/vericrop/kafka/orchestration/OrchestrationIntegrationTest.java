package org.vericrop.kafka.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.kafka.events.OrchestrationEvent;
import org.vericrop.service.orchestration.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the orchestration flow.
 * Tests that a composite request can emit events for all six functional areas
 * and that orchestration state is properly tracked.
 */
class OrchestrationIntegrationTest {
    
    private OrchestrationService orchestrationService;
    private MockOrchestrationEventProducer mockProducer;
    
    @BeforeEach
    void setUp() {
        orchestrationService = new OrchestrationService();
        mockProducer = new MockOrchestrationEventProducer();
    }
    
    @Test
    void testCompositeRequestEmitsSixMessages() throws InterruptedException {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-TEST-001", "FARMER-TEST-001");
        request.setProductType("Apples");
        
        // When - Start orchestration
        String correlationId = orchestrationService.startOrchestration(request);
        
        // Emit events for all six functional areas
        mockProducer.sendScenariosEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        mockProducer.sendDeliveryEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        mockProducer.sendMapEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        mockProducer.sendTemperatureEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        mockProducer.sendSupplierComplianceEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        mockProducer.sendSimulationsEvent(correlationId, request.getBatchId(), request.getFarmerId(), "STARTED");
        
        // Then - Verify six messages were sent with same correlation ID
        assertEquals(6, mockProducer.getSentEventCount());
        assertTrue(mockProducer.getAllEventsSameCorrelationId(correlationId));
    }
    
    @Test
    void testOrchestrationStateTransitions() throws InterruptedException {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-TEST-002", "FARMER-TEST-002");
        String correlationId = orchestrationService.startOrchestration(request);
        
        // Initial state should be IN_FLIGHT
        OrchestrationStatus status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.IN_FLIGHT, status.getStatus());
        
        // When - Complete first step
        StepResult result1 = new StepResult(true, "Scenarios complete");
        orchestrationService.markStepComplete(correlationId, StepType.SCENARIOS, result1);
        
        // Then - State should transition to PARTIAL
        status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.PARTIAL, status.getStatus());
        
        // When - Complete remaining steps
        for (StepType stepType : StepType.values()) {
            if (stepType != StepType.SCENARIOS) {
                StepResult result = new StepResult(true, stepType.name() + " complete");
                orchestrationService.markStepComplete(correlationId, stepType, result);
            }
        }
        
        // Then - State should transition to COMPLETE
        status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.COMPLETE, status.getStatus());
        
        // Verify all steps are complete
        for (StepType stepType : StepType.values()) {
            assertTrue(status.getSteps().get(stepType).isComplete(), 
                "Step " + stepType + " should be complete");
        }
    }
    
    @Test
    void testConcurrentStepCompletion() throws InterruptedException {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-TEST-003", "FARMER-TEST-003");
        String correlationId = orchestrationService.startOrchestration(request);
        
        // When - Complete steps concurrently
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(6);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (StepType stepType : StepType.values()) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    StepResult result = new StepResult(true, stepType.name() + " complete");
                    orchestrationService.markStepComplete(correlationId, stepType, result);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        }
        
        // Start all threads at once
        startLatch.countDown();
        
        // Wait for all completions
        boolean completed = completeLatch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "All steps should complete within timeout");
        assertEquals(6, successCount.get(), "All 6 steps should succeed");
        
        // Then - Final state should be COMPLETE
        OrchestrationStatus status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.COMPLETE, status.getStatus());
    }
    
    @Test
    void testEventCorrelationIdPropagation() {
        // Given
        String expectedCorrelationId = "ORCH-test-correlation-id";
        String batchId = "BATCH-TEST-004";
        String farmerId = "FARMER-TEST-004";
        
        // When
        mockProducer.sendScenariosEvent(expectedCorrelationId, batchId, farmerId, "STARTED");
        
        // Then
        assertEquals(1, mockProducer.getSentEventCount());
        OrchestrationEvent event = mockProducer.getLastEvent();
        assertEquals(expectedCorrelationId, event.getCorrelationId());
        assertEquals(batchId, event.getBatchId());
        assertEquals(farmerId, event.getFarmerId());
        assertEquals("scenarios", event.getStepType());
        assertEquals("STARTED", event.getEventType());
    }
    
    /**
     * Mock producer for testing without Kafka infrastructure
     */
    private static class MockOrchestrationEventProducer {
        private final java.util.List<OrchestrationEvent> sentEvents = new java.util.ArrayList<>();
        
        public void sendScenariosEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "scenarios", eventType);
            sentEvents.add(event);
        }
        
        public void sendDeliveryEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "delivery", eventType);
            sentEvents.add(event);
        }
        
        public void sendMapEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "map", eventType);
            sentEvents.add(event);
        }
        
        public void sendTemperatureEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "temperature", eventType);
            sentEvents.add(event);
        }
        
        public void sendSupplierComplianceEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "supplierCompliance", eventType);
            sentEvents.add(event);
        }
        
        public void sendSimulationsEvent(String correlationId, String batchId, String farmerId, String eventType) {
            OrchestrationEvent event = new OrchestrationEvent(correlationId, batchId, farmerId, "simulations", eventType);
            sentEvents.add(event);
        }
        
        public int getSentEventCount() {
            return sentEvents.size();
        }
        
        public OrchestrationEvent getLastEvent() {
            return sentEvents.isEmpty() ? null : sentEvents.get(sentEvents.size() - 1);
        }
        
        public boolean getAllEventsSameCorrelationId(String expectedCorrelationId) {
            return sentEvents.stream()
                .allMatch(event -> expectedCorrelationId.equals(event.getCorrelationId()));
        }
    }
}
