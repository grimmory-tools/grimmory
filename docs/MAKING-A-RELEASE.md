# Making a Release

This repository now uses `semantic-release` for stable releases.

Stable releases are not created by manually dispatching a "release" workflow. Instead, a stable release is triggered by pushing release-worthy conventional commits to `main`.

## Overview

The stable release flow has two stages:

1. Push or merge release-worthy commits to `main`.
2. Let GitHub Actions compute the version, create the tag, and publish the images.

The relevant workflows are:

- [`.github/workflows/release-main.yml`](../.github/workflows/release-main.yml)
- [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml)
- [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml)

## Prerequisites

Before cutting a stable release, make sure:

- The commits that should be released are already on `main`.
- Those commits follow conventional commit semantics.
- `RELEASE_BOT_TOKEN` is configured if branch protection requires more than the default `github.token`.
- `DOCKER_USERNAME` and `DOCKER_PASSWORD` are configured for Docker Hub publishing.

## What Triggers a Stable Release

`semantic-release` runs on every push to `main` and decides whether a new release is needed.

Release behavior is based on commit history since the last stable tag:

- `feat:` triggers a minor release.
- `fix:`, `perf:`, and `refactor:` trigger a patch release.
- `BREAKING CHANGE:` triggers a major release.
- `docs:`, `ci:`, `build:`, `chore:`, `test:`, and `style:` appear in notes but do not trigger a release on their own.

The next version is always computed from the latest stable tag already reachable from `main`.

## Recommended Maintainer Flow

### 1. Preview the next release

Run the `Release - Dry Run Preview` workflow from the Actions tab.

This workflow is defined in [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml) and accepts:

- `ref`
  Default: `develop`

The preview workflow treats the selected `ref` as a candidate promotion onto `main`. It verifies that the selected commit already contains the current `main` history, then runs `semantic-release --dry-run` against that candidate state.

Use it to confirm:

- whether a release will be created,
- what the next version will be,
- how the release notes will be grouped,
- and which commits are included in the `main..candidate` release range.

### 2. Merge or push the release-worthy change set to `main`

Once the dry run looks correct, merge or push the desired commits to `main`.

That automatically triggers [`.github/workflows/release-main.yml`](../.github/workflows/release-main.yml).

### 3. Let semantic-release do the versioning work

If a release is warranted, `release-main.yml` will:

- run the migration check,
- run the shared test suite,
- compute the next semantic version,
- update `CHANGELOG.md`,
- create a release commit with `[skip ci]`,
- create the Git tag `vX.Y.Z`,
- and create a draft GitHub release.

If no release is warranted, the workflow exits without tagging or publishing.

### 4. Let the stable publish job release the artifacts

When `semantic-release` creates a release, [`.github/workflows/release-main.yml`](../.github/workflows/release-main.yml) directly invokes [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml) with the resolved release tag and commit SHA.

That workflow will:

- build the multi-architecture container image,
- export `backend/build/openapi/grimmory-openapi.json`,
- attach `grimmory-openapi.json` to the published GitHub release,
- publish `grimmory/grimmory:vX.Y.Z`,
- publish `grimmory/grimmory:latest`,
- publish `ghcr.io/grimmory-tools/grimmory:vX.Y.Z`,
- publish `ghcr.io/grimmory-tools/grimmory:latest`,
- and flip the GitHub release from draft to published.

## Nightly Builds

Nightly builds are separate from stable releases.

They come from `develop` through [`.github/workflows/publish-nightly.yml`](../.github/workflows/publish-nightly.yml) and publish:

- `nightly`
- `nightly-YYYYMMDD-<sha>`
- `grimmory-openapi.json`

## Preview Builds

Manual preview builds for PRs or arbitrary refs are separate from stable releases.

Use the preview-image workflow if you want a one-off test image without creating a stable release.

## Notes

- Stable releases are driven by commit history on `main`, not by labels or manual version bump inputs.
- If you need to understand why a release did or did not happen, start with the `Release - Dry Run Preview` workflow and then inspect the `semantic-release` output.
- The latest stable OpenAPI artifact is always available at `https://github.com/grimmory-tools/grimmory/releases/latest/download/grimmory-openapi.json`.
