"""
Unit tests for compute_quality_metrics and compute_quality_based_rates functions.

These tests verify that the ML service calculation logic matches the frontend
calculation logic in ProducerController.calculateQualityMetrics().

The algorithm uses the classification label to determine the dominant rate:
- FRESH: prime% = 80 + quality% * 20, remainder distributed (80% low_quality, 20% rejection)
- LOW_QUALITY: low_quality% = 80 + quality% * 20, remainder distributed (80% prime, 20% rejection)
- ROTTEN: rejection% = 80 + quality% * 20, remainder distributed (80% low_quality, 20% prime)

Reference: vericrop-gui/src/main/java/org/vericrop/gui/ProducerController.java
"""
import sys
import os

# Add parent directory to path so we can import app module
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import compute_quality_metrics, compute_quality_based_rates, normalize_metrics


class TestComputeQualityMetrics:
    """Tests for compute_quality_metrics function"""

    def test_fresh_high_quality(self):
        """Test FRESH classification with high quality score (0.85)"""
        # quality_percent = 85
        # prime_rate = 80 + (85 * 0.2) = 80 + 17 = 97%
        # remainder = 3%
        # low_quality = 3 * 0.8 = 2.4%
        # rejection = 3 * 0.2 = 0.6%
        metrics = compute_quality_metrics("fresh", 0.85)
        
        assert "prime_rate" in metrics
        assert "low_quality_rate" in metrics
        assert "rejection_rate" in metrics
        
        assert abs(metrics["prime_rate"] - 0.97) < 0.01
        assert abs(metrics["low_quality_rate"] - 0.024) < 0.01
        assert abs(metrics["rejection_rate"] - 0.006) < 0.01
        
        # Verify sum is 1.0
        total = sum(metrics.values())
        assert abs(total - 1.0) < 0.001

    def test_fresh_low_quality(self):
        """Test FRESH classification with low quality score (0.45)"""
        # quality_percent = 45
        # prime_rate = 80 + (45 * 0.2) = 80 + 9 = 89%
        # remainder = 11%
        metrics = compute_quality_metrics("fresh", 0.45)
        
        assert abs(metrics["prime_rate"] - 0.89) < 0.01
        assert abs(metrics["low_quality_rate"] - 0.088) < 0.01
        assert abs(metrics["rejection_rate"] - 0.022) < 0.01

    def test_low_quality_classification(self):
        """Test LOW_QUALITY classification"""
        # quality_percent = 60
        # low_quality_rate = 80 + (60 * 0.2) = 80 + 12 = 92%
        # remainder = 8%
        # prime = 8 * 0.8 = 6.4%
        # rejection = 8 * 0.2 = 1.6%
        metrics = compute_quality_metrics("low_quality", 0.60)
        
        assert abs(metrics["low_quality_rate"] - 0.92) < 0.01
        assert abs(metrics["prime_rate"] - 0.064) < 0.01
        assert abs(metrics["rejection_rate"] - 0.016) < 0.01
        
        # Verify sum is 1.0
        total = sum(metrics.values())
        assert abs(total - 1.0) < 0.001

    def test_rotten_classification(self):
        """Test ROTTEN classification"""
        # quality_percent = 30
        # rejection_rate = 80 + (30 * 0.2) = 80 + 6 = 86%
        # remainder = 14%
        # low_quality = 14 * 0.8 = 11.2%
        # prime = 14 * 0.2 = 2.8%
        metrics = compute_quality_metrics("rotten", 0.30)
        
        assert abs(metrics["rejection_rate"] - 0.86) < 0.01
        assert abs(metrics["low_quality_rate"] - 0.112) < 0.01
        assert abs(metrics["prime_rate"] - 0.028) < 0.01
        
        # Verify sum is 1.0
        total = sum(metrics.values())
        assert abs(total - 1.0) < 0.001

    def test_unknown_classification_fallback(self):
        """Test fallback behavior for unknown classification"""
        metrics = compute_quality_metrics("unknown", 0.75)
        
        # Fallback: prime = quality_score, low_quality = (1-quality) * 0.7, rejection = (1-quality) * 0.3
        assert abs(metrics["prime_rate"] - 0.75) < 0.01
        assert abs(metrics["low_quality_rate"] - 0.175) < 0.01
        assert abs(metrics["rejection_rate"] - 0.075) < 0.01

    def test_case_insensitive_classification(self):
        """Test that classification is case-insensitive"""
        metrics_lower = compute_quality_metrics("fresh", 0.85)
        metrics_upper = compute_quality_metrics("FRESH", 0.85)
        metrics_mixed = compute_quality_metrics("Fresh", 0.85)
        
        assert metrics_lower == metrics_upper == metrics_mixed

    def test_edge_case_zero_quality(self):
        """Test with quality score of 0.0"""
        metrics = compute_quality_metrics("fresh", 0.0)
        
        # quality_percent = 0
        # prime_rate = 80 + (0 * 0.2) = 80%
        assert abs(metrics["prime_rate"] - 0.80) < 0.01
        
        total = sum(metrics.values())
        assert abs(total - 1.0) < 0.001

    def test_edge_case_full_quality(self):
        """Test with quality score of 1.0"""
        metrics = compute_quality_metrics("fresh", 1.0)
        
        # quality_percent = 100
        # prime_rate = 80 + (100 * 0.2) = 100% (capped)
        assert abs(metrics["prime_rate"] - 1.0) < 0.01
        assert metrics["low_quality_rate"] == 0.0
        assert metrics["rejection_rate"] == 0.0

    def test_none_classification(self):
        """Test with None classification defaults to FRESH behavior"""
        metrics = compute_quality_metrics(None, 0.85)
        
        # Should use FRESH behavior
        assert abs(metrics["prime_rate"] - 0.97) < 0.01


