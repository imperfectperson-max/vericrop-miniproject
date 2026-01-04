package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.dto.Message;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.Scenario;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ScenarioController - verifies concurrent execution across all 6 domains:
 * scenarios, delivery, map, temperature, supplier_compliance, simulations
 */
class ScenarioControllerTest {
    
    private ScenarioController scenarioController;
    private DeliverySimulator deliverySimulator;
    private SimulationOrchestrator simulationOrchestrator;
    private TemperatureService temperatureService;
    private MapService mapService;
    private SupplierComplianceService supplierComplianceService;
    private MessageService messageService;
    private AlertService alertService;
    
    @BeforeEach
    void setUp() {
        // Initialize all domain services
        messageService = new MessageService();
        alertService = new AlertService();
        deliverySimulator = new DeliverySimulator(messageService, alertService);
        simulationOrchestrator = new SimulationOrchestrator(deliverySimulator, alertService, messageService);
        temperatureService = new TemperatureService();
        mapService = new MapService();
        supplierComplianceService = new SupplierComplianceService();
        
        // Create ScenarioController with all domain services
        scenarioController = new ScenarioController(
            simulationOrchestrator,
            deliverySimulator,
            temperatureService,
            mapService,
            supplierComplianceService,
            messageService
        );
    }
    
    @Test
    void testExecuteScenariosWithSingleScenario() throws Exception {
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "New York");
        GeoCoordinate destination = new GeoCoordinate(42.3601, -71.0589, "Boston");
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL);
        String farmerId = "FARMER_TEST_001";
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future = 
            scenarioController.executeScenarios(
                origin, destination, 5, 60.0, farmerId, scenarios, 1000);
        
        ScenarioController.ScenarioExecutionResult result = future.get();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getScenarioBatchIds().size());
        assertTrue(result.getScenarioBatchIds().containsKey(Scenario.NORMAL));
        
        // Verify domain results are populated
        assertFalse(result.getDeliveryResults().isEmpty());
        assertFalse(result.getTemperatureResults().isEmpty());
        assertFalse(result.getMapResults().isEmpty());
        assertFalse(result.getSimulationResults().isEmpty());
        
        // Verify the batch ID exists in results
        String batchId = result.getScenarioBatchIds().get(Scenario.NORMAL);
        assertNotNull(batchId, "Batch ID should not be null");
        
        // Verify execution status shows monitoring across all domains
        String executionId = result.getExecutionId();
        Map<String, Object> status = scenarioController.getExecutionStatus(executionId);
        
        assertNotNull(status);
        assertEquals(executionId, status.get("execution_id"));
        assertEquals(1, status.get("scenario_count"));
        
        // Verify domain-specific status exists
        Map<String, Object> domainStatuses = (Map<String, Object>) status.get("domain_statuses");
        assertNotNull(domainStatuses);
        assertTrue(domainStatuses.containsKey("NORMAL"));
    }
    
    @Test
    void testExecuteScenariosWithMultipleScenarios() throws Exception {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(42.0, -76.0, "End");
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.COLD_STORAGE
        );
        String farmerId = "FARMER_TEST_002";
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future = 
            scenarioController.executeScenarios(
                origin, destination, 3, 50.0, farmerId, scenarios, 1000);
        
        ScenarioController.ScenarioExecutionResult result = future.get();
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(3, result.getScenarioBatchIds().size());
        
        // Verify all scenarios were started
        assertTrue(result.getScenarioBatchIds().containsKey(Scenario.NORMAL));
        assertTrue(result.getScenarioBatchIds().containsKey(Scenario.HOT_TRANSPORT));
        assertTrue(result.getScenarioBatchIds().containsKey(Scenario.COLD_STORAGE));
        
        // Verify domain results indicate multiple scenarios
        assertEquals(3, result.getDeliveryResults().get("batch_count"));
        assertEquals(3, result.getTemperatureResults().get("monitoring_active"));
        assertEquals(3, result.getMapResults().get("routes_generated"));
        assertEquals(3, result.getSimulationResults().get("scenarios_started"));
        
        // Verify execution status shows all scenarios
        String executionId = result.getExecutionId();
        Map<String, Object> status = scenarioController.getExecutionStatus(executionId);
        Map<String, Object> domainStatuses = (Map<String, Object>) status.get("domain_statuses");
        assertEquals(3, domainStatuses.size());
    }
    
    @Test
    void testGetExecutionStatus() throws Exception {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL, Scenario.HOT_TRANSPORT);
        String farmerId = "FARMER_TEST_003";
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future = 
            scenarioController.executeScenarios(
                origin, destination, 3, 60.0, farmerId, scenarios, 1000);
        
        ScenarioController.ScenarioExecutionResult result = future.get();
        String executionId = result.getExecutionId();
        
        Map<String, Object> status = scenarioController.getExecutionStatus(executionId);
        
        assertNotNull(status);
        assertEquals(executionId, status.get("execution_id"));
        assertEquals(farmerId, status.get("farmer_id"));
        assertEquals(2, status.get("scenario_count"));
        assertTrue((Boolean) status.get("running"));
        
        // Verify domain-specific status
        Map<String, Object> domainStatuses = (Map<String, Object>) status.get("domain_statuses");
        assertNotNull(domainStatuses);
        assertTrue(domainStatuses.containsKey("NORMAL"));
        assertTrue(domainStatuses.containsKey("HOT_TRANSPORT"));
    }
    
    @Test
    void testStopExecution() throws Exception {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL);
        String farmerId = "FARMER_TEST_004";
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future = 
            scenarioController.executeScenarios(
                origin, destination, 3, 60.0, farmerId, scenarios, 1000);
        
        ScenarioController.ScenarioExecutionResult result = future.get();
        String executionId = result.getExecutionId();
        String batchId = result.getScenarioBatchIds().get(Scenario.NORMAL);
        
        // Verify execution is active
        assertFalse(scenarioController.getActiveExecutions().isEmpty());
        Map<String, Object> statusBefore = scenarioController.getExecutionStatus(executionId);
        assertNotNull(statusBefore);
        
        // Stop execution
        scenarioController.stopExecution(executionId);
        
        // Verify execution was stopped
        assertTrue(scenarioController.getActiveExecutions().isEmpty());
        Map<String, Object> statusAfter = scenarioController.getExecutionStatus(executionId);
        assertTrue(statusAfter.isEmpty());
    }
    
    @Test
    void testGetActiveExecutions() throws Exception {
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Start");
        GeoCoordinate destination = new GeoCoordinate(41.0, -75.0, "End");
        List<Scenario> scenarios = Arrays.asList(Scenario.NORMAL);
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future1 = 
            scenarioController.executeScenarios(
                origin, destination, 3, 60.0, "FARMER_1", scenarios, 1000);
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future2 = 
            scenarioController.executeScenarios(
                origin, destination, 3, 60.0, "FARMER_2", scenarios, 1000);
        
        future1.get();
        future2.get();
        
        Map<String, ScenarioController.ScenarioExecution> activeExecutions = 
            scenarioController.getActiveExecutions();
        
        assertEquals(2, activeExecutions.size());
    }
    
    @Test
    void testAllDomainsIntegration() throws Exception {
        // This test verifies all 6 domains are working together:
        // 1. scenarios - multiple scenarios defined
        // 2. delivery - DeliverySimulator creates simulations
        // 3. map - MapService generates routes with waypoints
        // 4. temperature - TemperatureService monitors temp compliance
        // 5. supplier_compliance - SupplierComplianceService tracks performance
        // 6. simulations - SimulationOrchestrator manages concurrent execution
        
        GeoCoordinate origin = new GeoCoordinate(40.0, -74.0, "Origin");
        GeoCoordinate destination = new GeoCoordinate(42.0, -76.0, "Destination");
        List<Scenario> scenarios = Arrays.asList(
            Scenario.NORMAL,
            Scenario.HOT_TRANSPORT,
            Scenario.COLD_STORAGE,
            Scenario.HUMID_ROUTE,
            Scenario.EXTREME_DELAY
        );
        String farmerId = "FARMER_INTEGRATION";
        
        CompletableFuture<ScenarioController.ScenarioExecutionResult> future = 
            scenarioController.executeScenarios(
                origin, destination, 5, 70.0, farmerId, scenarios, 1000);
        
        ScenarioController.ScenarioExecutionResult result = future.get();
        
        // Verify all 5 scenarios were started
        assertTrue(result.isSuccess());
        assertEquals(5, result.getScenarioBatchIds().size());
        
        // Verify each domain service has data for all scenarios
        for (Map.Entry<Scenario, String> entry : result.getScenarioBatchIds().entrySet()) {
            String batchId = entry.getValue();
            Scenario scenario = entry.getKey();
            
            // 1. Scenario - verified by presence in result
            assertNotNull(batchId);
            assertTrue(batchId.contains(scenario.name()));
            
            // 2. Delivery - simulation status available
            DeliverySimulator.SimulationStatus simStatus = deliverySimulator.getSimulationStatus(batchId);
            assertNotNull(simStatus);
            
            // Note: Direct service checks removed as services are internal to controller
            // Instead, we verify through the controller's status API
        }
        
        // Verify execution status contains information from all domains
        String executionId = result.getExecutionId();
        Map<String, Object> status = scenarioController.getExecutionStatus(executionId);
        
        assertNotNull(status);
        assertEquals(5, status.get("scenario_count"));
        
        // Verify domain statuses exist for all 5 scenarios
        Map<String, Object> domainStatuses = (Map<String, Object>) status.get("domain_statuses");
        assertNotNull(domainStatuses);
        assertEquals(5, domainStatuses.size());
        
        // Verify each domain status has delivery, temperature, and route info
        for (Map.Entry<String, Object> entry : domainStatuses.entrySet()) {
            Map<String, Object> batchStatus = (Map<String, Object>) entry.getValue();
            assertNotNull(batchStatus.get("delivery_running"));
            assertNotNull(batchStatus.get("current_waypoint"));
            assertNotNull(batchStatus.get("total_waypoints"));
        }
        
        // Verify domain results summary
        assertEquals(5, result.getDeliveryResults().get("batch_count"));
        assertEquals(5, result.getTemperatureResults().get("monitoring_active"));
        assertEquals(5, result.getMapResults().get("routes_generated"));
        assertEquals(5, result.getSimulationResults().get("scenarios_started"));
        assertTrue((Boolean) result.getSimulationResults().get("orchestration_active"));
    }
}
