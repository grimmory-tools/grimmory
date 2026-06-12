# GrimmLink V1 API

The public GrimmLink backend contract is exposed under:

```text
/api/grimmlink/v1
```

This document is a route and behavior overview. Request and response DTOs in
`org.booklore.grimmlink` remain the source of truth for field-level details.

## Authentication

Every protected request uses these headers:

```text
x-auth-user
x-auth-key
```

## Endpoints

All paths below are relative to `/api/grimmlink/v1`.

### Auth

```http
GET /auth
```

### Books

```http
GET /books/by-hash/{bookHash}
GET /books/read-statuses
PUT /books/{bookId}/status
GET /books/{bookId}/download
```

### Progress

```http
GET /syncs/progress/{bookHash}
PUT /syncs/progress
GET /books/{bookId}/pdf-progress
PUT /books/{bookId}/pdf-progress
```

### Content / Activity

```http
POST /reading-sessions
POST /reading-sessions/batch
GET /shelves
GET /shelves/{shelfId}/books
GET /shelves/{shelfType}/{shelfId}/books
POST /shelves/{shelfId}/books/{bookId}/remove
POST /shelves/{shelfType}/{shelfId}/books/{bookId}/remove
```

### Metadata

```http
POST /syncs/metadata/batch
```

## Contract Behavior

- Book matching checks both `currentHash` and `initialHash`, preferring the
  current hash when both can resolve.
- EPUB percentage is mirrored to Web UI progress.
- Read status is derived from the real reading percentage.
- A newer manual read-status update is preserved instead of being overwritten
  by older progress data.
- The PDF bridge detects conflicting updates and supports an explicit
  `force=true` override.
- Reading-session writes are idempotent, including batch submissions.
- Removing a book from a regular shelf removes only the shelf membership; it
  does not delete the server library file.
- Removing a book from a magic shelf is unsupported because membership is
  rule-derived.
- Metadata pull responses include `deviceId` so clients can skip reapplying
  metadata written by the same device.
