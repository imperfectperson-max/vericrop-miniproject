# Custom Airflow image with VeriCrop dependencies
FROM apache/airflow:2.7.1-python3.11

# Switch to root to install system packages
USER root

# Install any system dependencies if needed
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Switch back to airflow user
USER airflow

# Install Python dependencies for VeriCrop DAGs
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

# Copy DAGs and plugins
COPY dags /opt/airflow/dags

# Default command will be overridden by docker-compose
CMD ["airflow", "webserver"]
