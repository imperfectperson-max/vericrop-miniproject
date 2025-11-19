package org.vericrop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MLServiceClient {
    private static final String ML_SERVICE_URL = "http://localhost:8000";
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public MLServiceClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    public MLPrediction predictQuality(File imageFile) throws IOException {
        // Validate input
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist");
        }

        // Validate file size
        if (imageFile.length() > 10 * 1024 * 1024) { // 10MB limit
            throw new IllegalArgumentException("Image file too large (max 10MB)");
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/*")))
                .build();

        Request request = new Request.Builder()
                .url(ML_SERVICE_URL + "/predict")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response from ML service");
            }

            String responseBody = body.string();
            return mapper.readValue(responseBody, MLPrediction.class);
        } catch (Exception e) {
            throw new IOException("Failed to call ML service: " + e.getMessage(), e);
        }
    }

    public static class MLPrediction {
        private double quality_score;
        private String label;
        private String report;
        private String data_hash;
        private String model_accuracy;

        // Default constructor for Jackson
        public MLPrediction() {}

        // Getters and setters
        public double getQuality_score() { return quality_score; }
        public void setQuality_score(double quality_score) {
            this.quality_score = quality_score;
        }

        public String getLabel() { return label != null ? label : "Unknown"; }
        public void setLabel(String label) { this.label = label; }

        public String getReport() { return report != null ? report : ""; }
        public void setReport(String report) { this.report = report; }

        public String getData_hash() { return data_hash != null ? data_hash : ""; }
        public void setData_hash(String data_hash) { this.data_hash = data_hash; }

        public String getModel_accuracy() { return model_accuracy != null ? model_accuracy : "Unknown"; }
        public void setModel_accuracy(String model_accuracy) { this.model_accuracy = model_accuracy; }

        @Override
        public String toString() {
            return String.format("MLPrediction{quality=%.2f, label='%s', hash='%s'}",
                    quality_score, label, data_hash != null && data_hash.length() > 16 ?
                            data_hash.substring(0, 16) + "..." : data_hash);
        }
    }
}