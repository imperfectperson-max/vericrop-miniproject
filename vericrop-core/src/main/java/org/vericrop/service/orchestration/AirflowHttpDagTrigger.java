package org.vericrop.service.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-based Airflow DAG trigger implementation.
 * 
 * Triggers Airflow DAGs via the Airflow REST API for analytics and simulation workflows.
 * Supports authentication and configuration via environment variables.
 */
public class AirflowHttpDagTrigger implements OrchestrationService.AirflowDagTrigger {
    private static final Logger logger = LoggerFactory.getLogger(AirflowHttpDagTrigger.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String airflowBaseUrl;
    private final String authHeader;
    
    /**
     * Creates an Airflow DAG trigger using configuration from environment variables.
     * 
     * Environment variables:
     * - AIRFLOW_BASE_URL: Base URL of Airflow (default: http://localhost:8080)
     * - AIRFLOW_USERNAME: Airflow username (optional)
     * - AIRFLOW_PASSWORD: Airflow password (optional)
     */
    public AirflowHttpDagTrigger() {
        this(
            getEnvOrDefault("AIRFLOW_BASE_URL", "http://localhost:8080"),
            System.getenv("AIRFLOW_USERNAME"),
            System.getenv("AIRFLOW_PASSWORD")
        );
    }
    
    /**
     * Creates an Airflow DAG trigger with explicit configuration.
     * 
     * @param airflowBaseUrl Base URL of Airflow
     * @param username Airflow username (nullable)
     * @param password Airflow password (nullable)
     */
    public AirflowHttpDagTrigger(String airflowBaseUrl, String username, String password) {
        this.airflowBaseUrl = airflowBaseUrl;
        this.objectMapper = new ObjectMapper();
        
        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        
        // Setup basic authentication if credentials provided
        if (username != null && !username.isEmpty() && password != null) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            this.authHeader = "Basic " + encoded;
        } else {
            this.authHeader = null;
        }
        
        logger.info("AirflowHttpDagTrigger initialized with base URL: {}", airflowBaseUrl);
    }
    
    @Override
    public boolean triggerDag(String dagName, Map<String, Object> dagConf) throws Exception {
        if (dagName == null || dagName.isEmpty()) {
            throw new IllegalArgumentException("DAG name cannot be null or empty");
        }
        
        try {
            // Prepare DAG run request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("conf", dagConf != null ? dagConf : new HashMap<>());
            
            String requestJson = objectMapper.writeValueAsString(requestBody);
            
            // Build HTTP request
            String url = String.format("%s/api/v1/dags/%s/dagRuns", airflowBaseUrl, dagName);
            
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestJson, JSON));
            
            // Add authentication header if configured
            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }
            
            Request request = requestBuilder.build();
            
            // Execute request
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Successfully triggered Airflow DAG: {}", dagName);
                    return true;
                } else if (response.code() == 404) {
                    logger.warn("Airflow DAG not found: {} (this is expected if Airflow is not configured)", dagName);
                    // Return true to not fail orchestration when Airflow is optional
                    return true;
                } else if (response.code() == 401 || response.code() == 403) {
                    logger.error("Airflow authentication failed for DAG: {}", dagName);
                    return false;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    logger.error("Failed to trigger Airflow DAG: {} - Status: {} - Body: {}",
                            dagName, response.code(), responseBody);
                    return false;
                }
            }
            
        } catch (IOException e) {
            logger.warn("Airflow not reachable for DAG: {} (continuing without Airflow)", dagName);
            // Return true to not fail orchestration when Airflow is optional/not available
            return true;
        } catch (Exception e) {
            logger.error("Error triggering Airflow DAG: {}", dagName, e);
            throw e;
        }
    }
    
    /**
     * Gets an environment variable or returns a default value.
     */
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        logger.info("Closing AirflowHttpDagTrigger");
        // OkHttpClient connection pool will be automatically cleaned up
    }
}
