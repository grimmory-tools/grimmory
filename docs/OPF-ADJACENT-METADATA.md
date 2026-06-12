# OPF-Adjacent Metadata

Grimmory can use an OPF sidecar and adjacent cover image without modifying the
book file itself.

## When It Is Applied

- OPF-adjacent metadata is applied during library scan and import.
- OPF-adjacent metadata is applied during the **Refresh Metadata** task.
- An adjacent cover is applied during library scan and import.
- An adjacent cover is applied during the **Refresh Metadata** task.

Metadata locks are respected, including `coverLocked`. Locked fields are not
overwritten. A malformed OPF or invalid cover is skipped safely so that the
book can continue through scan, import, or refresh.

## Cover Priority

When more than one cover candidate exists, Grimmory uses the first valid match
in this order:

1. OPF manifest cover referenced by `<meta name="cover" content="cover-id">`
2. OPF manifest image item with `properties="cover-image"`
3. OPF image item whose `id` or `href` indicates a cover
4. `cover.jpg`
5. `cover.jpeg`
6. `cover.png`
7. `folder.jpg`
8. `folder.png`
9. `<book-stem>.jpg`
10. `<book-stem>.png`

## File Integrity

Adjacent covers are cache/UI-only by default. OPF and cover application do not
write metadata or images back into EPUB, PDF, or CBZ files, and therefore do
not change book file hashes.

The cache-only default avoids changing book file hashes, which keeps
GrimmLink/KOReader book matching stable.
