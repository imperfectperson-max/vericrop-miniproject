# ProducerController Implementation Guide

## Overview

This document explains the implementation of the ProducerController fixes to ensure consistent calculations and UI updates across the vericrop-gui application.

## Problem Statement

The ProducerController had several issues:
1. Inconsistent rate calculations for prime and rejection rates
2. Pie chart segments were unlabeled or mislabeled
3. Blockchain view updates needed to show newest blocks first
4. No centralized calculation logic for rates

## Solution

### 1. Canonical Rate Calculation Formulas

All rate calculations now use these consistent formulas:

```
prime_rate = prime_count / total_count
rejection_rate = rejected_count / total_count
total_count = prime_count + rejected_count
```

**Edge Case Handling:**
- When `total_count == 0`, both rates are defined as `0.0` (not NaN or Infinity)

### 2. Backend Changes (docker/ml-service/app.py)

Updated the `/dashboard/farm` endpoint to include actual counts:

```json
{
  "kpis": {
    "total_batches_today": 5,
    "average_quality": 85.2,
    "prime_percentage": 75.0,
    "rejection_rate": 15.0
  },
  "counts": {
    "prime_count": 30,
    "rejected_count": 6,
    "total_count": 36
  },
  "quality_distribution": {
    "prime": 30,
    "standard": 4,
    "sub_standard": 6
  }
}
```

The `counts` section provides the raw data needed for consistent rate calculation in the GUI.

### 3. ProducerController Changes (vericrop-gui)

#### Added Helper Method

```java
/**
 * Calculate prime rate and rejection rate using consistent formulas.
 * 
 * @param primeCount number of prime quality samples
 * @param rejectedCount number of rejected samples
 * @return array with [primeRate, rejectionRate] as percentages (0.0 to 100.0)
 */
private double[] calculateRates(int primeCount, int rejectedCount) {
    int totalCount = primeCount + rejectedCount;
    
    // Handle zero-count edge case
    if (totalCount == 0) {
        return new double[] {0.0, 0.0};
    }
    
    // Calculate rates as percentages
    double primeRate = (primeCount * 100.0) / totalCount;
    double rejectionRate = (rejectedCount * 100.0) / totalCount;
    
    return new double[] {primeRate, rejectionRate};
}
```

#### Updated Dashboard UI

The `updateDashboardUI()` method now:
1. Extracts counts from the backend response
2. Uses the `calculateRates()` helper for consistent calculation
3. Updates UI labels with formatted percentages

#### Labeled Pie Chart

Pie chart segments now show both category name and percentage:

```java
// Example output: "Prime — 62.5%"
pieChartData.add(new PieChart.Data(
    String.format("Prime — %.1f%%", primePercent), primeCount));
```

#### Blockchain Display Ordering

The blockchain view now displays blocks in newest-first order:

```java
// Display blocks in reverse order (newest first)
for (int i = chain.size() - 1; i >= 0; i--) {
    Block block = chain.get(i);
    // ... display block details
}
```

This ensures users see the most recent activity at the top of the view.

### 4. Thread Safety

All UI updates are executed on the JavaFX application thread using `Platform.runLater()`:

```java
Platform.runLater(() -> {
    // UI update code
});
```

This prevents concurrency issues when blockchain updates come from background threads.

## Testing

### Backend Tests (test_deterministic_rates.py)

Added test to verify counts are returned:

```python
def test_dashboard_returns_counts():
    """Test that dashboard endpoint returns actual counts"""
    # Verifies presence of prime_count, rejected_count, total_count
    # Validates canonical formula: total = prime + rejected
    # Tests rate calculation with returned counts
```

### GUI Tests (ProducerControllerTest.java)

Comprehensive test suite with 8 tests covering:

1. **Normal counts**: `testCalculateRates_NormalCounts()` - Tests 8 prime, 2 rejected (80%, 20%)
2. **All prime**: `testCalculateRates_AllPrime()` - Tests 10 prime, 0 rejected (100%, 0%)
3. **All rejected**: `testCalculateRates_AllRejected()` - Tests 0 prime, 5 rejected (0%, 100%)
4. **Zero count edge case**: `testCalculateRates_ZeroCount()` - Tests 0 prime, 0 rejected returns 0.0, not NaN
5. **Equal counts**: `testCalculateRates_EqualCounts()` - Tests 5 prime, 5 rejected (50%, 50%)
6. **Large counts**: `testCalculateRates_LargeCounts()` - Tests 625 prime, 375 rejected (62.5%, 37.5%)
7. **Small percentage**: `testCalculateRates_SmallPercentage()` - Tests 99 prime, 1 rejected (99%, 1%)
8. **Rates sum validation**: `testCalculateRates_RatesSumTo100()` - Verifies rates always sum to 100%

