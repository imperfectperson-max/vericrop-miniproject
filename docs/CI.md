# CI/CD Documentation for VeriCrop

This document provides guidance for contributors on running tests locally with the same caching and retry semantics used in CI.

## Overview

The VeriCrop CI pipeline includes:
- **Java (Gradle) builds and tests** - Build and test all Java modules
- **Python ML service tests** - Test the FastAPI ML service
- **E2E integration tests** - Build Docker images and run end-to-end tests

## CI Improvements for Reliability and Speed

### Caching Strategy

The CI workflows use multiple caching strategies to speed up builds:

#### Gradle Caching
- **Location**: `~/.gradle/caches` and `~/.gradle/wrapper`
- **Cache key**: Based on Gradle version, OS, and Gradle files
- **Local testing**: Gradle automatically caches dependencies locally

```bash
# Clean cache if needed
rm -rf ~/.gradle/caches
./gradlew clean build --refresh-dependencies
```

#### Python Pip Caching
- **Location**: `~/.cache/pip`
- **Cache key**: Based on OS and requirements files
- **Local testing**: Pip automatically caches packages

```bash
# Clear pip cache if needed
pip cache purge
pip install -r docker/ml-service/requirements.txt
```

#### Docker Build Caching
- **Location**: `/tmp/.buildx-cache` (in CI)
- **Strategy**: Uses BuildKit cache with cache-from and cache-to
- **Local testing**: Docker automatically uses layer caching

```bash
# Build with cache
docker buildx build --cache-from type=local,src=/tmp/.buildx-cache \
  --cache-to type=local,dest=/tmp/.buildx-cache-new,mode=max \
  -t ml-service:test docker/ml-service
```

### Test Retry Configuration

To handle flaky tests caused by timing issues, network conditions, or external service dependencies:

#### Java Test Retry
- **Plugin**: `org.gradle.test-retry` v1.5.10
- **Configuration**: Retries failed tests up to 2 times
- **Applies to**: All JUnit tests in all subprojects

```bash
# Run tests with retries (automatic in CI and local)
./gradlew test

# View retry reports in build/reports/tests/test/
```

#### Python Test Retry
- **Plugin**: `pytest-rerunfailures`
- **Configuration**: Configured in `pytest.ini` with `--reruns=2 --reruns-delay=1`
- **Applies to**: All pytest tests

```bash
# Run tests with retries (uses pytest.ini config)
cd docker/ml-service
pytest tests/

# Or explicitly specify retry count
pytest tests/ --reruns=2 --reruns-delay=1
```

### Deterministic Test Execution

To reduce non-deterministic test failures, the CI sets environment variables:

#### For Java Tests
```bash
export TZ=UTC
export LANG=en_US.UTF-8
./gradlew test
```

#### For Python Tests
```bash
export PYTHONHASHSEED=0
export TZ=UTC
pytest tests/
```

### CI Workflow Features

#### Concurrency Control
- **Strategy**: `cancel-in-progress` on the same branch/PR
- **Benefit**: Prevents queue pileups from duplicate pushes
- **Example**: Pushing multiple commits in quick succession cancels older runs

#### Timeouts
- **Java build job**: 30 minutes
- **Python test job**: 20 minutes
- **Docker build job**: 20 minutes
- **E2E test job**: 30 minutes

#### Health Checks
E2E workflows include explicit health checks after starting containers:

```bash
# Wait for service to be ready
for i in $(seq 1 60); do
  if curl -fsS http://localhost:8000/health >/dev/null 2>&1; then
    echo "service is up"
    break
  fi
  sleep 1
done
```

## Running Tests Locally

### Java Module Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :vericrop-core:test
./gradlew :vericrop-gui:test
./gradlew :kafka-service:test

# Run a specific test class
./gradlew test --tests "*BlockchainTest"

# Build and test with no daemon (like CI)
./gradlew build test --no-daemon --stacktrace
```

### Python ML Service Tests

```bash
cd docker/ml-service

# Create virtual environment (first time only)
python3 -m venv venv
source venv/bin/activate  # Unix/Mac
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt
pip install -r requirements-test.txt

# Run all tests
pytest tests/

# Run with coverage
pytest tests/ --cov=. --cov-report=html

# Run specific test file
pytest tests/test_health.py -v
```

### E2E Integration Tests

```bash
# Build the ML service Docker image
docker build -t ml-service:local docker/ml-service

# Run container
docker run -d --name mlservice-local -p 8000:8000 ml-service:local

# Wait for service
for i in $(seq 1 30); do
  if curl -fsS http://localhost:8000/health >/dev/null 2>&1; then
    echo "Service is up"
    break
  fi
  sleep 1
done

# Run integration tests
cd docker/ml-service
export BASE_URL=http://localhost:8000
export PYTHONHASHSEED=0
pytest tests/ --reruns=2

# Clean up
docker rm -f mlservice-local
```

## Troubleshooting CI Failures

### Test Retries Exhausted
If tests fail even after retries:
1. Check if the failure is deterministic (fails consistently)
2. Review test logs in GitHub Actions artifacts
3. Run the test locally with the same environment variables
4. Check for external service dependencies (Kafka, PostgreSQL, etc.)

### Cache Issues
If builds are slower than expected:
1. Check cache hit/miss in CI logs
2. Verify cache key matches (OS, file hashes)
3. Force cache refresh by updating dependencies or clearing cache

### Timeout Issues
If jobs timeout:
1. Check if tests are hanging (no output for extended period)
2. Review logs for stuck processes
3. Consider increasing timeout if legitimately slow
4. Optimize slow tests identified by `--durations=10`

### Flaky Tests
If tests fail intermittently:
1. Add more retries for specific tests if needed
2. Increase wait times for service readiness
3. Add explicit synchronization (sleep, wait-for scripts)
4. Set fixed random seeds for randomized tests

## Re-running Failed Jobs

GitHub Actions allows re-running failed jobs:

1. Navigate to the Actions tab in GitHub
2. Click on the failed workflow run
3. Click "Re-run failed jobs" or "Re-run all jobs"
4. Monitor the re-run for success

## Best Practices for Contributors

1. **Run tests locally before pushing** to catch failures early
2. **Use the same environment variables** as CI for deterministic tests
3. **Keep tests fast** - slow tests slow down CI for everyone
4. **Avoid flaky tests** - ensure tests are deterministic and isolated
5. **Check CI logs** when tests fail to understand root cause
6. **Update documentation** when adding new tests or changing CI config

## CI Configuration Files

- `.github/workflows/ci.yml` - Main CI workflow (Java + Python + Docker)
- `.github/workflows/e2e.yml` - E2E integration tests
- `.github/workflows/e2e-integration.yml` - Alternative E2E tests
- `.github/workflows/integration.yml` - Integration tests with coverage
- `build.gradle` - Gradle configuration with test-retry plugin
- `pytest.ini` - Pytest configuration with rerun settings
- `docker/ml-service/requirements-test.txt` - Python test dependencies

## Resources

- [Gradle Test Retry Plugin](https://github.com/gradle/test-retry-gradle-plugin)
- [pytest-rerunfailures](https://github.com/pytest-dev/pytest-rerunfailures)
- [GitHub Actions Caching](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
- [Docker BuildKit Caching](https://docs.docker.com/build/cache/)

## Getting Help

If you encounter persistent CI issues:

1. Check existing GitHub Issues for similar problems
2. Review this documentation and troubleshooting steps
3. Run tests locally with CI environment settings
4. Open a new issue with:
   - Link to failed workflow run
   - Steps to reproduce locally
   - Relevant log excerpts
   - Environment details
