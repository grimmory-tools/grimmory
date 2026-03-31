# Frontend Coverage Phase 2: Harness-First Supervised Swarm

## Summary

Drive the next frontend coverage phase from reusable test harnesses instead of one-off spec rescue work.

Validated baseline on `chore/expand-frontend-tests`:
- `just ui typecheck`
- `just ui lint`
- `just ui test`
- `just ui coverage-summary`

Current baseline:
- statements `32.84%`
- branches `24.95%`
- functions `28.56%`
- lines `48.10%`

Current skipped-spec distribution:
- `features/stats`: `32`
- `features/settings`: `26`
- `features/book`: `25`
- `features/readers`: `15`
- `shared`: `12`
- `features/metadata`: `7`
- `features/author-browser`: `6`

Operating rules for this phase:
- no runtime code changes without explicit approval
- solve the next wave in the test harness first
- git operations stay in one thread only
- the controller is orchestration-only
- branch coverage gain is the primary metric
- mature packages are preferred when they clearly replace bespoke harness code

## Step 0

This file is the new execution plan for the phase.

Do not mutate `docs/plans/frontend-90-coverage-plan.md` except for cross-reference or historical context.

Each execution checkpoint appended here must record:
- timestamp
- current branch totals from `just ui coverage-summary`
- accepted harness families
- worker roster and lane ownership
- accepted bucket list
- blocked seam list

## Checkpoints

### 2026-03-27 16:36:07 CDT

Current branch totals from `just ui coverage-summary`:
- statements `33.60%`
- branches `25.28%`
- functions `29.59%`
- lines `48.65%`

Accepted harness families:
- query/auth/signal bootstrap via `createQueryClientHarness()` and `createAuthServiceStub(...)`
- non-rendering chart bootstrap via `nextChartEmission(...)` and `fakeChartTooltipContext(...)`
- dialog/form bootstrap via `createDynamicDialogHarness(...)` and message-service helpers

Worker roster and lane ownership for this bootstrap run:
- controller: local single-owner execution only
- harness lane: `frontend/src/app/core/testing/{query-testing,chart-testing,dialog-testing}.ts`
- query/service canaries: `AppSettingsService`, `LibraryService`
- chart canary: `GenreStatsChartComponent`
- dialog canary: `AuthorPhotoSearchComponent`

Accepted bucket list:
- add `ng-mocks`
- publish shared bootstrap helpers for query, chart, and dialog seams
- convert four skipped canaries into active specs
- validate with `just ui typecheck`, `just ui lint`, `just ui test`, `just ui coverage`, and `just ui coverage-summary`

Blocked seam list:
- none in the accepted bootstrap set

## Harness Families

### 1. Query/Auth/Signal Harness

Purpose:
- cover eager `injectQuery(...)`
- control auth-token enablement
- assert computed loading and error state
- assert cache invalidation and token-cleared removal

Shared helpers:
- `createQueryClientHarness()`
- `createAuthServiceStub(initialToken)`
- `flushSignalAndQueryEffects()`
- `createSignalFacade(...)`

First canaries:
- `AppSettingsService`
- `LibraryService`

### 2. Non-Rendering Chart Harness

Purpose:
- assert derived chart datasets
- assert summary fields
- assert tooltip and label callbacks
- avoid live chart rendering as the main assertion surface

Shared helpers:
- `createChartTranslocoStub()`
- `nextChartEmission(...)`
- `fakeChartTooltipContext(...)`

First canary:
- `GenreStatsChartComponent`

### 3. Dialog/Form Harness

Purpose:
- standardize `DynamicDialogConfig` and `DynamicDialogRef`
- standardize message and confirmation spies
- standardize PrimeNG event payload stubs
- standardize fake-timer flows for dialog lifecycle

Shared helpers:
- `createDynamicDialogHarness(data)`
- `createMessageServiceSpy()`
- `createConfirmServiceSpy()`

First canary:
- `AuthorPhotoSearchComponent`

### 4. Route/Timer/Host-Event Harness

Purpose:
- query-param syncing
- router navigation doubles
- debounced search
- document click and host-listener control

### 5. Reader Wrapper Harness

Purpose:
- cover reader header, sidebar, panel, quick-settings, and settings wrappers
- explicitly exclude full reader runtimes

## Package Posture

Default package stance:
- use mature libraries when they directly match the seam and reduce bespoke mocking
- use repo-local helpers only for repo-specific state wiring and tiny adapter glue

Immediate package decisions:
- standardize `@testing-library/angular`
- standardize `@testing-library/user-event`
- keep `jsdom`
- keep `vitest-canvas-mock`
- keep `msw` as secondary, not primary, for Angular service flows
- add `ng-mocks` early for standalone and PrimeNG-heavy shell work

## Attack Order

1. `features/stats`
   - highest branch upside with one reusable chart family
   - hold plugin and imperative canvas outliers until later
2. query-backed services across `shared`, `features/settings`, `features/book`, and `features/magic-shelf`
3. dialog and form specs across `features/book`, `features/bookdrop`, `features/author-browser`, and selected `features/metadata`
4. reader wrapper specs only
5. settings and shared shells that can reuse the proven signal and route harnesses

Deferred hold bucket:
- shared layout and menu shells
- author-browser page shell
- full reader runtimes
- view-manager and similar runtime seams

## Worker Model

Phase 0:
- one harness owner only
- no swarm until the controller accepts the shared conventions

Phase 1:
- one controller
- one git and integration lieutenant
- one notify lieutenant
- one harness-generation worker
- up to three spec-generation workers

Hard rules:
- only git and integration runs git commands
- only notify uses Pushover
- only harness-generation edits shared helper files
- spec workers consume published harnesses and do not invent competing ones

## Communication Spec

The controller is assertive and supervises every worker, including git and notify.

Worker heartbeat:
- target every `2-3` minutes
- absolute maximum `5` minutes
- immediate extra message on `start`, `blocked`, `handoff`, `needs-decision`, `done`, or `drift-detected`

Worker status format:
- `state | owned_paths | current_target | last_result | blocker | next_10m`

Allowed `state` values:
- `bootstrapping`
- `implementing`
- `validating`
- `blocked`
- `handoff-ready`
- `stopped`

Controller reply format:
- `decision | scope | priority | continue_until | stop_condition`

The controller interrupts immediately when:
- a heartbeat exceeds `5` minutes
- a status report is vague
- a worker touches unowned paths
- a worker proposes runtime edits without approval
- a worker continues low-yield work after a stop signal

## Stop and Recovery Rules

Harness canary window:
- `15-20` minutes maximum per family

Stop a harness spike when:
- it does not unlock one real spec and one near-neighbor candidate inside the window

Stop a lane when:
- two consecutive specs need bespoke lane-only mocks
- the worker edits outside the owned write set
- the worker reports no branch-relevant progress for one heartbeat cycle
- the worker proposes runtime changes without escalation

Recovery:
- classify the blocker as `blocked-by-harness`, `blocked-by-runtime-seam`, or `blocked-by-package-gap`
- record the exact seam here
- let git and integration quarantine or discard the batch
- do not let the controller salvage the bucket by doing implementation work

## Validation

Per bootstrap canary:
- focused spec
- `just ui typecheck`

Per accepted bucket:
- focused spec slice
- `just ui typecheck`
- `just ui lint`
- `just ui coverage-summary`

Checkpoint gates:
- `just ui test`

Phase close:
- `just ui coverage`
- `just ui coverage-summary`
- `just ui typecheck`
- `just ui lint`
- `just ui test`
