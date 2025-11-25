package org.vericrop.gui.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import org.vericrop.dto.ShipmentRecord;
import org.vericrop.kafka.producers.BlockchainEventProducer;
import org.vericrop.kafka.events.BlockchainEvent;
import org.vericrop.service.BlockchainService;
import org.vericrop.service.impl.FileLedgerService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for Producer endpoints.
 * Provides API to create blockchain records for agricultural products/batches.
 * 
 * <h2>Endpoint Summary</h2>
 * <ul>
 *   <li>POST /producer/blockchain-record - Create a new blockchain record</li>
 *   <li>GET /producer/blockchain - Get blockchain status and recent blocks</li>
 *   <li>GET /producer/blockchain/validate - Validate blockchain integrity</li>
 *   <li>GET /producer/batch/{batchId} - Get blockchain transactions for a batch</li>
 * </ul>
 */
@RestController
@RequestMapping("/producer")
@CrossOrigin(origins = "*")
@Tag(name = "Producer", description = "Producer blockchain record creation and management API")
public class ProducerRestController {
    private static final Logger logger = LoggerFactory.getLogger(ProducerRestController.class);
    
    private final Blockchain blockchain;
    private final BlockchainService blockchainService;
    private final FileLedgerService ledgerService;
    private BlockchainEventProducer blockchainProducer;
    
    /**
     * Constructor with dependency injection.
     * Initializes blockchain and related services.
     */
    public ProducerRestController() {
        // Initialize blockchain
        this.blockchain = new Blockchain();
        this.blockchainService = new BlockchainService(blockchain);
        this.ledgerService = new FileLedgerService();
        
        // Initialize Kafka producer (optional - may fail in environments without Kafka)
        try {
            this.blockchainProducer = new BlockchainEventProducer();
            logger.info("BlockchainEventProducer initialized successfully");
        } catch (Exception e) {
            logger.warn("Could not initialize BlockchainEventProducer: {}. Continuing without Kafka events.", e.getMessage());
            this.blockchainProducer = null;
        }
        
        logger.info("ProducerRestController initialized with blockchain containing {} blocks", 
                    blockchain.getBlockCount());
    }
    
