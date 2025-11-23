# Contacts and Messaging Feature

## Overview

The VeriCrop GUI application now includes a comprehensive contacts and messaging system that allows different GUI instances to discover each other and exchange messages. This feature enables real-time collaboration between farmers, suppliers, consumers, and other supply chain participants.

## Features

### 1. Participant Tracking
- **Automatic Registration**: GUI instances register themselves as participants when they start
- **Online Status**: Real-time tracking of participant online/offline status
- **Connection Information**: Store and retrieve connection details for direct communication
- **Last Seen Tracking**: Monitor participant activity with 5-minute online threshold

### 2. Contact Discovery
- **Contact List**: View all active participants in the system
- **Role-Based Display**: See participant roles (FARMER, SUPPLIER, CONSUMER, ADMIN)
- **Status Indicators**: Visual indicators for online (🟢) and offline (⚫) status
- **Auto-Refresh**: Contacts list automatically refreshes every 5 seconds

### 3. Messaging
- **Send Messages**: Send messages directly to any contact
- **Message History**: View complete conversation history with each contact
- **Subject Support**: Optional message subjects for better organization
- **Read Receipts**: Track when messages are read
- **Message Bubbles**: Chat-like interface for easy conversation flow

## Architecture

### Database Schema

#### Participants Table
```sql
CREATE TABLE participants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    instance_id VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    connection_info JSONB,
    gui_version VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### Messages Table (Existing)
The existing `messages` table stores all messages between users:
```sql
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    subject VARCHAR(255),
    body TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    ...
);
```

### Backend Components

#### Models
- **Participant**: Represents a GUI instance with connection and status information
- **Message**: Represents a message between users

#### DAOs (Data Access Objects)
- **ParticipantDao**: CRUD operations for participants
  - `registerParticipant()`: Register or update a participant (upsert)
  - `getActiveParticipants()`: Get all active participants
  - `findById()`: Find participant by ID
  - `findByInstanceId()`: Find participant by instance ID
  - `updateLastSeen()`: Update participant's last activity timestamp
  - `updateStatus()`: Update participant status
  - `markInactiveParticipants()`: Mark stale participants as disconnected

- **MessageDao**: CRUD operations for messages (existing)

#### REST API Endpoints

##### Contacts Management (`/api/contacts`)
- `GET /api/contacts` - List all active contacts
  - Query params: `excludeInstanceId` (optional)
  - Response: `{ success, count, contacts[] }`

- `GET /api/contacts/{id}` - Get contact details
  - Response: `{ success, contact }`

- `POST /api/contacts/register` - Register current participant
  - Body: `{ userId, instanceId, displayName, connectionInfo, guiVersion, status }`
  - Response: `{ success, participant }`

- `PUT /api/contacts/heartbeat/{instanceId}` - Update last seen timestamp
  - Response: `{ success }`

- `PUT /api/contacts/status/{instanceId}` - Update participant status
  - Query params: `status`
  - Response: `{ success }`

##### Messaging (`/api/messages`)
- `POST /api/messages` - Send message to contact
  - Body: `{ to_contact_id, from_user_id, subject, body }`
  - Response: `{ success, message_id, sent_at }`

- `GET /api/messages` - Get messages with contact
  - Query params: `contact_id` (optional), `user_id` (required)
  - Response: `{ success, count, messages[] }`

- `PUT /api/messages/{messageId}/read` - Mark message as read
  - Response: `{ success }`

### Frontend Components

#### Views
- **contacts.fxml**: Main contacts view with split-pane layout
  - Left pane: Contacts list with status indicators
  - Right pane: Contact details and messaging interface

#### Controllers
- **ContactsViewController**: Manages the contacts UI
  - Contact list management
  - Contact selection and display
  - Message sending and history
  - Auto-refresh timer (5-second interval)

#### Navigation
Contacts screen is accessible from all main views:
- Producer Dashboard (👥 Contacts button)
- Logistics View (👥 Contacts button)
- Analytics View (👥 Contacts button)

## Usage

### Accessing Contacts

1. Log in to any VeriCrop GUI instance
2. From the main dashboard, click the **👥 Contacts** button
3. The contacts screen will display all active participants

### Viewing Contacts

- **Online Contacts**: Shown with 🟢 indicator
- **Offline Contacts**: Shown with ⚫ indicator
- **Contact Info**: Click any contact to view details including:
  - Display name
  - Username
  - Role
  - Status
  - Last seen timestamp

### Sending Messages

1. Select a contact from the list
2. In the right pane, enter your message:
   - Subject (optional)
   - Message body (required)
3. Click **Send** button
4. Message will be delivered immediately to the recipient's inbox

### Viewing Message History

1. Select a contact from the list
2. Click **Load Messages** button
3. All messages to/from that contact will display in chronological order
4. Your messages appear in blue bubbles (right-aligned)
5. Contact's messages appear in gray bubbles (left-aligned)

### Auto-Refresh

The contacts list automatically refreshes every 5 seconds to show:
- New participants joining
- Status changes (online/offline)
- Participant disconnections

## Testing Between Multiple GUI Instances

### Setup

1. **Start First Instance**:
   ```bash
   ./gradlew :vericrop-gui:run
   ```
   - Log in as `farmer` (password: `farmer123`)

2. **Start Second Instance** (different terminal):
   ```bash
   ./gradlew :vericrop-gui:run
   ```
   - Log in as `supplier` (password: `supplier123`)

3. **Start Third Instance** (optional):
   ```bash
   ./gradlew :vericrop-gui:run
   ```
   - Log in as `admin` (password: `admin123`)

### Testing Flow

1. **Register Participants**:
   - Each instance should auto-register when accessing contacts
   - Check logs for registration confirmation

2. **Discover Contacts**:
   - Navigate to Contacts screen in each instance
   - Verify all other instances appear in the contacts list
   - Check online status indicators

3. **Send Messages**:
   - From Instance 1, select Instance 2 from contacts
   - Send a test message: "Hello from farmer!"
   - Verify message appears in conversation history

4. **Receive Messages**:
   - In Instance 2, click Messages (💬) button to view inbox
   - Verify message from Instance 1 appears
   - Click to read the message

5. **Two-Way Communication**:
   - From Instance 2, navigate to Contacts
   - Select Instance 1 from contacts list
   - Load message history to see previous messages
   - Send a reply: "Hello back from supplier!"

6. **Status Updates**:
   - Close Instance 3
   - Wait 5+ minutes (or run cleanup job)
   - Verify Instance 3 shows as offline in other instances

## Configuration

### Database Connection

Ensure PostgreSQL is running and configured in `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/vericrop
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Migration

