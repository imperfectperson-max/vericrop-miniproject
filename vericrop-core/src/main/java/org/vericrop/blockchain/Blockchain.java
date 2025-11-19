package org.vericrop.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Blockchain {
    private List<Block> chain;
    private final ObjectMapper mapper;
    private final String blockchainFile;

    public Blockchain() {
        this("blockchain.json");
    }

    public Blockchain(String blockchainFile) {
        this.chain = new ArrayList<>();
        this.mapper = new ObjectMapper();
        this.blockchainFile = blockchainFile;
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
    }

    public Block getLatestBlock() {
        if (chain.isEmpty()) {
            createGenesisBlock();
        }
        return chain.get(chain.size() - 1);
    }

    public Block addBlock(List<Transaction> transactions, String dataHash, String participant) {
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

        // Auto-save to file
        saveToFile();

        return newBlock;
    }

    public boolean isChainValid() {
        return isChainValid(this.chain);
    }

    public void saveToFile() {
        try {
            File file = new File(blockchainFile);
            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, chain);
        } catch (IOException e) {
            System.err.println("Warning: Could not save blockchain to file: " + e.getMessage());
        }
    }

    public void loadFromFile() {
        File file = new File(blockchainFile);
        if (!file.exists()) {
            System.out.println("No existing blockchain file found. Starting fresh.");
            return;
        }

        try {
            List<Block> loadedChain = mapper.readValue(file,
                    mapper.getTypeFactory().constructCollectionType(List.class, Block.class));

            // Validate loaded chain
            if (isChainValid(loadedChain)) {
                this.chain = loadedChain;
                System.out.println("Blockchain loaded from file: " + chain.size() + " blocks");
            } else {
                System.err.println("Loaded blockchain is invalid. Starting fresh.");
                this.chain = new ArrayList<>();
                createGenesisBlock();
            }
        } catch (IOException e) {
            System.err.println("Error loading blockchain: " + e.getMessage());
            System.out.println("Starting with fresh blockchain.");
        }
    }

    private boolean isChainValid(List<Block> chainToValidate) {
        if (chainToValidate == null || chainToValidate.isEmpty()) return false;

        // Check genesis block
        Block genesis = chainToValidate.get(0);
        if (genesis.getIndex() != 0 ||
                !"0".equals(genesis.getPreviousHash()) ||
                !genesis.getHash().equals(genesis.calculateHash())) {
            return false;
        }

        // Check subsequent blocks
        for (int i = 1; i < chainToValidate.size(); i++) {
            Block current = chainToValidate.get(i);
            Block previous = chainToValidate.get(i - 1);

            // Check if current block's hash is correct
            if (!current.getHash().equals(current.calculateHash())) {
                System.err.println("Block " + i + " has invalid hash");
                return false;
            }

            // Check if previous hash matches
            if (!current.getPreviousHash().equals(previous.getHash())) {
                System.err.println("Block " + i + " has incorrect previous hash");
                return false;
            }

            // Check block index
            if (current.getIndex() != i) {
                System.err.println("Block " + i + " has incorrect index");
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
        return true;
    }

    public List<Block> getChain() {
        return new ArrayList<>(chain);
    }

    public int getBlockCount() {
        return chain.size();
    }

    public List<Transaction> getTransactionsForBatch(String batchId) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }

        List<Transaction> batchTransactions = new ArrayList<>();
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                if (batchId.equals(tx.getBatchId())) {
                    batchTransactions.add(tx);
                }
            }
        }
        return batchTransactions;
    }

    public Block getBlockByIndex(int index) {
        if (index < 0 || index >= chain.size()) {
            throw new IllegalArgumentException("Block index out of bounds: " + index);
        }
        return chain.get(index);
    }

    @Override
    public String toString() {
        return String.format("Blockchain{blocks=%d, valid=%s}",
                chain.size(), isChainValid());
    }
}