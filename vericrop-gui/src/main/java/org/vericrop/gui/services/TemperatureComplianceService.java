package org.vericrop.gui.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.vericrop.dto.TemperatureComplianceEvent;
import org.vericrop.dto.TemperatureScenario;
import org.vericrop.kafka.producers.TemperatureComplianceEventProducer;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Service for simulating temperature compliance monitoring
 */
public class TemperatureComplianceService {
    
    private final TemperatureComplianceEventProducer producer;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Random random;
    private final ConcurrentHashMap<String, Future<?>> activeSimulations;
    
    // Configurable sampling interval (seconds)
    private static final int SAMPLE_INTERVAL_SECONDS = 5;
    
    public TemperatureComplianceService() {
        this.producer = new TemperatureComplianceEventProducer();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool();
        this.random = new Random();
        this.activeSimulations = new ConcurrentHashMap<>();
    }
    
    /**
     * Start a temperature compliance simulation for a batch
     * @param batchId Batch identifier
     * @param scenarioId Scenario identifier (e.g., "scenario-01")
     * @param duration Overall simulation duration
     */
    public void startComplianceSimulation(String batchId, String scenarioId, Duration duration) {
        // Cancel existing simulation for this batch if any
        stopSimulation(batchId);
        
        Future<?> future = executor.submit(() -> {
            try {
                runSimulation(batchId, scenarioId, duration);
            } catch (Exception e) {
                System.err.println("Temperature compliance simulation error for " + batchId + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        activeSimulations.put(batchId, future);
        System.out.println("🌡️ Temperature compliance simulation started for batch: " + batchId);
    }
    
    /**
     * Stop an active simulation
     */
    public void stopSimulation(String batchId) {
        Future<?> future = activeSimulations.remove(batchId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            System.out.println("🛑 Temperature compliance simulation stopped for batch: " + batchId);
        }
    }
    
    /**
     * Run the actual temperature simulation
     */
    private void runSimulation(String batchId, String scenarioId, Duration duration) throws Exception {
        // Load scenario
        TemperatureScenario scenario = loadScenario(scenarioId);
        if (scenario == null) {
            System.err.println("Could not load scenario: " + scenarioId + ", using default");
            scenario = createDefaultScenario();
        }
        
        System.out.println("📋 Using scenario: " + scenario);
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + duration.toMillis();
        int sampleCount = 0;
        List<TemperatureComplianceEvent.Violation> violations = new ArrayList<>();
        boolean overallCompliant = true;
        
        while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
            long currentTime = System.currentTimeMillis();
            double elapsedMinutes = (currentTime - startTime) / 60000.0;
            
            // Calculate temperature based on scenario
            double temperature = calculateTemperature(elapsedMinutes, scenario);
            
            // Check compliance
            boolean compliant = scenario.getTarget().isInRange(temperature);
            if (!compliant) {
                overallCompliant = false;
                // Track violation
                violations.add(new TemperatureComplianceEvent.Violation(
                    currentTime,
                    temperature,
                    SAMPLE_INTERVAL_SECONDS,
                    String.format("Temperature %.1f°C outside range %.1f-%.1f°C",
                        temperature, scenario.getTarget().getMin(), scenario.getTarget().getMax())
                ));
            }
            
            // Create and publish sample event
            TemperatureComplianceEvent event = new TemperatureComplianceEvent(
                batchId,
                temperature,
                compliant,
                scenarioId,
                compliant ? "Temperature within acceptable range" : 
                    String.format("Temperature outside range: %.1f°C", temperature)
            );
            
            producer.sendTemperatureComplianceEvent(event);
            sampleCount++;
            
            // Wait for next sample
            try {
                Thread.sleep(SAMPLE_INTERVAL_SECONDS * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Publish summary event
        publishSummary(batchId, scenarioId, overallCompliant, violations, sampleCount);
        
        activeSimulations.remove(batchId);
        System.out.println("✅ Temperature compliance simulation completed for batch: " + batchId);
    }
    
    /**
     * Calculate temperature at a given time based on scenario
     */
    private double calculateTemperature(double elapsedMinutes, TemperatureScenario scenario) {
        // Check if we're in a spike window
        if (scenario.getSpikes() != null) {
            for (TemperatureScenario.TemperatureSpike spike : scenario.getSpikes()) {
                double spikeStart = spike.getAtMinute();
                double spikeEnd = spike.getAtMinute() + spike.getDurationMinutes();
                
                if (elapsedMinutes >= spikeStart && elapsedMinutes < spikeEnd) {
                    // During spike, use spike temperature with small random variation
                    return spike.getTemperature() + (random.nextDouble() * 0.5 - 0.25);
                }
            }
        }
        
        // Normal operation: temperature within target range with small variations
        double targetMin = scenario.getTarget().getMin();
        double targetMax = scenario.getTarget().getMax();
        double targetMid = (targetMin + targetMax) / 2.0;
        double targetRange = targetMax - targetMin;
        
        // Add some realistic variation (±20% of range)
        double variation = (random.nextDouble() - 0.5) * targetRange * 0.4;
        return targetMid + variation;
    }
    
    /**
     * Publish summary event at end of simulation
     */
    private void publishSummary(String batchId, String scenarioId, boolean compliant, 
                                 List<TemperatureComplianceEvent.Violation> violations, int sampleCount) {
        TemperatureComplianceEvent summary = new TemperatureComplianceEvent();
        summary.setBatchId(batchId);
        summary.setScenarioId(scenarioId);
        summary.setCompliant(compliant);
        summary.setSummary(true);
        summary.setViolations(violations);
        summary.setDetails(String.format("Simulation completed: %d samples, %d violations", 
            sampleCount, violations.size()));
        
        producer.sendTemperatureComplianceEvent(summary);
        System.out.println("📊 Temperature compliance summary published for " + batchId + 
            ": compliant=" + compliant + ", violations=" + violations.size());
    }
    
    /**
     * Load scenario from resources
     */
    private TemperatureScenario loadScenario(String scenarioId) {
        try {
            String resourcePath = "/scenarios/" + scenarioId + ".json";
            InputStream is = getClass().getResourceAsStream(resourcePath);
            
            if (is == null) {
                System.err.println("Scenario resource not found: " + resourcePath);
                return null;
            }
            
            TemperatureScenario scenario = objectMapper.readValue(is, TemperatureScenario.class);
            System.out.println("✅ Loaded scenario: " + scenario);
            return scenario;
        } catch (Exception e) {
            System.err.println("Error loading scenario " + scenarioId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create a default scenario if loading fails
     */
    private TemperatureScenario createDefaultScenario() {
        TemperatureScenario scenario = new TemperatureScenario();
        scenario.setId("default");
        scenario.setDescription("Default cold-chain scenario");
        scenario.setTarget(new TemperatureScenario.TargetRange(2.0, 5.0));
        scenario.setDurationMinutes(30);
        scenario.setSpikes(new ArrayList<>());
        return scenario;
    }
    
    /**
     * Check if a simulation is active for a batch
     */
    public boolean isSimulationActive(String batchId) {
        Future<?> future = activeSimulations.get(batchId);
        return future != null && !future.isDone();
    }
    
    /**
     * Shutdown the service
     */
    public void shutdown() {
        // Cancel all active simulations
        for (String batchId : activeSimulations.keySet()) {
            stopSimulation(batchId);
        }
        
        // Shutdown executor
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Temperature compliance service executor did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Close producer
        producer.close();
        System.out.println("🔴 Temperature compliance service shutdown complete");
    }
}
