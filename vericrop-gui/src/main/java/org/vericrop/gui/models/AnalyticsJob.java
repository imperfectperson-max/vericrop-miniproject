package org.vericrop.gui.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Model representing an analytics job that coordinates workflow across
 * Airflow, vericrop-core, and kafka-service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsJob {
    
    @JsonProperty("job_id")
    private String jobId;
    
    @JsonProperty("dataset_id")
    private String datasetId;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    @JsonProperty("callback_url")
    private String callbackUrl;
    
    @JsonProperty("status")
    private JobStatus status;
    
    @JsonProperty("airflow_run_id")
    private String airflowRunId;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("updated_at")
    private Instant updatedAt;
    
    @JsonProperty("results_link")
    private String resultsLink;
    
    @JsonProperty("results")
    private Map<String, Object> results;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    public enum JobStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED
    }
    
    public AnalyticsJob() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = JobStatus.PENDING;
    }
    
    // Getters and Setters
    
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public String getDatasetId() {
        return datasetId;
    }
    
    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    public String getCallbackUrl() {
        return callbackUrl;
    }
    
    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
    
    public JobStatus getStatus() {
        return status;
    }
    
    public void setStatus(JobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    
    public String getAirflowRunId() {
        return airflowRunId;
    }
    
    public void setAirflowRunId(String airflowRunId) {
        this.airflowRunId = airflowRunId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getResultsLink() {
        return resultsLink;
    }
    
    public void setResultsLink(String resultsLink) {
        this.resultsLink = resultsLink;
    }
    
    public Map<String, Object> getResults() {
        return results;
    }
    
    public void setResults(Map<String, Object> results) {
        this.results = results;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
