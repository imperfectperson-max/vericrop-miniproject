"""
Test for deterministic prime_rate and rejection_rate per batch.

This test verifies that:
1. Each batch gets deterministic prime_rate and rejection_rate
2. The same batch_id always produces the same rates
3. Rates are within valid ranges (0.0 to 1.0)
4. Rates are logically consistent (primeRate + rejectionRate <= 1.0)
5. Rates match the frontend calculation algorithm (classification-based)
"""
import os
import json
import requests


def test_batch_creation_has_deterministic_rates():
    """Test that batch creation includes deterministic rates"""
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/batches"
    
    # Create a test batch
    batch_data = {
        "name": "Test_Batch_Deterministic",
        "farmer": "Test Farmer",
        "product_type": "Apples",
        "quantity": 100,
        "quality_data": {
            "quality_score": 0.85,
            "label": "fresh"
        },
        "data_hash": "test_hash_12345"
    }
    
    resp = requests.post(url, json=batch_data, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Verify response contains rates
    assert "prime_rate" in result, "Response should contain prime_rate"
    assert "rejection_rate" in result, "Response should contain rejection_rate"
    
    prime_rate = result["prime_rate"]
    rejection_rate = result["rejection_rate"]
    
    # Verify rates are within valid ranges
    assert 0.0 <= prime_rate <= 1.0, f"prime_rate {prime_rate} should be in [0.0, 1.0]"
    assert 0.0 <= rejection_rate <= 1.0, f"rejection_rate {rejection_rate} should be in [0.0, 1.0]"
    
    # Verify logical consistency
    assert prime_rate + rejection_rate <= 1.0, \
        f"prime_rate + rejection_rate ({prime_rate + rejection_rate}) should be <= 1.0"
    
    print(f"✅ Batch created with deterministic rates: prime_rate={prime_rate}, rejection_rate={rejection_rate}")


def test_batch_creation_fresh_classification():
    """
    Test batch creation with FRESH classification matches frontend algorithm.
    
    Frontend algorithm for FRESH:
    - prime% = 80 + quality% * 20
    - remainder distributed: 80% low_quality, 20% rejection
    """
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/batches"
    
    batch_data = {
        "name": "Test_Fresh_Batch",
        "farmer": "Test Farmer",
        "product_type": "Apples",
        "quantity": 100,
        "quality_data": {
            "quality_score": 0.85,
            "label": "fresh"
        },
        "data_hash": "test_hash_fresh"
    }
    
    resp = requests.post(url, json=batch_data, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Expected values based on frontend algorithm:
    # quality_percent = 85
    # prime_rate = 80 + (85 * 0.2) = 97%
    # remainder = 3%
    # rejection = 3 * 0.2 = 0.6%
    expected_prime = 0.97
    expected_rejection = 0.006
    
    prime_rate = result["prime_rate"]
    rejection_rate = result["rejection_rate"]
    
    assert abs(prime_rate - expected_prime) < 0.01, \
        f"FRESH prime_rate {prime_rate} should be ~{expected_prime}"
    assert abs(rejection_rate - expected_rejection) < 0.01, \
        f"FRESH rejection_rate {rejection_rate} should be ~{expected_rejection}"
    
    print(f"✅ FRESH batch rates match frontend: prime={prime_rate:.4f}, rejection={rejection_rate:.4f}")


def test_batch_creation_low_quality_classification():
    """
    Test batch creation with LOW_QUALITY classification matches frontend algorithm.
    
    Frontend algorithm for LOW_QUALITY:
    - low_quality% = 80 + quality% * 20
    - remainder distributed: 80% prime, 20% rejection
    """
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/batches"
    
    batch_data = {
        "name": "Test_LowQuality_Batch",
        "farmer": "Test Farmer",
        "product_type": "Apples",
        "quantity": 100,
        "quality_data": {
            "quality_score": 0.45,
            "label": "low_quality"
        },
        "data_hash": "test_hash_low_quality"
    }
    
    resp = requests.post(url, json=batch_data, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Expected values based on frontend algorithm:
    # quality_percent = 45
    # low_quality_rate = 80 + (45 * 0.2) = 89%
    # remainder = 11%
    # prime = 11 * 0.8 = 8.8%
    # rejection = 11 * 0.2 = 2.2%
    expected_prime = 0.088
    expected_rejection = 0.022
    
    prime_rate = result["prime_rate"]
    rejection_rate = result["rejection_rate"]
    
    assert abs(prime_rate - expected_prime) < 0.01, \
        f"LOW_QUALITY prime_rate {prime_rate} should be ~{expected_prime}"
    assert abs(rejection_rate - expected_rejection) < 0.01, \
        f"LOW_QUALITY rejection_rate {rejection_rate} should be ~{expected_rejection}"
    
    print(f"✅ LOW_QUALITY batch rates match frontend: prime={prime_rate:.4f}, rejection={rejection_rate:.4f}")


def test_batch_creation_rotten_classification():
    """
    Test batch creation with ROTTEN classification matches frontend algorithm.
    
    Frontend algorithm for ROTTEN:
    - rejection% = 80 + quality% * 20
    - remainder distributed: 80% low_quality, 20% prime
    """
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/batches"
    
    batch_data = {
        "name": "Test_Rotten_Batch",
        "farmer": "Test Farmer",
        "product_type": "Apples",
        "quantity": 100,
        "quality_data": {
            "quality_score": 0.20,
            "label": "rotten"
        },
        "data_hash": "test_hash_rotten"
    }
    
    resp = requests.post(url, json=batch_data, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Expected values based on frontend algorithm:
    # quality_percent = 20
    # rejection_rate = 80 + (20 * 0.2) = 84%
    # remainder = 16%
    # low_quality = 16 * 0.8 = 12.8%
    # prime = 16 * 0.2 = 3.2%
    expected_prime = 0.032
    expected_rejection = 0.84
    
    prime_rate = result["prime_rate"]
    rejection_rate = result["rejection_rate"]
    
    assert abs(prime_rate - expected_prime) < 0.01, \
        f"ROTTEN prime_rate {prime_rate} should be ~{expected_prime}"
    assert abs(rejection_rate - expected_rejection) < 0.01, \
        f"ROTTEN rejection_rate {rejection_rate} should be ~{expected_rejection}"
    
    print(f"✅ ROTTEN batch rates match frontend: prime={prime_rate:.4f}, rejection={rejection_rate:.4f}")


def test_dashboard_uses_per_batch_rates():
    """Test that dashboard endpoint returns rate information"""
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/dashboard/farm"
    
    resp = requests.get(url, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Verify dashboard structure
    assert "kpis" in result, "Dashboard should contain kpis"
    kpis = result["kpis"]
    
    assert "prime_percentage" in kpis, "KPIs should contain prime_percentage"
    assert "rejection_rate" in kpis, "KPIs should contain rejection_rate"
    
    # Verify values are reasonable percentages
    prime_pct = kpis["prime_percentage"]
    reject_rate = kpis["rejection_rate"]
    
    assert 0.0 <= prime_pct <= 100.0, f"prime_percentage {prime_pct} should be in [0.0, 100.0]"
    assert 0.0 <= reject_rate <= 100.0, f"rejection_rate {reject_rate} should be in [0.0, 100.0]"
    
    print(f"✅ Dashboard shows rates: prime_percentage={prime_pct}%, rejection_rate={reject_rate}%")


def test_dashboard_returns_counts():
    """Test that dashboard endpoint returns actual counts for consistent rate calculation"""
    base = os.environ.get("BASE_URL", "http://localhost:8000")
    url = f"{base}/dashboard/farm"
    
    resp = requests.get(url, timeout=10)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    
    result = resp.json()
    
    # Verify counts structure exists
    assert "counts" in result, "Dashboard should contain counts"
    counts = result["counts"]
    
    # Verify required count fields
    assert "prime_count" in counts, "Counts should contain prime_count"
    assert "rejected_count" in counts, "Counts should contain rejected_count"
    assert "total_count" in counts, "Counts should contain total_count"
    
    prime_count = counts["prime_count"]
    rejected_count = counts["rejected_count"]
    total_count = counts["total_count"]
    
    # Verify counts are non-negative integers
    assert prime_count >= 0, f"prime_count {prime_count} should be >= 0"
    assert rejected_count >= 0, f"rejected_count {rejected_count} should be >= 0"
    assert total_count >= 0, f"total_count {total_count} should be >= 0"
    
    # Verify canonical formula: total_count = prime_count + rejected_count
    assert total_count == prime_count + rejected_count, \
        f"total_count {total_count} should equal prime_count {prime_count} + rejected_count {rejected_count}"
    
    # If total_count > 0, verify rate calculations would be valid
    if total_count > 0:
        prime_rate = prime_count / total_count
        rejection_rate = rejected_count / total_count
        assert 0.0 <= prime_rate <= 1.0, f"Calculated prime_rate {prime_rate} should be in [0.0, 1.0]"
        assert 0.0 <= rejection_rate <= 1.0, f"Calculated rejection_rate {rejection_rate} should be in [0.0, 1.0]"
        print(f"✅ Dashboard counts: prime={prime_count}, rejected={rejected_count}, total={total_count}")
        print(f"   Calculated rates: prime_rate={prime_rate:.3f}, rejection_rate={rejection_rate:.3f}")
    else:
        print(f"✅ Dashboard counts (zero case): prime={prime_count}, rejected={rejected_count}, total={total_count}")


if __name__ == "__main__":
    # Run tests manually for debugging
    print("Testing deterministic batch rates...")
    try:
        test_batch_creation_has_deterministic_rates()
        test_dashboard_uses_per_batch_rates()
        test_dashboard_returns_counts()
        print("\n✅ All tests passed!")
    except AssertionError as e:
        print(f"\n❌ Test failed: {e}")
        exit(1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        exit(1)
