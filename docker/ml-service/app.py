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
from datetime import datetime

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
BATCHES_FILE = "batches_data.json"

# Constants
NORMALIZATION_TOLERANCE = 0.001  # Tolerance for floating point comparison in normalization

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

def compute_quality_metrics(classification: str, quality_score: float) -> dict:
    """
    Compute prime_rate, low_quality_rate, and rejection_rate based on classification and quality score.
    
    This mirrors the frontend calculation logic in ProducerController.calculateQualityMetrics().
    The algorithm uses the classification label to determine the dominant rate:
    
    - FRESH: prime% = 80 + quality% * 20, remainder distributed (80% low_quality, 20% rejection)
    - LOW_QUALITY: low_quality% = 80 + quality% * 20, remainder distributed (80% prime, 20% rejection)  
    - ROTTEN: rejection% = 80 + quality% * 20, remainder distributed (80% low_quality, 20% prime)
    
    Args:
        classification: Quality classification label (fresh, low_quality, rotten).
                       If None or empty, defaults to "FRESH" for backward compatibility.
        quality_score: Quality score from prediction (0.0 to 1.0)
    
    Returns:
        dict with keys: prime_rate, low_quality_rate, rejection_rate (all 0.0 to 1.0)
    
    Note:
        When classification is None or empty, the function defaults to FRESH behavior
        for backward compatibility with older batch data that may lack classification.
    
    Reference:
        Frontend source: vericrop-gui/src/main/java/org/vericrop/gui/ProducerController.java
        Method: calculateQualityMetrics()
    """
    metrics = {}
    
    # Convert quality score to percentage (0-100)
    quality_percent = quality_score * 100.0
    # Default to FRESH for None/empty classification for backward compatibility
    classification_upper = classification.upper() if classification else "FRESH"
    
    if classification_upper == "FRESH":
        # prime% = 80 + quality% * 20
        prime_rate = 80.0 + (quality_percent * 0.2)
        prime_rate = min(prime_rate, 100.0)  # Cap at 100%
        remainder = 100.0 - prime_rate
        
        metrics["prime_rate"] = prime_rate / 100.0
        metrics["low_quality_rate"] = (remainder * 0.8) / 100.0
        metrics["rejection_rate"] = (remainder * 0.2) / 100.0
        
    elif classification_upper == "LOW_QUALITY":
        # low_quality% = 80 + quality% * 20
        low_quality_rate = 80.0 + (quality_percent * 0.2)
        low_quality_rate = min(low_quality_rate, 100.0)
        remainder = 100.0 - low_quality_rate
        
        metrics["low_quality_rate"] = low_quality_rate / 100.0
        metrics["prime_rate"] = (remainder * 0.8) / 100.0
        metrics["rejection_rate"] = (remainder * 0.2) / 100.0
        
    elif classification_upper == "ROTTEN":
        # rejection% = 80 + quality% * 20
        rejection_rate = 80.0 + (quality_percent * 0.2)
        rejection_rate = min(rejection_rate, 100.0)
        remainder = 100.0 - rejection_rate
        
        metrics["rejection_rate"] = rejection_rate / 100.0
        metrics["low_quality_rate"] = (remainder * 0.8) / 100.0
        metrics["prime_rate"] = (remainder * 0.2) / 100.0
        
    else:
        # Fallback for unknown classifications (e.g., good, ripe, damaged)
        # Use quality score as prime rate with remainder distributed
        metrics["prime_rate"] = quality_score
        metrics["low_quality_rate"] = (1.0 - quality_score) * 0.7
        metrics["rejection_rate"] = (1.0 - quality_score) * 0.3
    
    # Normalize to ensure exact 100% total
    return normalize_metrics(metrics)


def normalize_metrics(metrics: dict) -> dict:
    """
    Normalize metrics to ensure they sum to 1.0 (100%).
    
    Args:
        metrics: dict with rate values
    
    Returns:
        dict with normalized rate values
    
    Note:
        Uses NORMALIZATION_TOLERANCE constant for floating point comparison.
    """
    total = sum(metrics.values())
    
    if total > 0 and abs(total - 1.0) > NORMALIZATION_TOLERANCE:
        factor = 1.0 / total
        metrics = {k: v * factor for k, v in metrics.items()}
    
    return metrics


