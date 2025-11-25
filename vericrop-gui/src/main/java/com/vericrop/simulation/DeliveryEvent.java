package com.vericrop.simulation;

import java.time.Instant;

/**
 * Represents an event in the delivery simulation lifecycle.
 * Contains all relevant data for state transitions and quality tracking.
 */
public class DeliveryEvent {
    
    private final String deliveryId;
    private final DeliveryState state;
    private final DeliveryState previousState;
    private final double progress;          // 0-100 percentage
    private final double latitude;
    private final double longitude;
    private final String locationName;
    private final double temperature;       // Celsius
    private final double humidity;          // Percentage
    private final double currentQuality;    // Current quality score (0-100)
    private final double finalQuality;      // Final quality at completion (null if not completed)
    private final long timestamp;
    private final EventType eventType;
    
    /**
     * Types of delivery events.
     */
    public enum EventType {
        /**
         * Simulation has started.
         */
        STARTED,
        
        /**
         * State has transitioned to a new lifecycle state.
         */
        STATE_CHANGED,
        
        /**
         * Progress update without state change.
         */
        PROGRESS_UPDATE,
        
        /**
         * Simulation has completed successfully.
         */
        COMPLETED,
        
        /**
         * Simulation was stopped/cancelled.
         */
        STOPPED,
        
        /**
         * An error occurred during simulation.
         */
        ERROR
    }
    
    private DeliveryEvent(Builder builder) {
        this.deliveryId = builder.deliveryId;
        this.state = builder.state;
        this.previousState = builder.previousState;
        this.progress = builder.progress;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.locationName = builder.locationName;
        this.temperature = builder.temperature;
        this.humidity = builder.humidity;
        this.currentQuality = builder.currentQuality;
        this.finalQuality = builder.finalQuality;
        this.timestamp = builder.timestamp;
        this.eventType = builder.eventType;
    }
    
    // Factory methods for common event types
    
