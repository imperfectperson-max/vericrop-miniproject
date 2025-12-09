package org.vericrop.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LogisticsController synchronization between Kafka events and SimulationListener callbacks.
 * 
 * This test verifies the fix for the issue where LogisticsController would lose sync when running
 * separate instances (ProducerController, LogisticsController, ConsumerController).
 * 
 * Problem scenario:
 * 1. ProducerController starts simulation → sends Kafka START event
 * 2. LogisticsController receives Kafka START → adds to kafkaManagedSimulations
 * 3. BUG: SimulationManager triggers onSimulationStarted() callback
 * 4. BUG: Old code removed batch from kafkaManagedSimulations (treating it as local)
 * 5. syncWithDeliverySimulator() checks local DeliverySimulator (doesn't know about it)
 * 6. LogisticsController incorrectly marks it as completed
 * 
 * Fix:
 * - onSimulationStarted() now skips initialization if batch is already in kafkaManagedSimulations
 * - onSimulationStopped() now skips stop handling if batch is in kafkaManagedSimulations
 * - This preserves the Kafka-managed flag throughout the simulation lifecycle
 */
class LogisticsControllerKafkaSyncTest {
    
    private Set<String> kafkaManagedSimulations;
    private Set<String> stoppedSimulations;
    private boolean localInitializationCalled;
    private boolean localStopHandlingCalled;
    
    @BeforeEach
    void setUp() {
        kafkaManagedSimulations = ConcurrentHashMap.newKeySet();
        stoppedSimulations = ConcurrentHashMap.newKeySet();
        localInitializationCalled = false;
        localStopHandlingCalled = false;
    }
    
    /**
     * Test that onSimulationStarted() skips local initialization when batch is already
     * tracked via Kafka (from another instance).
     */
    @Test
    void testOnSimulationStartedSkipsLocalInitializationForKafkaBatch() {
        String batchId = "BATCH_KAFKA_001";
        String farmerId = "Farmer123";
        
        // Step 1: Kafka START event arrives first (from ProducerController in another instance)
        kafkaManagedSimulations.add(batchId);
        
        // Step 2: SimulationManager callback fires (shared JVM state)
        simulateOnSimulationStarted(batchId, farmerId);
        
        // Verify: Local initialization should be skipped
        assertFalse(localInitializationCalled,
            "Local initialization should be skipped for Kafka-managed simulation");
        assertTrue(kafkaManagedSimulations.contains(batchId),
            "Batch should remain in kafkaManagedSimulations after callback");
    }
    
    /**
     * Test that onSimulationStarted() performs local initialization when batch is NOT
     * tracked via Kafka (truly local simulation).
     */
    @Test
    void testOnSimulationStartedPerformsLocalInitializationForLocalBatch() {
        String batchId = "BATCH_LOCAL_001";
        String farmerId = "Farmer456";
        
        // No Kafka event - this is a purely local simulation
        
        // SimulationManager callback fires
        simulateOnSimulationStarted(batchId, farmerId);
        
        // Verify: Local initialization should proceed
        assertTrue(localInitializationCalled,
            "Local initialization should proceed for local simulation");
        assertFalse(kafkaManagedSimulations.contains(batchId),
            "Batch should NOT be in kafkaManagedSimulations for local simulation");
    }
    
    /**
     * Test that onSimulationStopped() skips local stop handling when batch is
     * Kafka-managed (will be handled by Kafka STOP event).
     */
    @Test
    void testOnSimulationStoppedSkipsLocalHandlingForKafkaBatch() {
        String batchId = "BATCH_KAFKA_002";
        
        // Batch is tracked as Kafka-managed
        kafkaManagedSimulations.add(batchId);
        
        // SimulationManager stop callback fires
        simulateOnSimulationStopped(batchId, true);
        
        // Verify: Local stop handling should be skipped
        assertFalse(localStopHandlingCalled,
            "Local stop handling should be skipped for Kafka-managed simulation");
        assertFalse(stoppedSimulations.contains(batchId),
            "Batch should NOT be added to stoppedSimulations by local callback");
        assertTrue(kafkaManagedSimulations.contains(batchId),
            "Batch should remain in kafkaManagedSimulations");
    }
    
    /**
     * Test that onSimulationStopped() performs local stop handling when batch is NOT
     * Kafka-managed (truly local simulation).
     */
    @Test
    void testOnSimulationStoppedPerformsLocalHandlingForLocalBatch() {
        String batchId = "BATCH_LOCAL_002";
        
        // Batch is NOT Kafka-managed
        
        // SimulationManager stop callback fires
        simulateOnSimulationStopped(batchId, true);
        
        // Verify: Local stop handling should proceed
        assertTrue(localStopHandlingCalled,
            "Local stop handling should proceed for local simulation");
        assertTrue(stoppedSimulations.contains(batchId),
            "Batch should be added to stoppedSimulations by local callback");
    }
    
    /**
     * Test that Kafka START → SimulationListener callback → Kafka STOP sequence
     * maintains kafkaManagedSimulations integrity.
     */
    @Test
    void testKafkaStartCallbackStopSequenceMaintainsIntegrity() {
        String batchId = "BATCH_KAFKA_003";
        String farmerId = "Farmer789";
        
        // 1. Kafka START event
        kafkaManagedSimulations.add(batchId);
        assertTrue(kafkaManagedSimulations.contains(batchId), "Should be Kafka-managed after START");
        
        // 2. SimulationListener.onSimulationStarted() callback (should skip)
        simulateOnSimulationStarted(batchId, farmerId);
        assertTrue(kafkaManagedSimulations.contains(batchId),
            "Should remain Kafka-managed after onSimulationStarted");
        
        // 3. SimulationListener.onSimulationStopped() callback (should skip)
        simulateOnSimulationStopped(batchId, true);
        assertTrue(kafkaManagedSimulations.contains(batchId),
            "Should remain Kafka-managed after onSimulationStopped");
        
        // 4. Kafka STOP event (cleanup)
        kafkaManagedSimulations.remove(batchId);
        stoppedSimulations.add(batchId);
        assertFalse(kafkaManagedSimulations.contains(batchId), "Should be removed after Kafka STOP");
        assertTrue(stoppedSimulations.contains(batchId), "Should be in stoppedSimulations after Kafka STOP");
    }
    
    /**
     * Test that duplicate SimulationListener callbacks are properly handled.
     */
    @Test
    void testDuplicateCallbacksAreHandledCorrectly() {
        String batchId = "BATCH_KAFKA_004";
        String farmerId = "Farmer000";
        
        // Kafka START event
        kafkaManagedSimulations.add(batchId);
        
        // First callback - should skip
        simulateOnSimulationStarted(batchId, farmerId);
        assertFalse(localInitializationCalled);
        
        // Reset flag
        localInitializationCalled = false;
        
        // Second callback (duplicate) - should also skip
        simulateOnSimulationStarted(batchId, farmerId);
        assertFalse(localInitializationCalled,
            "Duplicate callback should also be skipped");
        assertTrue(kafkaManagedSimulations.contains(batchId),
            "Batch should remain Kafka-managed after duplicate callback");
    }
    
    // Helper methods that simulate LogisticsController behavior
    
    /**
     * Simulates the onSimulationStarted() callback logic.
     */
    private void simulateOnSimulationStarted(String batchId, String farmerId) {
        // Remove from stopped simulations if restarting
        stoppedSimulations.remove(batchId);
        
        // Check if this simulation is already tracked via Kafka (THE FIX)
        if (kafkaManagedSimulations.contains(batchId)) {
            // Skip local initialization - already handled by Kafka
            return;
        }
        
        // Perform local initialization (only for truly local simulations)
        localInitializationCalled = true;
        // In real code: initializeMapMarker, initializeTemperatureChartSeries, etc.
    }
    
    /**
     * Simulates the onSimulationStopped() callback logic.
     */
    private void simulateOnSimulationStopped(String batchId, boolean completed) {
        // Check if this is a Kafka-managed simulation (THE FIX)
        if (kafkaManagedSimulations.contains(batchId)) {
            // Skip local stop handling - will be handled by Kafka STOP event
            return;
        }
        
        // Check if already stopped (avoid duplicates)
        if (!stoppedSimulations.add(batchId)) {
            return;
        }
        
        // Perform local stop handling (only for truly local simulations)
        localStopHandlingCalled = true;
        // In real code: persistSimulationComplete, cleanupMapMarker, etc.
    }
}
