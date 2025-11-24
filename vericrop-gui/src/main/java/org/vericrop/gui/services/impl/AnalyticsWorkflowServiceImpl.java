package org.vericrop.gui.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.integration.AnalyticsAirflowClient;
import org.vericrop.gui.integration.AnalyticsKafkaProducer;
import org.vericrop.gui.models.AnalyticsJob;
import org.vericrop.gui.services.AnalyticsWorkflowService;
import org.vericrop.gui.store.JobStore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of AnalyticsWorkflowService.
 * Orchestrates analytics workflows across Airflow, Kafka, and job tracking.
 */
public class AnalyticsWorkflowServiceImpl implements AnalyticsWorkflowService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsWorkflowServiceImpl.class);
    
    private final JobStore jobStore;
    private final AnalyticsAirflowClient airflowClient;
    private final AnalyticsKafkaProducer kafkaProducer;
    
    public AnalyticsWorkflowServiceImpl(
            JobStore jobStore,
            AnalyticsAirflowClient airflowClient,
            AnalyticsKafkaProducer kafkaProducer) {
        this.jobStore = jobStore;
        this.airflowClient = airflowClient;
        this.kafkaProducer = kafkaProducer;
        logger.info("✅ AnalyticsWorkflowServiceImpl initialized");
    }
    
    @Override
    public AnalyticsJob triggerAnalyticsJob(String datasetId, Map<String, Object> parameters, String callbackUrl) {
        // Generate job ID
        String jobId = UUID.randomUUID().toString();
        
        // Create job
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId(jobId);
        job.setDatasetId(datasetId);
        job.setParameters(parameters);
        job.setCallbackUrl(callbackUrl);
        job.setStatus(AnalyticsJob.JobStatus.PENDING);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        
        // Save job
        jobStore.save(job);
        logger.info("Created analytics job: {}", jobId);
        
        try {
            // Trigger Airflow DAG
            Map<String, Object> dagConf = new HashMap<>();
            dagConf.put("job_id", jobId);
            dagConf.put("dataset_id", datasetId);
            dagConf.put("parameters", parameters);
            
            String airflowRunId = airflowClient.triggerDagRun(dagConf);
            
            // Update job with Airflow run ID
            job.setAirflowRunId(airflowRunId);
            job.setStatus(AnalyticsJob.JobStatus.RUNNING);
            jobStore.save(job);
            
            logger.info("Triggered Airflow DAG for job: {}, run_id: {}", jobId, airflowRunId);
            
        } catch (Exception e) {
            logger.error("Failed to trigger Airflow DAG for job: {}", jobId, e);
            job.setStatus(AnalyticsJob.JobStatus.FAILED);
            job.setErrorMessage("Failed to trigger Airflow DAG: " + e.getMessage());
            jobStore.save(job);
        }
        
        try {
            // Publish Kafka event (non-blocking)
            kafkaProducer.publishAnalyticsRequest(
                    jobId, datasetId, parameters, System.currentTimeMillis())
                    .exceptionally(throwable -> {
                        logger.warn("Failed to publish Kafka event for job: {}", jobId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            logger.warn("Failed to publish Kafka event for job: {}", jobId, e);
            // Don't fail the job if Kafka publish fails
        }
        
        return job;
    }
    
    @Override
    public AnalyticsJob getJobStatus(String jobId) {
        AnalyticsJob job = jobStore.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // If job is running, fetch status from Airflow
        if (job.getStatus() == AnalyticsJob.JobStatus.RUNNING && job.getAirflowRunId() != null) {
            try {
                Map<String, Object> dagRunStatus = airflowClient.getDagRunStatus(job.getAirflowRunId());
                String state = (String) dagRunStatus.get("state");
                
                // Map Airflow state to job status
                AnalyticsJob.JobStatus newStatus = mapAirflowStateToJobStatus(state);
                if (newStatus != job.getStatus()) {
                    job.setStatus(newStatus);
                    jobStore.save(job);
                    logger.info("Updated job status from Airflow: {} -> {}", jobId, newStatus);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to fetch Airflow status for job: {}", jobId, e);
            }
        }
        
        return job;
    }
    
    @Override
    public AnalyticsJob getJobResults(String jobId) {
        AnalyticsJob job = getJobStatus(jobId);
        
        // Results are only available if job is successful
        if (job.getStatus() != AnalyticsJob.JobStatus.SUCCESS) {
            logger.info("Job {} is not complete, status: {}", jobId, job.getStatus());
        }
        
        return job;
    }
    
    @Override
    public void updateJobViaCallback(String jobId, String status, Map<String, Object> results) {
        AnalyticsJob job = jobStore.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // Update status
        AnalyticsJob.JobStatus newStatus;
        try {
            newStatus = AnalyticsJob.JobStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status in callback: {}", status);
            return;
        }
        
        job.setStatus(newStatus);
        
        // Update results if provided
        if (results != null) {
            job.setResults(results);
        }
        
        job.setUpdatedAt(Instant.now());
        jobStore.save(job);
        
        logger.info("Updated job via callback: {} -> {}", jobId, newStatus);
    }
    
    /**
     * Map Airflow DAG run state to AnalyticsJob status
     */
    private AnalyticsJob.JobStatus mapAirflowStateToJobStatus(String airflowState) {
        if (airflowState == null) {
            return AnalyticsJob.JobStatus.PENDING;
        }
        
        return switch (airflowState.toLowerCase()) {
            case "success" -> AnalyticsJob.JobStatus.SUCCESS;
            case "failed" -> AnalyticsJob.JobStatus.FAILED;
            case "running" -> AnalyticsJob.JobStatus.RUNNING;
            default -> AnalyticsJob.JobStatus.PENDING;
        };
    }
}
