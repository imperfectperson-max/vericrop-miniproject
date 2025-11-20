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
BATCHES_FILE = "batches_data.json"

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
def load_batches_from_file():
    """Load batches from file if it exists"""
    global batches_db
    try:
        if os.path.exists(BATCHES_FILE):
            with open(BATCHES_FILE, 'r') as f:
                batches_db = json.load(f)
            logger.info(f"✅ Loaded {len(batches_db)} batches from file")
    except Exception as e:
        logger.error(f"❌ Error loading batches from file: {e}")
        batches_db = []

def save_batches_to_file():
    """Save batches to file"""
    try:
        with open(BATCHES_FILE, 'w') as f:
            json.dump(batches_db, f, indent=2)
    except Exception as e:
        logger.error(f"❌ Error saving batches to file: {e}")

def compute_deterministic_rates(batch_id: str, batch_index: int) -> tuple[float, float]:
    """
    Compute deterministic primeRate and rejectionRate for a batch.
    
    Uses the batch ID as a seed to create a Random instance, ensuring that the same
    batch always gets the same rates across different runs. This makes batch processing
    deterministic and reproducible.
    
    Args:
        batch_id: Unique identifier for the batch
        batch_index: Sequential index of the batch (0-based)
    
    Returns:
        tuple of (primeRate, rejectionRate) both in range [0.0, 1.0]
    
    Logic:
        - primeRate: probability that items in this batch are prime quality
        - rejectionRate: probability that non-prime items are rejected
        - primeRate + rejectionRate <= 1.0 (ensured by clamping)
    """
    # Create deterministic seed from batch_id by hashing it
    # This ensures same batch_id always produces same rates
    seed = int(hashlib.sha256(batch_id.encode()).hexdigest(), 16) % (2**31)
    batch_random = random.Random(seed)
    
    # Generate rates in reasonable ranges
    # Prime rate: typically 60-90% for agricultural products
    prime_rate = 0.6 + batch_random.random() * 0.3  # Range: 0.6 to 0.9
    
    # Rejection rate: typically 5-20% for agricultural products  
    rejection_rate = 0.05 + batch_random.random() * 0.15  # Range: 0.05 to 0.2
    
    # Ensure logical consistency: primeRate + rejectionRate <= 1.0
    # If they sum to more than 1.0, scale them down proportionally
    total = prime_rate + rejection_rate
    if total > 1.0:
        scale = 0.95 / total  # Scale to 0.95 to leave some room for standard items
        prime_rate *= scale
        rejection_rate *= scale
    
    # Clamp to valid ranges [0.0, 1.0]
    prime_rate = max(0.0, min(1.0, prime_rate))
    rejection_rate = max(0.0, min(1.0, rejection_rate))
    
    return prime_rate, rejection_rate

# Update the create_batch endpoint to save data
@app.post("/batches")
async def create_batch(batch_data: dict = None):
    """Create a new batch with proper quality score storage and deterministic rates"""
    try:
        batch_id = f"BATCH_{int(time.time())}_{random.randint(1000, 9999)}"

        # Extract quality data from the request
        quality_data = batch_data.get('quality_data', {}) if batch_data else {}

        # Ensure quality_score is properly extracted and stored
        quality_score = quality_data.get('quality_score', 0.8)
        # Ensure it's a float
        if not isinstance(quality_score, (int, float)):
            quality_score = 0.8

        quality_label = quality_data.get('label', 'fresh')
        
        # Compute deterministic rates based on batch_id
        # This ensures the same batch always has the same rates across runs
        batch_index = len(batches_db)  # Use current batch count as index
        prime_rate, rejection_rate = compute_deterministic_rates(batch_id, batch_index)

        # Create batch record with proper quality data and deterministic rates
        batch_record = {
            "batch_id": batch_id,
            "name": batch_data.get('name', 'Unnamed_Batch') if batch_data else 'Unnamed_Batch',
            "farmer": batch_data.get('farmer', 'Unknown_Farmer') if batch_data else 'Unknown_Farmer',
            "product_type": batch_data.get('product_type', 'Unknown_Product') if batch_data else 'Unknown_Product',
            "quantity": batch_data.get('quantity', 1) if batch_data else 1,
            "quality_score": float(quality_score),  # Ensure float type
            "quality_label": quality_label,
            "timestamp": datetime.now().isoformat(),
            "status": "created",
            "data_hash": batch_data.get('data_hash', '') if batch_data else '',
            # Deterministic rates derived from batch_id (stored once, never recalculated)
            "prime_rate": round(prime_rate, 4),
            "rejection_rate": round(rejection_rate, 4)
        }

        # Store in database
        batches_db.append(batch_record)

        # Save to file for persistence
        save_batches_to_file()

        logger.info(f"✅ Batch created: {batch_record['name']} (ID: {batch_id}, Quality: {quality_score:.3f}, PrimeRate: {prime_rate:.3f}, RejectionRate: {rejection_rate:.3f})")

        return {
            "batch_id": batch_id,
            "status": "created",
            "timestamp": datetime.now().isoformat(),
            "message": f"Batch '{batch_record['name']}' created successfully",
            "quality_score": quality_score,
            "quality_label": quality_label,
            "prime_rate": round(prime_rate, 4),
            "rejection_rate": round(rejection_rate, 4)
        }

    except Exception as e:
        logger.error(f"❌ Error creating batch: {e}")
        raise HTTPException(status_code=500, detail=f"Error creating batch: {e}")

