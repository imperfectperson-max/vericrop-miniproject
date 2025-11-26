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
 *   <li>POST /producer/batches - Create a new batch for a producer</li>
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
     * 
     * Uses default constructor for backwards compatibility with existing tests.
     * For production use with Spring, services can be injected via setter methods.
     */
    public ProducerRestController() {
        this(new Blockchain(), new FileLedgerService());
    }
    
    /**
     * Constructor for dependency injection (for testing and Spring DI).
     * 
     * @param blockchain The blockchain instance to use
     * @param ledgerService The ledger service for recording shipments
     */
    public ProducerRestController(Blockchain blockchain, FileLedgerService ledgerService) {
        this.blockchain = blockchain;
        this.blockchainService = new BlockchainService(blockchain);
        this.ledgerService = ledgerService;
        
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
     * POST /producer/batches
     * 
     * Create a new batch for a producer.
     * This endpoint creates a batch record and associates it with the specified producer.
     *
     * @param request The batch creation request containing producer and batch details
     * @return HTTP 201 Created with the batch details, or error response
     */
    @Operation(
        summary = "Create a batch for a producer",
        description = "Creates a new batch record and associates it with the specified producer. " +
                      "The batch is persisted and can be tracked through the supply chain."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Batch created successfully",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "batch_id": "BATCH_1700000000001",
                      "producer_id": "FARMER_001",
                      "name": "Apple Batch 2024-01",
                      "product_type": "Apple",
                      "quantity": 500,
                      "status": "created",
                      "timestamp": "2024-01-15T10:30:00",
                      "message": "Batch created successfully"
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
                    """)))
    })
    @PostMapping("/batches")
    public ResponseEntity<Map<String, Object>> createBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Batch creation request",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BatchCreationRequest.class),
                    examples = @ExampleObject(value = """
                        {
                          "producerId": "FARMER_001",
                          "name": "Apple Batch 2024-01",
                          "productType": "Apple",
                          "quantity": 500,
                          "qualityScore": 0.92,
                          "qualityLabel": "FRESH"
                        }
                        """)))
            @RequestBody BatchCreationRequest request) {
        
        logger.info("Received batch creation request for producer: {}", 
                   request != null ? request.getProducerId() : "null");
        
        try {
            // Validate request
            BatchValidationResult validation = validateBatchRequest(request);
            if (!validation.isValid()) {
                logger.warn("Batch validation failed: {}", validation.getError());
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Validation failed", validation.getError()));
            }
            
            // Generate batch ID if not provided (using UUID for uniqueness)
            String batchId = request.getBatchId();
            if (batchId == null || batchId.trim().isEmpty()) {
                batchId = generateBatchId();
            }
            
            // Create batch data map (simulating in-memory persistence for now)
            // In a full implementation, this would persist to the database via BatchService
            String timestamp = java.time.LocalDateTime.now().toString();
            
            // Build success response with batch details
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("batch_id", batchId);
            response.put("producer_id", request.getProducerId());
            response.put("name", request.getName());
            response.put("product_type", request.getProductType());
            response.put("quantity", request.getQuantity());
            response.put("quality_score", request.getQualityScore());
            response.put("quality_label", request.getQualityLabel());
            response.put("status", "created");
            response.put("timestamp", timestamp);
            response.put("message", "Batch created successfully");
            
            // Store batch in ledger for persistence
            try {
                ShipmentRecord record = new ShipmentRecord();
                record.setShipmentId(generateShipmentId());
                record.setBatchId(batchId);
                record.setFromParty(request.getProducerId());
                record.setToParty("processing");
                record.setStatus("CREATED");
                if (request.getQualityScore() != null) {
                    record.setQualityScore(convertQualityScoreToPercentage(request.getQualityScore()));
                }
                ShipmentRecord saved = ledgerService.recordShipment(record);
                response.put("ledger_id", saved.getLedgerId());
                logger.info("📝 Batch persisted to ledger: {}", saved.getLedgerId());
            } catch (Exception e) {
                logger.warn("Failed to persist batch to ledger (non-fatal): {}", e.getMessage());
                // Continue - the batch is still created, just not in ledger
            }
            
            logger.info("✅ Batch created successfully. Batch: {}, Producer: {}",
                       batchId, request.getProducerId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for batch creation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(createErrorResponse("Invalid request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating batch: {}", e.getMessage(), e);
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
        @ApiResponse(responseCode = "400", description = "Invalid batch ID"),
        @ApiResponse(responseCode = "404", description = "No transactions found for batch")
    })
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<Map<String, Object>> getBatchTransactions(
            @Parameter(description = "Batch ID to look up")
            @PathVariable String batchId) {
        
        try {
            // Validate batch ID
            if (batchId == null || batchId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid batch ID", "Batch ID cannot be null or empty"));
            }
            
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
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument for batch transactions: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(createErrorResponse("Invalid batch ID", e.getMessage()));
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
    
    /**
     * GET /producer/simulate-batch
     * 
     * Generate a simulated batch with realistic data suitable for consumer verification.
     * Creates a batch with UUID id, quality score (0-100), and base64 QR code.
     * 
     * @return Simulated batch data suitable for verification by ConsumerController
     */
    @Operation(
        summary = "Generate a simulated batch",
        description = "Creates a realistic simulated batch with UUID id, qualityScore (0-100), " +
                      "and base64 QR code. Suitable for integration testing with ConsumerController /consumer/verify endpoint."
    )
    @ApiResponse(responseCode = "200", description = "Simulated batch generated successfully",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(value = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "qualityScore": 85,
                  "qrCode": "eyJpZCI6IjU1MGU4NDAwLWUyOWItNDFkNC1hNzE2LTQ0NjY1NTQ0MDAwMCIsInF1YWxpdHlTY29yZSI6ODUsInByb2R1Y2VySWQiOiJGQVJNRVJfMDAxIiwicHJvZHVjdFR5cGUiOiJBcHBsZSJ9",
                  "producerId": "FARMER_001",
                  "productType": "Apple",
                  "batchName": "Apple Batch 2024-01",
                  "quantity": 500,
                  "location": "Sunny Valley Farm",
                  "timestamp": 1700000000000
                }
                """)))
    @GetMapping("/simulate-batch")
    public ResponseEntity<Map<String, Object>> simulateBatch() {
        try {
            // Generate a unique UUID for the batch
            String batchId = UUID.randomUUID().toString();
            
            // Generate realistic quality score (0-100)
            int qualityScore = generateRealisticQualityScore();
            
            // Generate sample producer data
            String producerId = "FARMER_" + String.format("%03d", new Random().nextInt(1000));
            String productType = getRandomProductType();
            String batchName = productType + " Batch " + java.time.LocalDate.now();
            int quantity = 100 + new Random().nextInt(900); // 100-999 units
            String location = getRandomLocation();
            long timestamp = System.currentTimeMillis();
            
            // Create QR code payload (JSON containing batch info)
            Map<String, Object> qrPayload = new LinkedHashMap<>();
            qrPayload.put("id", batchId);
            qrPayload.put("qualityScore", qualityScore);
            qrPayload.put("producerId", producerId);
            qrPayload.put("productType", productType);
            qrPayload.put("timestamp", timestamp);
            
            String qrCodePayloadJson = mapToJson(qrPayload);
            String qrCode = Base64.getEncoder().encodeToString(qrCodePayloadJson.getBytes());
            
            // Build response
            Map<String, Object> simulatedBatch = new LinkedHashMap<>();
            simulatedBatch.put("id", batchId);
            simulatedBatch.put("qualityScore", qualityScore);
            simulatedBatch.put("qrCode", qrCode);
            simulatedBatch.put("producerId", producerId);
            simulatedBatch.put("productType", productType);
            simulatedBatch.put("batchName", batchName);
            simulatedBatch.put("quantity", quantity);
            simulatedBatch.put("location", location);
            simulatedBatch.put("timestamp", timestamp);
            
            logger.info("🔧 Simulated batch generated: id={}, qualityScore={}", batchId, qualityScore);
            
            return ResponseEntity.ok(simulatedBatch);
            
        } catch (Exception e) {
            logger.error("Error generating simulated batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to generate simulated batch", e.getMessage()));
        }
    }
    
    /**
     * GET /producer/simulate-batches
     * 
     * Generate multiple simulated batches for integration testing.
     * 
     * @param count Number of batches to generate (default 5, max 20)
     * @return List of simulated batches
     */
    @Operation(
        summary = "Generate multiple simulated batches",
        description = "Creates multiple realistic simulated batches for integration testing"
    )
    @ApiResponse(responseCode = "200", description = "Simulated batches generated successfully")
    @GetMapping("/simulate-batches")
    public ResponseEntity<Map<String, Object>> simulateBatches(
            @Parameter(description = "Number of batches to generate (1-20)")
            @RequestParam(value = "count", defaultValue = "5") int count) {
        try {
            // Limit count to prevent abuse
            int actualCount = Math.max(1, Math.min(count, 20));
            
            List<Map<String, Object>> batches = new ArrayList<>();
            for (int i = 0; i < actualCount; i++) {
                ResponseEntity<Map<String, Object>> response = simulateBatch();
                if (response.getBody() != null && !response.getBody().containsKey("error")) {
                    batches.add(response.getBody());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("count", batches.size());
            result.put("batches", batches);
            result.put("timestamp", System.currentTimeMillis());
            
            logger.info("🔧 Generated {} simulated batches", batches.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error generating simulated batches: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to generate simulated batches", e.getMessage()));
        }
    }
    
    /**
     * Generate a realistic quality score (0-100).
     * Uses a distribution that favors higher quality scores (typical for production).
     */
    private int generateRealisticQualityScore() {
        Random random = new Random();
        // Use a distribution that favors scores between 70-95
        double base = 70.0 + random.nextGaussian() * 10;
        return Math.max(0, Math.min(100, (int) Math.round(base)));
    }
    
    /**
     * Get a random product type from common agricultural products.
     */
    private String getRandomProductType() {
        String[] types = {"Apple", "Orange", "Banana", "Strawberry", "Grape", "Tomato", "Carrot", "Lettuce"};
        return types[new Random().nextInt(types.length)];
    }
    
    /**
     * Get a random farm location name.
     */
    private String getRandomLocation() {
        String[] locations = {
            "Sunny Valley Farm", 
            "Green Acres Orchard", 
            "Mountain View Farm", 
            "Riverside Gardens",
            "Golden Harvest Fields",
            "Blue Sky Ranch"
        };
        return locations[new Random().nextInt(locations.length)];
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
            // Include batch ID if provided for deterministic hashing
            if (request.getBatchId() != null) {
                input.append(request.getBatchId());
            }
            
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
     * Generate a unique shipment ID using UUID for guaranteed uniqueness.
     * @return A unique shipment ID
     */
    private String generateShipmentId() {
        return "SHIP_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis();
    }
    
    /**
     * Generate a unique batch ID using UUID for guaranteed uniqueness.
     * @return A unique batch ID
     */
    private String generateBatchId() {
        return "BATCH_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis();
    }
    
    /**
     * Convert quality score (0.0-1.0) to percentage (0-100).
     * @param qualityScore Quality score between 0 and 1
     * @return Quality score as percentage
     */
    private double convertQualityScoreToPercentage(Double qualityScore) {
        return qualityScore != null ? qualityScore * 100 : 0.0;
    }
    
    /**
     * Create a ledger/shipment record.
     */
    private String createLedgerRecord(String batchId, BlockchainRecordRequest request, Block block) {
        try {
            ShipmentRecord record = new ShipmentRecord();
            record.setShipmentId(generateShipmentId());
            record.setBatchId(batchId);
            record.setFromParty(request.getProducerId());
            record.setToParty("blockchain");
            record.setStatus("CREATED");
            
            if (request.getQualityScore() != null) {
                record.setQualityScore(convertQualityScoreToPercentage(request.getQualityScore()));
            }
            
            ShipmentRecord saved = ledgerService.recordShipment(record);
            logger.info("📝 Ledger record created: {}", saved.getLedgerId());
            return saved.getLedgerId();
            
        } catch (Exception e) {
            logger.warn("Failed to create ledger record (non-fatal): {}", e.getMessage());
            // Use UUID-based fallback for uniqueness
            return "LEDGER_" + java.util.UUID.randomUUID().toString().substring(0, 12);
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
    
    /**
     * Request body for creating a batch.
     */
    @Schema(description = "Request to create a batch for a producer")
    public static class BatchCreationRequest {
        @Schema(description = "Producer/farmer identifier", example = "FARMER_001", required = true)
        private String producerId;
        
        @Schema(description = "Optional batch ID (auto-generated if not provided)", example = "BATCH_2024_001")
        private String batchId;
        
        @Schema(description = "Name/description of the batch", example = "Apple Batch 2024-01", required = true)
        private String name;
        
        @Schema(description = "Type of product", example = "Apple", required = true)
        private String productType;
        
        @Schema(description = "Quantity in units", example = "500")
        private Integer quantity;
        
        @Schema(description = "Quality score from 0.0 to 1.0", example = "0.92")
        private Double qualityScore;
        
        @Schema(description = "Quality label classification", example = "FRESH")
        private String qualityLabel;
        
        // Getters and setters
        public String getProducerId() { return producerId; }
        public void setProducerId(String producerId) { this.producerId = producerId; }
        
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public Double getQualityScore() { return qualityScore; }
        public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
        
        public String getQualityLabel() { return qualityLabel; }
        public void setQualityLabel(String qualityLabel) { this.qualityLabel = qualityLabel; }
    }
    
    /**
     * Validation result helper class for batch requests.
     */
    private static class BatchValidationResult {
        private final boolean valid;
        private final String error;
        
        private BatchValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static BatchValidationResult valid() {
            return new BatchValidationResult(true, null);
        }
        
        public static BatchValidationResult invalid(String error) {
            return new BatchValidationResult(false, error);
        }
        
        public boolean isValid() { return valid; }
        public String getError() { return error; }
    }
    
    /**
     * Validate the incoming batch creation request.
     */
    private BatchValidationResult validateBatchRequest(BatchCreationRequest request) {
        if (request == null) {
            return BatchValidationResult.invalid("Request body is required");
        }
        if (request.getProducerId() == null || request.getProducerId().trim().isEmpty()) {
            return BatchValidationResult.invalid("producerId is required");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return BatchValidationResult.invalid("name is required");
        }
        if (request.getProductType() == null || request.getProductType().trim().isEmpty()) {
            return BatchValidationResult.invalid("productType is required");
        }
        if (request.getQuantity() != null && request.getQuantity() < 0) {
            return BatchValidationResult.invalid("quantity cannot be negative");
        }
        if (request.getQualityScore() != null && (request.getQualityScore() < 0 || request.getQualityScore() > 1)) {
            return BatchValidationResult.invalid("qualityScore must be between 0 and 1");
        }
        return BatchValidationResult.valid();
    }
}