    /**
     * POST /producer/blockchain-record
     * 
     * Create a new blockchain record for a producer's batch/crop.
     * This endpoint accepts the batch details, persists them to the blockchain,
     * and returns the created record ID along with the block hash.
     *
     * @param request The blockchain record creation request containing batch details
     * @return HTTP 201 Created with the record details, or error response
     */
    @Operation(
        summary = "Create a blockchain record",
        description = "Creates a new immutable blockchain record for a producer's batch/crop registration. " +
                      "The record is persisted to the blockchain and a ledger entry is created."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Blockchain record created successfully",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "record_id": "BATCH_1700000000001",
                      "block_index": 5,
                      "block_hash": "a1b2c3d4e5f6...",
                      "previous_hash": "f6e5d4c3b2a1...",
                      "data_hash": "sha256_of_payload...",
                      "timestamp": 1700000000001,
                      "ledger_id": "SHIP_1700000000001",
                      "message": "Blockchain record created successfully"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Invalid request - missing required fields",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "error": "Validation failed",
                      "details": "producerId is required"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error - blockchain operation failed")
    })
    @PostMapping("/blockchain-record")
    public ResponseEntity<Map<String, Object>> createBlockchainRecord(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Blockchain record creation request",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BlockchainRecordRequest.class),
                    examples = @ExampleObject(value = """
                        {
                          "producerId": "FARMER_001",
                          "batchName": "Apple Batch 2024-01",
                          "productType": "Apple",
                          "quantity": 500,
                          "qualityScore": 0.92,
                          "location": "Sunny Valley Farm",
                          "additionalData": {
                            "variety": "Honeycrisp",
                            "harvestDate": "2024-01-15"
                          }
                        }
                        """)))
            @RequestBody BlockchainRecordRequest request) {
        
        logger.info("Received blockchain record creation request for producer: {}", 
                   request != null ? request.getProducerId() : "null");
        
        try {
            // Validate request (handles null case)
            ValidationResult validation = validateRequest(request);
            if (!validation.isValid()) {
                logger.warn("Validation failed: {}", validation.getError());
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Validation failed", validation.getError()));
            }
            
            // Generate batch ID if not provided
            String batchId = request.getBatchId();
            if (batchId == null || batchId.trim().isEmpty()) {
                batchId = "BATCH_" + System.currentTimeMillis();
            }
            
            // Calculate data hash from request payload
            String dataHash = calculateDataHash(request);
            
            // Create transaction data
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("batch_name", request.getBatchName());
            transactionData.put("producer_id", request.getProducerId());
            transactionData.put("product_type", request.getProductType());
            transactionData.put("quantity", request.getQuantity());
            transactionData.put("quality_score", request.getQualityScore());
            transactionData.put("location", request.getLocation());
            transactionData.put("timestamp", System.currentTimeMillis());
            if (request.getAdditionalData() != null) {
                transactionData.put("additional_data", request.getAdditionalData());
            }
            
            // Convert to JSON string for transaction
            String transactionDataJson = mapToJson(transactionData);
            
            // Create blockchain transaction
            List<Transaction> transactions = new ArrayList<>();
            transactions.add(new Transaction(
                "CREATE_BATCH",
                request.getProducerId(),
                "blockchain",
                batchId,
                transactionDataJson
            ));
            
            // Add block to blockchain (with timeout protection)
            final String finalBatchId = batchId;
            CompletableFuture<Block> blockFuture = blockchainService.addBlockAsync(
                transactions, dataHash, request.getProducerId());
            
            Block newBlock;
            try {
                newBlock = blockFuture.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("Blockchain operation timed out for batch: {}", finalBatchId);
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(createErrorResponse("Blockchain operation timed out", 
                          "The blockchain operation took too long. Please try again."));
            }
            
            if (newBlock == null) {
                logger.error("Failed to create blockchain block for batch: {}", finalBatchId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Blockchain operation failed", 
                          "Could not create blockchain block. Please try again."));
            }
            
            // Create shipment/ledger record
            String ledgerId = createLedgerRecord(finalBatchId, request, newBlock);
            
            // Send Kafka event (non-blocking, failures are logged but don't fail the request)
            sendBlockchainEvent(finalBatchId, request.getProducerId(), newBlock, dataHash);
            
            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("record_id", finalBatchId);
            response.put("block_index", newBlock.getIndex());
            response.put("block_hash", newBlock.getHash());
            response.put("previous_hash", newBlock.getPreviousHash());
            response.put("data_hash", dataHash);
            response.put("timestamp", newBlock.getTimestamp());
            response.put("ledger_id", ledgerId);
            response.put("message", "Blockchain record created successfully");
            
            logger.info("✅ Blockchain record created successfully. Batch: {}, Block: #{}, Hash: {}",
                       finalBatchId, newBlock.getIndex(), truncateHash(newBlock.getHash()));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for blockchain record creation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(createErrorResponse("Invalid request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating blockchain record: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Internal server error", 
                      "An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    /**
     * GET /producer/blockchain
     * 
     * Get blockchain status and recent blocks.
     */
    @Operation(summary = "Get blockchain status", 
               description = "Returns blockchain status including block count and recent blocks")
    @ApiResponse(responseCode = "200", description = "Blockchain status retrieved successfully")
    @GetMapping("/blockchain")
    public ResponseEntity<Map<String, Object>> getBlockchainStatus(
            @Parameter(description = "Number of recent blocks to return (default 10)")
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        
        try {
            List<Block> chain = blockchain.getChain();
            int totalBlocks = chain.size();
            
            // Get recent blocks (newest first)
            int startIndex = Math.max(0, totalBlocks - limit);
            List<Map<String, Object>> recentBlocks = new ArrayList<>();
            
            for (int i = totalBlocks - 1; i >= startIndex; i--) {
                Block block = chain.get(i);
                Map<String, Object> blockInfo = new HashMap<>();
                blockInfo.put("index", block.getIndex());
                blockInfo.put("hash", truncateHash(block.getHash()));
                blockInfo.put("previous_hash", truncateHash(block.getPreviousHash()));
                blockInfo.put("timestamp", block.getTimestamp());
                blockInfo.put("transaction_count", block.getTransactions().size());
                blockInfo.put("participant", block.getParticipant());
                recentBlocks.add(blockInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total_blocks", totalBlocks);
            response.put("chain_valid", blockchain.isChainValid());
            response.put("recent_blocks", recentBlocks);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving blockchain status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve blockchain status", e.getMessage()));
        }
    }
    
    /**
     * GET /producer/blockchain/validate
     * 
     * Validate the integrity of the blockchain.
     */
    @Operation(summary = "Validate blockchain integrity",
               description = "Validates the entire blockchain for tampering or corruption")
    @ApiResponse(responseCode = "200", description = "Validation completed")
    @GetMapping("/blockchain/validate")
    public ResponseEntity<Map<String, Object>> validateBlockchain() {
        try {
            boolean isValid = blockchain.isChainValid();
            int blockCount = blockchain.getBlockCount();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("valid", isValid);
            response.put("block_count", blockCount);
            response.put("message", isValid ? 
                "Blockchain is valid and tamper-free" : 
                "Blockchain integrity check failed - possible tampering detected");
            response.put("timestamp", System.currentTimeMillis());
            
            if (isValid) {
                logger.info("✅ Blockchain validation passed: {} blocks", blockCount);
            } else {
                logger.warn("❌ Blockchain validation failed: {} blocks", blockCount);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error validating blockchain: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Validation failed", e.getMessage()));
        }
    }
    
    /**
     * GET /producer/batch/{batchId}
     * 
     * Get all blockchain transactions for a specific batch.
     */
    @Operation(summary = "Get batch transactions",
               description = "Retrieves all blockchain transactions associated with a batch ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transactions retrieved"),
        @ApiResponse(responseCode = "404", description = "No transactions found for batch")
    })
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<Map<String, Object>> getBatchTransactions(
            @Parameter(description = "Batch ID to look up")
            @PathVariable String batchId) {
        
        try {
            List<Transaction> transactions = blockchain.getTransactionsForBatch(batchId);
            
            if (transactions.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("batch_id", batchId);
                response.put("message", "No transactions found for batch: " + batchId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            List<Map<String, Object>> transactionList = new ArrayList<>();
            for (Transaction tx : transactions) {
                Map<String, Object> txInfo = new HashMap<>();
                txInfo.put("type", tx.getType());
                txInfo.put("from", tx.getFrom());
                txInfo.put("to", tx.getTo());
                txInfo.put("batch_id", tx.getBatchId());
                txInfo.put("data", tx.getData());
                transactionList.add(txInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch_id", batchId);
            response.put("transaction_count", transactions.size());
            response.put("transactions", transactionList);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving batch transactions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to retrieve batch transactions", e.getMessage()));
        }
    }
    
    /**
     * GET /producer/health
     * 
     * Health check endpoint for the producer API.
     */
    @Operation(summary = "Health check", description = "Check producer service health")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "producer-api");
        health.put("blockchain_blocks", blockchain.getBlockCount());
        health.put("blockchain_valid", blockchain.isChainValid());
        health.put("kafka_enabled", blockchainProducer != null);
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Validate the incoming request.
     */
    private ValidationResult validateRequest(BlockchainRecordRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request body is required");
        }
        if (request.getProducerId() == null || request.getProducerId().trim().isEmpty()) {
            return ValidationResult.invalid("producerId is required");
        }
        if (request.getBatchName() == null || request.getBatchName().trim().isEmpty()) {
            return ValidationResult.invalid("batchName is required");
        }
        if (request.getProductType() == null || request.getProductType().trim().isEmpty()) {
            return ValidationResult.invalid("productType is required");
        }
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            return ValidationResult.invalid("quantity cannot be negative");
        }
        if (request.getQualityScore() != null && (request.getQualityScore() < 0 || request.getQualityScore() > 1)) {
            return ValidationResult.invalid("qualityScore must be between 0 and 1");
        }
        return ValidationResult.valid();
    }
    
    /**
     * Calculate SHA-256 hash of the request data.
     */
    private String calculateDataHash(BlockchainRecordRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder input = new StringBuilder();
            input.append(request.getProducerId());
            input.append(request.getBatchName());
            input.append(request.getProductType());
            input.append(request.getQuantity());
            input.append(request.getQualityScore());
            input.append(request.getLocation());
            input.append(System.currentTimeMillis());
            
            byte[] hashBytes = digest.digest(input.toString().getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * Create a ledger/shipment record.
     */
    private String createLedgerRecord(String batchId, BlockchainRecordRequest request, Block block) {
        try {
            ShipmentRecord record = new ShipmentRecord();
            record.setShipmentId("SHIP_" + System.currentTimeMillis());
            record.setBatchId(batchId);
            record.setFromParty(request.getProducerId());
            record.setToParty("blockchain");
            record.setStatus("CREATED");
            
            if (request.getQualityScore() != null) {
                record.setQualityScore(request.getQualityScore() * 100); // Convert to percentage
            }
            
            ShipmentRecord saved = ledgerService.recordShipment(record);
            logger.info("📝 Ledger record created: {}", saved.getLedgerId());
            return saved.getLedgerId();
            
        } catch (Exception e) {
            logger.warn("Failed to create ledger record (non-fatal): {}", e.getMessage());
            return "LEDGER_" + System.currentTimeMillis();
        }
    }
    
    /**
     * Send blockchain event to Kafka (non-blocking).
     */
    private void sendBlockchainEvent(String batchId, String producerId, Block block, String dataHash) {
        if (blockchainProducer == null) {
            logger.debug("Kafka producer not available, skipping blockchain event");
            return;
        }
        
        try {
            BlockchainEvent event = new BlockchainEvent(
                "CREATE_BATCH",
                batchId,
                producerId,
                block.getHash(),
                block.getIndex()
            );
            event.setDataHash(dataHash);
            blockchainProducer.sendBlockchainEvent(event);
            logger.info("⛓️ Blockchain event sent for batch: {}", batchId);
        } catch (Exception e) {
            logger.warn("Failed to send Kafka event (non-fatal): {}", e.getMessage());
        }
    }
    
    /**
     * Convert map to JSON string.
     */
    private String mapToJson(Map<String, Object> map) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * Create error response map.
     */
    private Map<String, Object> createErrorResponse(String error, String details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        response.put("details", details);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    /**
     * Truncate hash for display.
     */
    private String truncateHash(String hash) {
        if (hash == null) return "null";
        if (hash.length() <= 16) return hash;
        return hash.substring(0, 16) + "...";
    }
    
    // ==================== Request/Response DTOs ====================
    
    /**
     * Request body for creating a blockchain record.
     */
    @Schema(description = "Request to create a blockchain record for a producer's batch")
    public static class BlockchainRecordRequest {
        @Schema(description = "Producer/farmer identifier", example = "FARMER_001", required = true)
        private String producerId;
        
        @Schema(description = "Optional batch ID (auto-generated if not provided)", example = "BATCH_2024_001")
        private String batchId;
        
        @Schema(description = "Name/description of the batch", example = "Apple Batch 2024-01", required = true)
        private String batchName;
        
        @Schema(description = "Type of product", example = "Apple", required = true)
        private String productType;
        
        @Schema(description = "Quantity in units", example = "500")
        private Integer quantity;
        
        @Schema(description = "Quality score from 0.0 to 1.0", example = "0.92")
        private Double qualityScore;
        
        @Schema(description = "Location/origin of the batch", example = "Sunny Valley Farm")
        private String location;
        
        @Schema(description = "Additional metadata as key-value pairs")
        private Map<String, Object> additionalData;
        
        // Getters and setters
        public String getProducerId() { return producerId; }
        public void setProducerId(String producerId) { this.producerId = producerId; }
        
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
        
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public Double getQualityScore() { return qualityScore; }
        public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
    }
    
    /**
     * Validation result helper class.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() { return valid; }
        public String getError() { return error; }
    }
}
