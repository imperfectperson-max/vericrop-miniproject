# VeriCrop Comprehensive Features Implementation

This document describes the comprehensive features implemented across the VeriCrop system for supply chain management, batch processing, quality tracking, and reporting.

## Overview

This implementation completes the following requirements:
1. Real, exportable reports and summaries for batches and supply-chain events
2. Complete batch creation logic with quality metrics computation
3. Kafka integration for batch processing and order notifications
4. Airflow orchestration for delivery simulation and batch processing
5. Quality decay tracking during storage and transit
6. QR code generation integrated with batch creation
7. Comprehensive logging and reporting to logistics directory

## Directory Structure

### logistics-and-supply-chain/

Top-level directory for all supply chain exports and reports.

```
logistics-and-supply-chain/
├── README.md                    # Directory documentation
├── reports/                     # CSV and PDF reports
│   ├── journey_report_*.csv
│   ├── quality_report_*.csv
│   └── quality_report_*.pdf
├── summaries/                   # Aggregated metrics
│   └── aggregated_metrics_*.csv
├── batches/                     # Batch-specific exports
│   ├── batch_summary_*.csv
│   └── batch_summary_*.pdf
└── supply-chain-events/         # Timeline event logs
    ├── timeline_quality_change_*.json
    └── timeline_quality_assessment_*.json
```

## Services

### 1. BatchReportService
**Location**: `vericrop-gui/src/main/java/org/vericrop/gui/services/BatchReportService.java`

**Purpose**: Generate batch reports with aggregated metrics.

**Key Methods**:
- `computeAggregatedMetrics(List<BatchRecord>)`: Computes avg quality%, prime%, rejection%
- `generateBatchSummary(BatchRecord)`: Creates comprehensive batch summary
- `exportBatchSummary(BatchRecord)`: Exports as CSV and PDF
- `exportAggregatedMetrics(String, List<BatchRecord>)`: Exports aggregated report
- `generateQualityTimeline(List<BatchRecord>)`: Creates quality change timeline

**Quality Thresholds**:
- Prime quality: ≥80% (0.8)
- Rejection threshold: <50% (0.5)

**Metrics Computed**:
- Total batches
- Average quality percentage
- Prime count and percentage
- Rejection count and percentage
- Acceptable count and percentage

### 2. QualityTrackingService
**Location**: `vericrop-gui/src/main/java/org/vericrop/gui/services/QualityTrackingService.java`

**Purpose**: Track batch quality changes over time with decay application.

**Key Methods**:
- `applySupplierHandlingDecay(batchId, temp, humidity, hours)`: Apply storage decay
- `applyTransitDecay(batchId, readings)`: Apply transit decay with waypoint data
- `predictFutureQuality(batchId, temp, humidity, hours)`: Predict future quality
- `getDecayRate(temperature, humidity)`: Calculate decay rate for conditions
- `checkIdealConditions(temperature, humidity)`: Validate environmental conditions

**Quality Decay Model** (from QualityDecayService):
- Base decay: 0.5% per hour in ideal conditions (4-8°C, 70-85% humidity)
- Temperature penalty: 0.3% per degree outside ideal range
- Humidity penalty: 0.1% per percentage outside ideal range

### 3. Enhanced ReportGenerator
**Location**: `vericrop-gui/src/main/java/org/vericrop/gui/util/ReportGenerator.java`

**Purpose**: Generate reports in multiple formats with dual export.

**Key Methods**:
- `generateBatchSummaryCSV(batchId, data)`: Batch summary as CSV
- `generateBatchSummaryPDF(batchId, data)`: Batch summary as PDF
- `generateAggregatedMetricsCSV(name, metrics)`: Aggregated metrics report
- `generateQualityReportPDF(batchId, data)`: Quality report as PDF
- `generateSupplyChainTimelineJSON(eventType, events)`: Timeline logs

**Export Locations**:
- Legacy: `generated_reports/`
- New: `logistics-and-supply-chain/`

**Formats Supported**:
- CSV: Machine-readable, Excel-compatible
- PDF: Human-readable with tables and formatting
- JSON: Structured data for API consumption

## Kafka Integration

### Events

#### BatchCreatedEvent
**Location**: `kafka-service/src/main/java/org/vericrop/kafka/events/BatchCreatedEvent.java`

**Fields**:
- batch_id, batch_name, farmer, product_type, quantity
- quality_score, quality_label, prime_rate, rejection_rate
- image_path, qr_code_path, timestamp, status

**Published When**: New batch is created

#### OrderEvent
**Location**: `kafka-service/src/main/java/org/vericrop/kafka/events/OrderEvent.java`

**Fields**:
- order_id, batch_id, buyer, seller
- quantity_ordered, price_per_unit, total_price
- quality_disclosed, quality_score, prime_rate, rejection_rate
- timestamp, status, delivery_address

