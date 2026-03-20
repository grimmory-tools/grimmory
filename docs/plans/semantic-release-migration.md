# Semantic-Release Migration for Stable Releases

## Summary

Step 0: write this plan to `docs/plans/semantic-release-migration.md`.

Replace Release Drafter and the manual bump-based stable release flow with `semantic-release` as the single source of truth for stable versioning and release notes. Stable releases are computed automatically from conventional commit history on `main`, nightly builds remain on `develop`, and the first semantic-release-managed stable release must be `v2.2.2`.

## Key Changes

- Add dedicated release tooling under `tools/release/` with its own `package.json`, lockfile, and `release.config.cjs`.
- Remove Release Drafter and the manual stable bump workflow.
- Introduce a push-to-`main` semantic-release workflow that:
  - runs migration checks and the shared test suite,
  - updates `CHANGELOG.md`,
  - creates the release commit and `vX.Y.Z` tag,
  - and opens a draft GitHub release with grouped notes.
- Introduce a tag-driven stable image workflow on `v*` tags that publishes Docker Hub and GHCR images and finalizes the matching draft GitHub release.
- Keep nightly publication on `develop` and manual preview image builds unchanged.
- Enforce semantic PR titles on PRs targeting `develop` and `main`.
- Treat release bumps as:
  - `BREAKING CHANGE` => major
  - `feat` => minor
  - `fix`, `perf`, `refactor` => patch
  - `docs`, `ci`, `build`, `chore`, `test`, `style` => notes only
- Group release notes and `CHANGELOG.md` sections by type, including non-triggering categories.

## Bootstrap

- Seed the release history with an annotated `v2.2.1` tag on commit `384512534fe5a2f42d8482a0646337e28ef54e52`.
- Push the seed tag only. Do not create a public historical GitHub release for `v2.2.1`.
- Merge the semantic-release migration as a patch-triggering commit so the first semantic-release-managed stable release becomes `v2.2.2`.

## Test Plan

- Verify semantic-release dry run computes `2.2.2` from the seeded history.
- Verify `feat`, `fix`, `perf`, `refactor`, and breaking-change behavior matches the configured release rules.
- Verify `docs`, `ci`, `build`, `chore`, `test`, and `style` appear in notes but do not trigger releases by themselves.
- Verify semantic PR title linting blocks invalid squash-merge titles.
- Verify the stable semantic-release workflow skips `[skip ci]` release commits and does not loop.
- Verify the tag-driven image workflow publishes `vX.Y.Z` and `latest` and finalizes the matching GitHub release.

## Assumptions

- `develop` remains the integration branch and nightly source.
- `main` remains the stable release branch.
- Feature PRs are squash-merged into `develop` with conventional PR titles.
- Promotions from `develop` to `main` preserve cleaned commit history and are not squash merges.
- A release bot or PAT-backed identity can push release commits and tags back to `main`.
