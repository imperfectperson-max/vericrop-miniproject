package org.vericrop.service.controllers;

import java.util.Map;

/**
 * Controller for scenario-based simulations.
 */
public class ScenariosController extends BaseController {
    
    public ScenariosController(ControllerStatusPublisher statusPublisher) {
        super("scenarios", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception {
        logger.info("Executing scenarios controller task");
        
        // Simulate work
        Thread.sleep(2000);
        
        return "Scenarios analysis completed";
    }
}
