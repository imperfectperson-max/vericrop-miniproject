package org.vericrop.service.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrchestrationService
 */
class OrchestrationServiceTest {
    
    private OrchestrationService orchestrationService;
    
    @BeforeEach
    void setUp() {
        orchestrationService = new OrchestrationService();
    }
    
    @Test
    void testStartOrchestration() {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-001", "FARMER-001");
        request.setProductType("Apples");
        
        // When
        String correlationId = orchestrationService.startOrchestration(request);
        
        // Then
        assertNotNull(correlationId);
        assertTrue(correlationId.startsWith("ORCH-"));
        
        OrchestrationStatus status = orchestrationService.getStatus(correlationId);
        assertNotNull(status);
        assertEquals(correlationId, status.getCorrelationId());
        assertEquals("BATCH-001", status.getBatchId());
        assertEquals("FARMER-001", status.getFarmerId());
        assertEquals(OrchestrationStatus.Status.IN_FLIGHT, status.getStatus());
        assertEquals(6, status.getSteps().size()); // Six functional areas
    }
    
    @Test
    void testMarkStepComplete() {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-002", "FARMER-002");
        String correlationId = orchestrationService.startOrchestration(request);
        
        // When
        StepResult result = new StepResult(true, "Scenarios processed successfully");
        result.putData("scenario_count", 3);
        orchestrationService.markStepComplete(correlationId, StepType.SCENARIOS, result);
        
        // Then
        OrchestrationStatus status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.PARTIAL, status.getStatus());
        
        OrchestrationStatus.StepStatus scenariosStep = status.getSteps().get(StepType.SCENARIOS);
        assertTrue(scenariosStep.isComplete());
        assertTrue(scenariosStep.getResult().isSuccess());
        assertEquals("Scenarios processed successfully", scenariosStep.getResult().getMessage());
    }
    
    @Test
    void testAllStepsComplete() {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-003", "FARMER-003");
        String correlationId = orchestrationService.startOrchestration(request);
        
        // When - Mark all steps complete
        for (StepType stepType : StepType.values()) {
            StepResult result = new StepResult(true, stepType.name() + " completed");
            orchestrationService.markStepComplete(correlationId, stepType, result);
        }
        
        // Then
        OrchestrationStatus status = orchestrationService.getStatus(correlationId);
        assertEquals(OrchestrationStatus.Status.COMPLETE, status.getStatus());
        
        // All steps should be complete
        for (StepType stepType : StepType.values()) {
            assertTrue(status.getSteps().get(stepType).isComplete());
        }
    }
    
    @Test
    void testGetStatusForUnknownOrchestration() {
        // When
        OrchestrationStatus status = orchestrationService.getStatus("UNKNOWN-ID");
        
        // Then
        assertNull(status);
    }
    
    @Test
    void testGetAllOrchestrations() {
        // Given
        CompositeRequest request1 = new CompositeRequest("BATCH-004", "FARMER-004");
        CompositeRequest request2 = new CompositeRequest("BATCH-005", "FARMER-005");
        
        orchestrationService.startOrchestration(request1);
        orchestrationService.startOrchestration(request2);
        
        // When
        var allOrchestrations = orchestrationService.getAllOrchestrations();
        
        // Then
        assertEquals(2, allOrchestrations.size());
    }
    
    @Test
    void testCleanupOldOrchestrations() throws InterruptedException {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-006", "FARMER-006");
        String correlationId = orchestrationService.startOrchestration(request);
        
        // Complete all steps
        for (StepType stepType : StepType.values()) {
            StepResult result = new StepResult(true, stepType.name() + " completed");
            orchestrationService.markStepComplete(correlationId, stepType, result);
        }
        
        // Wait a bit
        Thread.sleep(100);
        
        // When - Clean up orchestrations older than 50ms
        int cleaned = orchestrationService.cleanupOldOrchestrations(50);
        
        // Then
        assertEquals(1, cleaned);
        assertNull(orchestrationService.getStatus(correlationId));
    }
    
    @Test
    void testCorrelationIdUniqueness() {
        // Given
        CompositeRequest request = new CompositeRequest("BATCH-007", "FARMER-007");
        
        // When - Start multiple orchestrations
        String id1 = orchestrationService.startOrchestration(request);
        String id2 = orchestrationService.startOrchestration(request);
        String id3 = orchestrationService.startOrchestration(request);
        
        // Then - All IDs should be unique
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
    }
}
