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

The current seeded baseline is `v2.2.1`, so the next patch release should be `v2.2.2`.

## Recommended Maintainer Flow

### 1. Preview the next release

Run the `Release - Dry Run Preview` workflow from the Actions tab.

This workflow is defined in [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml) and accepts:

- `ref`
  Default: `main`

Use it to confirm:

- whether a release will be created,
- what the next version will be,
- and how the release notes will be grouped.

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

### 4. Let the tag publish the release artifacts

When `semantic-release` creates the tag, that tag triggers [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml).

That workflow will:

- build the multi-architecture container image,
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

## Preview Builds

Manual preview builds for PRs or arbitrary refs are separate from stable releases.

Use the preview-image workflow if you want a one-off test image without creating a stable release.

## Notes

- The bootstrap tag `v2.2.1` is intentionally ignored by the stable image publish workflow so it does not back-publish an old release.
- Stable releases are driven by commit history on `main`, not by labels or manual version bump inputs.
- If you need to understand why a release did or did not happen, start with the `Release - Dry Run Preview` workflow and then inspect the `semantic-release` output.
