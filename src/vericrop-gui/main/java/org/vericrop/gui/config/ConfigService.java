package org.vericrop.gui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized configuration service for VeriCrop GUI application.
 * Loads configuration from application.properties and environment variables.
 * Environment variables take precedence over properties file values.
 */
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static ConfigService instance;
    private final Properties properties;

    private ConfigService() {
        properties = new Properties();
        loadDefaultProperties();
        logger.info("✅ ConfigService initialized");
    }

    /**
     * Get singleton instance of ConfigService
     */
    public static synchronized ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    /**
     * Load default properties from application.properties file
     */
    private void loadDefaultProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded application.properties");
            } else {
                logger.warn("application.properties not found, using defaults");
            }
        } catch (IOException e) {
            logger.error("Failed to load application.properties", e);
        }
    }

    /**
     * Get a configuration value. Environment variables take precedence.
     * @param key Configuration key
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    private String getConfig(String key, String defaultValue) {
        // Check environment variable first (convert dots to underscores and uppercase)
        String envKey = key.replace('.', '_').toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        
        // Fall back to properties file
        return properties.getProperty(key, defaultValue);
    }

    // ============================================================================
    // Database Configuration
    // ============================================================================

    public String getDbUrl() {
        String host = getConfig("postgres.host", "localhost");
        String port = getConfig("postgres.port", "5432");
        String database = getConfig("postgres.db", "vericrop");
        return String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
    }

    public String getDbUser() {
        return getConfig("postgres.user", "vericrop");
    }

    public String getDbPassword() {
        return getConfig("postgres.password", "vericrop123");
    }

    public int getDbPoolSize() {
        return Integer.parseInt(getConfig("db.pool.size", "10"));
    }

    public int getDbConnectionTimeout() {
        return Integer.parseInt(getConfig("db.connection.timeout", "30000"));
    }

    public int getDbIdleTimeout() {
        return Integer.parseInt(getConfig("db.idle.timeout", "600000"));
    }

    public int getDbMaxLifetime() {
        return Integer.parseInt(getConfig("db.max.lifetime", "1800000"));
    }

    // ============================================================================
    // Kafka Configuration
    // ============================================================================

    public String getKafkaBootstrapServers() {
        return getConfig("kafka.bootstrap.servers", "localhost:9092");
    }

    public boolean isKafkaEnabled() {
        return Boolean.parseBoolean(getConfig("kafka.enabled", "true"));
    }

    public String getKafkaAcks() {
        return getConfig("kafka.acks", "all");
    }

    public int getKafkaRetries() {
        return Integer.parseInt(getConfig("kafka.retries", "3"));
    }

    public boolean getKafkaIdempotence() {
        return Boolean.parseBoolean(getConfig("kafka.idempotence", "true"));
    }

    public int getKafkaBatchSize() {
        return Integer.parseInt(getConfig("kafka.batch.size", "16384"));
    }

    public int getKafkaLingerMs() {
        return Integer.parseInt(getConfig("kafka.linger.ms", "10"));
    }

    public long getKafkaBufferMemory() {
        return Long.parseLong(getConfig("kafka.buffer.memory", "33554432"));
    }

    // Kafka Topics
    public String getKafkaTopicBatchEvents() {
        return getConfig("kafka.topic.batch.events", "batch-events");
    }

    public String getKafkaTopicQualityAlerts() {
        return getConfig("kafka.topic.quality.alerts", "quality-alerts");
    }

    public String getKafkaTopicLogisticsEvents() {
        return getConfig("kafka.topic.logistics.events", "logistics-events");
    }

    public String getKafkaTopicBlockchainEvents() {
        return getConfig("kafka.topic.blockchain.events", "blockchain-events");
    }

    // ============================================================================
    // ML Service Configuration
    // ============================================================================

    public String getMlServiceUrl() {
        return getConfig("ml.service.url", "http://localhost:8000");
    }

    public int getMlServiceTimeout() {
        return Integer.parseInt(getConfig("ml.service.timeout", "30000"));
    }

    public int getMlServiceRetries() {
        return Integer.parseInt(getConfig("ml.service.retries", "3"));
    }

    public int getMlServiceRetryDelay() {
        return Integer.parseInt(getConfig("ml.service.retry.delay", "1000"));
    }

    public boolean isMlServiceDemoMode() {
        return Boolean.parseBoolean(getConfig("vericrop.load.demo", "true"));
    }

    // ============================================================================
    // Application Configuration
    // ============================================================================

    public String getApplicationMode() {
        return getConfig("vericrop.mode", "dev");
    }

    public int getServerPort() {
        return Integer.parseInt(getConfig("server.port", "8080"));
    }

    public String getLedgerPath() {
        return getConfig("ledger.path", "ledger");
    }

    public double getQualityPassThreshold() {
        return Double.parseDouble(getConfig("quality.pass.threshold", "0.7"));
    }

    // ============================================================================
    // Logging Configuration
    // ============================================================================

    public String getLogLevel() {
        return getConfig("log.level", "INFO");
    }

    public String getLogFilePath() {
        return getConfig("log.file.path", "logs/vericrop-gui.log");
    }

    /**
     * Print current configuration (for debugging)
     */
    public void printConfiguration() {
        logger.info("=== VeriCrop Configuration ===");
        logger.info("Database URL: {}", getDbUrl());
        logger.info("Database User: {}", getDbUser());
        logger.info("Kafka Bootstrap Servers: {}", getKafkaBootstrapServers());
        logger.info("Kafka Enabled: {}", isKafkaEnabled());
        logger.info("ML Service URL: {}", getMlServiceUrl());
        logger.info("ML Demo Mode: {}", isMlServiceDemoMode());
        logger.info("Application Mode: {}", getApplicationMode());
        logger.info("Server Port: {}", getServerPort());
        logger.info("==============================");
    }
}
