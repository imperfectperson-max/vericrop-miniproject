package org.vericrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.service.models.SupplierMetrics;

import static org.junit.jupiter.api.Assertions.*;

class SupplierComplianceServiceTest {
    
    private SupplierComplianceService complianceService;
    
    @BeforeEach
    void setUp() {
        complianceService = new SupplierComplianceService();
    }
    
    @Test
    void testRecordDeliveryCompliant() {
        String farmerId = "FARMER_001";
        
        // Record 10 successful deliveries
        for (int i = 0; i < 10; i++) {
            complianceService.recordDelivery(farmerId, true, false, 5.0, 2.0);
        }
        
        SupplierComplianceService.SupplierComplianceData data = complianceService.getComplianceData(farmerId);
        
        assertNotNull(data);
        assertEquals(farmerId, data.getFarmerId());
        assertEquals(10, data.getTotalDeliveries());
        assertEquals(10, data.getSuccessfulDeliveries());
        assertEquals(0, data.getSpoiledShipments());
        assertTrue(data.isCompliant());
        assertEquals("COMPLIANT", data.getComplianceStatus());
        assertEquals(1.0, data.getSuccessRate(), 0.01);
        assertEquals(0.0, data.getSpoilageRate(), 0.01);
    }
    
    @Test
    void testRecordDeliveryWarning() {
        String farmerId = "FARMER_002";
        
        // Record 8 successful, 2 failed
        for (int i = 0; i < 8; i++) {
            complianceService.recordDelivery(farmerId, true, false, 5.0, 2.0);
        }
        for (int i = 0; i < 2; i++) {
            complianceService.recordDelivery(farmerId, false, true, 25.0, 3.0);
        }
        
        SupplierComplianceService.SupplierComplianceData data = complianceService.getComplianceData(farmerId);
        
        assertNotNull(data);
        assertEquals(10, data.getTotalDeliveries());
        assertEquals(8, data.getSuccessfulDeliveries());
        assertEquals(2, data.getSpoiledShipments());
        assertFalse(data.isCompliant());
        assertEquals("WARNING", data.getComplianceStatus());
        assertEquals(0.8, data.getSuccessRate(), 0.01);
        assertEquals(0.2, data.getSpoilageRate(), 0.01);
    }
    
    @Test
    void testRecordDeliveryNonCompliant() {
        String farmerId = "FARMER_003";
        
        // Record 5 successful, 5 failed (50% success rate)
        for (int i = 0; i < 5; i++) {
            complianceService.recordDelivery(farmerId, true, false, 5.0, 2.0);
        }
        for (int i = 0; i < 5; i++) {
            complianceService.recordDelivery(farmerId, false, true, 30.0, 4.0);
        }
        
        SupplierComplianceService.SupplierComplianceData data = complianceService.getComplianceData(farmerId);
        
        assertNotNull(data);
        assertEquals(10, data.getTotalDeliveries());
        assertEquals(5, data.getSuccessfulDeliveries());
        assertEquals(5, data.getSpoiledShipments());
        assertFalse(data.isCompliant());
        assertEquals("NON_COMPLIANT", data.getComplianceStatus());
        assertEquals(0.5, data.getSuccessRate(), 0.01);
        assertEquals(0.5, data.getSpoilageRate(), 0.01);
    }
    
    @Test
    void testGetSupplierMetrics() {
        String farmerId = "FARMER_004";
        
        complianceService.recordDelivery(farmerId, true, false, 10.0, 2.5);
        complianceService.recordDelivery(farmerId, true, false, 15.0, 3.0);
        
        SupplierMetrics metrics = complianceService.getSupplierMetrics(farmerId);
        
        assertNotNull(metrics);
        assertEquals(farmerId, metrics.getFarmerId());
        assertEquals(2, metrics.getTotalDeliveries());
        assertEquals(2, metrics.getSuccessfulDeliveries());
        assertEquals(0, metrics.getSpoiledShipments());
        assertEquals(12.5, metrics.getAverageQualityDecay(), 0.01);
        assertEquals(2.75, metrics.getAverageDeliveryTime(), 0.01);
    }
    
    @Test
    void testIsSupplierCompliant() {
        String compliantFarmer = "FARMER_005";
        String nonCompliantFarmer = "FARMER_006";
        
        // Compliant supplier
        for (int i = 0; i < 10; i++) {
            complianceService.recordDelivery(compliantFarmer, true, false, 5.0, 2.0);
        }
        
        // Non-compliant supplier
        for (int i = 0; i < 5; i++) {
            complianceService.recordDelivery(nonCompliantFarmer, false, true, 30.0, 4.0);
        }
        
        assertTrue(complianceService.isSupplierCompliant(compliantFarmer));
        assertFalse(complianceService.isSupplierCompliant(nonCompliantFarmer));
    }
    
    @Test
    void testGetAllSupplierData() {
        complianceService.recordDelivery("FARMER_007", true, false, 5.0, 2.0);
        complianceService.recordDelivery("FARMER_008", true, false, 5.0, 2.0);
        
        assertEquals(2, complianceService.getAllSupplierData().size());
        assertTrue(complianceService.getAllSupplierData().containsKey("FARMER_007"));
        assertTrue(complianceService.getAllSupplierData().containsKey("FARMER_008"));
    }
    
    @Test
    void testClearSupplierData() {
        String farmerId = "FARMER_009";
        
        complianceService.recordDelivery(farmerId, true, false, 5.0, 2.0);
        assertNotNull(complianceService.getComplianceData(farmerId));
        
        complianceService.clearSupplierData(farmerId);
        assertNull(complianceService.getComplianceData(farmerId));
    }
    
    @Test
    void testAverageCalculations() {
        String farmerId = "FARMER_010";
        
        complianceService.recordDelivery(farmerId, true, false, 10.0, 2.0);
        complianceService.recordDelivery(farmerId, true, false, 20.0, 4.0);
        complianceService.recordDelivery(farmerId, true, false, 15.0, 3.0);
        
        SupplierComplianceService.SupplierComplianceData data = complianceService.getComplianceData(farmerId);
        
        assertEquals(15.0, data.getAverageQualityDecay(), 0.01);
        assertEquals(3.0, data.getAverageDeliveryTime(), 0.01);
    }
}