    /**
     * Create a started event.
     */
    public static DeliveryEvent started(String deliveryId, double latitude, double longitude, 
                                         String locationName, double initialQuality) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(DeliveryState.AVAILABLE)
            .progress(0)
            .latitude(latitude)
            .longitude(longitude)
            .locationName(locationName)
            .currentQuality(initialQuality)
            .eventType(EventType.STARTED)
            .build();
    }
    
    /**
     * Create a state transition event.
     */
    public static DeliveryEvent stateChanged(String deliveryId, DeliveryState newState, 
                                              DeliveryState previousState, double progress,
                                              double latitude, double longitude, String locationName,
                                              double temperature, double humidity, double currentQuality) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(newState)
            .previousState(previousState)
            .progress(progress)
            .latitude(latitude)
            .longitude(longitude)
            .locationName(locationName)
            .temperature(temperature)
            .humidity(humidity)
            .currentQuality(currentQuality)
            .eventType(EventType.STATE_CHANGED)
            .build();
    }
    
    /**
     * Create a progress update event.
     */
    public static DeliveryEvent progressUpdate(String deliveryId, DeliveryState state, double progress,
                                                double latitude, double longitude, String locationName,
                                                double temperature, double humidity, double currentQuality) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(state)
            .progress(progress)
            .latitude(latitude)
            .longitude(longitude)
            .locationName(locationName)
            .temperature(temperature)
            .humidity(humidity)
            .currentQuality(currentQuality)
            .eventType(EventType.PROGRESS_UPDATE)
            .build();
    }
    
    /**
     * Create a completion event with final quality.
     */
    public static DeliveryEvent completed(String deliveryId, double latitude, double longitude,
                                           String locationName, double temperature, double humidity,
                                           double finalQuality) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(DeliveryState.COMPLETED)
            .previousState(DeliveryState.APPROACHING)
            .progress(100)
            .latitude(latitude)
            .longitude(longitude)
            .locationName(locationName)
            .temperature(temperature)
            .humidity(humidity)
            .currentQuality(finalQuality)
            .finalQuality(finalQuality)
            .eventType(EventType.COMPLETED)
            .build();
    }
    
    /**
     * Create a stopped event.
     */
    public static DeliveryEvent stopped(String deliveryId, DeliveryState currentState, double progress,
                                         double currentQuality) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(currentState)
            .progress(progress)
            .currentQuality(currentQuality)
            .eventType(EventType.STOPPED)
            .build();
    }
    
    /**
     * Create an error event.
     */
    public static DeliveryEvent error(String deliveryId, DeliveryState currentState, 
                                       double progress, String errorMessage) {
        return new Builder()
            .deliveryId(deliveryId)
            .state(currentState)
            .progress(progress)
            .locationName(errorMessage)  // Reuse locationName for error message
            .eventType(EventType.ERROR)
            .build();
    }
    
    // Getters
    
    public String getDeliveryId() {
        return deliveryId;
    }
    
    public DeliveryState getState() {
        return state;
    }
    
    public DeliveryState getPreviousState() {
        return previousState;
    }
    
    public double getProgress() {
        return progress;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public double getHumidity() {
        return humidity;
    }
    
    public double getCurrentQuality() {
        return currentQuality;
    }
    
    public double getFinalQuality() {
        return finalQuality;
    }
    
    public boolean hasFinalQuality() {
        return eventType == EventType.COMPLETED && finalQuality > 0;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public boolean isStateChange() {
        return eventType == EventType.STATE_CHANGED || 
               eventType == EventType.STARTED || 
               eventType == EventType.COMPLETED;
    }
    
    public boolean isTerminal() {
        return eventType == EventType.COMPLETED || 
               eventType == EventType.STOPPED || 
               eventType == EventType.ERROR;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeliveryEvent{");
        sb.append("deliveryId='").append(deliveryId).append("'");
        sb.append(", type=").append(eventType);
        sb.append(", state=").append(state);
        if (previousState != null) {
            sb.append(", previousState=").append(previousState);
        }
        sb.append(", progress=").append(String.format("%.1f", progress)).append("%");
        if (locationName != null) {
            sb.append(", location='").append(locationName).append("'");
        }
        sb.append(", quality=").append(String.format("%.1f", currentQuality));
        if (hasFinalQuality()) {
            sb.append(", finalQuality=").append(String.format("%.1f", finalQuality));
        }
        sb.append(", timestamp=").append(Instant.ofEpochMilli(timestamp));
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Builder for DeliveryEvent.
     */
    public static class Builder {
        private String deliveryId;
        private DeliveryState state;
        private DeliveryState previousState;
        private double progress;
        private double latitude;
        private double longitude;
        private String locationName;
        private double temperature;
        private double humidity;
        private double currentQuality;
        private double finalQuality = -1;
        private long timestamp = System.currentTimeMillis();
        private EventType eventType = EventType.PROGRESS_UPDATE;
        
        public Builder deliveryId(String deliveryId) {
            this.deliveryId = deliveryId;
            return this;
        }
        
        public Builder state(DeliveryState state) {
            this.state = state;
            return this;
        }
        
        public Builder previousState(DeliveryState previousState) {
            this.previousState = previousState;
            return this;
        }
        
        public Builder progress(double progress) {
            this.progress = progress;
            return this;
        }
        
        public Builder latitude(double latitude) {
            this.latitude = latitude;
            return this;
        }
        
        public Builder longitude(double longitude) {
            this.longitude = longitude;
            return this;
        }
        
        public Builder locationName(String locationName) {
            this.locationName = locationName;
            return this;
        }
        
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public Builder humidity(double humidity) {
            this.humidity = humidity;
            return this;
        }
        
        public Builder currentQuality(double currentQuality) {
            this.currentQuality = currentQuality;
            return this;
        }
        
        public Builder finalQuality(double finalQuality) {
            this.finalQuality = finalQuality;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public DeliveryEvent build() {
            if (deliveryId == null || deliveryId.isEmpty()) {
                throw new IllegalStateException("deliveryId is required");
            }
            if (state == null) {
                throw new IllegalStateException("state is required");
            }
            return new DeliveryEvent(this);
        }
    }
}
