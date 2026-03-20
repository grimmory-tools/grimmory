# Grimmory CI/CD Rebrand and Release Refactor

## Summary

- Before changing any build flow, cut one archival pre-migration release from the current state, record image digests and release metadata, and tag that commit with a non-semver marker such as `pre-grimmory-buildflow` so the current Booklore-era artifact remains recoverable.
- Execute the implementation in three parallel workstreams with disjoint ownership: workflow/release automation under `.github/workflows`, Docker packaging and asset layout under `Dockerfile`, `packaging/docker`, and `.dockerignore`, and deploy/docs surfaces under `deploy/` plus the operational docs/templates.
- The target operating model is: automatic validation on PRs and pushes, automatic nightly publication from `develop`, manual GHCR preview builds for PR refs, and maintainer-run batch stable releases from `main`.

## Key Changes

- Replace the current `develop` + stable-release publish model with four explicit workflows:
  - `ci-validate.yml`: runs on PRs to `develop` and `main`, plus pushes to `develop` and `main`; runs migration diff/preview, backend tests, frontend tests, and a no-push packaging smoke build.
  - `publish-nightly.yml`: runs on `schedule` and `workflow_dispatch`, explicitly checks out `develop`, and publishes `nightly` plus `nightly-YYYYMMDD-<sha>` to Docker Hub and GHCR.
  - `publish-release.yml`: runs only on `workflow_dispatch`, accepts `ref` and `bump` (`major|minor|patch`), verifies the chosen ref is on `main`, creates and pushes `vX.Y.Z`, publishes `vX.Y.Z` and `latest` to both registries, and publishes the GitHub release.
  - `preview-image.yml`: runs only on `workflow_dispatch`, accepts a PR ref or arbitrary ref, and publishes a GHCR-only preview tag such as `pr-<number>-<sha>` or `preview-<sha>`.
- Keep the reusable migration-check workflow, but make every caller pass explicit `base_ref`, `head_ref`, and `checkout_ref`; remove hard-coded `develop`, `HEAD`, and `HEAD~1` logic so PRs, push validations, nightly, and release workflows all diff the correct commits.
- Use the manual release workflow, not PR labels, as the source of truth for `major`/`minor`/`patch`. `release-drafter` stays only as a changelog formatter; it must no longer decide version bumps, and its repo/image links must be updated to Grimmory.
- Rename only build/release/deploy surfaces from Booklore to Grimmory in this pass: registry targets, OCI labels, GitHub release links and notes, deploy examples, operational docs, contributor guidance, and release/helper scripts. Do not rename `booklore-api`, `booklore-ui`, Java package names, DB identifiers, migration paths, dist paths, or other app/runtime internals yet.
- Replace all remaining stable-branch references with `main`, while keeping `develop` as the integration branch. Update contributor docs to state that PRs land on `develop`, maintainers promote selected changes to `main`, and stable releases are cut manually from `main`.
- Converge on a single canonical root `Dockerfile` and delete `Dockerfile.ci`. The Dockerfile should be a normal multi-stage source build that produces one minimal runtime image; CI should stop doing native workspace mutations like copying frontend build output into tracked resources or editing tracked `application.yaml` before packaging.
- Move packaging assets under `packaging/docker/`, keep the public build entrypoint as the root `Dockerfile`, and move deploy examples into a clear `deploy/` tree: `deploy/compose`, `deploy/helm/grimmory`, and `deploy/podman/quadlet`. Split scripts by purpose under `scripts/release` and `scripts/i18n` or `scripts/ops`.
- Tighten `.dockerignore` so docs, deploy examples, plans, and unrelated tooling do not bloat the Docker build context.
- Standardize tag policy and example usage:
  - Stable: `vX.Y.Z` and `latest`.
  - Nightly: `nightly` and `nightly-YYYYMMDD-<sha>`.
  - Preview: GHCR-only, never `latest`, never Docker Hub.
  - Stable deployment examples and README snippets should show pinned semver first, with `latest` documented as a convenience tag.

## Test Plan

- PR validation to `develop` and `main` runs automatically, publishes no images, and uses the correct migration diff base/head.
- Push validation on `develop` and `main` runs automatically and produces no registry side effects.
- Scheduled nightly runs from `develop` even though GitHub schedules execute from the default branch context, and it publishes only the nightly tags.
- Manual preview build for a PR ref publishes only to GHCR and never mutates stable/nightly tags.
- Manual stable release from a selected `main` commit creates and pushes the git tag, publishes semver plus `latest` to both registries, and publishes a GitHub release using Grimmory links and image names.
- Local `docker build .` and CI image publication both use the same Dockerfile and produce the same runtime image layout.
- The packaging path leaves tracked files untouched; no workflow step edits repo-tracked source files as part of building an image.
- Deploy examples, release metadata, OCI labels, and contributor docs all consistently use Grimmory registry names and `main`/`develop` semantics.

## Assumptions and Defaults

- `main` is the default branch and the only stable release source.
- `develop` remains the integration branch and the source for nightly images.
- Stable releases are intentionally batch-based and maintainer-triggered; merging to `main` does not automatically publish a release.
- GHCR preview images are sufficient for PR testing; Docker Hub preview publication is out of scope.
- The canonical registry targets are `grimmory/grimmory` and `ghcr.io/grimmory-tools/grimmory`.
- This pass is intentionally limited to build, release, deploy, and operational surfaces so the existing application internals remain intact while the delivery system is rebranded and stabilized.
