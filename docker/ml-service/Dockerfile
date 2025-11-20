FROM python:3.11-slim-bookworm

WORKDIR /app

# Install system dependencies including curl for healthcheck
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create model directory first
RUN mkdir -p model

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies (including onnxruntime for production)
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

# Copy model files (ONNX model and label map) if they exist
# Note: In production, mount model directory as volume or use fetch script before building
COPY model/ ./model/

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV VERICROP_MODE=dev

# Health check using curl
# In production mode, this will fail if model is not loaded
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]