import requests
import sys
import json
from pathlib import Path

# Adjust SERVICE_URL if your service runs on a different host/port
SERVICE_URL = "http://localhost:8000/predict"
# Place a sample image at repo/examples/sample.jpg or change this path
SAMPLE_IMG = Path(__file__).parents[3] / "examples" / "sample.jpg"

def main():
    if not SAMPLE_IMG.exists():
        print("Sample image not found:", SAMPLE_IMG)
        sys.exit(2)

    with open(SAMPLE_IMG, "rb") as f:
        files = {"file": ("sample.jpg", f, "image/jpeg")}
        r = requests.post(SERVICE_URL, files=files, timeout=15)
    try:
        r.raise_for_status()
    except Exception as e:
        print("Request failed:", e, r.text)
        sys.exit(3)

    data = r.json()
    # Schema checks
    assert "quality_score" in data, "quality_score missing"
    assert ("label" in data) or ("predicted_class" in data), "label/predicted_class missing"
    assert "data_hash" in data, "data_hash missing"
    assert isinstance(data["quality_score"], (float, int)), "quality_score not numeric"

    print("SUCCESS: prediction returned and schema validated")
    print(json.dumps(data, indent=2))
    return 0

if __name__ == "__main__":
    sys.exit(main())