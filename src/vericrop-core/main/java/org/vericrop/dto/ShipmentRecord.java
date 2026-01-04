package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Data Transfer Object for shipment records in the blockchain ledger.
 * Represents an immutable record of a shipment transaction.
 */
public class ShipmentRecord {
    @JsonProperty("shipment_id")
    private String shipmentId;

    @JsonProperty("batch_id")
    private String batchId;

    @JsonProperty("from_party")
    private String fromParty;

    @JsonProperty("to_party")
    private String toParty;

    private String status;

    @JsonProperty("quality_score")
    private double qualityScore;

    @JsonProperty("ledger_id")
    private String ledgerId;

    @JsonProperty("ledger_hash")
    private String ledgerHash;

    private long timestamp;

    // Added fields for prime and rejection rates
    @JsonProperty("prime_rate")
    private double primeRate;

    @JsonProperty("rejection_rate")
    private double rejectionRate;

    public ShipmentRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    public ShipmentRecord(String shipmentId, String batchId, String fromParty, String toParty, String status) {
        this();
        this.shipmentId = shipmentId;
        this.batchId = batchId;
        this.fromParty = fromParty;
        this.toParty = toParty;
        this.status = status;
    }

    // Getters and Setters
    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getFromParty() {
        return fromParty;
    }

    public void setFromParty(String fromParty) {
        this.fromParty = fromParty;
    }

    public String getToParty() {
        return toParty;
    }

    public void setToParty(String toParty) {
        this.toParty = toParty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(String ledgerId) {
        this.ledgerId = ledgerId;
    }

    public String getLedgerHash() {
        return ledgerHash;
    }

    public void setLedgerHash(String ledgerHash) {
        this.ledgerHash = ledgerHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Added getters and setters for primeRate and rejectionRate
    public double getPrimeRate() {
        return primeRate;
    }

    public void setPrimeRate(double primeRate) {
        this.primeRate = primeRate;
    }

    public double getRejectionRate() {
        return rejectionRate;
    }

    public void setRejectionRate(double rejectionRate) {
        this.rejectionRate = rejectionRate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShipmentRecord that = (ShipmentRecord) o;
        return Double.compare(that.qualityScore, qualityScore) == 0 &&
                Double.compare(that.primeRate, primeRate) == 0 &&
                Double.compare(that.rejectionRate, rejectionRate) == 0 &&
                timestamp == that.timestamp &&
                Objects.equals(shipmentId, that.shipmentId) &&
                Objects.equals(batchId, that.batchId) &&
                Objects.equals(ledgerHash, that.ledgerHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipmentId, batchId, qualityScore, primeRate, rejectionRate, ledgerHash, timestamp);
    }

    @Override
    public String toString() {
        return String.format("ShipmentRecord{shipmentId='%s', batchId='%s', from='%s', to='%s', status='%s', quality=%.1f%%, prime=%.1f%%, reject=%.1f%%}",
                shipmentId, batchId, fromParty, toParty, status, qualityScore, primeRate * 100, rejectionRate * 100);
    }
}