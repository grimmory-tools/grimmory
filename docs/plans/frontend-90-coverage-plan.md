# Frontend 90% Coverage Swarm Plan

## Summary

Drive the frontend to an honest `>=90%` on statements, branches, functions, and lines for application code, and keep going until both the global total and each major frontend area clear the bar. Use `chore/expand-frontend-tests` as the controller branch and execute the bulk of the work through a swarm of sub-agents in repo-local worktrees under `.worktrees/`.

This file is the persistent source of truth for the run. Every later summary, checkpoint, or resume-after-compaction step should explicitly re-anchor to `docs/plans/frontend-90-coverage-plan.md`.

Operating rules:

- Default: do not change runtime code to make tests possible.
- Exception policy: if a file is truly blocked, make the smallest runtime seam change only in its own conventional commit with an explicit defense, so it can be reviewed or reverted independently.
- Commit frequently with conventional commits after each stable coverage gain batch; do not wait for one giant final commit.
- Do not push unless explicitly asked.

## Harness And Configuration

- Consolidate the frontend onto one canonical Vitest config used by Angular's unit-test builder. Remove the current drift between `vitest-base.config.ts` and the richer unused config.
- Enforce coverage thresholds in that canonical config for all four metrics at `90`, with machine-readable output enabled for automated progress tracking.
- Keep template coverage enabled and keep `src/main.ts` in scope. Do not broaden exclusions to hit the number.
- Install and integrate:
  - `@testing-library/angular`
  - `@testing-library/user-event`
  - `@testing-library/jest-dom`
  - `msw`
  - `@playwright/test`
  - `vitest-canvas-mock`
- Expand `src/test-setup.ts` with stable browser shims needed by the app and readers: observers, scroll APIs, pointer/canvas helpers, object URLs, and other browser primitives that are safe test-harness additions.
- Add shared test-only helpers for router rendering, Transloco, QueryClient setup, factory builders, and MSW handler bundles by domain.
- Add a coverage-summary script or command that reads the generated coverage JSON and reports:
  - global metrics
  - per-family metrics for `core`, `shared`, and each `features/*` bucket
  - the worst uncovered files by branch deficit
- Keep controller ownership over plan artifacts, harness files, shared test-only helper roots, and Playwright configuration.
- Keep the Playwright baseline narrow and reproducible:
  - the committed browser harness is `frontend/playwright.config.ts` plus the canonical `frontend/playwright/login-and-books.spec.ts` scenario
  - Playwright must use env-driven ports via `PLAYWRIGHT_PORT` or `PLAYWRIGHT_BASE_URL`; do not hard-code `4200` in specs or fixtures
  - future browser scenarios need their own dedicated fixture files and must not extend one catch-all route shim

## Swarm Topology

- Controller branch: `chore/expand-frontend-tests`.
- Worktree root: `.worktrees/` only.
- One branch and one worktree per independent effort.
- Controller owns:
  - `docs/plans/frontend-90-coverage-plan.md`
  - frontend harness files such as `vitest-base.config.ts`, `src/test-setup.ts`, and coverage-summary tooling
  - shared test-only helper roots
  - Playwright configuration and shared browser fixtures
  - integration, rebases, cherry-picks, and final validation
- Initial workers:
  1. `test/f90-core-auth` -> `.worktrees/f90-core-auth`
  2. `test/f90-shared` -> `.worktrees/f90-shared`
  3. `test/f90-book` -> `.worktrees/f90-book`
  4. `test/f90-metadata` -> `.worktrees/f90-metadata`
  5. `test/f90-settings-stats` -> `.worktrees/f90-settings-stats`
  6. `test/f90-readers` -> `.worktrees/f90-readers`
  7. `test/f90-playwright` -> `.worktrees/f90-playwright`
- Deferred worker:
  - `test/f90-gap-closer` -> `.worktrees/f90-gap-closer` after the first integration wave
- Worker ownership:
  - `f90-core-auth`: `frontend/src/app/core/**`, auth/bootstrap/login/routing-adjacent specs
  - `f90-shared`: `frontend/src/app/shared/**` excluding controller-owned helper roots and Playwright assets
  - `f90-book`: `frontend/src/app/features/book/**`, `bookdrop/**`, `library-creator/**`
  - `f90-metadata`: `frontend/src/app/features/metadata/**`
  - `f90-settings-stats`: `frontend/src/app/features/settings/**`, `stats/**`
  - `f90-readers`: `frontend/src/app/features/readers/**`
  - `f90-playwright`: browser specs and test-only browser fixtures only
