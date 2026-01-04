# Analytics Controller Integration

## Overview

The AnalyticsController has been successfully integrated into the VeriCrop system with admin-only access control and real simulation data integration.

## Features Implemented

### 1. Admin Authentication & Authorization

#### REST API Security
- **Location**: `AnalyticsRestController.java`
- **Authentication Method**: JWT Bearer token verification
- **Authorization**: ADMIN role required for all endpoints
- **Endpoints Protected**:
  - `GET /analytics/recent-batches` - Get recent batch analytics
  - `POST /analytics/recent-batches` - Update batch cache
  - `PUT /analytics/batches/{batchId}/status` - Update batch status
  - `PUT /analytics/batches/{batchId}/compliance` - Update compliance info

#### Response Codes
- `200 OK` - Admin authenticated successfully
- `401 Unauthorized` - Missing or invalid token
- `403 Forbidden` - Valid token but non-admin role

#### Example Request
```bash
# Admin access (succeeds)
curl -H "Authorization: Bearer <admin_jwt_token>" \
     http://localhost:8080/analytics/recent-batches

# Non-admin access (fails with 403)
curl -H "Authorization: Bearer <producer_jwt_token>" \
     http://localhost:8080/analytics/recent-batches
```

### 2. GUI Controller Security

#### Access Control
- **Location**: `AnalyticsController.java`
- **Check Point**: `initialize()` method
- **Authentication Service**: Uses `AuthenticationService` from `ApplicationContext`

#### User Flow
1. User navigates to analytics screen
2. Controller checks if user is authenticated
3. Controller verifies user has ADMIN role
4. If not admin:
   - Shows "Access Denied" dialog
   - Redirects to login screen
   - Logs unauthorized access attempt
5. If admin:
   - Initializes analytics components
   - Loads real simulation data
   - Displays KPIs and charts

#### Code Example
```java
if (!authService.isAuthenticated()) {
    showAccessDenied("You must be logged in to access analytics");
    return;
}

String currentRole = authService.getCurrentRole();
if (!"ADMIN".equalsIgnoreCase(currentRole)) {
    logger.warn("Non-admin user attempted to access analytics: user={}, role={}", 
               authService.getCurrentUser(), currentRole);
    showAccessDenied("Admin access required. Your role: " + currentRole);
    return;
}
```

### 3. Real Simulation Data Integration

#### Database Integration
- **DAO**: `SimulationDao.java`
- **New Methods Added**:
  - `getTotalCount()` - Get total simulation count
  - `getCountByStatus(String status)` - Get count by status
  - `getRecent(int limit)` - Get recent simulations

#### Analytics KPIs Displayed

1. **Total Batches**
   - Source: `simulationDao.getTotalCount()`
   - Shows total number of simulations in database

2. **On-Time Delivery**
   - Calculation: `(completedSimulations / totalSimulations) * 100`
   - Based on completed vs total simulations

3. **Quality Metrics** (Placeholder)
   - Average Quality: Currently "--" (ready for future integration)
   - Spoilage Rate: Currently "--" (ready for future integration)

#### Data Loading Logic
```java
private void setupKPIs() {
    if (simulationDao != null && !shouldLoadDemoData()) {
        try {
            int totalSimulations = simulationDao.getTotalCount();
            int completedSimulations = simulationDao.getCountByStatus("completed");
            
            totalBatchesLabel.setText(String.valueOf(totalSimulations));
            
            if (totalSimulations > 0) {
                int onTimePercentage = (int) ((completedSimulations * 100.0) / totalSimulations);
                onTimeDeliveryLabel.setText(onTimePercentage + "%");
            }
        } catch (Exception e) {
            logger.error("Failed to load analytics KPIs: {}", e.getMessage());
            setDefaultKPIs();
        }
    }
}
```

### 4. Navigation Integration

#### MainApp Enhancement
- **Method**: `showAnalyticsScreen()`
- **Location**: `MainApp.java`
- **Usage**: Called from analytics FXML action handlers

```java
public void showAnalyticsScreen() {
    switchToScreen("analytics.fxml");
}
```

#### Navigation Buttons
The analytics screen includes navigation buttons to:
- Producer Dashboard
- Logistics Dashboard
- Consumer Dashboard
- Messages (placeholder)
- Contacts (placeholder)
- Logout

### 5. Simulation Event Listener Integration

The AnalyticsController implements `SimulationListener` to receive real-time updates:

```java
@Override
public void onSimulationStarted(String batchId, String farmerId, String scenarioId) {
    // Add alert to analytics table
    alerts.add(0, new Alert(timestamp, "INFO", "Simulation started: " + batchId));
}

@Override
public void onSimulationStopped(String batchId, boolean completed) {
    // Add completion alert
    alerts.add(0, new Alert(timestamp, completed ? "SUCCESS" : "INFO", details));
}

@Override
public void onSimulationError(String batchId, String error) {
    // Add error alert
    alerts.add(0, new Alert(timestamp, "ERROR", "Simulation error: " + error));
}
```

## Testing

### Unit Tests

#### 1. Basic Functionality Tests
- **File**: `AnalyticsRestControllerTest.java`
- **Coverage**: CRUD operations for batch analytics
- **Test Mode**: Uses no-arg constructor (skips authentication)

#### 2. Authentication Tests
- **File**: `AnalyticsRestControllerAuthTest.java`
- **Coverage**:
  - No authentication (401)
  - Invalid token (401)
  - Non-admin user (403)
  - Admin user (200)
  - All endpoints tested

### Test Results
```
✅ AnalyticsRestControllerTest - All tests passed (7/7)
✅ AnalyticsRestControllerAuthTest - All tests passed (7/7)
```

