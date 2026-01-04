package org.vericrop.service;

import org.vericrop.service.models.*;
import org.vericrop.service.DeliverySimulator.*;

import java.util.List;

/**
 * Demo CLI program to demonstrate delivery simulation scenarios.
 * Run with: java org.vericrop.service.DeliverySimulatorDemo
 */
public class DeliverySimulatorDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(80));
        System.out.println("VeriCrop Delivery Simulator Demo");
        System.out.println("Demonstrating 5 Pre-defined Scenarios");
        System.out.println("=".repeat(80));
        System.out.println();
        
        MessageService messageService = new MessageService(false);
        AlertService alertService = new AlertService();
        DeliverySimulator simulator = new DeliverySimulator(messageService, alertService);
        
        // Define origin and destination using legacy inner classes for backwards compatibility
        DeliverySimulator.GeoCoordinate origin = new DeliverySimulator.GeoCoordinate(42.3601, -71.0589, "Sunny Valley Farm");
        DeliverySimulator.GeoCoordinate destination = new DeliverySimulator.GeoCoordinate(42.3736, -71.1097, "Metro Fresh Warehouse");
        
        // Run each scenario
        Scenario[] scenarios = Scenario.values();
        for (int i = 0; i < scenarios.length; i++) {
            Scenario scenario = scenarios[i];
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Scenario " + (i + 1) + ": " + scenario.getDisplayName());
            System.out.println("Temperature Drift: " + scenario.getTemperatureDrift() + "°C");
            System.out.println("Humidity Drift: " + scenario.getHumidityDrift() + "%");
            System.out.println("Speed Multiplier: " + scenario.getSpeedMultiplier());
            System.out.println("Spoilage Rate: " + scenario.getSpoilageRate() + " per hour");
            System.out.println("=".repeat(80));
            
            runScenarioDemo(simulator, origin, destination, scenario, alertService);
            
            // Wait between scenarios
            if (i < scenarios.length - 1) {
                System.out.println("\nWaiting before next scenario...");
                Thread.sleep(2000);
            }
        }
        
        // Print supplier metrics
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Supplier Performance Metrics");
        System.out.println("=".repeat(80));
        List<SupplierMetrics> metrics = simulator.getAllSupplierMetrics();
        for (SupplierMetrics m : metrics) {
            System.out.printf("Farmer: %s\n", m.getFarmerId());
            System.out.printf("  Total Deliveries: %d\n", m.getTotalDeliveries());
            System.out.printf("  Successful: %d\n", m.getSuccessfulDeliveries());
            System.out.printf("  Spoiled: %d\n", m.getSpoiledShipments());
            System.out.printf("  Avg Quality Decay: %.2f%%\n", m.getAverageQualityDecay());
            System.out.printf("  Success Rate: %.2f%%\n", m.getSuccessRate() * 100);
            System.out.println();
        }
        
        // Shutdown
        simulator.shutdown();
        System.out.println("Demo complete!");
    }
    
    private static void runScenarioDemo(DeliverySimulator simulator, DeliverySimulator.GeoCoordinate origin,
                                       DeliverySimulator.GeoCoordinate destination, Scenario scenario,
                                       AlertService alertService) throws InterruptedException {
        String batchId = "BATCH_" + scenario.name() + "_" + System.currentTimeMillis();
        String farmerId = "FARMER_" + (char)('A' + scenario.ordinal());
        
        // Generate route
        long startTime = System.currentTimeMillis();
        List<DeliverySimulator.RouteWaypoint> route = simulator.generateRoute(
            origin, destination, 5, startTime, 50.0, scenario);
        
        System.out.println("\nGenerated route with " + route.size() + " waypoints:");
        for (int i = 0; i < route.size(); i++) {
            DeliverySimulator.RouteWaypoint wp = route.get(i);
            System.out.printf("  %d. %s - Temp: %.1f°C, Humidity: %.1f%%\n",
                i + 1, wp.getLocation().getName(),
                wp.getTemperature(), wp.getHumidity());
        }
        
        // Start simulation
        simulator.startSimulation(batchId, farmerId, route, 500, scenario);
        
        // Monitor progress
        System.out.println("\nSimulation running...");
        for (int i = 0; i < 8; i++) {
            Thread.sleep(600);
            DeliverySimulator.SimulationStatus status = simulator.getSimulationStatus(batchId);
            if (status.isRunning()) {
                System.out.printf("  Progress: %d/%d waypoints\n",
                    status.getCurrentWaypoint(), status.getTotalWaypoints());
            } else {
                System.out.println("  Simulation completed!");
                break;
            }
        }
        
        // Get final report
        DeliveryReport report = simulator.generateDeliveryReport(batchId);
        if (report != null) {
            System.out.println("\n--- Delivery Report ---");
            System.out.printf("Batch ID: %s\n", report.getBatchId());
            System.out.printf("Farmer ID: %s\n", report.getFarmerId());
            System.out.printf("Distance: %.2f km\n", report.getTotalDistance());
            System.out.printf("Duration: %.2f hours\n", 
                (report.getEndTime() - report.getStartTime()) / (1000.0 * 3600.0));
            System.out.printf("Avg Temperature: %.1f°C\n", report.getAverageTemperature());
            System.out.printf("Avg Humidity: %.1f%%\n", report.getAverageHumidity());
            System.out.printf("Final Quality: %.1f%%\n", report.getFinalQualityScore());
            System.out.printf("Quality Decay: %.1f%%\n", report.getTotalQualityDecay());
            System.out.printf("Spoilage Probability: %.1f%%\n", report.getSpoilageProbability());
            System.out.printf("Delivered On Time: %s\n", report.isDeliveredOnTime() ? "Yes" : "No");
            System.out.printf("Alerts Generated: %d\n", report.getAlerts().size());
            
            if (!report.getAlerts().isEmpty()) {
                System.out.println("\nAlerts:");
                for (Alert alert : report.getAlerts()) {
                    System.out.printf("  [%s] %s: %s\n",
                        alert.getSeverity(), alert.getType(), alert.getMessage());
                }
            }
        }
        
        // Stop if still running
        simulator.stopSimulation(batchId);
    }
}
