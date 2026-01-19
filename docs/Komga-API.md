# Komga API Support

Booklore provides a Komga-compatible API that allows you to use Komga clients (like Tachiyomi, Tachidesk, Komelia, etc.) to access your Booklore library.

## Features

The Komga API implementation in Booklore provides the following endpoints:

### Libraries
- `GET /api/v1/libraries` - List all libraries
- `GET /api/v1/libraries/{libraryId}` - Get library details

### Series
- `GET /api/v1/series` - List series (supports pagination and library filtering)
- `GET /api/v1/series/{seriesId}` - Get series details
- `GET /api/v1/series/{seriesId}/books` - List books in a series
- `GET /api/v1/series/{seriesId}/thumbnail` - Get series thumbnail

### Books
- `GET /api/v1/books` - List all books (supports pagination and library filtering)
- `GET /api/v1/books/{bookId}` - Get book details
- `GET /api/v1/books/{bookId}/pages` - Get book pages metadata
- `GET /api/v1/books/{bookId}/pages/{pageNumber}` - Get book page image
- `GET /api/v1/books/{bookId}/file` - Download book file
- `GET /api/v1/books/{bookId}/thumbnail` - Get book thumbnail

### Users
- `GET /api/v2/users/me` - Get current user details

## Data Model Mapping

Booklore organizes books differently than Komga:

- **Komga**: Libraries → Series → Books
- **Booklore**: Libraries → Books (with optional series metadata)

The Komga API layer automatically creates virtual "series" by grouping books with the same series name in their metadata. Books without a series name are grouped under "Unknown Series".

## Enabling the Komga API

1. Navigate to **Settings** in Booklore
2. Find the **Komga API** section
3. Toggle **Enable Komga API** to ON
4. Click **Save**

## Authentication

The Komga API uses the same OPDS user accounts for authentication. To access the Komga API:

1. Create an OPDS user account in Booklore settings
2. Use those credentials when configuring your Komga client

Authentication uses HTTP Basic Auth, the same as OPDS.

## Using with Komga Clients

### Tachiyomi / TachiyomiSY / TachiyomiJ2K

1. Install the Tachiyomi app
2. Add a source → Browse → Sources → Komga
3. Configure the source:
   - Server URL: `http://your-booklore-server/`
   - Username: Your OPDS username
   - Password: Your OPDS password

### Komelia

1. Install Komelia
2. Add a server:
   - URL: `http://your-booklore-server/`
   - Username: Your OPDS username
   - Password: Your OPDS password

### Tachidesk

1. Install Tachidesk
2. Add Komga extension
3. Configure:
   - Server URL: `http://your-booklore-server/`
   - Username: Your OPDS username
   - Password: Your OPDS password

## Limitations

- Individual page extraction is not yet implemented; page requests return the book cover
- Read progress tracking from Komga clients is not synchronized with Booklore
- Not all Komga API endpoints are implemented (only the most commonly used ones)

## Troubleshooting

### Cannot connect to server

- Ensure the Komga API is enabled in Booklore settings
- Verify your OPDS credentials are correct
- Check that your server is accessible from the client device

### Books not appearing

- Ensure books have metadata populated, especially series information
- Try refreshing the library in your Komga client

### Authentication failures

- The Komga API uses OPDS user accounts, not your main Booklore account
- Create an OPDS user in the Settings → OPDS section
- Use those credentials in your Komga client

## API Compatibility

The Booklore Komga API aims to be compatible with Komga v1.x API. While not all endpoints are implemented, the core functionality needed for reading and browsing is supported.

For the complete Komga API specification, see: https://github.com/gotson/komga
