# VeriCrop GUI Demo Mode - Complete Guide

## Overview

The VeriCrop GUI now supports a **self-contained demo mode** that allows the application to run without any external services (PostgreSQL, Kafka, ML Service). This makes it perfect for:

- **Quick demonstrations** without infrastructure setup
- **Development** without running Docker containers
- **Testing** UI flows in isolation
- **Offline scenarios** where external services are unavailable

## ⚠️ Security Notice

**Demo mode bypasses normal database authentication.** This is intended for development and testing only:

- Demo mode must be **explicitly enabled**
- Only predefined demo accounts work in demo mode
- Demo mode credentials: admin/admin123, farmer/farmer123, supplier/supplier123, consumer/consumer123
- **NEVER use demo mode in production environments**

## Enabling Demo Mode

### Method 1: System Property (Recommended)

```bash
# Unix/Linux/Mac
./gradlew :vericrop-gui:run --args="-Dvericrop.demoMode=true"

# OR set directly
java -Dvericrop.demoMode=true -jar vericrop-gui.jar
```

### Method 2: Environment Variable

```bash
# Unix/Linux/Mac
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run

# Windows PowerShell
$env:VERICROP_LOAD_DEMO="true"
./gradlew :vericrop-gui:run

# Windows Command Prompt
set VERICROP_LOAD_DEMO=true
gradlew.bat :vericrop-gui:run
```

### Method 3: UI Toggle

1. Launch the application
2. On the login screen, check the "Demo Mode" checkbox
3. A warning dialog will appear showing available demo credentials
4. Use demo credentials to log in

## Demo Mode Authentication

When demo mode is enabled, you can log in with these predefined accounts:

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| farmer | farmer123 | FARMER |
| supplier | supplier123 | SUPPLIER |
| consumer | consumer123 | CONSUMER |

**Note**: Other username/password combinations will NOT work, even in demo mode. This is a security measure to prevent arbitrary access.

## What Works in Demo Mode

### ✅ Fully Functional Features

1. **Producer Dashboard**
   - Image upload simulation (uses local files)
   - Batch creation with mock ML predictions
   - In-memory blockchain recording
   - Quality score calculations
   - Dashboard KPI displays
   - QR code generation
   - Delivery simulation controls
   - Full screen display (maximized window)

2. **Logistics Dashboard**
   - Delivery simulator integration
   - Real-time shipment tracking
   - Environmental monitoring (temperature, humidity)
   - Map visualization
   - Shipments table with live updates
   - Alert system for temperature/humidity breaches

3. **Consumer Dashboard**
   - Product verification via Batch ID
   - Demo data for verified products
   - Verification history tracking
   - QR code scanning interface

4. **Analytics Dashboard**
   - Demo KPI displays
   - Quality trend charts
   - Supplier performance tables
   - Alerts history
   - Export preview functionality
   - Temperature compliance charts

5. **Core Services**
   - DeliverySimulator with route generation
   - FileLedgerService for shipment recording
   - In-memory Blockchain with validation
   - MessageService for inter-component communication
   - AlertService for notifications

### ⚠️ Limited Functionality (Graceful Degradation)

1. **Backend API Calls**
   - Dashboard data loading will fail gracefully
   - Shows "N/A" or demo data instead
   - Error messages displayed in status area

2. **Kafka Events**
   - Events are logged but not sent to Kafka
   - Application continues without Kafka
   - No impact on core functionality

3. **ML Service**
   - Uses mock predictions instead of real ML
   - Quality scores are simulated
   - Image analysis provides demo results

## Testing the Demo Mode

### 1. Basic Smoke Test

```bash
# Start in demo mode
export VERICROP_LOAD_DEMO=true
./gradlew :vericrop-gui:run
```

**Expected**: Application launches and shows login screen

### 2. Producer Flow Test

1. Login with demo credentials (if authentication is enabled)
2. Navigate to Producer dashboard
3. Upload an image (any image file)
4. Fill in batch details:
   - Batch Name: "Demo Batch 001"
   - Farmer: "John Farmer"
   - Product Type: "Apples"
   - Quantity: 100
5. Click "Create Batch"
6. Verify:
   - Batch created successfully
   - Blockchain display updates
   - Dashboard KPIs update
7. Click "Generate QR Code"
8. Verify QR code is generated in `generated_qr/` directory
9. Click "Start Simulation"
10. Verify simulation starts and status updates

### 3. Logistics Flow Test

1. Navigate to Logistics dashboard
2. Verify demo shipments appear in table
3. Verify map visualization shows route
4. If simulation was started from Producer:
   - Verify shipment appears on map
   - Verify real-time position updates
   - Verify environmental readings display
5. Check alerts panel for notifications

### 4. Consumer Flow Test

1. Navigate to Consumer dashboard
2. Enter a batch ID (e.g., "BATCH_A2386")
3. Click "Verify Product"
4. Verify:
   - Verification result displays
   - Product details shown
   - History entry added

### 5. Analytics Flow Test

1. Navigate to Analytics dashboard
2. Verify:
   - KPIs display demo data
   - Supplier table shows data
   - Alerts table shows data
   - Charts display demo trends
3. Test export functionality:
   - Select export type
   - Select format
   - Click "Export Data"
   - Verify preview updates

## Architecture in Demo Mode

