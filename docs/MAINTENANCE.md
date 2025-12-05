# VeriCrop Maintenance Guide

This document describes how to safely maintain the VeriCrop system, including how to delete Batch and Simulation records with proper backups.

## Overview

The maintenance system provides:
- **Safe deletion** of all Batch and Simulation records
- **Automatic backups** before any deletion
- **Multiple access methods**: HTTP API and CLI script
- **Safety gates** requiring explicit confirmation

## Quick Start

### Using the CLI Script

```bash
# Check current record counts (no deletion)
./scripts/clear_batches_and_simulations.sh

# Dry run - see what would be deleted
./scripts/clear_batches_and_simulations.sh --dry-run

# Actually delete all records (with backup)
./scripts/clear_batches_and_simulations.sh --confirm
```

### Using the HTTP API

```bash
# Check record counts
curl http://localhost:8080/api/maintenance/counts

# Dry run
curl -X POST http://localhost:8080/api/maintenance/dry-run

# Delete all records (requires confirmation token)
curl -X POST http://localhost:8080/api/maintenance/delete-all \
  -H "Content-Type: application/json" \
  -d '{"confirmation_token": "CONFIRM_DELETE_ALL_RECORDS"}'
```

## Safety Features

### Confirmation Requirements

Destructive operations require **one of**:

1. **Environment Variable**: Set `MAINTENANCE_MODE=true`
   ```bash
   MAINTENANCE_MODE=true ./scripts/clear_batches_and_simulations.sh --confirm
   ```

2. **Confirmation Token**: Include `CONFIRM_DELETE_ALL_RECORDS` in the request
   ```bash
   curl -X POST http://localhost:8080/api/maintenance/delete-all \
     -H "Content-Type: application/json" \
     -d '{"confirmation_token": "CONFIRM_DELETE_ALL_RECORDS"}'
   ```

### Automatic Backups

Before any deletion, the system automatically:
1. Fetches all records from the database
2. Creates a timestamped backup directory
3. Saves records as JSON files
4. Only then proceeds with deletion

## Backup Location and Format

### Directory Structure

```
backups/
└── {YYYYMMDD_HHmmss}/
    ├── manifest.json       # Backup metadata
    ├── simulations.json    # All simulation records
    └── batches.json        # All batch records
```

### Example Backup Files

**manifest.json**:
```json
{
  "backup_timestamp": "20241205_120000",
  "created_at": "2024-12-05T12:00:00",
  "simulations_count": 10,
  "batches_count": 50,
  "files": ["simulations.json", "batches.json"],
  "restore_instructions": "..."
}
```

**simulations.json**:
```json
{
  "count": 10,
  "backed_up_at": "2024-12-05T12:00:00",
  "records": [
    {
      "id": "uuid-here",
      "title": "Simulation 1",
      "status": "completed",
      "owner_user_id": 1,
      "supplier_user_id": 2,
      "consumer_user_id": 3,
      ...
    }
  ]
}
```

## API Reference

### GET /api/maintenance/counts
Get current record counts.

**Response:**
```json
{
  "simulations": 10,
  "batches": 50,
  "maintenance_mode_enabled": false,
  "timestamp": 1733400000000
}
```

### GET /api/maintenance/status
Get maintenance service status.

**Response:**
```json
{
  "service": "maintenance",
  "status": "UP",
  "maintenance_mode_enabled": false,
  "confirmation_token_required": true,
  "confirmation_token_hint": "Use 'CONFIRM_DELETE_ALL_RECORDS' to confirm..."
}
```

### POST /api/maintenance/dry-run
Preview deletion without executing.

**Response:**
```json
{
  "dry_run": true,
  "would_delete_simulations": 10,
  "would_delete_batches": 50,
  "message": "Dry run completed. No records were deleted."
}
```

### POST /api/maintenance/delete-all
Delete all records with backup.

**Request:**
```json
{
  "confirmation_token": "CONFIRM_DELETE_ALL_RECORDS"
}
```

**Success Response:**
```json
{
  "success": true,
  "message": "Maintenance operation completed successfully",
  "simulations_deleted": 10,
  "batches_deleted": 50,
  "backup_path": "/path/to/backups/20241205_120000",
  "simulations_backed_up": 10,
  "batches_backed_up": 50
}
```

**Failure Response (no confirmation):**
```json
{
  "success": false,
  "message": "Maintenance operation not allowed. Set MAINTENANCE_MODE=true or provide confirmation token."
}
```

### GET /api/maintenance/backups
List all available backups.

**Response:**
```json
{
  "backups": ["20241205_120000", "20241204_150000"],
  "count": 2,
  "backup_directory": "backups/"
}
```

## Restoring from Backup

To restore records from a backup:

1. **Locate the backup directory**:
   ```bash
   ls -la backups/
   ```

2. **Review the manifest**:
   ```bash
   cat backups/{timestamp}/manifest.json
   ```

3. **Manual SQL restore** (PostgreSQL):
   ```sql
   -- Parse JSON and insert into tables
   -- This requires custom SQL based on your schema
   ```

4. **Programmatic restore** (recommended):
   ```java
   // Use ObjectMapper to read JSON files
   // Insert records using DAO methods
   ```

**Note**: A full programmatic restore script is planned for a future release.

## Troubleshooting

### "API is not available"

Ensure the VeriCrop application is running:
```bash
./gradlew :vericrop-gui:bootRun
```

### "Maintenance operation not allowed"

Provide the confirmation token OR set the environment variable:
```bash
# Option 1: Environment variable
MAINTENANCE_MODE=true ./scripts/clear_batches_and_simulations.sh --confirm

# Option 2: API with token
curl -X POST http://localhost:8080/api/maintenance/delete-all \
  -H "Content-Type: application/json" \
  -d '{"confirmation_token": "CONFIRM_DELETE_ALL_RECORDS"}'
```

### Backup directory not created

Ensure the application has write permissions to the `backups/` directory:
```bash
mkdir -p backups
chmod 755 backups
```

## Best Practices

1. **Always run dry-run first** to see what would be deleted
2. **Keep backups** until you're sure you don't need the data
3. **Use in maintenance windows** when users aren't actively using the system
4. **Log the operation** for audit purposes (automatic in system logs)
5. **Verify backup integrity** after creation by checking file sizes and JSON validity

## Related Documentation

- [README.md](../README.md) - Project overview
- [DEPLOYMENT.md](../DEPLOYMENT.md) - Deployment guide
- [CHANGELOG.md](../CHANGELOG.md) - Change history