## Security Considerations

### 1. Authentication
- JWT tokens are validated for expiration and signature
- Tokens must be provided in `Authorization: Bearer <token>` header

### 2. Authorization
- Role-based access control (RBAC)
- Only ADMIN role can access analytics
- Non-admin attempts are logged for security auditing

### 3. Logging
- All unauthorized access attempts are logged
- Admin access is logged for audit trail
- Error conditions are logged with context

### 4. Error Messages
- Generic messages prevent information disclosure
- Specific error details logged server-side only
- User-friendly messages in GUI dialogs

## Usage Guide

### For Administrators

1. **Login as Admin**
   - Use credentials with ADMIN role
   - Can be database user or demo admin (if demo mode enabled)

2. **Access Analytics**
   - Navigate to analytics screen via navigation buttons
   - Or direct URL: `http://localhost:8080/analytics.html` (if web interface exists)

3. **View KPIs**
   - Total simulations count
   - On-time delivery percentage
   - Real-time simulation alerts

4. **Export Reports** (Future Enhancement)
   - Select report type from dropdown
   - Choose date range
   - Select format (PDF, CSV, Excel, JSON)
   - Click "Export" button

### For Developers

#### Adding New Analytics Metrics

1. **Add Database Query**
```java
// In SimulationDao.java
public double getAverageQuality() {
    String sql = "SELECT AVG(quality_score) FROM simulation_batches WHERE quality_score IS NOT NULL";
    // ... implementation
}
```

2. **Update Controller**
```java
// In AnalyticsController.java
private void setupKPIs() {
    double avgQuality = simulationDao.getAverageQuality();
    avgQualityLabel.setText(String.format("%.1f%%", avgQuality));
}
```

3. **Add Tests**
```java
@Test
void testGetAverageQuality() {
    // Test implementation
}
```

## Future Enhancements

### Planned Features

1. **Advanced Quality Metrics**
   - Average quality score calculation
   - Quality trend analysis over time
   - Spoilage rate tracking

2. **Supplier Analytics**
   - Supplier performance ratings
   - Quality by supplier
   - Delivery time analysis

3. **Temperature Compliance**
   - Violation tracking
   - Compliance percentage by batch
   - Temperature trend charts

4. **Export Functionality**
   - PDF report generation
   - CSV data export
   - Excel spreadsheet export
   - JSON API export

5. **Real-time Dashboards**
   - Live simulation monitoring
   - Alert notifications
   - Performance metrics

## Configuration

### Application Properties

```yaml
# JWT Configuration (application.yml)
jwt:
  secret: "your-secret-key-min-32-chars-for-hs256"
  expiration: 86400000  # 24 hours in milliseconds

# Demo Mode
vericrop:
  demoMode: false  # Set to true for demo data
  loadDemo: false  # Set to true for demo KPIs
```

### Database Schema

The analytics system uses the following tables:
- `simulations` - Main simulation records
- `simulation_batches` - Individual batch data
- `users` - User authentication and roles

## Troubleshooting

### Issue: Access Denied Dialog
**Cause**: Non-admin user trying to access analytics
**Solution**: Login with admin credentials

### Issue: "Authentication required" message
**Cause**: User not logged in
**Solution**: Navigate to login screen and authenticate

### Issue: No data showing (all "--")
**Cause**: No simulations in database or demo mode disabled
**Solution**: 
- Run simulations to generate data
- Enable demo mode in configuration

### Issue: 401 Unauthorized on API calls
**Cause**: Missing or invalid JWT token
**Solution**: Include valid admin JWT token in Authorization header

### Issue: 403 Forbidden on API calls
**Cause**: User has valid token but not ADMIN role
**Solution**: Use admin credentials or contact administrator

## API Documentation

### GET /analytics/recent-batches
Get recent batch analytics data.

**Headers:**
- `Authorization: Bearer <admin_jwt_token>`

**Response 200:**
```json
[
  {
    "batchId": "BATCH_001",
    "status": "IN_TRANSIT",
    "timestamp": 1703451234567,
    "compliant": true,
    "violationCount": 0,
    "location": "Farm A",
    "qualityScore": 92.5
  }
]
```

**Response 401:** Unauthorized
**Response 403:** Forbidden (non-admin)

### POST /analytics/recent-batches
Update batch analytics cache.

**Headers:**
- `Authorization: Bearer <admin_jwt_token>`
- `Content-Type: application/json`

**Request Body:**
```json
{
  "batchId": "BATCH_001",
  "status": "IN_TRANSIT",
  "location": "Farm A",
  "qualityScore": 92.5
}
```

**Response 200:** OK
**Response 401:** Unauthorized
**Response 403:** Forbidden

### PUT /analytics/batches/{batchId}/status
Update batch status.

**Headers:**
- `Authorization: Bearer <admin_jwt_token>`

**Parameters:**
- `batchId` (path) - Batch identifier
- `status` (query) - New status value

**Response 200:** OK
**Response 404:** Batch not found
**Response 401:** Unauthorized
**Response 403:** Forbidden

### PUT /analytics/batches/{batchId}/compliance
Update temperature compliance information.

**Headers:**
- `Authorization: Bearer <admin_jwt_token>`
- `Content-Type: application/json`

**Request Body:**
```json
{
  "compliant": true,
  "violationCount": 0
}
```

**Response 200:** OK
**Response 401:** Unauthorized
**Response 403:** Forbidden

## Conclusion

The AnalyticsController integration provides a secure, admin-only analytics dashboard with real simulation data integration. The implementation follows best practices for authentication, authorization, and data access patterns. The system is extensible and ready for future enhancements including advanced metrics, export functionality, and real-time monitoring.
