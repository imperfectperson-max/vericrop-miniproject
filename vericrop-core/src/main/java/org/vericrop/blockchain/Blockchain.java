package org.vericrop.blockchain;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple blockchain implementation with basic validation.
 * Maintains a chain of blocks and provides methods to add blocks and validate the chain.
 */
public class Blockchain {
    private final List<Block> chain;

    public Blockchain() {
        chain = new ArrayList<>();
        // Create genesis block
        chain.add(createGenesisBlock());
    }

    /**
     * Create the first block in the chain (genesis block).
     */
    private Block createGenesisBlock() {
        return new Block(0, 0L, "0", "Genesis Block");
    }

    /**
     * Get the latest block in the chain.
     */
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Add a new block to the chain.
     */
    public void addBlock(String data) {
        Block previousBlock = getLatestBlock();
        Block newBlock = new Block(
            chain.size(),
            previousBlock.getHash(),
            data
        );
        chain.add(newBlock);
    }

    /**
     * Get the entire chain.
     */
    public List<Block> getChain() {
        return new ArrayList<>(chain);
    }

    /**
     * Validate the blockchain by checking:
     * 1. Each block's hash is correct
     * 2. Each block's previousHash matches the previous block's hash
     */
    public boolean isValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i);
            Block previousBlock = chain.get(i - 1);

            // Check if the current block's hash is correct
            Block testBlock = new Block(
                currentBlock.getIndex(),
                currentBlock.getTimestamp(),
                currentBlock.getPreviousHash(),
                currentBlock.getData()
            );
            if (!currentBlock.getHash().equals(testBlock.getHash())) {
                return false;
            }

            // Check if previousHash matches the previous block's hash
            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the size of the blockchain.
     */
    public int size() {
        return chain.size();
    }
}
