# Concurrent Scenario Orchestration

## Overview

The VeriCrop platform now supports concurrent execution of multiple scenario groups across all controller endpoints. This orchestration capability allows parallel processing of six distinct scenario groups, improving performance and enabling complex workflows.

## Architecture

### Scenario Groups

The system supports six scenario groups that execute concurrently:

1. **SCENARIOS** - General scenario simulations (various delivery scenarios)
2. **DELIVERY** - Delivery tracking and logistics flows
3. **MAP** - Map and route visualization
4. **TEMPERATURE** - Temperature monitoring and environmental data
5. **SUPPLIER_COMPLIANCE** - Supplier compliance checks and validation
6. **SIMULATIONS** - Advanced simulation workflows

### Components

#### OrchestrationService

The core orchestration service located in `vericrop-core/src/main/java/org/vericrop/service/orchestration/OrchestrationService.java` manages:

- **Concurrent Execution**: Uses `CompletableFuture` for parallel scenario execution
- **Kafka Integration**: Publishes events to scenario-specific Kafka topics
- **Airflow Integration**: Triggers Airflow DAGs for analytics workflows
- **Result Aggregation**: Collects and aggregates per-scenario execution status
- **Error Handling**: Tracks partial failures and timeouts

#### DTOs

**ScenarioOrchestrationRequest**
```java
ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
    "req-001",              // requestId
    "user-123",             // userId
    scenarioGroups,         // Set<ScenarioGroup>
    contextData,            // Map<String, Object>
    30000                   // timeoutMs
);
```

**ScenarioOrchestrationResult**
```java
ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(request);

// Access overall status
boolean success = result.isOverallSuccess();
String message = result.getMessage();
long duration = result.getDurationMs();

// Access per-scenario status
Map<ScenarioGroup, ScenarioExecutionStatus> statuses = result.getScenarioStatuses();
for (ScenarioExecutionStatus status : statuses.values()) {
    System.out.println("Scenario: " + status.getScenarioGroup());
    System.out.println("Status: " + status.getStatus());
    System.out.println("Message: " + status.getMessage());
}
```

## Configuration

### Environment Variables

#### Kafka Configuration
```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Kafka broker address
```

#### Airflow Configuration
```bash
AIRFLOW_BASE_URL=http://localhost:8080  # Airflow web server URL
AIRFLOW_USERNAME=admin                   # Airflow username (optional)
AIRFLOW_PASSWORD=admin                   # Airflow password (optional)
```

### Kafka Topics

The orchestration service publishes events to the following Kafka topics:

- `scenarios` - General scenario events
- `delivery` - Delivery tracking events
- `map` - Map visualization events
- `temperature` - Temperature monitoring events
- `supplierCompliance` - Supplier compliance events
- `simulations` - Advanced simulation events

### Airflow DAGs

Corresponding Airflow DAGs are triggered for analytics workflows:

- `scenarios_dag`
- `delivery_dag`
- `map_dag`
- `temperature_dag`
- `supplierCompliance_dag`
- `simulations_dag`

## Controller Integration

### Example: ProducerController

```java
@RestController
@RequestMapping("/api/producer")
public class ProducerController {
    
    private final OrchestrationService orchestrationService;
    
    @PostMapping("/batch/create")
    public ResponseEntity<Map<String, Object>> createBatch(@RequestBody BatchRequest request) {
        // Prepare context
        Map<String, Object> context = new HashMap<>();
        context.put("batchId", request.getBatchId());
        context.put("farmerId", request.getFarmerId());
        
        // Run concurrent scenarios
        ScenarioOrchestrationRequest orchestrationRequest = new ScenarioOrchestrationRequest(
                UUID.randomUUID().toString(),
                request.getUserId(),
                EnumSet.allOf(ScenarioGroup.class),  // All 6 scenarios
                context,
                30000  // 30 second timeout
        );
        
        ScenarioOrchestrationResult result = orchestrationService.runConcurrentScenarios(orchestrationRequest);
        
        // Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isOverallSuccess());
        response.put("message", result.getMessage());
        response.put("duration_ms", result.getDurationMs());
        response.put("scenario_statuses", convertToResponseFormat(result.getScenarioStatuses()));
        
        return ResponseEntity.ok(response);
    }
}
```

### Response Format

```json
{
  "success": true,
  "message": "All 6 scenario groups executed successfully",
  "duration_ms": 1250,
  "scenario_statuses": {
    "SCENARIOS": {
      "status": "SUCCESS",
      "message": "Completed successfully",
      "timestamp": "2024-11-24T05:43:13.157Z"
    },
    "DELIVERY": {
      "status": "SUCCESS",
      "message": "Completed successfully",
      "timestamp": "2024-11-24T05:43:13.257Z"
    },
    ...
  }
}
```

