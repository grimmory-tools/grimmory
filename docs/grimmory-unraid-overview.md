# Grimmory on unRAID: Overview

This setup is a controlled adoption test for Grimmory on unRAID.

The goal is not to make Grimmory and Calibre share one live library forever. The goal is to prove that Grimmory can replace Calibre for your real workflow. If the test works well, the Grimmory-managed clone becomes the promotion candidate for your future primary library.

## What We Are Testing

- Grimmory can scan and use a cloned copy of the rebuilt Calibre library
- Grimmory can read representative EPUB and PDF titles successfully
- Grimmory can import new files through BookDrop
- Grimmory can perform real file operations safely on the clone
- Grimmory can preserve state cleanly across container restarts

## What We Are Not Doing

- We are not pointing Grimmory at the live Calibre library for write testing
- We are not designing a permanent shared-write setup between Calibre and Grimmory
- We are not using the unRAID Docker template UI for this phase

## Source Of Truth

The current live Calibre rebuild lives at:

- `/mnt/m2cache/calibre-cleanup-0326/CalibreLibrary-New`

Relevant operating notes came from:

- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-rebuild.md`
- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-web.md`
- `/Users/petetreadaway/Projects/new-calibre-library-import/docs/calibre-remaining-metadata-handover.md`

## Test Layout

Use a dedicated Grimmory test workspace on unRAID:

- cloned Grimmory test library:
  `/mnt/user/data/grimmory-test/CalibreLibrary-GrimmoryTest`
- BookDrop folder:
  `/mnt/user/data/grimmory-test/bookdrop`

Keep BookDrop as a sibling of the cloned library, not inside the library root. That avoids double-discovery where Grimmory could see the same files both as library content and as pending BookDrop imports.

## Existing Live Calibre Services

The live Calibre interfaces remain unchanged during testing:

- Calibre-Web: `http://100.85.214.86:8083`
- Calibre desktop admin: `https://100.85.214.86:8181`

Those services continue to point at the live Calibre library while Grimmory points at the clone.

## Success Criteria

The test is good enough to consider promotion if all of the following are true:

- Grimmory stack starts cleanly on unRAID
- initial library scan succeeds
- sample books open correctly
- BookDrop ingest works end to end
- rename/move/delete-style tests work on sacrificial titles in the clone
- restarts do not break the library or BookDrop state
- the live Calibre deployment remains untouched during the test

## Next Docs

- Technical runbook: [grimmory-unraid-runbook.md](/Users/petetreadaway/Projects/grimmory/docs/grimmory-unraid-runbook.md)
- Cutover path: [grimmory-cutover.md](/Users/petetreadaway/Projects/grimmory/docs/grimmory-cutover.md)
- Private environment details: [README.md](/Users/petetreadaway/Projects/grimmory/docs/private/README.md)
