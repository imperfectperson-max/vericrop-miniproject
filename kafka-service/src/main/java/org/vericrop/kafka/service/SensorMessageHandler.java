package org.vericrop.kafka.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.core.util.SensorUtils;
import org.vericrop.kafka.events.LogisticsEvent;

/**
 * Example handler demonstrating how to apply sensor value clamping
 * at the ingestion/consumer boundary for Kafka messages.
 * 
 * This is a minimal example. Maintainers can integrate the clamping
 * directly into existing consumer classes if preferred.
 */
public class SensorMessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SensorMessageHandler.class);
    
    /**
     * Process an incoming LogisticsEvent by clamping environmental sensor values
     * to valid ranges before further processing.
     * 
     * @param event the incoming logistics event
     * @return the event with clamped sensor values
     */
    public LogisticsEvent processLogisticsEvent(LogisticsEvent event) {
        if (event == null) {
            return null;
        }
        
        // Apply clamping to ensure sensor values are within valid ranges
        LogisticsEventAdapter adapter = new LogisticsEventAdapter(event);
        SensorUtils.clampReadings(adapter);
        
        logger.debug("Processed LogisticsEvent with clamped values: temp={}°C, humidity={}%",
                event.getTemperature(), event.getHumidity());
        
        return event;
    }
    
    /**
     * Adapter to make LogisticsEvent compatible with SensorUtils.EnvironmentalReadings.
     * This pattern allows applying clamping without modifying the original DTO.
     */
    private static class LogisticsEventAdapter implements SensorUtils.EnvironmentalReadings {
        private final LogisticsEvent event;
        
        public LogisticsEventAdapter(LogisticsEvent event) {
            this.event = event;
        }
        
        @Override
        public double getTemperature() {
            return event.getTemperature();
        }
        
        @Override
        public void setTemperature(double temperature) {
            event.setTemperature(temperature);
        }
        
        @Override
        public double getHumidity() {
            return event.getHumidity();
        }
        
        @Override
        public void setHumidity(double humidity) {
            event.setHumidity(humidity);
        }
        
        // LogisticsEvent doesn't have pressure or CO2 fields,
        // so we use default implementations that return null
    }
}
