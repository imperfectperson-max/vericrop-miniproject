FROM python:3.11-slim

WORKDIR /app

# Install system dependencies required by PyTorch and Pillow
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ca-certificates \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
 && rm -rf /var/lib/apt/lists/*

# Upgrade pip, setuptools and wheel to ensure modern installer and wheel support
RUN pip install --upgrade pip setuptools wheel

# Copy requirements and install dependencies (requirements.txt includes the PyTorch CPU wheel index)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

# Create weights directory
RUN mkdir -p weights

# Expose port
