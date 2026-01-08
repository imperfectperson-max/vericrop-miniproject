# Security Policy

## Overview

VeriCrop is designed with security as a core principle. This document outlines the security measures implemented in the system, best practices for deployment, and how to report security vulnerabilities.

## Table of Contents

- [Security Features](#security-features)
- [Authentication & Authorization](#authentication--authorization)
- [Data Protection](#data-protection)
- [Network Security](#network-security)
- [Dependency Management](#dependency-management)
- [Secure Configuration](#secure-configuration)
- [Security Best Practices](#security-best-practices)
- [Reporting Security Vulnerabilities](#reporting-security-vulnerabilities)
- [Security Testing](#security-testing)

## Security Features

### 1. Authentication & Authorization

#### BCrypt Password Hashing
- All user passwords are hashed using **BCrypt** with automatic salt generation
- BCrypt work factor: 10 (default, providing strong security)
- Passwords are **never stored in plaintext**
- Implementation: `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`

```java
// Example from AuthenticationService.java
private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
// Verification
if (passwordEncoder.matches(password, passwordHash)) {
    // Authentication successful
}
```

#### JWT Token-Based Authentication
- JSON Web Tokens (JWT) for stateless authentication
- Token expiration: 24 hours (configurable via `JWT_EXPIRATION`)
- Signed with HMAC SHA-256 algorithm
- Secret key should be at least 32 characters in production

**Configuration:**
```bash
# .env
JWT_SECRET=your_secure_random_secret_here_min_32_chars
JWT_EXPIRATION=86400000  # 24 hours in milliseconds
```

#### Account Security Features
- **Brute Force Protection**: Account lockout after 5 failed login attempts
- **Lockout Duration**: 30 minutes automatic unlock
- **Failed Attempt Tracking**: Database-backed tracking of login failures
- **Active Session Management**: Logout functionality clears all session data

```java
// Implementation details
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_DURATION_MINUTES = 30
```

#### Role-Based Access Control (RBAC)
Supported roles with specific permissions:
- **ADMIN**: Full system access, user management
- **FARMER**: Batch creation, quality assessment, producer operations
- **SUPPLIER**: Logistics tracking, shipment management
- **CONSUMER**: Product verification, QR code scanning

### 2. Data Protection

#### Database Security

**PostgreSQL Configuration:**
- Strong password requirements (see `.env.example`)
- Connection pooling with HikariCP for resource management
- Parameterized queries to prevent SQL injection
- Database migrations with Flyway (versioned, auditable)

```bash
# Required PostgreSQL settings
POSTGRES_USER=vericrop
POSTGRES_PASSWORD=<strong_password_here>  # Change in production!
POSTGRES_DB=vericrop
```

**SQL Injection Prevention:**
All database queries use prepared statements:
```java
// Safe: Parameterized query
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, username);
```

#### Blockchain Integrity
- **Immutable Ledger**: SHA-256 hashing for block integrity
- **Chain Validation**: Automatic validation of blockchain integrity
- **Tamper Detection**: Any modification to historical records is detectable

```java
// Block hashing implementation
Block {
    String hash;           // Current block hash
    String previousHash;   // Previous block hash for chain integrity
    // Hash = SHA256(previousHash + timestamp + data)
}
```

#### Data Encryption

**At Rest:**
- Database encryption available via PostgreSQL configuration
- Filesystem encryption recommended for production deployments
- Sensitive configuration in `.env` files (excluded from version control)

**In Transit:**
- HTTPS/TLS for all API communications in production
- Kafka can be configured with SSL/TLS for message encryption
- Database connections support SSL

### 3. Network Security

#### Docker Network Isolation
- Services communicate via internal Docker network (`vericrop-network`)
- Only necessary ports exposed to host
- Bridge network driver provides isolation

```yaml
# docker-compose.yml
networks:
  vericrop-network:
    driver: bridge
```

#### Port Security
Only essential ports are exposed:
- **5432**: PostgreSQL (database)
- **9092**: Kafka (messaging)
- **8000**: ML Service API
- **8080**: Application API/UI
- **8081**: Kafka UI (development only)

**Production Recommendation**: Use reverse proxy (nginx/Apache) with SSL/TLS termination.

#### API Security

**Rate Limiting:**
Available via Spring Boot Actuator and resilience4j:
```yaml
resilience4j.ratelimiter:
  instances:
    mlService:
      limitForPeriod: 100
      limitRefreshPeriod: 1s
      timeoutDuration: 5s
```

**Circuit Breaker:**
Protects against cascading failures:
```java
// Resilience4j circuit breaker for ML service calls
@CircuitBreaker(name = "mlService", fallbackMethod = "fallbackPrediction")
public PredictionResult predict(String imageUrl) {
    // ML service call
}
```

### 4. Dependency Management

#### Java Dependencies
All dependencies are managed via Gradle with specific versions:

**Key Security Libraries:**
- Spring Security Crypto 6.1.0 (BCrypt)
- Bouncy Castle 1.70 (cryptography)
- JWT (jjwt) 0.11.5
- PostgreSQL JDBC 42.6.0
- Jackson 2.15.2 (JSON processing with security fixes)

**Regular Updates:**
Dependencies should be reviewed quarterly for security updates:
```bash
./gradlew dependencyUpdates
```

#### Python Dependencies (ML Service)
```txt
# requirements.txt
fastapi==0.104.1      # Latest stable with security fixes
uvicorn==0.24.0       # ASGI server
pillow==10.1.0        # Image processing (security patches)
onnxruntime==1.16.3   # ML inference
```

**Vulnerability Scanning:**
```bash
# Python dependencies
pip install safety
safety check -r requirements.txt

# Or using pip-audit
pip install pip-audit
pip-audit -r requirements.txt
```

### 5. Secure Configuration

#### Environment Variables
Sensitive configuration must use environment variables (never hardcoded):

```bash
# .env (NEVER commit this file!)
POSTGRES_PASSWORD=<secure_password>
JWT_SECRET=<random_32+_char_string>
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
ML_SERVICE_URL=http://localhost:8000
```

#### Configuration Files
- `.env` and `.env.production` are in `.gitignore`
- `.env.example` provides template (no secrets)
- Secrets should be managed via external secret management in production (e.g., HashiCorp Vault, AWS Secrets Manager)

#### Demo Mode Security

**CRITICAL: Demo mode should NEVER be used in production**

Demo mode is explicitly opt-in:
```bash
# Enable demo mode (development only)
-Dvericrop.demoMode=true
```

Demo mode limitations:
- Only accepts predefined demo accounts (admin, farmer, supplier, consumer)
- Still requires correct passwords for demo accounts
- Logs warnings when enabled
- Should be disabled in production

```java
// Demo mode check
if (isDemoModeExplicitlyEnabled()) {
    logger.warn("⚠️  DEMO MODE ENABLED - DO NOT use in production!");
}
```

### 6. Security Best Practices

#### Development

1. **Never commit secrets**: Use `.env` files and `.gitignore`
2. **Use prepared statements**: Prevent SQL injection
3. **Validate all inputs**: Client-side and server-side validation
4. **Sanitize outputs**: Prevent XSS and injection attacks
5. **Keep dependencies updated**: Regular security patches
6. **Review code changes**: Security-focused code reviews

#### Deployment

1. **Change default passwords**: All default credentials must be changed
2. **Use HTTPS/TLS**: Enable SSL for all external communications
3. **Enable firewall**: Restrict access to necessary ports only
4. **Regular backups**: Database and blockchain ledger backups
5. **Monitor logs**: Security event monitoring and alerting
6. **Update regularly**: Apply security patches promptly
7. **Least privilege**: Run services with minimal required permissions
8. **Network segmentation**: Isolate services in separate networks

#### Production Checklist

- [ ] Change all default passwords (PostgreSQL, Airflow, application users)
- [ ] Set strong `JWT_SECRET` (min 32 characters, random)
- [ ] Enable HTTPS/TLS for all external communications
- [ ] Configure PostgreSQL SSL connections
- [ ] Enable Kafka SSL/TLS
- [ ] Disable demo mode (`vericrop.demoMode=false`)
- [ ] Configure firewall rules (only necessary ports open)
- [ ] Set up log monitoring and alerting
- [ ] Configure database backups
- [ ] Review and restrict user permissions
- [ ] Enable Spring Security CSRF protection for web endpoints
- [ ] Set up reverse proxy with SSL termination (nginx/Apache)
- [ ] Configure rate limiting on public APIs
- [ ] Enable audit logging for sensitive operations
- [ ] Set up intrusion detection system (IDS)

### 7. Docker Security

#### Container Hardening

**Non-root User:**
Containers should run as non-root users (where applicable):
```dockerfile
# Dockerfile example
RUN adduser --disabled-password --gecos '' appuser
USER appuser
```

**Resource Limits:**
```yaml
# docker-compose.yml
services:
  postgres:
    mem_limit: 512m
    cpus: 1.0
```

**Image Security:**
- Use official base images
- Scan images for vulnerabilities: `docker scan <image>`
- Keep base images updated
- Minimize image layers

#### Docker Compose Security

```yaml
# Production docker-compose.prod.yml should include:
services:
  postgres:
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
```

### 8. Kubernetes Security (Production)

For Kubernetes deployments (see `k8s/` directory):

1. **Secrets Management**: Use Kubernetes Secrets
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: vericrop-secrets
   type: Opaque
   data:
     postgres-password: <base64-encoded>
     jwt-secret: <base64-encoded>
   ```

2. **RBAC Configuration**: Restrict pod permissions
3. **Network Policies**: Limit inter-pod communication
4. **Pod Security Standards**: Enforce restricted policies
5. **Image Pull Policies**: Always pull from trusted registries

## Reporting Security Vulnerabilities

### Responsible Disclosure

If you discover a security vulnerability in VeriCrop, please report it responsibly:

**DO:**
- Email security details to the maintainer (see README.md for contact)
- Provide detailed steps to reproduce the vulnerability
- Allow reasonable time for a fix before public disclosure (90 days)
- Use encrypted communication if possible

**DO NOT:**
- Publicly disclose the vulnerability before it's fixed
- Exploit the vulnerability beyond proof-of-concept
- Access or modify data without authorization

### Vulnerability Report Format

Please include:
1. **Description**: Clear description of the vulnerability
2. **Impact**: Potential security impact (confidentiality, integrity, availability)
3. **Steps to Reproduce**: Detailed reproduction steps
4. **Affected Versions**: VeriCrop versions affected
5. **Suggested Fix**: If you have one
6. **Your Contact Info**: For follow-up questions

### Response Timeline

- **Initial Response**: Within 48 hours
- **Triage**: Within 7 days
- **Fix Development**: Varies by severity
- **Public Disclosure**: After fix deployed + 90 days (or earlier by agreement)

## Security Testing

### Manual Security Testing

1. **Authentication Testing**:
   ```bash
   # Test account lockout
   for i in {1..6}; do
     curl -X POST http://localhost:8080/api/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username":"testuser","password":"wrongpass"}'
   done
   # Account should be locked after 5 attempts
   ```

2. **SQL Injection Testing**:
   ```bash
   # Should be prevented by prepared statements
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin'\''; DROP TABLE users; --","password":"test"}'
   ```

3. **XSS Testing**: Test input validation in all forms

### Automated Security Testing

#### CodeQL Analysis
GitHub CodeQL is configured for automatic security analysis on pull requests.

Manual scan:
```bash
# Install CodeQL CLI
# Run analysis
codeql database create vericrop-db --language=java
codeql database analyze vericrop-db --format=sarif-latest --output=results.sarif
```

#### Dependency Scanning

**Java:**
```bash
# OWASP Dependency Check
./gradlew dependencyCheckAnalyze

# Gradle versions plugin
./gradlew dependencyUpdates
```

**Python:**
```bash
# Safety check
safety check -r requirements.txt

# pip-audit
pip-audit -r requirements.txt
```

#### Docker Image Scanning
```bash
docker scan vericrop-ml-service:latest
docker scan vericrop-gui:latest
```

### Penetration Testing

For production deployments, consider:
- **Third-party security audit**: Professional penetration testing
- **Bug bounty program**: Incentivize responsible disclosure
- **Red team exercises**: Simulate real-world attacks

## Security Monitoring

### Logging

Security-relevant events are logged:
- Authentication attempts (success/failure)
- Account lockouts
- Blockchain modifications
- Database connections
- API access

```java
// Example security logging
logger.warn("Login failed: incorrect password for user: {}", username);
logger.info("✅ User authenticated via database: {} (role: {})", currentUser, currentRole);
```

### Metrics and Alerting

**Recommended monitoring:**
- Failed login rate (alert if spike)
- API error rates
- Database connection failures
- Blockchain validation failures
- Resource utilization (CPU, memory, disk)

**Tools:**
- Prometheus + Grafana for metrics
- ELK Stack (Elasticsearch, Logstash, Kibana) for log aggregation
- Spring Boot Actuator endpoints: `/actuator/health`, `/actuator/metrics`

## Security Updates

### Update Policy

- **Critical vulnerabilities**: Patched within 24-48 hours
- **High severity**: Patched within 7 days
- **Medium severity**: Patched within 30 days
- **Low severity**: Patched in next regular release

### Notification Channels

Subscribe to security updates:
- GitHub Security Advisories (watch this repository)
- Release notes (GitHub Releases)
- Maintainer announcements

## Compliance and Standards

### Security Standards

VeriCrop follows industry best practices:
- **OWASP Top 10**: Protection against common web vulnerabilities
- **CWE/SANS Top 25**: Mitigation of most dangerous software errors
- **NIST Cybersecurity Framework**: Security controls alignment

### Regulatory Considerations

Depending on deployment context, consider:
- **GDPR**: For European user data
- **HIPAA**: If handling health-related data
- **SOC 2**: For service organization controls
- **ISO 27001**: Information security management

## Why VeriCrop is Secure

### Summary of Security Measures

1. **Strong Authentication**
   - BCrypt password hashing (work factor 10)
   - JWT token-based authentication
   - Account lockout after failed attempts
   - Role-based access control

2. **Data Protection**
   - SQL injection prevention (prepared statements)
   - Blockchain integrity (SHA-256 hashing)
   - Encrypted connections (HTTPS/TLS support)
   - Secure password storage (never plaintext)

3. **Secure Configuration**
   - Environment-based configuration
   - Secrets excluded from version control
   - Demo mode explicitly opt-in (dev only)
   - Production deployment guides

4. **Network Security**
   - Docker network isolation
   - Minimal port exposure
   - Rate limiting and circuit breakers
   - API security best practices

5. **Dependency Management**
   - Regularly updated dependencies
   - Vulnerability scanning tools
   - Security-focused libraries (Spring Security, Bouncy Castle)

6. **Monitoring and Auditing**
   - Comprehensive security logging
   - Health check endpoints
   - Blockchain audit trail
   - Failed login tracking

7. **Development Practices**
   - Security-focused code reviews
   - Automated security testing (CodeQL)
   - Secure coding guidelines
   - Responsible vulnerability disclosure

### Security Verification

To verify security measures:

1. **Check authentication**:
   ```bash
   # Password hashing
   psql -U vericrop -d vericrop -c "SELECT username, password_hash FROM users LIMIT 1;"
   # password_hash should start with $2a$ (BCrypt)
   ```

2. **Check blockchain integrity**:
   ```bash
   curl http://localhost:8080/producer/blockchain/validate
   # Should return: {"valid": true}
   ```

3. **Check account lockout**:
   - Try 6 failed logins
   - Account should be locked for 30 minutes

4. **Check SQL injection prevention**:
   - Try SQL injection payloads
   - All should be safely handled by prepared statements

5. **Review logs**:
   ```bash
   tail -f logs/vericrop-gui.log | grep -i "security\|auth\|login"
   ```

## Resources

### Documentation
- [Setup Guide](docs/guides/SETUP.md) - Secure installation
- [Deployment Guide](docs/deployment/DEPLOYMENT.md) - Production deployment
- [Kafka Integration](docs/implementation/KAFKA_INTEGRATION.md) - Secure messaging

### External Resources
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [PostgreSQL Security](https://www.postgresql.org/docs/current/security.html)

## Contact

For security concerns or questions:
- **GitHub Issues**: [Report a Security Issue](https://github.com/imperfectperson-max/vericrop-miniproject/issues)
- **Maintainer**: [@imperfectperson-max](https://github.com/imperfectperson-max)

**Note**: For sensitive security vulnerabilities, please use private communication channels rather than public issues.

---

**Last Updated**: 2026-01-08

This security policy is reviewed and updated regularly to reflect current best practices and new security measures implemented in VeriCrop.