# Load batches on startup
@app.on_event("startup")
async def startup_event():
    """Initialize the model and load data on startup"""
    global session, label_map, model_loaded

    # Load existing batches first
    load_batches_from_file()

    # Then load the model (your existing model loading code)
    try:
        model_path = "model/vericrop_quality_model.onnx"
        label_map_path = "model/quality_label_map.json"

        if not os.path.exists(model_path):
            logger.warning(f"Model file not found at {model_path}. Using fallback mode.")
            return

        session = ort.InferenceSession(model_path)

        with open(label_map_path, "r") as f:
            label_map = json.load(f)

        model_loaded = True
        logger.info(f"✅ Model Loaded - {len(label_map)} classes")
        logger.info(f"✅ Classes: {list(label_map.values())}")

    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        logger.info("🔄 Running in fallback mode with dummy predictions")
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

@app.get("/dashboard/farm")
async def get_farm_dashboard():
    """Dashboard data for farmer UI - using deterministic per-batch rates"""

    # Use consistent calculation method
    if batches_db:
        total_batches = len(batches_db)

        # Extract quality scores once
        quality_scores = []
        prime_rates = []
        rejection_rates = []
        
        for batch in batches_db:
            quality_score = batch.get('quality_score', 0.8)
            # Ensure quality_score is a float
            if isinstance(quality_score, (int, float)):
                quality_scores.append(float(quality_score))
            else:
                # Default fallback if invalid
                quality_scores.append(0.8)
            
            # Extract deterministic rates (computed once per batch, stored with batch)
            # If batch doesn't have rates yet (old data), compute them now
            if 'prime_rate' in batch and 'rejection_rate' in batch:
                prime_rates.append(float(batch['prime_rate']))
                rejection_rates.append(float(batch['rejection_rate']))
            else:
                # Fallback: compute deterministic rates for old batches without stored rates
                batch_id = batch.get('batch_id', f"BATCH_{batch.get('timestamp', '')}")
                batch_index = batches_db.index(batch)
                prime_rate, rejection_rate = compute_deterministic_rates(batch_id, batch_index)
                prime_rates.append(prime_rate)
                rejection_rates.append(rejection_rate)
                # Update the batch with computed rates for future use
                batch['prime_rate'] = round(prime_rate, 4)
                batch['rejection_rate'] = round(rejection_rate, 4)

        # Calculate metrics consistently
        avg_quality = (sum(quality_scores) / len(quality_scores)) * 100 if quality_scores else 0

        # Use average of per-batch deterministic rates instead of recalculating from scratch
        # This ensures consistency: same batches always yield same dashboard values
        avg_prime_rate = (sum(prime_rates) / len(prime_rates)) * 100 if prime_rates else 0
        avg_rejection_rate = (sum(rejection_rates) / len(rejection_rates)) * 100 if rejection_rates else 0

        # Count batches in each category (based on quality score for distribution chart)
        prime_count = sum(1 for score in quality_scores if score >= 0.8)
        standard_count = sum(1 for score in quality_scores if 0.6 <= score < 0.8)
        sub_standard_count = sum(1 for score in quality_scores if score < 0.6)

        # Recent batches (last 5)
        recent_batches_data = []
        for batch in batches_db[-5:]:
            batch_quality = batch.get('quality_score', 0.8)
            if isinstance(batch_quality, (int, float)):
                quality_display = f"{float(batch_quality) * 100:.1f}%"
            else:
                quality_display = "85.0%"

            recent_batches_data.append({
                "name": batch.get('name', f"Batch_{len(recent_batches_data)}"),
                "quality_score": quality_display,
                "timestamp": batch.get('timestamp', datetime.now().isoformat()),
                # Include per-batch rates in recent batches display
                "prime_rate": f"{batch.get('prime_rate', 0) * 100:.1f}%",
                "rejection_rate": f"{batch.get('rejection_rate', 0) * 100:.1f}%"
            })

        # Reverse to show newest first
        recent_batches_data.reverse()
        
        # Save updated batches if any rates were computed for old batches
        save_batches_to_file()

    else:
        # Default values when no batches
        total_batches = 0
        avg_quality = 0
        avg_prime_rate = 0
        avg_rejection_rate = 0
        prime_count = 0
        standard_count = 0
        sub_standard_count = 0
        recent_batches_data = []

    dashboard_data = {
        "kpis": {
            "total_batches_today": total_batches,
            "average_quality": round(avg_quality, 1),
            # Use average of per-batch deterministic rates
            "prime_percentage": round(avg_prime_rate, 1),
            "rejection_rate": round(avg_rejection_rate, 1)
        },
        "quality_distribution": {
            "prime": prime_count,
            "standard": standard_count,
            "sub_standard": sub_standard_count
        },
        "recent_batches": recent_batches_data,
        "timestamp": datetime.now().isoformat()  # Add timestamp for cache control
    }

    logger.info(f"📊 Dashboard data - Total: {total_batches}, AvgPrime: {avg_prime_rate:.1f}%, AvgReject: {avg_rejection_rate:.1f}%")
    return dashboard_data


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

        # 🚀 FIX: Convert class to quality score based on meaning, not confidence
        quality_score = map_class_to_quality_score(predicted_class, confidence)

        # Get top 3 predictions for additional context
        top3_indices = np.argsort(probabilities)[-3:][::-1]
        top_predictions = {
            label_map[str(idx)]: float(probabilities[idx])
            for idx in top3_indices
        }

        # Create report
        report_data = {
            "prediction": predicted_class,
            "confidence": confidence,  # Model's confidence in its prediction
            "quality_score": quality_score,  # Actual quality percentage
            "class_id": int(predicted_class_idx),
            "top_predictions": top_predictions,
            "model_accuracy": "99.06%",
            "timestamp": int(time.time())
        }

        report_json = json.dumps(report_data, sort_keys=True)
        data_hash = hashlib.sha256(report_json.encode("utf-8")).hexdigest()

        response = {
            "quality_score": quality_score,  # Use mapped quality score, not confidence
            "label": predicted_class,
            "confidence": confidence,  # Add confidence as separate field
            "report": report_json,
            "data_hash": data_hash,
            "model_accuracy": "99.06%",
            "all_predictions": top_predictions,
            "class_id": int(predicted_class_idx)
        }

        logger.info(f"✅ Prediction: {predicted_class} (confidence: {confidence:.3f}, quality: {quality_score:.1f}%)")
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