def compute_quality_based_rates(quality_score: float, classification: str = "fresh") -> tuple[float, float]:
    """
    Compute primeRate and rejectionRate based on classification and quality score.
    
    This is a convenience wrapper around compute_quality_metrics() that returns
    just the prime_rate and rejection_rate tuple for backward compatibility.

    Args:
        quality_score: The actual quality score from prediction (0.0 to 1.0)
        classification: Quality classification label (fresh, low_quality, rotten)

    Returns:
        tuple of (primeRate, rejectionRate) both in range [0.0, 1.0]
    """
    metrics = compute_quality_metrics(classification, quality_score)
    return metrics["prime_rate"], metrics["rejection_rate"]

# Update the create_batch endpoint to save data
@app.post("/batches")
async def create_batch(batch_data: dict = None):
    """
    Create a new batch with proper quality score storage and quality-based rates.
    
    The calculation logic mirrors the frontend algorithm in ProducerController.calculateQualityMetrics().
    This ensures consistency between backend and frontend rate calculations.
    
    Request body:
        batch_data: dict containing:
            - name: str (optional, defaults to 'Unnamed_Batch')
            - farmer: str (optional, defaults to 'Unknown_Farmer')
            - product_type: str (optional, defaults to 'Unknown_Product')
            - quantity: int (optional, defaults to 1)
            - quality_data: dict with:
                - quality_score: float (0.0-1.0, optional, defaults to 0.8)
                - label: str (fresh/low_quality/rotten, optional, defaults to 'fresh')
            - data_hash: str (optional)
    
    Returns:
        dict with batch_id, status, timestamp, message, quality_score, prime_rate, 
        rejection_rate, quality_label, and data_hash
    """
    try:
        # Log incoming request for debugging
        logger.info(f"📥 Batch creation request received: {batch_data}")
        
        # Validate batch_data presence
        if batch_data is None:
            logger.warning("⚠️ No batch data provided, using defaults")
            batch_data = {}
        
        batch_id = f"BATCH_{int(time.time())}_{int(time.time() * 1000) % 10000}"

        # Extract quality data from the request or use defaults
        quality_data = batch_data.get('quality_data', {})
        if not isinstance(quality_data, dict):
            logger.warning(f"⚠️ Invalid quality_data type: {type(quality_data)}, using defaults")
            quality_data = {}

        # Get and validate quality score
        quality_score = quality_data.get('quality_score', 0.8)
        if not isinstance(quality_score, (int, float)):
            logger.warning(f"⚠️ Invalid quality_score type: {type(quality_score)}, using default 0.8")
            quality_score = 0.8
        
        # Clamp quality score to valid range
        quality_score = max(0.0, min(1.0, float(quality_score)))

        # Get and validate quality label
        quality_label = quality_data.get('label', 'fresh')
        if not isinstance(quality_label, str) or not quality_label.strip():
            logger.warning(f"⚠️ Invalid quality_label: {quality_label}, using default 'fresh'")
            quality_label = 'fresh'
        quality_label = quality_label.strip().lower()

        # Compute rates based on classification and quality score
        # This mirrors the frontend calculation logic in ProducerController.calculateQualityMetrics()
        logger.debug(f"📊 Computing rates for classification='{quality_label}', quality_score={quality_score:.3f}")
        prime_rate, rejection_rate = compute_quality_based_rates(quality_score, quality_label)
        
        # Validate computed rates
        if not (0.0 <= prime_rate <= 1.0) or not (0.0 <= rejection_rate <= 1.0):
            logger.error(f"❌ Invalid computed rates: prime_rate={prime_rate}, rejection_rate={rejection_rate}")
            raise ValueError(f"Computed rates out of valid range: prime_rate={prime_rate}, rejection_rate={rejection_rate}")

        # Create batch record with proper quality data and quality-based rates
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
            # Quality-based rates derived from classification and quality score
            "prime_rate": round(prime_rate, 4),
            "rejection_rate": round(rejection_rate, 4)
        }

        # Store in database
        batches_db.append(batch_record)

        # Save to file for persistence
        save_batches_to_file()

        logger.info(f"✅ Batch created: {batch_record['name']} (ID: {batch_id}, "
                   f"Classification: {quality_label}, Quality: {quality_score:.3f}, "
                   f"PrimeRate: {prime_rate:.4f}, RejectionRate: {rejection_rate:.4f})")

        # Return ALL required fields including quality_score, prime_rate, and rejection_rate
        return {
            "batch_id": batch_id,
            "status": "created",
            "timestamp": datetime.now().isoformat(),
            "message": f"Batch '{batch_record['name']}' created successfully",
            # CRITICAL: Include these fields that the frontend expects
            "quality_score": float(quality_score),
            "prime_rate": round(prime_rate, 4),
            "rejection_rate": round(rejection_rate, 4),
            "quality_label": quality_label,
            "data_hash": batch_data.get('data_hash', '') if batch_data else ''
        }

    except ValueError as e:
        # Handle validation errors with 400 Bad Request
        logger.error(f"❌ Validation error creating batch: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid batch data: {e}")
    except Exception as e:
        # Handle unexpected errors with 500 Internal Server Error
        logger.error(f"❌ Error creating batch: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Error creating batch: {e}")

