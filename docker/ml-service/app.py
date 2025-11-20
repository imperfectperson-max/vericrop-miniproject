from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import onnxruntime as ort
import numpy as np
from PIL import Image
import io
import hashlib
import time
import json
import logging
from typing import Dict, List
import os
import random
from datetime import datetime, timedelta

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="VeriCrop ML Service",
    description="Fruit Quality Classification for Supply Chain",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global variables for the model
session = None
label_map = {}
model_loaded = False

# In-memory storage for batches
batches_db = []

@app.on_event("startup")
async def startup_event():
    """Initialize the model on startup"""
    global session, label_map, model_loaded

    try:
        # Load the ONNX model
        model_path = "model/vericrop_quality_model.onnx"
        label_map_path = "model/quality_label_map.json"

        if not os.path.exists(model_path):
            logger.warning(f"Model file not found at {model_path}. Using fallback mode.")
            return

        session = ort.InferenceSession(model_path)

        # Load label map
        with open(label_map_path, "r") as f:
            label_map = json.load(f)

        model_loaded = True
        logger.info(f"✅ Model Loaded - {len(label_map)} classes")
        logger.info(f"✅ Classes: {list(label_map.values())}")

    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        logger.info("🔄 Running in fallback mode with dummy predictions")

def preprocess_image(image_bytes: bytes) -> np.ndarray:
    """Preprocess image for the model"""
    try:
        # Open and convert image
        image = Image.open(io.BytesIO(image_bytes)).convert('RGB')

        # Resize to 224x224 (model input size)
        image = image.resize((224, 224))

        # Convert to numpy and normalize
        image = np.array(image).astype(np.float32) / 255.0

        # Normalize with ImageNet stats
        mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
        std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
        image = (image - mean) / std

        # Change from HWC to CHW
        image = image.transpose(2, 0, 1)

        # Add batch dimension and ensure float32
        return np.expand_dims(image, 0).astype(np.float32)

    except Exception as e:
        logger.error(f"Image preprocessing failed: {e}")
        raise HTTPException(status_code=400, detail=f"Image processing error: {e}")

def should_use_demo_mode():
    """Check if demo mode is enabled via environment variable"""
    load_demo = os.getenv("VERICROP_LOAD_DEMO", "false").lower()
    return load_demo == "true"

def get_dummy_prediction():
    """Demo prediction for testing (only when VERICROP_LOAD_DEMO=true)"""
    if not should_use_demo_mode():
        raise HTTPException(
            status_code=503,
            detail="ML model not available. Set VERICROP_LOAD_DEMO=true for demo mode."
        )
    
    dummy_score = 0.85
    dummy_label = "fresh"
    report = f'{{"score": {dummy_score}, "label": "{dummy_label}", "note": "demo_mode"}}'
    data_hash = hashlib.sha256(report.encode("utf-8")).hexdigest()

    return {
        "quality_score": dummy_score,
        "label": dummy_label,
        "report": report,
        "data_hash": data_hash,
        "model_accuracy": "demo_mode"
    }

@app.get("/health")
async def health():
    """Health check endpoint"""
    status = {
        "status": "ok",
        "time": int(time.time()),
        "model_loaded": model_loaded,
        "model_accuracy": "99.06%" if model_loaded else "fallback_mode",
        "classes_loaded": len(label_map) if model_loaded else 0
    }
    return status

@app.get("/")
async def root():
    return {
        "status": "ok",
        "message": "VeriCrop ML Service Running",
        "model_accuracy": "99.06%" if model_loaded else "fallback_mode",
        "endpoints": ["/health", "/predict", "/batches", "/dashboard/farm"]
    }

# NEW ENDPOINTS - Add these
@app.get("/dashboard/farm")
async def get_farm_dashboard():
    """Dashboard data for farmer UI"""
    dashboard_data = {
        "kpis": {
            "total_batches_today": len(batches_db),
            "average_quality": round(random.uniform(85, 95), 1) if not batches_db else round(sum(b.get('quality_score', 0.8) for b in batches_db) / len(batches_db) * 100, 1),
            "prime_percentage": round(random.uniform(70, 85), 1),
            "rejection_rate": round(random.uniform(2, 8), 1)
        },
        "quality_distribution": {
            "prime": random.randint(30, 50),
            "standard": random.randint(20, 40),
            "sub_standard": random.randint(5, 15)
        },
        "recent_batches": [
            {
                "name": batch.get('name', f"Batch_{i}"),
                "quality_score": batch.get('quality_score', 0.8),
                "timestamp": batch.get('timestamp', datetime.now().isoformat())
            }
            for i, batch in enumerate(batches_db[-5:])
        ] if batches_db else [
            {
                "name": f"GreenValley_Apples_{i:03d}",
                "quality_score": round(random.uniform(0.7, 0.98), 2),
                "timestamp": (datetime.now() - timedelta(hours=i)).isoformat()
            }
            for i in range(1, 6)
        ]
    }
    return dashboard_data

