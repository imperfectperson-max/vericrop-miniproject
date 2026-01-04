package org.vericrop.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true) // Add this to ignore unknown fields
public class Transaction {
    private final String type; // "CREATE_BATCH", "TRANSFER", "QUALITY_CHECK"
    private final String from;
    private final String to;
    private final String batchId;
    private final String data; // Additional data as JSON

    @JsonCreator
    public Transaction(@JsonProperty("type") String type,
                       @JsonProperty("from") String from,
                       @JsonProperty("to") String to,
                       @JsonProperty("batchId") String batchId,
                       @JsonProperty("data") String data) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type cannot be null or empty");
        }
        if (from == null || from.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction 'from' cannot be null or empty");
        }

        this.type = type.trim();
        this.from = from.trim();
        this.to = to != null ? to.trim() : "";
        this.batchId = batchId != null ? batchId.trim() : "";
        this.data = data != null ? data : "{}";
    }

    // Validation method - mark as @JsonIgnore to prevent serialization
    @JsonIgnore
    public boolean isValid() {
        if (type == null || type.trim().isEmpty() || from == null || from.trim().isEmpty()) {
            return false;
        }

        // Quality checks require batchId
        if ("QUALITY_CHECK".equals(type) && (batchId == null || batchId.trim().isEmpty())) {
            return false;
        }

        // Transfer transactions require 'to' field
        if ("TRANSFER".equals(type) && (to == null || to.trim().isEmpty())) {
            return false;
        }

        return true;
    }

    // Getters
    public String getType() { return type; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getBatchId() { return batchId; }
    public String getData() { return data; }

    @Override
    public String toString() {
        return String.format("Transaction{type='%s', from='%s', to='%s', batchId='%s'}",
                type, from, to, batchId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, from, to, batchId, data);
    }
}