```
┌────────────────────────────────────────────┐
│           JavaFX GUI Controllers           │
│   (Producer, Logistics, Consumer, etc.)    │
└──────────────────┬─────────────────────────┘
                   │
                   ▼
┌────────────────────────────────────────────┐
│          ApplicationContext                │
│  - DeliverySimulator (in-memory)          │
│  - FileLedgerService (file-based)         │
│  - BlockchainService (in-memory)          │
│  - MessageService (in-memory)             │
│  - AlertService (singleton)               │
└────────────────────────────────────────────┘
```

**No External Dependencies**:
- ❌ No PostgreSQL
- ❌ No Kafka/Zookeeper
- ❌ No ML Service (FastAPI)
- ❌ No Docker containers

## Demo Data Sources

### Producer Dashboard
- Mock ML predictions (quality scores, classifications)
- Demo batch data with realistic metrics
- Simulated blockchain transactions

### Logistics Dashboard
- Demo shipments with environmental data
- Simulated GPS coordinates
- Pre-configured delivery routes

### Consumer Dashboard
- Pattern-based verification (Batch ID determines result)
- Demo product information
- Historical verification records

### Analytics Dashboard
- Hardcoded KPI values
- Demo supplier performance data
- Sample alert history
- Trend data for charts

## Implementation Details

### Demo Mode Detection

All controllers check demo mode using:

```java
private boolean shouldLoadDemoData() {
    String loadDemo = System.getProperty("vericrop.loadDemo");
    if ("true".equalsIgnoreCase(loadDemo)) {
        return true;
    }
    String loadDemoEnv = System.getenv("VERICROP_LOAD_DEMO");
    return "true".equalsIgnoreCase(loadDemoEnv);
}
```

### Service Initialization

ApplicationContext provides:
- `getDeliverySimulator()` - always available
- `getFileLedgerService()` - always available
- `getBlockchainService(blockchain)` - lazy initialization
- `createKafkaServiceManager()` - returns null if unavailable

### Error Handling

All external service calls have try-catch blocks:
- Database failures → show "N/A"
- Kafka failures → log warning, continue
- ML service failures → use mock data
- Table binding failures → log warning, continue

## Troubleshooting

### Issue: Application won't start

**Solution**:
```bash
# Check Java version
java -version  # Should be 17+

# Clean build
./gradlew clean build

# Try with explicit demo flag
./gradlew :vericrop-gui:run --args="-Dvericrop.loadDemo=true"
```

### Issue: Tables not showing data

**Cause**: FXML column definitions may not match expectations

**Solution**: Check console for warnings about column configuration

### Issue: Simulation doesn't appear in Logistics

**Cause**: Sync service not running or failed to initialize

**Solution**: 
- Check console for errors
- Try manual refresh button in Logistics dashboard

### Issue: "N/A" appearing in dashboards

**Cause**: Backend service calls failing (expected in demo mode)

**Solution**: This is normal - demo mode shows "N/A" for unavailable backend data

## Switching Between Demo and Normal Mode

### From Demo to Normal

1. Stop the application
2. Unset the environment variable:
   ```bash
   unset VERICROP_LOAD_DEMO  # Unix/Linux/Mac
   ```
3. Ensure external services are running:
   ```bash
   docker-compose up -d postgres kafka ml-service
   ```
4. Start application normally:
   ```bash
   ./gradlew :vericrop-gui:run
   ```

### From Normal to Demo

1. Stop the application
2. Set the environment variable:
   ```bash
   export VERICROP_LOAD_DEMO=true
   ```
3. Start application in demo mode:
   ```bash
   ./gradlew :vericrop-gui:run
   ```

## Files Modified for Demo Mode

1. **vericrop-gui/src/main/java/org/vericrop/gui/app/ApplicationContext.java**
   - Added getters for core services
   - Added BlockchainService and FileLedgerService support
   - Enhanced shutdown for demo services

2. **vericrop-gui/src/main/java/org/vericrop/gui/LogisticsController.java**
   - Fixed table column bindings
   - Made map visualization null-safe
   - Made sync service startup safe

4. **vericrop-gui/src/main/java/org/vericrop/gui/ConsumerController.java**
   - Enhanced demo mode fallback
   - Improved verification messages

5. **vericrop-gui/README.md** & **README.md**
   - Added comprehensive demo mode documentation

## Benefits of Demo Mode

1. **Zero Infrastructure**: No need to run Docker, PostgreSQL, Kafka, etc.
2. **Fast Startup**: Application starts in seconds
3. **Offline Capable**: Works without internet connection
4. **Demo Ready**: Perfect for presentations and demonstrations
5. **Development Friendly**: Rapid iteration without service restarts
6. **Full Featured**: All UI flows work end-to-end

## Limitations

1. **No Persistence**: Data is not saved to database
2. **Mock Data**: ML predictions are simulated
3. **No Inter-Service Communication**: Kafka events are logged only
4. **Limited Backend**: Some features show "N/A" without backend

## Next Steps

For production deployment with full functionality:
1. Set up PostgreSQL database
2. Configure Kafka cluster
3. Deploy ML Service (FastAPI)
4. Remove or unset VERICROP_LOAD_DEMO flag
5. Configure production environment variables

See [DEPLOYMENT.md](DEPLOYMENT.md) for production deployment guide.
