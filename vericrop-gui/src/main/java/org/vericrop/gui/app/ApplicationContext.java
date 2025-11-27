package org.vericrop.gui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.clients.MLClientService;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.dao.ParticipantDao;
import org.vericrop.gui.dao.UserDao;
import org.vericrop.gui.persistence.DatabaseInitializer;
import org.vericrop.gui.persistence.PostgresBatchRepository;
import org.vericrop.gui.services.*;
import org.vericrop.service.DeliverySimulator;
import org.vericrop.service.MessageService;
import org.vericrop.service.simulation.SimulationManager;

import javax.sql.DataSource;

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
    
    // DAO layer
    private final UserDao userDao;
    private final ParticipantDao participantDao;
    
    // Business services
    private final AuthenticationService authenticationService;
    private final BatchService batchService;
    
    // Core services from vericrop-core
    private final MessageService messageService;
    private final DeliverySimulator deliverySimulator;
    private final AlertService alertService;
    private final org.vericrop.service.MapService mapService;
    private final org.vericrop.service.TemperatureService temperatureService;
    private final org.vericrop.service.AlertService coreAlertService;
    private final SimulationManager simulationManager;
    
    // Additional services for demo mode
    private org.vericrop.service.BlockchainService blockchainService;
    private org.vericrop.service.impl.FileLedgerService fileLedgerService;

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
        
        // Initialize database schema (idempotent - safe to run multiple times)
        DataSource dataSource = this.batchRepository.getDataSource();
        try {
            DatabaseInitializer.initialize(dataSource);
            logger.info("✅ Database schema initialized successfully");
        } catch (Exception e) {
            logger.warn("⚠️  Database schema initialization encountered issues: {}. Continuing anyway.", 
                       e.getMessage());
        }
        
        // Initialize DAO layer with shared DataSource
        this.userDao = new UserDao(dataSource);
        this.participantDao = new ParticipantDao(dataSource);
        
        // Initialize business services
        this.authenticationService = new AuthenticationService(dataSource);
        this.batchService = new BatchService(mlClientService, kafkaMessagingService, batchRepository);
        
        // Initialize core services
        this.messageService = new MessageService(true); // Enable persistence
        this.coreAlertService = new org.vericrop.service.AlertService();
        this.deliverySimulator = new DeliverySimulator(messageService, coreAlertService);
        this.alertService = AlertService.getInstance();
        this.mapService = new org.vericrop.service.MapService();
        this.temperatureService = new org.vericrop.service.TemperatureService();
        
        // Initialize MapSimulator and ScenarioManager
        org.vericrop.service.MapSimulator mapSimulator = new org.vericrop.service.MapSimulator();
        org.vericrop.service.ScenarioManager scenarioManager = new org.vericrop.service.ScenarioManager();
        logger.info("MapSimulator and ScenarioManager initialized");
        
        // Initialize SimulationManager with full dependencies including map simulation
        SimulationManager.initialize(this.deliverySimulator, this.mapService, 
                                     this.temperatureService, this.coreAlertService,
                                     mapSimulator, scenarioManager);
        this.simulationManager = SimulationManager.getInstance();
        logger.info("SimulationManager initialized with integrated services and map simulation");
        
        // Initialize additional services for demo mode
        this.fileLedgerService = new org.vericrop.service.impl.FileLedgerService();
        logger.info("FileLedgerService initialized");
        
        // BlockchainService will be initialized lazily when blockchain is ready
        
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

    public UserDao getUserDao() {
        return userDao;
    }
    
    public ParticipantDao getParticipantDao() {
        return participantDao;
    }
    
    public MessageService getMessageService() {
        return messageService;
    }
    
    public DeliverySimulator getDeliverySimulator() {
        return deliverySimulator;
    }
    
    public AlertService getAlertService() {
        return alertService;
    }
    
    public SimulationManager getSimulationManager() {
        return simulationManager;
    }
    
    public org.vericrop.service.MapService getMapService() {
        return mapService;
    }
    
    public org.vericrop.service.TemperatureService getTemperatureService() {
        return temperatureService;
    }
    
    public org.vericrop.service.AlertService getCoreAlertService() {
        return coreAlertService;
    }
    
    /**
     * Get MapSimulator from SimulationManager.
     * @return MapSimulator instance
     */
    public org.vericrop.service.MapSimulator getMapSimulator() {
        return simulationManager.getMapSimulator();
    }
    
    /**
     * Get ScenarioManager from SimulationManager.
     * @return ScenarioManager instance
     */
    public org.vericrop.service.ScenarioManager getScenarioManager() {
        return simulationManager.getScenarioManager();
    }
    
    /**
     * Get or create BlockchainService.
     * @param blockchain The blockchain instance to use
     * @return BlockchainService instance
     */
    public org.vericrop.service.BlockchainService getBlockchainService(org.vericrop.blockchain.Blockchain blockchain) {
        if (blockchainService == null && blockchain != null) {
            blockchainService = new org.vericrop.service.BlockchainService(blockchain);
            logger.info("BlockchainService initialized");
        }
        return blockchainService;
    }
    
    /**
     * Get FileLedgerService for shipment recording.
     * @return FileLedgerService instance
     */
    public org.vericrop.service.impl.FileLedgerService getFileLedgerService() {
        return fileLedgerService;
    }
    
    /**
     * Create a demo-friendly KafkaServiceManager that won't fail if Kafka is unavailable.
     * @return KafkaServiceManager or null if creation fails
     */
    public org.vericrop.kafka.KafkaServiceManager createKafkaServiceManager() {
        try {
            org.vericrop.kafka.KafkaServiceManager manager = new org.vericrop.kafka.KafkaServiceManager();
            logger.info("KafkaServiceManager created");
            return manager;
        } catch (Exception e) {
            logger.warn("Could not create KafkaServiceManager: {}. Continuing in demo mode without Kafka.", e.getMessage());
            return null;
        }
    }

    /**
     * Shutdown all services and release resources
     */
    public void shutdown() {
        logger.info("=== Shutting down Application Context ===");
        
        try {
            if (simulationManager != null) {
                simulationManager.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down simulation manager", e);
        }
        
        try {
            if (deliverySimulator != null) {
                deliverySimulator.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down delivery simulator", e);
        }
        
        try {
            if (blockchainService != null) {
                blockchainService.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down blockchain service", e);
        }
        
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
