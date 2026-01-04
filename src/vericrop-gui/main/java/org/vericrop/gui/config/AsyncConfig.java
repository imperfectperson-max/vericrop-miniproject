package org.vericrop.gui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Async configuration for Spring asynchronous processing.
 * Provides a configured ThreadPoolTaskExecutor for async operations
 * and custom exception handling for uncaught async exceptions.
 * 
 * <p>This configuration enables the @Async annotation to work properly
 * and provides a thread pool for background simulation creation tasks.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);
    
    /**
     * Creates and configures a ThreadPoolTaskExecutor for async operations.
     * 
     * <p>Thread pool configuration:</p>
     * <ul>
     *   <li>Core pool size: 2 threads for normal load</li>
     *   <li>Max pool size: 4 threads for peak load</li>
     *   <li>Queue capacity: 50 tasks waiting</li>
     *   <li>Thread name prefix: "SimulationAsync-"</li>
     * </ul>
     * 
     * @return Configured task executor
     */
    @Bean(name = "simulationAsyncExecutor")
    public Executor simulationAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("SimulationAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        logger.info("✅ SimulationAsyncExecutor initialized with core={}, max={}, queue={}",
                   executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    @Override
    public Executor getAsyncExecutor() {
        return simulationAsyncExecutor();
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }
    
    /**
     * Custom exception handler for uncaught exceptions in async methods.
     * Logs the exception details for debugging and monitoring.
     */
    private static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            logger.error("❌ Async exception in method '{}': {}", method.getName(), ex.getMessage(), ex);
            
            // Log parameters for debugging
            if (params.length > 0) {
                StringBuilder paramInfo = new StringBuilder("Parameters: ");
                for (int i = 0; i < params.length; i++) {
                    paramInfo.append(params[i] != null ? params[i].toString() : "null");
                    if (i < params.length - 1) {
                        paramInfo.append(", ");
                    }
                }
                logger.error("   {}", paramInfo);
            }
        }
    }
}
