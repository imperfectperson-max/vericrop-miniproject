# Security Testing Guide

This guide provides comprehensive instructions for testing VeriCrop's security features and identifying potential vulnerabilities.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Authentication Security Tests](#authentication-security-tests)
- [Authorization Tests](#authorization-tests)
- [Input Validation Tests](#input-validation-tests)
- [Database Security Tests](#database-security-tests)
- [API Security Tests](#api-security-tests)
- [Blockchain Integrity Tests](#blockchain-integrity-tests)
- [Network Security Tests](#network-security-tests)
- [Dependency Vulnerability Scanning](#dependency-vulnerability-scanning)
- [Automated Security Testing](#automated-security-testing)

## Prerequisites

Ensure the application is running:
```bash
docker-compose up -d
./gradlew :vericrop-gui:run
```

Tools needed:
- curl (HTTP testing)
- jq (JSON processing)
- psql (PostgreSQL client)
- Optional: Burp Suite, OWASP ZAP (advanced testing)

## Authentication Security Tests

### Test 1: BCrypt Password Hashing

**Objective**: Verify passwords are hashed with BCrypt, not stored in plaintext.

```bash
# Connect to database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

# Check password hashing
SELECT username, password_hash, LEFT(password_hash, 4) as hash_prefix 
FROM users LIMIT 5;

# Expected: password_hash should start with $2a$ or $2b$ (BCrypt)
# Example: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

**Pass Criteria**: All password_hash values start with `$2a$` or `$2b$`

### Test 2: Account Lockout After Failed Logins

**Objective**: Verify account lockout after 5 failed login attempts.

```bash
# Test script: try 6 failed logins
for i in {1..6}; do
  echo "Attempt $i:"
  curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"testuser","password":"wrongpassword"}' | jq .
  sleep 1
done

# Verify lockout in database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, failed_login_attempts, locked_until FROM users WHERE username='testuser';"
```

**Pass Criteria**: 
- After 5 failures, `failed_login_attempts` = 5
- `locked_until` is set to 30 minutes from now
- 6th attempt returns error about locked account

### Test 3: JWT Token Expiration

**Objective**: Verify JWT tokens expire after configured duration.

```bash
# 1. Login and get token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"farmer","password":"farmer123"}' | jq -r .token)

echo "Token: $TOKEN"

# 2. Use token immediately (should work)
curl -s http://localhost:8080/api/batches \
  -H "Authorization: Bearer $TOKEN" | jq .

# 3. Wait for token expiration (default: 24 hours, adjust JWT_EXPIRATION for testing)
# For testing, set JWT_EXPIRATION=60000 (1 minute) in .env and restart

# 4. Try after expiration (should fail)
sleep 65
curl -s http://localhost:8080/api/batches \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Pass Criteria**: Expired token returns 401 Unauthorized

### Test 4: Password Strength Enforcement

**Objective**: Test password validation rules.

```bash
# Test weak password
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username":"testuser",
    "password":"123",
    "email":"test@example.com"
  }' | jq .

# Expected: Error about password strength requirements
```

**Pass Criteria**: Weak passwords are rejected

### Test 5: Demo Mode Security

**Objective**: Verify demo mode only accepts predefined accounts.

```bash
# Start app without demo mode
./gradlew :vericrop-gui:run

# Try arbitrary credentials (should fail)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"hacker","password":"password123"}' | jq .

# Enable demo mode
./gradlew :vericrop-gui:run -Dvericrop.demoMode=true

# Try same credentials (should still fail)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"hacker","password":"password123"}' | jq .

# Try valid demo account (should succeed)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"farmer","password":"farmer123"}' | jq .
```

**Pass Criteria**: Demo mode only accepts predefined demo accounts (farmer, admin, supplier, consumer)

## Authorization Tests

### Test 6: Role-Based Access Control

**Objective**: Verify users can only access resources for their role.

```bash
# Login as farmer
FARMER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"farmer","password":"farmer123"}' | jq -r .token)

# Login as admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# Test: Farmer tries to access admin endpoint (should fail)
curl -s http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $FARMER_TOKEN" | jq .

# Test: Admin accesses admin endpoint (should succeed)
curl -s http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

**Pass Criteria**: Users cannot access resources outside their role permissions

## Input Validation Tests

### Test 7: SQL Injection Prevention

**Objective**: Verify all inputs are properly sanitized against SQL injection.

```bash
# Test SQL injection in login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin'\''-- ","password":"anything"}' | jq .

# Test SQL injection in batch creation
curl -s -X POST http://localhost:8080/api/batches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{
    "batch_name":"Test'\''; DROP TABLE batches; --",
    "product_type":"Apple"
  }' | jq .

# Verify tables still exist
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "\dt"
```

**Pass Criteria**: 
- SQL injection attempts are handled safely
- All tables remain intact
- No SQL errors in logs

### Test 8: XSS (Cross-Site Scripting) Prevention

**Objective**: Test input sanitization for XSS attacks.

```bash
# Test XSS in batch name
curl -s -X POST http://localhost:8080/api/batches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{
    "batch_name":"<script>alert(\"XSS\")</script>",
    "product_type":"Apple"
  }' | jq .

# Retrieve batch and check if script is escaped
curl -s http://localhost:8080/api/batches \
  -H "Authorization: Bearer $FARMER_TOKEN" | jq .
```

**Pass Criteria**: Script tags are escaped or sanitized in output

### Test 9: Command Injection Prevention

**Objective**: Ensure no command injection vulnerabilities.

```bash
# Test command injection in file upload
curl -s -X POST http://localhost:8080/api/upload \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -F "file=@/etc/passwd;filename=test.jpg" | jq .

# Test command injection in batch name
curl -s -X POST http://localhost:8080/api/batches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FARMER_TOKEN" \
  -d '{
    "batch_name":"test; rm -rf /",
    "product_type":"Apple"
  }' | jq .
```

**Pass Criteria**: Command injection attempts are blocked or sanitized

## Database Security Tests

### Test 10: Connection Security

**Objective**: Verify database connections are secured.

```bash
# Check if database requires authentication
docker exec -it vericrop-postgres psql -U wrong_user -d vericrop
# Should fail with authentication error

# Check SSL support (production)
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SHOW ssl;"
```

**Pass Criteria**: 
- Database requires valid credentials
- SSL is enabled (production)

### Test 11: Prepared Statements

**Objective**: Verify all queries use prepared statements.

```bash
# Code review check
grep -r "Statement stmt" src/vericrop-gui/main/java --include="*.java"
# Should show PreparedStatement, not Statement

# Dynamic SQL check
grep -r "executeQuery(\"SELECT" src/vericrop-gui/main/java --include="*.java"
# Should return no results (all queries should use parameterized form)
```

**Pass Criteria**: All database operations use PreparedStatement

## API Security Tests

### Test 12: Rate Limiting

**Objective**: Test API rate limiting to prevent abuse.

```bash
# Rapid fire requests to predict endpoint
for i in {1..150}; do
  curl -s -X POST http://localhost:8000/predict \
    -F "file=@examples/sample.jpg" > /dev/null &
done
wait

# Check if rate limiting kicked in
curl -s -X POST http://localhost:8000/predict \
  -F "file=@examples/sample.jpg"
```

**Pass Criteria**: After threshold, requests return 429 Too Many Requests

### Test 13: CORS Configuration

**Objective**: Verify CORS is properly configured.

```bash
# Test CORS with unauthorized origin
curl -s -X OPTIONS http://localhost:8080/api/batches \
  -H "Origin: http://evil.com" \
  -H "Access-Control-Request-Method: GET" \
  -v 2>&1 | grep "Access-Control"
```

**Pass Criteria**: CORS headers only allow trusted origins

### Test 14: API Authentication

**Objective**: Ensure all sensitive endpoints require authentication.

```bash
# Test endpoints without token (should fail)
curl -s http://localhost:8080/api/batches | jq .
curl -s http://localhost:8080/api/admin/users | jq .
curl -s http://localhost:8080/producer/blockchain | jq .

# Public endpoints (should work)
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8000/health | jq .
```

**Pass Criteria**: Sensitive endpoints return 401 without token

## Blockchain Integrity Tests

### Test 15: Blockchain Validation

**Objective**: Verify blockchain integrity validation.

```bash
# Check blockchain is valid
curl -s http://localhost:8080/producer/blockchain/validate | jq .

# Should return: {"valid": true}
```

**Pass Criteria**: Blockchain validation returns true

### Test 16: Tamper Detection

**Objective**: Test tamper detection by modifying blockchain file.

```bash
# Backup blockchain
cp blockchain.json blockchain.json.bak

# Modify blockchain file (simulate tampering)
jq '.[0].data = "tampered"' blockchain.json > blockchain_tampered.json
mv blockchain_tampered.json blockchain.json

# Validate blockchain
curl -s http://localhost:8080/producer/blockchain/validate | jq .

# Should return: {"valid": false}

# Restore backup
mv blockchain.json.bak blockchain.json
```

**Pass Criteria**: Tampered blockchain is detected as invalid

## Network Security Tests

### Test 17: Port Exposure

**Objective**: Verify only necessary ports are exposed.

```bash
# Check listening ports
netstat -tulpn | grep LISTEN

# Docker port mappings
docker-compose ps
```

**Pass Criteria**: Only essential ports (5432, 9092, 8000, 8080) are exposed

### Test 18: Docker Network Isolation

**Objective**: Verify services communicate via isolated network.

```bash
# Check network configuration
docker network inspect vericrop-miniproject_vericrop-network

# Verify services are in the same network
docker inspect vericrop-postgres | grep NetworkMode
docker inspect vericrop-kafka | grep NetworkMode
```

**Pass Criteria**: All services use the same isolated bridge network

## Dependency Vulnerability Scanning

### Test 19: Java Dependencies

```bash
# Using Gradle dependency-check plugin (add to build.gradle if not present)
./gradlew dependencyCheckAnalyze

# Review report
open build/reports/dependency-check-report.html

# Check for outdated dependencies
./gradlew dependencyUpdates
```

**Pass Criteria**: No critical or high severity vulnerabilities

### Test 20: Python Dependencies

```bash
# Navigate to ML service
cd docker/ml-service

# Install safety
pip install safety

# Check for vulnerabilities
safety check -r requirements.txt

# Alternative: pip-audit
pip install pip-audit
pip-audit -r requirements.txt
```

**Pass Criteria**: No known vulnerabilities in dependencies

### Test 21: Docker Image Scanning

```bash
# Scan ML service image
docker scan vericrop-ml-service:latest

# Scan GUI image (if built)
docker scan vericrop-gui:latest
```

**Pass Criteria**: No critical vulnerabilities in base images

## Automated Security Testing

### Test 22: CodeQL Analysis

**Objective**: Run GitHub CodeQL security analysis.

```bash
# Install CodeQL CLI (if not in CI)
# See: https://codeql.github.com/docs/codeql-cli/

# Create database
codeql database create vericrop-db \
  --language=java \
  --source-root=.

# Run security queries
codeql database analyze vericrop-db \
  --format=sarif-latest \
  --output=results.sarif \
  codeql/java-queries:codeql-suites/java-security-extended.qls

# View results
cat results.sarif | jq '.runs[0].results'
```

**Pass Criteria**: No critical security issues reported

### Test 23: OWASP ZAP Scanning

**Objective**: Run OWASP ZAP automated security scan.

```bash
# Pull OWASP ZAP Docker image
docker pull owasp/zap2docker-stable

# Run baseline scan
docker run -v $(pwd):/zap/wrk/:rw \
  -t owasp/zap2docker-stable \
  zap-baseline.py \
  -t http://host.docker.internal:8080 \
  -r zap-report.html

# View report
open zap-report.html
```

**Pass Criteria**: No high-risk vulnerabilities

## Security Test Automation

### Continuous Integration

Add security tests to CI pipeline (`.github/workflows/security.yml`):

```yaml
name: Security Tests

on: [push, pull_request]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Run dependency check
        run: ./gradlew dependencyCheckAnalyze
        
      - name: Upload vulnerability report
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: build/reports/dependency-check-report.html
          
      - name: Python dependency scan
        run: |
          cd docker/ml-service
          pip install safety
          safety check -r requirements.txt
```

## Test Checklist

Use this checklist to track security testing:

### Authentication & Authorization
- [ ] BCrypt password hashing verified
- [ ] Account lockout after failed logins
- [ ] JWT token expiration works
- [ ] Password strength enforced
- [ ] Demo mode security verified
- [ ] Role-based access control works
- [ ] Unauthorized access blocked

### Input Validation
- [ ] SQL injection prevented
- [ ] XSS attacks prevented
- [ ] Command injection prevented
- [ ] Path traversal prevented
- [ ] File upload validation works

### Data Protection
- [ ] Prepared statements used
- [ ] Database requires authentication
- [ ] SSL/TLS enabled (production)
- [ ] Blockchain integrity maintained
- [ ] Tamper detection works

### API Security
- [ ] Rate limiting functional
- [ ] CORS properly configured
- [ ] Authentication required for sensitive endpoints
- [ ] Error messages don't leak information

### Dependencies & Infrastructure
- [ ] Java dependencies scanned
- [ ] Python dependencies scanned
- [ ] Docker images scanned
- [ ] No critical vulnerabilities found
- [ ] Ports properly exposed
- [ ] Network isolation verified

### Automated Testing
- [ ] CodeQL analysis passing
- [ ] OWASP ZAP scan clean
- [ ] CI/CD security tests passing

## Reporting Security Issues

If security tests reveal vulnerabilities:

1. **Document the issue**: Include reproduction steps, impact assessment
2. **Create a private report**: Don't disclose publicly until fixed
3. **Follow responsible disclosure**: See SECURITY.md for contact info
4. **Verify the fix**: Re-test after patch is applied

## Resources

- [OWASP Testing Guide](https://owasp.org/www-project-web-security-testing-guide/)
- [PortSwigger Web Security Academy](https://portswigger.net/web-security)
- [NIST Security Testing](https://csrc.nist.gov/projects/security-content-automation-protocol)
- [VeriCrop Security Policy](../SECURITY.md)

---

**Last Updated**: 2026-01-08

Regular security testing is essential for maintaining a secure application. Run these tests:
- Before each release
- After major changes
- Quarterly for production systems
- After dependency updates
