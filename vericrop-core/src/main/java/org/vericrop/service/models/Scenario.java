package org.vericrop.service.models;

/**
 * Pre-defined delivery simulation scenarios with different environmental conditions.
 */
public enum Scenario {
    /**
     * Normal delivery conditions: stable temperature and humidity, no delays.
     */
    NORMAL("Normal", 0.0, 0.0, 1.0, 0.01),
    
    /**
     * Hot transport: elevated temperatures, increased spoilage risk.
     */
    HOT_TRANSPORT("Hot Transport", 8.0, 0.0, 1.0, 0.05),
    
    /**
     * Cold storage: lower temperatures, reduced spoilage but possible freezing.
     */
    COLD_STORAGE("Cold Storage", -5.0, -10.0, 1.0, 0.02),
    
    /**
     * Humid route: high humidity, mold and decay risk.
     */
    HUMID_ROUTE("Humid Route", 2.0, 15.0, 1.0, 0.04),
    
    /**
     * Extreme delay: normal conditions but slower speed and delays.
     */
    EXTREME_DELAY("Extreme Delay", 0.0, 0.0, 0.4, 0.06);
    
    private final String displayName;
    private final double temperatureDrift; // °C drift from ideal
    private final double humidityDrift;    // % drift from ideal
    private final double speedMultiplier;  // Speed factor (1.0 = normal, 0.5 = half speed)
    private final double spoilageRate;     // Spoilage probability per hour
    
    Scenario(String displayName, double temperatureDrift, double humidityDrift,
             double speedMultiplier, double spoilageRate) {
        this.displayName = displayName;
        this.temperatureDrift = temperatureDrift;
        this.humidityDrift = humidityDrift;
        this.speedMultiplier = speedMultiplier;
        this.spoilageRate = spoilageRate;
    }
    
    public String getDisplayName() { return displayName; }
    public double getTemperatureDrift() { return temperatureDrift; }
    public double getHumidityDrift() { return humidityDrift; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getSpoilageRate() { return spoilageRate; }
}
