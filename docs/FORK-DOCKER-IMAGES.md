# Fork Docker Images

This fork uses separate image channels for stable releases, continuous
`develop` testing, and manually selected preview builds.

## Image Channels

| Tag | Source | Description |
|---|---|---|
| `latest` | `main` / published release | Stable release image. Updated only when a stable GitHub Release is published. |
| `develop` | `develop` | Latest test build. Updated on every push to the `develop` branch. |
| `develop-YYYYMMDD-<sha>` | `develop` | Pinned test build for reproducible validation and rollback. |
| `preview` | manual preview / upstream-friendly work | Latest preview build for redesign, upstream-friendly refactor, or experimental validation before promoting to `develop` or `main`. |
| `preview-<sha>` | manual preview / selected ref | Pinned preview build for rollback or device testing. |
| `pr-<number>-<sha>` | manual preview with PR number | Pinned PR preview build. |
| `vX.Y.Z-GrimmLink` | release tag | Pinned stable version. |

## Recommended Usage

| Environment | Recommended tag |
|---|---|
| Production | `latest` or pinned `vX.Y.Z-GrimmLink` |
| Test device / develop server | `develop` |
| Experimental upstream-friendly testing | `preview` or `preview-<sha>` |
| Rollback | A pinned version or commit-specific tag |

The fork does not use `nightly` as the primary test channel. The `develop`
image replaces `nightly` for fork testing. `preview` is for manually selected
redesign or upstream-friendly work before promotion.

Prefer pinned tags when reproducibility or rollback matters. Moving tags such
as `latest`, `develop`, and `preview` are convenience channels and may point to
new builds over time.
