# Production Security Checklist

Use this checklist before deploying VeriCrop to production. This ensures all security measures are properly configured.

## Pre-Deployment Security Checklist

### 1. Authentication & Credentials

- [ ] **Change all default passwords**
  ```bash
  # PostgreSQL
  POSTGRES_PASSWORD=<strong-unique-password>
  
  # Airflow admin
  AIRFLOW_ADMIN_PASSWORD=<strong-unique-password>
  
  # Default demo users removed or disabled
  ```

- [ ] **Configure JWT secret**
  ```bash
  # Generate strong random secret (min 32 characters)
  JWT_SECRET=$(openssl rand -base64 32)
  echo "JWT_SECRET=$JWT_SECRET" >> .env.production
  ```

- [ ] **Set secure JWT expiration**
  ```bash
  JWT_EXPIRATION=86400000  # 24 hours, adjust as needed
  ```

- [ ] **Disable demo mode**
  ```bash
  # Ensure demo mode is NOT enabled
  # Remove or set to false: -Dvericrop.demoMode=false
  ```

- [ ] **Review user accounts**
  ```sql
  -- Connect to database
  psql -U vericrop -d vericrop
  
  -- List all users and their roles
  SELECT username, role, status, created_at FROM users;
  
  -- Disable unused accounts
  UPDATE users SET status = 'inactive' WHERE username IN ('old_user1', 'old_user2');
  ```

### 2. Network & TLS Configuration

- [ ] **Enable HTTPS/TLS**
  ```yaml
  # nginx configuration example
  server {
    listen 443 ssl http2;
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
  }
  ```

- [ ] **Configure PostgreSQL SSL**
  ```bash
  # In docker-compose.prod.yml or PostgreSQL config
  POSTGRES_SSL_MODE=require
  ```

- [ ] **Configure Kafka SSL/TLS**
  ```properties
  # kafka.properties
  security.protocol=SSL
  ssl.truststore.location=/path/to/truststore.jks
  ssl.truststore.password=<password>
  ssl.keystore.location=/path/to/keystore.jks
  ssl.keystore.password=<password>
  ```

- [ ] **Restrict port exposure**
  ```yaml
  # docker-compose.prod.yml
  # Only expose necessary ports
  # Consider using 127.0.0.1:port for internal services
  ports:
    - "127.0.0.1:5432:5432"  # PostgreSQL - internal only
    - "127.0.0.1:9092:9092"  # Kafka - internal only
  ```

- [ ] **Configure firewall rules**
  ```bash
  # Example: UFW (Ubuntu)
  sudo ufw default deny incoming
  sudo ufw default allow outgoing
  sudo ufw allow 443/tcp  # HTTPS
  sudo ufw allow 22/tcp   # SSH (restrict to specific IPs in production)
  sudo ufw enable
  ```

### 3. Database Security

- [ ] **Enable database backups**
  ```bash
  # Example backup script
  pg_dump -U vericrop -d vericrop > backup_$(date +%Y%m%d_%H%M%S).sql
  
  # Set up automated backups (cron)
  0 2 * * * /path/to/backup-script.sh
  ```

- [ ] **Configure connection limits**
  ```properties
  # application.properties
  spring.datasource.hikari.maximum-pool-size=10
  spring.datasource.hikari.connection-timeout=30000
  spring.datasource.hikari.idle-timeout=600000
  ```

- [ ] **Enable query logging for auditing**
  ```sql
  -- PostgreSQL log configuration
  ALTER SYSTEM SET log_statement = 'ddl';
  ALTER SYSTEM SET log_connections = on;
  ALTER SYSTEM SET log_disconnections = on;
  ```

- [ ] **Review database permissions**
  ```sql
  -- Verify user permissions
  \du
  
  -- Ensure users have minimal required permissions
  REVOKE ALL ON DATABASE vericrop FROM public;
  GRANT CONNECT ON DATABASE vericrop TO vericrop;
  ```

### 4. Application Security Configuration

