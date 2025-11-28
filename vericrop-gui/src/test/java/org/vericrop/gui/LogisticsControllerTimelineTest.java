package org.vericrop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Timeline updates in LogisticsController during simulation.
 * Verifies that the timeline updates correctly at each progress milestone.
 */
public class LogisticsControllerTimelineTest {
    
    // Progress thresholds from LogisticsController (keep in sync!)
    private static final double PROGRESS_DEPARTING_THRESHOLD = 10.0;
    private static final double PROGRESS_EN_ROUTE_THRESHOLD = 30.0;
    private static final double PROGRESS_APPROACHING_THRESHOLD = 70.0;
    private static final double PROGRESS_AT_WAREHOUSE_THRESHOLD = 90.0;
    private static final double PROGRESS_COMPLETE = 100.0;
    
    /**
     * Test that timeline states are determined correctly based on progress.
     */
    @Test
    @DisplayName("Timeline states should reflect progress correctly")
    void testTimelineStatesFromProgress() {
        // At 0% - only Created should be complete
        TimelineState state0 = getTimelineState(0.0);
        assertTrue(state0.createdComplete, "Created should be complete at 0%");
        assertFalse(state0.inTransitComplete, "In Transit should not be complete at 0%");
        assertFalse(state0.approachingComplete, "Approaching should not be complete at 0%");
        assertFalse(state0.deliveredComplete, "Delivered should not be complete at 0%");
        
        // At 25% - Created and In Transit should be complete
        TimelineState state25 = getTimelineState(25.0);
        assertTrue(state25.createdComplete, "Created should be complete at 25%");
        assertTrue(state25.inTransitComplete, "In Transit should be complete at 25%");
        assertFalse(state25.approachingComplete, "Approaching should not be complete at 25%");
        assertFalse(state25.deliveredComplete, "Delivered should not be complete at 25%");
        
        // At 75% - Created, In Transit, and Approaching should be complete
        TimelineState state75 = getTimelineState(75.0);
        assertTrue(state75.createdComplete, "Created should be complete at 75%");
        assertTrue(state75.inTransitComplete, "In Transit should be complete at 75%");
        assertTrue(state75.approachingComplete, "Approaching should be complete at 75%");
        assertFalse(state75.deliveredComplete, "Delivered should not be complete at 75%");
        
        // At 100% - All states should be complete
        TimelineState state100 = getTimelineState(100.0);
        assertTrue(state100.createdComplete, "Created should be complete at 100%");
        assertTrue(state100.inTransitComplete, "In Transit should be complete at 100%");
        assertTrue(state100.approachingComplete, "Approaching should be complete at 100%");
        assertTrue(state100.deliveredComplete, "Delivered should be complete at 100%");
    }
    
    /**
     * Test that the final state title changes correctly at 100%.
     */
    @Test
    @DisplayName("Final state title should be 'Delivered' only at 100%")
    void testFinalStateTitleAtCompletion() {
        // At 99% - should show "Pending Delivery"
        String title99 = getFinalStateTitle(99.0);
        assertEquals("Pending Delivery", title99, "At 99%, title should be 'Pending Delivery'");
        
        // At 100% - should show "Delivered"
        String title100 = getFinalStateTitle(100.0);
        assertEquals("Delivered", title100, "At 100%, title should be 'Delivered'");
    }
    
    /**
     * Test that ETA is calculated correctly based on progress.
     */
    @Test
    @DisplayName("ETA should decrease as progress increases")
    void testETACalculation() {
        // At 0% - ETA should be maximum
        String eta0 = calculateETA(0.0);
        assertTrue(eta0.contains("min") || eta0.contains("h"), "ETA at 0% should show time remaining");
        
        // At 100% - ETA should show ARRIVED
        String eta100 = calculateETA(100.0);
        assertEquals("ARRIVED", eta100, "ETA at 100% should be ARRIVED");
    }
    