All tests use reflection to access the private `calculateRates()` method and verify correct behavior.

## Manual Testing Steps

### Testing the Producer UI

1. **Start the services:**
   ```bash
   # Start ML service (backend)
   cd docker/ml-service
   python app.py
   
   # In another terminal, start GUI
   cd vericrop-miniproject
   ./gradlew :vericrop-gui:bootRun
   ```

2. **Create batches:**
   - Click "Select Image" to upload a food image
   - Wait for AI analysis to complete
   - Fill in batch details (name, farmer, product type, quantity)
   - Click "Create Blockchain Record"

3. **Verify calculations:**
   - Check that "Prime %" and "Rejection Rate" update after batch creation
   - Create multiple batches with varying quality scores
   - Verify rates are consistent and add up correctly

4. **Verify pie chart labels:**
   - Look at the "Quality Distribution" pie chart
   - Confirm segments show format: "Category — XX.X%"
   - Verify percentages match the displayed rates

5. **Verify blockchain display:**
   - Check "Live Blockchain" section
   - After creating a new batch, verify the newest block appears at the top
   - Blocks should be numbered and show hash, participant, etc.

6. **Test edge cases:**
   - Start with zero batches - rates should show "0%"
   - Create only high-quality batches - rejection rate should be "0%"
   - Create only low-quality batches - prime rate should be "0%"

## Files Modified

### Backend
- `docker/ml-service/app.py` - Updated `/dashboard/farm` endpoint to return counts
- `docker/ml-service/tests/test_deterministic_rates.py` - Added count validation test

### Frontend
- `src/vericrop-gui/main/java/org/vericrop/gui/ProducerController.java` - Added helper, updated calculations
- `src/vericrop-gui/test/java/org/vericrop/gui/ProducerControllerTest.java` - New test file with 8 tests

## Key Implementation Notes

### Why Centralize Calculation?

The `calculateRates()` helper ensures:
- Single source of truth for rate calculations
- Consistent formula application throughout the codebase
- Easy to test and maintain
- Proper edge case handling in one place

### Why Display Newest First?

Blockchain views traditionally show newest blocks first because:
- Users care most about recent activity
- Reduces scrolling to see latest changes
- Standard UX pattern for activity feeds

### Why Use Counts Instead of Pre-calculated Rates?

By having the backend return actual counts:
- GUI can apply the canonical formula consistently
- Reduces risk of backend/frontend calculation mismatches
- Makes testing easier (can verify with simple division)
- Allows GUI to format rates according to UI requirements

## Running Tests

```bash
# Run backend tests
cd docker/ml-service
python -m pytest tests/test_deterministic_rates.py -v

# Run GUI tests
cd vericrop-miniproject
./gradlew :vericrop-gui:test --tests "org.vericrop.gui.ProducerControllerTest"
```

## Future Enhancements

Potential improvements for future versions:

1. **Persistent Dashboard State**: Cache dashboard data to reduce backend calls
2. **Real-time WebSocket Updates**: Push blockchain updates to GUI immediately
3. **Batch Filtering**: Allow filtering blockchain view by date, farmer, or quality
4. **Export Functionality**: Export blockchain data and rates to CSV/JSON
5. **Interactive Pie Chart**: Click segments to filter batch list
6. **Rate History**: Track rate changes over time in a line chart

## Troubleshooting

### Rates showing as 0% or NaN
- Check that backend `/dashboard/farm` endpoint returns `counts` section
- Verify `prime_count` and `rejected_count` are non-negative integers
- Ensure GUI is parsing counts correctly with `safeGetInt()`

### Blockchain not updating
- Verify `updateBlockchainDisplay()` is called after `handleBatchSuccess()`
- Check that blockchain service is initialized (`blockchainReady == true`)
- Look for threading issues - all UI updates must use `Platform.runLater()`

### Pie chart labels missing
- Confirm `qualityDistributionChart.setLegendVisible(true)` is called
- Verify data format: `new PieChart.Data("Label — XX.X%", value)`
- Check that quality_distribution counts are being parsed correctly

### Tests failing
- Ensure JUnit 5 is on the classpath (should be in build.gradle)
- Verify reflection is accessing the correct method signature
- Check floating-point comparison tolerance (0.01) is appropriate

## Contact

For questions or issues related to this implementation, please open an issue in the repository.
