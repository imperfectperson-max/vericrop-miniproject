#!/bin/bash
# Script to validate Gradle wrapper download with retry logic
# This helps prevent CI failures due to transient network issues (HTTP 503, etc.)

set -e

MAX_ATTEMPTS="${MAX_ATTEMPTS:-3}"
RETRY_DELAY="${RETRY_DELAY:-5}"

echo "🔧 Validating Gradle wrapper download..."
echo "   Max attempts: $MAX_ATTEMPTS"
echo "   Retry delay: ${RETRY_DELAY}s"

attempt=0
until [ $attempt -ge $MAX_ATTEMPTS ]; do
  if ./gradlew --version; then
    echo "✅ Gradle wrapper downloaded and validated successfully"
    exit 0
  fi
  attempt=$((attempt+1))
  if [ $attempt -lt $MAX_ATTEMPTS ]; then
    echo "⚠️  Gradle download attempt $attempt failed, retrying in ${RETRY_DELAY}s..."
    sleep $RETRY_DELAY
  fi
done

echo "❌ Failed to download Gradle after $MAX_ATTEMPTS attempts"
exit 1