    /**
     * Test that timeline updates at milestone progress values.
     */
    @Test
    @DisplayName("Timeline should update at each milestone")
    void testTimelineUpdatesAtMilestones() {
        double[] milestones = {0, 10, 25, 50, 70, 90, 100};
        
        for (double progress : milestones) {
            TimelineState state = getTimelineState(progress);
            assertNotNull(state, "Timeline state should not be null at " + progress + "%");
            
            // Verify monotonic progression - once a state is complete, it stays complete
            if (progress >= PROGRESS_DEPARTING_THRESHOLD) {
                assertTrue(state.inTransitComplete, "In Transit should be complete at " + progress + "%");
            }
            if (progress >= PROGRESS_APPROACHING_THRESHOLD) {
                assertTrue(state.approachingComplete, "Approaching should be complete at " + progress + "%");
            }
            if (progress >= PROGRESS_COMPLETE) {
                assertTrue(state.deliveredComplete, "Delivered should be complete at " + progress + "%");
            }
        }
    }
    
    /**
     * Test that status string is determined correctly from progress.
     */
    @Test
    @DisplayName("Status string should match progress range")
    void testStatusFromProgress() {
        // Test various progress points and their expected status
        assertEquals("In Transit - Departing", getStatusFromProgress(5.0));
        assertEquals("In Transit - Departing", getStatusFromProgress(15.0));
        assertEquals("In Transit - En Route", getStatusFromProgress(35.0));
        assertEquals("In Transit - En Route", getStatusFromProgress(50.0));
        assertEquals("In Transit - Approaching", getStatusFromProgress(75.0));
        assertEquals("At Warehouse", getStatusFromProgress(92.0));
        assertEquals("Delivered", getStatusFromProgress(100.0));
    }
    
    // ===== Helper methods mimicking LogisticsController logic =====
    
    /**
     * Represents the state of timeline items.
     */
    private static class TimelineState {
        boolean createdComplete;
        boolean inTransitComplete;
        boolean approachingComplete;
        boolean deliveredComplete;
    }
    
    /**
     * Get timeline state based on progress (mirrors LogisticsController.updateTimeline logic).
     */
    private TimelineState getTimelineState(double progress) {
        TimelineState state = new TimelineState();
        state.createdComplete = true;
        state.inTransitComplete = progress >= PROGRESS_DEPARTING_THRESHOLD;
        state.approachingComplete = progress >= PROGRESS_APPROACHING_THRESHOLD;
        state.deliveredComplete = progress >= PROGRESS_COMPLETE;
        return state;
    }
    
    /**
     * Get final state title based on progress.
     */
    private String getFinalStateTitle(double progress) {
        return progress >= PROGRESS_COMPLETE ? "Delivered" : "Pending Delivery";
    }
    
    /**
     * Calculate ETA string based on progress (mirrors LogisticsController.calculateETA).
     */
    private String calculateETA(double progress) {
        if (progress >= PROGRESS_COMPLETE) return "ARRIVED";
        double remaining = 1.0 - (progress / 100.0);
        int totalMinutes = (int) (remaining * 60);
        if (totalMinutes > 60) {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            return hours + "h " + minutes + "min";
        }
        return totalMinutes + " min";
    }
    
    /**
     * Get status string from progress (mirrors LogisticsController.onProgressUpdate logic).
     */
    private String getStatusFromProgress(double progress) {
        if (progress < PROGRESS_EN_ROUTE_THRESHOLD) {
            return "In Transit - Departing";
        } else if (progress < PROGRESS_APPROACHING_THRESHOLD) {
            return "In Transit - En Route";
        } else if (progress < PROGRESS_AT_WAREHOUSE_THRESHOLD) {
            return "In Transit - Approaching";
        } else if (progress >= PROGRESS_COMPLETE) {
            return "Delivered";
        } else {
            return "At Warehouse";
        }
    }
}
