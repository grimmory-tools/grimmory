# Testing GrimmLink

Use this checklist for GrimmLink and OPF-adjacent changes. Start with targeted
tests around the edited behavior, then run the wider backend suite with
`just api test`.

## Backend Tests

- [ ] OPF metadata is applied during scan/import.
- [ ] OPF metadata is applied by the **Refresh Metadata** task.
- [ ] OPF cover application remains cache-only.
- [ ] Authentication succeeds with valid credentials and fails safely with
  invalid credentials.
- [ ] Book matching resolves both current and initial hashes.
- [ ] Progress GET and PUT preserve the expected contract.
- [ ] EPUB percentage appears in Web UI progress.
- [ ] Read status is derived from the real percentage.
- [ ] The manual read-status endpoint preserves newer manual state.
- [ ] PDF bridge conflict detection and `force=true` are covered.
- [ ] Single and batch reading sessions are idempotent.
- [ ] Regular shelves, magic shelves, download, and shelf removal are covered.
- [ ] Metadata batch push and pull are covered.

## Device And Integration Tests

- [ ] Install the GrimmLink plugin branch
  `codex/grimmlink-v1-api-cutover`.
- [ ] Configure the Grimmory server URL.
- [ ] Authenticate successfully.
- [ ] Match a book by hash.
- [ ] Push EPUB progress and confirm the percentage in the Web UI.
- [ ] Confirm read status changes with reading percentage.
- [ ] Push PDF progress and confirm the PDF bridge state.
- [ ] Sync reading sessions.
- [ ] Sync a regular shelf.
- [ ] Sync a magic shelf.
- [ ] Download a book to KOReader.
- [ ] Remove a book from a regular shelf and confirm that the server library
  file is not deleted.
- [ ] Push rating, annotations, and bookmarks.
- [ ] Pull metadata in another device or session.
- [ ] Confirm metadata from the same `deviceId` is not reapplied.

## Docker Image Tests

- [ ] Test `preview` before promoting a build to `develop`.
- [ ] Test `develop` before creating a stable release.
- [ ] Use pinned tags for rollback and reproducible device testing.

Record the image tag or SHA, plugin commit, server commit, and relevant test
output with the PR so a failure can be reproduced.
