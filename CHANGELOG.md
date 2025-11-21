# Changelog

All notable changes to the VeriCrop project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - Production Readiness & Infrastructure Hardening

#### Code Quality & Linting
- Added Checkstyle configuration for Java code quality enforcement
- Added SpotBugs integration for security analysis
- Integrated linting into Gradle build process
- Created production build script (`vericrop-gui/build-production.sh`) with validation steps

#### Design System & Styling
- Created comprehensive CSS design system with variables (`design-system.css`)
- Updated application styles for consistency and accessibility
- Added focus indicators for keyboard navigation
- Documented responsive breakpoints
- Added support for high contrast and reduced motion preferences

#### Docker & Containerization
- Enhanced GUI Dockerfile with multi-stage build and Alpine base image
- Enhanced ML Service Dockerfile with builder pattern and multiple workers
- Added non-root users to all containers for security
- Optimized layer caching with dependency extraction
- Added proper healthchecks for all services
- Added JVM tuning for container environments

#### Kubernetes Deployment
- Created comprehensive K8s deployment manifests (`k8s/vericrop-gui-deployment.yaml`)
- Added HorizontalPodAutoscaler for auto-scaling (3-10 replicas)
- Added PodDisruptionBudget for high availability
- Configured liveness, readiness, and startup probes
- Added resource requests and limits
- Configured pod anti-affinity for HA
- Added persistent volume claims for data persistence
- Created Ingress configuration with TLS support

#### Configuration & Security
- Created comprehensive `.env.template` with all configuration options
- Added detailed security documentation (`SECURITY.md`)
  - Authentication & Authorization guidelines
  - Data Protection (encryption at rest and in transit)
  - Network Security best practices
  - Secrets Management recommendations
  - Container Security hardening
  - Monitoring & Auditing guidance
  - Incident Response procedures
  - Security checklist for deployment
- Documented TLS/SSL configuration for all services
- Added password policy recommendations
- Documented RBAC implementation

#### CI/CD & Security Scanning
- Enhanced CI workflow with Checkstyle and SpotBugs checks
- Created comprehensive security scanning workflow (`security-scan.yml`)
  - Trivy vulnerability scanning for Docker images
  - CodeQL analysis for Java and Python
  - Dependency review for pull requests
  - Secret scanning with TruffleHog
  - Automated security reports

#### Documentation
- Created comprehensive deployment guide (`DEPLOYMENT.md`)
  - Docker Compose deployment instructions
  - Kubernetes deployment instructions
  - Post-deployment validation steps
  - Monitoring setup with Prometheus & Grafana
  - Backup & disaster recovery procedures
  - Troubleshooting guide
- Added this CHANGELOG.md
- Enhanced README with security considerations

### Changed

#### Dockerfiles
- GUI Dockerfile now uses Alpine-based JRE for smaller image size
- ML Service Dockerfile now uses multi-stage build pattern
- All containers now run as non-root user (UID 1000)
- Added tini for proper signal handling in ML service

#### CI Workflows
- CI now runs code quality checks before building
- Added artifact uploads for Checkstyle and SpotBugs reports
- Security scanning runs on every push and daily schedule

### Security

- All Docker containers now run as non-root users
- Added comprehensive security documentation and guidelines
- Implemented automated security scanning in CI/CD
- Added secret scanning to prevent credential leaks
- Documented encryption at rest and in transit
- Added security headers recommendations for Nginx

### Fixed

- Checkstyle XML configuration syntax error

## [1.0.0] - 2024-01-15

### Added
- Initial release of VeriCrop platform
- JavaFX GUI application with four dashboards
  - Farm Management (batch creation, quality assessment)
  - Logistics Tracking (shipment monitoring)
  - Consumer Verification (QR code scanning, product journey)
  - Analytics Dashboard (KPI monitoring, trend analysis)