**Published When**: Order is placed or updated

### Producers

#### BatchCreatedEventProducer
**Topic**: `batch-created-events`
**Purpose**: Notify system of new batch creation

#### OrderEventProducer
**Topic**: `order-events`
**Purpose**: Notify system of order placement and updates

### Consumers

#### OrderEventConsumer
**Topic**: `order-events`
**Group**: `vericrop-order-processors`

**Functionality**:
1. Validates order details
2. Discloses batch quality to buyer
3. Calculates price adjustments based on quality
4. Updates order status
5. Triggers delivery workflow if accepted

**Price Adjustment Algorithm**:
```
Prime Bonus: +20% maximum (based on prime_rate)
Rejection Penalty: -30% maximum (based on rejection_rate)

adjusted_price = base_price × (1 + prime_bonus - rejection_penalty)
```

**Constants**:
- `PRIME_BONUS_MULTIPLIER = 0.2`
- `REJECTION_PENALTY_MULTIPLIER = 0.3`

## Airflow DAGs

### delivery_simulation_dag.py
**Location**: `airflow/dags/delivery_simulation_dag.py`

**Purpose**: Simulate batch delivery with quality decay.

**Tasks**:
1. **start_delivery_simulation**: Initialize delivery route and conditions
2. **simulate_transit_conditions**: Simulate waypoints with environmental readings
3. **publish_delivery_event_to_kafka**: Publish completion event to logistics-events topic
4. **generate_delivery_report**: Create comprehensive delivery report

**Quality Decay Simulation**:
- Simulates 5 waypoints along route
- Temperature variation: -2°C to +5°C from ideal (6°C)
- Humidity variation: ±15% from ideal (75%)
- Quality decay calculated per waypoint
- Environmental violations tracked

**Report Contents**:
- Delivery summary (origin, destination, duration)
- Quality metrics (initial, final, degradation rate)
- Environmental summary (avg temp/humidity, violations)
- Generated timestamp

## Database Schema

### V5 Migration: Add Image and QR Paths
**Location**: `vericrop-gui/src/main/resources/db/migration/V5__add_image_and_qr_paths.sql`

**Columns Added to `batches` table**:
- `image_path VARCHAR(512)`: Path to batch image file
- `qr_code_path VARCHAR(512)`: Path to generated QR code
- `prime_rate NUMERIC(5, 4)`: Prime quality rate (fraction 0-1)
- `rejection_rate NUMERIC(5, 4)`: Rejection rate (fraction 0-1)

**Indexes Created**:
- `idx_batches_image_path`: Partial index on non-null image paths
- `idx_batches_qr_code_path`: Partial index on non-null QR paths

**Note**: Rates are stored as fractions (0.0000 to 1.0000), not percentages.

## Batch Metrics Algorithm

### Quality Classification

Based on ProducerController implementation:

**For FRESH classification**:
```
prime_rate = min(80 + quality_percent × 20, 100) / 100
remainder = 100 - (prime_rate × 100)
rejection_rate = (remainder × 0.2) / 100
```

**For LOW_QUALITY classification**:
```
low_quality_rate = min(80 + quality_percent × 20, 100)
remainder = 100 - low_quality_rate
prime_rate = (remainder × 0.8) / 100
rejection_rate = (remainder × 0.2) / 100
```

**For ROTTEN classification**:
```
rejection_rate = min(80 + quality_percent × 20, 100) / 100
remainder = 100 - (rejection_rate × 100)
prime_rate = (remainder × 0.2) / 100
```

### Quality Label Normalization

**Utility**: `QualityLabelUtil`
**Location**: `vericrop-gui/src/main/java/org/vericrop/gui/util/QualityLabelUtil.java`

**Purpose**: Ensure consistent quality label handling across system.

**Method**: `normalize(String label)`
- Converts to uppercase
- Replaces spaces with underscores
- Returns "UNKNOWN" for null input

**Helper Methods**:
- `isFresh(label)`: Checks for FRESH, HIGH_QUALITY, EXCELLENT
- `isLowQuality(label)`: Checks for LOW_QUALITY, FAIR, POOR
- `isRotten(label)`: Checks for ROTTEN, REJECTED, SPOILED

## QR Code Integration

**Generator**: `QRGenerator`
**Location**: `vericrop-gui/src/main/java/org/vericrop/gui/util/QRGenerator.java`

**Features**:
- Generates scannable RGB PNG QR codes
- Size: 300x300 pixels
- Error correction: HIGH level
- Encoding: UTF-8

**Generated Files**:
- Product QR: `generated_qr/product_{batchId}.png`
- Shipment QR: `generated_qr/shipment_{shipmentId}.png`

