package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.vericrop.gui.integration.AnalyticsAirflowClient;
import org.vericrop.gui.integration.AnalyticsKafkaProducer;
import org.vericrop.gui.models.AnalyticsJob;
import org.vericrop.gui.services.impl.AnalyticsWorkflowServiceImpl;
import org.vericrop.gui.store.JobStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsWorkflowServiceImpl
 */
class AnalyticsWorkflowServiceImplTest {
    
    private AnalyticsWorkflowService service;
    private JobStore mockJobStore;
    private AnalyticsAirflowClient mockAirflowClient;
    private AnalyticsKafkaProducer mockKafkaProducer;
    
    @BeforeEach
    void setUp() {
        mockJobStore = Mockito.mock(JobStore.class);
        mockAirflowClient = Mockito.mock(AnalyticsAirflowClient.class);
        mockKafkaProducer = Mockito.mock(AnalyticsKafkaProducer.class);
        
        service = new AnalyticsWorkflowServiceImpl(mockJobStore, mockAirflowClient, mockKafkaProducer);
    }
    
    @Test
    void testTriggerAnalyticsJob_Success() throws IOException {
        // Setup mocks
        when(mockAirflowClient.triggerDagRun(anyMap())).thenReturn("airflow-run-123");
        when(mockKafkaProducer.publishAnalyticsRequest(anyString(), anyString(), any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // Execute
        Map<String, Object> params = Map.of("param1", "value1");
        AnalyticsJob job = service.triggerAnalyticsJob("dataset-123", params, "http://callback.example.com");
        
        // Assert
        assertNotNull(job);
        assertNotNull(job.getJobId());
        assertEquals("dataset-123", job.getDatasetId());
        assertEquals(params, job.getParameters());
        assertEquals("http://callback.example.com", job.getCallbackUrl());
        assertEquals("airflow-run-123", job.getAirflowRunId());
        assertEquals(AnalyticsJob.JobStatus.RUNNING, job.getStatus());
        
        // Verify interactions
        verify(mockJobStore, atLeast(2)).save(any(AnalyticsJob.class));
        verify(mockAirflowClient).triggerDagRun(anyMap());
        verify(mockKafkaProducer).publishAnalyticsRequest(anyString(), eq("dataset-123"), eq(params), anyLong());
    }
    
    @Test
    void testTriggerAnalyticsJob_AirflowFailure() throws IOException {
        // Setup mocks - Airflow fails
        when(mockAirflowClient.triggerDagRun(anyMap())).thenThrow(new IOException("Airflow connection failed"));
        when(mockKafkaProducer.publishAnalyticsRequest(anyString(), anyString(), any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // Execute
        Map<String, Object> params = Map.of("param1", "value1");
        AnalyticsJob job = service.triggerAnalyticsJob("dataset-123", params, null);
        
        // Assert
        assertNotNull(job);
        assertEquals(AnalyticsJob.JobStatus.FAILED, job.getStatus());
        assertNotNull(job.getErrorMessage());
        assertTrue(job.getErrorMessage().contains("Failed to trigger Airflow DAG"));
        
        // Verify job was saved at least twice (initial and after failure)
        verify(mockJobStore, atLeast(2)).save(any(AnalyticsJob.class));
    }
    
    @Test
    void testTriggerAnalyticsJob_KafkaDisabled() throws IOException {
        // Setup mocks - Kafka is disabled
        when(mockAirflowClient.triggerDagRun(anyMap())).thenReturn("airflow-run-123");
        when(mockKafkaProducer.publishAnalyticsRequest(anyString(), anyString(), any(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // Execute
        Map<String, Object> params = Map.of("param1", "value1");
        AnalyticsJob job = service.triggerAnalyticsJob("dataset-123", params, null);
        
        // Assert - job should still succeed even if Kafka fails
        assertNotNull(job);
        assertEquals(AnalyticsJob.JobStatus.RUNNING, job.getStatus());
    }
    
    @Test
    void testGetJobStatus_JobNotFound() {
        when(mockJobStore.findById("non-existent")).thenReturn(Optional.empty());
        
        assertThrows(IllegalArgumentException.class, () -> service.getJobStatus("non-existent"));
    }
    
    @Test
    void testGetJobStatus_PendingJob() throws IOException {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.PENDING);
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        
        AnalyticsJob result = service.getJobStatus("job-123");
        
        assertNotNull(result);
        assertEquals("job-123", result.getJobId());
        assertEquals(AnalyticsJob.JobStatus.PENDING, result.getStatus());
        
        // Should not query Airflow for pending jobs without airflow run ID
        verify(mockAirflowClient, never()).getDagRunStatus(anyString());
    }
    
    @Test
    void testGetJobStatus_RunningJobWithAirflowUpdate() throws IOException {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        job.setAirflowRunId("airflow-run-123");
        
        Map<String, Object> airflowStatus = new HashMap<>();
        airflowStatus.put("state", "success");
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        when(mockAirflowClient.getDagRunStatus("airflow-run-123")).thenReturn(airflowStatus);
        
        AnalyticsJob result = service.getJobStatus("job-123");
        
        assertNotNull(result);
        assertEquals(AnalyticsJob.JobStatus.SUCCESS, result.getStatus());
        
        // Verify Airflow was queried and job was saved
        verify(mockAirflowClient).getDagRunStatus("airflow-run-123");
        verify(mockJobStore).save(any(AnalyticsJob.class));
    }
    
    @Test
    void testGetJobStatus_AirflowQueryFailure() throws IOException {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        job.setAirflowRunId("airflow-run-123");
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        when(mockAirflowClient.getDagRunStatus("airflow-run-123"))
                .thenThrow(new IOException("Airflow connection failed"));
        
        AnalyticsJob result = service.getJobStatus("job-123");
        
        // Should still return job even if Airflow query fails
        assertNotNull(result);
        assertEquals(AnalyticsJob.JobStatus.RUNNING, result.getStatus());
    }
    
    @Test
    void testGetJobResults_Success() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.SUCCESS);
        Map<String, Object> results = Map.of("accuracy", 0.95);
        job.setResults(results);
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        
        AnalyticsJob result = service.getJobResults("job-123");
        
        assertNotNull(result);
        assertEquals(AnalyticsJob.JobStatus.SUCCESS, result.getStatus());
        assertEquals(results, result.getResults());
    }
    
    @Test
    void testGetJobResults_NotComplete() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        
        AnalyticsJob result = service.getJobResults("job-123");
        
        assertNotNull(result);
        assertEquals(AnalyticsJob.JobStatus.RUNNING, result.getStatus());
        assertNull(result.getResults());
    }
    
    @Test
    void testUpdateJobViaCallback_Success() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        
        Map<String, Object> results = Map.of("completed", true);
        service.updateJobViaCallback("job-123", "SUCCESS", results);
        
        verify(mockJobStore).findById("job-123");
        verify(mockJobStore).save(argThat(j -> 
            j.getStatus() == AnalyticsJob.JobStatus.SUCCESS && 
            j.getResults().equals(results)
        ));
    }
    
    @Test
    void testUpdateJobViaCallback_InvalidStatus() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("job-123");
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        
        when(mockJobStore.findById("job-123")).thenReturn(Optional.of(job));
        
        // Should not throw, just log warning
        service.updateJobViaCallback("job-123", "INVALID_STATUS", null);
        
        // Job should not be saved with invalid status
        verify(mockJobStore, never()).save(any(AnalyticsJob.class));
    }
    
    @Test
    void testUpdateJobViaCallback_JobNotFound() {
        when(mockJobStore.findById("non-existent")).thenReturn(Optional.empty());
        
        assertThrows(IllegalArgumentException.class, 
            () -> service.updateJobViaCallback("non-existent", "SUCCESS", null));
    }
}
