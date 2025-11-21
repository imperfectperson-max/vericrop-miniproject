package org.vericrop.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SensorUtils class.
 * Tests validate clamping behavior for environmental sensor values.
 */
public class SensorUtilsTest {
    
    @Test
    public void testClamp_ValueWithinRange() {
        double result = SensorUtils.clamp(50.0, 0.0, 100.0);
        assertEquals(50.0, result, 0.001);
    }
    
    @Test
    public void testClamp_ValueBelowMin() {
        double result = SensorUtils.clamp(-10.0, 0.0, 100.0);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClamp_ValueAboveMax() {
        double result = SensorUtils.clamp(150.0, 0.0, 100.0);
        assertEquals(100.0, result, 0.001);
    }
    
    @Test
    public void testClamp_ValueAtMin() {
        double result = SensorUtils.clamp(0.0, 0.0, 100.0);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClamp_ValueAtMax() {
        double result = SensorUtils.clamp(100.0, 0.0, 100.0);
        assertEquals(100.0, result, 0.001);
    }
    
    @Test
    public void testClamp_NaN() {
        double result = SensorUtils.clamp(Double.NaN, 0.0, 100.0);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClampTemperatureC_NormalRange() {
        double result = SensorUtils.clampTemperatureC(25.0);
        assertEquals(25.0, result, 0.001);
    }
    
    @Test
    public void testClampTemperatureC_BelowMin() {
        double result = SensorUtils.clampTemperatureC(-100.0);
        assertEquals(-50.0, result, 0.001);
    }
    
    @Test
    public void testClampTemperatureC_AboveMax() {
        double result = SensorUtils.clampTemperatureC(80.0);
        assertEquals(60.0, result, 0.001);
    }
    
    @Test
    public void testClampTemperatureC_AtBoundaries() {
        assertEquals(-50.0, SensorUtils.clampTemperatureC(-50.0), 0.001);
        assertEquals(60.0, SensorUtils.clampTemperatureC(60.0), 0.001);
    }
    
    @Test
    public void testClampTemperatureC_NaN() {
        double result = SensorUtils.clampTemperatureC(Double.NaN);
        assertEquals(-50.0, result, 0.001);
    }
    
    @Test
    public void testClampRelativeHumidity_NormalRange() {
        double result = SensorUtils.clampRelativeHumidity(75.0);
        assertEquals(75.0, result, 0.001);
    }
    
    @Test
    public void testClampRelativeHumidity_BelowMin() {
        double result = SensorUtils.clampRelativeHumidity(-10.0);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClampRelativeHumidity_AboveMax() {
        double result = SensorUtils.clampRelativeHumidity(150.0);
        assertEquals(100.0, result, 0.001);
    }
    
    @Test
    public void testClampRelativeHumidity_AtBoundaries() {
        assertEquals(0.0, SensorUtils.clampRelativeHumidity(0.0), 0.001);
        assertEquals(100.0, SensorUtils.clampRelativeHumidity(100.0), 0.001);
    }
    
    @Test
    public void testClampRelativeHumidity_NaN() {
        double result = SensorUtils.clampRelativeHumidity(Double.NaN);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClampPressure_NormalRange() {
        double result = SensorUtils.clampPressure(1013.25);
        assertEquals(1013.25, result, 0.001);
    }
    
    @Test
    public void testClampPressure_BelowMin() {
        double result = SensorUtils.clampPressure(100.0);
        assertEquals(300.0, result, 0.001);
    }
    
    @Test
    public void testClampPressure_AboveMax() {
        double result = SensorUtils.clampPressure(1500.0);
        assertEquals(1100.0, result, 0.001);
    }
    
    @Test
    public void testClampPressure_AtBoundaries() {
        assertEquals(300.0, SensorUtils.clampPressure(300.0), 0.001);
        assertEquals(1100.0, SensorUtils.clampPressure(1100.0), 0.001);
    }
    
    @Test
    public void testClampCo2Ppm_NormalRange() {
        double result = SensorUtils.clampCo2Ppm(400.0);
        assertEquals(400.0, result, 0.001);
    }
    
    @Test
    public void testClampCo2Ppm_BelowMin() {
        double result = SensorUtils.clampCo2Ppm(-100.0);
        assertEquals(0.0, result, 0.001);
    }
    
    @Test
    public void testClampCo2Ppm_AboveMax() {
        double result = SensorUtils.clampCo2Ppm(150000.0);
        assertEquals(100000.0, result, 0.001);
    }
    
    @Test
    public void testClampCo2Ppm_AtBoundaries() {
        assertEquals(0.0, SensorUtils.clampCo2Ppm(0.0), 0.001);
        assertEquals(100000.0, SensorUtils.clampCo2Ppm(100000.0), 0.001);
    }
    
    @Test
    public void testClampReadings_WithValidDTO() {
        TestEnvironmentalDTO dto = new TestEnvironmentalDTO();
        dto.setTemperature(25.0);
        dto.setHumidity(75.0);
        dto.setPressure(1013.25);
        dto.setCo2Ppm(400.0);
        
        SensorUtils.clampReadings(dto);
        
        assertEquals(25.0, dto.getTemperature(), 0.001);
        assertEquals(75.0, dto.getHumidity(), 0.001);
        assertEquals(1013.25, dto.getPressure(), 0.001);
        assertEquals(400.0, dto.getCo2Ppm(), 0.001);
    }
    
    @Test
    public void testClampReadings_WithInvalidValues() {
        TestEnvironmentalDTO dto = new TestEnvironmentalDTO();
        dto.setTemperature(-100.0);  // Below min
        dto.setHumidity(150.0);      // Above max
        dto.setPressure(100.0);      // Below min
        dto.setCo2Ppm(150000.0);     // Above max
        
        SensorUtils.clampReadings(dto);
        
        assertEquals(-50.0, dto.getTemperature(), 0.001);
        assertEquals(100.0, dto.getHumidity(), 0.001);
        assertEquals(300.0, dto.getPressure(), 0.001);
        assertEquals(100000.0, dto.getCo2Ppm(), 0.001);
    }
    
    @Test
    public void testClampReadings_WithNullDTO() {
        TestEnvironmentalDTO result = SensorUtils.clampReadings(null);
        assertNull(result);
    }
    
    @Test
    public void testClampReadings_WithNaNValues() {
        TestEnvironmentalDTO dto = new TestEnvironmentalDTO();
        dto.setTemperature(Double.NaN);
        dto.setHumidity(Double.NaN);
        dto.setPressure(Double.NaN);
        dto.setCo2Ppm(Double.NaN);
        
        SensorUtils.clampReadings(dto);
        
        assertEquals(-50.0, dto.getTemperature(), 0.001);
        assertEquals(0.0, dto.getHumidity(), 0.001);
        assertEquals(300.0, dto.getPressure(), 0.001);
        assertEquals(0.0, dto.getCo2Ppm(), 0.001);
    }
    
    @Test
    public void testClampReadings_WithOptionalFieldsNull() {
        TestEnvironmentalDTOMinimal dto = new TestEnvironmentalDTOMinimal();
        dto.setTemperature(25.0);
        dto.setHumidity(75.0);
        
        SensorUtils.clampReadings(dto);
        
        assertEquals(25.0, dto.getTemperature(), 0.001);
        assertEquals(75.0, dto.getHumidity(), 0.001);
        assertNull(dto.getPressure());
        assertNull(dto.getCo2Ppm());
    }
    
    /**
     * Test DTO with all environmental readings.
     */
    private static class TestEnvironmentalDTO implements SensorUtils.EnvironmentalReadings {
        private double temperature;
        private double humidity;
        private double pressure;
        private double co2Ppm;
        
        @Override
        public double getTemperature() {
            return temperature;
        }
        
        @Override
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
        
        @Override
        public double getHumidity() {
            return humidity;
        }
        
        @Override
        public void setHumidity(double humidity) {
            this.humidity = humidity;
        }
        
        @Override
        public Double getPressure() {
            return pressure;
        }
        
        @Override
        public void setPressure(double pressure) {
            this.pressure = pressure;
        }
        
        @Override
        public Double getCo2Ppm() {
            return co2Ppm;
        }
        
        @Override
        public void setCo2Ppm(double co2Ppm) {
            this.co2Ppm = co2Ppm;
        }
    }
    
    /**
     * Test DTO with only required fields.
     */
    private static class TestEnvironmentalDTOMinimal implements SensorUtils.EnvironmentalReadings {
        private double temperature;
        private double humidity;
        
        @Override
        public double getTemperature() {
            return temperature;
        }
        
        @Override
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
        
        @Override
        public double getHumidity() {
            return humidity;
        }
        
        @Override
        public void setHumidity(double humidity) {
            this.humidity = humidity;
        }
    }
}
