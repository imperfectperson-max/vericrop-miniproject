package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.api.AnalyticsRestController.BatchInfo;
import org.vericrop.gui.api.AnalyticsRestController.ComplianceInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for AnalyticsRestController
 * Note: Tests use no-arg constructor which skips authentication checks
 */
class AnalyticsRestControllerTest {
    
    private AnalyticsRestController controller;
    
    @BeforeEach
    void setUp() {
        // Use no-arg constructor for test mode (skips authentication)
        controller = new AnalyticsRestController();
    }
    
    @Test
    void testGetRecentBatches_Empty() {
        ResponseEntity<?> response = controller.getRecentBatches(null);
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        
        // Safe cast after instanceof check
        assertTrue(response.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) response.getBody();
        assertTrue(batches.isEmpty());
    }
    
    @Test
    void testUpdateRecentBatches() {
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("IN_TRANSIT");
        batchInfo.setLocation("Farm Location");
        
        ResponseEntity<?> updateResponse = controller.updateRecentBatches(null, batchInfo);
        
        assertEquals(200, updateResponse.getStatusCodeValue());
        
        // Verify batch was added
        ResponseEntity<?> getResponse = controller.getRecentBatches(null);
        assertNotNull(getResponse.getBody());
        assertTrue(getResponse.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) getResponse.getBody();
        assertEquals(1, batches.size());
        assertEquals("TEST_BATCH_001", batches.get(0).getBatchId());
    }
    
    @Test
    void testUpdateBatchStatus_Existing() {
        // First add a batch
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("CREATED");
        controller.updateRecentBatches(null, batchInfo);
        
        // Update status
        ResponseEntity<?> response = controller.updateBatchStatus(null, "TEST_BATCH_001", "DELIVERED");
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify status was updated
        ResponseEntity<?> getResponse = controller.getRecentBatches(null);
        assertTrue(getResponse.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) getResponse.getBody();
        assertEquals("DELIVERED", batches.get(0).getStatus());
    }
    
    @Test
    void testUpdateBatchStatus_NonExistent() {
        ResponseEntity<?> response = controller.updateBatchStatus(null, "NON_EXISTENT", "DELIVERED");
        
        assertEquals(404, response.getStatusCodeValue());
    }
    
    @Test
    void testUpdateComplianceStatus_Existing() {
        // First add a batch
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("IN_TRANSIT");
        controller.updateRecentBatches(null, batchInfo);
        
        // Update compliance
        ComplianceInfo compliance = new ComplianceInfo(true, 0);
        ResponseEntity<?> response = controller.updateComplianceStatus(null, "TEST_BATCH_001", compliance);
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify compliance was updated
        ResponseEntity<?> getResponse = controller.getRecentBatches(null);
        assertTrue(getResponse.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) getResponse.getBody();
        assertTrue(batches.get(0).getCompliant());
        assertEquals(0, batches.get(0).getViolationCount());
    }
    
    @Test
    void testUpdateComplianceStatus_NonExistent() {
        // Should create new batch
        ComplianceInfo compliance = new ComplianceInfo(false, 3);
        ResponseEntity<?> response = controller.updateComplianceStatus(null, "NEW_BATCH", compliance);
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify batch was created with compliance
        ResponseEntity<?> getResponse = controller.getRecentBatches(null);
        assertTrue(getResponse.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) getResponse.getBody();
        assertEquals(1, batches.size());
        assertEquals("NEW_BATCH", batches.get(0).getBatchId());
        assertFalse(batches.get(0).getCompliant());
        assertEquals(3, batches.get(0).getViolationCount());
    }
    
    @Test
    void testMultipleBatches_SortedByTimestamp() throws InterruptedException {
        // Add multiple batches with delays to ensure different timestamps
        BatchInfo batch1 = new BatchInfo();
        batch1.setBatchId("BATCH_001");
        controller.updateRecentBatches(null, batch1);
        
        Thread.sleep(10);
        
        BatchInfo batch2 = new BatchInfo();
        batch2.setBatchId("BATCH_002");
        controller.updateRecentBatches(null, batch2);
        
        Thread.sleep(10);
        
        BatchInfo batch3 = new BatchInfo();
        batch3.setBatchId("BATCH_003");
        controller.updateRecentBatches(null, batch3);
        
        // Should be sorted by timestamp descending (most recent first)
        ResponseEntity<?> response = controller.getRecentBatches(null);
        assertTrue(response.getBody() instanceof java.util.List);
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = (List<BatchInfo>) response.getBody();
        
        assertEquals(3, batches.size());
        assertEquals("BATCH_003", batches.get(0).getBatchId());
        assertEquals("BATCH_002", batches.get(1).getBatchId());
        assertEquals("BATCH_001", batches.get(2).getBatchId());
    }
}
