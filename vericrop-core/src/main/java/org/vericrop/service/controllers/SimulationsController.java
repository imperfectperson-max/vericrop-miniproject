package org.vericrop.service.controllers;

import java.util.Map;

/**
 * Controller for running simulations.
 */
public class SimulationsController extends BaseController {
    
    public SimulationsController(ControllerStatusPublisher statusPublisher) {
        super("simulations", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, Map<String, Object> parameters) throws Exception {
        logger.info("Executing simulations controller task");
        
        // Simulate work
        Thread.sleep(2500);
        
        return "Simulations run completed";
    }
}