# Load batches on startup
@app.on_event("startup")
async def startup_event():
    """
    Initialize the model and load data on startup.
    
    Model Loading Priority:
    1. ONNX model (model/vericrop_quality_model.onnx) - PREFERRED for production
       - Cross-platform compatibility
       - Optimized inference performance
       - No PyTorch dependency required
    
    2. PyTorch TorchScript model (model/vericrop_quality_model_scripted.pt) - FALLBACK
       - Used when ONNX not available
       - Requires PyTorch installed
       - Good for development
    
    3. Demo Mode - LAST RESORT (only if VERICROP_LOAD_DEMO=true)
       - Returns deterministic demo predictions
       - Allows testing without model files
       - Not allowed in PROD mode unless explicitly enabled
    
    Environment Variables:
    - VERICROP_MODE: "dev" (default) or "prod"
      - In PROD mode, service fails startup if no model found
      - In DEV mode, allows running without model if VERICROP_LOAD_DEMO=true
    
    - VERICROP_LOAD_DEMO: "true" or "false" (default)
      - When true, enables demo predictions when model not available
      - Can be used in PROD mode if needed for testing
    """
    global session, label_map, model_loaded

    # Load existing batches first
    load_batches_from_file()

    # Determine production mode
    vericrop_mode = os.getenv("VERICROP_MODE", "dev").lower()
    is_production = vericrop_mode == "prod"

    # Then load the model - prefer ONNX, fallback to PyTorch
    try:
        onnx_model_path = "model/vericrop_quality_model.onnx"
        pytorch_model_path = "model/vericrop_quality_model_scripted.pt"
        label_map_path = "model/quality_label_map.json"

        # Try ONNX first (preferred for production)
        if os.path.exists(onnx_model_path):
            try:
                logger.info(f"📦 Loading ONNX model from {onnx_model_path}...")
                session = ort.InferenceSession(onnx_model_path)
                
                # Load label map
                if os.path.exists(label_map_path):
                    with open(label_map_path, "r") as f:
                        label_map = json.load(f)
                else:
                    # Use default label map if not found
                    logger.warning("⚠️  Label map not found, using default mapping")
                    label_map = {"0": "fresh", "1": "good", "2": "ripe", "3": "low_quality", "4": "rotten"}
                
                model_loaded = True
                logger.info(f"✅ ONNX Model Loaded Successfully - {len(label_map)} classes")
                logger.info(f"✅ Classes: {list(label_map.values())}")
                logger.info(f"✅ Mode: {vericrop_mode.upper()}")
                return
            except Exception as e:
                logger.warning(f"⚠️  Failed to load ONNX model: {e}")
                if is_production:
                    raise
                logger.info("🔄 Attempting PyTorch fallback...")
        
        # Fallback to PyTorch if ONNX not available
        if os.path.exists(pytorch_model_path):
            try:
                import torch
                logger.info(f"📦 Loading PyTorch scripted model from {pytorch_model_path}...")
                torch_model = torch.jit.load(pytorch_model_path)
                torch_model.eval()
                
                # Store in session variable (will need special handling in predict)
                session = torch_model
                
                # Load or create label map
                if os.path.exists(label_map_path):
                    with open(label_map_path, "r") as f:
                        label_map = json.load(f)
                else:
                    logger.warning("⚠️  Label map not found, using default mapping")
                    label_map = {"0": "fresh", "1": "good", "2": "ripe", "3": "low_quality", "4": "rotten"}
                
                model_loaded = True
                logger.info(f"✅ PyTorch Model Loaded Successfully - {len(label_map)} classes")
                logger.info(f"✅ Classes: {list(label_map.values())}")
                logger.info(f"✅ Mode: {vericrop_mode.upper()} (PyTorch fallback)")
                return
            except Exception as e:
                logger.warning(f"⚠️  Failed to load PyTorch model: {e}")
                if is_production:
                    raise

        # No models found
        if is_production:
            logger.error(f"❌ CRITICAL: No model files found in PRODUCTION mode")
            logger.error(f"❌ Looked for: {onnx_model_path} or {pytorch_model_path}")
            logger.error("❌ Please provide ONNX model (preferred) or PyTorch scripted model")
            raise RuntimeError(f"Production startup failed: No model files found")
        else:
            logger.warning(f"⚠️  No model files found. Running in DEV mode with demo fallback.")
            logger.warning(f"⚠️  Set VERICROP_LOAD_DEMO=true to enable demo predictions")
            return

    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        if is_production:
            logger.error("❌ Cannot start in PRODUCTION mode without model")
            raise RuntimeError(f"Production startup failed: {e}")
        logger.info("🔄 Running in DEV mode - demo predictions available if VERICROP_LOAD_DEMO=true")

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
            detail="ML model not available. Set VERICROP_LOAD_DEMO=true for demo mode or provide ONNX model."
        )

    # Use consistent demo data without randomization
    dummy_score = 0.85
    dummy_label = "fresh"
    dummy_confidence = 0.92
    
    # Generate deterministic demo data_hash
    demo_report = {
        "quality_score": dummy_score,
        "label": dummy_label,
        "confidence": dummy_confidence,
        "timestamp": "demo"
    }
    demo_hash = hashlib.sha256(json.dumps(demo_report, sort_keys=True).encode("utf-8")).hexdigest()

    # Return response matching the exact contract from README
    return {
        "quality_score": dummy_score,
        "quality_label": dummy_label,
        "confidence": dummy_confidence,
        "metadata": {
            "color_consistency": 0.88,
            "size_uniformity": 0.85,
            "defect_density": 0.02
        },
        "data_hash": demo_hash,
        "label": dummy_label,  # Alias for backward compatibility
        "all_predictions": {
            "fresh": 0.92,
            "low_quality": 0.05,
            "rotten": 0.03
        }
    }

