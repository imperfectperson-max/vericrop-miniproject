package org.vericrop.service.orchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Orchestrator component.
 */
public class OrchestratorTest {
    
    private Orchestrator orchestrator;
    private TestEventPublisher eventPublisher;
    
    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        orchestrator = new Orchestrator(eventPublisher);
    }
    
    @AfterEach
    void tearDown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
    
    @Test
    void testStartOrchestration() throws InterruptedException {
        // Given
        String orchestrationId = "TEST_ORCH_001";
        List<String> controllers = Arrays.asList("scenarios", "delivery", "map");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("test", "value");
        
        // When
        Orchestrator.OrchestrationContext context = orchestrator.startOrchestration(
                orchestrationId, controllers, parameters, 10000);
        
        // Then
        assertNotNull(context);
        assertEquals(orchestrationId, context.getOrchestrationId());
        assertEquals(3, context.getControllers().size());
        assertFalse(context.isCompleted());
        
        // Wait for commands to be published asynchronously
        Thread.sleep(1000);
        assertEquals(3, eventPublisher.getCommandCount());
    }
    
    @Test
    void testControllerStatusUpdates() {
        // Given
        String orchestrationId = "TEST_ORCH_002";
        List<String> controllers = Arrays.asList("scenarios", "delivery");
        
        orchestrator.startOrchestration(orchestrationId, controllers, new HashMap<>(), 10000);
        
        // When - send status updates
        orchestrator.handleControllerStatus(orchestrationId, "scenarios", "inst-1", 
                "started", "Task started", null);
        orchestrator.handleControllerStatus(orchestrationId, "delivery", "inst-2", 
                "started", "Task started", null);
        
        // Then
        Orchestrator.OrchestrationContext context = orchestrator.getOrchestration(orchestrationId);
        assertNotNull(context);
        
        Map<String, Orchestrator.ControllerState> states = context.getControllerStates();
        assertEquals("started", states.get("scenarios").getStatus());
        assertEquals("started", states.get("delivery").getStatus());
        assertFalse(context.allControllersCompleted());
    }
    
    @Test
    void testAllControllersCompletedDetection() {
        // Given
        String orchestrationId = "TEST_ORCH_003";
        List<String> controllers = Arrays.asList("scenarios", "delivery");
        
        orchestrator.startOrchestration(orchestrationId, controllers, new HashMap<>(), 10000);
        
        // When - complete all controllers
        orchestrator.handleControllerStatus(orchestrationId, "scenarios", "inst-1", 
                "completed", "Scenarios completed", null);
        orchestrator.handleControllerStatus(orchestrationId, "delivery", "inst-2", 
                "completed", "Delivery completed", null);
        
        // Then - context should reflect completion
        Orchestrator.OrchestrationContext context = orchestrator.getOrchestration(orchestrationId);
        assertNotNull(context);
        assertTrue(context.allControllersCompleted());
        assertEquals(2, context.getCompletedCount());
        assertEquals(0, context.getFailedCount());
        
        // Completion event should be published (triggered by handleControllerStatus)
        // Wait a moment for the completion to be processed
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        assertTrue(eventPublisher.isCompletionEventPublished());
        assertTrue(eventPublisher.getLastCompletionSuccess());
    }
    
    @Test
    void testPartialCompletion() {
        // Given
        String orchestrationId = "TEST_ORCH_004";
        List<String> controllers = Arrays.asList("scenarios", "delivery", "map");
        
        orchestrator.startOrchestration(orchestrationId, controllers, new HashMap<>(), 10000);
        
        // When - only some controllers complete
        orchestrator.handleControllerStatus(orchestrationId, "scenarios", "inst-1", 
                "completed", "Scenarios completed", null);
        
        // Then - orchestration should not be marked as complete
        Orchestrator.OrchestrationContext context = orchestrator.getOrchestration(orchestrationId);
        assertNotNull(context);
        assertFalse(context.allControllersCompleted());
        assertEquals(1, context.getCompletedCount());
        assertFalse(eventPublisher.isCompletionEventPublished());
    }
    
    @Test
    void testOrchestrationWithFailure() {
        // Given
        String orchestrationId = "TEST_ORCH_005";
        List<String> controllers = Arrays.asList("scenarios", "delivery");
        
        orchestrator.startOrchestration(orchestrationId, controllers, new HashMap<>(), 10000);
        
        // When - one controller fails
        orchestrator.handleControllerStatus(orchestrationId, "scenarios", "inst-1", 
                "completed", "Scenarios completed", null);
        orchestrator.handleControllerStatus(orchestrationId, "delivery", "inst-2", 
                "failed", null, "Task failed due to error");
        
        // Then
        Orchestrator.OrchestrationContext context = orchestrator.getOrchestration(orchestrationId);
        assertNotNull(context);
        assertTrue(context.allControllersCompleted());
        assertEquals(1, context.getCompletedCount());
        assertEquals(1, context.getFailedCount());
        
        // Completion event should indicate failure
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        assertTrue(eventPublisher.isCompletionEventPublished());
        assertFalse(eventPublisher.getLastCompletionSuccess());
    }
    
    /**
     * Test event publisher implementation for testing.
     */
    private static class TestEventPublisher implements Orchestrator.OrchestrationEventPublisher {
        
        private final AtomicInteger commandCount = new AtomicInteger(0);
        private final AtomicBoolean completionEventPublished = new AtomicBoolean(false);
        private final AtomicBoolean lastCompletionSuccess = new AtomicBoolean(false);
        
        @Override
        public void publishControllerCommand(String orchestrationId, String controllerName, 
                                            String command, Map<String, Object> parameters) {
            commandCount.incrementAndGet();
        }
        
        @Override
        public void publishCompletedEvent(String orchestrationId, boolean success, 
                                          Map<String, String> results, String summary) {
            completionEventPublished.set(true);
            lastCompletionSuccess.set(success);
        }
        
        public int getCommandCount() {
            return commandCount.get();
        }
        
        public boolean isCompletionEventPublished() {
            return completionEventPublished.get();
        }
        
        public boolean getLastCompletionSuccess() {
            return lastCompletionSuccess.get();
        }
    }
}