class TestComputeQualityBasedRates:
    """Tests for compute_quality_based_rates function (backward compatibility wrapper)"""

    def test_returns_tuple(self):
        """Test that function returns a tuple of two values"""
        result = compute_quality_based_rates(0.85, "fresh")
        
        assert isinstance(result, tuple)
        assert len(result) == 2

    def test_fresh_classification(self):
        """Test FRESH classification returns correct rates"""
        prime_rate, rejection_rate = compute_quality_based_rates(0.85, "fresh")
        
        assert abs(prime_rate - 0.97) < 0.01
        assert abs(rejection_rate - 0.006) < 0.01

    def test_rotten_classification(self):
        """Test ROTTEN classification returns correct rates"""
        prime_rate, rejection_rate = compute_quality_based_rates(0.30, "rotten")
        
        assert abs(rejection_rate - 0.86) < 0.01
        assert abs(prime_rate - 0.028) < 0.01

    def test_default_classification(self):
        """Test default classification parameter (should be 'fresh')"""
        prime_rate, rejection_rate = compute_quality_based_rates(0.85)
        
        # Should use FRESH behavior by default
        assert abs(prime_rate - 0.97) < 0.01

    def test_rates_in_valid_range(self):
        """Test that all rates are in valid range [0.0, 1.0]"""
        test_cases = [
            (0.0, "fresh"),
            (0.5, "fresh"),
            (1.0, "fresh"),
            (0.3, "rotten"),
            (0.6, "low_quality"),
        ]
        
        for quality_score, classification in test_cases:
            prime_rate, rejection_rate = compute_quality_based_rates(quality_score, classification)
            
            assert 0.0 <= prime_rate <= 1.0, f"prime_rate {prime_rate} out of range for {quality_score}, {classification}"
            assert 0.0 <= rejection_rate <= 1.0, f"rejection_rate {rejection_rate} out of range for {quality_score}, {classification}"


