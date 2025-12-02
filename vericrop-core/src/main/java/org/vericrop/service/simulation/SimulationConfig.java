package org.vericrop.service.simulation;

/**
 * Configuration class for simulation timing and parameters.
 * Allows demo simulations to run in 3-4 minutes instead of real-time.
 */
public class SimulationConfig {
    
    /**
     * Default simulation duration in milliseconds (3 minutes for demo).
     */
    public static final long DEFAULT_SIMULATION_DURATION_MS = 180_000L;
    
    /**
     * Default time scale factor. A value of 10 means 10 minutes of
     * simulated time passes for each 1 minute of real time.
     * With this scale, a 30-minute simulated route completes in 3 minutes.
     */
    public static final double DEFAULT_TIME_SCALE = 10.0;
    
    /**
     * Interpolation interval in milliseconds for smooth map animation.
     * Updates position every 200ms for 60fps-like smoothness.
     */
    public static final long DEFAULT_INTERPOLATION_INTERVAL_MS = 200L;
    
    /**
     * Default number of waypoints for a route segment.
     */
    public static final int DEFAULT_WAYPOINTS_PER_SEGMENT = 20;
    
    /**
     * Progress update interval for UI callbacks in milliseconds.
     */
    public static final long DEFAULT_PROGRESS_UPDATE_INTERVAL_MS = 1000L;
    
    /**
     * Simulation lifecycle states following a strict finite-state machine.
     */
    public enum SimulationState {
        /** Initial state - simulation is configured but not started */
        AVAILABLE,
        /** Simulation is running and shipment is in transit */
        IN_TRANSIT,
        /** Shipment is approaching destination (>= 80% progress) */
        APPROACHING,
        /** Simulation completed successfully */
        COMPLETED,
        /** Simulation failed or was cancelled */
        STOPPED
    }
    
    private long simulationDurationMs;
    private double timeScale;
    private long interpolationIntervalMs;
    private int waypointsPerSegment;
    private long progressUpdateIntervalMs;
    private double approachingThreshold;
    
    /**
     * Create config with default values for demo mode.
     */
    public SimulationConfig() {
        this.simulationDurationMs = DEFAULT_SIMULATION_DURATION_MS;
        this.timeScale = DEFAULT_TIME_SCALE;
        this.interpolationIntervalMs = DEFAULT_INTERPOLATION_INTERVAL_MS;
        this.waypointsPerSegment = DEFAULT_WAYPOINTS_PER_SEGMENT;
        this.progressUpdateIntervalMs = DEFAULT_PROGRESS_UPDATE_INTERVAL_MS;
        this.approachingThreshold = 80.0; // Enter APPROACHING at 80% progress
    }
    
    /**
     * Create config with custom time scale.
     * @param timeScale time multiplier (higher = faster simulation)
     */
    public SimulationConfig(double timeScale) {
        this();
        this.timeScale = timeScale;
    }
    
    /**
     * Create config with full customization.
     */
    public SimulationConfig(long simulationDurationMs, double timeScale, 
                           long interpolationIntervalMs, int waypointsPerSegment) {
        this.simulationDurationMs = simulationDurationMs;
        this.timeScale = timeScale;
        this.interpolationIntervalMs = interpolationIntervalMs;
        this.waypointsPerSegment = waypointsPerSegment;
        this.progressUpdateIntervalMs = DEFAULT_PROGRESS_UPDATE_INTERVAL_MS;
        this.approachingThreshold = 80.0;
    }
    
    /**
     * Builder for creating SimulationConfig instances.
     */
    public static class Builder {
        private long simulationDurationMs = DEFAULT_SIMULATION_DURATION_MS;
        private double timeScale = DEFAULT_TIME_SCALE;
        private long interpolationIntervalMs = DEFAULT_INTERPOLATION_INTERVAL_MS;
        private int waypointsPerSegment = DEFAULT_WAYPOINTS_PER_SEGMENT;
        private long progressUpdateIntervalMs = DEFAULT_PROGRESS_UPDATE_INTERVAL_MS;
        private double approachingThreshold = 80.0;
        
        public Builder simulationDurationMs(long durationMs) {
            this.simulationDurationMs = durationMs;
            return this;
        }
        
        public Builder timeScale(double scale) {
            this.timeScale = scale;
            return this;
        }
        
        public Builder interpolationIntervalMs(long intervalMs) {
            this.interpolationIntervalMs = intervalMs;
            return this;
        }
        
        public Builder waypointsPerSegment(int waypoints) {
            this.waypointsPerSegment = waypoints;
            return this;
        }
        
        public Builder progressUpdateIntervalMs(long intervalMs) {
            this.progressUpdateIntervalMs = intervalMs;
            return this;
        }
        
        public Builder approachingThreshold(double threshold) {
            this.approachingThreshold = threshold;
            return this;
        }
        
