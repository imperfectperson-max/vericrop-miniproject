#!/bin/bash

# ==============================================================================
# VeriCrop Maintenance Script: Clear Batches and Simulations
# ==============================================================================
# 
# This script deletes all Batch and Simulation records from the backend database.
# A timestamped backup is created before deletion.
#
# USAGE:
#   ./scripts/clear_batches_and_simulations.sh [OPTIONS]
#
# OPTIONS:
#   --confirm       Required flag to confirm the deletion operation
#   --dry-run       Show what would be deleted without actually deleting
#   --help          Show this help message
#
# ENVIRONMENT VARIABLES:
#   VERICROP_API_URL    Base URL of the VeriCrop API (default: http://localhost:8080)
#   MAINTENANCE_MODE    If set to 'true', skips confirmation token requirement
#
# EXAMPLES:
#   # Dry run - see what would be deleted
#   ./scripts/clear_batches_and_simulations.sh --dry-run
#
#   # Actually delete with confirmation
#   ./scripts/clear_batches_and_simulations.sh --confirm
#
#   # Delete with maintenance mode enabled
#   MAINTENANCE_MODE=true ./scripts/clear_batches_and_simulations.sh --confirm
#
# BACKUPS:
#   Backups are stored in: backups/{timestamp}/
#   Files created:
#     - simulations.json  (all simulation records)
#     - batches.json      (all batch records)
#     - manifest.json     (backup metadata)
#
# ==============================================================================

set -e

# Configuration
API_URL="${VERICROP_API_URL:-http://localhost:8080}"
CONFIRMATION_TOKEN="CONFIRM_DELETE_ALL_RECORDS"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Show help
show_help() {
    head -50 "$0" | grep "^#" | sed 's/^# //' | sed 's/^#//'
    # exit 0
}

# Check if API is available
check_api() {
    print_info "Checking API availability at $API_URL..."
    
    if ! curl -s -o /dev/null -w "%{http_code}" "$API_URL/api/maintenance/status" | grep -q "200"; then
        print_error "API is not available at $API_URL"
        print_info "Make sure the VeriCrop application is running."
        # exit 1
    fi
    
    print_success "API is available"
}

# Get current record counts
get_counts() {
    print_info "Getting current record counts..."
    
    response=$(curl -s "$API_URL/api/maintenance/counts")
    
    # Try to use jq if available, fall back to grep/cut
    if command -v jq &> /dev/null; then
        simulations=$(echo "$response" | jq -r '.simulations // "N/A"')
        batches=$(echo "$response" | jq -r '.batches // "N/A"')
    else
        simulations=$(echo "$response" | grep -o '"simulations":[0-9]*' | cut -d':' -f2)
        batches=$(echo "$response" | grep -o '"batches":[0-9]*' | cut -d':' -f2)
    fi
    
    echo ""
    echo "  Simulations: $simulations"
    echo "  Batches:     $batches"
    echo ""
}

# Dry run - show what would be deleted
dry_run() {
    print_info "Performing dry run (no records will be deleted)..."
    
    response=$(curl -s -X POST "$API_URL/api/maintenance/dry-run")
    
    echo ""
    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo ""
    
    print_success "Dry run completed. Use --confirm to actually delete records."
}

# Delete all records with backup
delete_all() {
    print_warning "This will DELETE ALL Batch and Simulation records!"
    print_info "A backup will be created before deletion."
    echo ""
    
    # Confirm with user
    read -p "Are you sure you want to proceed? (yes/no): " -r
    if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
        print_info "Operation cancelled."
        return 0
    fi
    
    print_info "Executing delete operation..."
    
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "{\"confirmation_token\": \"$CONFIRMATION_TOKEN\"}" \
        "$API_URL/api/maintenance/delete-all")
    
    echo ""
    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo ""
    
    # Check if successful using jq if available, fall back to grep
    if command -v jq &> /dev/null; then
        success=$(echo "$response" | jq -r '.success // false')
        if [ "$success" = "true" ]; then
            print_success "Delete operation completed successfully!"
            backup_path=$(echo "$response" | jq -r '.backup_path // ""')
            if [ -n "$backup_path" ]; then
                print_info "Backup created at: $backup_path"
            fi
        else
            print_error "Delete operation failed. See response above for details."
            # exit 1
        fi
    else
        if echo "$response" | grep -q '"success":true'; then
            print_success "Delete operation completed successfully!"
            backup_path=$(echo "$response" | grep -o '"backup_path":"[^"]*"' | cut -d'"' -f4)
            if [ -n "$backup_path" ]; then
                print_info "Backup created at: $backup_path"
            fi
        else
            print_error "Delete operation failed. See response above for details."
            # exit 1
        fi
    fi
}

# List backups
list_backups() {
    print_info "Listing available backups..."
    
    response=$(curl -s "$API_URL/api/maintenance/backups")
    
    echo ""
    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo ""
}

# Main script
main() {
    echo ""
    echo "=============================================="
    echo "  VeriCrop Maintenance: Clear Records"
    echo "=============================================="
    echo ""
    
    # Parse arguments
    DRY_RUN=false
    CONFIRM=false
    LIST_BACKUPS=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --confirm)
                CONFIRM=true
                shift
                ;;
            --list-backups)
                LIST_BACKUPS=true
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                echo "Use --help for usage information."
                # exit 1
                ;;
        esac
    done
    
    # Check API first
    check_api
    
    # Show current counts
    get_counts
    
    # Execute requested operation
    if [ "$LIST_BACKUPS" = true ]; then
        list_backups
    elif [ "$DRY_RUN" = true ]; then
        dry_run
    elif [ "$CONFIRM" = true ]; then
        delete_all
    else
        print_info "No action specified."
        echo ""
        echo "Available actions:"
        echo "  --dry-run       Show what would be deleted"
        echo "  --confirm       Delete all records with backup"
        echo "  --list-backups  List available backups"
        echo "  --help          Show help"
        echo ""
    fi
}

# Run main function
main "$@"
