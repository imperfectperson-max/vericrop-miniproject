package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.api.AnalyticsRestController.BatchInfo;
import org.vericrop.gui.api.AnalyticsRestController.CallbackRequest;
import org.vericrop.gui.api.AnalyticsRestController.ComplianceInfo;
import org.vericrop.gui.api.AnalyticsRestController.TriggerRequest;
import org.vericrop.gui.models.AnalyticsJob;
import org.vericrop.gui.services.AnalyticsWorkflowService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test for AnalyticsRestController
 */
class AnalyticsRestControllerTest {
    
    private AnalyticsRestController controller;
    private AnalyticsWorkflowService mockAnalyticsService;
    
    @BeforeEach
    void setUp() {
        mockAnalyticsService = Mockito.mock(AnalyticsWorkflowService.class);
        controller = new AnalyticsRestController(mockAnalyticsService);
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
    
    // ============================================================================
    // New Analytics Workflow Endpoint Tests
    // ============================================================================
    
    @Test
    void testTriggerAnalyticsJob_Success() {
        // Setup mock
        AnalyticsJob mockJob = new AnalyticsJob();
        mockJob.setJobId("job-123");
        mockJob.setDatasetId("dataset-456");
        mockJob.setAirflowRunId("airflow-run-789");
        mockJob.setStatus(AnalyticsJob.JobStatus.RUNNING);
        mockJob.setCreatedAt(Instant.now());
        
        when(mockAnalyticsService.triggerAnalyticsJob(anyString(), anyMap(), anyString()))
                .thenReturn(mockJob);
        
        // Create request
        TriggerRequest request = new TriggerRequest();
        request.setDatasetId("dataset-456");
        request.setParameters(Map.of("param1", "value1"));
        request.setCallbackUrl("http://callback.example.com");
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.triggerAnalyticsJob(request);
        
        // Assert
        assertEquals(201, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("job_id"));
        assertEquals("airflow-run-789", response.getBody().get("airflow_run_id"));
        assertEquals("RUNNING", response.getBody().get("status"));
        
        // Verify service was called
        verify(mockAnalyticsService).triggerAnalyticsJob(
                eq("dataset-456"),
                anyMap(),
                eq("http://callback.example.com")
        );
    }
    
    @Test
    void testTriggerAnalyticsJob_Failure() {
        // Setup mock to throw exception
        when(mockAnalyticsService.triggerAnalyticsJob(anyString(), anyMap(), anyString()))
                .thenThrow(new RuntimeException("Airflow connection failed"));
        
        TriggerRequest request = new TriggerRequest();
        request.setDatasetId("dataset-456");
        request.setParameters(Map.of("param1", "value1"));
        
        ResponseEntity<Map<String, Object>> response = controller.triggerAnalyticsJob(request);
        
        assertEquals(500, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("Failed to trigger analytics job", response.getBody().get("error"));
    }
    
    @Test
    void testGetJobStatus_Success() {
        // Setup mock
        AnalyticsJob mockJob = new AnalyticsJob();
        mockJob.setJobId("job-123");
        mockJob.setStatus(AnalyticsJob.JobStatus.RUNNING);
        mockJob.setAirflowRunId("airflow-run-789");
        mockJob.setCreatedAt(Instant.now());
        mockJob.setUpdatedAt(Instant.now());
        
        when(mockAnalyticsService.getJobStatus("job-123")).thenReturn(mockJob);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getJobStatus("job-123");
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("job_id"));
        assertEquals("running", response.getBody().get("status"));
        assertEquals("airflow-run-789", response.getBody().get("airflow_run_id"));
    }
    
    @Test
    void testGetJobStatus_NotFound() {
        when(mockAnalyticsService.getJobStatus("non-existent"))
                .thenThrow(new IllegalArgumentException("Job not found: non-existent"));
        
        ResponseEntity<Map<String, Object>> response = controller.getJobStatus("non-existent");
        
        assertEquals(404, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("Job not found", response.getBody().get("error"));
    }
    
    @Test
    void testGetJobResults_Success() {
        // Setup mock
        AnalyticsJob mockJob = new AnalyticsJob();
        mockJob.setJobId("job-123");
        mockJob.setStatus(AnalyticsJob.JobStatus.SUCCESS);
        Map<String, Object> results = new HashMap<>();
        results.put("accuracy", 0.95);
        results.put("records_processed", 1000);
        mockJob.setResults(results);
        mockJob.setResultsLink("http://results.example.com/job-123");
        
        when(mockAnalyticsService.getJobResults("job-123")).thenReturn(mockJob);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getJobResults("job-123");
        
        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("job_id"));
        assertEquals("success", response.getBody().get("status"));
        assertEquals(results, response.getBody().get("results"));
        assertEquals("http://results.example.com/job-123", response.getBody().get("results_link"));
    }
    
    @Test
    void testGetJobResults_NotReady() {
        // Setup mock
        AnalyticsJob mockJob = new AnalyticsJob();
        mockJob.setJobId("job-123");
        mockJob.setStatus(AnalyticsJob.JobStatus.RUNNING);
        
        when(mockAnalyticsService.getJobResults("job-123")).thenReturn(mockJob);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getJobResults("job-123");
        
        // Assert
        assertEquals(202, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("job_id"));
        assertEquals("running", response.getBody().get("status"));
        assertEquals("Results not yet available", response.getBody().get("message"));
    }
    
    @Test
    void testGetJobResults_NotFound() {
        when(mockAnalyticsService.getJobResults("non-existent"))
                .thenThrow(new IllegalArgumentException("Job not found: non-existent"));
        
        ResponseEntity<Map<String, Object>> response = controller.getJobResults("non-existent");
        
        assertEquals(404, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("Job not found", response.getBody().get("error"));
    }
    
    @Test
    void testJobCallback_Success() {
        doNothing().when(mockAnalyticsService).updateJobViaCallback(anyString(), anyString(), anyMap());
        
        CallbackRequest request = new CallbackRequest();
        request.setStatus("SUCCESS");
        Map<String, Object> results = new HashMap<>();
        results.put("completed", true);
        request.setResults(results);
        
        ResponseEntity<Map<String, Object>> response = controller.jobCallback("job-123", request);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().get("job_id"));
        assertEquals("Job updated successfully", response.getBody().get("message"));
        
        verify(mockAnalyticsService).updateJobViaCallback(eq("job-123"), eq("SUCCESS"), eq(results));
    }
    
    @Test
    void testJobCallback_NotFound() {
        doThrow(new IllegalArgumentException("Job not found: non-existent"))
                .when(mockAnalyticsService).updateJobViaCallback(anyString(), anyString(), any());
        
        CallbackRequest request = new CallbackRequest();
        request.setStatus("SUCCESS");
        
        ResponseEntity<Map<String, Object>> response = controller.jobCallback("non-existent", request);
        
        assertEquals(404, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("Job not found", response.getBody().get("error"));
    }
}
