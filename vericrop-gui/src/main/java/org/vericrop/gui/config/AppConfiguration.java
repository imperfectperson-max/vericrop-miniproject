package org.vericrop.gui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.vericrop.kafka.messaging.KafkaProducerService;
import org.vericrop.service.QualityEvaluationService;
import org.vericrop.service.impl.FileLedgerService;

/**
 * Application configuration for VeriCrop services.
 */
@Configuration
public class AppConfiguration {
    
    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;
    
    @Value("${ledger.path:ledger}")
    private String ledgerPath;
    
    /**
     * Configure CORS to allow requests from any origin.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
    
    /**
     * Create Quality Evaluation Service bean.
     */
    @Bean
    public QualityEvaluationService qualityEvaluationService() {
        return new QualityEvaluationService();
    }
    
    /**
     * Create File Ledger Service bean.
     */
    @Bean
    public FileLedgerService fileLedgerService() {
        return new FileLedgerService(ledgerPath);
    }
    
    /**
     * Create Kafka Producer Service bean.
     */
    @Bean
    public KafkaProducerService kafkaProducerService() {
        return new KafkaProducerService(kafkaEnabled);
    }
    
    /**
     * Create MapSimulator bean for grid-based simulation.
     */
    @Bean
    public org.vericrop.service.MapSimulator mapSimulator() {
        return new org.vericrop.service.MapSimulator();
    }
    
    /**
     * Create ScenarioManager bean for scenario selection and configuration.
     */
    @Bean
    public org.vericrop.service.ScenarioManager scenarioManager() {
        return new org.vericrop.service.ScenarioManager();
    }
    
    /**
     * Create DeliverySimulator bean.
     * Dependencies will be injected from other beans.
     */
    @Bean
    public org.vericrop.service.DeliverySimulator deliverySimulator(
            org.vericrop.service.MessageService messageService,
            org.vericrop.service.AlertService alertService) {
        return new org.vericrop.service.DeliverySimulator(messageService, alertService);
    }
    
    /**
     * Create MessageService bean.
     */
    @Bean
    public org.vericrop.service.MessageService messageService() {
        return new org.vericrop.service.MessageService(true);
    }
    
    /**
     * Create AlertService bean.
     */
    @Bean
    public org.vericrop.service.AlertService alertService() {
        return new org.vericrop.service.AlertService();
    }
    
    /**
     * Create MapService bean.
     */
    @Bean
    public org.vericrop.service.MapService mapService() {
        return new org.vericrop.service.MapService();
    }
    
    /**
     * Create TemperatureService bean.
     */
    @Bean
    public org.vericrop.service.TemperatureService temperatureService() {
        return new org.vericrop.service.TemperatureService();
    }
    
    /**
     * Create SimulationManager bean with full dependencies.
     * This is the singleton that manages all simulation state.
     */
    @Bean
    public org.vericrop.service.simulation.SimulationManager simulationManager(
            org.vericrop.service.DeliverySimulator deliverySimulator,
            org.vericrop.service.MapService mapService,
            org.vericrop.service.TemperatureService temperatureService,
            org.vericrop.service.AlertService alertService,
            org.vericrop.service.MapSimulator mapSimulator,
            org.vericrop.service.ScenarioManager scenarioManager) {
        // Initialize the singleton
        org.vericrop.service.simulation.SimulationManager.initialize(
            deliverySimulator, mapService, temperatureService, alertService,
            mapSimulator, scenarioManager);
        return org.vericrop.service.simulation.SimulationManager.getInstance();
    }
}
