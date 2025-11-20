# GitHub Actions Workflow Fixes

This document describes the fixes applied to resolve CI failures related to permission errors and network issues.

## Issues Addressed

### 1. HTTP 403 "Resource not accessible by integration" Error

**Problem**: The `EnricoMi/publish-unit-test-result-action@v2` action in the CI workflow was failing with a 403 error when trying to publish test results because the default GITHUB_TOKEN lacked sufficient permissions.

**Solution**: 
- Added `pull-requests: write` permission to the workflow-level permissions block in `ci.yml`
- Added `actions: read` permission for artifact access
- Applied same permissions to all E2E and integration test workflows

**Files Modified**:
- `.github/workflows/ci.yml`
- `.github/workflows/integration.yml`
- `.github/workflows/e2e-integration.yml`
- `.github/workflows/e2e.yml`

### 2. HTTP 503 Gradle Distribution Download Failures (Qodana)

**Problem**: The Qodana static analysis job was failing when trying to download the Gradle distribution from GitHub's services, which occasionally return HTTP 503 errors due to rate limiting or temporary unavailability.

**Solution**: 
- Added `actions/setup-java@v4` step to ensure Java 17 is available before running Gradle
- Added `actions/cache@v4` to cache the Gradle wrapper distribution directory (`~/.gradle/wrapper`)
- Added pre-download step with retry logic (3 attempts, 5-second delays) to download Gradle before Qodana runs
- This ensures Gradle is already available locally when Qodana needs it, avoiding external download failures

**Files Modified**:
- `.github/workflows/qodana_code_quality.yml`

### 3. Transient Network Failures in CI

**Problem**: CI builds could fail due to temporary network issues when downloading the Gradle wrapper.

**Solution**:
- Added "Validate Gradle wrapper download" step to `ci.yml` with retry logic
- Created reusable script `scripts/validate-gradle-wrapper.sh` for Gradle validation
- Script supports configurable retry parameters via environment variables

**Files Modified**:
- `.github/workflows/ci.yml`
- `scripts/validate-gradle-wrapper.sh` (new file)

### 4. Outdated Action Versions

**Problem**: Several workflows were using deprecated or outdated action versions.

**Solution**: Updated to latest stable versions:
- `docker/setup-buildx-action@v2` → `docker/setup-buildx-action@v3`
- `docker/build-push-action@v4` → `docker/build-push-action@v5`
- `docker/login-action@v2` → `docker/login-action@v3`

**Files Modified**:
- `.github/workflows/integration.yml`
- `.github/workflows/e2e-integration.yml`
- `.github/workflows/e2e.yml`
- `.github/workflows/publish-ml-service.yml`

## Summary of Changes

| Workflow | Changes Applied |
|----------|----------------|
| `ci.yml` | ✅ Added permissions (pull-requests: write, actions: read)<br>✅ Added Gradle validation with retry logic |
| `qodana_code_quality.yml` | ✅ Added Java setup step<br>✅ Added Gradle wrapper caching<br>✅ Added pre-download with retry logic<br>✅ Added actions: read permission |
| `integration.yml` | ✅ Added pull-requests: write permission<br>✅ Updated action versions (buildx v3, build-push v5) |
| `e2e.yml` | ✅ Added pull-requests: write permission<br>✅ Updated action versions (buildx v3, build-push v5) |
| `e2e-integration.yml` | ✅ Added pull-requests: write permission<br>✅ Updated action versions (buildx v3, build-push v5) |
| `cd.yml` | ✅ Added minimal permissions (contents: read) |
| `publish-ml-service.yml` | ✅ Updated action versions (buildx v3, login v3, build-push v5) |

## Testing

All workflow files have been validated:
- ✅ YAML syntax validation passed
- ✅ Gradle wrapper validation script tested successfully
- ✅ Permissions structure verified

## Maintenance Notes

### Gradle Wrapper Validation Script

The `scripts/validate-gradle-wrapper.sh` script can be configured with environment variables:

```bash
# Default values
MAX_ATTEMPTS=3  # Number of retry attempts
RETRY_DELAY=5   # Delay between retries in seconds

# Example: Run with custom values
MAX_ATTEMPTS=5 RETRY_DELAY=10 ./scripts/validate-gradle-wrapper.sh
```

### Permissions Reference

The following permissions are now granted to workflows:

- `contents: read` - Read repository contents (default, secure)
- `checks: write` - Create and update check runs (for test results)
- `pull-requests: write` - Comment on and update pull requests (for test summaries)
- `actions: read` - Read workflow run artifacts
- `packages: write` - Push to GitHub Container Registry (GHCR workflows only)

### Caching Strategy

The Qodana workflow now caches:
- `~/.gradle/wrapper` - Gradle wrapper distributions
- `~/.gradle/caches` - Gradle build cache

Cache keys are based on `gradle-wrapper.properties` hash, ensuring the cache is invalidated when Gradle version changes.

## Repository Settings

No repository settings changes are required. All changes use the default `GITHUB_TOKEN` which is automatically provided by GitHub Actions.

## Further Reading

- [GitHub Actions Permissions](https://docs.github.com/en/actions/security-guides/automatic-token-authentication#permissions-for-the-github_token)
- [Gradle Wrapper Documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Qodana Documentation](https://www.jetbrains.com/help/qodana/)
