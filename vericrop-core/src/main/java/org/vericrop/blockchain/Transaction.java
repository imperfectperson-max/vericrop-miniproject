package org.vericrop.blockchain;

public class Transaction {
    private String type; // "CREATE_BATCH", "TRANSFER", "QUALITY_CHECK"
    private String from;
    private String to;
    private String batchId;
    private String data; // Additional data as JSON

    public Transaction(String type, String from, String to, String batchId, String data) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.batchId = batchId;
        this.data = data;
    }

    // Getters and setters
    public String getType() { return type; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getBatchId() { return batchId; }
    public String getData() { return data; }
}