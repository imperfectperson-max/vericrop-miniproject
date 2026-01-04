package org.vericrop.blockchain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * High-level service wrapper for the external blockchain.jar.
 * 
 * This service provides convenient methods to interact with the blockchain
 * functionality from the external jar (acsse.csc03a3.* package) without
 * requiring compile-time dependencies.
 */
public class BlockchainJarService {
    
    private static final Logger logger = LoggerFactory.getLogger(BlockchainJarService.class);
    
    // Class names from the external blockchain.jar
    private static final String BLOCKCHAIN_CLASS = "acsse.csc03a3.Blockchain";
    private static final String BLOCK_CLASS = "acsse.csc03a3.Block";
    private static final String TRANSACTION_CLASS = "acsse.csc03a3.Transaction";
    
    private final BlockchainLoader loader;
    private final ObjectMapper objectMapper;
    
    private Object blockchainInstance;
    private boolean initialized;
    
    /**
     * Create a new BlockchainJarService using the singleton BlockchainLoader.
     */
    public BlockchainJarService() {
        this(BlockchainLoader.getInstance());
    }
    
    /**
     * Create a new BlockchainJarService with a custom BlockchainLoader.
     * 
     * @param loader the BlockchainLoader to use
     */
    public BlockchainJarService(BlockchainLoader loader) {
        this.loader = loader;
        this.objectMapper = new ObjectMapper();
        this.initialized = false;
    }
    
    /**
     * Check if the external blockchain jar is available.
     * 
     * @return true if the jar is loaded and available
     */
    public boolean isAvailable() {
        return loader.isAvailable();
    }
    
    /**
     * Initialize the blockchain service.
     * Creates a new Blockchain instance from the external jar.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean init() {
        if (!loader.isAvailable()) {
            logger.warn("Cannot initialize: blockchain.jar not available");
            return false;
        }
        
        try {
            Optional<Object> instance = loader.createInstance(BLOCKCHAIN_CLASS);
            if (instance.isPresent()) {
                this.blockchainInstance = instance.get();
                this.initialized = true;
                logger.info("BlockchainJarService initialized successfully");
                return true;
            } else {
                logger.error("Failed to create Blockchain instance");
                return false;
            }
        } catch (Exception e) {
            logger.error("Error initializing BlockchainJarService: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if the service has been initialized.
     * 
     * @return true if init() was called successfully
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Record a transaction on the blockchain.
     * 
     * @param transactionData map containing transaction data with keys like:
     *                       - "type": transaction type (e.g., "CREATE_BATCH", "TRANSFER")
     *                       - "from": sender identifier
     *                       - "to": recipient identifier (optional)
     *                       - "batchId": batch identifier (optional)
     *                       - "data": additional data as JSON string (optional)
     * @return true if the transaction was recorded successfully, false otherwise
     */
    public boolean recordTransaction(Map<String, Object> transactionData) {
        if (!initialized) {
            logger.warn("Cannot record transaction: service not initialized. Call init() first.");
            return false;
        }
        
        if (transactionData == null || transactionData.isEmpty()) {
            logger.warn("Cannot record transaction: transaction data is null or empty");
            return false;
        }
        
        try {
            // Extract transaction data with defaults
            String type = getStringValue(transactionData, "type", "UNKNOWN");
            String from = getStringValue(transactionData, "from", "unknown");
            String to = getStringValue(transactionData, "to", "");
            String batchId = getStringValue(transactionData, "batchId", "");
            String data = getDataString(transactionData);
            
            // Create Transaction instance from external jar
            Optional<Object> txInstance = loader.createInstance(
                TRANSACTION_CLASS,
                new Class<?>[]{String.class, String.class, String.class, String.class, String.class},
                new Object[]{type, from, to, batchId, data}
            );
            
            if (txInstance.isEmpty()) {
                logger.error("Failed to create Transaction instance");
                return false;
            }
            
            // Try to add the transaction to the blockchain
            // The external Blockchain may have different API, so we try common patterns
            Object transaction = txInstance.get();
            
            // Get the Transaction class for method parameter type
            Optional<Class<?>> txClassOpt = loader.loadClass(TRANSACTION_CLASS);
            if (txClassOpt.isEmpty()) {
                logger.error("Cannot invoke addTransaction: Transaction class not available");
                return false;
            }
            
            // Try addTransaction method first
            Optional<Object> result = loader.invoke(
                blockchainInstance,
                "addTransaction",
                new Class<?>[]{txClassOpt.get()},
                new Object[]{transaction}
            );
            
            if (result.isPresent()) {
                logger.info("Transaction recorded successfully: type={}, from={}", type, from);
                return true;
            }
            
            // addTransaction method not available in this blockchain implementation
            logger.debug("addTransaction method not found or returned null");
            
            return false;
        } catch (Exception e) {
            logger.error("Error recording transaction: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get the current blockchain chain as a string representation.
     * 
     * @return Optional containing the chain representation, empty if unavailable
     */
    public Optional<String> getChainAsString() {
        if (!initialized) {
            logger.warn("Cannot get chain: service not initialized");
            return Optional.empty();
        }
        
        try {
            Optional<Object> result = loader.invoke(
                blockchainInstance,
                "toString",
                new Class<?>[0],
                new Object[0]
            );
            return result.map(Object::toString);
        } catch (Exception e) {
            logger.error("Error getting chain: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Get the block count from the blockchain.
     * 
     * @return Optional containing the block count, empty if unavailable
     */
    public Optional<Integer> getBlockCount() {
        if (!initialized) {
            logger.warn("Cannot get block count: service not initialized");
            return Optional.empty();
        }
        
        try {
            // Try getBlockCount method
            Optional<Object> result = loader.invoke(
                blockchainInstance,
                "getBlockCount",
                new Class<?>[0],
                new Object[0]
            );
            
            if (result.isPresent() && result.get() instanceof Integer) {
                return Optional.of((Integer) result.get());
            }
            
            // Try size method as fallback
            result = loader.invoke(
                blockchainInstance,
                "size",
                new Class<?>[0],
                new Object[0]
            );
            
            if (result.isPresent() && result.get() instanceof Integer) {
                return Optional.of((Integer) result.get());
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error getting block count: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if the blockchain is valid.
     * 
     * @return Optional containing the validation result, empty if unavailable
     */
    public Optional<Boolean> isChainValid() {
        if (!initialized) {
            logger.warn("Cannot validate chain: service not initialized");
            return Optional.empty();
        }
        
        try {
            Optional<Object> result = loader.invoke(
                blockchainInstance,
                "isChainValid",
                new Class<?>[0],
                new Object[0]
            );
            
            if (result.isPresent() && result.get() instanceof Boolean) {
                return Optional.of((Boolean) result.get());
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error validating chain: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Get status information about the service.
     * 
     * @return map containing status information
     */
    public Map<String, Object> getStatus() {
        return Map.of(
            "available", isAvailable(),
            "initialized", initialized,
            "jarPath", loader.getLoadedJarPath() != null ? loader.getLoadedJarPath() : "N/A",
            "blockchainClass", BLOCKCHAIN_CLASS
        );
    }
    
    /**
     * Close the service and release resources.
     */
    public void close() {
        this.blockchainInstance = null;
        this.initialized = false;
        logger.info("BlockchainJarService closed");
    }
    
    // Helper methods
    
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
    
    private String getDataString(Map<String, Object> map) {
        Object data = map.get("data");
        if (data == null) {
            return "{}";
        }
        if (data instanceof String) {
            return (String) data;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize data to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
