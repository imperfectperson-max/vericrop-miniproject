#!/bin/bash
#
# Test script for VeriCrop REST APIs
# Demonstrates messaging, quality decay, and delivery simulation features
#
# Usage:
#   1. Start VeriCrop API: ./gradlew :vericrop-gui:bootRun
#   2. Run this script: bash examples/test-apis.sh
#

set -e

API_URL="${VERICROP_API_URL:-http://localhost:8080}"
echo "Testing VeriCrop APIs at $API_URL"
echo "========================================"

# Test 1: Health Check
echo -e "\n[Test 1] Health Check"
curl -s "$API_URL/api/health" | python3 -m json.tool || echo "Health check endpoint not responding"

# Test 2: Send a Message
echo -e "\n\n[Test 2] Send Message: Farmer to Supplier"
curl -s -X POST "$API_URL/api/v1/messaging/send" \
  -H "Content-Type: application/json" \
  -d '{
    "senderRole": "farmer",
    "senderId": "farmer_001",
    "recipientRole": "supplier",
    "recipientId": "supplier_001",
    "subject": "Batch ABC123 Ready",
    "content": "Fresh apples ready for pickup. Quality: Prime. Quantity: 500kg",
    "batchId": "ABC123"
  }' | python3 -m json.tool

# Test 3: Get Inbox
echo -e "\n\n[Test 3] Get Supplier Inbox"
curl -s "$API_URL/api/v1/messaging/inbox?recipientRole=supplier&recipientId=supplier_001" \
  | python3 -m json.tool

# Test 4: Quality Prediction
echo -e "\n\n[Test 4] Predict Quality (Current: 95%, Temp: 12°C, Humidity: 85%, Hours: 24)"
curl -s -X POST "$API_URL/api/v1/quality/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "current_quality": 95.0,
    "temperature": 12.0,
    "humidity": 85.0,
    "hours_in_future": 24.0
  }' | python3 -m json.tool

# Test 5: Get Ideal Ranges
echo -e "\n\n[Test 5] Get Ideal Temperature/Humidity Ranges"
curl -s "$API_URL/api/v1/quality/ideal-ranges" | python3 -m json.tool

# Test 6: Quality Simulation
echo -e "\n\n[Test 6] Simulate Quality Over Route (3 readings)"
TIMESTAMP=$(date +%s)000
HOUR_MS=3600000
curl -s -X POST "$API_URL/api/v1/quality/simulate" \
  -H "Content-Type: application/json" \
  -d '{
    "initial_quality": 100.0,
    "readings": [
      {"timestamp": '"$TIMESTAMP"', "temperature": 5.0, "humidity": 75.0},
      {"timestamp": '"$((TIMESTAMP + HOUR_MS))"', "temperature": 8.0, "humidity": 80.0},
      {"timestamp": '"$((TIMESTAMP + 2 * HOUR_MS))"', "temperature": 6.0, "humidity": 77.0}
    ]
  }' | python3 -m json.tool

# Test 7: Generate Route
echo -e "\n\n[Test 7] Generate Delivery Route (New York to Los Angeles)"
curl -s -X POST "$API_URL/api/v1/delivery/generate-route" \
  -H "Content-Type: application/json" \
  -d '{
    "origin": {"latitude": 40.7128, "longitude": -74.0060, "name": "New York Farm"},
    "destination": {"latitude": 34.0522, "longitude": -118.2437, "name": "LA Market"},
    "num_waypoints": 5,
    "avg_speed_kmh": 80
  }' | python3 -m json.tool

# Test 8: Decay Rate Calculation
echo -e "\n\n[Test 8] Calculate Decay Rate (Temp: 15°C, Humidity: 90%)"
curl -s "$API_URL/api/v1/quality/decay-rate?temperature=15&humidity=90" \
  | python3 -m json.tool

echo -e "\n\n========================================"
echo "API Tests Complete!"
echo "Note: Delivery simulation tests require starting and monitoring a simulation"
echo "      Use the start-simulation endpoint with a generated route to test."
