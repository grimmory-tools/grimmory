# Grimmory unRAID Runbook

This runbook sets up Grimmory on unRAID against a cloned copy of the rebuilt Calibre library plus a writable BookDrop folder.

## Live Reference Environment

- live Calibre library:
  `/mnt/m2cache/calibre-cleanup-0326/CalibreLibrary-New`
- Calibre-Web:
  `http://100.85.214.86:8083`
- Calibre desktop admin:
  `https://100.85.214.86:8181`
- Calibre metadata DB backups:
  `/mnt/m2cache/calibre-cleanup-0326/Backups/metadata-db`

## One-Writer Rule

Only one system should be treated as the active writer for a given library tree.

During this test:

- Calibre-Web and Calibre desktop remain the writers for the live Calibre library
- Grimmory is the writer for the cloned Grimmory test library

Do not point Grimmory at the live Calibre library for write testing.

## unRAID Folder Layout

Recommended paths for the test:

- Grimmory app data:
  `/mnt/user/appdata/grimmory/data`
- Grimmory MariaDB:
  `/mnt/user/appdata/grimmory-mariadb`
- Grimmory test workspace root:
  `/mnt/user/data/grimmory-test`
- cloned Grimmory test library:
  `/mnt/user/data/grimmory-test/CalibreLibrary-GrimmoryTest`
- BookDrop:
  `/mnt/user/data/grimmory-test/bookdrop`

BookDrop must remain outside the library root to prevent duplicate discovery.

## Container Defaults

Use the same unRAID container user defaults already used by the Calibre stacks:

- `PUID=99`
- `PGID=100`
- `TZ=Europe/London`

Keep Grimmory LAN/Tailscale-only during this phase. Do not expose it publicly without adding a proper reverse proxy and origin controls later.

## Compose File

Use:

- [docker-compose.unraid-calibre-test.yml](/Users/petetreadaway/Projects/grimmory/deploy/compose/docker-compose.unraid-calibre-test.yml)

Before launch:

1. Copy the file to your unRAID stack location if needed.
2. Replace:
   - `CHANGE_ME_GRIMMORY_DB_PASSWORD`
   - `CHANGE_ME_MARIADB_ROOT_PASSWORD`
3. Confirm the library and BookDrop host paths match your unRAID shares.

## Clone Preparation

Prepare the Grimmory test workspace on unRAID:

```bash
mkdir -p /mnt/user/data/grimmory-test
mkdir -p /mnt/user/data/grimmory-test/bookdrop
rsync -aH --delete /mnt/m2cache/calibre-cleanup-0326/CalibreLibrary-New/ /mnt/user/data/grimmory-test/CalibreLibrary-GrimmoryTest/
mkdir -p /mnt/user/appdata/grimmory/data
mkdir -p /mnt/user/appdata/grimmory-mariadb
```

If you want a quicker first pass, create the clone once and then refresh it before each major test cycle.

## Launch

```bash
docker compose -f deploy/compose/docker-compose.unraid-calibre-test.yml up -d
```

## Required Validation

### 1. Container Health

```bash
docker ps
docker logs --tail 100 grimmory
docker logs --tail 100 grimmory-mariadb
curl -fsS http://127.0.0.1:6060/api/v1/healthcheck
```

Expected result:

- MariaDB is healthy
- Grimmory responds on `/api/v1/healthcheck`
- no datasource or path-access errors in logs

### 2. Permission Checks

Confirm the Grimmory container can actually use the mounted paths:

```bash
docker exec grimmory sh -lc 'ls -ld /books /bookdrop && touch /bookdrop/.permcheck && rm /bookdrop/.permcheck'
docker exec grimmory sh -lc 'find /books -maxdepth 1 | head'
```

Before claiming direct-write testing works, also verify create, rename, move, and delete on sacrificial files inside the clone:

```bash
docker exec grimmory sh -lc 'mkdir -p /books/.grimmory-permcheck && touch /books/.grimmory-permcheck/test.tmp && mv -f /books/.grimmory-permcheck/test.tmp /books/.grimmory-permcheck/test-renamed.tmp && rm -f /books/.grimmory-permcheck/test-renamed.tmp && rmdir /books/.grimmory-permcheck'
```

### 3. In-App Setup

1. Open `http://<unraid-host-or-tailscale-ip>:6060`
2. Create the Grimmory admin account
3. Create one library pointing at `/books`
4. Let Grimmory scan the clone

The admin account should be sufficient for BookDrop testing. If you later create a non-admin test user, grant that user BookDrop access explicitly before testing the BookDrop UI.

### 4. Library Validation

Check:

- Grimmory accepts `/books` as a valid library path
- the initial scan completes
- sample EPUB and PDF titles open correctly
- scanned totals are close to the clone's Calibre `metadata.db` counts

Count the clone directly if needed:

```bash
sqlite3 /mnt/user/data/grimmory-test/CalibreLibrary-GrimmoryTest/metadata.db 'select count(*) from books;'
```

### 5. BookDrop Validation

Copy a few small test titles into BookDrop and then validate end to end:

```bash
cp -f /path/to/test1.epub /mnt/user/data/grimmory-test/bookdrop/
cp -f /path/to/test2.pdf /mnt/user/data/grimmory-test/bookdrop/
```

Then in Grimmory:

1. open BookDrop
2. confirm files appear
3. run manual rescan if needed
4. finalize import
5. verify the files now exist in the cloned library, not the live Calibre tree

### 6. Restart Validation

```bash
docker compose -f deploy/compose/docker-compose.unraid-calibre-test.yml restart
```

Recheck:

- health endpoint
- library presence
- BookDrop status
- recent logs

## Recommended Smoke Tests

- open one EPUB
- open one PDF
- import at least two files via BookDrop
- perform one rename/move/delete-style operation on sacrificial files in the clone
- rescan and confirm Grimmory remains consistent afterward

## Rollback

If the test misbehaves:

1. stop Grimmory and MariaDB
2. discard the cloned test library
3. recreate the clone from the live Calibre source
4. clear test BookDrop files if needed
5. restart the stack

The live Calibre library should not need restoration if Grimmory was kept on the clone.
