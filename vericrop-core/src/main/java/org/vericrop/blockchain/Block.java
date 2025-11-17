package org.vericrop.blockchain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Block {
    private int index;
    private long timestamp;
    private String previousHash;
    private String hash;
    private List<Transaction> transactions;
    private String dataHash; // Hash of ML report data
    private String participant; // "farmer", "transporter", "warehouse"

    public Block(int index, String previousHash, List<Transaction> transactions,
                 String dataHash, String participant) {
        this.index = index;
        this.timestamp = System.currentTimeMillis();
        this.previousHash = previousHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.dataHash = dataHash;
        this.participant = participant;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = index + timestamp + previousHash +
                    transactions.hashCode() + dataHash + participant;
            byte[] hash = digest.digest(input.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Getters and setters
    public int getIndex() { return index; }
    public long getTimestamp() { return timestamp; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
    public List<Transaction> getTransactions() { return transactions; }
    public String getDataHash() { return dataHash; }
    public String getParticipant() { return participant; }

    public void setHash(String hash) { this.hash = hash; }
}