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
}
