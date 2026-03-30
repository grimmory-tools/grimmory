# Grimmory Cutover

This guide describes how to promote the Grimmory-managed clone if the unRAID test succeeds.

## Goal

The end state is:

- Grimmory becomes the system of record for your primary library
- the validated Grimmory-managed clone becomes the promotion candidate
- Calibre is retired instead of remaining a concurrent writer

## Non-Goal

Do not move into a steady-state setup where Calibre and Grimmory both keep writing to the same library tree.

## Promotion Checklist

Promotion is reasonable only after all of these are true:

- Grimmory scans the clone successfully
- representative EPUB and PDF titles open correctly
- BookDrop imports work end to end
- direct file operations work on sacrificial test books in the clone
- Grimmory survives restarts cleanly
- you are satisfied that the Grimmory workflow covers the jobs you actually need

## Freeze Window

Before promotion:

1. stop making metadata or file changes in Calibre-Web
2. stop using the Calibre desktop writer
3. stop any Calibre-side scripts that write to `metadata.db`
4. stop BookDrop testing in Grimmory

## Backup Before Promotion

Take or confirm backups of:

- live Calibre metadata DB:
  `/mnt/m2cache/calibre-cleanup-0326/Backups/metadata-db`
- the Grimmory clone
- the Grimmory MariaDB data directory

If you want one last manual Calibre backup before retirement work:

```bash
ssh root@optiplex3070-1 bash /boot/config/custom/calibre_metadata_db_backup.sh --label pre_grimmory_cutover
```

## Final Validation Before Promotion

Run one last pass on the clone:

- compare counts against the clone's `metadata.db`
- open sample EPUB and PDF files
- confirm recently imported BookDrop books are present and readable
- check Grimmory logs for file, scan, or DB errors

## Promotion Options

### Option A: Keep The Clone As Primary

This is the preferred path.

1. keep Grimmory pointed at the validated clone
2. designate that clone as the new primary library tree
3. retire Calibre as the active writer
4. keep Calibre available only as a short-lived fallback reference if needed

### Option B: Rebuild The Test If Confidence Is Too Low

If Grimmory still has workflow gaps:

1. stop Grimmory
2. discard the clone
3. recreate it from the live Calibre source
4. repeat testing after fixing the identified gaps

## After Promotion

- document the final primary-library path
- stop treating the live Calibre tree as authoritative
- stop using Calibre-Web and Calibre desktop for new writes
- keep a rollback window long enough to restore from the Calibre backup if you discover a late issue

## Rollback

If the promotion fails, fall back to the last known-good Calibre environment and the pre-cutover backups.

Do not try to merge concurrent Grimmory and Calibre writes after the fact.
