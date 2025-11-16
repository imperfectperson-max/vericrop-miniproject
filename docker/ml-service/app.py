from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image
import torch
import torchvision.transforms as transforms
import io
import os
import hashlib
import json
from pathlib import Path

app = FastAPI()

# Path to model weights
MODEL_PATH = Path(__file__).parent / "weights" / "model.pt"

# Global model placeholder
model = None
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])


def load_model():
    """Load PyTorch model if weights exist, otherwise return None"""
    global model
    if MODEL_PATH.exists():
        try:
            # Security Note: torch.load with weights_only=False is used here because we're loading
            # our own trusted model files. Only load models from trusted sources.
            # For production, consider using weights_only=True or torch.jit.load for safer loading.
            model = torch.load(MODEL_PATH, map_location=torch.device('cpu'), weights_only=False)
            model.eval()
            print(f"Model loaded successfully from {MODEL_PATH}")
        except Exception as e:
            print(f"Failed to load model: {e}")
            model = None
    else:
        print(f"Model weights not found at {MODEL_PATH}, using placeholder")
        model = None


# Load model on startup
load_model()


@app.get("/")
async def root():
    return {"status": "ok", "message": "VeriCrop ML Service"}


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "model_loaded": model is not None
    }


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """
    Accept an image file and return prediction results.
    If model weights are not loaded, returns dummy scores.
    """
    try:
        # Read and validate image
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
    except Exception as e:
        return JSONResponse(
            status_code=400,
            content={"error": f"Invalid image file: {str(e)}"}
        )
    
    # Transform image
    try:
        image_tensor = transform(image).unsqueeze(0)
    except Exception as e:
        return JSONResponse(
            status_code=400,
            content={"error": f"Image transformation failed: {str(e)}"}
        )
    
    # Run inference
    if model is not None:
        try:
            with torch.no_grad():
                output = model(image_tensor)
                # Assuming classification output
                probabilities = torch.nn.functional.softmax(output, dim=1)
                confidence, predicted_class = torch.max(probabilities, 1)
                
                quality_score = confidence.item()
                label = f"class_{predicted_class.item()}"
        except Exception as e:
            return JSONResponse(
                status_code=500,
                content={"error": f"Model inference failed: {str(e)}"}
            )
    else:
        # Use dummy scores when model is not loaded
        quality_score = 0.85
        label = "grade_A"
    
    # Create report and hash for backward compatibility with GUI
    report = json.dumps({"score": quality_score, "label": label})
    data_hash = hashlib.sha256(report.encode("utf-8")).hexdigest()
    
    return {
        "quality_score": quality_score,
        "label": label,
        "report": report,
        "data_hash": data_hash,
        "model_loaded": model is not None
    }
