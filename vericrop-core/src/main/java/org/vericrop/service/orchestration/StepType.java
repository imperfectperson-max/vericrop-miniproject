package org.vericrop.service.orchestration;

/**
 * Enumeration of functional areas that can be orchestrated concurrently
 */
public enum StepType {
    SCENARIOS,             // Scenario processing
    DELIVERY,              // Delivery simulation/tracking
    MAP,                   // Map/route generation
    TEMPERATURE,           // Temperature monitoring
    SUPPLIER_COMPLIANCE,   // Supplier compliance checking
    SIMULATIONS            // General simulation tasks
}
