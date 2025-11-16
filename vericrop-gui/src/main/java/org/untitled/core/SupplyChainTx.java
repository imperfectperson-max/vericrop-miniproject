package org.untitled.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight supply chain transaction representation used in blocks.
 */
public class SupplyChainTx {
    private final String type;      // e.g., "CreateShipment", "ConfirmReceipt"
    private final String details;   // freeform details or JSON string
    private final long timestamp;

    public SupplyChainTx(String type, String details) {
        this.type = type;
        this.details = details == null ? "" : details;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getType() {
        return type;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SupplyChainTx{" +
                "type='" + type + '\'' +
                ", details='" + details + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyChainTx)) return false;
        SupplyChainTx that = (SupplyChainTx) o;
        return timestamp == that.timestamp &&
                Objects.equals(type, that.type) &&
                Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, details, timestamp);
    }
}