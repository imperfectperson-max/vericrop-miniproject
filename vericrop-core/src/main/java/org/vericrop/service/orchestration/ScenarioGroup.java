package org.vericrop.service.orchestration;

/**
 * Represents the six scenario groups that can be executed concurrently.
 */
public enum ScenarioGroup {
    /**
     * General scenario simulations (various delivery scenarios).
     */
    SCENARIOS("scenarios"),
    
    /**
     * Delivery tracking and logistics flows.
     */
    DELIVERY("delivery"),
    
    /**
     * Map and route visualization.
     */
    MAP("map"),
    
    /**
     * Temperature monitoring and environmental data.
     */
    TEMPERATURE("temperature"),
    
    /**
     * Supplier compliance checks and validation.
     */
    SUPPLIER_COMPLIANCE("supplierCompliance"),
    
    /**
     * Advanced simulation workflows.
     */
    SIMULATIONS("simulations");
    
    private final String kafkaTopic;
    
    ScenarioGroup(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }
    
    /**
     * Returns the Kafka topic name for this scenario group.
     */
    public String getKafkaTopic() {
        return kafkaTopic;
    }
    
    /**
     * Returns the Airflow DAG name for this scenario group.
     */
    public String getAirflowDagName() {
        return kafkaTopic + "_dag";
    }
}
