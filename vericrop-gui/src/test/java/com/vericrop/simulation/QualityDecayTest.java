package com.vericrop.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for quality decay calculation.
 * Tests the exponential decay formula: finalQuality = initialQuality * exp(-decayRate * exposureSeconds)
 */
@DisplayName("Quality Decay Tests")
class QualityDecayTest {
    
    // Tolerance for floating-point comparisons
    private static final double EPSILON = 0.001;
    
    @Test
    @DisplayName("Quality at time zero equals initial quality")
    void qualityAtZeroTimeEqualsInitial() {
        double initialQuality = 100.0;
        double decayRate = 0.001;
        double exposureSeconds = 0;
        
        double result = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, exposureSeconds
        );
        
        assertEquals(initialQuality, result, EPSILON,
            "Quality at time zero should equal initial quality");
    }
    
    @Test
    @DisplayName("Quality decreases over time")
    void qualityDecreasesOverTime() {
        double initialQuality = 100.0;
        double decayRate = 0.001;
        
        double quality1Min = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 60
        );
        double quality5Min = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 300
        );
        double quality10Min = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 600
        );
        
        assertTrue(quality1Min < initialQuality, "Quality should decrease after 1 minute");
        assertTrue(quality5Min < quality1Min, "Quality after 5 minutes should be less than 1 minute");
        assertTrue(quality10Min < quality5Min, "Quality after 10 minutes should be less than 5 minutes");
    }
    
    @Test
    @DisplayName("Quality never goes below zero")
    void qualityNeverBelowZero() {
        double initialQuality = 100.0;
        double decayRate = 0.1; // High decay rate
        double veryLongTime = 100000; // Very long exposure
        
        double result = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, veryLongTime
        );
        
        assertTrue(result >= 0, "Quality should never be negative");
    }
    
    @Test
    @DisplayName("Quality never exceeds 100")
    void qualityNeverExceeds100() {
        double initialQuality = 100.0;
        double decayRate = 0.001;
        
        double result = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 60
        );
        
        assertTrue(result <= 100, "Quality should never exceed 100");
    }
    
    @Test
    @DisplayName("Zero decay rate means no quality loss")
    void zeroDecayRateNoLoss() {
        double initialQuality = 100.0;
        double decayRate = 0;
        double anyTime = 3600; // 1 hour
        
        double result = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, anyTime
        );
        
        assertEquals(initialQuality, result, EPSILON,
            "With zero decay rate, quality should not change");
    }
    
    @ParameterizedTest
    @DisplayName("Exponential decay formula verification")
    @CsvSource({
        "100.0, 0.001, 60, 94.176", // exp(-0.001 * 60) ≈ 0.94176
        "100.0, 0.001, 300, 74.082", // exp(-0.001 * 300) ≈ 0.74082
        "100.0, 0.01, 60, 54.881", // exp(-0.01 * 60) ≈ 0.54881
        "80.0, 0.001, 120, 71.041", // 80 * exp(-0.001 * 120) ≈ 71.041
    })
    void exponentialDecayFormulaVerification(double initial, double rate, double time, double expected) {
        double result = EnhancedDeliverySimulator.calculateQualityDecay(initial, rate, time);
        assertEquals(expected, result, 0.1, 
            String.format("Expected %.3f for initial=%.1f, rate=%.3f, time=%.0f", 
                         expected, initial, rate, time));
    }
    
    @Test
    @DisplayName("Higher decay rate causes faster quality loss")
    void higherDecayRateFasterLoss() {
        double initialQuality = 100.0;
        double exposureSeconds = 60;
        
        double qualityLowDecay = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, 0.001, exposureSeconds
        );
        double qualityHighDecay = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, 0.01, exposureSeconds
        );
        
        assertTrue(qualityHighDecay < qualityLowDecay,
            "Higher decay rate should result in lower quality");
    }
    
    @Test
    @DisplayName("Negative exposure time throws exception")
    void negativeExposureTimeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            EnhancedDeliverySimulator.calculateQualityDecay(100.0, 0.001, -60);
        }, "Negative exposure time should throw exception");
    }
    
    @Test
    @DisplayName("Negative decay rate throws exception")
    void negativeDecayRateThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            EnhancedDeliverySimulator.calculateQualityDecay(100.0, -0.001, 60);
        }, "Negative decay rate should throw exception");
    }
    
    @ParameterizedTest
    @DisplayName("Various initial qualities decay correctly")
    @ValueSource(doubles = {100.0, 90.0, 80.0, 70.0, 50.0})
    void variousInitialQualitiesDecay(double initialQuality) {
        double decayRate = 0.001;
        double exposureSeconds = 120;
        
        double result = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, exposureSeconds
        );
        
        // Calculate expected value
        double expected = initialQuality * Math.exp(-decayRate * exposureSeconds);
        
        assertEquals(expected, result, EPSILON,
            "Quality decay should match exponential formula for initial=" + initialQuality);
    }
    
    @Test
    @DisplayName("Demo mode decay rate results in visible change in 3-4 minutes")
    void demoModeDecayRateVisibleChange() {
        SimulationConfig demoConfig = SimulationConfig.createDemoMode();
        double decayRate = demoConfig.getDecayRate();
        double initialQuality = demoConfig.getInitialQuality();
        
        // After 3 minutes (180 seconds)
        double quality3Min = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 180
        );
        
        // After 4 minutes (240 seconds)
        double quality4Min = EnhancedDeliverySimulator.calculateQualityDecay(
            initialQuality, decayRate, 240
        );
        
        // Quality should have dropped noticeably (at least 5%)
        double dropPercent3Min = 100 - quality3Min;
        double dropPercent4Min = 100 - quality4Min;
        
        System.out.println("Demo mode quality drop after 3 min: " + String.format("%.1f%%", dropPercent3Min));
        System.out.println("Demo mode quality drop after 4 min: " + String.format("%.1f%%", dropPercent4Min));
        
        assertTrue(dropPercent3Min > 5, 
            "Demo mode should show at least 5% quality drop in 3 minutes");
        assertTrue(quality3Min > 60, 
            "Quality should remain above 60% after 3 minutes in demo mode");
    }
}
