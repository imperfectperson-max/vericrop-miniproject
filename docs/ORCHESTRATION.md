# VeriCrop Orchestration System

## Overview

The VeriCrop Orchestration System provides event-driven coordination of multiple controllers through Kafka messaging and Airflow workflow automation. This enables parallel execution of scenarios, delivery simulations, map routing, temperature monitoring, supplier compliance checks, and other operations.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       Airflow DAG                                │
│                  (vericrop_orchestrator)                         │
└──────────────────┬──────────────────────────────────────────────┘
                   │ 1. Trigger
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Kafka: orchestrator.start                        │
└──────────────────┬──────────────────────────────────────────────┘
                   │ 2. Consume
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Orchestrator Component                        │
│              (vericrop-core/orchestrator)                        │
└──────────┬────────────────────────────────────────────┬─────────┘
           │ 3. Publish Commands                        │ 5. Aggregate
           ▼                                            │
┌───────────────────────────────────────────┐          │
│    Kafka: controller.*.command Topics     │          │
└──────────┬────────────────────────────────┘          │
           │ 4. Consume & Execute                      │
           ▼                                            │
┌───────────────────────────────────────────┐          │
│         Controller Implementations         │          │
│  • scenarios                               │          │
│  • delivery                                │          │
│  • map                                     │          │
│  • temperature                             │          │
│  • supplier_compliance                     │          │
│  • simulations                             │          │
└──────────┬────────────────────────────────┘          │
           │ 6. Publish Status                         │
           ▼                                            │
┌───────────────────────────────────────────┐          │
│    Kafka: controller.*.status Topics      │──────────┘
└───────────────────────────────────────────┘
                                                        │
                                                        │ 7. Completion
                                                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              Kafka: orchestrator.completed                       │
└──────────────────┬──────────────────────────────────────────────┘
                   │ 8. Monitor
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Airflow DAG                                 │
│           (Sensor waits for completion)                          │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Orchestrator

**Location**: `vericrop-core/src/main/java/org/vericrop/service/orchestrator/Orchestrator.java`

The core orchestration component that:
- Tracks multiple controller executions
- Manages timeouts and failures
- Aggregates results from all controllers
- Emits completion events

**Key Features**:
- Independent and testable design
- Configurable timeout (default: 5 minutes)
- Parallel controller execution
- Automatic cleanup of completed orchestrations

### 2. Kafka Events

**Location**: `kafka-service/src/main/java/org/vericrop/kafka/events/`

Event DTOs for orchestration communication:

- **OrchestratorStartEvent**: Triggers orchestration with list of controllers
- **ControllerCommandEvent**: Commands sent to individual controllers
- **ControllerStatusEvent**: Status updates from controllers (started, completed, failed)
- **OrchestratorCompletedEvent**: Final aggregated result

### 3. Kafka Topics

Defined in `kafka-service/src/main/java/org/vericrop/kafka/KafkaConfig.java`:

#### Orchestrator Topics
- `vericrop.orchestrator.start` - Trigger orchestration
- `vericrop.orchestrator.completed` - Orchestration completion

#### Controller Command Topics
- `vericrop.controller.scenarios.command`
- `vericrop.controller.delivery.command`
- `vericrop.controller.map.command`
- `vericrop.controller.temperature.command`
- `vericrop.controller.supplier_compliance.command`
- `vericrop.controller.simulations.command`

#### Controller Status Topics
- `vericrop.controller.scenarios.status`
- `vericrop.controller.delivery.status`
- `vericrop.controller.map.status`
- `vericrop.controller.temperature.status`
- `vericrop.controller.supplier_compliance.status`
- `vericrop.controller.simulations.status`

### 4. Controllers

**Location**: `vericrop-core/src/main/java/org/vericrop/service/controllers/`

Base controller class with standardized orchestration support:

```java
public abstract class BaseController {
    protected abstract String executeTask(String orchestrationId, 
                                         Map<String, Object> parameters) throws Exception;
}
```

**Implemented Controllers**:
1. **ScenariosController** - Scenario-based simulation analysis
2. **DeliveryController** - Delivery processing and routing
3. **MapController** - Map and routing operations
4. **TemperatureController** - Temperature monitoring
5. **SupplierComplianceController** - Supplier compliance checks
6. **SimulationsController** - Simulation execution

