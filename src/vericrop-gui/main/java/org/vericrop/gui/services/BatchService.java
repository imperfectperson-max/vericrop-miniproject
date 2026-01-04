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
                
                // Store image path
                batch.setImagePath(imageFile.getAbsolutePath());
                
                logger.info("Quality prediction: score={}, label={}", 
                        prediction.getQualityScore(), prediction.getLabel());
                
                // Compute batch metrics (prime%, rejection%)
                computeBatchMetrics(batch);
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
    
    /**
     * Compute batch metrics (prime%, rejection%) based on quality score and label.
     * Uses the algorithm from ProducerController:
     * - Fresh: prime% = 80 + quality% * 20
     * - Low Quality: low_quality% = 80 + quality% * 20
     * - Rotten: rejection% = 80 + quality% * 20
     */
    private void computeBatchMetrics(BatchRecord batch) {
        if (batch.getQualityScore() == null || batch.getQualityLabel() == null) {
            logger.warn("Cannot compute batch metrics - quality score or label is null");
            return;
        }
        
        double qualityScore = batch.getQualityScore();
        double qualityPercent = qualityScore * 100.0;
        String classification = org.vericrop.gui.util.QualityLabelUtil.normalize(batch.getQualityLabel());
        
        double primeRate, rejectionRate;
        
        switch (classification) {
            case "FRESH":
                primeRate = Math.min(80 + (qualityPercent * 0.2), 100.0) / 100.0;
                double freshRemainder = 100.0 - (primeRate * 100.0);
                rejectionRate = (freshRemainder * 0.2) / 100.0;
                break;
                
            case "LOW_QUALITY":
                double lowQualityRate = Math.min(80 + (qualityPercent * 0.2), 100.0);
                double lowQualityRemainder = 100.0 - lowQualityRate;
                primeRate = (lowQualityRemainder * 0.8) / 100.0;
                rejectionRate = (lowQualityRemainder * 0.2) / 100.0;
                break;
            
            case "ROTTEN":
                rejectionRate = Math.min(80 + (qualityPercent * 0.2), 100.0) / 100.0;
                double rottenRemainder = 100.0 - (rejectionRate * 100.0);
                primeRate = (rottenRemainder * 0.2) / 100.0;
                break;
                
            default:
                // Fallback for unknown classifications
                primeRate = qualityScore;
                rejectionRate = (1.0 - qualityScore) * 0.3;
        }
        
        batch.setPrimeRate(primeRate);
        batch.setRejectionRate(rejectionRate);
        
        logger.info("Computed batch metrics: prime={}%, rejection={}%", 
                    primeRate * 100, rejectionRate * 100);
    }
    
    /**
     * Generate QR code for a batch.
     * QR code is saved in generated_qr/ directory with filename: product_{batchId}.png
     * 
     * @param batch The batch to generate QR code for
     * @return Path to the generated QR code file
     * @throws IOException if QR code generation fails
     */
    public String generateBatchQRCode(BatchRecord batch) throws IOException {
        try {
            // Use QRGenerator from util package
            java.nio.file.Path qrPath = org.vericrop.gui.util.QRGenerator.generateProductQR(
                batch.getBatchId(), 
                batch.getFarmer()
            );
            
            String qrPathStr = qrPath.toString();
            batch.setQrCodePath(qrPathStr);
            
            // Update in database
            repository.update(batch);
            
            logger.info("Generated QR code for batch {}: {}", batch.getBatchId(), qrPathStr);
            return qrPathStr;
        } catch (Exception e) {
            logger.error("Failed to generate QR code for batch {}", batch.getBatchId(), e);
            throw new IOException("QR code generation failed", e);
        }
    }
}
