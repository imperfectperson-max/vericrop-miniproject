package org.vericrop.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class Blockchain {
    private List<Block> chain;
    private final ObjectMapper mapper;
    private final String blockchainFile;
    private final ReentrantLock lock;

    public Blockchain() {
        this("blockchain.json");
    }

    public Blockchain(String blockchainFile) {
        this.chain = new ArrayList<>();
        this.mapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties globally
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.blockchainFile = blockchainFile;
        this.lock = new ReentrantLock();

        // Create genesis block
        createGenesisBlock();

        // Try to load existing blockchain
        loadFromFile();
    }

    private void createGenesisBlock() {
        if (!chain.isEmpty()) return; // Already has genesis block

        List<Transaction> genesisTransactions = new ArrayList<>();
        genesisTransactions.add(new Transaction("GENESIS", "system", "system", "0", "{}"));
        Block genesis = new Block(0, "0", genesisTransactions,
                "genesis_hash", "system");
        chain.add(genesis);

        // Save the genesis block
        saveToFile();
    }

    public Block getLatestBlock() {
        lock.lock();
        try {
            if (chain.isEmpty()) {
                createGenesisBlock();
            }
            return chain.get(chain.size() - 1);
        } finally {
            lock.unlock();
        }
    }

    public Block addBlock(List<Transaction> transactions, String dataHash, String participant) {
        lock.lock();
        try {
            // Validate transactions
            if (transactions == null || transactions.isEmpty()) {
                throw new IllegalArgumentException("Block must contain at least one transaction");
            }

            for (Transaction tx : transactions) {
                if (!tx.isValid()) {
                    throw new IllegalArgumentException("Invalid transaction: " + tx);
                }
            }

            if (participant == null || participant.trim().isEmpty()) {
                throw new IllegalArgumentException("Participant cannot be null or empty");
            }

            Block latestBlock = getLatestBlock();
            Block newBlock = new Block(chain.size(), latestBlock.getHash(),
                    transactions, dataHash, participant);

            // Verify block hash is correct
            String calculatedHash = newBlock.calculateHash();
            if (!newBlock.getHash().equals(calculatedHash)) {
                throw new IllegalStateException("Block hash is invalid. Expected: " + calculatedHash +
                        ", Got: " + newBlock.getHash());
            }

            chain.add(newBlock);

            // Auto-save to file in background thread to avoid blocking
            new Thread(this::saveToFile).start();

            return newBlock;
        } finally {
            lock.unlock();
        }
    }

    public boolean isChainValid() {
        lock.lock();
        try {
            return isChainValid(this.chain);
        } finally {
            lock.unlock();
        }
    }

    public void saveToFile() {
        lock.lock();
        try {
            File file = new File(blockchainFile);
            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                try {
                    Files.createDirectories(parentDir.toPath());
                } catch (IOException e) {
                    System.err.println("❌ Failed to create parent directories: " + e.getMessage());
                    throw e;
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, chain);
            System.out.println("✅ Blockchain saved to file: " + chain.size() + " blocks");
        } catch (IOException e) {
            System.err.println("❌ Warning: Could not save blockchain to file: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void loadFromFile() {
        lock.lock();
        try {
            File file = new File(blockchainFile);
            if (!file.exists()) {
                System.out.println("ℹ️ No existing blockchain file found. Starting fresh.");
                return;
            }

            System.out.println("🔄 Loading blockchain from file: " + file.getAbsolutePath());

            List<Block> loadedChain = mapper.readValue(file,
                    mapper.getTypeFactory().constructCollectionType(List.class, Block.class));

            System.out.println("📥 Loaded " + loadedChain.size() + " blocks from file");

            // Validate loaded chain
            if (isChainValid(loadedChain)) {
                this.chain = loadedChain;
                System.out.println("✅ Blockchain loaded successfully: " + chain.size() + " blocks");
            } else {
                System.err.println("❌ Loaded blockchain is invalid. Starting fresh.");
                // Create backup of corrupted file
                Path sourcePath = file.toPath();
                Path backupPath = Path.of(blockchainFile + ".corrupted." + System.currentTimeMillis());
                try {
                    Files.move(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("💾 Corrupted file backed up as: " + backupPath.getFileName());
                } catch (IOException moveEx) {
                    System.err.println("⚠️ Failed to backup corrupted file: " + moveEx.getMessage());
                }

                this.chain = new ArrayList<>();
                createGenesisBlock();
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading blockchain: " + e.getMessage());
            // If loading fails, start with fresh blockchain but don't delete the file
            System.out.println("🔄 Starting with fresh blockchain due to load error");
            this.chain = new ArrayList<>();
            createGenesisBlock();
        } finally {
            lock.unlock();
        }
    }

    private boolean isChainValid(List<Block> chainToValidate) {
        if (chainToValidate == null || chainToValidate.isEmpty()) {
            System.err.println("Chain is null or empty");
            return false;
        }

        // Check genesis block
        Block genesis = chainToValidate.get(0);
        if (genesis.getIndex() != 0) {
            System.err.println("Genesis block has wrong index: " + genesis.getIndex());
            return false;
        }
        if (!"0".equals(genesis.getPreviousHash())) {
            System.err.println("Genesis block has wrong previous hash: " + genesis.getPreviousHash());
            return false;
        }

        String genesisCalculatedHash = genesis.calculateHash();
        if (!genesis.getHash().equals(genesisCalculatedHash)) {
            System.err.println("Genesis block hash invalid. Expected: " + genesisCalculatedHash + ", Got: " + genesis.getHash());
            return false;
        }

        // Check subsequent blocks
        for (int i = 1; i < chainToValidate.size(); i++) {
            Block current = chainToValidate.get(i);
            Block previous = chainToValidate.get(i - 1);

            // Check if current block's hash is correct
            String currentCalculatedHash = current.calculateHash();
            if (!current.getHash().equals(currentCalculatedHash)) {
                System.err.println("Block " + i + " has invalid hash. Expected: " + currentCalculatedHash + ", Got: " + current.getHash());
                return false;
            }

            // Check if previous hash matches
            if (!current.getPreviousHash().equals(previous.getHash())) {
                System.err.println("Block " + i + " has incorrect previous hash. Expected: " + previous.getHash() + ", Got: " + current.getPreviousHash());
                return false;
            }

            // Check block index
            if (current.getIndex() != i) {
                System.err.println("Block " + i + " has incorrect index. Expected: " + i + ", Got: " + current.getIndex());
                return false;
            }

            // Check transactions
            for (Transaction tx : current.getTransactions()) {
                if (!tx.isValid()) {
                    System.err.println("Block " + i + " contains invalid transaction: " + tx);
                    return false;
                }
            }
        }

        System.out.println("✅ Chain validation passed for " + chainToValidate.size() + " blocks");
        return true;
    }

    public List<Block> getChain() {
        lock.lock();
        try {
            return new ArrayList<>(chain);
        } finally {
            lock.unlock();
        }
    }

    public int getBlockCount() {
        lock.lock();
        try {
            return chain.size();
        } finally {
            lock.unlock();
        }
    }

    public List<Transaction> getTransactionsForBatch(String batchId) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }

        lock.lock();
        try {
            List<Transaction> batchTransactions = new ArrayList<>();
            for (Block block : chain) {
                for (Transaction tx : block.getTransactions()) {
                    if (batchId.equals(tx.getBatchId())) {
                        batchTransactions.add(tx);
                    }
                }
            }
            return batchTransactions;
        } finally {
            lock.unlock();
        }
    }

    public Block getBlockByIndex(int index) {
        lock.lock();
        try {
            if (index < 0 || index >= chain.size()) {
                throw new IllegalArgumentException("Block index out of bounds: " + index);
            }
            return chain.get(index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("Blockchain{blocks=%d, valid=%s}",
                chain.size(), isChainValid());
    }
}