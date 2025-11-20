"""
Unit tests for ONNX inference and API contract
"""

import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock
import numpy as np
import io
from PIL import Image

# Import the FastAPI app
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from app import app


client = TestClient(app)


class TestHealthEndpoint:
    """Test health endpoint behavior in different modes"""

    def test_health_in_dev_mode_without_model(self):
        """Health should return ok in dev mode even without model"""
        with patch.dict(os.environ, {"VERICROP_MODE": "dev"}):
            with patch('app.model_loaded', False):
                response = client.get("/health")
                assert response.status_code == 200
                data = response.json()
                assert data["mode"] == "dev"
                assert data["model_loaded"] == False

    def test_health_in_prod_mode_without_model(self):
        """Health should fail in prod mode without model"""
        with patch.dict(os.environ, {"VERICROP_MODE": "prod"}):
            with patch('app.model_loaded', False):
                response = client.get("/health")
                assert response.status_code == 503
                assert "Model not loaded" in response.json()["detail"]

    def test_health_in_prod_mode_with_model(self):
        """Health should succeed in prod mode with model"""
        with patch.dict(os.environ, {"VERICROP_MODE": "prod"}):
            with patch('app.model_loaded', True):
                with patch('app.label_map', {"0": "fresh", "1": "low_quality", "2": "rotten"}):
                    response = client.get("/health")
                    assert response.status_code == 200
                    data = response.json()
                    assert data["status"] == "healthy"
                    assert data["mode"] == "prod"
                    assert data["model_loaded"] == True
                    assert data["classes_loaded"] == 3


class TestPredictEndpoint:
    """Test predict endpoint API contract"""

    def test_predict_returns_correct_contract(self):
        """Predict should return quality_score, quality_label, confidence, metadata"""
        # Create a test image
        img = Image.new('RGB', (224, 224), color='red')
        img_bytes = io.BytesIO()
        img.save(img_bytes, format='JPEG')
        img_bytes.seek(0)

        # Mock the model inference
        with patch('app.model_loaded', True):
            with patch('app.session') as mock_session:
                # Mock ONNX inference output
                mock_outputs = [np.array([[2.5, 1.0, 0.5]])]  # Logits for 3 classes
                mock_session.run.return_value = mock_outputs
                
                with patch('app.label_map', {"0": "fresh", "1": "low_quality", "2": "rotten"}):
                    response = client.post(
                        "/predict",
                        files={"file": ("test.jpg", img_bytes, "image/jpeg")}
                    )

        assert response.status_code == 200
        data = response.json()
        
        # Verify API contract
        assert "quality_score" in data
        assert "quality_label" in data
        assert "confidence" in data
        assert "metadata" in data
        
        # Verify types
        assert isinstance(data["quality_score"], float)
        assert isinstance(data["quality_label"], str)
        assert isinstance(data["confidence"], float)
        assert isinstance(data["metadata"], dict)
        
        # Verify metadata contains required fields
        assert "color_consistency" in data["metadata"]
        assert "size_uniformity" in data["metadata"]
        assert "defect_density" in data["metadata"]
        
        # Verify ranges
        assert 0.0 <= data["quality_score"] <= 1.0
        assert 0.0 <= data["confidence"] <= 1.0

    def test_predict_without_model_in_dev_mode(self):
        """Predict should use demo mode when model not loaded in dev"""
        img = Image.new('RGB', (224, 224), color='red')
        img_bytes = io.BytesIO()
        img.save(img_bytes, format='JPEG')
        img_bytes.seek(0)

        with patch.dict(os.environ, {"VERICROP_LOAD_DEMO": "true"}):
            with patch('app.model_loaded', False):
                response = client.post(
                    "/predict",
                    files={"file": ("test.jpg", img_bytes, "image/jpeg")}
                )

        assert response.status_code == 200
        data = response.json()
        
        # Should return demo data with correct contract
        assert data["quality_score"] == 0.85
        assert data["quality_label"] == "fresh"
        assert "metadata" in data

    def test_predict_without_model_in_prod_mode(self):
        """Predict should fail in prod mode without model and demo disabled"""
        img = Image.new('RGB', (224, 224), color='red')
        img_bytes = io.BytesIO()
        img.save(img_bytes, format='JPEG')
        img_bytes.seek(0)

        with patch.dict(os.environ, {"VERICROP_LOAD_DEMO": "false", "VERICROP_MODE": "prod"}):
            with patch('app.model_loaded', False):
                response = client.post(
                    "/predict",
                    files={"file": ("test.jpg", img_bytes, "image/jpeg")}
                )

        assert response.status_code == 503
        assert "not available" in response.json()["detail"]

    def test_predict_rejects_non_image(self):
        """Predict should reject non-image files"""
        text_file = io.BytesIO(b"This is not an image")
        
        response = client.post(
            "/predict",
            files={"file": ("test.txt", text_file, "text/plain")}
        )

        assert response.status_code == 400
        assert "must be an image" in response.json()["detail"]


class TestBatchEndpoint:
    """Test batch creation endpoint"""

    def test_create_batch_with_quality_data(self):
        """Batch creation should store quality score and compute rates"""
        batch_data = {
            "name": "Test Batch",
            "farmer": "Test Farmer",
            "product_type": "Apple",
            "quantity": 100,
            "quality_data": {
                "quality_score": 0.85,
                "label": "fresh"
            }
        }

        response = client.post("/batches", json=batch_data)

        assert response.status_code == 200
        data = response.json()
        
        # Verify response includes quality metrics
        assert "quality_score" in data
        assert "prime_rate" in data
        assert "rejection_rate" in data
        assert data["quality_score"] == 0.85
        
        # Verify rates are computed correctly (higher quality = higher prime rate)
        assert data["prime_rate"] > 0.7
        assert data["rejection_rate"] < 0.3


class TestStartupBehavior:
    """Test startup behavior in different modes"""

    @patch('app.os.path.exists')
    def test_startup_fails_in_prod_without_model(self, mock_exists):
        """Startup should fail in prod mode if model file missing"""
        mock_exists.return_value = False
        
        with patch.dict(os.environ, {"VERICROP_MODE": "prod"}):
            with pytest.raises(RuntimeError) as exc_info:
                # Import app module to trigger startup
                import importlib
                importlib.reload(sys.modules['app'])
            
            assert "Production startup failed" in str(exc_info.value) or \
                   "Model file not found" in str(exc_info.value)

    @patch('app.os.path.exists')
    def test_startup_succeeds_in_dev_without_model(self, mock_exists):
        """Startup should succeed in dev mode even without model"""
        mock_exists.return_value = False
        
        with patch.dict(os.environ, {"VERICROP_MODE": "dev"}):
            # Should not raise an error
            try:
                import importlib
                importlib.reload(sys.modules['app'])
            except RuntimeError:
                pytest.fail("Dev mode startup should not fail without model")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
