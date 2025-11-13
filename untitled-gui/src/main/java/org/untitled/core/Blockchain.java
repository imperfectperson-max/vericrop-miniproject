package org.untitled.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal in-memory blockchain for the mini-project.
 * - genesis block is created on construction
 * - addBlock creates a new block linked to the previous
 * - isValid checks previousHash linkage and that each block's stored hash matches recalculated hash
 *
 * Note: This is intentionally small and educational — for the project demo this is sufficient.
 */
public class Blockchain {
    private final List<Block> chain = new ArrayList<>();

    public Blockchain() {
        // create genesis block with deterministic fields
        Block genesis = new Block(0, 0L, "0", "genesis", "genesis-data", List.of(new SupplyChainTx("genesis", "genesis")));
        chain.add(genesis);
    }

    public synchronized Block addBlock(String participant, String dataHash, List<SupplyChainTx> transactions) {
        int index = chain.size();
        String previousHash = getLatest().getBlockHash();
        Block block = new Block(index, previousHash, participant, dataHash, transactions);
        chain.add(block);
        return block;
    }

    public synchronized List<Block> getChain() {
        return List.copyOf(chain);
    }

    public synchronized Block getLatest() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Validate the full chain:
     * - each block.previousHash must equal the previous block's blockHash
     * - each block's stored hash must equal calculated hash from its fields
     */
    public synchronized boolean isValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // verify linkage
            if (!current.getPreviousHash().equals(previous.getBlockHash())) {
                return false;
            }
            // verify hash integrity
            String recalculated = Block.calculateHash(
                    current.getIndex(),
                    current.getTimestamp(),
                    current.getPreviousHash(),
                    current.getParticipant(),
                    current.getDataHash(),
                    current.getTransactions()
            );
            if (!recalculated.equals(current.getBlockHash())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tamper helper for demo/testing: replace a block's dataHash (NOT for production).
     * Used to show isValid() returns false after tampering.
     */
    public synchronized void tamperWithBlockData(int index, String newDataHash) {
        if (index <= 0 || index >= chain.size()) throw new IllegalArgumentException("invalid index to tamper");
        Block old = chain.get(index);
        Block tampered = new Block(old.getIndex(), old.getTimestamp(), old.getPreviousHash(), old.getParticipant(), newDataHash, old.getTransactions());
        chain.set(index, tampered);
    }
}