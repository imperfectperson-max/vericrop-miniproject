package com.vericrop.simulation;

/**
 * Represents the lifecycle state of a delivery simulation.
 * State transitions follow this order: AVAILABLE -> IN_TRANSIT -> APPROACHING -> COMPLETED
 */
public enum DeliveryState {
    /**
     * Initial state: delivery is available and ready to start.
     * Progress: 0%
     */
    AVAILABLE(0, 10, "Available"),
    
    /**
     * Delivery is in transit from origin.
     * Progress: 10-70%
     */
    IN_TRANSIT(10, 70, "In Transit"),
    
    /**
     * Delivery is approaching destination.
     * Progress: 70-95%
     */
    APPROACHING(70, 95, "Approaching"),
    
    /**
     * Delivery has been completed.
     * Progress: 95-100%
     */
    COMPLETED(95, 100, "Completed");
    
    private final int minProgress;
    private final int maxProgress;
    private final String displayName;
    
    DeliveryState(int minProgress, int maxProgress, String displayName) {
        this.minProgress = minProgress;
        this.maxProgress = maxProgress;
        this.displayName = displayName;
    }
    
    public int getMinProgress() {
        return minProgress;
    }
    
    public int getMaxProgress() {
        return maxProgress;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Determine the state based on progress percentage.
     * @param progressPercent Progress as percentage (0-100)
     * @return The corresponding delivery state
     */
    public static DeliveryState fromProgress(double progressPercent) {
        if (progressPercent < IN_TRANSIT.minProgress) {
            return AVAILABLE;
        } else if (progressPercent < APPROACHING.minProgress) {
            return IN_TRANSIT;
        } else if (progressPercent < COMPLETED.minProgress) {
            return APPROACHING;
        } else {
            return COMPLETED;
        }
    }
    
    /**
     * Check if transition to another state is valid.
     * Only forward transitions are allowed.
     * @param nextState The target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(DeliveryState nextState) {
        return nextState.ordinal() > this.ordinal();
    }
    
    /**
     * Get the next state in the lifecycle.
     * @return The next state, or this state if already at COMPLETED
     */
    public DeliveryState getNextState() {
        DeliveryState[] values = DeliveryState.values();
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal < values.length) {
            return values[nextOrdinal];
        }
        return this;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
