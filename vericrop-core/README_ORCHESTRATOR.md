# VeriCrop Orchestrator

## Overview

The VeriCrop Orchestrator provides a centralized REST API endpoint to trigger coordinated runs of multiple subsystems simultaneously. It uses Apache Kafka for loose coupling and parallel execution, allowing subsystems to run independently while being coordinated from a single entry point.

## Architecture

The orchestrator consists of the following components:

1. **OrchestratorController** - REST API endpoint for triggering orchestration
2. **OrchestratorService** - Business logic for publishing start messages to Kafka
3. **KafkaPublisher** - Reusable utility for publishing JSON messages to Kafka topics
4. **StartTopicConsumers** - Consumer stubs that listen for start messages and trigger subsystem logic

## Subsystems

The orchestrator supports the following subsystems:

- `scenarios` - Scenario management and execution
- `delivery` - Delivery simulation and tracking
- `map` - Mapping and geolocation services
- `temperature` - Temperature monitoring and alerts
- `supplier-compliance` - Supplier compliance checks
- `simulations` - General simulation engine

## Kafka Topics

Each subsystem has a dedicated Kafka topic for start messages:

- `vericrop.scenarios.start`
- `vericrop.delivery.start`
- `vericrop.map.start`
- `vericrop.temperature.start`
- `vericrop.supplier_compliance.start`
- `vericrop.simulations.start`

## Usage

### Triggering Orchestration via REST API

#### Trigger All Subsystems

```bash
curl -X POST http://localhost:8080/api/orchestrator/run \
  -H "Content-Type: application/json"
```

#### Trigger Specific Subsystems

```bash
curl -X POST http://localhost:8080/api/orchestrator/run \
  -H "Content-Type: application/json" \
  -d '{
    "subsystems": ["scenarios", "delivery", "simulations"]
  }'
```

#### Trigger with Custom Run ID

```bash
curl -X POST http://localhost:8080/api/orchestrator/run \
  -H "Content-Type: application/json" \
  -d '{
    "subsystems": ["delivery", "temperature"],
    "runId": "custom-run-2024-001"
  }'
```

### Response Format

The orchestrator returns HTTP 202 Accepted with a JSON response:

```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:00Z",
  "subsystems": ["scenarios", "delivery", "map", "temperature", "supplier-compliance", "simulations"],
  "successful": ["scenarios", "delivery", "map", "temperature", "supplier-compliance", "simulations"],
  "errors": [],
  "status": "success"
}
```

### Health Check

Check if the orchestrator is running:

```bash
curl http://localhost:8080/api/orchestrator/health
```

Response:
```json
{
  "status": "healthy",
  "service": "orchestrator"
}
```

## Configuration

### Kafka Bootstrap Servers

Configure Kafka bootstrap servers in `application-orchestrator.properties` or via environment variable:

```properties
kafka.bootstrap.servers=localhost:9092
```

Or:

```bash
export KAFKA_BOOTSTRAP_SERVERS=kafka-broker:9092
```

### Spring Boot Integration

To use the orchestrator in a Spring Boot application (e.g., vericrop-gui):

1. Ensure vericrop-core is included as a dependency
2. Enable component scanning for orchestrator packages:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.vericrop.gui",
    "org.vericrop.orchestrator",
    "org.vericrop.kafka"
})
public class MainApp {
    // ...
}
```

3. Include `application-orchestrator.properties` or merge its settings into your `application.properties`

## Integrating Subsystems

### For Controllers/Services in vericrop-core

If your subsystem controller/service is in vericrop-core, wire it directly in `StartTopicConsumers.java`:

```java
@Autowired
private MySubsystemService mySubsystemService;

@KafkaListener(topics = "vericrop.mysubsystem.start", groupId = "orchestrator-consumer-group")
public void consumeMySubsystemStart(String message) {
    Map<String, Object> payload = objectMapper.readValue(message, Map.class);
    String runId = (String) payload.get("runId");
    
    // Call your service
    mySubsystemService.execute(runId);
}
```

### For Controllers in Other Modules (vericrop-gui, etc.)

If your subsystem controller is in another module or microservice, use `RestTemplate` or `WebClient` to make HTTP calls:

```java
@Autowired
private RestTemplate restTemplate;

