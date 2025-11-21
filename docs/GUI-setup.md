# VeriCrop GUI Setup Guide

## User Authentication and Messaging System

This guide explains how to set up and use the VeriCrop GUI application with the new authentication and messaging features.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Running the Application](#running-the-application)
- [User Registration](#user-registration)
- [User Login](#user-login)
- [Using the Messaging System](#using-the-messaging-system)
- [Troubleshooting](#troubleshooting)

## Prerequisites

Before you begin, ensure you have:

1. **Java 17 or higher** installed
   ```bash
   java -version  # Should show 17 or higher
   ```

2. **PostgreSQL database** running
   - Default connection: `localhost:5432`
   - Database name: `vericrop`
   - Username: `vericrop`
   - Password: `vericrop123`

3. **Docker and Docker Compose** (recommended for infrastructure)
   ```bash
   docker-compose up -d postgres
   ```

## Database Setup

### Automatic Migration (Recommended)

The application uses Flyway for automatic database migrations. On first startup, it will automatically:

1. Create the `batches` table (V1 migration)
2. Create the `users` table with demo accounts (V2 migration)
3. Create the `shipments` table (V3 migration)
4. Create the `messages` table (V4 migration)

No manual database setup is required!

### Demo User Accounts

Three demo accounts are created automatically:

| Username | Password | Role | Description |
|----------|----------|------|-------------|
| `admin` | `admin123` | ADMIN | Full system access, analytics dashboard |
| `farmer` | `farmer123` | FARMER | Farm management, batch creation |
| `supplier` | `supplier123` | SUPPLIER | Logistics, shipment tracking |

## Running the Application

### Using Gradle (Recommended)

From the project root directory:

```bash
./gradlew :vericrop-gui:run
```

On Windows:
```cmd
gradlew.bat :vericrop-gui:run
```

## User Registration

### Registering a New Account

1. **Launch the application** - The login screen will appear
2. **Click "Register here"** link at the bottom
3. **Fill in the registration form:**
   - **Username**: 3+ characters, letters, numbers, and underscores only
   - **Email**: Valid email format
   - **Full Name**: Minimum 2 characters
   - **Password**: Minimum 6 characters
   - **Confirm Password**: Must match the password field
   - **Role**: Select one of:
     - 👨‍🌾 **Farmer**: Farm management and batch creation
     - 👥 **Consumer**: Product verification
     - 🏭 **Supplier**: Logistics and shipment tracking
     - 🔧 **Admin**: Full system access and analytics

4. **Click "Create Account"**
5. **Automatic redirect** to login screen after success

## User Login

### Logging In

1. **Enter your username**
2. **Enter your password**
3. **Click "Sign In"**
4. **Automatic navigation** to role-based dashboard:
   - **FARMER** → Producer Dashboard
   - **SUPPLIER** → Logistics Dashboard
   - **CONSUMER** → Consumer Dashboard
   - **ADMIN** → Analytics Dashboard

### Demo Mode

Check **"Enable Demo Mode"** on login screen to skip authentication for testing.

### Security Features

- Account lockout after 5 failed login attempts (30 minutes)
- BCrypt password hashing (never stored in plain text)
- Session management with role-based access

## Using the Messaging System

### Accessing Messages

Navigate to the inbox from any dashboard (Messages button).

### Inbox Features

- **Unread Count Badge**: Shows number of unread messages
- **Message List**: Subject, sender, date, preview
- **Message Detail**: Full message with sender, recipient, date, body
- **Inbox/Sent Tabs**: Switch between received and sent messages

### Composing Messages

1. Click **"Compose"** button
2. Select recipient from dropdown
3. Enter subject (required)
4. Type message (required)
5. Click **"Send"**

### Reading and Managing Messages

- **Auto-mark as read**: Inbox messages marked read when viewed
- **Reply**: Click "Reply" to respond (pre-fills recipient and subject)
- **Mark as Unread**: Toggle read status
- **Delete**: Soft delete (hidden but preserved in database)

## Troubleshooting

### Database Connection Failed

```bash
# Verify PostgreSQL is running
docker-compose ps postgres

# Test connection
psql -h localhost -U vericrop -d vericrop
```

### Login Fails for Demo Accounts

```sql
-- Verify users exist
SELECT username, role FROM users;

-- If empty, run V2 migration manually
\i vericrop-gui/src/main/resources/db/migration/V2__create_users_table.sql
```

### Account Locked

Wait 30 minutes or manually unlock:

```sql
UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE username = 'your_username';
```

## Advanced Configuration

Configure via `application.properties` or environment variables:

```properties
# Database
postgres.host=localhost
postgres.port=5432
postgres.db=vericrop
postgres.user=vericrop
postgres.password=vericrop123

# Connection pool
db.pool.size=10
db.connection.timeout=30000
```

---

For more information, see the [Main README](../README.md)
