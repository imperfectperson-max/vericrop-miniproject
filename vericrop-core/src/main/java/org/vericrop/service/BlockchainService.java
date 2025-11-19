package org.vericrop.service;

import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Transaction;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockchainService {
    private final Blockchain blockchain;
    private final ExecutorService executor;

    public BlockchainService(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.executor = Executors.newFixedThreadPool(2); // Dedicated thread pool for blockchain ops
    }

    public CompletableFuture<Block> addBlockAsync(List<Transaction> transactions, String dataHash, String participant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("⛓️ Starting blockchain operation...");
                Block newBlock = blockchain.addBlock(transactions, dataHash, participant);
                System.out.println("✅ Blockchain operation completed - Block #" + newBlock.getIndex());
                return newBlock;
            } catch (Exception e) {
                System.err.println("❌ Blockchain operation failed: " + e.getMessage());
                throw new RuntimeException("Blockchain operation failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> validateChainAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return blockchain.isChainValid();
            } catch (Exception e) {
                throw new RuntimeException("Chain validation failed", e);
            }
        }, executor);
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}