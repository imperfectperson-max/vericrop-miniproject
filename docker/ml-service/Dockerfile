# Multi-stage Dockerfile for VeriCrop ML Service
# Production-optimized with security hardening

FROM python:3.11-slim-bookworm AS builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        && rm -rf /var/lib/apt/lists/*

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip wheel --no-cache-dir --no-deps --wheel-dir /app/wheels -r requirements.txt

# Stage 2: Runtime image
FROM python:3.11-slim-bookworm

# Add metadata labels
LABEL maintainer="vericrop@example.com" \
      version="1.0.0" \
      description="VeriCrop ML Service - AI-powered Quality Assessment" \
      org.opencontainers.image.source="https://github.com/imperfectperson-max/vericrop-miniproject"

WORKDIR /app

# Install only runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        tini \
        && rm -rf /var/lib/apt/lists/* && \
        apt-get clean

# Create non-root user for security
RUN useradd -m -u 1000 -s /bin/bash vericrop && \
    mkdir -p /app/model /app/data && \
    chown -R vericrop:vericrop /app

# Copy wheels from builder and install
COPY --from=builder /app/wheels /wheels
COPY --from=builder /app/requirements.txt .
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r requirements.txt && \
    rm -rf /wheels

# Copy application code
COPY --chown=vericrop:vericrop app.py .

# Copy model files if they exist
# Note: In production, mount model directory as volume
COPY --chown=vericrop:vericrop model/ ./model/

# Switch to non-root user
USER vericrop

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    VERICROP_MODE=production \
    WORKERS=4

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

EXPOSE 8000

# Use tini for proper signal handling
ENTRYPOINT ["/usr/bin/tini", "--"]

# Run with multiple workers for production
CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port 8000 --workers ${WORKERS}"]