# Security Guide for VeriCrop

This document outlines security considerations, best practices, and configurations for deploying VeriCrop in production.

## Table of Contents

- [Security Overview](#security-overview)
- [Authentication & Authorization](#authentication--authorization)
- [Data Protection](#data-protection)
- [Network Security](#network-security)
- [Secrets Management](#secrets-management)
- [Container Security](#container-security)
- [Monitoring & Auditing](#monitoring--auditing)
- [Incident Response](#incident-response)
- [Security Checklist](#security-checklist)

## Security Overview

VeriCrop implements multiple layers of security:

1. **Application Security**: BCrypt password hashing, input validation, SQL injection prevention
2. **Network Security**: TLS/SSL encryption, firewall rules, network segmentation
3. **Container Security**: Non-root users, minimal base images, security scanning
4. **Data Security**: Encryption at rest and in transit, secure backups
5. **Access Control**: Role-based access control (RBAC), principle of least privilege

## Authentication & Authorization

### Password Security

VeriCrop uses BCrypt for password hashing with a cost factor of 10 (configurable).

```sql
-- Example: User with BCrypt hashed password
INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2a$10$...', 'ADMIN');
```

**Best Practices:**
- Enforce strong password policies (minimum 12 characters, mixed case, numbers, symbols)
- Implement account lockout after 5 failed login attempts
- Use 2FA/MFA for production deployments (future enhancement)
- Rotate passwords every 90 days
- Never store passwords in plaintext

### Role-Based Access Control (RBAC)

VeriCrop supports five user roles:

| Role | Permissions |
|------|-------------|
| `ADMIN` | Full system access, user management |
| `FARMER` | Create batches, view analytics |
| `SUPPLIER` | Logistics operations, shipment tracking |
| `CONSUMER` | Verify products, view blockchain |
| `USER` | Basic read-only access |

**Implementation:**
```java
@PreAuthorize("hasRole('ADMIN')")
public void adminOnlyOperation() {
    // Admin-only code
}
```

### Session Management

- Session timeout: 30 minutes of inactivity (configurable)
- Secure session cookies with HttpOnly and Secure flags
- CSRF protection enabled for all state-changing operations
- Session invalidation on logout

## Data Protection

### Encryption at Rest

**PostgreSQL Database:**
```bash
# Enable Transparent Data Encryption (TDE)
# Use encrypted volumes in cloud environments (AWS EBS encryption, Azure Disk Encryption)
```

**Blockchain Ledger:**
- Stored as JSON files with SHA-256 hashing
- File permissions: 600 (owner read/write only)
- Backup encrypted with GPG or similar

### Encryption in Transit

**TLS/SSL Configuration:**

1. **PostgreSQL TLS:**
```yaml
# docker-compose.prod.yml
postgres:
  command: -c ssl=on -c ssl_cert_file=/etc/ssl/certs/server.crt -c ssl_key_file=/etc/ssl/private/server.key
  volumes:
    - ./certs:/etc/ssl/certs:ro
    - ./keys:/etc/ssl/private:ro
```

2. **Kafka TLS:**
```properties
# server.properties
listeners=SSL://kafka:9093
ssl.keystore.location=/etc/kafka/secrets/kafka.keystore.jks
ssl.keystore.password=${KAFKA_KEYSTORE_PASSWORD}
ssl.key.password=${KAFKA_KEY_PASSWORD}
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.jks
ssl.truststore.password=${KAFKA_TRUSTSTORE_PASSWORD}
```

3. **Application TLS:**
```yaml
# application-production.yml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: vericrop
```

### Data Sanitization

**Input Validation:**
- Use prepared statements for all SQL queries (prevents SQL injection)
- Sanitize user inputs (XSS prevention)
- Validate file uploads (type, size, content)
- Use parameterized Kafka messages

**Example:**
```java
// GOOD: Prepared statement
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, username);

// BAD: String concatenation
String sql = "SELECT * FROM users WHERE username = '" + username + "'";  // ❌ SQL Injection risk!
```

## Network Security

### Firewall Rules

**Production Network Topology:**
```
Internet
   │
   ├─→ Load Balancer (HTTPS:443)
   │      │
   │      └─→ VeriCrop GUI (Internal: 8080)
   │
   ├─→ Bastion Host (SSH:22, restricted IPs)
   │
   └─→ Internal Network (no direct internet access)
          ├─→ PostgreSQL (5432)
          ├─→ Kafka (9092)
          ├─→ ML Service (8000)
          └─→ Airflow (8082)
```

**Firewall Configuration:**
```bash
# Allow HTTPS from internet
iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# Allow SSH from specific IPs only
iptables -A INPUT -p tcp --dport 22 -s 203.0.113.0/24 -j ACCEPT

# Drop all other incoming traffic
iptables -P INPUT DROP
```

### Network Segmentation

Use Docker networks or Kubernetes network policies:

```yaml
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # No external access
```

Services requiring internet access (ML service for model downloads):
- Use explicit egress rules
- Whitelist specific domains
- Use HTTP proxy for logging/auditing

## Secrets Management

### Never Commit Secrets

**Prevent accidental commits:**
```bash
# .gitignore
.env
.env.production
.env.local
*.key
*.pem
*.p12
*.jks
secrets/
```

**Git hooks:**
```bash
# .git/hooks/pre-commit
#!/bin/bash
if git diff --cached --name-only | grep -E '\.env$|\.key$|\.pem$'; then
    echo "Error: Attempting to commit sensitive files!"
    exit 1
fi
```

### Recommended Secret Management Solutions

1. **HashiCorp Vault** (Recommended for enterprise)
   ```bash
   # Store secret
   vault kv put secret/vericrop/db password=mySecurePassword
   
   # Retrieve secret
   vault kv get -field=password secret/vericrop/db
   ```

2. **AWS Secrets Manager**
   ```bash
   # Store secret
   aws secretsmanager create-secret --name vericrop/db/password --secret-string "mySecurePassword"
   
   # Retrieve secret
   aws secretsmanager get-secret-value --secret-id vericrop/db/password --query SecretString --output text
   ```

3. **Kubernetes Secrets**
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: vericrop-secrets
   type: Opaque
   data:
     db-password: <base64-encoded-password>
   ```

### Environment-Specific Secrets

Use different secrets for each environment:
- Development: Less restrictive, rotated monthly
- Staging: Production-like, rotated bi-weekly
- Production: Highly secure, rotated weekly

## Container Security

### Non-Root Users

All VeriCrop containers run as non-root users:

```dockerfile
# Create non-root user
RUN useradd -m -u 1000 vericrop

# Switch to non-root user
USER vericrop
```

### Minimal Base Images

- **Java Services**: `eclipse-temurin:17-jre-alpine` (minimal JRE)
- **Python Services**: `python:3.11-slim-bookworm` (slim Debian)
- **Avoid**: full JDK, Ubuntu/CentOS (too large)

### Image Scanning

**Scan for vulnerabilities:**
```bash
# Using Trivy
trivy image vericrop-gui:latest

# Using Snyk
snyk container test vericrop-gui:latest

# Using Docker Scout
docker scout cves vericrop-gui:latest
```

**CI/CD Integration:**
```yaml
# .github/workflows/security-scan.yml
- name: Run Trivy vulnerability scanner
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: 'vericrop-gui:latest'
    format: 'sarif'
    output: 'trivy-results.sarif'
```

### Resource Limits

Prevent resource exhaustion attacks:

```yaml
# docker-compose.prod.yml
services:
  vericrop-gui:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
```

## Monitoring & Auditing

### Security Logging

**What to Log:**
- Authentication events (login, logout, failed attempts)
- Authorization failures
- Data access (who accessed what, when)
- Configuration changes
- System errors and exceptions

**Centralized Logging:**
```yaml
# docker-compose.prod.yml
logging:
  driver: "syslog"
  options:
    syslog-address: "tcp://logstash:5000"
    tag: "vericrop-{{.Name}}"
```

### Audit Trail

**Database Audit Table:**
```sql
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    details JSONB
);

-- Example audit trigger
CREATE OR REPLACE FUNCTION audit_user_changes()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (user_id, action, entity_type, entity_id, details)
    VALUES (current_user_id(), TG_OP, 'user', NEW.id, row_to_json(NEW));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

### Security Metrics

**Prometheus Metrics:**
- `vericrop_login_attempts_total{status="success|failure"}`
- `vericrop_api_requests_total{endpoint, status}`
- `vericrop_authentication_failures_total{reason}`
- `vericrop_rate_limit_exceeded_total{endpoint}`

**Grafana Dashboard:**
- Failed login attempts (last 24h)
- API error rates
- Unusual access patterns
- Resource utilization

### Alerting

**Example Alerts:**
```yaml
groups:
  - name: security
    rules:
      - alert: HighFailedLoginRate
        expr: rate(vericrop_login_attempts_total{status="failure"}[5m]) > 10
        for: 5m
        annotations:
          summary: "High rate of failed login attempts"
          description: "More than 10 failed logins per minute in the last 5 minutes"
```

## Incident Response

### Incident Response Plan

1. **Detection**: Monitoring systems alert on suspicious activity
2. **Containment**: Isolate affected systems, disable compromised accounts
3. **Eradication**: Remove malware, patch vulnerabilities
4. **Recovery**: Restore from clean backups, verify integrity
5. **Lessons Learned**: Document incident, update procedures

### Emergency Procedures

**Compromised Credentials:**
```bash
# 1. Immediately revoke access
psql -U admin -d vericrop -c "UPDATE users SET status='locked' WHERE username='compromised_user';"

# 2. Reset password
psql -U admin -d vericrop -c "UPDATE users SET password_hash='$2a$10$...' WHERE username='compromised_user';"

# 3. Invalidate all sessions
# (Implementation depends on session management strategy)

# 4. Review audit logs
psql -U admin -d vericrop -c "SELECT * FROM audit_log WHERE user_id=(SELECT id FROM users WHERE username='compromised_user') ORDER BY timestamp DESC LIMIT 100;"
```

**Database Breach:**
```bash
# 1. Immediately block external access
iptables -A INPUT -p tcp --dport 5432 -j DROP

# 2. Take snapshot for forensics
docker exec vericrop-postgres pg_dump -U vericrop vericrop > breach_snapshot_$(date +%Y%m%d_%H%M%S).sql

# 3. Restore from last known good backup
# 4. Analyze logs and determine scope of breach
# 5. Notify affected users (if applicable)
```

### Contact Information

- **Security Team**: security@vericrop.local
- **On-Call Engineer**: Use PagerDuty/OpsGenie
- **Emergency Hotline**: +1-XXX-XXX-XXXX

## Security Checklist

### Pre-Deployment Checklist

- [ ] All default passwords changed
- [ ] Strong passwords enforced (min 12 chars, mixed case, numbers, symbols)
- [ ] TLS/SSL enabled for all services
- [ ] Secrets stored in secret management system (not .env files)
- [ ] Non-root users configured for all containers
- [ ] Resource limits set for all containers
- [ ] Firewall rules configured
- [ ] Security scanning completed (no critical/high vulnerabilities)
- [ ] Backup strategy implemented and tested
- [ ] Monitoring and alerting configured
- [ ] Incident response plan documented
- [ ] Security audit completed

### Regular Maintenance

- [ ] Weekly: Review security logs and alerts
- [ ] Weekly: Rotate production secrets
- [ ] Monthly: Update dependencies and patch vulnerabilities
- [ ] Monthly: Review user access and permissions
- [ ] Quarterly: Conduct security audit
- [ ] Quarterly: Test backup restoration
- [ ] Annually: Penetration testing
- [ ] Annually: Security training for team

### Vulnerability Response

When a vulnerability is discovered:

1. **Assess severity** (Critical/High/Medium/Low)
2. **Determine impact** on VeriCrop
3. **Patch immediately** if critical
4. **Schedule update** if non-critical
5. **Communicate** to stakeholders
6. **Document** in security log

## Additional Resources

- [OWASP Top Ten](https://owasp.org/www-project-top-ten/)
- [CIS Docker Benchmark](https://www.cisecurity.org/benchmark/docker)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [Java Security Best Practices](https://docs.oracle.com/en/java/javase/17/security/)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)

## Reporting Security Issues

If you discover a security vulnerability in VeriCrop, please report it to:

**Email**: security@vericrop.local  
**PGP Key**: [Link to PGP public key]

Please do NOT create public GitHub issues for security vulnerabilities.

---

**Last Updated**: 2024-01-15  
**Next Review**: 2024-04-15  
**Maintained By**: VeriCrop Security Team