### 5. Airflow DAG

**Location**: `airflow/dags/vericrop_orchestrator_dag.py`

Workflow automation for orchestration:

**Tasks**:
1. **trigger_orchestrator** - Publishes start event to Kafka
2. **wait_for_completion** - Sensor that polls for completion event
3. **handle_result** - Processes success/failure and logs results

## How It Works

### 1. Triggering Orchestration

#### Via Airflow

Manually trigger the DAG in Airflow UI:
```
http://localhost:8082/dags/vericrop_orchestrator/grid
```

Or via Airflow CLI:
```bash
airflow dags trigger vericrop_orchestrator
```

#### Via Kafka Directly (for testing)

Publish to `vericrop.orchestrator.start` topic:
```json
{
  "orchestration_id": "ORCH_TEST_001",
  "timestamp": 1234567890000,
  "controllers": [
    "scenarios",
    "delivery",
    "map",
    "temperature",
    "supplier_compliance",
    "simulations"
  ],
  "parameters": {
    "origin": "manual_test"
  }
}
```

### 2. Controller Execution Flow

When a controller receives a start command:

1. **Started** - Publishes `started` status
2. **Executing** - Runs `executeTask()` method
3. **Completed/Failed** - Publishes `completed` or `failed` status with summary

Status message format:
```json
{
  "orchestration_id": "ORCH_TEST_001",
  "controller_name": "scenarios",
  "instance_id": "uuid",
  "status": "completed",
  "timestamp": 1234567890000,
  "summary": "Scenarios analysis completed",
  "error_message": null
}
```

### 3. Monitoring Progress

The orchestrator tracks all controller status updates and:
- Monitors for timeouts (default: 5 minutes)
- Detects when all controllers complete
- Aggregates success/failure counts
- Emits final completion event

### 4. Completion Event

When orchestration completes:
```json
{
  "orchestration_id": "ORCH_TEST_001",
  "timestamp": 1234567890000,
  "success": true,
  "total_controllers": 6,
  "completed_controllers": 6,
  "failed_controllers": 0,
  "summary": "Orchestration completed: 6/6 succeeded, 0 failed, timeout: false",
  "controller_results": {
    "scenarios": "completed - Scenarios analysis completed",
    "delivery": "completed - Delivery processing completed",
    "map": "completed - Map routing completed",
    "temperature": "completed - Temperature monitoring completed",
    "supplier_compliance": "completed - Supplier compliance check completed",
    "simulations": "completed - Simulations run completed"
  }
}
```

## Local Development Setup

### Prerequisites

1. **Kafka** - Running on `localhost:9092`
2. **Airflow** - Running on `localhost:8082`
3. **Java 17+** - For building VeriCrop components

### Starting Services

```bash
# 1. Start Kafka and Zookeeper
docker-compose up -d kafka zookeeper

# 2. Start Airflow
docker-compose up -d airflow-webserver airflow-scheduler postgres-airflow

# 3. Verify services
docker-compose ps
curl http://localhost:8082/health  # Airflow
```

### Building VeriCrop

```bash
# Build all modules
./gradlew build

# Build specific modules
./gradlew :vericrop-core:build
./gradlew :kafka-service:build
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run orchestrator tests
./gradlew test --tests "*OrchestratorTest"
```

## Configuration

### Environment Variables

Set in `.env` or system environment:

```bash
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ENABLED=true

# Orchestrator Configuration
ORCHESTRATOR_TIMEOUT_MINUTES=10
```

### application.properties

Add to `vericrop-gui/src/main/resources/application.properties`:

```properties
# Orchestrator Kafka Topics
vericrop.orchestrator.start.topic=vericrop.orchestrator.start
vericrop.orchestrator.completed.topic=vericrop.orchestrator.completed

# Controller Topics
vericrop.controller.scenarios.command=vericrop.controller.scenarios.command
vericrop.controller.scenarios.status=vericrop.controller.scenarios.status
# ... (repeat for other controllers)
```

## Testing Orchestration

### Integration Test

Run the integration test that simulates the full orchestration flow:

```bash
./gradlew test --tests "*OrchestratorIntegrationTest"
```

### Manual Testing with Kafka

1. **Start Kafka consumer** to monitor completion:
```bash
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.orchestrator.completed \
  --from-beginning
```

