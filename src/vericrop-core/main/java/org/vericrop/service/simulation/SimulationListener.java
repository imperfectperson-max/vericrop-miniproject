package org.vericrop.service.simulation;

/**
 * Listener interface for simulation lifecycle events.
 * Implementations should be thread-safe and handle updates on appropriate threads
 * (e.g., use Platform.runLater() for JavaFX UI updates).
 */
public interface SimulationListener {
    
    /**
     * Called when a simulation starts.
     * 
     * @param batchId the ID of the batch being simulated
     * @param farmerId the ID of the farmer/producer
     * @param scenarioId the ID of the scenario being run (e.g., "example_1", "presentation_scenario_2", or "NORMAL" for default)
     */
    void onSimulationStarted(String batchId, String farmerId, String scenarioId);
    
    /**
     * Called periodically during simulation to report progress.
     * 
     * @param batchId the ID of the batch being simulated
     * @param progress progress as a percentage (0.0 to 100.0)
     * @param currentLocation current location description
     */
    void onProgressUpdate(String batchId, double progress, String currentLocation);
    
    /**
     * Called when a simulation stops (either completes or is manually stopped).
     * 
     * @param batchId the ID of the batch that was being simulated
     * @param completed true if simulation completed naturally, false if stopped manually
     */
    void onSimulationStopped(String batchId, boolean completed);
    
    /**
     * Called when an error occurs during simulation.
     * 
     * @param batchId the ID of the batch being simulated
     * @param error the error message
     */
    default void onSimulationError(String batchId, String error) {
        // Default implementation does nothing
    }
}
