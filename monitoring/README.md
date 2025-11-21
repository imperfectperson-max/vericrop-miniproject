# VeriCrop Monitoring & Observability

This directory contains monitoring configurations for VeriCrop production deployments.

## Overview

VeriCrop uses a comprehensive monitoring stack:

- **Prometheus**: Metrics collection and alerting
- **Grafana**: Visualization and dashboards
- **AlertManager**: Alert routing and notification
- **Exporters**: Specialized metrics collectors (PostgreSQL, Kafka, Node)

## Quick Start

### Docker Compose Deployment

```bash
# Start monitoring stack
docker-compose -f docker-compose.monitoring.yml up -d

# Access Grafana
open http://localhost:3000
# Default credentials: admin/admin (change on first login)

# Access Prometheus
open http://localhost:9090

# Access AlertManager
open http://localhost:9093
```

### Kubernetes Deployment

```bash
# Install Prometheus Operator
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --values monitoring/prometheus/values-k8s.yaml

# Access Grafana (port-forward)
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80

# Access Prometheus
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090
```

## Metrics Endpoints

VeriCrop services expose the following metrics endpoints:

| Service | Endpoint | Port | Format |
|---------|----------|------|--------|
| VeriCrop GUI | `/actuator/prometheus` | 8080 | Prometheus |
| ML Service | `/metrics` | 8000 | Prometheus |
| PostgreSQL | N/A (via exporter) | 9187 | Prometheus |
| Kafka | N/A (via exporter) | 9308 | Prometheus |
| Node | N/A (via exporter) | 9100 | Prometheus |

## Available Metrics

### Application Metrics

**VeriCrop GUI (Spring Boot Actuator):**
- `http_server_requests_seconds_*` - HTTP request duration and count
- `jvm_memory_*` - JVM memory usage
- `jvm_gc_*` - Garbage collection metrics
- `jvm_threads_*` - Thread count and states
- `hikaricp_connections_*` - Database connection pool
- `vericrop_login_attempts_total` - Login attempts by status
- `vericrop_batch_creation_*` - Batch creation metrics
- `vericrop_blockchain_validation_*` - Blockchain integrity checks

**ML Service (FastAPI):**
- `ml_prediction_duration_seconds` - Prediction latency
- `ml_prediction_errors_total` - Prediction error count
- `ml_predictions_total` - Total predictions count
- `http_requests_total` - HTTP requests by endpoint and status

### Infrastructure Metrics

**PostgreSQL Exporter:**
- `pg_up` - PostgreSQL availability
- `pg_stat_database_*` - Database statistics
- `pg_stat_user_tables_*` - Table statistics
- `pg_locks_*` - Database locks

**Kafka Exporter:**
- `kafka_brokers` - Number of available brokers
- `kafka_topic_*` - Topic metrics (partitions, replicas, size)
- `kafka_consumer_group_*` - Consumer group lag and offsets

**Node Exporter:**
- `node_cpu_*` - CPU usage and stats
- `node_memory_*` - Memory usage
- `node_disk_*` - Disk I/O and usage
- `node_network_*` - Network traffic

## Alerts

Alert rules are defined in `prometheus/alerts.yml` and include:

### Critical Alerts (Immediate Action Required)

- **ServiceDown**: Any VeriCrop service is unreachable
- **PostgresDown**: Database is unavailable
- **KafkaBrokerDown**: Kafka broker is down
- **MLServiceDown**: ML service is unavailable
- **OutOfDiskSpace**: Disk usage > 95%
- **BlockchainValidationFailure**: Data integrity issue

### Warning Alerts (Attention Required)

- **HighErrorRate**: HTTP 5xx errors > 5%
- **HighResponseTime**: 99th percentile > 2s
- **HighCPUUsage**: CPU usage > 80% for 10 minutes
- **HighMemoryUsage**: Memory usage > 90%
- **HighDiskUsage**: Disk usage > 85%
- **HighConsumerLag**: Kafka consumer lag > 1000 messages
- **HighFailedLoginAttempts**: Possible brute force attack

## Alert Routing

Configure AlertManager to route alerts to appropriate channels:

