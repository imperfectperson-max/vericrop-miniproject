package org.vericrop.service;

import org.vericrop.blockchain.Blockchain;
import org.vericrop.blockchain.Block;
import org.vericrop.blockchain.Transaction;
import java.util.ArrayList;
import java.util.List;

public class BlockchainService {
    private final Blockchain blockchain;
    private final MLServiceClient mlClient;

    public BlockchainService() {
        this.blockchain = new Blockchain();
        this.mlClient = new MLServiceClient();
    }

    public Block addQualityCheck(String batchId, String participant,
                                 MLServiceClient.MLPrediction prediction) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }
        if (participant == null || participant.trim().isEmpty()) {
            throw new IllegalArgumentException("Participant cannot be null or empty");
        }
        if (prediction == null) {
            throw new IllegalArgumentException("Prediction cannot be null");
        }

        List<Transaction> transactions = new ArrayList<>();

        Transaction qualityTx = new Transaction(
                "QUALITY_CHECK",
                participant,
                "blockchain",
                batchId,
                prediction.getReport()
        );

        if (!qualityTx.isValid()) {
            throw new IllegalArgumentException("Invalid quality check transaction");
        }

        transactions.add(qualityTx);

        return blockchain.addBlock(transactions, prediction.getData_hash(), participant);
    }

    public Block createBatch(String batchId, String farmerId, String initialData) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }
        if (farmerId == null || farmerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Farmer ID cannot be null or empty");
        }

        List<Transaction> transactions = new ArrayList<>();

        Transaction createTx = new Transaction(
                "CREATE_BATCH",
                farmerId,
                "blockchain",
                batchId,
                initialData != null ? initialData : "{}"
        );

        if (!createTx.isValid()) {
            throw new IllegalArgumentException("Invalid create batch transaction");
        }

        transactions.add(createTx);

        String dataHash = initialData != null ?
                String.valueOf(initialData.hashCode()) : "empty_data";

        return blockchain.addBlock(transactions, dataHash, farmerId);
    }

    public Block transferBatch(String batchId, String from, String to, String transferData) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID cannot be null or empty");
        }
        if (from == null || from.trim().isEmpty()) {
            throw new IllegalArgumentException("From cannot be null or empty");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("To cannot be null or empty");
        }

        List<Transaction> transactions = new ArrayList<>();

        Transaction transferTx = new Transaction(
                "TRANSFER",
                from,
                to,
                batchId,
                transferData != null ? transferData : "{}"
        );

        if (!transferTx.isValid()) {
            throw new IllegalArgumentException("Invalid transfer transaction");
        }

        transactions.add(transferTx);

        String dataHash = transferData != null ?
                String.valueOf(transferData.hashCode()) : "empty_transfer";

        return blockchain.addBlock(transactions, dataHash, from);
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public MLServiceClient getMlClient() {
        return mlClient;
    }

    public boolean validateBlockchain() {
        return blockchain.isChainValid();
    }

    public int getBlockCount() {
        return blockchain.getBlockCount();
    }
}