package org.vericrop.gui.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.config.ConfigService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with Airflow REST API to trigger and monitor DAG runs.
 * Supports both basic authentication and token-based authentication.
 */
public class AnalyticsAirflowClient {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsAirflowClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String dagId;
    private final String authHeader;
    
    /**
     * Constructor with configuration
     */
    public AnalyticsAirflowClient(ConfigService config) {
        this.baseUrl = config.getAirflowBaseUrl();
        this.dagId = config.getAirflowDagId();
        
        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        
        this.objectMapper = new ObjectMapper();
        
        // Setup authentication header
        String username = config.getAirflowUsername();
        String password = config.getAirflowPassword();
        
        if (username != null && !username.isEmpty()) {
            String credentials = username + ":" + (password != null ? password : "");
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            this.authHeader = "Basic " + encodedCredentials;
            logger.info("✅ AnalyticsAirflowClient initialized with basic auth for user: {}", username);
        } else {
            this.authHeader = null;
            logger.warn("⚠️ AnalyticsAirflowClient initialized without authentication");
        }
        
        logger.info("✅ AnalyticsAirflowClient configured with base URL: {}, DAG ID: {}", baseUrl, dagId);
    }
    
    /**
     * Trigger a DAG run with configuration
     * POST /api/v1/dags/{dag_id}/dagRuns
     * 
     * @param conf Configuration parameters for the DAG run
     * @return The DAG run ID (dag_run_id)
     * @throws IOException if the request fails
     */
    public String triggerDagRun(Map<String, Object> conf) throws IOException {
        String url = baseUrl + "/api/v1/dags/" + dagId + "/dagRuns";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conf", conf);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json");
        
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader);
        }
        
        Request request = requestBuilder.build();
        
        logger.debug("Triggering Airflow DAG run: {} with config: {}", dagId, conf);
        
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Response body is null");
            }
            
            String responseString = responseBody.string();
            
            if (!response.isSuccessful()) {
                throw new IOException(String.format("Failed to trigger DAG run. HTTP %d: %s - %s",
                        response.code(), response.message(), responseString));
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseString, Map.class);
            String dagRunId = (String) result.get("dag_run_id");
            
            logger.info("✅ Successfully triggered Airflow DAG run: {}", dagRunId);
            return dagRunId;
            
        } catch (IOException e) {
            logger.error("❌ Failed to trigger Airflow DAG run: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get the status of a DAG run
     * GET /api/v1/dags/{dag_id}/dagRuns/{dag_run_id}
     * 
     * @param dagRunId The DAG run ID
     * @return Map containing DAG run details including state
     * @throws IOException if the request fails
     */
    public Map<String, Object> getDagRunStatus(String dagRunId) throws IOException {
        String url = baseUrl + "/api/v1/dags/" + dagId + "/dagRuns/" + dagRunId;
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get();
        
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader);
        }
        
        Request request = requestBuilder.build();
        
        logger.debug("Fetching Airflow DAG run status: {}", dagRunId);
        
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Response body is null");
            }
            
            String responseString = responseBody.string();
            
            if (!response.isSuccessful()) {
                throw new IOException(String.format("Failed to fetch DAG run status. HTTP %d: %s - %s",
                        response.code(), response.message(), responseString));
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseString, Map.class);
            
            logger.debug("DAG run status retrieved: {}", result.get("state"));
            return result;
            
        } catch (IOException e) {
            logger.error("❌ Failed to fetch Airflow DAG run status: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Test connection to Airflow API
     * 
     * @return true if connection is successful
     */
    public boolean testConnection() {
        try {
            String url = baseUrl + "/api/v1/health";
            
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            
            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                if (success) {
                    logger.info("✅ Airflow connection test successful");
                } else {
                    logger.warn("⚠️ Airflow connection test failed with status: {}", response.code());
                }
                return success;
            }
        } catch (Exception e) {
            logger.error("❌ Airflow connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Close HTTP client resources
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        logger.info("AnalyticsAirflowClient shutdown complete");
    }
}
