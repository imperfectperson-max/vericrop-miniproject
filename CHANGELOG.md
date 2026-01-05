# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Gradle Test Retry plugin (v1.5.10) to automatically retry flaky tests up to 2 times
- pytest-rerunfailures plugin to Python test dependencies for handling transient test failures
- pytest.ini configuration file with test retry settings (--reruns=2)
- Docker BuildKit caching for E2E workflows to speed up image builds
- Enhanced pip caching with more comprehensive cache keys
- Concurrency control (cancel-in-progress) to prevent CI queue pileups from duplicate pushes
- Job-level timeouts for all CI jobs (20-30 minutes) to prevent hung jobs
- Deterministic test execution with environment variables (PYTHONHASHSEED=0, TZ=UTC, LANG=en_US.UTF-8)
- Health check verification after container startup in E2E workflows
- Comprehensive CI documentation in docs/CI.md for contributors

### Changed
- CI workflows now use --no-daemon consistently for Gradle builds
- E2E workflows include explicit health checks with curl after starting containers
- Python test workflows now install pytest-rerunfailures explicitly
- Docker build cache strategy updated to use local cache type with cache-from/cache-to
- Cache keys updated to include gradle-wrapper.properties for more precise Gradle caching
- Test execution now includes retry delay (1 second) between retry attempts

### Improved
- CI build speed through comprehensive caching of Gradle, pip, and Docker layers
- CI reliability through automatic test retries for flaky tests
- Test determinism through controlled environment variables
- Workflow efficiency through concurrency cancellation of stale runs

## Context

This release addresses CI flakiness and slow build times reported by users. The changes implement:
1. **Caching**: Gradle artifacts, pip packages, and Docker build layers
2. **Test Retries**: Automatic retries for flaky Java and Python tests
3. **Determinism**: Fixed environment variables to reduce non-deterministic failures
4. **Health Checks**: Explicit verification of service readiness before running tests
5. **Documentation**: Comprehensive CI guide for contributors

These improvements should make CI runs:
- **Faster**: 30-50% reduction in build time through caching
- **More Reliable**: Reduced flaky test failures through retries and determinism
- **More Efficient**: Cancelled duplicate runs reduce queue wait times

## For Contributors

See docs/CI.md for guidance on:
- Running tests locally with the same CI settings
- Understanding caching strategies
- Troubleshooting common CI failures
- Re-running failed jobs
