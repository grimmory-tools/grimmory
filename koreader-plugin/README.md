# Booklore KOReader Plugin

Automatically sync your reading sessions from KOReader to your Booklore server.

## Features

- **Automatic Session Tracking**: Records reading time, progress, and location
- **REST API Integration**: Uses standard Booklore endpoints (no WebDAV needed)
- **Book Matching**: Automatically matches books using MD5 hash
- **Configurable Sync**: Control when and how often to sync
- **Background Sync**: Syncs on document close, suspend, and app exit

## Installation

1. Copy the `booklore.koplugin` folder to your KOReader plugins directory:
   - **Android**: `/sdcard/koreader/plugins/`
   - **Kobo**: `.adds/koreader/plugins/`
   - **Kindle**: `extensions/koreader/plugins/`

2. Restart KOReader

## Configuration

1. Open KOReader
2. Go to **Menu → Tools → Booklore Sync**
3. Configure:
   - **Server URL**: Your Booklore server address (e.g., `http://192.168.1.100:6060`)
   - **Username**: Your Booklore username
   - **Password**: Your Booklore password
4. Enable **Enable Sync**
5. Test connection to verify settings

## Usage

Once configured, the plugin works automatically:

- **Reading starts**: Session tracking begins when you open a book
- **Close book**: Session is synced to server when you close the document or KOReader suspends
- **Progress tracking**: Tracks page changes and reading duration
- **Notifications**: Shows confirmation when sessions are synced successfully

Sessions shorter than 5 seconds are not recorded to avoid false triggers.

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
- Book type (PDF, EPUB, CBX)

## Troubleshooting

### "Could not find book on server"
- Ensure the book exists in your Booklore library
- Check that the partial MD5 hash matches (plugin uses same algorithm as Booklore)
- The book must have been added to Booklore before tracking sessions

### "Sync failed: 401" or "403"
- Check username and password in plugin settings
- Verify you can login to Booklore web interface
- Ensure KOReader integration is enabled on the server

### "Sync failed: 404"
- Book ID not found - book may have been deleted from library
- Re-add the book to Booklore

### No sync happening
- Check "Enable Sync" is turned on
- Verify server URL is correct (include `http://` or `https://`)
- Check network connectivity (WiFi must be enabled)
- Look at KOReader logs for detailed error messages

## Development

The plugin uses:
- Standard KOReader plugin API
- `socket.http` and `ltn12` for HTTP requests
- `json` for encoding session data
- `ffi/sha2` for computing partial MD5 hashes
- `DataStorage` for storing plugin settings

### API Endpoints

- `GET /api/koreader/users/auth` - Authentication check
- `GET /api/koreader/books/by-hash/{md5}` - Book lookup by partial MD5 hash
- `POST /api/koreader/reading-sessions` - Record reading session

### Hash Algorithm

The plugin uses a partial MD5 algorithm matching Booklore's FileFingerprint:
- Samples 1KB blocks at positions: `base << (2*i)` for i=-1 to 10
- Base: 1024 bytes
- Positions: 512, 2048, 8192, 32768, 131072, 524288, 2097152, 8388608, 33554432, 134217728, 536870912, 2147483648

## License

MIT License - see LICENSE file for details