The participants table is created automatically via Flyway migration V6:
```
src/main/resources/db/migration/V6__create_participants_table.sql
```

### Auto-Refresh Interval

To change the contacts refresh interval, modify `ContactsViewController`:
```java
private static final int REFRESH_INTERVAL_MS = 5000; // Change this value
```

## Troubleshooting

### Contacts Not Appearing

1. **Check Database Connection**: Ensure PostgreSQL is running
2. **Verify Migration**: Check that V6 migration ran successfully
   ```sql
   SELECT * FROM flyway_schema_history WHERE script = 'V6__create_participants_table.sql';
   ```
3. **Check Logs**: Look for participant registration messages in console

### Messages Not Sending

1. **Verify User Authentication**: Ensure logged in user exists in database
2. **Check Contact Status**: Ensure selected contact is active
3. **Review Logs**: Check for error messages in application logs

### Offline Status Not Updating

1. **Auto-Refresh**: Verify auto-refresh timer is running (check logs)
2. **Time Threshold**: Default threshold is 5 minutes - wait longer or reduce threshold
3. **Manual Refresh**: Click Refresh button to force update

### Performance Issues

1. **Reduce Refresh Interval**: Increase `REFRESH_INTERVAL_MS` to reduce load
2. **Limit Results**: Modify queries to return fewer participants
3. **Add Indexes**: Ensure database indexes are created (done in migration)

## Future Enhancements

### Planned Features

1. **Real-Time Delivery**: WebSocket support for instant message delivery
2. **Message Notifications**: Toast/popup notifications for new messages
3. **Group Messaging**: Send messages to multiple contacts simultaneously
4. **File Attachments**: Share files and images with contacts
5. **Message Search**: Search through message history
6. **Contact Groups**: Organize contacts by role or custom groups
7. **Presence System**: More detailed presence information (typing, idle, etc.)
8. **Message Reactions**: React to messages with emojis
9. **Message Threading**: Reply to specific messages in threads

### Integration Points

1. **Kafka Integration**: Route messages through Kafka for guaranteed delivery
2. **Blockchain Integration**: Store message hashes on blockchain for audit trail
3. **Push Notifications**: Mobile push notifications for new messages
4. **Email Fallback**: Email notifications when participant is offline

## API Examples

### Register Participant
```bash
curl -X POST http://localhost:8080/api/contacts/register \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "instanceId": "gui-instance-abc123",
    "displayName": "John Farmer",
    "guiVersion": "1.0.0",
    "connectionInfo": {
      "host": "localhost",
      "port": 8080,
      "endpoint": "/api"
    }
  }'
```

### List Contacts
```bash
curl http://localhost:8080/api/contacts?excludeInstanceId=gui-instance-abc123
```

### Send Message
```bash
curl -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -d '{
    "to_contact_id": 2,
    "from_user_id": 1,
    "subject": "Quality Check",
    "body": "The batch quality looks good. Ready to ship."
  }'
```

### Get Messages with Contact
```bash
curl "http://localhost:8080/api/messages?contact_id=2&user_id=1"
```

## License

This feature is part of the VeriCrop project. See main README for license information.

## Support

For issues or questions about the contacts and messaging feature:
1. Check this documentation
2. Review application logs
3. Check database connectivity
4. Verify all migrations have run
5. Open an issue on GitHub with detailed information
