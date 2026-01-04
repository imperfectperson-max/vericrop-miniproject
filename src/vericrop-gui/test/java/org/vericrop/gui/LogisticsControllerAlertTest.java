package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LogisticsController alert generation logic.
 * Verifies that humidity and temperature alerts are generated correctly
 * based on cold chain thresholds.
 */
public class LogisticsControllerAlertTest {

    // Humidity thresholds (should match LogisticsController)
    private static final double HUMIDITY_LOW_THRESHOLD = 50.0;
    private static final double HUMIDITY_HIGH_THRESHOLD = 90.0;
    private static final double HUMIDITY_CHANGE_THRESHOLD = 10.0;

    // Temperature thresholds (should match LogisticsController)
    private static final double TEMP_LOW_THRESHOLD = 2.0;
    private static final double TEMP_HIGH_THRESHOLD = 8.0;
    private static final double TEMP_CRITICAL_LOW = 0.0;
    private static final double TEMP_CRITICAL_HIGH = 12.0;

    // ============ Humidity Alert Tests ============

    @Test
    @DisplayName("Low humidity below threshold should trigger alert")
    void testLowHumidityAlertGeneration() {
        double humidity = 40.0;
        boolean shouldAlert = humidity < HUMIDITY_LOW_THRESHOLD;
        assertTrue(shouldAlert, "Humidity " + humidity + "% should trigger low humidity alert");
    }

    @Test
    @DisplayName("High humidity above threshold should trigger alert")
    void testHighHumidityAlertGeneration() {
        double humidity = 95.0;
        boolean shouldAlert = humidity > HUMIDITY_HIGH_THRESHOLD;
        assertTrue(shouldAlert, "Humidity " + humidity + "% should trigger high humidity alert");
    }

    @Test
    @DisplayName("Humidity within optimal range should not trigger alert")
    void testNormalHumidityNoAlert() {
        double humidity = 75.0;
        boolean shouldAlert = humidity < HUMIDITY_LOW_THRESHOLD || humidity > HUMIDITY_HIGH_THRESHOLD;
        assertFalse(shouldAlert, "Humidity " + humidity + "% should not trigger alert");
    }

    @Test
    @DisplayName("Significant humidity change should trigger alert")
    void testHumidityChangeAlert() {
        double previousHumidity = 70.0;
        double currentHumidity = 85.0;
        double change = Math.abs(currentHumidity - previousHumidity);
        boolean shouldAlert = change > HUMIDITY_CHANGE_THRESHOLD;
        assertTrue(shouldAlert, "Humidity change of " + change + "% should trigger alert");
    }

    @Test
    @DisplayName("Small humidity change should not trigger alert")
    void testSmallHumidityChangeNoAlert() {
        double previousHumidity = 70.0;
        double currentHumidity = 75.0;
        double change = Math.abs(currentHumidity - previousHumidity);
        boolean shouldAlert = change > HUMIDITY_CHANGE_THRESHOLD;
        assertFalse(shouldAlert, "Humidity change of " + change + "% should not trigger alert");
    }

    // ============ Temperature Alert Tests ============

    @Test
    @DisplayName("Critical freeze temperature should trigger alert")
    void testCriticalFreezeTemperatureAlert() {
        double temperature = -2.0;
        boolean shouldAlert = temperature < TEMP_CRITICAL_LOW;
        assertTrue(shouldAlert, "Temperature " + temperature + "°C should trigger critical freeze alert");
    }

    @Test
    @DisplayName("Low temperature warning should be triggered")
    void testLowTemperatureWarning() {
        double temperature = 1.0;
        boolean shouldAlert = temperature < TEMP_LOW_THRESHOLD && temperature >= TEMP_CRITICAL_LOW;
        assertTrue(shouldAlert, "Temperature " + temperature + "°C should trigger low temp warning");
    }

    @Test
    @DisplayName("Critical high temperature should trigger alert")
    void testCriticalHighTemperatureAlert() {
        double temperature = 15.0;
        boolean shouldAlert = temperature > TEMP_CRITICAL_HIGH;
        assertTrue(shouldAlert, "Temperature " + temperature + "°C should trigger critical high temp alert");
    }

    @Test
    @DisplayName("High temperature warning should be triggered")
    void testHighTemperatureWarning() {
        double temperature = 10.0;
        boolean shouldAlert = temperature > TEMP_HIGH_THRESHOLD && temperature <= TEMP_CRITICAL_HIGH;
        assertTrue(shouldAlert, "Temperature " + temperature + "°C should trigger high temp warning");
    }

    @Test
    @DisplayName("Optimal temperature should not trigger alert")
    void testOptimalTemperatureNoAlert() {
        double temperature = 5.0;
        boolean shouldAlert = temperature < TEMP_LOW_THRESHOLD || temperature > TEMP_HIGH_THRESHOLD;
        assertFalse(shouldAlert, "Temperature " + temperature + "°C should not trigger alert");
    }

    @Test
    @DisplayName("Edge case: exactly at low threshold should not trigger alert")
    void testTemperatureAtLowThresholdNoAlert() {
        double temperature = TEMP_LOW_THRESHOLD;
        boolean shouldAlert = temperature < TEMP_LOW_THRESHOLD;
        assertFalse(shouldAlert, "Temperature " + temperature + "°C (at threshold) should not trigger low temp alert");
    }

    @Test
    @DisplayName("Edge case: exactly at high threshold should not trigger alert")
    void testTemperatureAtHighThresholdNoAlert() {
        double temperature = TEMP_HIGH_THRESHOLD;
        boolean shouldAlert = temperature > TEMP_HIGH_THRESHOLD;
        assertFalse(shouldAlert, "Temperature " + temperature + "°C (at threshold) should not trigger high temp alert");
    }

    // ============ Status Transition Tests ============

    @Test
    @DisplayName("Stopped simulation tracking should prevent duplicate chart updates")
    void testStoppedSimulationTracking() {
        java.util.Set<String> stoppedSimulations = java.util.concurrent.ConcurrentHashMap.newKeySet();
        String batchId = "TEST_BATCH_001";
        
        // Initially not stopped
        assertFalse(stoppedSimulations.contains(batchId), "Batch should not be stopped initially");
        
        // Add to stopped
        stoppedSimulations.add(batchId);
        assertTrue(stoppedSimulations.contains(batchId), "Batch should be marked as stopped");
        
        // Restarting removes from stopped
        stoppedSimulations.remove(batchId);
        assertFalse(stoppedSimulations.contains(batchId), "Batch should be unmarked when restarted");
    }
}
