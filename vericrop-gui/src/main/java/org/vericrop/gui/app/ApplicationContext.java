package org.vericrop.gui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.clients.MLClientService;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.persistence.PostgresBatchRepository;
import org.vericrop.gui.services.*;

/**
 * Application Context for VeriCrop GUI.
 * Manages singleton instances of all services and provides dependency injection.
 * 
 * This class follows the Service Locator pattern to provide centralized
 * access to all application services.
 */
public class ApplicationContext {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);
    private static ApplicationContext instance;
    
    // Core services
    private final ConfigService configService;
    private final MLClientService mlClientService;
    private final KafkaMessagingService kafkaMessagingService;
    private final PostgresBatchRepository batchRepository;
    
    // Business services
    private final AuthenticationService authenticationService;
    private final BatchService batchService;
    private final AnalyticsService analyticsService;

    /**
     * Private constructor - use getInstance() to get singleton
     */
    private ApplicationContext() {
        logger.info("=== Initializing VeriCrop Application Context ===");
        
        // Initialize configuration
        this.configService = ConfigService.getInstance();
        this.configService.printConfiguration();
        
        // Initialize infrastructure services
        this.mlClientService = new MLClientService(configService);
        this.kafkaMessagingService = new KafkaMessagingService(configService);
        this.batchRepository = new PostgresBatchRepository(configService);
        
        // Initialize business services
        this.authenticationService = new AuthenticationService();
        this.analyticsService = new AnalyticsService(mlClientService);
        this.batchService = new BatchService(mlClientService, kafkaMessagingService, batchRepository);
        
        // Test connections
        testConnections();
        
        logger.info("=== Application Context Initialized Successfully ===");
    }

    /**
     * Get singleton instance of ApplicationContext
     */
    public static synchronized ApplicationContext getInstance() {
        if (instance == null) {
            instance = new ApplicationContext();
        }
        return instance;
    }

    /**
     * Test connections to external services
     */
    private void testConnections() {
        logger.info("Testing service connections...");
        
        // Test ML Service
        if (mlClientService.testConnection()) {
            logger.info("✅ ML Service connection: OK");
        } else {
            logger.warn("⚠️  ML Service connection: FAILED (continuing in degraded mode)");
        }
        
        // Test Database
        if (batchRepository.testConnection()) {
            logger.info("✅ Database connection: OK");
        } else {
            logger.warn("⚠️  Database connection: FAILED");
        }
        
        // Test Kafka
        if (kafkaMessagingService.isEnabled()) {
            if (kafkaMessagingService.testConnection()) {
                logger.info("✅ Kafka connection: OK");
            } else {
                logger.warn("⚠️  Kafka connection: FAILED (continuing without messaging)");
            }
        } else {
            logger.info("ℹ️  Kafka: DISABLED");
        }
    }

    // Getters for all services

    public ConfigService getConfigService() {
        return configService;
    }

    public MLClientService getMlClientService() {
        return mlClientService;
    }

    public KafkaMessagingService getKafkaMessagingService() {
        return kafkaMessagingService;
    }

    public PostgresBatchRepository getBatchRepository() {
        return batchRepository;
    }

    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    public BatchService getBatchService() {
        return batchService;
    }

    public AnalyticsService getAnalyticsService() {
        return analyticsService;
    }

    /**
     * Shutdown all services and release resources
     */
    public void shutdown() {
        logger.info("=== Shutting down Application Context ===");
        
        try {
            if (kafkaMessagingService != null) {
                kafkaMessagingService.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down Kafka service", e);
        }
        
        try {
            if (mlClientService != null) {
                mlClientService.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down ML client", e);
        }
        
        try {
            if (batchRepository != null) {
                batchRepository.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down database connection", e);
        }
        
        logger.info("=== Application Context Shutdown Complete ===");
    }
}
