# Grimmory on unRAID: Overview

This setup has now been promoted from a controlled adoption test to the primary Grimmory library on unRAID.

The goal is still not to make Grimmory and Calibre share one live library forever. Grimmory is now the active system of record for the primary library, and the old Calibre surfaces are retained only as a short rollback reference.

## Current Primary State

- Grimmory runs against the primary library at `/mnt/m2cache/grimmory-test/CalibreLibrary-GrimmoryTest`
- Grimmory imports new content through BookDrop at `/mnt/m2cache/grimmory-test/bookdrop`
- MAM/qBittorrent seeds from `/mnt/m2cache/MAM-QBTorrent` and hardlinks newly completed torrents into BookDrop
- Calibre-Web and Calibre desktop are no longer active writers for the primary library

## What We Are Not Doing

- We are not designing a permanent shared-write setup between Calibre and Grimmory
- We are not writing to the retained legacy Calibre tree during the rollback window

## Source Of Truth

The current primary Grimmory library lives at:

- `/mnt/m2cache/grimmory-test/CalibreLibrary-GrimmoryTest`

The retained legacy Calibre library for rollback only lives at:

- `/mnt/m2cache/calibre-cleanup-0326/CalibreLibrary-New`

Relevant operating notes came from:

- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-rebuild.md`
- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-web.md`
- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-remaining-metadata-handover.md`

## Active Layout

Use the existing Grimmory workspace on unRAID as the primary layout:

- primary Grimmory library:
  `/mnt/m2cache/grimmory-test/CalibreLibrary-GrimmoryTest`
- BookDrop folder:
  `/mnt/m2cache/grimmory-test/bookdrop`

Keep BookDrop as a sibling of the cloned library, not inside the library root. That avoids double-discovery where Grimmory could see the same files both as library content and as pending BookDrop imports.

## Retained Legacy Calibre Services

The old Calibre interfaces are retained only for rollback:

- Calibre-Web: `http://100.85.214.86:8083`
- Calibre desktop admin: `https://100.85.214.86:8181`

Those services point at the retained legacy Calibre library and should remain stopped unless rollback work is required.

## Operational Expectations

- Grimmory remains the only active writer for the primary library
- BookDrop ingest stays one-way from qBittorrent into Grimmory
- The retained Calibre tree is used only if rollback becomes necessary
- The rollback window should expire before any permanent deletion of the retained Calibre surfaces

## Next Docs

- Technical runbook: [grimmory-unraid-runbook.md](/Users/petetreadaway/Projects/grimmory/docs/grimmory-unraid-runbook.md)
- Cutover path: [grimmory-cutover.md](/Users/petetreadaway/Projects/grimmory/docs/grimmory-cutover.md)
- Private environment details: [README.md](/Users/petetreadaway/Projects/grimmory/docs/private/README.md)
