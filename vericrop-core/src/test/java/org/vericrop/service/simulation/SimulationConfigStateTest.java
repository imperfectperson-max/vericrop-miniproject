package org.vericrop.service.simulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationConfig state machine and timing calculations.
 */
public class SimulationConfigStateTest {
    
    @Test
    public void testStateProgression_Available() {
        SimulationConfig config = SimulationConfig.forDemo();
        
        // At 0% progress, state should be AVAILABLE
        assertEquals(SimulationConfig.SimulationState.AVAILABLE, 
            config.determineState(0.0));
    }
    
    @Test
    public void testStateProgression_InTransit() {
        SimulationConfig config = SimulationConfig.forDemo();
        
        // Between 0% and 80%, state should be IN_TRANSIT
        assertEquals(SimulationConfig.SimulationState.IN_TRANSIT, 
            config.determineState(1.0));
        assertEquals(SimulationConfig.SimulationState.IN_TRANSIT, 
            config.determineState(25.0));
        assertEquals(SimulationConfig.SimulationState.IN_TRANSIT, 
            config.determineState(50.0));
        assertEquals(SimulationConfig.SimulationState.IN_TRANSIT, 
            config.determineState(79.9));
    }
    
    @Test
    public void testStateProgression_Approaching() {
        SimulationConfig config = SimulationConfig.forDemo();
        
        // Between 80% and 100%, state should be APPROACHING
        assertEquals(SimulationConfig.SimulationState.APPROACHING, 
            config.determineState(80.0));
        assertEquals(SimulationConfig.SimulationState.APPROACHING, 
            config.determineState(90.0));
        assertEquals(SimulationConfig.SimulationState.APPROACHING, 
            config.determineState(99.9));
    }
    
    @Test
    public void testStateProgression_Completed() {
        SimulationConfig config = SimulationConfig.forDemo();
        
        // At 100% or more, state should be COMPLETED
        assertEquals(SimulationConfig.SimulationState.COMPLETED, 
            config.determineState(100.0));
        assertEquals(SimulationConfig.SimulationState.COMPLETED, 
            config.determineState(100.1)); // Edge case
    }
    
    @Test
    public void testStateTransitionsAreMonotonic() {
        SimulationConfig config = SimulationConfig.forDemo();
        
        // Verify states follow strict order: AVAILABLE -> IN_TRANSIT -> APPROACHING -> COMPLETED
        SimulationConfig.SimulationState[] expectedOrder = {
            SimulationConfig.SimulationState.AVAILABLE,
            SimulationConfig.SimulationState.IN_TRANSIT,
            SimulationConfig.SimulationState.APPROACHING,
            SimulationConfig.SimulationState.COMPLETED
        };
        
        double[] progressPoints = {0.0, 40.0, 85.0, 100.0};
        
        for (int i = 0; i < progressPoints.length; i++) {
            assertEquals(expectedOrder[i], config.determineState(progressPoints[i]),
                "State at " + progressPoints[i] + "% should be " + expectedOrder[i]);
        }
    }
    
    @Test
    public void testCustomApproachingThreshold() {
        // Create config with custom threshold at 90%
        SimulationConfig config = SimulationConfig.builder()
            .approachingThreshold(90.0)
            .build();
        
        // At 85%, should still be IN_TRANSIT with 90% threshold
        assertEquals(SimulationConfig.SimulationState.IN_TRANSIT, 
            config.determineState(85.0));
        
        // At 90%, should be APPROACHING
        assertEquals(SimulationConfig.SimulationState.APPROACHING, 
            config.determineState(90.0));
    }
    
    @Test
    public void testDemoConfigDefaults() {
        SimulationConfig demoConfig = SimulationConfig.forDemo();
        
        // Demo should have 3-minute duration (180,000 ms)
        assertEquals(180_000L, demoConfig.getSimulationDurationMs());
        
        // Demo should have 10x time scale
        assertEquals(10.0, demoConfig.getTimeScale(), 0.01);
        
        // Demo should have 80% approaching threshold
        assertEquals(80.0, demoConfig.getApproachingThreshold(), 0.01);
    }
    
    @Test
    public void testRealTimeConfigDefaults() {
        SimulationConfig realTimeConfig = SimulationConfig.forRealTime();
        
        // Real-time should have 30-minute duration (1,800,000 ms)
        assertEquals(1_800_000L, realTimeConfig.getSimulationDurationMs());
        
        // Real-time should have 1x time scale
        assertEquals(1.0, realTimeConfig.getTimeScale(), 0.01);
    }
    
