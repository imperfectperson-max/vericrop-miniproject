import os
import requests

def test_health_ok():
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    r = requests.get(f"{base}/health", timeout=5)
    assert r.status_code == 200
    body = r.json()
    assert body.get("status") == "ok"