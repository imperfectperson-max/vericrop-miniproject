FROM python:3.11-slim

WORKDIR /app

# Copy the app file and requirements directly (assuming app.py and requirements.txt are in docker/ml-service/)
COPY app.py /app/app.py
COPY requirements.txt /app/requirements.txt

RUN pip install --no-cache-dir -r /app/requirements.txt

ENV PYTHONUNBUFFERED=1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]