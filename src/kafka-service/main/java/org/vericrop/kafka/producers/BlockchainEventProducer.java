package org.vericrop.kafka.producers;

import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.BlockchainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class BlockchainEventProducer {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final String topic;

    public BlockchainEventProducer() {
        this(KafkaConfig.TOPIC_BLOCKCHAIN_EVENTS);
    }

    public BlockchainEventProducer(String topic) {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.mapper = new ObjectMapper();
        this.topic = topic;
    }

    public void sendBlockchainEvent(BlockchainEvent event) {
        try {
            String eventJson = mapper.writeValueAsString(event);
            String key = event.getBatchId() != null ? event.getBatchId() : event.getBlockHash();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, eventJson);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Error sending blockchain event: " + exception.getMessage());
                } else {
                    System.out.println("⛓️ Blockchain event sent: " + event.getTransactionType() +
                            " for batch " + event.getBatchId());
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Failed to serialize/send blockchain event: " + e.getMessage());
        }
    }

    public void close() {
        producer.close();
    }
}