- Every worker must stay inside its ownership boundary, must not revert edits from others, and must hand runtime-code blockers back to the controller.

## Execution Loop

- Controller prep first:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
  - `just ui coverage`
  - land the harness prep commit before creating worktrees
- After the harness prep commit:
  - create `.worktrees/` if missing
  - create one worktree per initial worker branch from `chore/expand-frontend-tests`
  - spawn sub-agents with explicit ownership, validation requirements, and commit expectations
- Worker loop:
  - add tests inside the owned surface
  - run targeted tests for that surface
  - run `just ui typecheck`
  - run `just ui lint`
  - commit one logical bucket at a time with a Conventional Commit subject and meaningful body
- Controller integration loop:
  - integrate worker branches in this order: harness, core/auth, shared, book/bookdrop/library-creator, metadata, settings/stats, readers, playwright, gap-closer
  - after each integration run `just ui typecheck`, `just ui lint`, and `just ui test`
  - every two integrations run `just ui coverage` and `just ui coverage-summary`
  - repoint the deferred gap-closer worker at the worst remaining files by branch deficit
- Do not stop when global coverage first crosses `90` if any major area remains materially behind; continue until the lagging areas also clear `90`.

## Test Generation Scope

### Bootstrap/Auth/Routing

- Cover `main.ts` provider wiring, app initializers, QueryClient defaults, Transloco bootstrap, service worker enablement, and router bootstrap.
- Cover `app.routes.ts` route definitions, guards, lazy route entrypoints, and fallback redirects.
- Cover auth/setup/login guards and auth initializer.
- Cover `AuthInterceptorService` including token attach, non-API bypass, 401 refresh success, refresh failure, and concurrent refresh waiting.
- Cover the login component thoroughly: local login paths, default-password redirect, rate-limit/network/unexpected failures, OIDC redirect limits, query-param error mapping, and provider-init failures.

### Shared/Core

- Raise coverage across app config/settings/auth/version/startup/local storage/websocket/dialog-launcher/icon/font helpers.
- Add render/integration tests for shared shell pieces and interactive shared components, focusing on permissions, empty states, toggles, and error branches.

### Feature Families

- `book`: services, query/cache/socket helpers, browser filters/sorting/view toggles, bulk actions, dialogs, and series flows.
- `bookdrop`: review/finalize/pattern extraction/state transitions and event-driven behavior.
- `library-creator`: validation, progress, cancellation, and failure states.
- `metadata`: picker/searcher/viewer/editor, task/review flows, sidecar interactions, fetch option dialogs, and bulk update branches.
- `settings`: user management, content restrictions, fonts, email, metadata settings, reader preferences, task management, and profile flows.
- `stats`: data-transform services and route-entry components with deterministic chart inputs; verify behavior and outputs rather than brittle chart internals.

### Readers

- Cover `ebook-reader` and `cbx-reader` logic-heavy services/state first, then component/control behavior, then dialogs/layout shells.
- Add Playwright smoke/regression tests for login, guarded navigation, book browser, metadata center, one stats page, ebook reader, and cbx reader.
- Use Playwright to prove browser-real flows, but keep Vitest as the source of coverage truth.

## Commit And Acceptance Rules

- Commit cadence:
  - one controller setup commit for harness/tooling plus swarm plan update
  - then one conventional commit per stable worker bucket or integration step
  - runtime seam exceptions, if any, get their own isolated conventional commit with explicit justification
- Preferred commit pattern:
  - `chore(testing): ...` for harness/config changes
  - `test(frontend): ...` for test additions
  - `test(auth): ...`, `test(readers): ...`, `test(metadata): ...` for focused batches
  - `fix(testing): ...` only for genuinely broken harness behavior
- Final acceptance requires:
  - `just ui typecheck` passing
  - `just ui lint` passing
  - `just ui test` passing
  - `just ui coverage` proving `>=90` on statements, branches, functions, and lines
  - periodic `just ui check` green at the end
  - Playwright smoke suite passing for the browser-critical flows above
  - the final status summary explicitly references `docs/plans/frontend-90-coverage-plan.md`

## Assumptions And Defaults

- The `90%` target means all four metrics, not statements only.
- Coverage scope includes frontend application code and templates plus `src/main.ts`; no broad exclusions are allowed.
- Runtime code changes are disallowed by default; if unavoidable, they must be isolated in a separately defended commit.
- `chore/expand-frontend-tests` is the controller branch and worker branches are derived from it.
- `.worktrees/` is the only allowed worktree root.
- Frequent conventional commits are part of the execution plan, not deferred cleanup.
