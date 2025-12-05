# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Maintenance Service**: New `MaintenanceService` for safe deletion of Batch and Simulation records
  - Creates timestamped JSON backups before any deletion
  - Requires confirmation via environment variable (`MAINTENANCE_MODE=true`) or explicit token
  - Idempotent operations with detailed logging
- **Maintenance API**: New REST endpoints under `/api/maintenance/`
  - `GET /api/maintenance/counts` - Get current record counts
  - `GET /api/maintenance/status` - Get maintenance service status
  - `GET /api/maintenance/backups` - List available backups
  - `POST /api/maintenance/dry-run` - Preview deletion without executing
  - `POST /api/maintenance/delete-all` - Delete all records with backup
- **CLI Script**: `scripts/clear_batches_and_simulations.sh` for command-line maintenance
  - Supports `--dry-run`, `--confirm`, `--list-backups` options
  - Colorized output and interactive confirmation
- **Documentation**: `docs/MAINTENANCE.md` with complete usage guide

### Fixed
- **Simulation Status Bug**: Fixed issue where batches could get stuck in `in_transit` status
  - Refactored `SimulationPersistenceService.createBatch()` to use transactional batch creation
  - Added `createBatchWithTransaction()` method in `SimulationBatchDao` that atomically creates batch with correct status
  - Status is now set within the same database transaction as batch creation
  - Added detailed logging for batch creation and status transitions
  - Added new status constants: `STATUS_SAVING`, `STATUS_SAVED`, `STATUS_SAVE_FAILED`

### Changed
- `SimulationBatchDao`: Added `findAll()` and `deleteAll()` methods for maintenance operations
- `SimulationDao`: Added `findAll()` and `deleteAll()` methods for maintenance operations
- Enhanced logging in `SimulationPersistenceService` for better debugging of save operations

## [1.0.0] - Previous Release

### Initial Features
- VeriCrop supply chain tracking and simulation platform
- Multi-user simulation with supplier/consumer relationships
- Real-time SSE (Server-Sent Events) for live state updates
- Kafka integration for event sourcing
- PostgreSQL database with automatic schema initialization
- JWT-based authentication
- QR code generation for batch tracking
- PDF report generation
- REST API for simulations, batches, and analytics