@app.get("/health")
async def health():
    """Health check endpoint - fails in prod mode if model not loaded"""
    vericrop_mode = os.getenv("VERICROP_MODE", "dev").lower()
    is_production = vericrop_mode == "prod"
    demo_allowed = should_use_demo_mode()
    
    # In production mode, service is unhealthy if model not loaded (unless demo explicitly enabled)
    if is_production and not model_loaded and not demo_allowed:
        raise HTTPException(
            status_code=503,
            detail="Service unavailable: Model not loaded in production mode"
        )
    
    status = {
        "status": "ok",  # Always "ok" when request succeeds (503 raised above if unhealthy)
        "time": int(time.time()),
        "mode": vericrop_mode,
        "model_loaded": model_loaded,
        "model_accuracy": "99.06%" if model_loaded else "demo_mode",
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
    """
    Dashboard data for farmer UI.
    
    Returns actual counts for prime/rejected samples to enable consistent rate calculation:
    - prime_rate = prime_count / total_count
    - rejection_rate = rejected_count / total_count  
    - total_count = prime_count + rejected_count
    
    When total_count == 0, rates should be 0.0 (not NaN/inf).
    """

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

            # Extract quality-based rates (computed from classification and quality score)
            # If batch doesn't have rates yet (old data), compute them now
            if 'prime_rate' in batch and 'rejection_rate' in batch:
                prime_rates.append(float(batch['prime_rate']))
                rejection_rates.append(float(batch['rejection_rate']))
            else:
                # Fallback: compute quality-based rates for old batches without stored rates
                quality_score = batch.get('quality_score', 0.8)
                quality_label = batch.get('quality_label', 'fresh')
                prime_rate, rejection_rate = compute_quality_based_rates(quality_score, quality_label)
                prime_rates.append(prime_rate)
                rejection_rates.append(rejection_rate)
                # Update the batch with computed rates for future use
                batch['prime_rate'] = round(prime_rate, 4)
                batch['rejection_rate'] = round(rejection_rate, 4)

        # Calculate metrics consistently
        avg_quality = (sum(quality_scores) / len(quality_scores)) * 100 if quality_scores else 0

        # Use average of per-batch quality-based rates
        avg_prime_rate = (sum(prime_rates) / len(prime_rates)) * 100 if prime_rates else 0
        avg_rejection_rate = (sum(rejection_rates) / len(rejection_rates)) * 100 if rejection_rates else 0

        # Count batches in each category (based on quality score for distribution chart)
        # These represent the actual prime/standard/rejected counts
        prime_count = sum(1 for score in quality_scores if score >= 0.8)
        standard_count = sum(1 for score in quality_scores if 0.6 <= score < 0.8)
        sub_standard_count = sum(1 for score in quality_scores if score < 0.6)
        
        # For the canonical formula: total = prime + rejected (standard items are not counted)
        rejected_count = sub_standard_count
        total_count = prime_count + rejected_count

        # Recent batches (last 5)
        recent_batches_data = []
        for batch in batches_db[-5:]:
            batch_quality = batch.get('quality_score', 0.8)
            if isinstance(batch_quality, (int, float)):
                quality_display = f"{float(batch_quality) * 100:.1f}%"
            else:
                quality_display = "85.0%"

            recent_batches_data.append({
                "batch_id": batch.get('batch_id', 'UNKNOWN'),  # Include batch_id for UI selection
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
        rejected_count = 0
        total_count = 0
        recent_batches_data = []

    dashboard_data = {
        "kpis": {
            "total_batches_today": total_batches,
            "average_quality": round(avg_quality, 1),
            # Use average of per-batch quality-based rates
            "prime_percentage": round(avg_prime_rate, 1),
            "rejection_rate": round(avg_rejection_rate, 1)
        },
        # Include actual counts for consistent rate calculation in the GUI
        "counts": {
            "prime_count": prime_count,
            "rejected_count": rejected_count,
            "total_count": total_count
        },
        "quality_distribution": {
            "prime": prime_count,
            "standard": standard_count,
            "sub_standard": sub_standard_count
        },
        "recent_batches": recent_batches_data,
        "timestamp": datetime.now().isoformat()  # Add timestamp for cache control
    }

    logger.info(f"📊 Dashboard data - Total: {total_batches}, Prime: {prime_count}, Rejected: {rejected_count}, AvgPrime: {avg_prime_rate:.1f}%, AvgReject: {avg_rejection_rate:.1f}%")
    return dashboard_data

@app.get("/batches")
async def get_batches():
    """Get all batches"""
    return {
        "batches": batches_db,
        "total_count": len(batches_db),
        "timestamp": datetime.now().isoformat()
    }

@app.get("/batches/{batch_id}")
async def get_batch_by_id(batch_id: str):
    """
    Get a single batch by its batch ID.
    
    This endpoint enables ConsumerController to verify batches scanned via QR code.
    Returns 404 if the batch is not found in the database.
    
    Args:
        batch_id: The unique batch identifier (e.g., "BATCH_1764759800_304")
    
    Returns:
        The batch record if found
        
    Raises:
        HTTPException 404 if batch not found
    
    Note:
        Current implementation uses O(n) linear search which is acceptable for
        development/demo use with small datasets. For production with large
        datasets, consider using a dictionary indexed by lowercase batch_id
        for O(1) lookups, or migrating to a proper database with indexing.
    """
    # Perform case-insensitive search for the batch ID
    # O(n) complexity - acceptable for demo/development use case
    batch_id_lower = batch_id.lower()
    for batch in batches_db:
        stored_batch_id = batch.get('batch_id', '')
        if stored_batch_id.lower() == batch_id_lower:
            logger.info(f"✅ Batch found: {batch_id}")
            return batch
    
    # Batch not found
    logger.warning(f"⚠️ Batch not found: {batch_id}")
    raise HTTPException(status_code=404, detail=f"Batch '{batch_id}' not found")

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

        # Convert class to quality score based on meaning, not confidence
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

        # Calculate metadata metrics for comprehensive quality assessment
        metadata = {
            "color_consistency": round(min(confidence * 1.1, 1.0), 2),  # Derived from confidence
            "size_uniformity": round(quality_score * 0.95, 2),  # Derived from quality
            "defect_density": round((1.0 - quality_score) * 0.15, 2)  # Inverse of quality
        }

        # Return response matching the exact contract from README
        response = {
            "quality_score": round(quality_score, 2),  # 0.0 to 1.0
            "quality_label": predicted_class,  # fresh, low_quality, rotten
            "confidence": round(confidence, 2),  # Model confidence 0.0 to 1.0
            "metadata": metadata,  # Additional quality metrics
            "data_hash": data_hash,  # SHA-256 hash for blockchain
            "label": predicted_class,  # Alias for quality_label (for backward compatibility)
            "all_predictions": top_predictions  # Top predictions for detailed analysis
        }

        logger.info(f"✅ Prediction: {predicted_class} (confidence: {confidence:.3f}, quality: {quality_score:.2f}, hash: {data_hash[:16]}...)")
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

@app.get("/dashboard/analytics")
async def get_analytics_dashboard():
    """
    Analytics dashboard data for KPI monitoring and trend analysis.
    Provides aggregated metrics and quality trends over time.
    """
    try:
        # Calculate aggregate metrics from batches
        if batches_db:
            total_batches = len(batches_db)
            
            # Calculate quality metrics
            quality_scores = [batch.get('quality_score', 0.8) for batch in batches_db]
            avg_quality = (sum(quality_scores) / len(quality_scores)) if quality_scores else 0
            
            # Calculate spoilage rate (batches with quality < 0.5)
            spoiled_batches = sum(1 for score in quality_scores if score < 0.5)
            spoilage_rate = (spoiled_batches / total_batches) if total_batches > 0 else 0
            
            # Calculate prime rate (batches with quality >= 0.8)
            prime_batches = sum(1 for score in quality_scores if score >= 0.8)
            prime_rate = (prime_batches / total_batches) if total_batches > 0 else 0
            
            # Quality trends (last 10 batches for trend line)
            recent_batches = batches_db[-10:] if len(batches_db) >= 10 else batches_db
            quality_trends = [
                {
                    "batch_id": batch.get('batch_id', 'Unknown'),
                    "quality_score": float(batch.get('quality_score', 0.8)),
                    "timestamp": batch.get('timestamp', datetime.now().isoformat())
                }
                for batch in recent_batches
            ]
            
        else:
            total_batches = 0
            avg_quality = 0
            spoilage_rate = 0
            prime_rate = 0
            quality_trends = []
        
        # Return analytics data
        analytics_data = {
            "kpi_metrics": {
                "total_batches": total_batches,
                "avg_quality": round(avg_quality, 3),
                "spoilage_rate": round(spoilage_rate, 3),
                "prime_rate": round(prime_rate, 3)
            },
            "quality_trends": quality_trends,
            "timestamp": datetime.now().isoformat(),
            "model_accuracy": "99.06%" if model_loaded else "demo_mode"
        }
        
        logger.info(f"📊 Analytics dashboard - Batches: {total_batches}, AvgQuality: {avg_quality:.2f}, Spoilage: {spoilage_rate:.2%}")
        return analytics_data
        
    except Exception as e:
        logger.error(f"❌ Error generating analytics dashboard: {e}")
        raise HTTPException(status_code=500, detail=f"Analytics generation failed: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")