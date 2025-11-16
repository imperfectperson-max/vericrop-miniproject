package org.untitled.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Simple block structure for the mini-project ledger.
 */
public class Block {
    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final String participant;
    private final String dataHash; // hash of off-chain ML report / sensor logs
    private final List<SupplyChainTx> transactions;
    private final String blockHash;

    public Block(int index, long timestamp, String previousHash, String participant, String dataHash, List<SupplyChainTx> transactions) {
        this.index = index;
        this.timestamp = timestamp;
        this.previousHash = previousHash == null ? "0" : previousHash;
        this.participant = participant;
        this.dataHash = dataHash == null ? "" : dataHash;
        this.transactions = transactions;
        this.blockHash = calculateHash(index, timestamp, this.previousHash, this.participant, this.dataHash, this.transactions);
    }

    public Block(int index, String previousHash, String participant, String dataHash, List<SupplyChainTx> transactions) {
        this(index, Instant.now().toEpochMilli(), previousHash, participant, dataHash, transactions);
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

    public String getParticipant() {
        return participant;
    }

    public String getDataHash() {
        return dataHash;
    }

    public List<SupplyChainTx> getTransactions() {
        return transactions;
    }

    public String getBlockHash() {
        return blockHash;
    }

    /**
     * Deterministic hash calculation from block fields. Used both when creating and validating blocks.
     */
    public static String calculateHash(int index, long timestamp, String previousHash, String participant, String dataHash, List<SupplyChainTx> transactions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String txString = (transactions == null || transactions.isEmpty())
                    ? ""
                    : transactions.stream().map(Objects::toString).collect(Collectors.joining(","));
            String payload = index + "|" + timestamp + "|" + previousHash + "|" + participant + "|" + dataHash + "|" + txString;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", timestamp=" + timestamp +
                ", previousHash='" + previousHash + '\'' +
                ", participant='" + participant + '\'' +
                ", dataHash='" + dataHash + '\'' +
                ", transactions=" + transactions +
                ", blockHash='" + blockHash + '\'' +
                '}';
    }
}