# Logistics REST API Documentation

## Overview

The Logistics REST API provides endpoints for monitoring shipments in real-time, including live temperature tracking via Server-Sent Events (SSE) and historical data retrieval.

**Base URL**: `/api/logistics`

## Endpoints

### 1. Stream Temperature Updates (SSE)

Stream live temperature readings for a specific shipment using Server-Sent Events.

**Endpoint**: `GET /api/logistics/shipments/{shipmentId}/temperature/stream`

**Method**: GET

**Content-Type**: `text/event-stream`

**Path Parameters**:
- `shipmentId` (string, required): The batch/shipment identifier

**Response**: Server-Sent Events stream

**Event Types**:
- `temperature`: Temperature reading event
- `status`: Status update event
- `error`: Error event

**Example Usage (JavaScript)**:

```javascript
const eventSource = new EventSource('/api/logistics/shipments/BATCH_123/temperature/stream');

eventSource.addEventListener('temperature', (event) => {
    const reading = JSON.parse(event.data);
    console.log('Temperature:', reading.temperature, '°C at', reading.location_name);
    console.log('Timestamp:', new Date(reading.timestamp));
});

eventSource.addEventListener('status', (event) => {
    const status = JSON.parse(event.data);
    console.log('Status:', status.message);
    console.log('Simulation active:', status.simulation_active);
});

eventSource.addEventListener('error', (event) => {
    const error = JSON.parse(event.data);
    console.error('Error:', error.message);
});

eventSource.onerror = (error) => {
    console.error('Connection error:', error);
    eventSource.close();
};
```

**Temperature Event Data Structure**:

```json
{
  "batch_id": "BATCH_123",
  "timestamp": 1700000000000,
  "temperature": 4.5,
  "sensor_id": "SIM-SENSOR",
  "location_name": "Highway Mile 50"
}
```

**Status Event Data Structure**:

```json
{
  "simulation_active": true,
  "message": "Streaming live temperature data",
  "shipment_id": "BATCH_123"
}
```

**Features**:
- Returns historical readings first (if available)
- Streams live updates when simulation is active
- Automatically subscribes to simulation events
- 30-minute timeout for idle connections
- Proper cleanup on disconnect

---

### 2. Get Temperature History

Retrieve historical temperature readings for a shipment.

**Endpoint**: `GET /api/logistics/shipments/{shipmentId}/temperature-history`

**Method**: GET

**Path Parameters**:
- `shipmentId` (string, required): The batch/shipment identifier

**Query Parameters**:
- `limit` (integer, optional): Maximum number of readings to return (default: 100)

**Response**: JSON object

**Status Codes**:
- `200 OK`: Success
- `500 Internal Server Error`: Server error

**Example Request**:

```bash
curl -X GET "http://localhost:8080/api/logistics/shipments/BATCH_123/temperature-history?limit=50"
```

**Example Response**:

```json
{
  "shipment_id": "BATCH_123",
  "readings": [
    {
      "batch_id": "BATCH_123",
      "timestamp": 1700000000000,
      "temperature": 4.5,
      "sensor_id": "SIM-SENSOR",
      "location_name": "Origin Farm"
    },
    {
      "batch_id": "BATCH_123",
      "timestamp": 1700000060000,
      "temperature": 4.6,
      "sensor_id": "SIM-SENSOR",
      "location_name": "Highway Mile 10"
    }
  ],
  "count": 2,
  "simulation_active": true,
  "timestamp": 1700000120000,
  "message": "Simulation active. Use /stream endpoint for live updates."
}
```

---

### 3. Get Active Shipments

List all currently active shipments with temperature monitoring.

**Endpoint**: `GET /api/logistics/shipments/active`

**Method**: GET

**Response**: JSON object

**Status Codes**:
- `200 OK`: Success
- `500 Internal Server Error`: Server error

**Example Request**:

```bash
curl -X GET "http://localhost:8080/api/logistics/shipments/active"
```

**Example Response**:

```json
{
  "shipments": [
    {
      "shipment_id": "BATCH_123",
      "simulation_active": true,
      "farmer_id": "FARMER_001",
      "progress": 45.5,
      "current_location": "Highway Mile 50"
    }
  ],
  "count": 1,
  "timestamp": 1700000000000
}
```

---

### 4. Health Check

Check the health status of the Logistics API.

**Endpoint**: `GET /api/logistics/health`

**Method**: GET

**Response**: JSON object

**Status Codes**:
- `200 OK`: Service is healthy

**Example Request**:

```bash
curl -X GET "http://localhost:8080/api/logistics/health"
```

**Example Response**:

```json
{
  "status": "UP",
  "service": "logistics-api",
  "timestamp": 1700000000000
}
```

---

## Usage Scenarios

### Scenario 1: Real-time Dashboard Monitoring

For a live dashboard that displays temperature monitoring:

1. Connect to the SSE stream endpoint
2. Display historical data immediately
3. Update chart/UI as new temperature events arrive
4. Handle status events to show simulation state
5. Reconnect on error with exponential backoff

