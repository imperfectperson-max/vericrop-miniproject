package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.ScenarioRunRequest;
import org.vericrop.dto.ScenarioRunResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScenarioOrchestrator.
 */
class ScenarioOrchestratorTest {
    
    private ScenarioOrchestrator orchestrator;
    private MessageService messageService;
    private AlertService alertService;
    
    @BeforeEach
    void setUp() {
        messageService = new MessageService();
        alertService = new AlertService();
        
        DeliverySimulator deliverySimulator = new DeliverySimulator(messageService, alertService);
        SimulationOrchestrator simulationOrchestrator = new SimulationOrchestrator(
            deliverySimulator, alertService, messageService);
        TemperatureService temperatureService = new TemperatureService();
        MapService mapService = new MapService();
        SupplierComplianceService supplierComplianceService = new SupplierComplianceService();
        
        orchestrator = new ScenarioOrchestrator(
            simulationOrchestrator,
            deliverySimulator,
            temperatureService,
            mapService,
            supplierComplianceService
        );
    }
    
    @Test
    void testRunScenariosConcurrently_withDeliveryScenario() {
        // Arrange
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setDeliveryScenario(true);
        request.setFarmerId("test-farmer");
        request.setBatchId("test-batch");
        
        // Act
        ScenarioRunResult result = orchestrator.runScenariosConcurrently(request);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getRunId());
        assertEquals("ACCEPTED", result.getStatus());
        assertNotNull(result.getMessage());
    }
    
    @Test
    void testRunScenariosConcurrently_withMultipleScenarios() {
        // Arrange
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setDeliveryScenario(true);
        request.setMapScenario(true);
        request.setTemperatureScenario(true);
        request.setFarmerId("test-farmer");
        request.setBatchId("test-batch");
        
        // Act
        ScenarioRunResult result = orchestrator.runScenariosConcurrently(request);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getRunId());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals(3, request.getEnabledScenarioCount());
    }
    
    @Test
    void testRunScenariosConcurrently_withNoScenariosEnabled() {
        // Arrange
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setFarmerId("test-farmer");
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.runScenariosConcurrently(request);
        });
    }
    
    @Test
    void testGetRunStatus() throws InterruptedException {
        // Arrange
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setDeliveryScenario(true);
        request.setFarmerId("test-farmer");
        
        // Act
        ScenarioRunResult result = orchestrator.runScenariosConcurrently(request);
        String runId = result.getRunId();
        
        // Give orchestrator time to process
        Thread.sleep(100);
        
        // Get status
        ScenarioRunResult status = orchestrator.getRunStatus(runId);
        
        // Assert
        assertNotNull(status);
        assertEquals(runId, status.getRunId());
        assertTrue(status.getStatus().equals("ACCEPTED") || 
                   status.getStatus().equals("RUNNING") ||
                   status.getStatus().equals("COMPLETED"));
    }
    
    @Test
    void testGetRunStatus_nonExistentRun() {
        // Act
        ScenarioRunResult status = orchestrator.getRunStatus("non-existent-id");
        
        // Assert
        assertNull(status);
    }
    
    @Test
    void testGetActiveRuns() {
        // Arrange
        ScenarioRunRequest request1 = new ScenarioRunRequest();
        request1.setDeliveryScenario(true);
        request1.setFarmerId("farmer1");
        
        ScenarioRunRequest request2 = new ScenarioRunRequest();
        request2.setMapScenario(true);
        request2.setFarmerId("farmer2");
        
        // Act
        orchestrator.runScenariosConcurrently(request1);
        orchestrator.runScenariosConcurrently(request2);
        
        var activeRuns = orchestrator.getActiveRuns();
        
        // Assert
        assertNotNull(activeRuns);
        assertEquals(2, activeRuns.size());
    }
}
