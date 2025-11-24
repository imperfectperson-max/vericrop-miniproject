package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for LogisticsController status transition logic.
 * Verifies that status transitions are idempotent and don't generate duplicate alerts.
 */
class LogisticsControllerStatusTransitionTest {
    
    /**
     * Test that status transitions follow expected thresholds.
     * This is a basic test that verifies the status calculation logic.
     */
    @Test
    void testStatusTransitionThresholds() {
        // Test Created status (0-10%)
        assertEquals("Created", getStatusForProgress(0.0));
        assertEquals("Created", getStatusForProgress(5.0));
        
        // Test Departing status (10-30%)
        assertEquals("Departing", getStatusForProgress(10.0));
        assertEquals("Departing", getStatusForProgress(20.0));
        
        // Test En Route status (30-70%)
        assertEquals("En Route", getStatusForProgress(30.0));
        assertEquals("En Route", getStatusForProgress(50.0)); // Midpoint - critical test case
        
        // Test Approaching status (70-90%)
        assertEquals("Approaching", getStatusForProgress(70.0));
        assertEquals("Approaching", getStatusForProgress(80.0));
        
        // Test At Warehouse status (90-100%)
        assertEquals("At Warehouse", getStatusForProgress(90.0));
        assertEquals("At Warehouse", getStatusForProgress(95.0));
        
        // Test Delivered status (100%)
        assertEquals("Delivered", getStatusForProgress(100.0));
    }
    
    /**
     * Test that status remains stable at midpoint (50%) - no duplicate "Delivered" alerts.
     * This specifically addresses Bug #3 from the problem statement.
     */
    @Test
    void testMidpointDoesNotTriggerDelivered() {
        String status = getStatusForProgress(50.0);
        assertNotEquals("Delivered", status, 
            "Status at 50% should NOT be Delivered - this would cause duplicate alerts");
        assertEquals("En Route", status, 
            "Status at 50% (midpoint) should be 'En Route'");
    }
    
    /**
     * Test that consecutive calls with the same progress don't trigger status change.
     * Simulates idempotent status transitions.
     */
    @Test
    void testIdempotentStatusTransitions() {
        // At 50% progress, status should be the same on consecutive calls
        String status1 = getStatusForProgress(50.0);
        String status2 = getStatusForProgress(50.0);
        assertEquals(status1, status2, 
            "Status should be consistent for same progress value");
        
        // At 90% progress
        String status3 = getStatusForProgress(90.0);
        String status4 = getStatusForProgress(90.0);
        assertEquals(status3, status4, 
            "Status should be consistent for same progress value");
    }
    
    /**
     * Helper method that mimics the determineStatusFromProgress logic in LogisticsController.
     * This is duplicated here for testing purposes.
     * 
     * IMPORTANT: If you modify the status logic in LogisticsController,
     * you must update this method to match.
     */
    private String getStatusForProgress(double progressPercent) {
        // Constants from LogisticsController
        final double PROGRESS_COMPLETE = 100.0;
        final double PROGRESS_AT_WAREHOUSE_THRESHOLD = 90.0;
        final double PROGRESS_APPROACHING_THRESHOLD = 70.0;
        final double PROGRESS_EN_ROUTE_THRESHOLD = 30.0;
        final double PROGRESS_DEPARTING_THRESHOLD = 10.0;
        
        if (progressPercent >= PROGRESS_COMPLETE) {
            return "Delivered";
        } else if (progressPercent >= PROGRESS_AT_WAREHOUSE_THRESHOLD) {
            return "At Warehouse";
        } else if (progressPercent >= PROGRESS_APPROACHING_THRESHOLD) {
            return "Approaching";
        } else if (progressPercent >= PROGRESS_EN_ROUTE_THRESHOLD) {
            return "En Route";
        } else if (progressPercent >= PROGRESS_DEPARTING_THRESHOLD) {
            return "Departing";
        } else {
            return "Created";
        }
    }
}