@KafkaListener(topics = "vericrop.mysubsystem.start", groupId = "orchestrator-consumer-group")
public void consumeMySubsystemStart(String message) {
    Map<String, Object> payload = objectMapper.readValue(message, Map.class);
    
    // Call external API
    String url = "http://localhost:8080/api/mysubsystem/run";
    restTemplate.postForEntity(url, payload, String.class);
}
```

### For Independent Microservices

If your subsystem runs as a separate microservice:

1. Implement a Kafka consumer in that service listening to the appropriate topic
2. Parse the start message and trigger your business logic
3. Return HTTP 202 Accepted or appropriate status

## Airflow Integration

The orchestrator can be scheduled via Apache Airflow using the provided DAG:

### Using the Orchestrator DAG

1. The DAG is located at `airflow/dags/orchestrator_dag.py`
2. It is paused by default - enable it in Airflow UI if needed
3. Two triggering approaches are available:

#### Approach 1: Direct Kafka Publishing (Default)

The DAG publishes start messages directly to Kafka topics:

```python
# Enabled by default in the DAG
trigger_kafka = PythonOperator(
    task_id='trigger_via_kafka',
    python_callable=publish_orchestrator_kafka
)
```

#### Approach 2: HTTP REST API

Alternatively, use the SimpleHttpOperator to call the orchestrator endpoint:

```python
# Uncomment in DAG to use this approach
trigger_http = SimpleHttpOperator(
    task_id='trigger_via_http',
    endpoint='/api/orchestrator/run',
    method='POST',
    data=json.dumps({'subsystems': ['scenarios', 'delivery']})
)
```

### Configuring Airflow

Set environment variables for Airflow:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export VERICROP_API_URL=http://localhost:8080
```

### Scheduling

The DAG is set to manual trigger by default. To schedule it:

1. Edit `orchestrator_dag.py`
2. Change `schedule_interval` from `None` to your desired schedule:
   - `'@daily'` - Once per day
   - `'@hourly'` - Once per hour
   - `'0 8 * * *'` - Daily at 8 AM (cron format)

## Development and Testing

### Manual Testing with Kafka

1. Start Kafka locally:
   ```bash
   docker-compose -f docker-compose-kafka.yml up -d
   ```

2. Start the application with orchestrator profile:
   ```bash
   ./gradlew :vericrop-gui:bootRun --args='--spring.profiles.active=orchestrator'
   ```

3. Trigger orchestration:
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/run
   ```

4. Monitor Kafka topics:
   ```bash
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic vericrop.scenarios.start \
     --from-beginning
   ```

### Verifying Consumer Logs

Check application logs to see consumer activity:

```bash
tail -f logs/vericrop-gui.log | grep -E '\[SCENARIOS\]|\[DELIVERY\]|\[SIMULATIONS\]'
```

Expected output:
```
[SCENARIOS] Received start message - runId: 550e8400-e29b-41d4-a716-446655440000
[DELIVERY] Received start message - runId: 550e8400-e29b-41d4-a716-446655440000
...
```

## Troubleshooting

### Kafka Connection Issues

**Problem**: `Connection to node -1 could not be established`

**Solution**: Verify Kafka is running and bootstrap servers are configured correctly:
```bash
docker ps | grep kafka
echo $KAFKA_BOOTSTRAP_SERVERS
```

### Consumers Not Receiving Messages

**Problem**: Start messages are published but consumers don't trigger

**Possible causes**:
1. Consumer group offset is set to `latest` instead of `earliest`
2. Consumers are not registered (component scanning issue)
3. Kafka listener is not enabled

**Solution**:
- Check `spring.kafka.consumer.auto-offset-reset=earliest` in properties
- Verify component scanning includes `org.vericrop.kafka.consumers`
- Check logs for consumer registration messages

### Subsystem Integration

**Problem**: Consumer receives message but subsystem doesn't execute

**Solution**: This is expected! The provided consumers are scaffolded with TODO markers. You need to:
1. Wire your subsystem service/controller
2. Implement the actual business logic call
3. Test end-to-end integration

## Production Considerations

1. **Security**: Add authentication/authorization to the orchestrator endpoint
2. **Monitoring**: Integrate with monitoring tools to track orchestration runs
3. **Error Handling**: Implement dead letter queues for failed messages
4. **Idempotency**: Ensure subsystems can handle duplicate start messages
5. **Scalability**: Use Kafka consumer groups to scale consumer instances

## Contributing

To add a new subsystem to the orchestrator:

1. Add the topic constant in `OrchestratorService.java`
2. Add the subsystem case in `getTopicForSubsystem()` method
3. Add a new `@KafkaListener` method in `StartTopicConsumers.java`
4. Update this README with the new subsystem details

## Support

For issues or questions:
- Check the [main README](../README.md)
- Review Kafka integration docs in `KAFKA_INTEGRATION.md`
- Open an issue on GitHub
