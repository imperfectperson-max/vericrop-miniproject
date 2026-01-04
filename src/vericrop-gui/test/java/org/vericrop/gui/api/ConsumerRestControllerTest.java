package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.api.ConsumerRestController.ComplianceStatus;
import org.vericrop.gui.api.ConsumerRestController.ViolationDetail;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ConsumerRestController
 */
class ConsumerRestControllerTest {
    
    private ConsumerRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ConsumerRestController();
    }
    
    @Test
    void testGetBatchCompliance_NonExistent() {
        ResponseEntity<ComplianceStatus> response = controller.getBatchCompliance("NON_EXISTENT");
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("NON_EXISTENT", response.getBody().getBatchId());
        assertNull(response.getBody().getCompliant());
        assertEquals("No compliance data available", response.getBody().getDetails());
    }
    
    @Test
    void testUpdateBatchCompliance() {
        ComplianceStatus status = new ComplianceStatus("TEST_BATCH_001", true, 0, "All checks passed");
        
        ResponseEntity<Void> updateResponse = controller.updateBatchCompliance("TEST_BATCH_001", status);
        
        assertEquals(200, updateResponse.getStatusCodeValue());
        
        // Verify status was stored
        ResponseEntity<ComplianceStatus> getResponse = controller.getBatchCompliance("TEST_BATCH_001");
        assertNotNull(getResponse.getBody());
        assertEquals("TEST_BATCH_001", getResponse.getBody().getBatchId());
        assertTrue(getResponse.getBody().getCompliant());
        assertEquals(0, getResponse.getBody().getViolationCount());
        assertEquals("All checks passed", getResponse.getBody().getDetails());
    }
    
    @Test
    void testUpdateBatchCompliance_WithViolations() {
        ComplianceStatus status = new ComplianceStatus("TEST_BATCH_002", false, 2, "Temperature violations detected");
        
        List<ViolationDetail> violations = new ArrayList<>();
        violations.add(new ViolationDetail(System.currentTimeMillis(), 10.5, 120, "Temp exceeded 10°C"));
        violations.add(new ViolationDetail(System.currentTimeMillis(), 9.8, 60, "Temp exceeded 9°C"));
        status.setViolations(violations);
        
        controller.updateBatchCompliance("TEST_BATCH_002", status);
        
        // Verify violations were stored
        ResponseEntity<ComplianceStatus> getResponse = controller.getBatchCompliance("TEST_BATCH_002");
        assertNotNull(getResponse.getBody());
        assertFalse(getResponse.getBody().getCompliant());
        assertEquals(2, getResponse.getBody().getViolationCount());
        assertEquals(2, getResponse.getBody().getViolations().size());
    }
    
    @Test
    void testGetAllBatchCompliance_Empty() {
        ResponseEntity<List<ComplianceStatus>> response = controller.getAllBatchCompliance();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
    
    @Test
    void testGetAllBatchCompliance_Multiple() throws InterruptedException {
        // Add multiple compliance statuses
        ComplianceStatus status1 = new ComplianceStatus("BATCH_001", true, 0, "Compliant");
        controller.updateBatchCompliance("BATCH_001", status1);
        
        Thread.sleep(10);
        
        ComplianceStatus status2 = new ComplianceStatus("BATCH_002", false, 1, "Minor violation");
        controller.updateBatchCompliance("BATCH_002", status2);
        
        Thread.sleep(10);
        
        ComplianceStatus status3 = new ComplianceStatus("BATCH_003", true, 0, "Compliant");
        controller.updateBatchCompliance("BATCH_003", status3);
        
        // Get all
        ResponseEntity<List<ComplianceStatus>> response = controller.getAllBatchCompliance();
        List<ComplianceStatus> statuses = response.getBody();
        
        assertEquals(3, statuses.size());
        // Should be sorted by timestamp descending
        assertEquals("BATCH_003", statuses.get(0).getBatchId());
        assertEquals("BATCH_002", statuses.get(1).getBatchId());
        assertEquals("BATCH_001", statuses.get(2).getBatchId());
    }
    
    @Test
    void testBatchIdOverride() {
        // Create status with different batch ID
        ComplianceStatus status = new ComplianceStatus("WRONG_ID", true, 0, "Test");
        
        // Should be overridden by path variable
        controller.updateBatchCompliance("CORRECT_ID", status);
        
        ResponseEntity<ComplianceStatus> response = controller.getBatchCompliance("CORRECT_ID");
        assertEquals("CORRECT_ID", response.getBody().getBatchId());
    }
}
