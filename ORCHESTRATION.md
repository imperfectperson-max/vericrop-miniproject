# VeriCrop Orchestration Architecture

## Overview

The VeriCrop orchestration system enables concurrent execution of multiple functional areas across the supply chain. It coordinates scenarios, delivery tracking, map generation, temperature monitoring, supplier compliance checking, and simulations using a correlation-based event-driven architecture.

## Architecture

### Components

1. **OrchestrationService** (vericrop-core)
   - Manages orchestration lifecycle
   - Tracks progress of concurrent steps
   - Provides status queries

2. **Kafka Event Bus**
   - Distributes orchestration events across services
   - Uses correlation IDs for distributed tracing
   - Topics for each functional area

3. **Airflow DAGs**
   - Handles long-running orchestrated workflows
   - Publishes completion events back to Kafka

4. **Controllers** (vericrop-gui)
   - Initiate orchestrations via composite requests
   - Return 202 Accepted with orchestration ID
   - Provide status polling endpoints

## Orchestration Flow

```
┌──────────────┐
│   Client     │
│  (GUI/API)   │
└──────┬───────┘
       │
       │ POST /orchestration/start
       │ (Composite Request)
       ▼
┌──────────────────────────────────────┐
│   OrchestrationService               │
│   • Generate correlationId           │
│   • Register expected steps          │
│   • Return 202 Accepted              │
└──────────┬───────────────────────────┘
           │
           │ Publish events with correlationId
           ▼
┌──────────────────────────────────────┐
│          Kafka Topics                │
│  • vericrop.orchestration.scenarios  │
│  • vericrop.orchestration.delivery   │
│  • vericrop.orchestration.map        │
│  • vericrop.orchestration.temperature│
│  • vericrop.orchestration.compliance │
│  • vericrop.orchestration.simulations│
└──────────┬───────────────────────────┘
           │
           ├─────► Scenario Processor
           ├─────► Delivery Tracker
           ├─────► Map Generator
           ├─────► Temperature Monitor
           ├─────► Compliance Checker
           └─────► Simulation Engine
                   │
                   │ Completion Events
                   ▼
           ┌──────────────────────┐
           │ OrchestrationService │
           │ • markStepComplete() │
           │ • Update status      │
           └──────────────────────┘
```

## Usage

### Starting an Orchestration

```java
// Create a composite request
CompositeRequest request = new CompositeRequest("BATCH-001", "FARMER-001");
request.setProductType("Apples");

// Start orchestration
OrchestrationService orchestrationService = new OrchestrationService();
String correlationId = orchestrationService.startOrchestration(request);

// Publish events to Kafka for each step
OrchestrationEventProducer producer = new OrchestrationEventProducer();
producer.sendScenariosEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");
producer.sendDeliveryEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");
producer.sendMapEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");
producer.sendTemperatureEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");
producer.sendSupplierComplianceEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");
producer.sendSimulationsEvent(correlationId, "BATCH-001", "FARMER-001", "STARTED");

// Return to client
return ResponseEntity.accepted()
    .header("X-Orchestration-Id", correlationId)
    .body(Map.of(
        "orchestration_id", correlationId,
        "status_url", "/orchestration/status/" + correlationId
    ));
```

### Checking Status

```java
OrchestrationStatus status = orchestrationService.getStatus(correlationId);

System.out.println("Status: " + status.getStatus());
System.out.println("Batch: " + status.getBatchId());
System.out.println("Progress: " + 
    status.getSteps().values().stream().filter(s -> s.isComplete()).count() + 
    "/" + status.getSteps().size());
```

### Completing a Step

```java
StepResult result = new StepResult(true, "Scenarios processed successfully");
result.putData("scenario_count", 5);
result.putData("success_rate", 0.95);

orchestrationService.markStepComplete(correlationId, StepType.SCENARIOS, result);
```

### Triggering Airflow DAGs

```python
from airflow.dags.orchestration_helpers import trigger_dag_with_correlation_id

# Trigger a DAG with orchestration context
dag_run = trigger_dag_with_correlation_id(
    dag_id='orchestration_simulation',
    correlation_id='ORCH-12345',
    batch_id='BATCH-001',
    farmer_id='FARMER-001',
    step_type='simulations',
    additional_conf={'scenario': 'temperature_stress'}
)
```

### Publishing Completion from Airflow

```python
from airflow.dags.orchestration_helpers import (
    get_orchestration_context,
    publish_orchestration_completion_event
)

def my_task(**context):
    # Extract orchestration context
    orch_context = get_orchestration_context(context)
    
    # Do work...
    result = process_data()
    
    # Publish completion event
    publish_orchestration_completion_event(
        correlation_id=orch_context['correlation_id'],
        batch_id=orch_context['batch_id'],
        step_type=orch_context['step_type'],
        success=True,
        message='Processing completed',
        data=result
    )
```

## Kafka Topics

All orchestration topics follow the pattern: `vericrop.orchestration.<step_type>`

