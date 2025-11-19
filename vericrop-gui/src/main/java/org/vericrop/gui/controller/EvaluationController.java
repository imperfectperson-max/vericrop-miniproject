package org.vericrop.gui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.vericrop.dto.EvaluationRequest;
import org.vericrop.dto.EvaluationResult;
import org.vericrop.dto.ShipmentRecord;
import org.vericrop.kafka.messaging.KafkaProducerService;
import org.vericrop.service.QualityEvaluationService;
import org.vericrop.service.impl.FileLedgerService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for quality evaluation and shipment management.
 * Provides endpoints for image upload, evaluation, and ledger queries.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EvaluationController {
    private static final Logger logger = LoggerFactory.getLogger(EvaluationController.class);
    
    private final QualityEvaluationService evaluationService;
    private final FileLedgerService ledgerService;
    private final KafkaProducerService kafkaProducer;
    
    @Autowired(required = false)
    public EvaluationController(QualityEvaluationService evaluationService,
                                FileLedgerService ledgerService,
                                KafkaProducerService kafkaProducer) {
        this.evaluationService = evaluationService != null ? evaluationService : new QualityEvaluationService();
        this.ledgerService = ledgerService != null ? ledgerService : new FileLedgerService();
        this.kafkaProducer = kafkaProducer != null ? kafkaProducer : new KafkaProducerService(false);
        
        logger.info("Evaluation Controller initialized");
    }
    
    /**
     * POST /api/evaluate
     * Evaluate fruit quality from uploaded image or base64 data.
     */
    @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> evaluateImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "batch_id", required = false) String batchId,
            @RequestParam(value = "product_type", required = false) String productType,
            @RequestParam(value = "farmer_id", required = false) String farmerId) {
        
        logger.info("Received evaluation request for batch: {}", batchId);
        
        try {
            // Create evaluation request
            EvaluationRequest request = new EvaluationRequest();
            request.setBatchId(batchId != null ? batchId : "BATCH_" + System.currentTimeMillis());
            request.setProductType(productType != null ? productType : "apple");
            request.setFarmerId(farmerId != null ? farmerId : "farmer_001");
            
            // Handle file upload
            if (file != null && !file.isEmpty()) {
                String imagePath = saveUploadedFile(file);
                request.setImagePath(imagePath);
            } else {
                logger.warn("No image file provided, using batch ID for evaluation");
            }
            
            // Publish to Kafka if available
            kafkaProducer.sendEvaluationRequest(request);
            
            // Perform evaluation
            EvaluationResult result = evaluationService.evaluate(request);
            
            // Publish result to Kafka
            kafkaProducer.sendEvaluationResult(result);
            
            // Record in ledger
            ShipmentRecord shipmentRecord = new ShipmentRecord();
            shipmentRecord.setShipmentId("SHIP_" + System.currentTimeMillis());
            shipmentRecord.setBatchId(request.getBatchId());
            shipmentRecord.setFromParty(request.getFarmerId());
            shipmentRecord.setToParty("warehouse");
            shipmentRecord.setStatus("EVALUATED");
            shipmentRecord.setQualityScore(result.getQualityScore());
            
            ledgerService.recordShipment(shipmentRecord);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch_id", result.getBatchId());
            response.put("quality_score", result.getQualityScore());
            response.put("pass_fail", result.getPassFail());
            response.put("prediction", result.getPrediction());
            response.put("confidence", result.getConfidence());
            response.put("metadata", result.getMetadata());
            response.put("ledger_id", shipmentRecord.getLedgerId());
            response.put("ledger_hash", shipmentRecord.getLedgerHash());
            response.put("timestamp", result.getTimestamp());
            
            logger.info("Evaluation completed for batch {}: score={}, passFail={}", 
                result.getBatchId(), result.getQualityScore(), result.getPassFail());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during evaluation: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * POST /api/evaluate (JSON body)
     * Evaluate fruit quality from JSON request with base64 image or path.
     */
    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> evaluateFromJson(@RequestBody EvaluationRequest request) {
        logger.info("Received JSON evaluation request for batch: {}", request.getBatchId());
        
        try {
            // Publish to Kafka
            kafkaProducer.sendEvaluationRequest(request);
            
            // Perform evaluation
            EvaluationResult result = evaluationService.evaluate(request);
            
            // Publish result to Kafka
            kafkaProducer.sendEvaluationResult(result);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch_id", result.getBatchId());
            response.put("quality_score", result.getQualityScore());
            response.put("pass_fail", result.getPassFail());
            response.put("prediction", result.getPrediction());
            response.put("confidence", result.getConfidence());
            response.put("metadata", result.getMetadata());
            response.put("timestamp", result.getTimestamp());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during JSON evaluation: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/shipments/{id}
     * Get shipment record by ledger ID.
     */
    @GetMapping("/shipments/{id}")
    public ResponseEntity<Map<String, Object>> getShipment(@PathVariable String id) {
        logger.info("Retrieving shipment record: {}", id);
        
        try {
            ShipmentRecord record = ledgerService.getShipmentByLedgerId(id);
            
            if (record == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Shipment not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("shipment_id", record.getShipmentId());
            response.put("batch_id", record.getBatchId());
            response.put("from_party", record.getFromParty());
            response.put("to_party", record.getToParty());
            response.put("status", record.getStatus());
            response.put("quality_score", record.getQualityScore());
            response.put("ledger_id", record.getLedgerId());
            response.put("ledger_hash", record.getLedgerHash());
            response.put("timestamp", record.getTimestamp());
            response.put("verified", ledgerService.verifyRecordIntegrity(record));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving shipment: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/shipments
     * Get all shipments for a batch.
     */
    @GetMapping("/shipments")
    public ResponseEntity<Map<String, Object>> getShipments(
            @RequestParam(value = "batch_id", required = false) String batchId) {
        
        try {
            List<ShipmentRecord> records;
            
            if (batchId != null && !batchId.isEmpty()) {
                logger.info("Retrieving shipments for batch: {}", batchId);
                records = ledgerService.getShipmentsByBatchId(batchId);
            } else {
                logger.info("Retrieving all shipments");
                records = ledgerService.getAllShipments();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", records.size());
            response.put("shipments", records);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving shipments: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * GET /api/health
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "vericrop-evaluation-api");
        response.put("kafka_enabled", kafkaProducer.isKafkaEnabled());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Save uploaded file to temporary directory.
     */
    private String saveUploadedFile(MultipartFile file) throws IOException {
        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);
        
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);
        
        file.transferTo(filePath.toFile());
        
        logger.info("Saved uploaded file: {}", filePath);
        return filePath.toString();
    }
}