class TestNormalizeMetrics:
    """Tests for normalize_metrics function"""

    def test_already_normalized(self):
        """Test metrics that already sum to 1.0"""
        metrics = {"a": 0.5, "b": 0.3, "c": 0.2}
        result = normalize_metrics(metrics)
        
        assert abs(result["a"] - 0.5) < 0.001
        assert abs(result["b"] - 0.3) < 0.001
        assert abs(result["c"] - 0.2) < 0.001

    def test_needs_normalization(self):
        """Test metrics that need normalization"""
        metrics = {"a": 0.6, "b": 0.6}  # sum = 1.2
        result = normalize_metrics(metrics)
        
        total = sum(result.values())
        assert abs(total - 1.0) < 0.001

    def test_zero_values(self):
        """Test with all zero values"""
        metrics = {"a": 0.0, "b": 0.0, "c": 0.0}
        result = normalize_metrics(metrics)
        
        # Should return unchanged since total is 0
        assert result == metrics


class TestMatchFrontendCalculation:
    """
    Integration tests to verify ML service matches frontend calculation.
    
    These test cases are derived from the frontend algorithm in
    ProducerController.calculateQualityMetrics()
    """

    def test_frontend_fresh_example(self):
        """
        Test example from frontend: FRESH with quality_score 0.85
        
        Frontend calculation:
        - quality_percent = 85
        - prime_rate = 80 + (85 * 0.2) = 97%
        - remainder = 3%
        - low_quality = 3 * 0.8 = 2.4%
        - rejection = 3 * 0.2 = 0.6%
        """
        metrics = compute_quality_metrics("fresh", 0.85)
        
        # Match frontend expected values
        expected_prime = 0.97
        expected_low_quality = 0.024
        expected_rejection = 0.006
        
        assert abs(metrics["prime_rate"] - expected_prime) < 0.01
        assert abs(metrics["low_quality_rate"] - expected_low_quality) < 0.01
        assert abs(metrics["rejection_rate"] - expected_rejection) < 0.01

    def test_frontend_low_quality_example(self):
        """
        Test example from frontend: LOW_QUALITY with quality_score 0.45
        
        Frontend calculation:
        - quality_percent = 45
        - low_quality_rate = 80 + (45 * 0.2) = 89%
        - remainder = 11%
        - prime = 11 * 0.8 = 8.8%
        - rejection = 11 * 0.2 = 2.2%
        """
        metrics = compute_quality_metrics("low_quality", 0.45)
        
        expected_low_quality = 0.89
        expected_prime = 0.088
        expected_rejection = 0.022
        
        assert abs(metrics["low_quality_rate"] - expected_low_quality) < 0.01
        assert abs(metrics["prime_rate"] - expected_prime) < 0.01
        assert abs(metrics["rejection_rate"] - expected_rejection) < 0.01

    def test_frontend_rotten_example(self):
        """
        Test example from frontend: ROTTEN with quality_score 0.20
        
        Frontend calculation:
        - quality_percent = 20
        - rejection_rate = 80 + (20 * 0.2) = 84%
        - remainder = 16%
        - low_quality = 16 * 0.8 = 12.8%
        - prime = 16 * 0.2 = 3.2%
        """
        metrics = compute_quality_metrics("rotten", 0.20)
        
        expected_rejection = 0.84
        expected_low_quality = 0.128
        expected_prime = 0.032
        
        assert abs(metrics["rejection_rate"] - expected_rejection) < 0.01
        assert abs(metrics["low_quality_rate"] - expected_low_quality) < 0.01
        assert abs(metrics["prime_rate"] - expected_prime) < 0.01

    def test_rates_sum_to_100_percent(self):
        """Test that all rates sum to exactly 100% for various inputs"""
        test_cases = [
            ("fresh", 0.0),
            ("fresh", 0.5),
            ("fresh", 0.85),
            ("fresh", 1.0),
            ("low_quality", 0.3),
            ("low_quality", 0.6),
            ("rotten", 0.2),
            ("rotten", 0.5),
            ("unknown", 0.75),
        ]
        
        for classification, quality_score in test_cases:
            metrics = compute_quality_metrics(classification, quality_score)
            total = sum(metrics.values())
            
            assert abs(total - 1.0) < 0.001, \
                f"Rates should sum to 1.0 for {classification}/{quality_score}, got {total}"