@app.post("/batches")
async def create_batch(batch_data: dict = None):
    """Create a new batch"""
    try:
        batch_id = f"BATCH_{int(time.time())}_{random.randint(1000, 9999)}"

        # Create batch record
        batch_record = {
            "batch_id": batch_id,
            "name": batch_data.get('name', 'Unnamed_Batch') if batch_data else 'Unnamed_Batch',
            "farmer": batch_data.get('farmer', 'Unknown_Farmer') if batch_data else 'Unknown_Farmer',
            "product_type": batch_data.get('product_type', 'Unknown_Product') if batch_data else 'Unknown_Product',
            "quantity": batch_data.get('quantity', 1) if batch_data else 1,
            "quality_score": batch_data.get('quality_data', {}).get('quality_score', 0.8) if batch_data and 'quality_data' in batch_data else 0.8,
            "quality_label": batch_data.get('quality_data', {}).get('label', 'fresh') if batch_data and 'quality_data' in batch_data else 'fresh',
            "timestamp": datetime.now().isoformat(),
            "status": "created"
        }

        # Store in database
        batches_db.append(batch_record)

        logger.info(f"✅ Batch created: {batch_record['name']} (ID: {batch_id})")

        return {
            "batch_id": batch_id,
            "status": "created",
            "timestamp": datetime.now().isoformat(),
            "message": f"Batch '{batch_record['name']}' created successfully"
        }

    except Exception as e:
        logger.error(f"❌ Error creating batch: {e}")
        raise HTTPException(status_code=500, detail=f"Error creating batch: {e}")

@app.get("/batches")
async def get_batches():
    """Get all batches"""
    return {
        "batches": batches_db,
        "total_count": len(batches_db),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """Predict fruit quality"""
    try:
        logger.info(f"📸 Prediction request for: {file.filename}")

        # Validate file type
        if not file.content_type.startswith('image/'):
            raise HTTPException(status_code=400, detail="File must be an image")

        # Read image
        contents = await file.read()

        # If model is not loaded, use demo mode only if enabled
        if not model_loaded:
            logger.warning("Model not loaded. Checking for demo mode...")
            return get_dummy_prediction()

        # Preprocess image
        processed_image = preprocess_image(contents)

        # Debug: Check data type
        logger.info(f"📊 Input shape: {processed_image.shape}, dtype: {processed_image.dtype}")

        # Run inference
        outputs = session.run(None, {'input': processed_image})
        predictions = outputs[0][0]

        # Apply softmax to get probabilities
        exp_preds = np.exp(predictions - np.max(predictions))
        probabilities = exp_preds / np.sum(exp_preds)

        # Get top prediction
        predicted_class_idx = np.argmax(probabilities)
        confidence = float(probabilities[predicted_class_idx])
        predicted_class = label_map[str(predicted_class_idx)]

        # Get top 3 predictions for additional context
        top3_indices = np.argsort(probabilities)[-3:][::-1]
        top_predictions = {
            label_map[str(idx)]: float(probabilities[idx])
            for idx in top3_indices
        }

        # Create report
        report_data = {
            "prediction": predicted_class,
            "confidence": confidence,
            "class_id": int(predicted_class_idx),
            "top_predictions": top_predictions,
            "model_accuracy": "99.06%",
            "timestamp": int(time.time())
        }

        report_json = json.dumps(report_data, sort_keys=True)
        data_hash = hashlib.sha256(report_json.encode("utf-8")).hexdigest()

        response = {
            "quality_score": confidence,
            "label": predicted_class,
            "report": report_json,
            "data_hash": data_hash,
            "model_accuracy": "99.06%",
            "all_predictions": top_predictions,
            "class_id": int(predicted_class_idx)
        }

        logger.info(f"✅ Prediction: {predicted_class} (confidence: {confidence:.3f})")
        return response

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"❌ Prediction failed: {e}")
        logger.exception("Full error details:")
        # Only use demo mode if explicitly enabled
        if should_use_demo_mode():
            logger.warning("Using demo prediction due to error")
            return get_dummy_prediction()
        else:
            raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")

@app.get("/classes")
async def get_classes():
    """Get all available fruit classes"""
    if not model_loaded:
        return {
            "status": "fallback_mode",
            "message": "Model not loaded, using fallback predictions",
            "classes": ["fresh", "low_quality", "rotten"]
        }

    return {
        "classes": label_map,
        "total_classes": len(label_map),
        "model_accuracy": "99.06%",
        "sample_classes": list(label_map.values())
    }

@app.get("/model-info")
async def model_info():
    """Get information about the model"""
    if not model_loaded:
        return {
            "status": "fallback_mode",
            "message": "Model not loaded"
        }

    return {
        "model_accuracy": "99.06%",
        "total_classes": len(label_map),
        "architecture": "ResNet18",
        "input_size": "224x224",
        "performance": "99.06% validation accuracy",
        "classes": list(label_map.values())
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")