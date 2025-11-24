#!/bin/bash
#
# find-big-files.sh
# 
# Script to find and list the largest files in the repository working tree.
# This helps identify files that should be removed or migrated to Git LFS.
#
# Usage: ./scripts/find-big-files.sh [number_of_files]
#
# Arguments:
#   number_of_files - Number of largest files to display (default: 20)
#

set -e

# Configuration
NUM_FILES="${1:-20}"
MIN_SIZE_MB="${2:-1}"  # Minimum file size in MB to report

echo "========================================================================"
echo "VeriCrop Repository - Large Files Report"
echo "========================================================================"
echo ""
echo "Finding the top $NUM_FILES largest files (>= ${MIN_SIZE_MB}MB)..."
echo ""

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "Error: Not in a git repository"
    exit 1
fi

# Get repository root
REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

echo "Repository root: $REPO_ROOT"
echo ""

# Find large files in the working tree
echo "Largest files in working tree:"
echo "------------------------------------------------------------------------"
printf "%-60s %10s\n" "FILE PATH" "SIZE"
echo "------------------------------------------------------------------------"

# Find files, sort by size, and display
find . -type f -not -path './.git/*' -exec du -h {} + 2>/dev/null | \
    sort -rh | \
    head -n "$NUM_FILES" | \
    while IFS= read -r line; do
        SIZE=$(echo "$line" | awk '{print $1}')
        FILE=$(echo "$line" | awk '{$1=""; print $0}' | sed 's/^ //')
        
        # Convert size to MB for filtering
        SIZE_NUM=$(echo "$SIZE" | sed 's/[^0-9.]//g')
        SIZE_UNIT=$(echo "$SIZE" | sed 's/[0-9.]//g')
        
        # Convert to MB for comparison using awk
        case "$SIZE_UNIT" in
            K)
                SIZE_MB=$(awk "BEGIN {printf \"%.2f\", $SIZE_NUM / 1024}")
                ;;
            M)
                SIZE_MB=$SIZE_NUM
                ;;
            G)
                SIZE_MB=$(awk "BEGIN {printf \"%.2f\", $SIZE_NUM * 1024}")
                ;;
            *)
                SIZE_MB=0
                ;;
        esac
        
        # Only show files >= MIN_SIZE_MB using awk
        if [ "$(awk "BEGIN {print ($SIZE_MB >= $MIN_SIZE_MB)}")" = "1" ]; then
            printf "%-60s %10s\n" "$FILE" "$SIZE"
        fi
    done

echo "------------------------------------------------------------------------"
echo ""

# Calculate total size of large files (>5MB)
echo "Files larger than 5MB (should be considered for Git LFS or removal):"
echo "------------------------------------------------------------------------"
find . -type f -not -path './.git/*' -size +5M -exec ls -lh {} \; | while read -r line; do
    SIZE=$(echo "$line" | awk '{print $5}')
    FILE=$(echo "$line" | awk '{print $NF}')
    printf "%-60s %10s\n" "$FILE" "$SIZE"
done

LARGE_FILE_COUNT=$(find . -type f -not -path './.git/*' -size +5M | wc -l | tr -d ' ')
echo "------------------------------------------------------------------------"
echo "Total files larger than 5MB: $LARGE_FILE_COUNT"
echo ""

# Show repository size summary
echo "Repository size summary:"
echo "------------------------------------------------------------------------"
REPO_SIZE=$(du -sh . 2>/dev/null | awk '{print $1}')
GIT_SIZE=$(du -sh .git 2>/dev/null | awk '{print $1}')
# Calculate working tree size by finding all files excluding .git directory
WORKING_SIZE=$(find . -type f -not -path './.git/*' -exec du -ch {} + 2>/dev/null | grep total$ | awk '{print $1}')

echo "Total repository size:     $REPO_SIZE"
echo "Git history size (.git):   $GIT_SIZE"
echo "Working tree size:         $WORKING_SIZE"
echo ""

# Recommendations
echo "========================================================================"
echo "Recommendations:"
echo "========================================================================"
echo ""
if [ "$LARGE_FILE_COUNT" -gt 0 ]; then
    echo "⚠️  Found $LARGE_FILE_COUNT files larger than 5MB"
    echo ""
    echo "Actions to take:"
    echo "  1. Review the files listed above"
    echo "  2. Consider these options for large files:"
    echo "     - Migrate to Git LFS (for binary assets that need versioning)"
    echo "     - Move to external storage (S3, CDN, etc.)"
    echo "     - Use GitHub Release assets (for distribution)"
    echo "     - Generate at build time (if possible)"
    echo "  3. Remove large files from history using docs/CLEANUP_HISTORY.md"
    echo ""
else
    echo "✅ No files larger than 5MB found in working tree"
    echo ""
fi

echo "For more information:"
echo "  - docs/README_REPO_SIZE.md     - Repository size management guide"
echo "  - docs/CLEANUP_HISTORY.md      - How to clean up repository history"
echo "  - .gitattributes               - Git LFS configuration"
echo ""
echo "========================================================================"
