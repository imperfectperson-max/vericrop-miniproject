package org.vericrop.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class BlockchainEvent {
    private String transactionType; // "CREATE_BATCH", "QUALITY_CHECK", "TRANSFER"
    private String batchId;
    private String fromParticipant;
    private String toParticipant;
    private String blockHash;
    private int blockIndex;
    private String dataHash;
    private long timestamp;
    private String additionalData;

    public BlockchainEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public BlockchainEvent(String transactionType, String batchId, String fromParticipant,
                           String blockHash, int blockIndex) {
        this();
        this.transactionType = transactionType;
        this.batchId = batchId;
        this.fromParticipant = fromParticipant;
        this.blockHash = blockHash;
        this.blockIndex = blockIndex;
    }

    // Getters and Setters
    @JsonProperty("transaction_type")
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    @JsonProperty("batch_id")
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    @JsonProperty("from_participant")
    public String getFromParticipant() { return fromParticipant; }
    public void setFromParticipant(String fromParticipant) { this.fromParticipant = fromParticipant; }

    @JsonProperty("to_participant")
    public String getToParticipant() { return toParticipant; }
    public void setToParticipant(String toParticipant) { this.toParticipant = toParticipant; }

    @JsonProperty("block_hash")
    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }

    @JsonProperty("block_index")
    public int getBlockIndex() { return blockIndex; }
    public void setBlockIndex(int blockIndex) { this.blockIndex = blockIndex; }

    @JsonProperty("data_hash")
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @JsonProperty("additional_data")
    public String getAdditionalData() { return additionalData; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }

    @Override
    public String toString() {
        return String.format("BlockchainEvent{type='%s', batchId='%s', block=%d}",
                transactionType, batchId, blockIndex);
    }
}