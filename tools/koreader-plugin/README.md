# Booklore KOReader Plugin

Automatically sync your reading sessions from KOReader to your Booklore server.

## Features

- **Automatic Session Tracking**: Records reading time, progress, and location
- **REST API Integration**: Uses standard Booklore endpoints (no WebDAV needed)
- **Book Matching**: Automatically matches books using MD5 hash
- **Offline Support**: Queues sessions when offline, syncs when connection is available
- **Configurable Settings**: Control minimum session duration and manage pending syncs
- **Background Sync**: Syncs on document close and device suspend/resume

## Installation

1. Copy the `booklore.koplugin` folder to your KOReader plugins directory:
   - **Android**: `/sdcard/koreader/plugins/`
   - **Kobo**: `.adds/koreader/plugins/`
   - **Kindle**: `extensions/koreader/plugins/`

2. Restart KOReader

## Configuration

1. Open KOReader
2. Go to **Menu → Tools → More Tools → Booklore Sync**
3. Configure settings:
   - **Enable Sync**: Toggle to enable/disable automatic syncing
   - **Server URL**: Your Booklore server address (e.g., `http://192.168.1.100:6060`)
   - **Username**: Your Booklore username
   - **Password**: Your Booklore password
   - **Test Connection**: Verify your server settings
   - **Sync Pending Sessions**: Manually sync queued offline sessions
   - **Clear Pending Sessions**: Clear the offline sync queue
   - **View Pending Count**: See how many sessions are queued for sync
   - **Sync Historical Data**: Sync historical data from koreader to booklore

## Usage

Once configured, the plugin works automatically in the background:

### Session Lifecycle
- **Book opens**: Session tracking begins; plugin looks up book ID from local cache or queries the server
- **Reading**: Tracks your current page/location and progress throughout the session
- **Book closes**: Session ends and syncs to server with duration, progress delta, and location data
- **Device suspends**: Active session is saved when the e-reader goes to sleep
- **Device resumes**: Pending sessions are automatically synced when waking from sleep

### Online vs Offline Behavior
- **Online**: Sessions sync immediately to the server; book IDs are cached locally for faster lookups
- **Offline**: Sessions are queued locally and will sync automatically when connection is restored
- **Partial connectivity**: If book ID lookup fails, session is queued with book hash for later resolution
- **Success notifications**: Visual feedback confirms when sessions sync successfully

### Offline Support

The plugin includes robust offline functionality:
- Sessions are queued locally when the server is unreachable
- Automatically attempts to sync pending sessions on device wake and after successful syncs
- Manual sync available via **Sync Pending Sessions** menu option
- View pending session count in the menu

**Note**: Sessions shorter than 5 seconds are not recorded to avoid false triggers.

## Requirements

- Booklore server with KOReader integration enabled
- Books must exist in your Booklore library (matched by partial MD5 hash)
- Network connectivity to your Booklore server
- KOReader with Lua plugin support

## Data Synced

Each reading session includes:
- Book ID (matched by MD5 hash)
- Start/end time (ISO 8601 format)
- Duration in seconds
- Start/end progress (0.0 to 1.0)
- Progress delta (pages read)
- Start/end location (page number or position)
- Book type (PDF, EPUB, etc.)

## Historical Data

Historical data calculates reading sessions just like the original koreader plugin. After that, it attempts to get the bookId from booklore, and sync the session.

**Important**: Right now, the historical data is pushed to the upload date, but shows up in the weekly overview at the correct time/date

## Troubleshooting

### Sessions are queued but not syncing
- Check that **Enable Sync** is turned on
- Verify server URL is correct (include `http://` or `https://`)
- Test connection using **Test Connection** menu option
- Manually trigger sync with **Sync Pending Sessions**
- Check network connectivity (WiFi must be enabled)

### "Could not find book on server" / Book tracking offline
- The book doesn't exist in your Booklore library yet
- Session will be queued with the book hash for later resolution
- Add the book to Booklore, then use **Sync Pending Sessions**
- The plugin will resolve the book ID and sync the queued sessions

### "Connection failed" errors
- **401/403**: Check username and password in plugin settings
- **404**: Verify server URL and that Booklore server is running
- **Network error**: Check WiFi is enabled and server is reachable
- Verify you can access the Booklore web interface

### Sessions too short not recording
- Default minimum duration is 5 seconds
- This prevents false triggers from quick book opens/closes
- Sessions shorter than this threshold are automatically discarded

### Viewing debug information
- Check KOReader logs for detailed error messages
- Use **View Pending Count** to see queued sessions
- Connection test will show specific error codes

## Development

The plugin uses:
- Standard KOReader plugin API
- `socket.http` and `ltn12` for HTTP requests
- `json` for encoding session data
- `ffi/sha2` for computing partial MD5 hashes
- `DataStorage` for storing plugin settings

### API Endpoints

- `GET /api/koreader/users/auth` - Authentication check (test connection)
- `GET /api/koreader/books/by-hash/{md5}` - Book lookup by partial MD5 hash
- `POST /api/v1/reading-sessions` - Record reading session

### Event Handlers

The plugin hooks into KOReader lifecycle events:
- `onReaderReady()` - Starts session tracking when document opens
- `onCloseDocument()` - Ends and syncs session when document closes
- `onSuspend()` - Saves session when device goes to sleep
- `onResume()` - Syncs pending sessions when device wakes up

### Hash Algorithm

The plugin uses a partial MD5 algorithm matching Booklore's FileFingerprint:
- Samples 1KB blocks at positions: `base << (2*i)` for i=-1 to 10
- Base: 1024 bytes
- Positions: 512, 2048, 8192, 32768, 131072, 524288, 2097152, 8388608, 33554432, 134217728, 536870912, 2147483648
