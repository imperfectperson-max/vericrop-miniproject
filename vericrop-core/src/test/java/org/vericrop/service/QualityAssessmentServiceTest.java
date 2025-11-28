package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.GeoCoordinate;
import org.vericrop.service.models.RouteWaypoint;
import org.vericrop.service.models.Scenario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QualityAssessmentService.
 * Tests final quality computation based on sensor data aggregation.
 */
public class QualityAssessmentServiceTest {

    private QualityAssessmentService qualityAssessmentService;
    private QualityDecayService qualityDecayService;
    private TemperatureService temperatureService;
    private AlertService alertService;
    private MapService mapService;

    @BeforeEach
    public void setUp() {
        qualityDecayService = new QualityDecayService();
        temperatureService = new TemperatureService();
        alertService = new AlertService();
        mapService = new MapService();
        qualityAssessmentService = new QualityAssessmentService(
            qualityDecayService, temperatureService, alertService);
    }

    @Test
    public void testAssessFinalQuality_IdealConditions() {
        // Test with ideal temperature and humidity conditions
        String batchId = "TEST_BATCH_001";
        double initialQuality = 100.0;
        
        // Generate route with ideal conditions
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 10, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL);

        QualityAssessmentService.FinalQualityAssessment assessment = 
            qualityAssessmentService.assessFinalQuality(batchId, initialQuality, route);

        assertNotNull(assessment);
        assertEquals(batchId, assessment.getBatchId());
        assertEquals(initialQuality, assessment.getInitialQuality());
        assertTrue(assessment.getFinalQuality() > 0);
        assertTrue(assessment.getFinalQuality() <= initialQuality);
        assertNotNull(assessment.getQualityGrade());
        assertTrue(assessment.getTransitTimeMs() >= 0);
    }

    @Test
    public void testAssessFinalQuality_TemperatureViolations() {
        // Test with high temperature scenario
        String batchId = "TEST_BATCH_002";
        double initialQuality = 100.0;
        
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        // Generate route with hot transport scenario
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 10, 
            System.currentTimeMillis(), 50.0, Scenario.HOT_TRANSPORT);

        QualityAssessmentService.FinalQualityAssessment assessment = 
            qualityAssessmentService.assessFinalQuality(batchId, initialQuality, route);

        assertNotNull(assessment);
        // Hot transport should cause temperature violations
        assertTrue(assessment.getTemperatureViolations() > 0);
        // Final quality should be lower than ideal conditions would produce
        assertTrue(assessment.getFinalQuality() < 100.0);
    }

    @Test
    public void testAssessFinalQuality_EmptyRoute() {
        String batchId = "TEST_BATCH_003";
        double initialQuality = 100.0;

        // Test with empty route - should return default assessment
        QualityAssessmentService.FinalQualityAssessment assessment = 
            qualityAssessmentService.assessFinalQuality(batchId, initialQuality, null);

        assertNotNull(assessment);
        assertEquals(batchId, assessment.getBatchId());
        assertEquals(initialQuality, assessment.getInitialQuality());
        assertTrue(assessment.getFinalQuality() > 0);
    }

    @Test
    public void testQualityGrades() {
        String batchId = "TEST_BATCH_GRADE";
        
        // Test PRIME grade (>=90%)
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        List<RouteWaypoint> idealRoute = mapService.generateRoute(
            batchId, origin, destination, 3, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL);

        QualityAssessmentService.FinalQualityAssessment primeAssessment = 
            qualityAssessmentService.assessFinalQuality(batchId + "_PRIME", 100.0, idealRoute);
        
        // With short ideal route, quality should remain high
        assertTrue(primeAssessment.isPrimeQuality() || primeAssessment.isAcceptableQuality());
    }

    @Test
    public void testAssessFinalQualityFromMonitoring() {
        String batchId = "TEST_BATCH_MONITORING";
        double initialQuality = 100.0;
        long startTime = System.currentTimeMillis() - 3600000; // 1 hour ago

        // Start temperature monitoring
        temperatureService.startMonitoring(batchId);

        // Generate and record route for monitoring
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 10, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL);
        
        temperatureService.recordRoute(batchId, route);

        // Assess quality using monitoring data
        QualityAssessmentService.FinalQualityAssessment assessment = 
            qualityAssessmentService.assessFinalQualityFromMonitoring(batchId, initialQuality, startTime);

        assertNotNull(assessment);
        assertEquals(batchId, assessment.getBatchId());
        assertTrue(assessment.getFinalQuality() > 0);
        assertTrue(assessment.getAvgTemperature() > 0);

        // Stop monitoring
        temperatureService.stopMonitoring(batchId);
    }

    @Test
    public void testCachedAssessment() {
        String batchId = "TEST_BATCH_CACHE";
        double initialQuality = 95.0;

        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 5, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL);

        // First assessment
        QualityAssessmentService.FinalQualityAssessment assessment1 = 
            qualityAssessmentService.assessFinalQuality(batchId, initialQuality, route);

        // Get cached assessment
        QualityAssessmentService.FinalQualityAssessment cached = 
            qualityAssessmentService.getCachedAssessment(batchId);

        assertNotNull(cached);
        assertEquals(assessment1.getBatchId(), cached.getBatchId());
        assertEquals(assessment1.getFinalQuality(), cached.getFinalQuality(), 0.001);

        // Clear cache
        qualityAssessmentService.clearCachedAssessment(batchId);
        assertNull(qualityAssessmentService.getCachedAssessment(batchId));
    }

    @Test
    public void testFinalQualityAssessment_ToString() {
        String batchId = "TEST_BATCH_STRING";
        
        GeoCoordinate origin = new GeoCoordinate(40.7128, -74.0060, "Farm");
        GeoCoordinate destination = new GeoCoordinate(40.7489, -73.9680, "Warehouse");
        
        List<RouteWaypoint> route = mapService.generateRoute(
            batchId, origin, destination, 5, 
            System.currentTimeMillis(), 50.0, Scenario.NORMAL);

        QualityAssessmentService.FinalQualityAssessment assessment = 
            qualityAssessmentService.assessFinalQuality(batchId, 100.0, route);

        String toString = assessment.toString();
        assertNotNull(toString);
        assertTrue(toString.contains(batchId));
        assertTrue(toString.contains("finalQuality"));
        assertTrue(toString.contains("grade"));
    }
}
