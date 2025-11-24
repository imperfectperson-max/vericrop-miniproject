package org.vericrop.gui.services;

import org.vericrop.gui.models.AnalyticsJob;

import java.util.Map;

/**
 * Service interface for analytics workflow orchestration.
 * Coordinates Airflow DAG triggers, Kafka events, and job tracking.
 */
public interface AnalyticsWorkflowService {
    
    /**
     * Trigger an analytics job
     * 
     * @param datasetId The dataset ID to process
     * @param parameters Job parameters
     * @param callbackUrl Optional callback URL for job completion notifications
     * @return The created job
     */
    AnalyticsJob triggerAnalyticsJob(String datasetId, Map<String, Object> parameters, String callbackUrl);
    
    /**
     * Get job status by job ID
     * 
     * @param jobId The job ID
     * @return The job with current status
     */
    AnalyticsJob getJobStatus(String jobId);
    
    /**
     * Get job results if available
     * 
     * @param jobId The job ID
     * @return The job with results
     */
    AnalyticsJob getJobResults(String jobId);
    
    /**
     * Update job status via callback
     * 
     * @param jobId The job ID
     * @param status New status
     * @param results Optional results payload
     */
    void updateJobViaCallback(String jobId, String status, Map<String, Object> results);
}
