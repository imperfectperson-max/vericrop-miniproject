package org.vericrop.service.controllers;

import java.util.Map;

/**
 * Controller for delivery operations.
 */
public class DeliveryController extends BaseController {
    
    public DeliveryController(ControllerStatusPublisher statusPublisher) {
        super("delivery", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception {
        logger.info("Executing delivery controller task");
        
        // Simulate work
        Thread.sleep(1500);
        
        return "Delivery processing completed";
    }
}
