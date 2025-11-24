package org.vericrop.gui.store;

import org.vericrop.gui.models.AnalyticsJob;

import java.util.List;
import java.util.Optional;

/**
 * Interface for storing and retrieving analytics job metadata.
 * Designed to be replaceable with persistent storage implementations.
 */
public interface JobStore {
    
    /**
     * Save or update a job
     * @param job The job to save
     */
    void save(AnalyticsJob job);
    
    /**
     * Find a job by its ID
     * @param jobId The job ID
     * @return Optional containing the job if found
     */
    Optional<AnalyticsJob> findById(String jobId);
    
    /**
     * Get all jobs
     * @return List of all jobs
     */
    List<AnalyticsJob> findAll();
    
    /**
     * Delete a job by its ID
     * @param jobId The job ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(String jobId);
    
    /**
     * Get total number of jobs stored
     * @return The count of jobs
     */
    long count();
}
