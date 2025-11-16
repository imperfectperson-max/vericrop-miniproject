import os
import json
import re
import requests

HEX64_RE = re.compile(r"^[0-9a-f]{64}$")

def load_sample():
    p = os.path.join(os.path.dirname(__file__), "data", "sample_input.json")
    with open(p, "r") as f:
        return json.load(f)


def _write_debug(resp, suffix=""):
    debug_path = os.path.join(os.path.dirname(__file__), f"debug_predict_response{suffix}.json")
    try:
        with open(debug_path, "w", encoding="utf-8") as f:
            json.dump({"status_code": resp.status_code, "text": resp.text}, f)
    except Exception:
        pass


def test_predict_strict_file_upload():
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/predict"
    # payload is kept for compatibility or for endpoints expecting instances; not used in strict file test
    _ = load_sample()

    # Always use the bundled test image (deterministic)
    image_path = os.path.join(os.path.dirname(__file__), "data", "sample.png")
    if not os.path.exists(image_path):
        # fallback: repo-root examples/sample.png (for older clones)
        image_path = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "examples", "sample.png"))
        if not os.path.exists(image_path):
            import pytest
            pytest.skip("Bundled test image not found; skipping")

    with open(image_path, "rb") as fh:
        content_type = "image/png" if image_path.lower().endswith(".png") else "application/octet-stream"
        files = {"file": (os.path.basename(image_path), fh, content_type)}
        resp = requests.post(url, files=files, timeout=20)
        _write_debug(resp, suffix="_file")
        assert resp.status_code == 200, f"/predict returned {resp.status_code}: {resp.text}"

        data = resp.json()
        assert isinstance(data, dict)
        assert "quality_score" in data
        qs = data["quality_score"]
        assert isinstance(qs, (float, int))
        assert 0.0 <= float(qs) <= 1.0
        assert "data_hash" in data
        dh = data["data_hash"]
        assert isinstance(dh, str) and HEX64_RE.match(dh), f"data_hash looks invalid: {dh}"
        assert "label" in data and isinstance(data["label"], str) and data["label"]