- [ ] **Set production environment variables**
  ```bash
  # .env.production
  VERICROP_MODE=prod
  SPRING_PROFILES_ACTIVE=production
  LOG_LEVEL=INFO
  ```

- [ ] **Enable security headers**
  ```java
  // Spring Security configuration
  http.headers()
    .contentSecurityPolicy("default-src 'self'")
    .xssProtection()
    .frameOptions().deny()
    .httpStrictTransportSecurity();
  ```

- [ ] **Configure CORS properly**
  ```java
  @Configuration
  public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
      return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
          registry.addMapping("/api/**")
            .allowedOrigins("https://yourdomain.com")  // Specific domains only
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowCredentials(true);
        }
      };
    }
  }
  ```

- [ ] **Enable rate limiting**
  ```yaml
  # application.yml
  resilience4j.ratelimiter:
    instances:
      api:
        limitForPeriod: 100
        limitRefreshPeriod: 60s
        timeoutDuration: 5s
  ```

- [ ] **Configure session timeout**
  ```properties
  server.servlet.session.timeout=30m
  ```

### 5. Docker & Container Security

- [ ] **Use non-root users in containers**
  ```dockerfile
  # Dockerfile
  RUN adduser --disabled-password --gecos '' appuser
  USER appuser
  ```

- [ ] **Set resource limits**
  ```yaml
  # docker-compose.prod.yml
  services:
    postgres:
      mem_limit: 2g
      cpus: 2.0
      mem_reservation: 1g
  ```

- [ ] **Use Docker secrets for sensitive data**
  ```yaml
  # docker-compose.prod.yml
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

- [ ] **Scan images for vulnerabilities**
  ```bash
  docker scan vericrop-ml-service:latest
  docker scan vericrop-gui:latest
  ```

- [ ] **Keep base images updated**
  ```bash
  docker pull postgres:15
  docker pull confluentinc/cp-kafka:7.4.0
  docker compose build --no-cache
  ```

### 6. Monitoring & Logging

- [ ] **Configure centralized logging**
  ```yaml
  # Logstash/ELK configuration
  logging:
    level:
      org.vericrop: INFO
      org.springframework.security: WARN
    file:
      name: /var/log/vericrop/application.log
      max-size: 100MB
      max-history: 30
  ```

- [ ] **Set up security monitoring**
  ```bash
  # Monitor failed login attempts
  tail -f logs/vericrop-gui.log | grep "Login failed"
  
  # Monitor blockchain validation
  tail -f logs/vericrop-gui.log | grep "Blockchain validation"
  ```

- [ ] **Configure alerting**
  ```yaml
  # Example: Prometheus alerting rules
  - alert: HighFailedLoginRate
    expr: rate(login_failed_total[5m]) > 10
    for: 5m
    annotations:
      summary: High rate of failed logins detected
  ```

- [ ] **Enable audit logging**
  ```properties
  # Log security-relevant events
  logging.level.org.springframework.security=DEBUG
  ```

### 7. Dependency & Vulnerability Management

- [ ] **Update all dependencies**
  ```bash
  # Java dependencies
  ./gradlew dependencyUpdates
  
  # Python dependencies
  cd docker/ml-service
  pip list --outdated
  ```

- [ ] **Run vulnerability scans**
  ```bash
  # Java dependencies
  ./gradlew dependencyCheckAnalyze
  
  # Python dependencies
  pip install safety
  safety check -r requirements.txt
  ```

- [ ] **Subscribe to security advisories**
  - GitHub Security Advisories (watch repository)
  - Spring Security mailing list
  - PostgreSQL security announcements

### 8. Backup & Recovery

- [ ] **Database backup strategy**
  ```bash
  # Automated daily backups
  pg_dump -U vericrop -d vericrop | gzip > backup.sql.gz
  
  # Test restore procedure
  gunzip < backup.sql.gz | psql -U vericrop -d vericrop_test
  ```

- [ ] **Blockchain backup**
  ```bash
  # Backup blockchain ledger files
  cp blockchain.json /backup/blockchain_$(date +%Y%m%d).json
  cp vericrop_chain.json /backup/vericrop_chain_$(date +%Y%m%d).json
  ```

- [ ] **Test recovery procedures**
  - Document restoration steps
  - Test database restore
  - Verify blockchain integrity after restore

### 9. Access Control

- [ ] **Implement principle of least privilege**
  ```sql
  -- Grant only necessary permissions
  GRANT SELECT, INSERT, UPDATE ON batches TO vericrop_app;
  REVOKE DELETE ON batches FROM vericrop_app;
  ```

- [ ] **Configure SSH access**
  ```bash
  # Disable password authentication, use keys only
  # /etc/ssh/sshd_config
  PasswordAuthentication no
  PubkeyAuthentication yes
  PermitRootLogin no
  ```

- [ ] **Set up VPN for administrative access**
  - Consider VPN or bastion host for database access
  - Restrict administrative interfaces to internal network

### 10. Compliance & Documentation

- [ ] **Document security architecture**
  - Network diagram with security zones
  - Data flow diagram
  - Access control matrix

- [ ] **Create incident response plan**
  - Contact information
  - Escalation procedures
  - Communication templates

- [ ] **Review privacy requirements**
  - GDPR compliance (if applicable)
  - Data retention policies
  - User data handling procedures

- [ ] **Perform security audit**
  - Internal security review
  - Consider third-party penetration testing
  - Document findings and remediation

## Post-Deployment Verification

After deployment, verify security measures:

### 1. Authentication Tests
```bash
# Test account lockout
for i in {1..6}; do
  curl -X POST https://yourdomain.com/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"testuser","password":"wrongpass"}'