**QR Content Format** (JSON):
```json
{
  "type": "product",
  "id": "BATCH_12345",
  "farmer": "John Farmer",
  "verifyUrl": "https://vericrop.app/verify?id=BATCH_12345"
}
```

**Integration**:
- QR generated during batch creation
- Path stored in BatchRecord.qr_code_path
- Persisted to database via PostgresBatchRepository

## Usage Examples

### Generate Batch Report

```java
BatchReportService reportService = new BatchReportService();
List<BatchRecord> batches = batchRepository.findAll();

// Generate aggregated metrics
Map<String, Object> metrics = reportService.computeAggregatedMetrics(batches);
// metrics contains: total_batches, avg_quality_percent, prime_percent, rejection_percent

// Export to CSV
Path reportPath = reportService.exportAggregatedMetrics("Daily Summary", batches);
// Saved to: logistics-and-supply-chain/summaries/aggregated_metrics_daily_summary_*.csv
```

### Apply Quality Decay During Storage

```java
QualityTrackingService trackingService = new QualityTrackingService(decayService, repository);

// Apply decay during 24 hours in storage at 10°C, 60% humidity
BatchRecord batch = trackingService.applySupplierHandlingDecay(
    "BATCH_12345", 
    10.0,  // temperature (outside ideal 4-8°C)
    60.0,  // humidity (below ideal 70-85%)
    24.0   // hours elapsed
);

// Batch quality updated, prime/rejection rates recomputed
// Event logged to logistics-and-supply-chain/supply-chain-events/
```

### Process Order with Quality Disclosure

```java
// Create order event
OrderEvent order = new OrderEvent(
    "ORDER_001", "BATCH_12345", "Buyer Co.", "Farmer Co.",
    100, 5.00, LocalDateTime.now().toString()
);

// Disclose quality for price negotiation
order.discloseQuality(0.85, 0.70, 0.05);

// Publish to Kafka
orderProducer.sendEvent(order);

// OrderEventConsumer processes:
// - Validates order
// - Calculates adjusted price based on quality
// - Updates order status
```

### Simulate Delivery with Quality Decay

```python
# Trigger Airflow DAG via API
airflow_client.trigger_dag(
    'vericrop_delivery_simulation',
    conf={
        'batch_id': 'BATCH_12345',
        'origin': 'Farm Location',
        'destination': 'Distribution Center',
        'initial_quality': 0.85
    }
)

# DAG executes:
# 1. Creates 5 waypoints with environmental readings
# 2. Applies quality decay at each waypoint
# 3. Publishes delivery event to Kafka
# 4. Generates delivery report with metrics
```

## Testing

### Build and Test

```bash
# Build all modules
./gradlew build -x test

# Run tests
./gradlew test

# Test specific module
./gradlew :vericrop-gui:test
./gradlew :kafka-service:test
```

### Manual Testing

1. **Start Services**:
```bash
docker-compose up -d postgres kafka ml-service airflow-webserver airflow-scheduler
```

2. **Run Application**:
```bash
./gradlew :vericrop-gui:run
```

3. **Verify Reports Generated**:
```bash
ls -la logistics-and-supply-chain/reports/
ls -la logistics-and-supply-chain/batches/
ls -la logistics-and-supply-chain/summaries/
```

4. **Check Kafka Topics**:
```bash
docker exec -it vericrop-kafka kafka-topics --list --bootstrap-server localhost:9092
docker exec -it vericrop-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic batch-created-events \
  --from-beginning
```

5. **Trigger Airflow DAG**:
- Access Airflow UI: http://localhost:8080
- Username/Password: admin/admin
- Enable and trigger `vericrop_delivery_simulation`

## Security Considerations

1. **File Paths**: All generated files use relative paths within project directory
2. **Database**: Rates stored as fractions (0-1) with NUMERIC(5, 4) precision
3. **Kafka**: Topics can be secured with SASL/SSL in production
4. **QR Codes**: URLs should use HTTPS in production
5. **Reports**: Contain sensitive batch quality data - secure access required

## Future Enhancements

1. **QR Scanner**: Implement ConsumerController QR scanning capability
2. **Farmer Directories**: Organize QR codes by farmer name
3. **Real-time Dashboard**: Live quality monitoring across all batches
4. **Email Notifications**: Send reports to stakeholders
5. **API Endpoints**: REST APIs for external system integration
6. **Blockchain Integration**: Enhanced immutability for quality records

## References

- Main README: `/README.md`
- Logistics Directory: `/logistics-and-supply-chain/README.md`
- GUI Setup: `/docs/GUI-setup.md`
- Kafka Integration: `/KAFKA_INTEGRATION.md`
- Producer Controller: `/PRODUCER_CONTROLLER_IMPLEMENTATION.md`

## Contact

For questions or issues related to this implementation, please refer to the project maintainers or open an issue on GitHub.
