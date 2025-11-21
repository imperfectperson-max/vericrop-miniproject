package org.vericrop.core.util;

/**
 * Utility class for validating and clamping environmental sensor values.
 * Provides generic and domain-specific clamping methods to ensure sensor
 * readings fall within realistic ranges.
 */
public class SensorUtils {
    
    // Temperature range for cold chain produce (Celsius)
    private static final double TEMP_MIN_C = -50.0;
    private static final double TEMP_MAX_C = 60.0;
    
    // Relative humidity range (percentage)
    private static final double HUMIDITY_MIN = 0.0;
    private static final double HUMIDITY_MAX = 100.0;
    
    // Atmospheric pressure range (hPa)
    private static final double PRESSURE_MIN_HPA = 300.0;
    private static final double PRESSURE_MAX_HPA = 1100.0;
    
    // CO2 concentration range (ppm)
    private static final double CO2_MIN_PPM = 0.0;
    private static final double CO2_MAX_PPM = 100000.0;
    
    /**
     * Interface for DTOs containing environmental sensor readings.
     * Implement this interface to enable automatic clamping of sensor values.
     */
    public interface EnvironmentalReadings {
        double getTemperature();
        void setTemperature(double temperature);
        
        double getHumidity();
        void setHumidity(double humidity);
        
        default Double getPressure() { return null; }
        default void setPressure(double pressure) {}
        
        default Double getCo2Ppm() { return null; }
        default void setCo2Ppm(double co2Ppm) {}
    }
    
    /**
     * Generic clamp method that constrains a value within a specified range.
     * Treats NaN as min value and returns min in that case.
     * 
     * @param value the value to clamp
     * @param min the minimum allowed value
     * @param max the maximum allowed value
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
    
    /**
     * Clamp temperature to valid range for cold chain applications.
     * 
     * @param temperatureC temperature in Celsius
     * @return clamped temperature value
     */
    public static double clampTemperatureC(double temperatureC) {
        return clamp(temperatureC, TEMP_MIN_C, TEMP_MAX_C);
    }
    
    /**
     * Clamp relative humidity to valid percentage range.
     * 
     * @param humidity relative humidity as percentage
     * @return clamped humidity value
     */
    public static double clampRelativeHumidity(double humidity) {
        return clamp(humidity, HUMIDITY_MIN, HUMIDITY_MAX);
    }
    
    /**
     * Clamp atmospheric pressure to valid range.
     * 
     * @param pressureHpa pressure in hectopascals (hPa)
     * @return clamped pressure value
     */
    public static double clampPressure(double pressureHpa) {
        return clamp(pressureHpa, PRESSURE_MIN_HPA, PRESSURE_MAX_HPA);
    }
    
    /**
     * Clamp CO2 concentration to valid range.
     * 
     * @param co2Ppm CO2 concentration in parts per million (ppm)
     * @return clamped CO2 value
     */
    public static double clampCo2Ppm(double co2Ppm) {
        return clamp(co2Ppm, CO2_MIN_PPM, CO2_MAX_PPM);
    }
    
    /**
     * Apply clamping to all sensor readings in a DTO that implements
     * the EnvironmentalReadings interface.
     * 
     * @param readings DTO containing environmental sensor readings
     * @param <T> type implementing EnvironmentalReadings interface
     * @return the same DTO instance with clamped values
     */
    public static <T extends EnvironmentalReadings> T clampReadings(T readings) {
        if (readings == null) {
            return null;
        }
        
        // Clamp temperature and humidity (required fields)
        readings.setTemperature(clampTemperatureC(readings.getTemperature()));
        readings.setHumidity(clampRelativeHumidity(readings.getHumidity()));
        
        // Clamp optional fields if present
        Double pressure = readings.getPressure();
        if (pressure != null) {
            readings.setPressure(clampPressure(pressure));
        }
        
        Double co2 = readings.getCo2Ppm();
        if (co2 != null) {
            readings.setCo2Ppm(clampCo2Ppm(co2));
        }
        
        return readings;
    }
}
