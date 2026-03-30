# Frontend Grimmory Migration

## Summary

This plan renames the Angular project from `booklore-ui/` to `frontend/`, keeps a temporary `booklore-ui -> frontend` symlink for compatibility, migrates the JavaScript tooling to Yarn 4 with the `node-modules` linker, adds a consistent frontend quality gate, and introduces replay automation for open pull requests that were based on the pre-migration tree.

The work is intentionally staged so each step can be reviewed and, later, committed independently with minimal ambiguity.

## Step 1: Planning and Inventory

- Add this implementation plan under `docs/plans/`.
- Add a cleanup ledger under `docs/plans/frontend-post-cutover-cleanup-ledger.md` for Booklore-era aliases that must remain temporarily.
- Record the current frontend quality baseline before any lint cleanup:
  - ESLint errors: `721`
  - ESLint warnings: `0`
  - Build warnings: `0`

## Step 2: Rename `booklore-ui/` to `frontend/`

- Move the tracked Angular project from `booklore-ui/` to `frontend/`.
- Add a tracked symlink `booklore-ui -> frontend` immediately after the move.
- Switch all first-party repo-owned path consumers to `frontend/` in the same pass:
  - Root `Justfile`
  - Root `AGENTS.md`
  - `README.md`
  - `CONTRIBUTING.md`
  - `Dockerfile`
  - `dev.docker-compose.yml`
  - `scripts/i18n/weblate-setup.sh`
  - `booklore-api/build.gradle.kts`
  - `.github/workflows/*.yml`
  - `.github/dependabot.yml`

### Compatibility Window Rules

- The symlink exists only to keep stale local workflows and existing PR branches from breaking immediately.
- New repo-owned code, docs, CI, and packaging should point at `frontend/`, not `booklore-ui/`.
- The symlink should remain until the replay tooling has been used on the active open PRs and the maintainers are comfortable removing the fallback.

## Step 3: Migrate to Yarn 4

### Shared Defaults

- Use Corepack-managed Yarn 4.
- Use the `node-modules` linker, not Plug'n'Play.
- Keep dependency versions unchanged during lockfile migration.
- Do not introduce a monorepo workspace layout in this phase.

### Frontend Surface

- Add `packageManager` to `frontend/package.json`.
- Replace `frontend/package-lock.json` with `frontend/yarn.lock`.
- Add or update repo-level Yarn config in `.yarnrc.yml`.
- Convert `frontend/Justfile`, Docker build steps, local setup docs, and test workflows from npm to Yarn.

### Release Tooling Surface

- Add `packageManager` to `tools/release/package.json`.
- Replace `tools/release/package-lock.json` with `tools/release/yarn.lock`.
- Convert `tools/release/Justfile` and release-preview/release-main workflows from npm to Yarn.

## Step 4: Add the Frontend Quality Gate

- Keep the current Angular ESLint setup for TypeScript and Angular templates.
- Add Stylelint for SCSS under `frontend/`.
- Keep `tsc --noEmit` and the production Angular build as required verification steps.
- Replace the threshold-only frontend workflow with explicit jobs that fail independently:
  - ESLint
  - Stylelint
  - Typecheck
  - Production build
- Store the current lint baseline in version control so CI can block regressions before the manual cleanup is complete.

## Step 5: Safe Lint Passes

- Run the least risky lint work first and keep each pass logically isolated:
  1. Stylelint autofixes and SCSS-only cleanup.
  2. ESLint autofixes that are mechanically safe.
  3. Low-risk manual fixes such as unused locals, `autofocus`, output naming collisions, and obvious accessibility handler fixes.
- Do not take on high-risk reader/Foliate typing work or broad `no-explicit-any` refactors in this migration unless a focused follow-up explicitly approves that churn.

## Step 6: PR Replay Tooling

- Add replay automation that rebases or replays pre-migration PRs onto `develop` after this migration lands.
- Default behavior should preserve commit topology:
  - Recreate commits one by one when practical.
  - Re-run the rename/lint normalization script before each recreated commit.
  - Preserve the original author.
  - Add traceability trailers such as `Replayed-from: <sha>` and, when provided, the original PR URL.
- Support an explicit squash fallback for conflict-heavy PRs.
- Support both maintainer and contributor use:
  - Maintainer replay against a contributor branch or patch series.
  - Contributor self-service replay against their own topic branch.

## Validation

- After the rename pass:
  - `just ui test`
  - `just ui build`
  - `just image-build`
- After the Yarn migration:
  - `just ui install-ci`
  - `just ui check`
  - `just release install`
  - `just image-build`
- After the lint and CI pass:
  - Frontend lint/typecheck/build commands individually
  - `just test`
  - `just check`

## Notes for Later Backend Work

- The backend still hardcodes some frontend dist and path assumptions, so the frontend rename should keep those references minimal and explicit.
- The Booklore compatibility aliases tracked in the cleanup ledger should inform the backend migration plan, because some of those aliases can only be removed once backend contracts change.
