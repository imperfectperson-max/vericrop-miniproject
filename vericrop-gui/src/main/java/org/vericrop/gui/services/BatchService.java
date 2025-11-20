package org.vericrop.gui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.clients.MLClientService;
import org.vericrop.gui.models.BatchRecord;
import org.vericrop.gui.models.BatchResponse;
import org.vericrop.gui.models.PredictionResult;
import org.vericrop.gui.persistence.PostgresBatchRepository;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Batch Service for managing batch operations.
 * Coordinates between ML service, Kafka messaging, and database persistence.
 * 
 * Business logic for:
 * - Creating batches with quality predictions
 * - Updating batch status through supply chain
 * - Querying batch information
 * - Publishing batch events to Kafka
 */
public class BatchService {
    private static final Logger logger = LoggerFactory.getLogger(BatchService.class);
    
    private final MLClientService mlClient;
    private final KafkaMessagingService kafkaService;
    private final PostgresBatchRepository repository;

    public BatchService(MLClientService mlClient, 
                       KafkaMessagingService kafkaService,
                       PostgresBatchRepository repository) {
        this.mlClient = mlClient;
        this.kafkaService = kafkaService;
        this.repository = repository;
        logger.info("✅ BatchService initialized");
    }

    /**
     * Create a new batch with image quality prediction
     * @param batch Batch metadata
     * @param imageFile Image file for quality prediction (optional)
     * @return Created batch with quality score
     */
    public BatchRecord createBatch(BatchRecord batch, File imageFile) throws IOException, SQLException {
        logger.info("Creating batch: {}", batch.getName());
        
        // Step 1: Predict quality if image provided
        if (imageFile != null) {
            try {
                PredictionResult prediction = mlClient.predictImage(imageFile);
                batch.setQualityScore(prediction.getQualityScore());
                batch.setQualityLabel(prediction.getLabel());
                batch.setDataHash(prediction.getDataHash());
                logger.info("Quality prediction: score={}, label={}", 
                        prediction.getQualityScore(), prediction.getLabel());
            } catch (IOException e) {
                logger.error("Failed to predict quality for batch {}", batch.getName(), e);
                // Continue without prediction - set defaults
                batch.setQualityScore(null);
                batch.setQualityLabel("unknown");
            }
        }
        
        // Step 2: Create batch in ML service
        try {
            BatchResponse mlResponse = mlClient.createBatch(batch);
            batch.setBatchId(mlResponse.getBatchId());
            batch.setTimestamp(mlResponse.getTimestamp());
            
            // Update quality data from ML response if available
            if (mlResponse.getQualityScore() != null) {
                batch.setQualityScore(mlResponse.getQualityScore());
            }
            if (mlResponse.getQualityLabel() != null) {
                batch.setQualityLabel(mlResponse.getQualityLabel());
            }
            
            logger.info("Batch created in ML service: {}", batch.getBatchId());
        } catch (IOException e) {
            logger.error("Failed to create batch in ML service", e);
            // Generate batch ID locally if ML service fails
            batch.setBatchId("BATCH_" + System.currentTimeMillis());
            batch.setTimestamp(LocalDateTime.now().toString());
        }
        
        // Step 3: Save to database
        try {
            batch.setStatus("created");
            batch = repository.create(batch);
            logger.info("Batch saved to database: id={}", batch.getId());
        } catch (SQLException e) {
            logger.error("Failed to save batch to database", e);
            throw e;
        }
        
        // Step 4: Publish to Kafka
        if (kafkaService.isEnabled()) {
            try {
                kafkaService.sendBatchSync(batch);
                logger.info("Batch event published to Kafka: {}", batch.getBatchId());
            } catch (ExecutionException | InterruptedException e) {
                logger.error("Failed to publish batch event to Kafka", e);
                // Don't fail the whole operation if Kafka fails
            }
        }
        
        return batch;
    }

    /**
     * Create a batch without image prediction
     */
    public BatchRecord createBatch(BatchRecord batch) throws IOException, SQLException {
        return createBatch(batch, null);
    }

    /**
     * Update batch status (e.g., in_transit, delivered)
     */
    public BatchRecord updateBatchStatus(String batchId, String newStatus) throws SQLException {
        logger.info("Updating batch {} status to {}", batchId, newStatus);
        
        Optional<BatchRecord> batchOpt = repository.findByBatchId(batchId);
        if (batchOpt.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        
        BatchRecord batch = batchOpt.get();
        batch.setStatus(newStatus);
        batch = repository.update(batch);
        
        // Publish status update to Kafka
        if (kafkaService.isEnabled()) {
            try {
                kafkaService.sendBatchSync(batch);
                logger.info("Batch status update published to Kafka");
            } catch (ExecutionException | InterruptedException e) {
                logger.error("Failed to publish status update to Kafka", e);
            }
        }
        
        return batch;
    }

    /**
     * Find batch by batch ID
     */
    public Optional<BatchRecord> findBatch(String batchId) throws SQLException {
        return repository.findByBatchId(batchId);
    }

    /**
     * Find all batches
     */
    public List<BatchRecord> findAllBatches() throws SQLException {
        return repository.findAll();
    }

    /**
     * Find batches by farmer
     */
    public List<BatchRecord> findBatchesByFarmer(String farmer) throws SQLException {
        return repository.findByFarmer(farmer);
    }

    /**
     * Find batches by status
     */
    public List<BatchRecord> findBatchesByStatus(String status) throws SQLException {
        return repository.findByStatus(status);
    }

    /**
     * Delete a batch
     */
    public boolean deleteBatch(String batchId) throws SQLException {
        logger.info("Deleting batch: {}", batchId);
        return repository.delete(batchId);
    }

    /**
     * Count total batches
     */
    public long countBatches() throws SQLException {
        return repository.count();
    }

    /**
     * Test database connection
     */
    public boolean testDatabaseConnection() {
        return repository.testConnection();
    }
}
