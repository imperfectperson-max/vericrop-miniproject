package org.vericrop.kafka.producers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.SimulationControlEvent;

import java.util.concurrent.Future;

/**
 * Kafka producer for simulation control events.
 * Used by ProducerController to broadcast simulation START/STOP events
 * to other application instances (LogisticsController, ConsumerController).
 */
public class SimulationControlProducer implements AutoCloseable {
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    
    /**
     * Create a new simulation control producer.
     */
    public SimulationControlProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Send a simulation control event to the simulation-control topic.
     * 
     * @param event The simulation control event
     * @return Future containing the record metadata
     */
    public Future<RecordMetadata> sendEvent(SimulationControlEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_SIMULATION_CONTROL,
                event.getSimulationId(),
                json
            );
            
            return producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Failed to send simulation control event: " + exception.getMessage());
                } else {
                    System.out.println("📡 Simulation control event sent: " + event.getAction() + 
                                     " for simulation " + event.getSimulationId() +
                                     " to partition " + metadata.partition());
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Error serializing simulation control event: " + e.getMessage());
            throw new RuntimeException("Failed to send simulation control event", e);
        }
    }
    
    /**
     * Send a START simulation event.
     * 
     * @param simulationId Unique identifier for the simulation
     * @param instanceId ID of the instance starting the simulation
     * @param batchId Batch ID being simulated
     * @param farmerId Farmer ID associated with the batch
     * @return Future containing the record metadata
     */
    public Future<RecordMetadata> sendStart(String simulationId, String instanceId, 
                                            String batchId, String farmerId) {
        SimulationControlEvent event = SimulationControlEvent.start(
            simulationId, instanceId, batchId, farmerId);
        return sendEvent(event);
    }
    
    /**
     * Send a STOP simulation event.
     * 
     * @param simulationId Unique identifier for the simulation
     * @param instanceId ID of the instance stopping the simulation
     * @return Future containing the record metadata
     */
    public Future<RecordMetadata> sendStop(String simulationId, String instanceId) {
        SimulationControlEvent event = SimulationControlEvent.stop(simulationId, instanceId);
        return sendEvent(event);
    }
    
    /**
     * Flush any pending messages.
     */
    public void flush() {
        producer.flush();
    }
    
    @Override
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
