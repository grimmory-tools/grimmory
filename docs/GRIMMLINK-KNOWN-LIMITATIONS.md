# GrimmLink Known Limitations

## EPUB Web Reader CFI Bridge

The EPUB Web Reader CFI bridge is intentionally **not supported**. EPUB progress should use KOReader-native progress rather than CFI-based position tracking.

## Metadata Pull Scope

Metadata pull may skip item types that KOReader cannot safely apply on the target device. Only well-understood metadata fields are pushed/pulled; unknown or structurally incompatible items are silently omitted.

## Server Library Files

This fork **does not delete server library files** in response to KOReader shelf removal actions unless that behaviour is explicitly implemented and documented. Removing a book from a device-side shelf removes the shelf entry only — the server's library file is unaffected.

## OPF Adjacent Metadata

OPF adjacent metadata extraction is **best-effort**. It runs during scan/import and the Refresh Metadata task, but may not handle every OPF variant or edge case. Malformed OPF/RDF files are skipped and do not interrupt the overall refresh.

## Magic Shelf and Shelf Sync

Magic shelves and shelf sync rely on server-side query logic that differs from KOReader's native shelf evaluation. Always test magic shelf behaviour on a real device before release.
