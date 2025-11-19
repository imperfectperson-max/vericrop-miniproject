#!/bin/bash

# Test script for CustomerController API
# Prerequisites: Java service must be running on http://localhost:8080

API_URL="${JAVA_SERVICE_URL:-http://localhost:8080}"
echo "Testing CustomerController API at $API_URL"
echo "================================================"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Health check
echo -e "\n${YELLOW}Test 1: Health Check${NC}"
curl -s "$API_URL/api/health" | jq '.' || echo -e "${RED}❌ Failed${NC}"

# Test 2: Create a customer
echo -e "\n${YELLOW}Test 2: Create Customer (FARMER)${NC}"
RESPONSE=$(curl -s -X POST "$API_URL/api/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Farmer",
    "email": "john.farmer@test.com",
    "phone": "+1234567890",
    "customerType": "FARMER",
    "address": "123 Farm Road",
    "active": true
  }')
echo "$RESPONSE" | jq '.'
CUSTOMER_ID=$(echo "$RESPONSE" | jq -r '.id')
echo -e "${GREEN}Created customer with ID: $CUSTOMER_ID${NC}"

# Test 3: Get customer by ID
echo -e "\n${YELLOW}Test 3: Get Customer by ID${NC}"
curl -s "$API_URL/api/customers/$CUSTOMER_ID" | jq '.'

# Test 4: Create another customer
echo -e "\n${YELLOW}Test 4: Create Customer (RETAILER)${NC}"
curl -s -X POST "$API_URL/api/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Retail Store Inc",
    "email": "contact@retailstore.com",
    "phone": "+0987654321",
    "customerType": "RETAILER",
    "address": "456 Market Street",
    "active": true
  }' | jq '.'

# Test 5: List all customers
echo -e "\n${YELLOW}Test 5: List All Customers${NC}"
curl -s "$API_URL/api/customers" | jq '.'

# Test 6: Filter by customer type
echo -e "\n${YELLOW}Test 6: Filter Customers by Type (FARMER)${NC}"
curl -s "$API_URL/api/customers?type=FARMER" | jq '.'

# Test 7: Update customer
echo -e "\n${YELLOW}Test 7: Update Customer${NC}"
curl -s -X PUT "$API_URL/api/customers/$CUSTOMER_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Farmer Updated",
    "email": "john.farmer@test.com",
    "phone": "+1234567890",
    "customerType": "FARMER",
    "address": "123 Farm Road, Suite B",
    "active": true
  }' | jq '.'

# Test 8: Validation error (invalid email)
echo -e "\n${YELLOW}Test 8: Validation Error (Invalid Email)${NC}"
curl -s -X POST "$API_URL/api/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Invalid Customer",
    "email": "invalid-email",
    "customerType": "CONSUMER"
  }' | jq '.'

# Test 9: Duplicate email error
echo -e "\n${YELLOW}Test 9: Duplicate Email Error${NC}"
curl -s -X POST "$API_URL/api/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Duplicate Customer",
    "email": "john.farmer@test.com",
    "customerType": "CONSUMER"
  }' | jq '.'

# Test 10: Delete customer
echo -e "\n${YELLOW}Test 10: Delete Customer${NC}"
curl -s -X DELETE "$API_URL/api/customers/$CUSTOMER_ID"
echo -e "${GREEN}Customer deleted (should return 204 No Content)${NC}"

# Test 11: Get deleted customer (404)
echo -e "\n${YELLOW}Test 11: Get Deleted Customer (Should Return 404)${NC}"
curl -s "$API_URL/api/customers/$CUSTOMER_ID" | jq '.'

echo -e "\n${GREEN}================================================${NC}"
echo -e "${GREEN}✅ All tests completed!${NC}"
echo -e "${GREEN}================================================${NC}"
