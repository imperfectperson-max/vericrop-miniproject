#!/bin/bash

# Multi-Instance Simulation Runner Script
# This script starts and manages multi-instance simulation scenarios
# 
# Usage:
#   ./run-multi-instance-simulation.sh [scenario] [options]
#
# Scenarios:
#   normal            - Normal transit with high quality (default)
#   temperature_breach - Temperature breach scenario
#   route_disruption  - Route disruption scenario
#   all               - Run all scenarios sequentially
#
# Options:
#   --duration SECONDS  - Simulation duration (default: 120)
#   --instances NUM     - Number of controller instances (default: 3)
#   --no-docker         - Use existing Kafka (don't start Docker)
#   --airflow           - Trigger via Airflow DAG
#   --help              - Show this help message

set -e

# Default configuration
SCENARIO="${1:-normal}"
DURATION=120
NUM_INSTANCES=3
USE_DOCKER=true
USE_AIRFLOW=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --instances)
            NUM_INSTANCES="$2"
            shift 2
            ;;
        --no-docker)
            USE_DOCKER=false
            shift
            ;;
        --airflow)
            USE_AIRFLOW=true
            shift
            ;;
        --help)
            head -30 "$0" | tail -25
            # exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            # exit 1
            ;;
    esac
done

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  VeriCrop Multi-Instance Simulation ${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo -e "  Scenario:    ${GREEN}$SCENARIO${NC}"
echo -e "  Duration:    ${GREEN}${DURATION}s${NC}"
echo -e "  Instances:   ${GREEN}$NUM_INSTANCES${NC}"
echo -e "  Docker:      ${GREEN}$USE_DOCKER${NC}"
echo -e "  Airflow:     ${GREEN}$USE_AIRFLOW${NC}"
echo ""

# Function to check if Kafka is running
check_kafka() {
    if command -v nc &> /dev/null; then
        nc -z localhost 9092 2>/dev/null
        return $?
    elif command -v kafka-topics &> /dev/null; then
        kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null
        return $?
    else
        # Assume Kafka is running if we can't check
        return 0
    fi
}

# Function to start Docker services
start_docker() {
    echo -e "${YELLOW}Starting Docker services...${NC}"
    cd "$PROJECT_ROOT"
    
    docker-compose -f docker-compose-simulation.yml up -d zookeeper kafka kafka-init postgres
    
    echo -e "${YELLOW}Waiting for Kafka to be ready...${NC}"
    for i in {1..30}; do
        if check_kafka; then
            echo -e "${GREEN}Kafka is ready!${NC}"
            break
        fi
        echo "  Waiting for Kafka... ($i/30)"
        sleep 2
    done
    
    if ! check_kafka; then
        echo -e "${RED}Kafka failed to start${NC}"
        # exit 1
    fi
}

# Function to run simulation via Python script
run_python_simulation() {
    local scenario=$1
    
    echo -e "${BLUE}Starting $scenario scenario...${NC}"
    
    cd "$PROJECT_ROOT"
    
    # Create a temporary Python script to run the simulation
    python3 << EOF
import sys
import json
import time
import random
import uuid
from datetime import datetime

# Configuration
SCENARIO = "$scenario"
DURATION = $DURATION
NUM_INSTANCES = $NUM_INSTANCES

print(f"🚀 Running {SCENARIO} scenario with {NUM_INSTANCES} instances for {DURATION}s")

try:
    from kafka import KafkaProducer
    
    producer = KafkaProducer(
        bootstrap_servers=['localhost:9092'],
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None
    )
    kafka_available = True
    print("✅ Connected to Kafka")
except ImportError:
    print("⚠️  kafka-python not installed. Running in simulation mode.")
    kafka_available = False
except Exception as e:
    print(f"⚠️  Could not connect to Kafka: {e}")
    kafka_available = False

# Generate batches
batches = []
for i in range(NUM_INSTANCES):
    batch_id = f"BATCH_{SCENARIO.upper()}_{i+1}_{uuid.uuid4().hex[:8]}"
    farmer_id = f"FARMER_{i+1:03d}"
    
    if SCENARIO == 'normal':
        temp_min, temp_max = 2.0, 8.0
    elif SCENARIO == 'temperature_breach':
        temp_min = -2.0 if i == 0 else 2.0
        temp_max = 12.0 if i == 0 else 8.0
    else:  # route_disruption
        temp_min, temp_max = 2.0, 8.0
    
    batches.append({
        'batch_id': batch_id,
        'farmer_id': farmer_id,
        'instance_id': i + 1,
        'temp_min': temp_min,
        'temp_max': temp_max,
        'initial_quality': random.uniform(95.0, 99.0)
    })
    print(f"📦 Created batch: {batch_id}")

# Start simulations
print("\\n🎬 Starting simulations...")
for batch in batches:
    control_event = {
        'action': 'START',
        'simulationId': f"SIM_{batch['batch_id']}",
        'batchId': batch['batch_id'],
        'farmerId': batch['farmer_id'],
        'instanceId': f"python-{batch['instance_id']}",
        'timestamp': int(datetime.now().timestamp() * 1000)
    }
    
    if kafka_available:
        producer.send('simulation-control', key=batch['batch_id'], value=control_event)
    print(f"  Started: {batch['batch_id']}")
    time.sleep(1)

if kafka_available:
    producer.flush()

# Simulate logistics updates
print(f"\\n🚚 Running simulation for {DURATION} seconds...")
update_interval = 5
total_updates = DURATION // update_interval
alerts_count = 0

for update_num in range(total_updates):
    progress = ((update_num + 1) / total_updates) * 100
    
    for batch in batches:
        lat = 40.7128 + (progress / 100) * 0.5
        lon = -74.0060 + (progress / 100) * 0.3
        
        # Temperature based on scenario
        if SCENARIO == 'temperature_breach' and batch['instance_id'] == 1 and 30 <= progress <= 50:
            temperature = random.uniform(10.0, 15.0)  # Breach!
        else:
            temperature = random.uniform(batch['temp_min'], batch['temp_max'])
        
        # Status based on progress
        if progress < 10:
            status = 'DEPARTING'
            location = 'Farm Distribution Center'
        elif progress < 30:
            status = 'IN_TRANSIT'
            location = 'Highway 95'
        elif progress < 70:
            status = 'IN_TRANSIT'
            location = 'Interstate 78'
        elif progress < 90:
            status = 'APPROACHING'
            location = 'Local Distribution'
        else:
            status = 'ARRIVING'
            location = 'Consumer Warehouse'
        
        # Route disruption
        if SCENARIO == 'route_disruption' and batch['instance_id'] == 2 and 40 <= progress <= 60:
            status = 'DELAYED'
            location = 'Traffic Delay - I-78'
        
        map_event = {
            'batch_id': batch['batch_id'],
            'timestamp': int(datetime.now().timestamp() * 1000),
            'latitude': round(lat, 6),
            'longitude': round(lon, 6),
            'location_name': location,
            'temperature': round(temperature, 2),
            'humidity': round(random.uniform(65.0, 85.0), 2),
            'status': status,
            'progress_percent': round(progress, 1)
        }
        
        if kafka_available:
            producer.send('map-simulation', key=batch['batch_id'], value=map_event)
        
        # Check for breaches
        is_breach = temperature < batch['temp_min'] or temperature > batch['temp_max']
        if is_breach:
            alerts_count += 1
            alert_event = {
                'batch_id': batch['batch_id'],
                'alert_type': 'TEMPERATURE_BREACH',
                'temperature': round(temperature, 2),
                'timestamp': int(datetime.now().timestamp() * 1000)
            }
            if kafka_available:
                producer.send('quality-alerts', key=batch['batch_id'], value=alert_event)
            print(f"  🚨 Alert: Temperature breach for {batch['batch_id']}: {temperature:.1f}°C")
    
    if kafka_available:
        producer.flush()
    
    if update_num % 6 == 0:
        elapsed = (update_num + 1) * update_interval
        print(f"  📍 Progress: {progress:.1f}% (elapsed: {elapsed}s)")
    
    time.sleep(update_interval)

# Stop simulations
print("\\n🛑 Stopping simulations...")
for batch in batches:
    control_event = {
        'action': 'STOP',
        'simulationId': f"SIM_{batch['batch_id']}",
        'batchId': batch['batch_id'],
        'instanceId': f"python-{batch['instance_id']}",
        'timestamp': int(datetime.now().timestamp() * 1000),
        'completed': True
    }
    
    if kafka_available:
        producer.send('simulation-control', key=batch['batch_id'], value=control_event)
    print(f"  Stopped: {batch['batch_id']}")

if kafka_available:
    producer.flush()
    producer.close()

# Compute results
print("\\n📊 Final Results:")
print("=" * 50)
for batch in batches:
    if SCENARIO == 'temperature_breach' and batch['instance_id'] == 1:
        degradation = random.uniform(10.0, 20.0)
    elif SCENARIO == 'route_disruption' and batch['instance_id'] == 2:
        degradation = random.uniform(5.0, 10.0)
    else:
        degradation = random.uniform(1.0, 3.0)
    
    final_quality = max(0, batch['initial_quality'] - degradation)
    grade = 'PRIME' if final_quality >= 90 else 'STANDARD' if final_quality >= 70 else 'REJECT'
    
    print(f"  {batch['batch_id']}")
    print(f"    Initial Quality: {batch['initial_quality']:.1f}%")
    print(f"    Final Quality:   {final_quality:.1f}%")
    print(f"    Grade:           {grade}")
    print()

print(f"Total Alerts: {alerts_count}")
print("=" * 50)
print("✅ Simulation completed!")
EOF
}

# Function to run simulation via Airflow
run_airflow_simulation() {
    local scenario=$1
    
    echo -e "${BLUE}Triggering Airflow DAG for $scenario...${NC}"
    
    # Check if Airflow is running
    if ! curl -s http://localhost:8080/health >/dev/null 2>&1; then
        echo -e "${YELLOW}Starting Airflow...${NC}"
        cd "$PROJECT_ROOT"
        docker-compose -f docker-compose-simulation.yml up -d airflow-webserver airflow-scheduler
        
        echo "Waiting for Airflow to be ready..."
        for i in {1..60}; do
            if curl -s http://localhost:8080/health | grep -q "healthy"; then
                echo -e "${GREEN}Airflow is ready!${NC}"
                break
            fi
            echo "  Waiting... ($i/60)"
            sleep 5
        done
    fi
    
    # Trigger DAG via API
    curl -X POST \
        -H "Content-Type: application/json" \
        -d "{\"conf\": {\"scenario\": \"$scenario\", \"duration_seconds\": $DURATION, \"num_instances\": $NUM_INSTANCES}}" \
        "http://localhost:8080/api/v1/dags/vericrop_multi_instance_${scenario}/dagRuns" \
        -u admin:admin 2>/dev/null || echo "Note: API authentication may be required"
    
    echo -e "${GREEN}DAG triggered. View progress at http://localhost:8080${NC}"
}

# Main execution
main() {
    # Start Docker if needed
    if $USE_DOCKER; then
        start_docker
    fi
    
    # Run scenarios
    if [ "$SCENARIO" = "all" ]; then
        for s in normal temperature_breach route_disruption; do
            if $USE_AIRFLOW; then
                run_airflow_simulation "$s"
            else
                run_python_simulation "$s"
            fi
            echo ""
            echo "Waiting 10 seconds before next scenario..."
            sleep 10
        done
    else
        if $USE_AIRFLOW; then
            run_airflow_simulation "$SCENARIO"
        else
            run_python_simulation "$SCENARIO"
        fi
    fi
    
    echo ""
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}  Simulation Complete!                ${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo ""
    echo "View Kafka UI: http://localhost:8090"
    echo "View Airflow:  http://localhost:8080"
}

main "$@"
