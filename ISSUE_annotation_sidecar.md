# Feature: Annotation & Note Sidecar Files with KOReader Sync

## What's Your Idea?

Store highlights and notes as KOReader-compatible sidecar files (`.sdr/metadata.epub.<username>.lua`) alongside each book in the library, in addition to the database, and extend the existing KOReader Sync endpoints to include annotations alongside reading progress.

## Why Would This Be Helpful?

Right now, highlights and notes only exist in the database. If the database is lost or corrupted, all annotations are permanently gone with no way to recover them from the book files themselves.

This also makes cross-device reading painful. A user reading across the web reader, a Kobo running KOReader, and a mobile browser has no way to get highlights from one surface to another — reading progress syncs via the existing KOReader endpoint, but highlights and notes do not travel at all.

There are two complementary improvements that together solve this:

**1. Sidecar files alongside books**

Write annotations to KOReader's `.sdr/metadata.epub.<username>.lua` format alongside each EPUB in the library. This gives:

- **Resilience** — annotations survive a database loss and can be rebuilt by scanning the library folder, the same way book metadata already can be
- **Grimmory → KOReader via OPDS** — when a user downloads a book through OPDS, the sidecar travels with it and KOReader picks up the highlights on the device automatically, with no plugin or configuration change needed
- **Portability** — copy a book and its `.sdr` folder anywhere and highlights come with it

**2. Extend KOReader Sync endpoints to include annotations**

This path applies to Kobo users who have KOReader installed on their device. The existing `/api/koreader` endpoints handle reading progress. Adding a `GET` endpoint for annotations at the same base path would give users who already have KOReader Sync pointed at Grimmory a way to pull their web highlights down to their device — no new configuration needed.

This is not a complete bidirectional sync solution. KOReader → Grimmory would require either a future `PUT` endpoint extension or a plugin on the KOReader side that writes to the `.sdr` sidecar, which Grimmory would then pick up during a library scan.

Users running the **native Kobo firmware** (without KOReader) would only benefit from the sidecar path above — highlights made in the native Kobo reader would not sync back to Grimmory without a separate Kobo annotation sync integration.

## Design Decisions

**Database remains the source of truth.** Sidecar files are a secondary backup and sync mechanism, not the primary store. The DB is what the application reads at runtime. Sidecars are written as a side effect of annotation mutations and are best-effort (a write failure does not roll back the database transaction).

**Sidecar feature is optional and disabled by default**, consistent with the existing metadata sidecar behaviour. Users enable it explicitly, and import/export can be triggered at any time via the task manager.

**User identifiers appear in both the filename and the file content.**

- Filename: `metadata.epub.<username>.lua` — follows the KOReader per-device convention, making the file discoverable by KOReader and compatible third-party tools without parsing content.
- File content: a `booklore_meta` table at the top of the file stores `username`, `user_id`, `book_id`, `exported_at`, and `schema_version`. This makes the file self-describing even if renamed, and the `user_id` provides a stable identifier for import when a username has changed.

**Format: KOReader's established `.sdr/` Lua format, extended with a vendor namespace.**

The base format is KOReader's own — the closest thing to an open community standard for EPUB annotation sidecars, used by Calibre plugins, Obsidian importers, and a number of other tools. This maximises compatibility: any tool that already reads `.sdr/` files works out of the box with Grimmory-generated sidecars.

A `booklore` sub-table is added to each highlight entry. KOReader ignores keys it doesn't recognise, so existing KOReader behaviour is unaffected. The extension block stores fields not representable in the KOReader format (`color`, original `style`, the user's typed `note`, and a stable `id`), enabling lossless round-trip import back into the database.

A schema version hint (`-- booklore:schema_version=1`) is emitted in a comment so a future parser can fast-fail on an unknown format version without needing to parse the Lua table.

Example file:

```lua
-- KOReader bookmark file
-- version: 1
-- booklore:schema_version=1
{
    ["booklore_meta"] = {
        ["username"] = "alice",
        ["user_id"] = 7,
        ["book_id"] = 42,
        ["exported_at"] = "2024-01-15 10:30:00",
        ["schema_version"] = 1,
    },
    ["highlights"] = {
        [1] = {
            -- KOReader fields (used by KOReader, ignored otherwise)
            ["chapter"] = "Chapter One",
            ["datetime"] = "2024-01-15 10:30:00",
            ["drawer"] = "lighten",
            ["notes"] = "the highlighted text",
            ["page"] = "/body/DocFragment[1]/body/p",
            ["pos0"] = "/body/DocFragment[1]/body/p/text().5",
            ["pos1"] = "/body/DocFragment[1]/body/p/text().20",
            -- Grimmory extension (ignored by KOReader)
            ["booklore"] = {
                ["id"] = 42,
                ["color"] = "#FFFF00",
                ["style"] = "highlight",
                ["note"] = "My personal comment on this passage",
                ["version"] = 3,
            },
        },
    },
    ["bookmarks"] = {},
}
```

## Anything Else? (Optional)

The main design decision would be conflict resolution when annotations diverge between the sidecar/KOReader and the database (e.g. a highlight made on the Kobo while offline, and a different highlight made in the web reader on the same passage). Last-write-wins is probably the pragmatic starting point.

A lot of the groundwork is already in place: `CfiConvertor` and `EpubCfiService` handle conversion between Grimmory's EPUB CFI format and KOReader's XPointer (`pos0`/`pos1`) format, and `SidecarMetadataWriter` establishes the pattern for writing files alongside books.

## Want to Help Out?

I have a working implementation ready for review covering:

1. **`fix(cfi)`** — `CfiConvertor.cfiToXPointer` now correctly handles range CFIs (both the compact epub.js form and the full-paths form), returning distinct `pos0`/`pos1` for highlights
2. **`feat(sidecar)`** — `AnnotationSidecarService` writes the per-user `.sdr/` sidecar on every annotation mutation (create, update, delete), with atomic writes and full exception isolation
3. **`feat(sidecar)`** — `AnnotationService` triggers sidecar refresh after each mutation
4. **`feat(koreader)`** — `GET /api/koreader/syncs/annotations/{bookHash}` endpoint so KOReader devices can pull highlights via the existing kosync credentials
5. **`feat(sidecar)`** — `booklore_meta` and per-entry `booklore` extension blocks for lossless round-trip import
6. **`feat(sidecar)`** — `SidecarAnnotationParser` and `AnnotationSidecarImporter` plus a new `IMPORT_SIDECAR_ANNOTATIONS` task type, allowing admins to restore annotations from sidecar files after a database loss

## Have You Considered Any Alternatives? (Optional)

Calibre-web was the obvious comparison point but it stores annotations in its own SQLite database with the same fragility. KOReader's sidecar approach is the only self-hosted solution that actually keeps annotations durable alongside the book file.

## Before Submitting

- I've searched existing issues and confirmed this feature hasn't been requested yet