## Testing

### Unit Tests

Unit tests with mocked Kafka and Airflow components are located in:
```
vericrop-core/src/test/java/org/vericrop/service/orchestration/OrchestrationServiceTest.java
```

Run tests:
```bash
./gradlew :vericrop-core:test --tests "*OrchestrationServiceTest"
```

### Integration Tests

Integration tests with Testcontainers (Kafka) can be run using:
```bash
./gradlew :vericrop-core:integrationTest
```

**Note**: Integration tests require Docker to be running.

## Performance

### Concurrency Benefits

With 6 scenario groups executing in parallel:
- **Sequential execution**: ~6000ms (assuming 1000ms per scenario)
- **Concurrent execution**: ~1200ms (with overhead)
- **Performance gain**: ~5x faster

### Thread Pool Configuration

The OrchestrationService uses a fixed thread pool:
```java
private static final int THREAD_POOL_SIZE = 10;
```

This can be adjusted based on workload requirements.

### Timeout Configuration

Default timeout is 60 seconds but can be configured per request:
```java
ScenarioOrchestrationRequest request = new ScenarioOrchestrationRequest(
    requestId,
    userId,
    scenarioGroups,
    context,
    120000  // 2 minute timeout
);
```

## Error Handling

### Partial Failures

The orchestration service tracks partial failures:

```java
ScenarioExecutionStatus status = result.getScenarioStatuses().get(ScenarioGroup.DELIVERY);

if (status.getStatus() == ExecutionStatus.PARTIAL) {
    // Some components succeeded, some failed
    System.out.println("Partial failure: " + status.getMessage());
    System.out.println("Error details: " + status.getErrorDetails());
}
```

### Status Types

- `SUCCESS` - All components executed successfully
- `FAILURE` - Scenario group failed completely
- `TIMEOUT` - Execution exceeded timeout
- `PARTIAL` - Some components succeeded, some failed

### Graceful Degradation

The system continues to function even if Kafka or Airflow are unavailable:

- **Kafka unavailable**: Logs warning, continues execution
- **Airflow unavailable**: Logs warning, continues execution
- **Both unavailable**: Core orchestration still completes

## Monitoring and Logging

### Log Levels

```
INFO  - Orchestration start/complete, major milestones
DEBUG - Detailed execution per scenario group
ERROR - Failures and exceptions
WARN  - Partial failures, service unavailability
```

### Example Logs

```
2024-11-24 05:43:13 INFO  OrchestrationService - Starting orchestration for request: req-001 with 6 scenario groups
2024-11-24 05:43:13 INFO  OrchestrationService - Executing scenario group: SCENARIOS for request: req-001
2024-11-24 05:43:13 INFO  OrchestrationService - Scenario group SCENARIOS completed - Status: SUCCESS
2024-11-24 05:43:14 INFO  OrchestrationService - All scenario groups completed for request: req-001
2024-11-24 05:43:14 INFO  OrchestrationService - Orchestration completed for request: req-001 - Success: true, Duration: 1250ms
```

## Best Practices

1. **Context Data**: Include relevant context (batchId, farmerId, etc.) for traceability
2. **Timeouts**: Set appropriate timeouts based on expected scenario duration
3. **Error Handling**: Always check `isOverallSuccess()` and handle partial failures
4. **Logging**: Use requestId for correlation across distributed components
5. **Resource Cleanup**: Call `orchestrationService.shutdown()` on application shutdown

## Troubleshooting

### Common Issues

**Issue: Scenarios timing out**
- Increase timeout in request
- Check Kafka/Airflow availability
- Review scenario execution logs

**Issue: Kafka connection failures**
- Verify KAFKA_BOOTSTRAP_SERVERS is correct
- Check Kafka broker is running
- Review network connectivity

**Issue: Airflow DAGs not triggering**
- Verify AIRFLOW_BASE_URL is correct
- Check Airflow credentials
- Ensure DAGs are deployed in Airflow

## Future Enhancements

- [ ] Dynamic timeout calculation based on scenario complexity
- [ ] Retry mechanisms for transient failures
- [ ] Circuit breaker integration for external services
- [ ] Metrics collection for performance monitoring
- [ ] Distributed tracing with OpenTelemetry
- [ ] Support for custom scenario group priority

## Support

For questions or issues, please contact the VeriCrop development team or create an issue in the repository.
