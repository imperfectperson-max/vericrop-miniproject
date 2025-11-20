package org.vericrop.gui.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.models.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ML Client Service for communicating with the FastAPI ML Service.
 * Provides typed HTTP client methods mapping to docker/ml-service/app.py endpoints.
 * 
 * Features:
 * - Resilient HTTP client with configurable timeout and retries
 * - JSON serialization/deserialization with Jackson
 * - Request/response DTO mapping
 * - Error handling and logging
 */
public class MLClientService {
    private static final Logger logger = LoggerFactory.getLogger(MLClientService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int retries;
    private final int retryDelay;

    /**
     * Constructor with configuration from ConfigService
     */
    public MLClientService(ConfigService config) {
        this.baseUrl = config.getMlServiceUrl();
        this.retries = config.getMlServiceRetries();
        this.retryDelay = config.getMlServiceRetryDelay();
        
        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getMlServiceTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getMlServiceTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getMlServiceTimeout(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
        
        // Configure JSON mapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        logger.info("✅ MLClientService initialized with base URL: {}", baseUrl);
    }

    /**
     * Health check endpoint
     * GET /health
     */
    public HealthStatus health() throws IOException {
        String url = baseUrl + "/health";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        return executeWithRetry(request, HealthStatus.class);
    }

    /**
     * Create a new batch
     * POST /batches
     */
    public BatchResponse createBatch(BatchRecord batchRecord) throws IOException {
        String url = baseUrl + "/batches";
        
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", batchRecord.getName());
        requestBody.put("farmer", batchRecord.getFarmer());
        requestBody.put("product_type", batchRecord.getProductType());
        requestBody.put("quantity", batchRecord.getQuantity());
        
        // Add quality data if available
        if (batchRecord.getQualityScore() != null || batchRecord.getQualityLabel() != null) {
            Map<String, Object> qualityData = new HashMap<>();
            if (batchRecord.getQualityScore() != null) {
                qualityData.put("quality_score", batchRecord.getQualityScore());
            }
            if (batchRecord.getQualityLabel() != null) {
                qualityData.put("label", batchRecord.getQualityLabel());
            }
            requestBody.put("quality_data", qualityData);
        }
        
        if (batchRecord.getDataHash() != null) {
            requestBody.put("data_hash", batchRecord.getDataHash());
        }
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        
        return executeWithRetry(request, BatchResponse.class);
    }

    /**
     * List all batches
     * GET /batches
     */
    @SuppressWarnings("unchecked")
    public List<BatchRecord> listBatches() throws IOException {
        String url = baseUrl + "/batches";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> batchMaps = (List<Map<String, Object>>) result.get("batches");
            
            return batchMaps.stream()
                    .map(map -> objectMapper.convertValue(map, BatchRecord.class))
                    .toList();
        }
    }

    /**
     * Predict image quality
     * POST /predict (multipart/form-data)
     */
    public PredictionResult predictImage(File imageFile) throws IOException {
        String url = baseUrl + "/predict";
        
        RequestBody fileBody = RequestBody.create(imageFile, MediaType.parse("image/*"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(), fileBody)
                .build();
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        
        return executeWithRetry(request, PredictionResult.class);
    }

    /**
     * Predict image quality from InputStream
     * POST /predict (multipart/form-data)
     */
    public PredictionResult predictImage(InputStream imageStream, String fileName) throws IOException {
        String url = baseUrl + "/predict";
        
        byte[] imageBytes = imageStream.readAllBytes();
        RequestBody fileBody = RequestBody.create(imageBytes, MediaType.parse("image/*"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName != null ? fileName : "image.jpg", fileBody)
                .build();
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        
        return executeWithRetry(request, PredictionResult.class);
    }

    /**
     * Get farm dashboard data
     * GET /dashboard/farm
     */
    public DashboardData getDashboardFarm() throws IOException {
        String url = baseUrl + "/dashboard/farm";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        return executeWithRetry(request, DashboardData.class);
    }

    /**
     * Get general dashboard data (alias for getDashboardFarm)
     * GET /dashboard/farm
     */
    public DashboardData getDashboard() throws IOException {
        return getDashboardFarm();
    }

    /**
     * Execute HTTP request with retry logic
     */
    private <T> T executeWithRetry(Request request, Class<T> responseType) throws IOException {
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                logger.debug("Executing request: {} (attempt {}/{})", request.url(), attempt + 1, retries + 1);
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        throw new IOException(String.format("HTTP %d: %s - %s", 
                                response.code(), response.message(), errorBody));
                    }
                    
                    String responseBody = response.body().string();
                    T result = objectMapper.readValue(responseBody, responseType);
                    
                    logger.debug("Request successful: {}", request.url());
                    return result;
                }
                
            } catch (IOException e) {
                lastException = e;
                logger.warn("Request failed (attempt {}/{}): {}", attempt + 1, retries + 1, e.getMessage());
                
                // Don't retry on client errors (4xx)
                if (e.getMessage().contains("HTTP 4")) {
                    throw e;
                }
                
                // Wait before retrying (except on last attempt)
                if (attempt < retries) {
                    try {
                        Thread.sleep(retryDelay * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("All retry attempts exhausted for request: {}", request.url());
        throw new IOException("Failed after " + (retries + 1) + " attempts", lastException);
    }

    /**
     * Test connection to ML service
     */
    public boolean testConnection() {
        try {
            HealthStatus status = health();
            return status.isHealthy();
        } catch (IOException e) {
            logger.error("ML service connection test failed", e);
            return false;
        }
    }

    /**
     * Close HTTP client resources
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        logger.info("MLClientService shutdown complete");
    }
}
