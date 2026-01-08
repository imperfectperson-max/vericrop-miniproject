# VeriCrop Security Quick Reference

## 🔒 Security Features Summary

### Authentication & Authorization
- **Password Hashing**: BCrypt (work factor 10)
- **Token Auth**: JWT (HMAC SHA-256, 24h expiration)
- **Account Protection**: Lockout after 5 failed attempts (30 min)
- **Access Control**: Role-based (ADMIN, FARMER, SUPPLIER, CONSUMER)

### Data Protection
- **SQL Injection**: ✅ Prevented (PreparedStatements)
- **XSS**: ✅ Input sanitization
- **Blockchain**: ✅ SHA-256 integrity
- **Encryption**: ✅ TLS/SSL support

### Infrastructure
- **Network**: Docker isolation
- **Ports**: Minimal exposure
- **Secrets**: Environment variables
- **Monitoring**: Security event logging

## 🚀 Quick Start Security Checks

### 1. Verify Password Hashing (5 seconds)
```bash
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, LEFT(password_hash, 4) FROM users LIMIT 1;"
# Expected: $2a$ (BCrypt prefix)
```

### 2. Test Account Lockout (30 seconds)
```bash
for i in {1..6}; do
  curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}' | jq .
done
# Expected: Account locked after 5 attempts
```

### 3. Verify Blockchain Integrity (5 seconds)
```bash
curl -s http://localhost:8080/producer/blockchain/validate | jq .
# Expected: {"valid": true}
```

### 4. Check SQL Injection Protection (10 seconds)
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin'\''--","password":"any"}' | jq .
# Expected: Authentication failed (not SQL error)
```

### 5. Verify Secrets Not in Version Control (5 seconds)
```bash
cat .gitignore | grep ".env"
ls -la | grep ".env$"
# Expected: .env in .gitignore, .env file not tracked
```

## 🛡️ Common Security Tasks

### Change Default Passwords
```bash
# PostgreSQL
docker exec -it vericrop-postgres psql -U vericrop -d vericrop
ALTER USER vericrop WITH PASSWORD 'new_strong_password';

