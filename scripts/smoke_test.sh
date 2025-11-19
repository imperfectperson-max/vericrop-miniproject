#!/bin/bash
# VeriCrop End-to-End Smoke Test Script
# Tests the complete stack: Kafka, VeriCrop API, ML Service, Airflow

set -e

VERICROP_API_URL="${VERICROP_API_URL:-http://localhost:8080}"
ML_SERVICE_URL="${ML_SERVICE_URL:-http://localhost:8000}"
KAFKA_UI_URL="${KAFKA_UI_URL:-http://localhost:8081}"
AIRFLOW_UI_URL="${AIRFLOW_UI_URL:-http://localhost:8082}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "================================================"
echo "VeriCrop End-to-End Smoke Test"
echo "================================================"
echo ""

# Function to check if a service is healthy
check_service() {
    local name=$1
    local url=$2
    local max_retries=${3:-30}
    local retry_interval=${4:-2}
    
    echo -n "Checking ${name}... "
    
    for i in $(seq 1 $max_retries); do
        if curl -s -f "${url}" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ OK${NC}"
            return 0
        fi
        sleep $retry_interval
    done
    
    echo -e "${RED}✗ FAILED${NC}"
    return 1
}

# Function to run a test
run_test() {
    local test_name=$1
    local test_command=$2
    
    echo -n "${test_name}... "
    if eval "${test_command}" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ PASS${NC}"
        return 0
    else
        echo -e "${RED}✗ FAIL${NC}"
        return 1
    fi
}

echo "Step 1: Checking Service Health"
echo "--------------------------------"

# Check ML Service
check_service "ML Service" "${ML_SERVICE_URL}/health" || exit 1

# Check VeriCrop API
check_service "VeriCrop API" "${VERICROP_API_URL}/api/health" || exit 1

# Check Kafka UI
check_service "Kafka UI" "${KAFKA_UI_URL}" || exit 1

# Check Airflow UI
check_service "Airflow UI" "${AIRFLOW_UI_URL}/health" || exit 1

echo ""
echo "Step 2: Testing VeriCrop API Endpoints"
echo "---------------------------------------"

# Test health endpoint
run_test "Health endpoint" "curl -s -f ${VERICROP_API_URL}/api/health"

# Test evaluation endpoint with JSON
BATCH_ID="SMOKE_TEST_$(date +%s)"
EVAL_REQUEST="{\"batch_id\":\"${BATCH_ID}\",\"product_type\":\"apple\",\"farmer_id\":\"smoke_test_farmer\"}"
run_test "Evaluation endpoint (JSON)" "curl -s -f -X POST -H 'Content-Type: application/json' -d '${EVAL_REQUEST}' ${VERICROP_API_URL}/api/evaluate"

# Save the ledger ID for later verification
EVAL_RESPONSE=$(curl -s -X POST -H 'Content-Type: application/json' -d "${EVAL_REQUEST}" ${VERICROP_API_URL}/api/evaluate)
LEDGER_ID=$(echo $EVAL_RESPONSE | grep -o '"ledger_id":"[^"]*"' | cut -d'"' -f4 || echo "")

if [ -n "$LEDGER_ID" ]; then
    # Test shipment retrieval by ledger ID
    run_test "Get shipment by ledger ID" "curl -s -f ${VERICROP_API_URL}/api/shipments/${LEDGER_ID}"
    
    # Test shipment retrieval by batch ID
    run_test "Get shipments by batch ID" "curl -s -f ${VERICROP_API_URL}/api/shipments?batch_id=${BATCH_ID}"
else
    echo -e "${YELLOW}⚠ Skipping shipment tests (no ledger ID)${NC}"
fi

echo ""
echo "Step 3: Testing Kafka Integration"
echo "----------------------------------"

# Check if kafka-console-consumer is available
if command -v kafka-console-consumer &> /dev/null || command -v docker &> /dev/null; then
    # Try to list Kafka topics using docker
    if docker ps | grep -q vericrop-kafka; then
        run_test "List Kafka topics" "docker exec vericrop-kafka kafka-topics --bootstrap-server localhost:9092 --list"
        
        # Check for required topics
        TOPICS=$(docker exec vericrop-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null)
        if echo "$TOPICS" | grep -q "evaluation-requests"; then
            echo -e "Topic 'evaluation-requests': ${GREEN}✓ EXISTS${NC}"
        else
            echo -e "Topic 'evaluation-requests': ${YELLOW}⚠ NOT FOUND${NC}"
        fi
        
        if echo "$TOPICS" | grep -q "evaluation-results"; then
            echo -e "Topic 'evaluation-results': ${GREEN}✓ EXISTS${NC}"
        else
            echo -e "Topic 'evaluation-results': ${YELLOW}⚠ NOT FOUND${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ Kafka container not running, skipping topic checks${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Kafka CLI tools not available, skipping topic checks${NC}"
fi

echo ""
echo "Step 4: Testing ML Service"
echo "--------------------------"

# Test ML service health
run_test "ML Service health" "curl -s -f ${ML_SERVICE_URL}/health"

# Test ML service dashboard endpoints (if available)
if curl -s -f "${ML_SERVICE_URL}/dashboard/farm" > /dev/null 2>&1; then
    run_test "ML Service farm dashboard" "curl -s -f ${ML_SERVICE_URL}/dashboard/farm"
else
    echo -e "ML Service farm dashboard: ${YELLOW}⚠ NOT AVAILABLE${NC}"
fi

echo ""
echo "Step 5: Summary"
echo "---------------"

# Display service URLs
echo "Service URLs:"
echo "  - VeriCrop API:  ${VERICROP_API_URL}"
echo "  - ML Service:    ${ML_SERVICE_URL}"
echo "  - Kafka UI:      ${KAFKA_UI_URL}"
echo "  - Airflow UI:    ${AIRFLOW_UI_URL}"
echo ""
echo "Airflow Login:"
echo "  - Username: admin"
echo "  - Password: admin"
echo ""

echo "================================================"
echo -e "${GREEN}Smoke Test Completed Successfully!${NC}"
echo "================================================"
echo ""
echo "Next steps:"
echo "  1. Access Airflow UI at ${AIRFLOW_UI_URL}"
echo "  2. Enable and trigger the 'vericrop_evaluation_pipeline' DAG"
echo "  3. Monitor Kafka topics at ${KAFKA_UI_URL}"
echo "  4. Send test requests to ${VERICROP_API_URL}/api/evaluate"
echo ""
