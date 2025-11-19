package org.vericrop.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true) // Add this to ignore unknown fields
public class Block {
    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final List<Transaction> transactions;
    private final String dataHash;
    private final String participant;
    private String hash;

    @JsonCreator
    public Block(@JsonProperty("index") int index,
                 @JsonProperty("timestamp") Long timestamp,
                 @JsonProperty("previousHash") String previousHash,
                 @JsonProperty("transactions") List<Transaction> transactions,
                 @JsonProperty("dataHash") String dataHash,
                 @JsonProperty("participant") String participant,
                 @JsonProperty("hash") String hash) { // Added hash parameter
        this.index = index;
        this.timestamp = timestamp != null ? timestamp : System.currentTimeMillis();
        this.previousHash = previousHash != null ? previousHash : "0";
        this.transactions = transactions != null ? new ArrayList<>(transactions) : new ArrayList<>();
        this.dataHash = dataHash != null ? dataHash : "";
        this.participant = participant != null ? participant : "unknown";
        this.hash = hash != null ? hash : calculateHash(); // Use provided hash or calculate
    }

    // Original constructor for backward compatibility
    public Block(int index, String previousHash, List<Transaction> transactions,
                 String dataHash, String participant) {
        this(index, System.currentTimeMillis(), previousHash, transactions, dataHash, participant, null);
    }

    @JsonIgnore // Mark as ignore to prevent circular reference in JSON
    public String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder input = new StringBuilder();

            // Build deterministic string for hashing - use stored timestamp
            input.append(index);
            input.append(timestamp);
            input.append(previousHash);
            input.append(dataHash != null ? dataHash : "");
            input.append(participant != null ? participant : "");

            // Include all transactions in hash calculation
            for (Transaction tx : transactions) {
                input.append(tx.getType());
                input.append(tx.getFrom());
                input.append(tx.getTo() != null ? tx.getTo() : "");
                input.append(tx.getBatchId() != null ? tx.getBatchId() : "");
                input.append(tx.getData() != null ? tx.getData() : "");
            }

            byte[] hashBytes = digest.digest(input.toString().getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Getters
    public int getIndex() { return index; }
    public long getTimestamp() { return timestamp; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
    public List<Transaction> getTransactions() { return new ArrayList<>(transactions); }
    public String getDataHash() { return dataHash; }
    public String getParticipant() { return participant; }

    // Setter for deserialization
    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return index == block.index &&
                timestamp == block.timestamp &&
                Objects.equals(previousHash, block.previousHash) &&
                Objects.equals(transactions, block.transactions) &&
                Objects.equals(dataHash, block.dataHash) &&
                Objects.equals(participant, block.participant) &&
                Objects.equals(hash, block.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, timestamp, previousHash, transactions, dataHash, participant, hash);
    }
}