# Booklore KOReader Plugin

Automatically sync your reading sessions from KOReader to your Booklore server.

## Installation

Copy the `booklore.koplugin` folder to your KOReader plugins directory:

- **Android**: `/sdcard/koreader/plugins/`
- **Kobo**: `.adds/koreader/plugins/`
- **Kindle**: `extensions/koreader/plugins/`

Restart KOReader.

## Configuration

1. Open KOReader → **Menu → Tools → Booklore Sync**
2. Configure settings:
   - **Server URL**: Your Booklore server (e.g., `http://192.168.1.100:6060`)
   - **Username**: Your Booklore KOReader username
   - **Password**: Your Booklore KOReader password
   - **Test Connection**: Verify settings before enabling
3. Enable **Enable Sync** to start automatic syncing

## Usage

The plugin works automatically:

- **Reading**: Tracks your progress and location
- **Book closes**: Syncs session to server
- **Offline**: Sessions are queued and sync when connection restores
- **Device sleep/wake**: Saves and syncs pending sessions

### Gesture Controls

Booklore Sync registers customizable actions with KOReader's gesture manager. You can assign any gesture to these actions:

**Available Actions:**

- **Toggle Booklore Sync**: Toggle sync on/off
- **Sync Pending Sessions**: Manually trigger a sync (only if sync is enabled and sessions are queued)
- **Test Booklore Connection**: Test your server connection

**To assign gestures:**

1. Open KOReader → **Menu → Tools → Gesture Manager**
2. Select the Gesture you want to assign the action to
3. Select the desired action from the `General` section

## Syncing Historical Data

Use **Sync Historical Data** to sync past reading sessions from KOReader to Booklore.

**Note**: Some sessions may not sync if book hashes don't match between KOReader and Booklore.

## Troubleshooting

- **Sessions not syncing**: Check server URL, credentials, and network connectivity
- **Book not found**: Add the book to Booklore library, then use **Sync Pending Sessions**
- **Connection errors**: Verify Booklore server is running and accessible

## Requirements

- Booklore server with KOReader integration enabled
- Books must exist in your Booklore library (matched by MD5 hash)
- Network connectivity to your Booklore server

Further documentation can be found over in the [docs](link to docs here)