- User authentication system with BCrypt password hashing
- User messaging system (inbox, sent items, compose)
- FastAPI ML service for quality prediction using ONNX model
- PostgreSQL database with Flyway migrations
- Apache Kafka integration for event streaming
- Blockchain ledger for supply chain transparency
- Apache Airflow for workflow orchestration
- Docker Compose setup for local development
- Comprehensive test suite for Java components
- CI/CD pipelines with GitHub Actions

### Security
- BCrypt password hashing with cost factor 10
- Account lockout after 5 failed login attempts
- Role-based access control (RBAC) with 5 roles
- SQL injection prevention with prepared statements
- Input validation and sanitization

## Migration Guide

### Upgrading from pre-1.0 to 1.0.0

**No migration needed** - This is the initial stable release.

### Upgrading to Unreleased (Production Hardening)

1. **Update Configuration:**
   ```bash
   # Copy new environment template
   cp .env.template .env.new
   
   # Migrate your settings from old .env to .env.new
   # Pay attention to new required variables
   ```

2. **Update Docker Images:**
   ```bash
   # Pull latest images
   docker-compose -f docker-compose.prod.yml pull
   
   # Or rebuild
   docker-compose -f docker-compose.prod.yml build
   ```

3. **Database Migrations:**
   ```bash
   # Migrations run automatically on startup
   # No manual steps required
   ```

4. **Security Hardening:**
   - Review SECURITY.md for new security requirements
   - Update all default passwords
   - Configure TLS/SSL for all services
   - Review and apply firewall rules

5. **Kubernetes Deployment (New):**
   ```bash
   # Create namespace
   kubectl create namespace vericrop
   
   # Create secrets (DO NOT use defaults!)
   kubectl create secret generic vericrop-gui-secrets \
     --from-literal=POSTGRES_PASSWORD=YOUR_PASSWORD \
     -n vericrop
   
   # Deploy
   kubectl apply -f k8s/vericrop-gui-deployment.yaml
   ```

6. **Verify Deployment:**
   ```bash
   # Check all services
   docker-compose -f docker-compose.prod.yml ps
   
   # Or for Kubernetes
   kubectl get pods -n vericrop
   
   # Verify health endpoints
   curl https://your-domain.com/actuator/health
   ```

## Breaking Changes

### Unreleased

**Docker Images:**
- All containers now run as non-root user (UID 1000)
  - **Impact**: Volume mounts may need permission adjustments
  - **Fix**: `chown -R 1000:1000 /path/to/volume`

- GUI image base changed from `temurin-jammy` to `temurin-alpine`
  - **Impact**: Smaller image size, but Alpine uses `apk` instead of `apt`
  - **Fix**: Update any custom scripts that assume Debian/Ubuntu

**Environment Variables:**
- New required variables in `.env.template`
  - **Impact**: Missing variables may cause startup failures
  - **Fix**: Copy `.env.template` and populate all values

**Kubernetes:**
- ServiceAccount now required for GUI deployment
  - **Impact**: May require RBAC updates
  - **Fix**: Apply full `k8s/vericrop-gui-deployment.yaml`

## Deprecations

None in this release.

## Removed

None in this release.

## Known Issues

### Unreleased

1. **JavaFX GUI cannot run in headless environment**
   - The GUI requires a display (X11, Wayland, or VNC)
   - Workaround: Use Xvfb for headless testing
   - Planned fix: Separate REST API from GUI in future release

2. **Checkstyle may report warnings on generated code**
   - Some generated classes don't pass Checkstyle
   - Workaround: Exclude generated sources in config
   - Planned fix: Update exclusion patterns

3. **SpotBugs false positives**
   - Some legitimate code patterns trigger warnings
   - Workaround: Use `@SuppressFBWarnings` annotation
   - Planned fix: Fine-tune SpotBugs configuration

## Contributors

- imperfectperson-max ([@imperfectperson-max](https://github.com/imperfectperson-max))
- GitHub Copilot (AI pair programmer)

## License

[To be determined]

---

For more details on changes, see the [commit history](https://github.com/imperfectperson-max/vericrop-miniproject/commits/main).

To report issues or suggest features, please [open an issue](https://github.com/imperfectperson-max/vericrop-miniproject/issues).
