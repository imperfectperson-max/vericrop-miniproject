package org.vericrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request object for triggering concurrent scenario execution.
 * Contains flags for which scenarios to run and their parameters.
 */
public class ScenarioRunRequest {
    
    private boolean deliveryScenario;
    private boolean mapScenario;
    private boolean temperatureScenario;
    private boolean supplierComplianceScenario;
    private boolean simulationsScenario;
    
    private String farmerId;
    private String batchId;
    
    private Map<String, Object> scenarioParameters;
    
    public ScenarioRunRequest() {
        this.scenarioParameters = new HashMap<>();
    }
    
    // Getters and Setters
    @JsonProperty("delivery_scenario")
    public boolean isDeliveryScenario() {
        return deliveryScenario;
    }
    
    public void setDeliveryScenario(boolean deliveryScenario) {
        this.deliveryScenario = deliveryScenario;
    }
    
    @JsonProperty("map_scenario")
    public boolean isMapScenario() {
        return mapScenario;
    }
    
    public void setMapScenario(boolean mapScenario) {
        this.mapScenario = mapScenario;
    }
    
    @JsonProperty("temperature_scenario")
    public boolean isTemperatureScenario() {
        return temperatureScenario;
    }
    
    public void setTemperatureScenario(boolean temperatureScenario) {
        this.temperatureScenario = temperatureScenario;
    }
    
    @JsonProperty("supplier_compliance_scenario")
    public boolean isSupplierComplianceScenario() {
        return supplierComplianceScenario;
    }
    
    public void setSupplierComplianceScenario(boolean supplierComplianceScenario) {
        this.supplierComplianceScenario = supplierComplianceScenario;
    }
    
    @JsonProperty("simulations_scenario")
    public boolean isSimulationsScenario() {
        return simulationsScenario;
    }
    
    public void setSimulationsScenario(boolean simulationsScenario) {
        this.simulationsScenario = simulationsScenario;
    }
    
    @JsonProperty("farmer_id")
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    @JsonProperty("batch_id")
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    @JsonProperty("scenario_parameters")
    public Map<String, Object> getScenarioParameters() {
        return scenarioParameters;
    }
    
    public void setScenarioParameters(Map<String, Object> scenarioParameters) {
        this.scenarioParameters = scenarioParameters != null ? scenarioParameters : new HashMap<>();
    }
    
    /**
     * Check if any scenario is enabled.
     */
    public boolean hasAnyScenarioEnabled() {
        return deliveryScenario || mapScenario || temperatureScenario || 
               supplierComplianceScenario || simulationsScenario;
    }
    
    /**
     * Count number of enabled scenarios.
     */
    public int getEnabledScenarioCount() {
        int count = 0;
        if (deliveryScenario) count++;
        if (mapScenario) count++;
        if (temperatureScenario) count++;
        if (supplierComplianceScenario) count++;
        if (simulationsScenario) count++;
        return count;
    }
    
    @Override
    public String toString() {
        return String.format("ScenarioRunRequest{delivery=%s, map=%s, temperature=%s, supplierCompliance=%s, simulations=%s, farmerId='%s', batchId='%s'}",
                           deliveryScenario, mapScenario, temperatureScenario, supplierComplianceScenario, 
                           simulationsScenario, farmerId, batchId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioRunRequest that = (ScenarioRunRequest) o;
        return deliveryScenario == that.deliveryScenario &&
               mapScenario == that.mapScenario &&
               temperatureScenario == that.temperatureScenario &&
               supplierComplianceScenario == that.supplierComplianceScenario &&
               simulationsScenario == that.simulationsScenario &&
               Objects.equals(farmerId, that.farmerId) &&
               Objects.equals(batchId, that.batchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(deliveryScenario, mapScenario, temperatureScenario, 
                          supplierComplianceScenario, simulationsScenario, farmerId, batchId);
    }
}
