package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Temperature compliance scenario definition
 */
public class TemperatureScenario {
    private String id;
    private String description;
    private TargetRange target;
    private int durationMinutes;
    private List<TemperatureSpike> spikes;
    
    public TemperatureScenario() {}
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public TargetRange getTarget() { return target; }
    public void setTarget(TargetRange target) { this.target = target; }
    
    @JsonProperty("duration_minutes")
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    
    public List<TemperatureSpike> getSpikes() { return spikes; }
    public void setSpikes(List<TemperatureSpike> spikes) { this.spikes = spikes; }
    
    @Override
    public String toString() {
        return String.format("TemperatureScenario{id='%s', target=%.1f-%.1f°C, duration=%dmin, spikes=%d}",
                id, target != null ? target.getMin() : 0, target != null ? target.getMax() : 0, 
                durationMinutes, spikes != null ? spikes.size() : 0);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemperatureScenario that = (TemperatureScenario) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * Target temperature range
     */
    public static class TargetRange {
        private double min;
        private double max;
        
        public TargetRange() {}
        
        public TargetRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
        
        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
        
        public boolean isInRange(double temperature) {
            return temperature >= min && temperature <= max;
        }
    }
    
    /**
     * Represents a planned temperature spike in the scenario.
     * Uses double for atMinute and durationMinutes to support fractional minutes
     * needed for short 2-minute presentation scenarios.
     */
    public static class TemperatureSpike {
        private double atMinute;
        private double durationMinutes;
        private double temperature;
        private String description;
        
        public TemperatureSpike() {}
        
        public TemperatureSpike(double atMinute, double durationMinutes, double temperature) {
            this.atMinute = atMinute;
            this.durationMinutes = durationMinutes;
            this.temperature = temperature;
        }
        
        @JsonProperty("at_minute")
        public double getAtMinute() { return atMinute; }
        public void setAtMinute(double atMinute) { this.atMinute = atMinute; }
        
        @JsonProperty("duration_minutes")
        public double getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(double durationMinutes) { this.durationMinutes = durationMinutes; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
