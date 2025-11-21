# Running VeriCrop GUI Application

This guide explains how to run the VeriCrop JavaFX GUI application with authentication.

## Prerequisites

- Java 17 or higher
- Gradle (included via wrapper)

## Quick Start

### 1. Build the Application

```bash
./gradlew :vericrop-gui:build
```

### 2. Run the Application

```bash
./gradlew :vericrop-gui:run
```

The application will start and display the login screen.

## Authentication Modes

The VeriCrop GUI supports two authentication modes:

### 1. REST API Mode (Default)

By default, the application attempts to connect to a backend REST API at `http://localhost:8080/api`.

If the backend is available, authentication requests will be sent to:
- `POST /api/auth/login` - for login
- `POST /api/auth/register` - for registration

**To configure a different backend URL:**

```bash
export VERICROP_BACKEND_URL=http://your-backend-url:port/api
./gradlew :vericrop-gui:run
```

### 2. Fallback Mode (In-Memory)

If the backend API is not available, the application automatically falls back to an in-memory authentication mode suitable for local testing and development.

**To force fallback mode:**

```bash
export VERICROP_AUTH_FALLBACK=true
./gradlew :vericrop-gui:run
```

## Test Credentials (Fallback Mode)

When using fallback mode, the following test accounts are pre-configured:

| Username   | Password   | Role     |
|------------|------------|----------|
| `farmer`   | `password` | Farmer   |
| `consumer` | `password` | Consumer |
| `supplier` | `password` | Supplier |
| `admin`    | `password` | Admin    |

## User Roles and Screens

Each role is routed to a specific screen after successful login:

- **Farmer** → Producer Screen (batch creation, quality analysis)
- **Supplier** → Logistics Screen (shipment tracking, monitoring)
- **Consumer** → Consumer Screen (product verification)
- **Admin** → Analytics Screen (system metrics, reports)

## Creating New Users

### Via Registration Form

1. Start the application
2. Click the **Register** tab
3. Fill in the required fields:
   - Username (minimum 3 characters)
   - Email (valid email format)
   - Password (minimum 6 characters)
   - Role (select from dropdown)
4. Click **Create Account**

In fallback mode, the new user is stored in memory and available immediately.

### Via Backend API

If using REST API mode, users are created through the backend. Refer to your backend API documentation for user management.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `VERICROP_BACKEND_URL` | Backend REST API base URL | `http://localhost:8080/api` |
| `VERICROP_AUTH_FALLBACK` | Force fallback mode (`true`/`false`) | `false` (auto-detect) |

## Logout

To logout:

1. Click the **🚪 Logout** button in the top-right corner of any screen
2. Confirm the logout action
3. You will be returned to the login screen

## Running with Custom Configuration

### Example: Force Fallback Mode

```bash
VERICROP_AUTH_FALLBACK=true ./gradlew :vericrop-gui:run
```

### Example: Custom Backend URL

```bash
VERICROP_BACKEND_URL=http://production-server:8080/api ./gradlew :vericrop-gui:run
```

### Example: Both Options

```bash
VERICROP_BACKEND_URL=http://localhost:9000/api \
VERICROP_AUTH_FALLBACK=false \
./gradlew :vericrop-gui:run
```

## Building for Distribution

### Create Executable JAR

```bash
./gradlew :vericrop-gui:bootJar
```

The JAR will be created at `vericrop-gui/build/libs/vericrop-gui-1.0.0.jar`.

### Run the JAR

```bash
java -jar vericrop-gui/build/libs/vericrop-gui-1.0.0.jar
```

### Create Distribution Archive

```bash
./gradlew :vericrop-gui:distZip
```

This creates a distribution archive at `vericrop-gui/build/distributions/vericrop-gui-1.0.0.zip` containing:
- Application JAR
- Start scripts for Windows and Unix
- Dependencies

## Troubleshooting

### Application Won't Start

**Error: "Backend API not available"**

This is not an error - the application will automatically use fallback mode. If you want to use the backend API:

1. Ensure your backend service is running
2. Verify the backend URL is correct
3. Check network connectivity

### Login Fails

**In Fallback Mode:**
- Verify you're using the correct test credentials (see table above)
- Username is case-insensitive
- Password must match exactly

**In REST API Mode:**
- Check backend logs for authentication errors
- Verify user exists in backend database
- Ensure backend authentication endpoints are working

### Screen Navigation Issues

If you can't navigate between screens:
- Ensure you're logged in (check for username display in top-right)
- Try logging out and logging back in
- Check console logs for errors

## Console Logging

The application provides detailed console logging:

```
✅ FallbackAuthService initialized (in-memory mode)
✅ User logged in (fallback mode): farmer (role: farmer)
```

Watch the console for:
- Authentication mode selection
- Login/logout events
- Navigation changes
- Error messages

## Security Notes

- **Fallback mode is for development/testing only** - credentials are stored in memory without encryption
- In production, always use REST API mode with a secure backend
- Never commit credentials or sensitive data to source control
- Change default test passwords before deploying

## Additional Resources

- [Main README](README.md) - Project overview and architecture
- [Setup Guide](SETUP.md) - Development environment setup
- Backend API documentation (if available)
