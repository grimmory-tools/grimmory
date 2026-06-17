# GrimmLink Release Overview

This is a **Grimmory fork** with integrated **GrimmLink / KOReader** support.

The upstream project remains [Grimmory](https://github.com/grimmory-tools/grimmory). Fork-specific additions are isolated to minimize upstream merge conflicts.

## Additions vs Upstream

- **GrimmLink API**: `/api/grimmlink/v1/**` — separate namespace, no upstream route changes.
- **Fork migrations**: `V9001+` block, reserved to avoid collision with upstream Flyway migrations.
- **KOReader plugin**: standalone [GrimmLink](https://github.com/0xstillb/GrimmLink) plugin talks to this API.

## Main Features

| Feature | Description |
| :------ | :---------- |
| **KOReader Auth** | Token-based authentication for KOReader devices |
| **Progress Sync** | Push/pull reading progress between KOReader and server |
| **WebUI Progress** | KOReader reading percentage shown in the Web UI |
| **PDF Web Reader Bridge** | Cross-reference PDF progress between KOReader and built-in reader |
| **Reading Sessions** | Push and query per-book reading sessions |
| **Shelf Sync** | Sync KOReader shelves (regular + magic) with server shelves |
| **Metadata Push/Pull** | Push ratings, bookmarks, annotations; pull metadata to other devices |
| **OPF Adjacent Metadata** | Best-effort OPF metadata extraction during scan and refresh |

## Documentation Index

| File | Purpose |
| :--- | :------ |
| `GRIMMLINK-RELEASE-CHECKLIST.md` | QA checklist for a release candidate |
| `GRIMMLINK-KNOWN-LIMITATIONS.md` | Known limits and design decisions |
| `GRIMMLINK-INSTALL-UPGRADE.md` | Install and upgrade notes |
| `GRIMMLINK-V1-API.md` | API reference |
| `TESTING-GRIMMLINK.md` | Testing checklist |
| `UPSTREAM-FRIENDLY-MAINTENANCE.md` | Rules for keeping fork work reviewable |
