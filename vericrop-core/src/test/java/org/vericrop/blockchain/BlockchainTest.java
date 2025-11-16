package org.vericrop.blockchain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Block and Blockchain classes.
 */
public class BlockchainTest {

    @Test
    public void testGenesisBlockCreation() {
        Blockchain blockchain = new Blockchain();
        assertEquals(1, blockchain.size());
        Block genesisBlock = blockchain.getLatestBlock();
        assertEquals(0, genesisBlock.getIndex());
        assertEquals("0", genesisBlock.getPreviousHash());
    }

    @Test
    public void testAddBlock() {
        Blockchain blockchain = new Blockchain();
        blockchain.addBlock("Block 1 Data");
        blockchain.addBlock("Block 2 Data");
        
        assertEquals(3, blockchain.size());
        Block latestBlock = blockchain.getLatestBlock();
        assertEquals(2, latestBlock.getIndex());
    }

    @Test
    public void testBlockchainValidation() {
        Blockchain blockchain = new Blockchain();
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");
        
        assertTrue(blockchain.isValid());
    }

    @Test
    public void testBlockHashCalculation() {
        Block block = new Block(0, "0", "Test Data");
        assertNotNull(block.getHash());
        assertTrue(block.getHash().length() > 0);
    }

    @Test
    public void testPreviousHashLinking() {
        Blockchain blockchain = new Blockchain();
        blockchain.addBlock("Block 1");
        
        Block firstBlock = blockchain.getChain().get(0);
        Block secondBlock = blockchain.getChain().get(1);
        
        assertEquals(firstBlock.getHash(), secondBlock.getPreviousHash());
    }
}