    @Test
    public void testTimeScaleCalculation() {
        SimulationConfig config = SimulationConfig.builder()
            .timeScale(10.0)
            .build();
        
        // 30 minutes simulated = 3 minutes real time at 10x scale
        long simulatedDurationMs = 30 * 60 * 1000; // 30 minutes
        long realTimeDuration = config.calculateRealTimeDuration(simulatedDurationMs);
        
        assertEquals(3 * 60 * 1000, realTimeDuration); // 3 minutes
    }
    
    @Test
    public void testSimulatedTimeCalculation() {
        SimulationConfig config = SimulationConfig.builder()
            .timeScale(10.0)
            .build();
        
        // 3 minutes real time = 30 minutes simulated at 10x scale
        long realTimeElapsedMs = 3 * 60 * 1000; // 3 minutes
        long simulatedTime = config.calculateSimulatedTimeElapsed(realTimeElapsedMs);
        
        assertEquals(30 * 60 * 1000, simulatedTime); // 30 minutes
    }
    
    @Test
    public void testBuilderPattern() {
        SimulationConfig config = SimulationConfig.builder()
            .simulationDurationMs(240_000L) // 4 minutes
            .timeScale(12.0)
            .interpolationIntervalMs(100L)
            .waypointsPerSegment(25)
            .progressUpdateIntervalMs(500L)
            .approachingThreshold(75.0)
            .build();
        
        assertEquals(240_000L, config.getSimulationDurationMs());
        assertEquals(12.0, config.getTimeScale(), 0.01);
        assertEquals(100L, config.getInterpolationIntervalMs());
        assertEquals(25, config.getWaypointsPerSegment());
        assertEquals(500L, config.getProgressUpdateIntervalMs());
        assertEquals(75.0, config.getApproachingThreshold(), 0.01);
    }
    
    @Test
    public void testProgressNeverRestarts() {
        // This test validates the fix for the ~50% restart bug
        // by ensuring state progression is strictly forward
        SimulationConfig config = SimulationConfig.forDemo();
        
        SimulationConfig.SimulationState prevState = SimulationConfig.SimulationState.AVAILABLE;
        
        // Simulate progress from 0 to 100
        for (int progress = 0; progress <= 100; progress += 5) {
            SimulationConfig.SimulationState currentState = config.determineState(progress);
            
            // State should never go backward
            int prevOrdinal = prevState.ordinal();
            int currentOrdinal = currentState.ordinal();
            
            assertTrue(currentOrdinal >= prevOrdinal || currentState == prevState,
                "State should never regress: was " + prevState + " at progress " + (progress - 5) +
                ", now " + currentState + " at progress " + progress);
            
            prevState = currentState;
        }
    }
    
    @Test
    public void testPresentationConfigDefaults() {
        SimulationConfig presentationConfig = SimulationConfig.forPresentation();
        
        // Presentation mode should have 2-minute duration (120,000 ms)
        assertEquals(120_000L, presentationConfig.getSimulationDurationMs());
        
        // Presentation should have 15x time scale for faster completion
        assertEquals(15.0, presentationConfig.getTimeScale(), 0.01);
        
        // Presentation should have 85% approaching threshold
        assertEquals(85.0, presentationConfig.getApproachingThreshold(), 0.01);
        
        // Presentation should have 24 waypoints
        assertEquals(24, presentationConfig.getWaypointsPerSegment());
    }
    
    @Test
    public void testPresentationConfigWithCustomDuration() {
        // Test 90-second presentation
        SimulationConfig config90s = SimulationConfig.forPresentation(90);
        assertEquals(90_000L, config90s.getSimulationDurationMs());
        
        // Test 180-second presentation
        SimulationConfig config180s = SimulationConfig.forPresentation(180);
        assertEquals(180_000L, config180s.getSimulationDurationMs());
        
        // Time scale should be calculated to fit 30 minutes of simulated time
        // into the specified duration
        // For 90s: scale = (30 * 60 * 1000) / (90 * 1000) = 20.0
        assertEquals(20.0, config90s.getTimeScale(), 0.01);
        
        // For 180s: scale = (30 * 60 * 1000) / (180 * 1000) = 10.0
        assertEquals(10.0, config180s.getTimeScale(), 0.01);
    }
}
