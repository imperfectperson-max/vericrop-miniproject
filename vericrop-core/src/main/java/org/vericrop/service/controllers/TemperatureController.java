package org.vericrop.service.controllers;

import java.util.Map;

/**
 * Controller for temperature monitoring.
 */
public class TemperatureController extends BaseController {
    
    public TemperatureController(ControllerStatusPublisher statusPublisher) {
        super("temperature", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception {
        logger.info("Executing temperature controller task");
        
        // Simulate work
        Thread.sleep(1200);
        
        return "Temperature monitoring completed";
    }
}
