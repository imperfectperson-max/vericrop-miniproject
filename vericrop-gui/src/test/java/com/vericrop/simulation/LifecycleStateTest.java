package com.vericrop.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for delivery lifecycle state transitions.
 * Validates the state machine: AVAILABLE -> IN_TRANSIT -> APPROACHING -> COMPLETED
 */
@DisplayName("Lifecycle State Tests")
class LifecycleStateTest {
    
    @Test
    @DisplayName("State order is correct")
    void stateOrderIsCorrect() {
        DeliveryState[] states = DeliveryState.values();
        
        assertEquals(4, states.length, "Should have exactly 4 states");
        assertEquals(DeliveryState.AVAILABLE, states[0], "First state should be AVAILABLE");
        assertEquals(DeliveryState.IN_TRANSIT, states[1], "Second state should be IN_TRANSIT");
        assertEquals(DeliveryState.APPROACHING, states[2], "Third state should be APPROACHING");
        assertEquals(DeliveryState.COMPLETED, states[3], "Fourth state should be COMPLETED");
    }
    
    @ParameterizedTest
    @DisplayName("State from progress - boundary tests")
    @CsvSource({
        "0, AVAILABLE",
        "5, AVAILABLE",
        "9.9, AVAILABLE",
        "10, IN_TRANSIT",
        "10.1, IN_TRANSIT",
        "50, IN_TRANSIT",
        "69.9, IN_TRANSIT",
        "70, APPROACHING",
        "70.1, APPROACHING",
        "85, APPROACHING",
        "94.9, APPROACHING",
        "95, COMPLETED",
        "99, COMPLETED",
        "100, COMPLETED"
    })
    void stateFromProgressBoundaries(double progress, DeliveryState expected) {
        DeliveryState actual = DeliveryState.fromProgress(progress);
        assertEquals(expected, actual, 
            "Progress " + progress + " should map to " + expected);
    }
    
    @Test
    @DisplayName("Negative progress maps to AVAILABLE")
    void negativeProgressMapsToAvailable() {
        DeliveryState state = DeliveryState.fromProgress(-10);
        assertEquals(DeliveryState.AVAILABLE, state,
            "Negative progress should map to AVAILABLE");
    }
    
    @Test
    @DisplayName("Progress over 100 maps to COMPLETED")
    void overHundredMapsToCompleted() {
        DeliveryState state = DeliveryState.fromProgress(150);
        assertEquals(DeliveryState.COMPLETED, state,
            "Progress over 100 should map to COMPLETED");
    }
    
    @Test
    @DisplayName("Forward transitions are valid")
    void forwardTransitionsValid() {
        assertTrue(DeliveryState.AVAILABLE.canTransitionTo(DeliveryState.IN_TRANSIT),
            "AVAILABLE -> IN_TRANSIT should be valid");
        assertTrue(DeliveryState.AVAILABLE.canTransitionTo(DeliveryState.APPROACHING),
            "AVAILABLE -> APPROACHING should be valid");
        assertTrue(DeliveryState.AVAILABLE.canTransitionTo(DeliveryState.COMPLETED),
            "AVAILABLE -> COMPLETED should be valid");
        
        assertTrue(DeliveryState.IN_TRANSIT.canTransitionTo(DeliveryState.APPROACHING),
            "IN_TRANSIT -> APPROACHING should be valid");
        assertTrue(DeliveryState.IN_TRANSIT.canTransitionTo(DeliveryState.COMPLETED),
            "IN_TRANSIT -> COMPLETED should be valid");
        
        assertTrue(DeliveryState.APPROACHING.canTransitionTo(DeliveryState.COMPLETED),
            "APPROACHING -> COMPLETED should be valid");
    }
    
    @Test
    @DisplayName("Backward transitions are invalid")
    void backwardTransitionsInvalid() {
        assertFalse(DeliveryState.COMPLETED.canTransitionTo(DeliveryState.APPROACHING),
            "COMPLETED -> APPROACHING should be invalid");
        assertFalse(DeliveryState.COMPLETED.canTransitionTo(DeliveryState.IN_TRANSIT),
            "COMPLETED -> IN_TRANSIT should be invalid");
        assertFalse(DeliveryState.COMPLETED.canTransitionTo(DeliveryState.AVAILABLE),
            "COMPLETED -> AVAILABLE should be invalid");
        
        assertFalse(DeliveryState.APPROACHING.canTransitionTo(DeliveryState.IN_TRANSIT),
            "APPROACHING -> IN_TRANSIT should be invalid");
        assertFalse(DeliveryState.APPROACHING.canTransitionTo(DeliveryState.AVAILABLE),
            "APPROACHING -> AVAILABLE should be invalid");
        
        assertFalse(DeliveryState.IN_TRANSIT.canTransitionTo(DeliveryState.AVAILABLE),
            "IN_TRANSIT -> AVAILABLE should be invalid");
    }
    
    @Test
    @DisplayName("Same state transition is invalid")
    void sameStateTransitionInvalid() {
        for (DeliveryState state : DeliveryState.values()) {
            assertFalse(state.canTransitionTo(state),
                state + " -> " + state + " should be invalid");
        }
    }
    
