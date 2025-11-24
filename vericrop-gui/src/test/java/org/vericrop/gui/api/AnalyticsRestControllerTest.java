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
 */
class AnalyticsRestControllerTest {
    
    private AnalyticsRestController controller;
    
    @BeforeEach
    void setUp() {
        controller = new AnalyticsRestController();
    }
    
    @Test
    void testGetRecentBatches_Empty() {
        ResponseEntity<List<BatchInfo>> response = controller.getRecentBatches();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
    
    @Test
    void testUpdateRecentBatches() {
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("IN_TRANSIT");
        batchInfo.setLocation("Farm Location");
        
        ResponseEntity<Void> updateResponse = controller.updateRecentBatches(batchInfo);
        
        assertEquals(200, updateResponse.getStatusCodeValue());
        
        // Verify batch was added
        ResponseEntity<List<BatchInfo>> getResponse = controller.getRecentBatches();
        assertNotNull(getResponse.getBody());
        assertEquals(1, getResponse.getBody().size());
        assertEquals("TEST_BATCH_001", getResponse.getBody().get(0).getBatchId());
    }
    
    @Test
    void testUpdateBatchStatus_Existing() {
        // First add a batch
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("CREATED");
        controller.updateRecentBatches(batchInfo);
        
        // Update status
        ResponseEntity<Void> response = controller.updateBatchStatus("TEST_BATCH_001", "DELIVERED");
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify status was updated
        ResponseEntity<List<BatchInfo>> getResponse = controller.getRecentBatches();
        assertEquals("DELIVERED", getResponse.getBody().get(0).getStatus());
    }
    
    @Test
    void testUpdateBatchStatus_NonExistent() {
        ResponseEntity<Void> response = controller.updateBatchStatus("NON_EXISTENT", "DELIVERED");
        
        assertEquals(404, response.getStatusCodeValue());
    }
    
    @Test
    void testUpdateComplianceStatus_Existing() {
        // First add a batch
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        batchInfo.setStatus("IN_TRANSIT");
        controller.updateRecentBatches(batchInfo);
        
        // Update compliance
        ComplianceInfo compliance = new ComplianceInfo(true, 0);
        ResponseEntity<Void> response = controller.updateComplianceStatus("TEST_BATCH_001", compliance);
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify compliance was updated
        ResponseEntity<List<BatchInfo>> getResponse = controller.getRecentBatches();
        assertTrue(getResponse.getBody().get(0).getCompliant());
        assertEquals(0, getResponse.getBody().get(0).getViolationCount());
    }
    
    @Test
    void testUpdateComplianceStatus_NonExistent() {
        // Should create new batch
        ComplianceInfo compliance = new ComplianceInfo(false, 3);
        ResponseEntity<Void> response = controller.updateComplianceStatus("NEW_BATCH", compliance);
        
        assertEquals(200, response.getStatusCodeValue());
        
        // Verify batch was created with compliance
        ResponseEntity<List<BatchInfo>> getResponse = controller.getRecentBatches();
        assertEquals(1, getResponse.getBody().size());
        assertEquals("NEW_BATCH", getResponse.getBody().get(0).getBatchId());
        assertFalse(getResponse.getBody().get(0).getCompliant());
        assertEquals(3, getResponse.getBody().get(0).getViolationCount());
    }
    
    @Test
    void testMultipleBatches_SortedByTimestamp() throws InterruptedException {
        // Add multiple batches with delays to ensure different timestamps
        BatchInfo batch1 = new BatchInfo();
        batch1.setBatchId("BATCH_001");
        controller.updateRecentBatches(batch1);
        
        Thread.sleep(10);
        
        BatchInfo batch2 = new BatchInfo();
        batch2.setBatchId("BATCH_002");
        controller.updateRecentBatches(batch2);
        
        Thread.sleep(10);
        
        BatchInfo batch3 = new BatchInfo();
        batch3.setBatchId("BATCH_003");
        controller.updateRecentBatches(batch3);
        
        // Should be sorted by timestamp descending (most recent first)
        ResponseEntity<List<BatchInfo>> response = controller.getRecentBatches();
        List<BatchInfo> batches = response.getBody();
        
        assertEquals(3, batches.size());
        assertEquals("BATCH_003", batches.get(0).getBatchId());
        assertEquals("BATCH_002", batches.get(1).getBatchId());
        assertEquals("BATCH_001", batches.get(2).getBatchId());
    }
}
