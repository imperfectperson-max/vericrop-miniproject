package com.vericrop.simulation;

/**
 * Configuration for simulation parameters including demo mode settings.
 * Supports time compression for faster demonstrations.
 */
public class SimulationConfig {
    
    // Default values for normal mode
    private static final int DEFAULT_UPDATE_INTERVAL_MS = 5000;    // 5 seconds between updates
    private static final double DEFAULT_SPEED_FACTOR = 1.0;        // Normal speed
    private static final int DEFAULT_WAYPOINT_COUNT = 20;          // Number of waypoints
    private static final double DEFAULT_DECAY_RATE = 0.001;        // Quality decay rate per second
    
    // Demo mode values for faster completion (~3-4 minutes)
    private static final int DEMO_UPDATE_INTERVAL_MS = 1000;       // 1 second between updates
    private static final double DEMO_SPEED_FACTOR = 10.0;          // 10x speed
    private static final int DEMO_WAYPOINT_COUNT = 20;             // Fewer waypoints
    
    /**
     * Demo mode decay rate - public constant for use by other components.
     * Rate: 0.002/sec results in ~30% quality drop over 3 minutes.
     */
    public static final double DEMO_MODE_DECAY_RATE = 0.002;
    
    private static final double DEMO_DECAY_RATE = DEMO_MODE_DECAY_RATE;
    
    private final boolean demoMode;
    private final double speedFactor;
    private final int updateIntervalMs;
    private final int waypointCount;
    private final double decayRate;
    private final double initialQuality;
    
    private SimulationConfig(Builder builder) {
        this.demoMode = builder.demoMode;
        this.speedFactor = builder.speedFactor;
        this.updateIntervalMs = builder.updateIntervalMs;
        this.waypointCount = builder.waypointCount;
        this.decayRate = builder.decayRate;
        this.initialQuality = builder.initialQuality;
    }
    
    /**
     * Create a default configuration for normal mode.
     */
    public static SimulationConfig createDefault() {
        return new Builder()
            .demoMode(false)
            .speedFactor(DEFAULT_SPEED_FACTOR)
            .updateIntervalMs(DEFAULT_UPDATE_INTERVAL_MS)
            .waypointCount(DEFAULT_WAYPOINT_COUNT)
            .decayRate(DEFAULT_DECAY_RATE)
            .initialQuality(100.0)
            .build();
    }
    
    /**
     * Create a configuration for demo mode.
     * Optimized to complete simulation in ~3-4 minutes.
     */
    public static SimulationConfig createDemoMode() {
        return new Builder()
            .demoMode(true)
            .speedFactor(DEMO_SPEED_FACTOR)
            .updateIntervalMs(DEMO_UPDATE_INTERVAL_MS)
            .waypointCount(DEMO_WAYPOINT_COUNT)
            .decayRate(DEMO_DECAY_RATE)
            .initialQuality(100.0)
            .build();
    }
    
    /**
     * Create configuration based on environment settings.
     * Checks VERICROP_DEMO_MODE environment variable and vericrop.demoMode system property.
     */
    public static SimulationConfig fromEnvironment() {
        boolean isDemoMode = isDemoModeEnabled();
        return isDemoMode ? createDemoMode() : createDefault();
    }
    
    /**
     * Check if demo mode is enabled via environment or system property.
     */
    public static boolean isDemoModeEnabled() {
        // Check system property first
        String propValue = System.getProperty("vericrop.demoMode");
        if ("true".equalsIgnoreCase(propValue)) {
            return true;
        }
        
        // Check environment variable
        String envValue = System.getenv("VERICROP_DEMO_MODE");
        if ("true".equalsIgnoreCase(envValue)) {
            return true;
        }
        
        // Also check the existing loadDemo flag for backwards compatibility
        String loadDemo = System.getProperty("vericrop.loadDemo");
        if ("true".equalsIgnoreCase(loadDemo)) {
            return true;
        }
        
        String loadDemoEnv = System.getenv("VERICROP_LOAD_DEMO");
        return "true".equalsIgnoreCase(loadDemoEnv);
    }
    
    // Getters
    
    public boolean isDemoMode() {
        return demoMode;
    }
    
    public double getSpeedFactor() {
        return speedFactor;
    }
    
    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }
    
    /**
     * Get effective update interval accounting for speed factor.
     */
    public int getEffectiveUpdateIntervalMs() {
        return (int) (updateIntervalMs / speedFactor);
    }
    
    public int getWaypointCount() {
        return waypointCount;
    }
    
    public double getDecayRate() {
        return decayRate;
    }
    
    public double getInitialQuality() {
        return initialQuality;
    }
    
    /**
     * Calculate estimated completion time in minutes.
     */
    public double getEstimatedCompletionMinutes() {
        double totalMs = (double) waypointCount * getEffectiveUpdateIntervalMs();
        return totalMs / 60000.0;
    }
    
    @Override
    public String toString() {
        return String.format("SimulationConfig{demoMode=%s, speedFactor=%.1f, updateIntervalMs=%d, " +
                            "waypointCount=%d, decayRate=%.4f, initialQuality=%.1f, estCompletionMin=%.1f}",
                            demoMode, speedFactor, updateIntervalMs, waypointCount, 
                            decayRate, initialQuality, getEstimatedCompletionMinutes());
    }
    
    /**
     * Builder for SimulationConfig.
     */
    public static class Builder {
        private boolean demoMode = false;
        private double speedFactor = DEFAULT_SPEED_FACTOR;
        private int updateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;
        private int waypointCount = DEFAULT_WAYPOINT_COUNT;
        private double decayRate = DEFAULT_DECAY_RATE;
        private double initialQuality = 100.0;
        
        public Builder demoMode(boolean demoMode) {
            this.demoMode = demoMode;
            return this;
        }
        
        public Builder speedFactor(double speedFactor) {
            if (speedFactor <= 0) {
                throw new IllegalArgumentException("speedFactor must be positive");
            }
            this.speedFactor = speedFactor;
            return this;
        }
        
        public Builder updateIntervalMs(int updateIntervalMs) {
            if (updateIntervalMs <= 0) {
                throw new IllegalArgumentException("updateIntervalMs must be positive");
            }
            this.updateIntervalMs = updateIntervalMs;
            return this;
        }
        
        public Builder waypointCount(int waypointCount) {
            if (waypointCount < 2) {
                throw new IllegalArgumentException("waypointCount must be at least 2");
            }
            this.waypointCount = waypointCount;
            return this;
        }
        
        public Builder decayRate(double decayRate) {
            if (decayRate < 0) {
                throw new IllegalArgumentException("decayRate cannot be negative");
            }
            this.decayRate = decayRate;
            return this;
        }
        
        public Builder initialQuality(double initialQuality) {
            if (initialQuality < 0 || initialQuality > 100) {
                throw new IllegalArgumentException("initialQuality must be between 0 and 100");
            }
            this.initialQuality = initialQuality;
            return this;
        }
        
        public SimulationConfig build() {
            return new SimulationConfig(this);
        }
    }
}
