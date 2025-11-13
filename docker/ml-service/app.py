from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image
import io
import hashlib
import time

app = FastAPI()

@app.get("/health")
async def health():
    return {"status": "ok", "time": int(time.time())}

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
    except Exception:
        return JSONResponse(status_code=400, content={"error": "Invalid image"})

    # TODO: replace with real model inference later
    dummy_score = 0.85
    dummy_label = "grade_A"

    report = f'{{"score": {dummy_score}, "label": "{dummy_label}"}}'
    data_hash = hashlib.sha256(report.encode("utf-8")).hexdigest()

    return {
        "quality_score": dummy_score,
        "label": dummy_label,
        "report": report,
        "data_hash": data_hash
    }