```yaml
# alertmanager.yml
route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  
  routes:
    # Critical alerts -> PagerDuty
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    
    # Security alerts -> Slack #security
    - match:
        team: security
      receiver: 'slack-security'
    
    # Database alerts -> Slack #database
    - match:
        team: database
      receiver: 'slack-database'
    
    # All alerts -> Email
    - match_re:
        severity: (warning|critical)
      receiver: 'email'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:5001/webhook'
  
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_KEY'
        description: '{{ .CommonAnnotations.summary }}'
  
  - name: 'slack-security'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#security'
        title: '{{ .CommonAnnotations.summary }}'
        text: '{{ .CommonAnnotations.description }}'
  
  - name: 'email'
    email_configs:
      - to: 'ops@vericrop.local'
        from: 'alertmanager@vericrop.local'
        smarthost: 'smtp.gmail.com:587'
        auth_username: 'alerts@vericrop.local'
        auth_password: 'YOUR_PASSWORD'
```

## Grafana Dashboards

Pre-built dashboards are available in `grafana/dashboards/`:

1. **VeriCrop Overview** - High-level system health and KPIs
2. **Application Performance** - Request rates, latency, errors
3. **Database Monitoring** - PostgreSQL performance and queries
4. **Kafka Monitoring** - Topics, consumer lag, broker health
5. **ML Service Monitoring** - Prediction latency and throughput
6. **JVM Metrics** - Heap usage, GC, threads
7. **Infrastructure** - CPU, memory, disk, network

### Importing Dashboards

```bash
# Via Grafana UI
1. Login to Grafana (http://localhost:3000)
2. Click + → Import
3. Upload JSON file from grafana/dashboards/
4. Select Prometheus data source
5. Click Import

# Via API
curl -X POST http://admin:admin@localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @grafana/dashboards/vericrop-overview.json
```

## Custom Metrics

To add custom application metrics, use Micrometer in Spring Boot:

```java
@Component
public class BatchMetrics {
    private final Counter batchCreationCounter;
    private final Timer batchCreationTimer;
    
    public BatchMetrics(MeterRegistry registry) {
        this.batchCreationCounter = Counter.builder("vericrop.batch.creation.total")
            .description("Total number of batch creations")
            .tag("status", "success")
            .register(registry);
        
        this.batchCreationTimer = Timer.builder("vericrop.batch.creation.duration")
            .description("Batch creation duration")
            .register(registry);
    }
    
    public void recordBatchCreation(String status, Duration duration) {
        batchCreationCounter.increment();
        batchCreationTimer.record(duration);
    }
}
```

## Troubleshooting

### Metrics Not Appearing

1. Check service health:
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8080/actuator/prometheus
   ```

2. Check Prometheus targets:
   - Open http://localhost:9090/targets
   - Verify all targets are UP

3. Check Prometheus logs:
   ```bash
   docker logs prometheus
   # or
   kubectl logs -n monitoring prometheus-prometheus-kube-prometheus-prometheus-0
   ```

### Alerts Not Firing

1. Check alert rules:
   - Open http://localhost:9090/alerts
   - Verify rules are loaded

2. Check AlertManager:
   - Open http://localhost:9093
   - Check alert status

3. Verify alert configuration:
   ```bash
   promtool check rules prometheus/alerts.yml
   ```

### High Cardinality Issues

If Prometheus is consuming too much memory:

1. Reduce scrape interval in `prometheus.yml`
2. Limit label cardinality in application code
3. Increase Prometheus memory limits
4. Use recording rules for high-cardinality metrics

## Best Practices

1. **Label Naming**: Use consistent label names across services
2. **Metric Names**: Follow Prometheus naming conventions
   - `<namespace>_<name>_<unit>` (e.g., `vericrop_batch_creation_total`)
   - Use base units (seconds, bytes, not milliseconds or kilobytes)
3. **Instrumentation**: Instrument critical paths and business metrics
4. **Alert Tuning**: Adjust thresholds based on actual usage patterns
5. **Dashboard Organization**: Group related metrics in dashboards
6. **Data Retention**: Configure appropriate retention periods
   - Development: 7 days
   - Staging: 30 days
   - Production: 90 days (or use remote storage)

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)

## Support

For monitoring issues:
1. Check logs: `docker-compose logs prometheus grafana`
2. Review Prometheus targets: http://localhost:9090/targets
3. Check alert status: http://localhost:9090/alerts
4. Open GitHub issue: https://github.com/imperfectperson-max/vericrop-miniproject/issues

---

**Last Updated**: 2024-01-15  
**Maintained By**: VeriCrop DevOps Team
