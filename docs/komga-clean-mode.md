# Komga API Clean Mode

## Overview
The Komga API now supports a `clean` query parameter that allows clients to receive cleaner, more compact JSON responses.

## Usage

Add the `clean` query parameter to any Komga API endpoint. Both syntaxes are supported:

```
# Using parameter without value
GET /komga/api/v1/series?clean
GET /komga/api/v1/books/123?clean
GET /komga/api/v1/libraries?clean

# Using parameter with explicit true value
GET /komga/api/v1/series?clean=true
GET /komga/api/v1/books/123?clean=true
GET /komga/api/v1/libraries?clean=true
```

## Behavior

When the `clean` parameter is present (either `?clean` or `?clean=true`):

1. **Lock Fields Excluded**: All fields ending with "Lock" (e.g., `titleLock`, `summaryLock`, `authorsLock`) are removed from the response
2. **Null Values Excluded**: All fields with `null` values are removed from the response
3. **Empty Arrays Excluded**: All empty arrays/collections (e.g., empty `genres`, `tags`, `links`) are removed from the response
4. **Metadata Fields**: Metadata fields like `summary`, `language`, and `publisher` that would normally default to empty strings or default values can now be `null` and thus filtered out

## Examples

### Without Clean Mode (default)
```json
{
  "title": "My Book",
  "titleLock": false,
  "summary": "",
  "summaryLock": false,
  "language": "en",
  "languageLock": false,
  "publisher": "",
  "publisherLock": false,
  "genres": [],
  "genresLock": false,
  "tags": [],
  "tagsLock": false
}
```

### With Clean Mode (`?clean` or `?clean=true`)
```json
{
  "title": "My Book"
}
```

All the Lock fields, empty strings, and null values are excluded, resulting in a much smaller response.

## Benefits

- **Reduced Payload Size**: Significantly smaller JSON responses, especially important for mobile clients or slow connections
- **Cleaner API**: Removes clutter from responses that clients typically don't need
- **Backward Compatible**: Default behavior remains unchanged; opt-in via query parameter

## Implementation Details

- Works with all Komga API GET endpoints under `/komga/api/**`
- Uses a custom Jackson serializer modifier with ThreadLocal context
- Implemented via Spring interceptor that detects the query parameter
- ThreadLocal is properly cleaned up after each request to prevent memory leaks
