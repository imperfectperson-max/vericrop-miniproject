package org.vericrop.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimulationResult DTO.
 */
class SimulationResultTest {
    
    @Test
    void testDefaultConstructor() {
        SimulationResult result = new SimulationResult();
        
        assertNotNull(result.getTemperatureSeries());
        assertNotNull(result.getHumiditySeries());
        assertTrue(result.getTemperatureSeries().isEmpty());
        assertTrue(result.getHumiditySeries().isEmpty());
    }
    
    @Test
    void testAddTemperaturePoint() {
        SimulationResult result = new SimulationResult("BATCH_001", "FARMER_A");
        
        result.addTemperaturePoint(System.currentTimeMillis(), 4.5, "Highway Mile 10");
        result.addTemperaturePoint(System.currentTimeMillis() + 1000, 4.8, "Highway Mile 20");
        result.addTemperaturePoint(System.currentTimeMillis() + 2000, 5.0, "Highway Mile 30");
        
        assertEquals(3, result.getTemperatureSeries().size());
        assertEquals(4.5, result.getTemperatureSeries().get(0).getTemperature(), 0.01);
    }
    
    @Test
    void testAddHumidityPoint() {
        SimulationResult result = new SimulationResult("BATCH_001", "FARMER_A");
        
        result.addHumidityPoint(System.currentTimeMillis(), 65.0, "Location A");
        result.addHumidityPoint(System.currentTimeMillis() + 1000, 68.0, "Location B");
        
        assertEquals(2, result.getHumiditySeries().size());
        assertEquals(65.0, result.getHumiditySeries().get(0).getHumidity(), 0.01);
    }
    
    @Test
    void testCalculateStatistics_AllCompliant() {
        SimulationResult result = new SimulationResult("BATCH_001", "FARMER_A");
        
        // Add temperature points within compliant range (2-8°C)
        result.addTemperaturePoint(System.currentTimeMillis(), 4.0, "Loc 1");
        result.addTemperaturePoint(System.currentTimeMillis(), 5.0, "Loc 2");
        result.addTemperaturePoint(System.currentTimeMillis(), 6.0, "Loc 3");
        result.addTemperaturePoint(System.currentTimeMillis(), 4.5, "Loc 4");
        
        result.calculateStatistics();
        
        assertEquals(4, result.getWaypointsCount());
        assertEquals(4.875, result.getAvgTemperature(), 0.01);
        assertEquals(4.0, result.getMinTemperature(), 0.01);
        assertEquals(6.0, result.getMaxTemperature(), 0.01);
        assertEquals(0, result.getViolationsCount());
        assertEquals("COMPLIANT", result.getComplianceStatus());
    }
    
    @Test
    void testCalculateStatistics_WithViolations() {
        SimulationResult result = new SimulationResult("BATCH_002", "FARMER_B");
        
        // Add some temperature points outside compliant range
        result.addTemperaturePoint(System.currentTimeMillis(), 4.0, "Loc 1");
        result.addTemperaturePoint(System.currentTimeMillis(), 9.0, "Loc 2"); // Violation (> 8°C)
        result.addTemperaturePoint(System.currentTimeMillis(), 5.0, "Loc 3");
        result.addTemperaturePoint(System.currentTimeMillis(), 1.0, "Loc 4"); // Violation (< 2°C)
        
        result.calculateStatistics();
        
        assertEquals(4, result.getWaypointsCount());
        assertEquals(4.75, result.getAvgTemperature(), 0.01);
        assertEquals(1.0, result.getMinTemperature(), 0.01);
        assertEquals(9.0, result.getMaxTemperature(), 0.01);
        assertEquals(2, result.getViolationsCount());
        assertEquals("NON_COMPLIANT", result.getComplianceStatus());
    }
    
    @Test
    void testCalculateStatistics_WithHumidity() {
        SimulationResult result = new SimulationResult("BATCH_003", "FARMER_C");
        
        result.addTemperaturePoint(System.currentTimeMillis(), 5.0, "Loc 1");
        result.addHumidityPoint(System.currentTimeMillis(), 60.0, "Loc 1");
        result.addHumidityPoint(System.currentTimeMillis(), 70.0, "Loc 2");
        result.addHumidityPoint(System.currentTimeMillis(), 65.0, "Loc 3");
        
        result.calculateStatistics();
        
        assertEquals(65.0, result.getAvgHumidity(), 0.01);
    }
    
    @Test
    void testFullConstructor() {
        SimulationResult.TemperatureDataPoint tempPoint = 
                new SimulationResult.TemperatureDataPoint(System.currentTimeMillis(), 4.5, "Highway");
        
        assertEquals(4.5, tempPoint.getTemperature(), 0.01);
        assertEquals("Highway", tempPoint.getLocation());
    }
    
    @Test
    void testSettersAndGetters() {
        SimulationResult result = new SimulationResult();
        
        result.setBatchId("BATCH_TEST");
        result.setFarmerId("FARMER_TEST");
        result.setStartTime(1000L);
        result.setEndTime(2000L);
        result.setStatus("COMPLETED");
        result.setFinalQuality(95.0);
        
        assertEquals("BATCH_TEST", result.getBatchId());
        assertEquals("FARMER_TEST", result.getFarmerId());
        assertEquals(1000L, result.getStartTime());
        assertEquals(2000L, result.getEndTime());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(95.0, result.getFinalQuality(), 0.01);
    }
    
    @Test
    void testEmptySeriesCalculateStatistics() {
        SimulationResult result = new SimulationResult("BATCH_EMPTY", "FARMER_X");
        
        // Should not throw exception
        assertDoesNotThrow(() -> result.calculateStatistics());
        
        assertEquals(0, result.getWaypointsCount());
    }
    
    @Test
    void testEqualsAndHashCode() {
        SimulationResult result1 = new SimulationResult("BATCH_EQ", "FARMER_EQ");
        result1.setStartTime(1000L);
        result1.setEndTime(2000L);
        
        SimulationResult result2 = new SimulationResult("BATCH_EQ", "FARMER_EQ");
        result2.setStartTime(1000L);
        result2.setEndTime(2000L);
        
        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }
    
    @Test
    void testToString() {
        SimulationResult result = new SimulationResult("BATCH_STR", "FARMER_STR");
        result.setStatus("COMPLETED");
        result.setFinalQuality(90.0);
        
        String str = result.toString();
        
        assertTrue(str.contains("BATCH_STR"));
        assertTrue(str.contains("COMPLETED"));
    }
    
    @Test
    void testTemperatureDataPointToString() {
        SimulationResult.TemperatureDataPoint point = 
                new SimulationResult.TemperatureDataPoint(1000L, 5.5, "Test Location");
        
        String str = point.toString();
        assertTrue(str.contains("5.5"));
        assertTrue(str.contains("Test Location"));
    }
    
    @Test
    void testHumidityDataPointToString() {
        SimulationResult.HumidityDataPoint point = 
                new SimulationResult.HumidityDataPoint(1000L, 70.5, "Test Location");
        
        String str = point.toString();
        assertTrue(str.contains("70.5"));
        assertTrue(str.contains("Test Location"));
    }
}
