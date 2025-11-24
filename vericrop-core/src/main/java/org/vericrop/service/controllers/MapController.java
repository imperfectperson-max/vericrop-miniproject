package org.vericrop.service.controllers;

import java.util.Map;

/**
 * Controller for map and routing operations.
 */
public class MapController extends BaseController {
    
    public MapController(ControllerStatusPublisher statusPublisher) {
        super("map", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception {
        logger.info("Executing map controller task");
        
        // Simulate work
        Thread.sleep(1000);
        
        return "Map routing completed";
    }
}
