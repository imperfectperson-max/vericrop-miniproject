package org.vericrop.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    private List<Block> chain;
    private ObjectMapper mapper;

    public Blockchain() {
        this.chain = new ArrayList<>();
        this.mapper = new ObjectMapper();
        // Create genesis block
        createGenesisBlock();
    }

    private void createGenesisBlock() {
        List<Transaction> genesisTransactions = new ArrayList<>();
        genesisTransactions.add(new Transaction("GENESIS", "system", "system", "0", "{}"));
        Block genesis = new Block(0, "0", genesisTransactions, "genesis", "system");
        chain.add(genesis);
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public Block addBlock(List<Transaction> transactions, String dataHash, String participant) {
        Block latestBlock = getLatestBlock();
        Block newBlock = new Block(chain.size(), latestBlock.getHash(),
                transactions, dataHash, participant);
        chain.add(newBlock);
        return newBlock;
    }

    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // Check if hash is correct
            if (!current.getHash().equals(current.calculateHash())) {
                return false;
            }

            // Check if previous hash matches
            if (!current.getPreviousHash().equals(previous.getHash())) {
                return false;
            }
        }
        return true;
    }

    public void saveToFile(String filename) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), chain);
    }

    public void loadFromFile(String filename) throws IOException {
        chain = mapper.readValue(new File(filename),
                mapper.getTypeFactory().constructCollectionType(List.class, Block.class));
    }

    public List<Block> getChain() {
        return new ArrayList<>(chain);
    }
}