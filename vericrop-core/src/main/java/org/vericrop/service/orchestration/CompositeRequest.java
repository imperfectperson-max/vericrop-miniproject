package org.vericrop.service.orchestration;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for initiating a composite orchestration.
 * Contains all necessary information to kickoff the combined workflow.
 */
public class CompositeRequest {
    private String batchId;
    private String farmerId;
    private String productType;
    private Map<String, Object> metadata;
    
    public CompositeRequest() {
        this.metadata = new HashMap<>();
    }
    
    public CompositeRequest(String batchId, String farmerId) {
        this.batchId = batchId;
        this.farmerId = farmerId;
        this.metadata = new HashMap<>();
    }
    
    public String getBatchId() {
        return batchId;
    }
    
    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }
    
    public String getFarmerId() {
        return farmerId;
    }
    
    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }
    
    public String getProductType() {
        return productType;
    }
    
    public void setProductType(String productType) {
        this.productType = productType;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    public void putMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    @Override
    public String toString() {
        return String.format("CompositeRequest{batchId='%s', farmerId='%s', productType='%s', metadata=%s}",
            batchId, farmerId, productType, metadata);
    }
}