done

# Verify lockout occurred
```

### 2. SSL/TLS Verification
```bash
# Check SSL certificate
openssl s_client -connect yourdomain.com:443

# Verify SSL rating
curl https://www.ssllabs.com/ssltest/analyze.html?d=yourdomain.com
```

### 3. Port Scan
```bash
# Verify only necessary ports are open
nmap -sV yourdomain.com
```

### 4. Blockchain Integrity
```bash
# Verify blockchain is valid
curl https://yourdomain.com/producer/blockchain/validate
```

### 5. Backup Verification
```bash
# Test database backup restore
pg_restore -U vericrop -d vericrop_test backup.sql
```

## Continuous Security

Security is an ongoing process. Schedule regular reviews:

### Daily
- [ ] Monitor security logs
- [ ] Review failed login attempts
- [ ] Check system resource usage

### Weekly
- [ ] Review access logs
- [ ] Check for dependency updates
- [ ] Verify backup integrity

### Monthly
- [ ] Update dependencies
- [ ] Review user accounts
- [ ] Test disaster recovery procedures
- [ ] Review security logs for anomalies

### Quarterly
- [ ] Security audit
- [ ] Penetration testing
- [ ] Update security documentation
- [ ] Review and update security policies

## Emergency Contacts

Document emergency contacts for security incidents:

- **Security Team Lead**: [Name, Email, Phone]
- **System Administrator**: [Name, Email, Phone]
- **Database Administrator**: [Name, Email, Phone]
- **Hosting Provider Support**: [Contact Info]
- **Security Incident Hotline**: [Number]

## Security Incident Response

If a security incident is detected:

1. **Isolate**: Disconnect affected systems if necessary
2. **Document**: Record all actions taken
3. **Notify**: Contact security team and stakeholders
4. **Investigate**: Determine scope and impact
5. **Remediate**: Apply fixes and patches
6. **Review**: Post-incident analysis and lessons learned

## References

- [VeriCrop Security Policy](../SECURITY.md)
- [Security Testing Guide](./SECURITY_TESTING.md)
- [Deployment Guide](./deployment/DEPLOYMENT.md)
- [OWASP Security Checklist](https://owasp.org/www-project-web-security-testing-guide/)

---

**Last Updated**: 2026-01-08

**Checklist Version**: 1.0

This checklist should be reviewed and updated regularly to reflect new security requirements and best practices.
