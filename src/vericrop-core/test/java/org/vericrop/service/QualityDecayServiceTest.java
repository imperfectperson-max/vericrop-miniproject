package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.QualityDecayService.EnvironmentalReading;
import org.vericrop.service.QualityDecayService.QualityTracePoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QualityDecayService.
 */
public class QualityDecayServiceTest {
    
    private QualityDecayService service;
    
    @BeforeEach
    public void setUp() {
        service = new QualityDecayService();
    }
    
    @Test
    public void testCalculateQuality_IdealConditions() {
        // In ideal conditions (5°C, 75% humidity), quality should decay slowly
        double initialQuality = 100.0;
        double temperature = 5.0;
        double humidity = 75.0;
        double hours = 10.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        // Should decay by base rate only (0.5 per hour = 5.0 total)
        assertEquals(95.0, finalQuality, 0.1);
    }
    
    @Test
    public void testCalculateQuality_HighTemperature() {
        // High temperature should cause faster decay
        double initialQuality = 100.0;
        double temperature = 15.0;  // 7°C above ideal max
        double humidity = 75.0;
        double hours = 10.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        // Should decay faster than ideal conditions
        assertTrue(finalQuality < 95.0, "Quality should decay faster at high temperature");
    }
    
    @Test
    public void testCalculateQuality_LowTemperature() {
        // Low temperature should also cause faster decay
        double initialQuality = 100.0;
        double temperature = 0.0;  // 4°C below ideal min
        double humidity = 75.0;
        double hours = 10.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        // Should decay faster than ideal conditions
        assertTrue(finalQuality < 95.0, "Quality should decay faster at low temperature");
    }
    
    @Test
    public void testCalculateQuality_HighHumidity() {
        // High humidity should cause faster decay
        double initialQuality = 100.0;
        double temperature = 5.0;
        double humidity = 95.0;  // 10% above ideal max
        double hours = 10.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        // Should decay faster than ideal conditions
        assertTrue(finalQuality < 95.0, "Quality should decay faster at high humidity");
    }
    
    @Test
    public void testCalculateQuality_LowHumidity() {
        // Low humidity should cause faster decay
        double initialQuality = 100.0;
        double temperature = 5.0;
        double humidity = 50.0;  // 20% below ideal min
        double hours = 10.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        // Should decay faster than ideal conditions
        assertTrue(finalQuality < 95.0, "Quality should decay faster at low humidity");
    }
    
    @Test
    public void testCalculateQuality_NeverNegative() {
        // Quality should never go below 0
        double initialQuality = 10.0;
        double temperature = 20.0;
        double humidity = 100.0;
        double hours = 100.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        assertTrue(finalQuality >= 0.0, "Quality should never be negative");
    }
    
    @Test
    public void testCalculateQuality_NeverExceedsInitial() {
        // Quality should never exceed initial value
        double initialQuality = 80.0;
        double temperature = 5.0;
        double humidity = 75.0;
        double hours = 0.0;
        
        double finalQuality = service.calculateQuality(initialQuality, temperature, humidity, hours);
        
        assertEquals(80.0, finalQuality, 0.01);
    }
    
    @Test
    public void testCalculateDecayRate_Ideal() {
        double temperature = 6.0;
        double humidity = 77.5;
        
        double decayRate = service.calculateDecayRate(temperature, humidity);
        
        // Should be base decay rate only
        assertEquals(0.5, decayRate, 0.01);
    }
    
    @Test
    public void testCalculateDecayRate_OutsideIdeal() {
        double temperature = 12.0;  // 4°C above max
        double humidity = 90.0;     // 5% above max
        
        double decayRate = service.calculateDecayRate(temperature, humidity);
        
        // Should be base + temp penalty + humidity penalty
        // 0.5 + (4 * 0.3) + (5 * 0.1) = 0.5 + 1.2 + 0.5 = 2.2
        assertEquals(2.2, decayRate, 0.01);
    }
    
    @Test
    public void testIsTemperatureIdeal() {
        assertTrue(service.isTemperatureIdeal(5.0));
        assertTrue(service.isTemperatureIdeal(6.0));
        assertTrue(service.isTemperatureIdeal(7.0));
        assertFalse(service.isTemperatureIdeal(3.0));
        assertFalse(service.isTemperatureIdeal(10.0));
    }
    
    @Test
    public void testIsHumidityIdeal() {
        assertTrue(service.isHumidityIdeal(75.0));
        assertTrue(service.isHumidityIdeal(80.0));
        assertFalse(service.isHumidityIdeal(60.0));
        assertFalse(service.isHumidityIdeal(90.0));
    }
    
    @Test
    public void testSimulateQualityTrace() {
        double initialQuality = 100.0;
        
        long startTime = System.currentTimeMillis();
        long oneHour = 3600 * 1000;
        
        List<EnvironmentalReading> readings = new ArrayList<>();
        readings.add(new EnvironmentalReading(startTime, 5.0, 75.0));
        readings.add(new EnvironmentalReading(startTime + oneHour, 6.0, 76.0));
        readings.add(new EnvironmentalReading(startTime + 2 * oneHour, 7.0, 77.0));
        
        List<QualityTracePoint> trace = service.simulateQualityTrace(initialQuality, readings);
        
        assertNotNull(trace);
        assertEquals(3, trace.size());
        
        // First point should be initial quality
        assertEquals(100.0, trace.get(0).getQuality(), 0.01);
        
        // Quality should decrease over time
        assertTrue(trace.get(1).getQuality() < trace.get(0).getQuality());
        assertTrue(trace.get(2).getQuality() < trace.get(1).getQuality());
        
        // Final quality should be deterministic
        double finalQuality = trace.get(2).getQuality();
        assertTrue(finalQuality < 100.0 && finalQuality > 90.0);
    }
    
    @Test
    public void testSimulateQualityTrace_EmptyReadings() {
        List<EnvironmentalReading> readings = new ArrayList<>();
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.simulateQualityTrace(100.0, readings);
        });
    }
    
    @Test
    public void testPredictQuality() {
        double currentQuality = 95.0;
        double temperature = 5.0;
        double humidity = 75.0;
        double hoursInFuture = 5.0;
        
        double predictedQuality = service.predictQuality(
            currentQuality, temperature, humidity, hoursInFuture);
        
        // Should be deterministic
        assertEquals(
            service.calculateQuality(currentQuality, temperature, humidity, hoursInFuture),
            predictedQuality,
            0.01
        );
    }
    
    @Test
    public void testGetIdealRanges() {
        double[] tempRange = QualityDecayService.getIdealTemperatureRange();
        assertEquals(4.0, tempRange[0], 0.01);
        assertEquals(8.0, tempRange[1], 0.01);
        
        double[] humidityRange = QualityDecayService.getIdealHumidityRange();
        assertEquals(70.0, humidityRange[0], 0.01);
        assertEquals(85.0, humidityRange[1], 0.01);
    }
}