2. **Trigger orchestration** via Airflow UI or CLI:
```bash
airflow dags trigger vericrop_orchestrator
```

3. **Monitor controller status topics**:
```bash
# Watch scenarios controller
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.controller.scenarios.status \
  --from-beginning
```

### Expected Timeline

For default controller implementations:
- **scenarios**: ~2 seconds
- **delivery**: ~1.5 seconds
- **map**: ~1 second
- **temperature**: ~1.2 seconds
- **supplier_compliance**: ~1.8 seconds
- **simulations**: ~2.5 seconds

**Total Duration**: ~3 seconds (parallel execution)

## Troubleshooting

### Orchestration Timeout

**Symptom**: Orchestration completes with timeout=true

**Solution**:
1. Check controller logs for slow tasks
2. Increase timeout: `ORCHESTRATOR_TIMEOUT_MINUTES=20`
3. Verify Kafka connectivity

### Controller Not Responding

**Symptom**: Controller status never reaches "completed"

**Solution**:
1. Verify controller is subscribed to command topic
2. Check controller logs for exceptions
3. Verify Kafka topic exists:
```bash
docker exec -it vericrop-kafka kafka-topics \
  --list --bootstrap-server localhost:9092
```

### Airflow DAG Not Completing

**Symptom**: wait_for_completion sensor times out

**Solution**:
1. Check if completion event was published:
```bash
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vericrop.orchestrator.completed \
  --from-beginning
```
2. Verify Airflow can connect to Kafka
3. Check Airflow task logs for errors

### Missing Kafka Topics

**Symptom**: "Unknown topic" errors in logs

**Solution**:
Kafka auto-creates topics on first use. Alternatively, create manually:
```bash
docker exec -it vericrop-kafka kafka-topics \
  --create --topic vericrop.orchestrator.start \
  --bootstrap-server localhost:9092 \
  --partitions 1 --replication-factor 1
```

## Extending the System

### Adding a New Controller

1. **Create controller class**:
```java
package org.vericrop.service.controllers;

public class MyNewController extends BaseController {
    public MyNewController(ControllerStatusPublisher statusPublisher) {
        super("my_new_controller", statusPublisher);
    }
    
    @Override
    protected String executeTask(String orchestrationId, 
                                Map<String, Object> parameters) throws Exception {
        // Implementation here
        return "My task completed";
    }
}
```

2. **Add Kafka topics** to `KafkaConfig.java`:
```java
public static final String TOPIC_CONTROLLER_MY_NEW_COMMAND = 
    "vericrop.controller.my_new_controller.command";
public static final String TOPIC_CONTROLLER_MY_NEW_STATUS = 
    "vericrop.controller.my_new_controller.status";
```

3. **Update orchestrator producer** to recognize new controller name

4. **Add controller to DAG** in `vericrop_orchestrator_dag.py`:
```python
controllers = [
    'scenarios',
    'delivery',
    # ... existing controllers
    'my_new_controller'  # Add here
]
```

### Custom Orchestration Logic

Extend the `Orchestrator` class or create a new orchestration service:
```java
public class CustomOrchestrator extends Orchestrator {
    public CustomOrchestrator(OrchestrationEventPublisher eventPublisher) {
        super(eventPublisher);
    }
    
    @Override
    protected void completeOrchestration(OrchestrationContext context) {
        // Custom completion logic
        super.completeOrchestration(context);
    }
}
```

## Performance Considerations

- **Parallel Execution**: Controllers run in parallel, total time ≈ slowest controller
- **Kafka Throughput**: Default configuration handles 100+ orchestrations/minute
- **Memory**: Each orchestration context ~1KB, cleaned after 60 seconds
- **Scaling**: Add more Kafka partitions for higher throughput

## Security

- **Kafka Authentication**: Configure SASL/SSL for production
- **Topic ACLs**: Restrict topic access per controller
- **Input Validation**: Controllers should validate parameters
- **Rate Limiting**: Consider rate limiting orchestration triggers

## References

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [VeriCrop Main README](../README.md)
- [Kafka Integration Guide](../KAFKA_INTEGRATION.md)

## Support

For issues or questions:
- **GitHub Issues**: [vericrop-miniproject/issues](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
- **Documentation**: Check main README and other docs in `/docs`
