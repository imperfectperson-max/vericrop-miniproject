package org.vericrop.gui.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.vericrop.gui.util.Config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service class for interacting with the VeriCrop ML backend API.
 * Handles HTTP requests to /predict, /batches, and /dashboard/farm endpoints.
 * Provides resilience with timeouts, retries, and demo mode fallback.
 */
public class ApiService {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final int MAX_RETRIES = 2;
    
    public ApiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Predict image quality by calling POST /predict endpoint.
     * If model is not loaded and demo mode is enabled, returns a deterministic demo prediction.
     * 
     * @param imageFile the image file to analyze
     * @return Map containing quality_score (0..1), label, confidence (~0..1), data_hash
     * @throws IOException if the request fails and demo mode is not enabled
     */
    public Map<String, Object> predictImage(File imageFile) throws IOException {
        String url = Config.getMlServiceEndpoint("/predict");
        
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/*")))
                .build();
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    // Check if it's a 503 (service unavailable) and demo mode is enabled
                    if (response.code() == 503 && Config.isDemoMode()) {
                        System.out.println("⚠️ ML service unavailable, using demo mode");
                        return getDemoPrediction();
                    }
                    throw new IOException("Prediction request failed: HTTP " + response.code());
                }
                
                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, 
                        new TypeReference<Map<String, Object>>() {});
                
                // Ensure required fields are present with safe defaults
                return ensurePredictionFields(result);
                
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.out.println("⚠️ Prediction attempt " + (attempt + 1) + " failed, retrying...");
                    try {
                        Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted", ie);
                    }
                }
            }
        }
        
        // If all retries failed and demo mode is enabled, return demo data
        if (Config.isDemoMode()) {
            System.out.println("⚠️ All prediction attempts failed, using demo mode");
            return getDemoPrediction();
        }
        
        throw new IOException("ML service unavailable after " + MAX_RETRIES + " retries. " +
                "Enable demo mode in the login screen to use sample data.", lastException);
    }
    
    /**
     * Create a batch by calling POST /batches endpoint.
     * Ensures returned Map contains quality_score, prime_rate, rejection_rate, batch_id, and data_hash.
     * 
     * @param batchData the batch data to send
     * @return Map containing batch creation result with all required fields
     * @throws IOException if the request fails
     */
    public Map<String, Object> createBatch(Map<String, Object> batchData) throws IOException {
        String url = Config.getMlServiceEndpoint("/batches");
        
        String jsonBody = objectMapper.writeValueAsString(batchData);
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 503 && Config.isDemoMode()) {
                        return getDemoBatchCreation(batchData);
                    }
                    throw new IOException("Batch creation failed: HTTP " + response.code());
                }
                
                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, 
                        new TypeReference<Map<String, Object>>() {});
                
                // Ensure all required fields are present
                return ensureBatchFields(result, batchData);
                
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.out.println("⚠️ Batch creation attempt " + (attempt + 1) + " failed, retrying...");
                    try {
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted", ie);
                    }
                }
            }
        }
        
        // If all retries failed and demo mode is enabled, return demo data
        if (Config.isDemoMode()) {
            System.out.println("⚠️ All batch creation attempts failed, using demo mode");
            return getDemoBatchCreation(batchData);
        }
        
        throw new IOException("ML service unavailable after " + MAX_RETRIES + " retries", lastException);
    }
    
    /**
     * Get farm dashboard data by calling GET /dashboard/farm endpoint.
     * 
     * @return Map containing dashboard data with kpis, counts, quality_distribution, recent_batches
     * @throws IOException if the request fails
     */
    public Map<String, Object> getFarmDashboard() throws IOException {
        String url = Config.getMlServiceEndpoint("/dashboard/farm");
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 503 && Config.isDemoMode()) {
                        return getDemoDashboard();
                    }
                    throw new IOException("Dashboard request failed: HTTP " + response.code());
                }
                
                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, 
                        new TypeReference<Map<String, Object>>() {});
                
                return result;
                
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.out.println("⚠️ Dashboard request attempt " + (attempt + 1) + " failed, retrying...");
                    try {
                        Thread.sleep(500 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Request interrupted", ie);
                    }
                }
            }
        }
        
        // If all retries failed and demo mode is enabled, return demo data
        if (Config.isDemoMode()) {
            System.out.println("⚠️ All dashboard attempts failed, using demo mode");
            return getDemoDashboard();
        }
        
        throw new IOException("ML service unavailable after " + MAX_RETRIES + " retries", lastException);
    }
    
    /**
     * Get a deterministic demo prediction.
     * 
     * @return Map with demo prediction data
     */
    private Map<String, Object> getDemoPrediction() {
        Map<String, Object> demo = new HashMap<>();
        demo.put("quality_score", 0.85);
        demo.put("label", "fresh");
        demo.put("confidence", 0.92);
        demo.put("data_hash", "demo_hash_" + String.format("%04d", (int)(System.currentTimeMillis() % 10000)));
        demo.put("model_accuracy", "demo_mode");
        
        // Add all_predictions for compatibility
        Map<String, Double> allPredictions = new HashMap<>();
        allPredictions.put("fresh", 0.92);
        allPredictions.put("good", 0.05);
        allPredictions.put("ripe", 0.03);
        demo.put("all_predictions", allPredictions);
        
        return demo;
    }
    
    /**
     * Get a deterministic demo batch creation response.
     * 
     * @param batchData the original batch data
     * @return Map with demo batch creation result
     */
    private Map<String, Object> getDemoBatchCreation(Map<String, Object> batchData) {
        Map<String, Object> demo = new HashMap<>();
        demo.put("batch_id", "DEMO_BATCH_" + System.currentTimeMillis());
        demo.put("status", "created");
        demo.put("timestamp", java.time.Instant.now().toString());
        demo.put("message", "Demo batch created successfully");
        demo.put("quality_score", 0.85);
        demo.put("prime_rate", 0.75);
        demo.put("rejection_rate", 0.10);
        demo.put("quality_label", "fresh");
        demo.put("data_hash", batchData.get("data_hash"));
        return demo;
    }
    
    /**
     * Get demo dashboard data.
     * 
     * @return Map with demo dashboard data
     */
    private Map<String, Object> getDemoDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("total_batches_today", 12);
        kpis.put("average_quality", 85.0);
        kpis.put("prime_percentage", 75.0);
        kpis.put("rejection_rate", 10.0);
        dashboard.put("kpis", kpis);
        
        Map<String, Object> counts = new HashMap<>();
        counts.put("prime_count", 9);
        counts.put("rejected_count", 1);
        counts.put("total_count", 10);
        dashboard.put("counts", counts);
        
        Map<String, Object> distribution = new HashMap<>();
        distribution.put("prime", 9);
        distribution.put("standard", 2);
        distribution.put("sub_standard", 1);
        dashboard.put("quality_distribution", distribution);
        
        dashboard.put("recent_batches", new java.util.ArrayList<>());
        dashboard.put("timestamp", java.time.Instant.now().toString());
        
        return dashboard;
    }
    
    /**
     * Ensure prediction result has all required fields with safe defaults.
     * 
     * @param result the prediction result from API
     * @return the result with all required fields
     */
    private Map<String, Object> ensurePredictionFields(Map<String, Object> result) {
        if (!result.containsKey("quality_score")) {
            result.put("quality_score", 0.8);
            System.out.println("⚠️ Missing quality_score in prediction, using default 0.8");
        }
        if (!result.containsKey("label")) {
            result.put("label", "unknown");
            System.out.println("⚠️ Missing label in prediction, using 'unknown'");
        }
        if (!result.containsKey("confidence")) {
            result.put("confidence", 0.8);
            System.out.println("⚠️ Missing confidence in prediction, using default 0.8");
        }
        if (!result.containsKey("data_hash")) {
            result.put("data_hash", "");
            System.out.println("⚠️ Missing data_hash in prediction, using empty string");
        }
        return result;
    }
    
    /**
     * Ensure batch creation result has all required fields with safe defaults.
     * 
     * @param result the batch creation result from API
     * @param originalData the original batch data sent
     * @return the result with all required fields
     */
    private Map<String, Object> ensureBatchFields(Map<String, Object> result, Map<String, Object> originalData) {
        if (!result.containsKey("quality_score")) {
            // Try to extract from original data
            Object qualityData = originalData.get("quality_data");
            if (qualityData instanceof Map) {
                Object qs = ((Map<?, ?>) qualityData).get("quality_score");
                result.put("quality_score", qs != null ? qs : 0.8);
            } else {
                result.put("quality_score", 0.8);
            }
            System.out.println("⚠️ Missing quality_score in batch result, using fallback");
        }
        if (!result.containsKey("prime_rate")) {
            result.put("prime_rate", 0.7);
            System.out.println("⚠️ Missing prime_rate in batch result, using default 0.7");
        }
        if (!result.containsKey("rejection_rate")) {
            result.put("rejection_rate", 0.1);
            System.out.println("⚠️ Missing rejection_rate in batch result, using default 0.1");
        }
        if (!result.containsKey("batch_id")) {
            result.put("batch_id", "BATCH_" + System.currentTimeMillis());
            System.out.println("⚠️ Missing batch_id in batch result, generating fallback");
        }
        if (!result.containsKey("data_hash")) {
            result.put("data_hash", originalData.get("data_hash"));
        }
        return result;
    }
}