# Update .env
POSTGRES_PASSWORD=new_strong_password
```

### Generate JWT Secret
```bash
# Generate 32+ character random secret
openssl rand -base64 32
# Add to .env
JWT_SECRET=<generated_secret>
```

### Enable HTTPS (Production)
```nginx
# nginx.conf
server {
    listen 443 ssl http2;
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

### Check Open Ports
```bash
# List listening ports
netstat -tulpn | grep LISTEN

# Docker port mappings
docker-compose ps
```

### Backup Database
```bash
# Create backup
docker exec vericrop-postgres pg_dump -U vericrop -d vericrop > backup.sql

# Restore backup
docker exec -i vericrop-postgres psql -U vericrop -d vericrop < backup.sql
```

## 🔍 Security Monitoring Commands

### View Failed Login Attempts
```bash
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, failed_login_attempts, locked_until FROM users WHERE failed_login_attempts > 0;"
```

### Check Active Sessions
```bash
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, last_login FROM users WHERE last_login > NOW() - INTERVAL '1 day';"
```

### Monitor Security Logs
```bash
# Real-time log monitoring
tail -f logs/vericrop-gui.log | grep -i "security\|auth\|login\|failed"

# Count failed logins today
grep "Login failed" logs/vericrop-gui.log | grep $(date +%Y-%m-%d) | wc -l
```

### Verify Blockchain Integrity
```bash
# Validate entire chain
curl -s http://localhost:8080/producer/blockchain/validate

# Check recent blocks
curl -s http://localhost:8080/producer/blockchain?limit=5 | jq '.recent_blocks'
```

## 🚨 Security Incident Response

### 1. Suspected Breach
```bash
# Immediately disable suspected accounts
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "UPDATE users SET status='inactive' WHERE username='suspect_user';"

# Review access logs
tail -n 1000 logs/vericrop-gui.log | grep "suspect_user"

# Check for SQL injection attempts
grep -i "drop\|delete\|insert\|update" logs/vericrop-gui.log
```

### 2. Password Reset (Force)
```sql
-- Connect to database
docker exec -it vericrop-postgres psql -U vericrop -d vericrop

-- Force password reset
UPDATE users SET 
  password_hash = '$2a$10$temporary_hash',
  status = 'password_reset_required'
WHERE username = 'affected_user';
```

### 3. Lock All Accounts (Emergency)
```sql
UPDATE users SET locked_until = NOW() + INTERVAL '24 hours';
-- Unlock admin account for investigation
UPDATE users SET locked_until = NULL WHERE username = 'admin';
```

## 📊 Security Health Check

Run these checks regularly (weekly):

```bash
# 1. Check for outdated dependencies
./gradlew dependencyUpdates

# 2. Scan for vulnerabilities
./gradlew dependencyCheckAnalyze

# 3. Verify database backup exists
ls -lh backups/ | tail -5

# 4. Check disk space
df -h

# 5. Review recent users
docker exec -it vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT username, role, created_at FROM users WHERE created_at > NOW() - INTERVAL '7 days';"
```

## 🔑 Security Configuration Files

### Key Files to Protect
- `.env` - **NEVER commit** (contains secrets)
- `.env.production` - **NEVER commit** (production secrets)
- `blockchain.json` - Backup regularly
- `vericrop_chain.json` - Backup regularly
- SSL certificates - Secure storage only

### Safe to Commit
- `.env.example` - Template without secrets
- `docker-compose.yml` - Generic configuration
- `README.md` - Public documentation
- `SECURITY.md` - Security documentation

## 🎯 Security Testing Shortcuts

```bash
# Quick security test suite (2 minutes)
cd /home/runner/work/vericrop-miniproject/vericrop-miniproject

# 1. BCrypt check
docker exec vericrop-postgres psql -U vericrop -d vericrop \
  -c "SELECT LEFT(password_hash,4) FROM users LIMIT 1;"

# 2. SQL injection test
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin'\''--","password":"x"}'

# 3. Account lockout test
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
done

# 4. Blockchain validation
curl http://localhost:8080/producer/blockchain/validate

# 5. Check .env not in git
git ls-files | grep "\.env$"
# Should return nothing
```

## 📚 Security Resources

### Documentation
- [Full Security Policy](../SECURITY.md)
- [Security Testing Guide](./SECURITY_TESTING.md)
- [Production Checklist](./SECURITY_CHECKLIST.md)

### External Resources
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Docs](https://spring.io/projects/spring-security)
- [Docker Security](https://docs.docker.com/engine/security/)

### Tools
- [OWASP ZAP](https://www.zaproxy.org/) - Security testing
- [Safety](https://github.com/pyupio/safety) - Python dependency scanner
- [CodeQL](https://codeql.github.com/) - Code analysis

## 🆘 Emergency Contacts

Update with your actual contacts:

- **Security Lead**: [Contact Info]
- **System Admin**: [Contact Info]
- **On-Call**: [Contact Info]

## ⚡ Quick Reference Table

| Task | Command | Expected Time |
|------|---------|---------------|
| Check password hash | `psql -c "SELECT LEFT(password_hash,4)"` | 5s |
| Validate blockchain | `curl /blockchain/validate` | 5s |
| Test account lockout | 6x failed login | 30s |
| Generate JWT secret | `openssl rand -base64 32` | 5s |
| Backup database | `pg_dump > backup.sql` | 30s |
| Check open ports | `netstat -tulpn` | 10s |
| Monitor logs | `tail -f logs/vericrop-gui.log` | Real-time |
| Scan dependencies | `./gradlew dependencyCheck` | 5 min |

## 🎓 Security Training

For new team members:

1. Read [SECURITY.md](../SECURITY.md) (15 min)
2. Run through [Security Testing Guide](./SECURITY_TESTING.md) (30 min)
3. Review [Production Checklist](./SECURITY_CHECKLIST.md) (20 min)
4. Complete security quick checks above (5 min)
5. Set up monitoring and alerts (30 min)

**Total onboarding time**: ~2 hours

## ✅ Pre-Deployment Checklist

Quick checklist before going live:

- [ ] Changed all default passwords
- [ ] Set strong JWT_SECRET
- [ ] Disabled demo mode
- [ ] Enabled HTTPS/TLS
- [ ] Configured firewall
- [ ] Set up database backups
- [ ] Enabled security logging
- [ ] Reviewed user accounts
- [ ] Tested authentication
- [ ] Validated blockchain
- [ ] Scanned dependencies
- [ ] Documented security contacts

---

**Last Updated**: 2026-01-08

**Keep this card handy for daily security operations!**
