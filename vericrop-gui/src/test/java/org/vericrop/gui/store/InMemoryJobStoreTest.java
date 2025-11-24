package org.vericrop.gui.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.gui.models.AnalyticsJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryJobStore
 */
class InMemoryJobStoreTest {
    
    private InMemoryJobStore jobStore;
    
    @BeforeEach
    void setUp() {
        jobStore = new InMemoryJobStore();
    }
    
    @Test
    void testSaveAndFindById() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("test-job-1");
        job.setDatasetId("dataset-1");
        job.setStatus(AnalyticsJob.JobStatus.PENDING);
        
        jobStore.save(job);
        
        Optional<AnalyticsJob> found = jobStore.findById("test-job-1");
        assertTrue(found.isPresent());
        assertEquals("test-job-1", found.get().getJobId());
        assertEquals("dataset-1", found.get().getDatasetId());
    }
    
    @Test
    void testFindById_NotFound() {
        Optional<AnalyticsJob> found = jobStore.findById("non-existent");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindById_NullId() {
        Optional<AnalyticsJob> found = jobStore.findById(null);
        assertFalse(found.isPresent());
    }
    
    @Test
    void testSaveUpdate() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("test-job-1");
        job.setStatus(AnalyticsJob.JobStatus.PENDING);
        
        jobStore.save(job);
        
        // Update the job
        job.setStatus(AnalyticsJob.JobStatus.RUNNING);
        job.setAirflowRunId("run-123");
        jobStore.save(job);
        
        Optional<AnalyticsJob> found = jobStore.findById("test-job-1");
        assertTrue(found.isPresent());
        assertEquals(AnalyticsJob.JobStatus.RUNNING, found.get().getStatus());
        assertEquals("run-123", found.get().getAirflowRunId());
    }
    
    @Test
    void testFindAll_Empty() {
        List<AnalyticsJob> jobs = jobStore.findAll();
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }
    
    @Test
    void testFindAll_Multiple() {
        AnalyticsJob job1 = new AnalyticsJob();
        job1.setJobId("job-1");
        job1.setDatasetId("dataset-1");
        
        AnalyticsJob job2 = new AnalyticsJob();
        job2.setJobId("job-2");
        job2.setDatasetId("dataset-2");
        
        jobStore.save(job1);
        jobStore.save(job2);
        
        List<AnalyticsJob> jobs = jobStore.findAll();
        assertEquals(2, jobs.size());
    }
    
    @Test
    void testDeleteById() {
        AnalyticsJob job = new AnalyticsJob();
        job.setJobId("test-job-1");
        jobStore.save(job);
        
        boolean deleted = jobStore.deleteById("test-job-1");
        assertTrue(deleted);
        
        Optional<AnalyticsJob> found = jobStore.findById("test-job-1");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testDeleteById_NotFound() {
        boolean deleted = jobStore.deleteById("non-existent");
        assertFalse(deleted);
    }
    
    @Test
    void testDeleteById_NullId() {
        boolean deleted = jobStore.deleteById(null);
        assertFalse(deleted);
    }
    
    @Test
    void testCount() {
        assertEquals(0, jobStore.count());
        
        AnalyticsJob job1 = new AnalyticsJob();
        job1.setJobId("job-1");
        jobStore.save(job1);
        
        assertEquals(1, jobStore.count());
        
        AnalyticsJob job2 = new AnalyticsJob();
        job2.setJobId("job-2");
        jobStore.save(job2);
        
        assertEquals(2, jobStore.count());
        
        jobStore.deleteById("job-1");
        assertEquals(1, jobStore.count());
    }
    
    @Test
    void testSaveNullJob() {
        assertThrows(IllegalArgumentException.class, () -> jobStore.save(null));
    }
    
    @Test
    void testSaveJobWithNullId() {
        AnalyticsJob job = new AnalyticsJob();
        // jobId is null
        assertThrows(IllegalArgumentException.class, () -> jobStore.save(job));
    }
}
