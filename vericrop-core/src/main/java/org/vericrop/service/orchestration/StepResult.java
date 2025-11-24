package org.vericrop.service.orchestration;

import java.util.HashMap;
import java.util.Map;

/**
 * Result data from a completed step.
 * Flexible structure to accommodate different types of results.
 */
public class StepResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> data;
    
    public StepResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = new HashMap<>();
    }
    
    public StepResult(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }
    
    public void putData(String key, Object value) {
        data.put(key, value);
    }
    
    public Object getData(String key) {
        return data.get(key);
    }
    
    @Override
    public String toString() {
        return String.format("StepResult{success=%s, message='%s', data=%s}", 
            success, message, data);
    }
}
