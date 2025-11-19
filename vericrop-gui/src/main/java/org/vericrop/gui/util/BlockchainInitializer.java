package org.vericrop.gui.util;

import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility class for initializing blockchain with different modes.
 * Supports fast dev mode (lightweight, in-memory) and full production mode.
 */
public class BlockchainInitializer {
    private static final Logger logger = LoggerFactory.getLogger(BlockchainInitializer.class);
    
    public enum Mode {
        DEV,    // Fast mode with minimal blockchain initialization
        PROD    // Full blockchain initialization
    }
    
    /**
     * Initialize blockchain asynchronously based on mode.
     * 
     * @param mode The initialization mode (DEV or PROD)
     * @param progressCallback Callback for progress updates
     * @return CompletableFuture that completes with the initialized blockchain
     */
    public static CompletableFuture<Blockchain> initializeAsync(Mode mode, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (mode == Mode.DEV) {
                    return createFastDevChain(progressCallback);
                } else {
                    return createFullChain(progressCallback);
                }
            } catch (Exception e) {
                logger.error("Blockchain initialization failed", e);
                throw new RuntimeException("Failed to initialize blockchain", e);
            }
        });
    }
    
    /**
     * Create a lightweight development blockchain.
     * Fast initialization with minimal data for quick iteration.
     */
    private static Blockchain createFastDevChain(Consumer<String> progressCallback) {
        logger.info("🚀 Initializing blockchain in FAST DEV mode");
        notifyProgress(progressCallback, "Starting fast dev blockchain...");
        
        // Use in-memory blockchain file for dev
        Blockchain blockchain = new Blockchain("dev_blockchain.json");
        
        // Add a few sample blocks for testing
        notifyProgress(progressCallback, "Creating sample dev blocks...");
        addSampleDevBlocks(blockchain);
        
        logger.info("✅ Fast dev blockchain initialized with {} blocks", blockchain.getChain().size());
        notifyProgress(progressCallback, "Dev blockchain ready!");
        
        return blockchain;
    }
    
    /**
     * Create a full production blockchain.
     * Complete initialization with validation and persistence.
     */
    private static Blockchain createFullChain(Consumer<String> progressCallback) {
        logger.info("⛓️ Initializing blockchain in FULL PRODUCTION mode");
        notifyProgress(progressCallback, "Starting full blockchain initialization...");
        
        // Use persistent blockchain file
        Blockchain blockchain = new Blockchain("vericrop_chain.json");
        
        notifyProgress(progressCallback, "Loading existing blockchain data...");
        
        // Validate existing chain
        notifyProgress(progressCallback, "Validating blockchain integrity...");
        if (!blockchain.isChainValid()) {
            logger.warn("⚠️ Blockchain validation failed, creating new chain");
            blockchain = new Blockchain("vericrop_chain.json");
        }
        
        logger.info("✅ Full blockchain initialized with {} blocks", blockchain.getChain().size());
        notifyProgress(progressCallback, "Production blockchain ready!");
        
        return blockchain;
    }
    
    /**
     * Add sample blocks for development/testing.
     */
    private static void addSampleDevBlocks(Blockchain blockchain) {
        try {
            // Add a few lightweight sample transactions
            List<Transaction> sampleTx1 = new ArrayList<>();
            sampleTx1.add(new Transaction(
                "CREATE_BATCH",
                "dev_farmer_001",
                "system",
                "DEV_BATCH_001",
                "{\"product\":\"apple\",\"quality\":\"prime\"}"
            ));
            
            blockchain.addBlock(sampleTx1, "dev_hash_001", "dev_farmer_001");
            
            List<Transaction> sampleTx2 = new ArrayList<>();
            sampleTx2.add(new Transaction(
                "CREATE_BATCH",
                "dev_farmer_002",
                "system",
                "DEV_BATCH_002",
                "{\"product\":\"orange\",\"quality\":\"standard\"}"
            ));
            
            blockchain.addBlock(sampleTx2, "dev_hash_002", "dev_farmer_002");
            
            logger.debug("Added {} sample blocks for dev mode", 2);
        } catch (Exception e) {
            logger.warn("Could not add sample blocks: {}", e.getMessage());
        }
    }
    
    /**
     * Helper to safely call progress callback.
     */
    private static void notifyProgress(Consumer<String> callback, String message) {
        if (callback != null) {
            try {
                callback.accept(message);
            } catch (Exception e) {
                logger.warn("Progress callback failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Determine mode from environment variable or system property.
     */
    public static Mode getModeFromEnvironment() {
        // Check system property first
        String modeProp = System.getProperty("vericrop.mode");
        if (modeProp != null) {
            return parseMode(modeProp);
        }
        
        // Check environment variable
        String modeEnv = System.getenv("VERICROP_MODE");
        if (modeEnv != null) {
            return parseMode(modeEnv);
        }
        
        // Default to DEV for safety and quick iteration
        logger.info("No vericrop.mode specified, defaulting to DEV mode");
        return Mode.DEV;
    }
    
    private static Mode parseMode(String modeStr) {
        if (modeStr == null) {
            return Mode.DEV;
        }
        
        String normalized = modeStr.trim().toUpperCase();
        switch (normalized) {
            case "PROD":
            case "PRODUCTION":
                return Mode.PROD;
            case "DEV":
            case "DEVELOPMENT":
            default:
                return Mode.DEV;
        }
    }
}
