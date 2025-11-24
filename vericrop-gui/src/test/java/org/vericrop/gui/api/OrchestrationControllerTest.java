package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.vericrop.service.orchestration.*;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ExecutionStatus;
import org.vericrop.service.orchestration.ScenarioOrchestrationResult.ScenarioExecutionStatus;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrchestrationController.
 */
class OrchestrationControllerTest {
    
    private OrchestrationController controller;
    private OrchestrationService mockOrchestrationService;
    
    @BeforeEach
    void setUp() {
        mockOrchestrationService = mock(OrchestrationService.class);
        controller = new OrchestrationController(mockOrchestrationService);
    }
    
    @Test
    void testExecuteScenarios_Success() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                Arrays.asList("SCENARIOS", "DELIVERY"),
                createContext(),
                30000
        );
        
        ScenarioOrchestrationResult mockResult = createMockResult(
                "req-001",
                true,
                EnumSet.of(ScenarioGroup.SCENARIOS, ScenarioGroup.DELIVERY)
        );
        
        when(mockOrchestrationService.runConcurrentScenarios(any()))
                .thenReturn(mockResult);
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertEquals(2, body.getScenarioStatuses().size());
        assertTrue(body.getScenarioStatuses().containsKey("SCENARIOS"));
        assertTrue(body.getScenarioStatuses().containsKey("DELIVERY"));
        
        // Verify service was called
        verify(mockOrchestrationService, times(1)).runConcurrentScenarios(any());
    }
    
    @Test
    void testExecuteScenarios_MissingUserId() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                null,  // Missing userId
                Arrays.asList("SCENARIOS"),
                createContext(),
                30000
        );
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertEquals(400, response.getStatusCodeValue());
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("userId is required", body.getMessage());
        
        // Verify service was not called
        verify(mockOrchestrationService, never()).runConcurrentScenarios(any());
    }
    
    @Test
    void testExecuteScenarios_EmptyScenarioGroups() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                Collections.emptyList(),  // Empty scenario groups
                createContext(),
                30000
        );
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertEquals(400, response.getStatusCodeValue());
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertTrue(body.getMessage().contains("At least one scenario group is required"));
        
        // Verify service was not called
        verify(mockOrchestrationService, never()).runConcurrentScenarios(any());
    }
    
    @Test
    void testExecuteScenarios_InvalidScenarioGroup() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                Arrays.asList("INVALID_SCENARIO"),  // Invalid scenario group
                createContext(),
                30000
        );
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertEquals(400, response.getStatusCodeValue());
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertTrue(body.getMessage().contains("Invalid scenario group"));
        
        // Verify service was not called
        verify(mockOrchestrationService, never()).runConcurrentScenarios(any());
    }
    
    @Test
    void testExecuteScenarios_WithContext() {
        // Arrange
        Map<String, Object> context = new HashMap<>();
        context.put("batchId", "BATCH-001");
        context.put("farmerId", "FARM-123");
        
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                Arrays.asList("MAP", "DELIVERY"),
                context,
                30000
        );
        
        ScenarioOrchestrationResult mockResult = createMockResult(
                "req-002",
                true,
                EnumSet.of(ScenarioGroup.MAP, ScenarioGroup.DELIVERY)
        );
        
        when(mockOrchestrationService.runConcurrentScenarios(any()))
                .thenReturn(mockResult);
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        
        // Capture the argument passed to the service
        ArgumentCaptor<ScenarioOrchestrationRequest> captor = 
                ArgumentCaptor.forClass(ScenarioOrchestrationRequest.class);
        verify(mockOrchestrationService).runConcurrentScenarios(captor.capture());
        
        ScenarioOrchestrationRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest.getContext());
        assertEquals("BATCH-001", capturedRequest.getContext().get("batchId"));
        assertEquals("FARM-123", capturedRequest.getContext().get("farmerId"));
    }
    
    @Test
    void testExecuteAllScenarios() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                null,  // Will be replaced with all scenarios
                createContext(),
                30000
        );
        
        ScenarioOrchestrationResult mockResult = createMockResult(
                "req-003",
                true,
                EnumSet.allOf(ScenarioGroup.class)  // All 6 scenarios
        );
        
        when(mockOrchestrationService.runConcurrentScenarios(any()))
                .thenReturn(mockResult);
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeAllScenarios(request);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertEquals(6, body.getScenarioStatuses().size());
        
        // Verify all scenario groups are present
        assertTrue(body.getScenarioStatuses().containsKey("SCENARIOS"));
        assertTrue(body.getScenarioStatuses().containsKey("DELIVERY"));
        assertTrue(body.getScenarioStatuses().containsKey("MAP"));
        assertTrue(body.getScenarioStatuses().containsKey("TEMPERATURE"));
        assertTrue(body.getScenarioStatuses().containsKey("SUPPLIER_COMPLIANCE"));
        assertTrue(body.getScenarioStatuses().containsKey("SIMULATIONS"));
    }
    
    @Test
    void testExecuteScenarios_PartialFailure() {
        // Arrange
        OrchestrationController.OrchestrationRequest request = createRequest(
                "user-123",
                Arrays.asList("SCENARIOS", "DELIVERY", "TEMPERATURE"),
                createContext(),
                30000
        );
        
        ScenarioOrchestrationResult mockResult = createMockPartialFailureResult(
                "req-004",
                EnumSet.of(ScenarioGroup.SCENARIOS, ScenarioGroup.DELIVERY, ScenarioGroup.TEMPERATURE)
        );
        
        when(mockOrchestrationService.runConcurrentScenarios(any()))
                .thenReturn(mockResult);
        
        // Act
        ResponseEntity<OrchestrationController.OrchestrationResponse> response = 
                controller.executeScenarios(request);
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        
        OrchestrationController.OrchestrationResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());  // Overall failure due to one scenario failing
        assertEquals(3, body.getScenarioStatuses().size());
        
        // Verify partial failure details
        OrchestrationController.ScenarioStatusResponse temperatureStatus = 
                body.getScenarioStatuses().get("TEMPERATURE");
        assertNotNull(temperatureStatus);
        assertEquals("PARTIAL", temperatureStatus.getStatus());
    }
    
    // Helper methods
    
    private OrchestrationController.OrchestrationRequest createRequest(
            String userId,
            List<String> scenarioGroups,
            Map<String, Object> context,
            long timeoutMs) {
        OrchestrationController.OrchestrationRequest request = 
                new OrchestrationController.OrchestrationRequest();
        request.setUserId(userId);
        request.setScenarioGroups(scenarioGroups);
        request.setContext(context);
        request.setTimeoutMs(timeoutMs);
        return request;
    }
    
    private Map<String, Object> createContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("batchId", "BATCH-001");
        return context;
    }
    
    private ScenarioOrchestrationResult createMockResult(
            String requestId,
            boolean success,
            EnumSet<ScenarioGroup> scenarioGroups) {
        Map<ScenarioGroup, ScenarioExecutionStatus> statuses = new HashMap<>();
        
        Instant now = Instant.now();
        for (ScenarioGroup group : scenarioGroups) {
            statuses.put(group, new ScenarioExecutionStatus(
                    group,
                    ExecutionStatus.SUCCESS,
                    "Completed successfully",
                    now,
                    null
            ));
        }
        
        return new ScenarioOrchestrationResult(
                requestId,
                now.minusMillis(1000),
                now,
                statuses
        );
    }
    
    private ScenarioOrchestrationResult createMockPartialFailureResult(
            String requestId,
            EnumSet<ScenarioGroup> scenarioGroups) {
        Map<ScenarioGroup, ScenarioExecutionStatus> statuses = new HashMap<>();
        
        Instant now = Instant.now();
        for (ScenarioGroup group : scenarioGroups) {
            ExecutionStatus status = (group == ScenarioGroup.TEMPERATURE) ? 
                    ExecutionStatus.PARTIAL : ExecutionStatus.SUCCESS;
            String message = (group == ScenarioGroup.TEMPERATURE) ? 
                    "Partial success" : "Completed successfully";
            
            statuses.put(group, new ScenarioExecutionStatus(
                    group,
                    status,
                    message,
                    now,
                    (group == ScenarioGroup.TEMPERATURE) ? "Kafka failure" : null
            ));
        }
        
        return new ScenarioOrchestrationResult(
                requestId,
                now.minusMillis(1000),
                now,
                statuses
        );
    }
}
