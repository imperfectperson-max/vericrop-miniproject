package org.vericrop.blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Represents a single block in the blockchain.
 * Contains index, timestamp, previous hash, data, and its own hash.
 */
public class Block {
    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final String data;
    private final String hash;

    public Block(int index, long timestamp, String previousHash, String data) {
        this.index = index;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.data = data;
        this.hash = calculateHash();
    }

    public Block(int index, String previousHash, String data) {
        this(index, Instant.now().toEpochMilli(), previousHash, data);
    }

    /**
     * Calculate SHA-256 hash of the block's contents.
     */
    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = index + timestamp + previousHash + data;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public int getIndex() {
        return index;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getData() {
        return data;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", timestamp=" + timestamp +
                ", previousHash='" + previousHash + '\'' +
                ", data='" + data + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