    @Test
    @DisplayName("getNextState returns correct next state")
    void getNextStateReturnsCorrectState() {
        assertEquals(DeliveryState.IN_TRANSIT, DeliveryState.AVAILABLE.getNextState(),
            "Next state after AVAILABLE should be IN_TRANSIT");
        assertEquals(DeliveryState.APPROACHING, DeliveryState.IN_TRANSIT.getNextState(),
            "Next state after IN_TRANSIT should be APPROACHING");
        assertEquals(DeliveryState.COMPLETED, DeliveryState.APPROACHING.getNextState(),
            "Next state after APPROACHING should be COMPLETED");
        assertEquals(DeliveryState.COMPLETED, DeliveryState.COMPLETED.getNextState(),
            "Next state after COMPLETED should be COMPLETED (terminal)");
    }
    
    @ParameterizedTest
    @DisplayName("Each state has valid progress ranges")
    @EnumSource(DeliveryState.class)
    void statesHaveValidProgressRanges(DeliveryState state) {
        assertTrue(state.getMinProgress() >= 0, 
            state + " minProgress should be >= 0");
        assertTrue(state.getMaxProgress() <= 100, 
            state + " maxProgress should be <= 100");
        assertTrue(state.getMinProgress() < state.getMaxProgress(),
            state + " minProgress should be < maxProgress");
    }
    
    @Test
    @DisplayName("State progress ranges are contiguous")
    void stateProgressRangesContiguous() {
        DeliveryState[] states = DeliveryState.values();
        
        for (int i = 0; i < states.length - 1; i++) {
            DeliveryState current = states[i];
            DeliveryState next = states[i + 1];
            
            assertEquals(current.getMaxProgress(), next.getMinProgress(),
                "State " + current + " maxProgress should equal " + next + " minProgress");
        }
    }
    
    @ParameterizedTest
    @DisplayName("Display names are correct")
    @EnumSource(DeliveryState.class)
    void displayNamesAreCorrect(DeliveryState state) {
        assertNotNull(state.getDisplayName(), 
            state + " should have a display name");
        assertFalse(state.getDisplayName().isEmpty(),
            state + " display name should not be empty");
        assertEquals(state.toString(), state.getDisplayName(),
            "toString should return display name");
    }
    
    @Test
    @DisplayName("Complete state progression")
    void completeStateProgression() {
        // Simulate a complete delivery journey
        DeliveryState state = DeliveryState.AVAILABLE;
        
        // Progress through the journey
        double[] progressValues = {0, 5, 10, 30, 50, 70, 85, 95, 100};
        DeliveryState[] expectedStates = {
            DeliveryState.AVAILABLE,
            DeliveryState.AVAILABLE,
            DeliveryState.IN_TRANSIT,
            DeliveryState.IN_TRANSIT,
            DeliveryState.IN_TRANSIT,
            DeliveryState.APPROACHING,
            DeliveryState.APPROACHING,
            DeliveryState.COMPLETED,
            DeliveryState.COMPLETED
        };
        
        for (int i = 0; i < progressValues.length; i++) {
            DeliveryState newState = DeliveryState.fromProgress(progressValues[i]);
            assertEquals(expectedStates[i], newState,
                "Progress " + progressValues[i] + " should be " + expectedStates[i]);
            
            // Verify transition is valid
            if (!newState.equals(state)) {
                assertTrue(state.canTransitionTo(newState),
                    "Transition from " + state + " to " + newState + " should be valid");
                state = newState;
            }
        }
        
        assertEquals(DeliveryState.COMPLETED, state,
            "Final state should be COMPLETED");
    }
    
    @Test
    @DisplayName("DeliveryEvent state transitions emit correctly")
    void deliveryEventStateTransitions() {
        String deliveryId = "TEST-001";
        
        // Create state change event
        DeliveryEvent event = DeliveryEvent.stateChanged(
            deliveryId,
            DeliveryState.IN_TRANSIT,
            DeliveryState.AVAILABLE,
            15.0, // progress
            42.36, -71.06, // coordinates
            "Highway Rest Stop",
            4.5, 65.0, // temperature, humidity
            95.0 // quality
        );
        
        assertEquals(DeliveryState.IN_TRANSIT, event.getState());
        assertEquals(DeliveryState.AVAILABLE, event.getPreviousState());
        assertTrue(event.isStateChange());
        assertFalse(event.isTerminal());
        assertEquals(DeliveryEvent.EventType.STATE_CHANGED, event.getEventType());
    }
    
    @Test
    @DisplayName("DeliveryEvent completion carries final quality")
    void deliveryEventCompletionHasFinalQuality() {
        String deliveryId = "TEST-001";
        double finalQuality = 87.5;
        
        // Create completion event
        DeliveryEvent event = DeliveryEvent.completed(
            deliveryId,
            42.37, -71.11, // coordinates
            "Warehouse",
            4.2, 62.0, // temperature, humidity
            finalQuality
        );
        
        assertEquals(DeliveryState.COMPLETED, event.getState());
        assertTrue(event.hasFinalQuality());
        assertEquals(finalQuality, event.getFinalQuality(), 0.001);
        assertTrue(event.isTerminal());
        assertEquals(100.0, event.getProgress(), 0.001);
    }
}