### Scenario 2: Historical Analysis

For analyzing past temperature data:

1. Call the temperature-history endpoint
2. Specify appropriate limit based on time range
3. Check `simulation_active` flag
4. Process returned readings for analysis

### Scenario 3: Multi-Shipment Monitoring

For monitoring multiple active shipments:

1. Call the active shipments endpoint
2. For each active shipment, establish SSE connection
3. Maintain separate streams for each shipment
4. Handle disconnects and reconnects gracefully

---

## Best Practices

### 1. SSE Connection Management

- **Always close connections** when no longer needed
- **Implement reconnection logic** with exponential backoff
- **Handle network errors** gracefully
- **Set appropriate timeouts** based on your use case

### 2. Data Handling

- **Buffer temperature data** to avoid overwhelming UI
- **Limit displayed data points** for performance
- **Store historical data** if long-term analysis is needed
- **Handle out-of-order events** (use timestamps)

### 3. Error Handling

- **Monitor error events** from SSE stream
- **Log connection issues** for debugging
- **Provide user feedback** on connection status
- **Implement fallback** to polling if SSE fails

---

## Integration Examples

### React Integration

```javascript
import { useEffect, useState } from 'react';

function TemperatureMonitor({ shipmentId }) {
  const [temperatures, setTemperatures] = useState([]);
  const [status, setStatus] = useState(null);

  useEffect(() => {
    const eventSource = new EventSource(
      `/api/logistics/shipments/${shipmentId}/temperature/stream`
    );

    eventSource.addEventListener('temperature', (event) => {
      const reading = JSON.parse(event.data);
      setTemperatures(prev => [...prev, reading].slice(-20)); // Keep last 20
    });

    eventSource.addEventListener('status', (event) => {
      const statusData = JSON.parse(event.data);
      setStatus(statusData);
    });

    return () => {
      eventSource.close();
    };
  }, [shipmentId]);

  return (
    <div>
      <h2>Temperature Monitor</h2>
      {status && <div>Status: {status.message}</div>}
      <ul>
        {temperatures.map((reading, idx) => (
          <li key={idx}>
            {reading.temperature}°C at {reading.location_name}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

### Python Integration

```python
import requests
import json

def monitor_temperature(shipment_id):
    url = f"http://localhost:8080/api/logistics/shipments/{shipment_id}/temperature/stream"
    
    with requests.get(url, stream=True) as response:
        for line in response.iter_lines():
            if line:
                decoded_line = line.decode('utf-8')
                if decoded_line.startswith('data:'):
                    data = json.loads(decoded_line[5:])
                    print(f"Temperature: {data['temperature']}°C at {data['location_name']}")

# Usage
monitor_temperature('BATCH_123')
```

---

## Architecture Notes

### Server-Side Implementation

The SSE endpoint is implemented in `LogisticsRestController`:
- Uses Spring's `SseEmitter` for streaming
- Subscribes to `SimulationService` for live updates
- Automatically sends historical data first
- Manages connections and cleanup

### Event Flow

1. Client connects to SSE endpoint
2. Server sends historical readings immediately
3. Server checks if simulation is active
4. If active, server subscribes to `SimulationService`
5. Live temperature events are forwarded to client
6. Connection is maintained until timeout or disconnect

### Scalability Considerations

- SSE connections are long-lived (30 min timeout)
- Each connection uses one thread from the web server pool
- For high-scale deployments, consider:
  - Using reactive frameworks (WebFlux)
  - Implementing connection pooling
  - Load balancing across multiple instances
  - Using message queues for event distribution

---

## Troubleshooting

### Connection Issues

**Problem**: SSE connection drops frequently

**Solutions**:
- Check network stability
- Increase timeout if needed
- Implement reconnection logic
- Check server logs for errors

### Missing Data

**Problem**: Not receiving temperature updates

**Solutions**:
- Verify simulation is active
- Check shipment ID is correct
- Confirm `SimulationService` is running
- Review server logs for subscription errors

### Performance Issues

**Problem**: High memory usage or slow updates

**Solutions**:
- Limit number of historical readings
- Implement data buffering on client
- Close unused connections
- Monitor server resources

---

## Future Enhancements

Potential improvements for future versions:

1. **Authentication & Authorization**: Add JWT-based auth
2. **WebSocket Support**: Alternative to SSE for bidirectional communication
3. **Data Persistence**: Store temperature readings in database
4. **Alert Thresholds**: Real-time alerting for temperature violations
5. **Multiple Sensor Support**: Track multiple sensors per shipment
6. **Aggregation**: Summary statistics and trend analysis
7. **GraphQL API**: Alternative query interface
8. **Rate Limiting**: Prevent abuse and ensure fair usage

---

## Support

For issues or questions about the Logistics API:
- Review application logs for detailed error messages
- Check system health via the `/health` endpoint
- Consult the main project documentation
- Review test cases for usage examples