| Topic | Purpose |
|-------|---------|
| `vericrop.orchestration.scenarios` | Scenario execution and completion events |
| `vericrop.orchestration.delivery` | Delivery tracking and logistics events |
| `vericrop.orchestration.map` | Route and map generation events |
| `vericrop.orchestration.temperature` | Temperature monitoring events |
| `vericrop.orchestration.supplierCompliance` | Supplier compliance checking events |
| `vericrop.orchestration.simulations` | Simulation execution events |

### Event Structure

All orchestration events have the following structure:

```json
{
  "correlation_id": "ORCH-uuid",
  "batch_id": "BATCH-001",
  "farmer_id": "FARMER-001",
  "step_type": "scenarios",
  "event_type": "STARTED|IN_PROGRESS|COMPLETED|FAILED",
  "timestamp": 1234567890000,
  "metadata": {
    "additional": "data"
  }
}
```

### Message Headers

All events include a correlation ID header for distributed tracing:

```
X-Correlation-Id: ORCH-uuid
```

## Orchestration States

| State | Description |
|-------|-------------|
| `IN_FLIGHT` | Orchestration started, no steps complete |
| `PARTIAL` | Some steps complete, some pending |
| `COMPLETE` | All steps complete |

## API Endpoints

### Start Orchestration

```
POST /api/orchestration/start
Content-Type: application/json

{
  "batch_id": "BATCH-001",
  "farmer_id": "FARMER-001",
  "product_type": "Apples",
  "metadata": {
    "priority": "high"
  }
}

Response: 202 Accepted
{
  "orchestration_id": "ORCH-uuid",
  "status": "IN_FLIGHT",
  "status_url": "/api/orchestration/status/ORCH-uuid"
}
```

### Check Status

```
GET /api/orchestration/status/{orchestrationId}

Response: 200 OK
{
  "correlation_id": "ORCH-uuid",
  "batch_id": "BATCH-001",
  "farmer_id": "FARMER-001",
  "status": "PARTIAL",
  "start_time": 1234567890000,
  "steps": {
    "SCENARIOS": {
      "complete": true,
      "completion_time": 1234567892000,
      "result": {
        "success": true,
        "message": "Scenarios processed",
        "data": {...}
      }
    },
    "DELIVERY": {
      "complete": false,
      "completion_time": 0,
      "result": null
    },
    ...
  }
}
```

## Testing

### Unit Tests

```java
OrchestrationService service = new OrchestrationService();
CompositeRequest request = new CompositeRequest("BATCH-001", "FARMER-001");

// Start orchestration
String id = service.startOrchestration(request);
assertNotNull(id);

// Check initial state
OrchestrationStatus status = service.getStatus(id);
assertEquals(OrchestrationStatus.Status.IN_FLIGHT, status.getStatus());

// Complete a step
StepResult result = new StepResult(true, "Done");
service.markStepComplete(id, StepType.SCENARIOS, result);

// Check updated state
status = service.getStatus(id);
assertEquals(OrchestrationStatus.Status.PARTIAL, status.getStatus());
```

### Integration Tests

See `OrchestrationIntegrationTest.java` for end-to-end testing with Kafka.

## Configuration

### Application Properties

```properties
# Kafka configuration
kafka.bootstrap.servers=localhost:9092
kafka.orchestration.topic.prefix=vericrop.orchestration

# Orchestration service configuration
orchestration.cleanup.interval=3600000  # 1 hour
orchestration.max.age=86400000          # 24 hours
```

### Environment Variables

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export ORCHESTRATION_SERVICE_ENABLED=true
```

## Monitoring

### Metrics

The orchestration service exposes the following metrics:

- `orchestration.active` - Number of active orchestrations
- `orchestration.complete` - Number of completed orchestrations
- `orchestration.step.duration` - Duration of each step type
- `orchestration.failure.rate` - Failure rate by step type

### Logging

Set log level for orchestration:

```properties
logging.level.org.vericrop.service.orchestration=DEBUG
```

## Best Practices

1. **Always use correlation IDs** - Include correlation IDs in all service calls and logs for distributed tracing

2. **Handle failures gracefully** - Design services to handle partial failures and retry logic

3. **Monitor orchestration progress** - Implement dashboards to track orchestration metrics

4. **Clean up old orchestrations** - Periodically clean up completed orchestrations to free memory

5. **Use idempotent operations** - Ensure operations can be safely retried without side effects

6. **Validate input early** - Validate composite requests before starting orchestration

7. **Publish completion events** - Always publish completion events, even on failure

## Troubleshooting

### Orchestration Stuck in IN_FLIGHT

Check if all step processors are running and consuming from Kafka topics.

```bash
# Check Kafka consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group orchestration-consumer-group
```

### Step Not Completing

1. Check Kafka topic for messages
2. Verify consumer is processing messages
3. Check service logs for errors
4. Ensure correlation ID is correctly propagated

### High Memory Usage

Run cleanup periodically:

```java
orchestrationService.cleanupOldOrchestrations(TimeUnit.DAYS.toMillis(1));
```

## Future Enhancements

- [ ] Persistent storage (database) for orchestration state
- [ ] WebSocket support for real-time status updates
- [ ] Saga pattern for compensating transactions
- [ ] Circuit breaker integration
- [ ] Advanced retry policies
- [ ] Orchestration templates
- [ ] Performance optimizations for high-volume scenarios