        public SimulationConfig build() {
            SimulationConfig config = new SimulationConfig();
            config.simulationDurationMs = this.simulationDurationMs;
            config.timeScale = this.timeScale;
            config.interpolationIntervalMs = this.interpolationIntervalMs;
            config.waypointsPerSegment = this.waypointsPerSegment;
            config.progressUpdateIntervalMs = this.progressUpdateIntervalMs;
            config.approachingThreshold = this.approachingThreshold;
            return config;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Get configuration for fast demo mode (3 minutes total).
     */
    public static SimulationConfig forDemo() {
        return new Builder()
            .simulationDurationMs(180_000L) // 3 minutes
            .timeScale(10.0) // 10x speed
            .interpolationIntervalMs(200L)
            .waypointsPerSegment(30)
            .build();
    }
    
    /**
     * Get configuration for real-time simulation.
     */
    public static SimulationConfig forRealTime() {
        return new Builder()
            .simulationDurationMs(1_800_000L) // 30 minutes
            .timeScale(1.0) // Real time
            .interpolationIntervalMs(1000L)
            .waypointsPerSegment(60)
            .build();
    }
    
    /**
     * Simulated route duration in milliseconds (30 minutes of simulated time).
     * This is the base duration that presentation mode scales into the real-time duration.
     */
    public static final long SIMULATED_ROUTE_DURATION_MS = 30 * 60 * 1000L;
    
    /**
     * Get configuration for presentation mode (~2 minutes total).
     * Designed for multi-instance simulation demonstrations with
     * 3 instances of each controller type running in parallel.
     */
    public static SimulationConfig forPresentation() {
        return new Builder()
            .simulationDurationMs(120_000L) // 2 minutes
            .timeScale(15.0) // 15x speed for faster completion
            .interpolationIntervalMs(200L)
            .waypointsPerSegment(24) // 24 waypoints for smooth animation
            .progressUpdateIntervalMs(1000L)
            .approachingThreshold(85.0) // Enter APPROACHING at 85%
            .build();
    }
    
    /**
     * Get configuration with custom duration for presentation mode.
     * @param durationSeconds Duration in seconds (default: 120)
     * @return SimulationConfig configured for the specified duration
     */
    public static SimulationConfig forPresentation(int durationSeconds) {
        // Calculate time scale to fit simulated route duration into the specified duration
        double timeScale = (double) SIMULATED_ROUTE_DURATION_MS / (durationSeconds * 1000.0);
        
        return new Builder()
            .simulationDurationMs(durationSeconds * 1000L)
            .timeScale(timeScale)
            .interpolationIntervalMs(200L)
            .waypointsPerSegment(Math.max(12, durationSeconds / 5)) // Scale waypoints
            .progressUpdateIntervalMs(1000L)
            .approachingThreshold(85.0)
            .build();
    }
    
    // Getters
    
    public long getSimulationDurationMs() {
        return simulationDurationMs;
    }
    
    public double getTimeScale() {
        return timeScale;
    }
    
    public long getInterpolationIntervalMs() {
        return interpolationIntervalMs;
    }
    
    public int getWaypointsPerSegment() {
        return waypointsPerSegment;
    }
    
    public long getProgressUpdateIntervalMs() {
        return progressUpdateIntervalMs;
    }
    
    public double getApproachingThreshold() {
        return approachingThreshold;
    }
    
    /**
     * Calculate real-time duration based on simulated duration and time scale.
     * @param simulatedDurationMs the simulated duration
     * @return real-time duration in milliseconds
     */
    public long calculateRealTimeDuration(long simulatedDurationMs) {
        return (long) (simulatedDurationMs / timeScale);
    }
    
    /**
     * Calculate simulated time elapsed based on real time and time scale.
     * @param realTimeElapsedMs real time elapsed
     * @return simulated time elapsed in milliseconds
     */
    public long calculateSimulatedTimeElapsed(long realTimeElapsedMs) {
        return (long) (realTimeElapsedMs * timeScale);
    }
    
    /**
     * Determine the simulation state based on progress percentage.
     * @param progressPercent progress as percentage (0-100)
     * @return the appropriate simulation state
     */
    public SimulationState determineState(double progressPercent) {
        if (progressPercent >= 100.0) {
            return SimulationState.COMPLETED;
        } else if (progressPercent >= approachingThreshold) {
            return SimulationState.APPROACHING;
        } else if (progressPercent > 0) {
            return SimulationState.IN_TRANSIT;
        } else {
            return SimulationState.AVAILABLE;
        }
    }
    
    @Override
    public String toString() {
        return String.format("SimulationConfig{duration=%dms, timeScale=%.1fx, interpolation=%dms, waypoints=%d}",
            simulationDurationMs, timeScale, interpolationIntervalMs, waypointsPerSegment);
    }
}
