package org.vericrop.gui.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.models.AnalyticsJob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory implementation of JobStore.
 * Uses ConcurrentHashMap for thread-safe operations without external locking.
 */
public class InMemoryJobStore implements JobStore {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryJobStore.class);
    
    private final ConcurrentMap<String, AnalyticsJob> jobs = new ConcurrentHashMap<>();
    
    @Override
    public void save(AnalyticsJob job) {
        if (job == null || job.getJobId() == null) {
            throw new IllegalArgumentException("Job and jobId cannot be null");
        }
        
        jobs.put(job.getJobId(), job);
        logger.debug("Saved job: {}", job.getJobId());
    }
    
    @Override
    public Optional<AnalyticsJob> findById(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(jobs.get(jobId));
    }
    
    @Override
    public List<AnalyticsJob> findAll() {
        return new ArrayList<>(jobs.values());
    }
    
    @Override
    public boolean deleteById(String jobId) {
        if (jobId == null) {
            return false;
        }
        
        boolean removed = jobs.remove(jobId) != null;
        if (removed) {
            logger.debug("Deleted job: {}", jobId);
        }
        return removed;
    }
    
    @Override
    public long count() {
        return jobs.size();
    }
}