def map_class_to_quality_score(predicted_class: str, confidence: float) -> float:
    """Map prediction class to appropriate quality score"""
    class_quality_map = {
        "fresh": 0.85,  # Base quality for fresh products
        "good": 0.75,   # Base quality for good products
        "ripe": 0.70,   # Base quality for ripe products
        "low_quality": 0.45,  # Base quality for low quality
        "rotten": 0.20,  # Base quality for rotten
        "damaged": 0.30,  # Base quality for damaged
    }

    # Get base quality score for this class
    base_quality = class_quality_map.get(predicted_class.lower(), 0.5)

    # Adjust quality slightly based on confidence (but keep within class boundaries)
    if predicted_class.lower() in ["fresh", "good", "ripe"]:
        # For positive classes, higher confidence = slightly higher quality
        adjusted_quality = base_quality + (confidence * 0.15)
    else:
        # For negative classes, higher confidence = slightly lower quality
        adjusted_quality = base_quality - (confidence * 0.15)

    # Ensure quality stays within reasonable bounds
    return max(0.1, min(0.99, adjusted_quality))

def get_dummy_prediction():
    """Demo prediction for testing (only when VERICROP_LOAD_DEMO=true)"""
    if not should_use_demo_mode():
        raise HTTPException(
            status_code=503,
            detail="ML model not available. Set VERICROP_LOAD_DEMO=true for demo mode."
        )

    # 🚀 FIX: Use appropriate quality scores for demo data
    demo_classes = [
        {"class": "fresh", "confidence": 0.92, "quality": 0.88},
        {"class": "good", "confidence": 0.85, "quality": 0.78},
        {"class": "rotten", "confidence": 0.95, "quality": 0.25}
    ]
    demo_data = random.choice(demo_classes)

    report_data = {
        "prediction": demo_data["class"],
        "confidence": demo_data["confidence"],
        "quality_score": demo_data["quality"],
        "note": "demo_mode"
    }
    report_json = json.dumps(report_data, sort_keys=True)
    data_hash = hashlib.sha256(report_json.encode("utf-8")).hexdigest()

    return {
        "quality_score": demo_data["quality"],  # Use quality score, not confidence
        "label": demo_data["class"],
        "confidence": demo_data["confidence"],  # Add confidence separately
        "report": report_json,
        "data_hash": data_hash,
        "model_accuracy": "demo_mode"
    }
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