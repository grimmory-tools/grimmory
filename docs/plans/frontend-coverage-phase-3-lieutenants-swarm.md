# Frontend Coverage Phase 3: Lieutenants Swarm

## Summary

This phase moves from bootstrap into supervised multi-lane execution.

Operating model:
- controller: orchestration only
- gitops lieutenant: all git, worktrees, staging, commits, and integration
- notify lieutenant: all Pushover communication through `$pushover-notify`
- workers: tightly bounded lane work only

Current baseline at phase start on `chore/expand-frontend-tests` (`HEAD 111466b5`):
- statements `33.60%`
- branches `25.28%`
- functions `29.59%`
- lines `48.65%`

Current skipped-spec distribution:
- `features/stats`: `31`
- `features/settings`: `26`
- `features/book`: `24`
- `features/readers`: `15`
- `shared`: `11`
- `features/metadata`: `7`
- `features/author-browser`: `5`
- `features/bookdrop`: `4`

Hard constraints:
- no runtime code changes without explicit approval
- git operations stay in one thread only
- notifications are mandatory and plain English
- workers may do seam work, but only within a short, high-yield budget
- branch coverage gain is the primary metric

## Step 0

This file is the execution artifact for the lieutenants swarm phase.

Do not reuse:
- `docs/plans/frontend-90-coverage-plan.md`
- `docs/plans/frontend-coverage-phase-2-supervised-swarm.md`

Every checkpoint appended here must record:
- timestamp
- active lanes
- accepted batch
- coverage totals
- bucket-level movement
- worker stop or replacement events
- blocked seams

## Worktree Model

Use a new worktree prefix for this phase:
- `.worktrees/covswarm-stats`
- `.worktrees/covswarm-query`
- `.worktrees/covswarm-dialog`
- `.worktrees/covswarm-harness` only if a dedicated shared-helper lane becomes necessary

Legacy `f90-*` worktrees:
- preserve and ignore
- snapshot their status once during pre-flight
- do not edit them
- do not clean them up during this phase

## Role Contract

### Controller

Responsibilities:
- launch and stop agents
- assign lanes and write scopes
- enforce heartbeats
- accept or reject worker batches
- tell gitops when to integrate
- tell notify when to send normal and urgent messages

Not allowed:
- no code edits
- no git
- no notifications

### Gitops Lieutenant

Responsibilities:
- verify git write access before lane launch
- create and manage `covswarm-*` branches and worktrees
- snapshot root and legacy worktree state
- stage accepted lane changes
- create conventional commits
- integrate accepted lane commits into the root branch
- quarantine or discard failed lane worktrees

Not allowed:
- no test implementation
- no runtime code edits
- no notifications

### Notify Lieutenant

Responsibilities:
- verify Pushover credentials
- run `$pushover-notify --dry-run` pre-flight
- send all progress and escalation messages

Normal-priority events:
- swarm launch
- bootstrap handoff
- lane launch
- worker stopped or replaced
- checkpoint update
- integration complete
- coverage moved
- final completion

Urgent events:
- blocker requiring a decision
- runtime change would be needed
- permission problem stops the swarm
- repeated worker-runtime failure
- controller halts the swarm

Cadence:
- never more often than every `2` minutes
- target every `2-3` minutes when meaningful progress exists
- always at least once every `5` minutes while work is ongoing
- urgent alerts break through immediately

### Workers

Responsibilities:
- operate only inside assigned lane paths
- consume existing shared harnesses and packages
- run targeted tests only for touched specs
- report on cadence

Not allowed:
- no git
- no notifications
- no package changes
- no docs changes
- no shared harness edits unless explicitly assigned as a harness worker

Required status line:
- `state | owned_paths | current_target | last_result | blocker | next_10m`

Allowed states:
- `bootstrapping`
- `implementing`
- `validating`
- `blocked`
- `handoff-ready`
- `stopped`

## Pre-Flight

Gitops pre-flight:
- confirm root checkout is clean except accepted baseline commits
- confirm `.worktrees/` exists and remains ignored
- snapshot root `git status`
- snapshot `git worktree list`
- snapshot status for each legacy `f90-*` worktree
- create fresh `covswarm-*` worktrees from current root `HEAD`

Notify pre-flight:
- confirm `PUSHOVER_APP_TOKEN` and `PUSHOVER_USER_KEY`
- run `$pushover-notify --dry-run`
- send one real normal-priority launch update after dry-run success

Worker runtime health gate:
- first useful status required within `2-3` minutes
- no heartbeat may exceed `5` minutes
- prompt contamination or silent workers fail the gate

Failure handling:
1. interrupt once for status
2. replace once with a stronger model if needed
3. if replacement also fails, pause that lane and either narrow it or run it locally
4. notify sends normal replacement update and urgent alert only if the swarm is blocked

## Lane Order

### Lane 1: Stats

Scope:
- `frontend/src/app/features/stats/**` spec files only

Goal:
- convert the next `2-4` high-yield stats specs using the non-rendering chart harness

Acceptance gate before opening lane 2:
- targeted Vitest for touched stats specs
- gitops integrates the accepted lane batch
- `just ui typecheck`
- `just ui lint`
- `just ui test`
- `just ui coverage-summary`
- controller confirms real `features/stats` branch movement

### Lane 2: Query/Signal

Start as soon as lane 1 passes its first acceptance gate.

Priority order:
1. `frontend/src/app/features/book/service/shelf.service.spec.ts`
2. `frontend/src/app/features/settings/user-management/user.service.spec.ts`
3. `frontend/src/app/features/magic-shelf/service/magic-shelf.service.spec.ts`

Deferred:
- `frontend/src/app/shared/service/custom-font.service.spec.ts`
  - needs a separate browser-font seam family

### Lane 3: Dialog/Overlay

Start once either:
- lane 1 reaches its second healthy checkpoint, or
- lane 2 reaches its first healthy checkpoint after launch

Priority order:
1. `frontend/src/app/features/bookdrop/component/bookdrop-bulk-edit-dialog/bookdrop-bulk-edit-dialog.component.spec.ts`
2. `frontend/src/app/features/book/components/book-browser/lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component.spec.ts`
3. `frontend/src/app/features/book/components/shelf-edit-dialog/shelf-edit-dialog.component.spec.ts`

Deferred:
- full reader runtimes
- mixed query-heavy dialog seams until the relevant lane is ready

Concurrency rule:
- start with lane 1 only
- expand to two active implementation lanes immediately after the first accepted stats batch
- expand to three only if heartbeat discipline and gitops throughput remain healthy

## Seam Valuation

Good seam work:
- reusing an existing shared harness
- small lane-local adapters that stay inside a spec file
- thin glue around an approved helper
- mature package usage instead of custom mocking

Borderline seam work:
- one lane-local helper reused across `2+` specs in the same lane
- one small signal/effect flush or callback adapter
- one thin branch-enabling stub that does not spill into other lanes

Bad seam work:
- inventing a new shared harness inside a worker lane
- reproducing package behavior with custom test infrastructure
- broad browser-runtime emulation for one spec
- `15-20` minutes of seam work without unlocking a real spec
- two consecutive candidate specs requiring bespoke seam rescue

Rule:
- each lane gets one short seam spike
- if it does not unlock a real spec, stop and escalate
- if the helper would serve multiple lanes, it becomes a dedicated harness task rather than silent worker drift

## Validation

Per worker batch:
- targeted Vitest on touched files

Per integrated lane batch:
- `just ui typecheck`
- `just ui lint`
- `just ui test`
- `just ui coverage-summary`

Major checkpoints:
- `just ui coverage`

## Checkpoints

### 2026-03-27 bootstrap handoff

Accepted baseline:
- bootstrap commit `111466b5`
- shared query/chart/dialog helpers landed
- `ng-mocks` added
- four canary specs activated

Current coverage totals:
- statements `33.60%`
- branches `25.28%`
- functions `29.59%`
- lines `48.65%`

Current blocked seams:
- browser-font-specific service testing
- full reader runtime seams

### 2026-03-28 replay window 1

Accepted replay batches:
- `2a3b82b6` `test(author-browser): cover author match component flows`
- `152ee9a0` `test(book): cover book notes component flows`
- `fadf732a` `test(author-browser): cover author editor component flows`
- `bf3c5fc0` `test(book): cover shelf creator component flows`
- `6fc5a42d` `test(book): cover book file service flows`
- `32c1de1d` `test(stats): cover page count chart bucketing`

Replay status:
- deferred queue drained into root
- root frontend gates passing after replay:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
  - `just ui coverage`
  - `just ui coverage-summary`

Current coverage totals:
- statements `37.50%`
- branches `27.59%`
- functions `34.66%`
- lines `51.74%`

Highest-value bucket movement this cycle:
- `features/author-browser` branches `5.19% -> 14.81%`
- `features/book` branches `27.62% -> 29.68%`
- `features/stats` branches `8.40% -> 9.44%`

Current queued accumulation batches:
- `covswarm-author3`
  - file: `frontend/src/app/features/author-browser/components/author-card/author-card.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/author-browser/components/author-card/author-card.component.spec.ts`
  - result: `1` file passed, `8` tests passed
  - proposed subject: `test(author-browser): cover author card component flows`
- `covswarm-bookcomp3`
  - file: `frontend/src/app/features/book/components/shelf-assigner/shelf-assigner.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/book/components/shelf-assigner/shelf-assigner.component.spec.ts`
  - result: `1` file passed, `7` tests passed
  - proposed subject: `test(book): cover shelf assigner component flows`
- `covswarm-author4`
  - file: `frontend/src/app/features/author-browser/components/author-detail/author-detail.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/author-browser/components/author-detail/author-detail.component.spec.ts`
  - result: `1` file passed, `8` tests passed
  - proposed subject: `test(author-browser): cover author detail component flows`
- `covswarm-bookcomp4`
  - file: `frontend/src/app/features/book/components/add-physical-book-dialog/add-physical-book-dialog.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn --cwd frontend vitest run src/app/features/book/components/add-physical-book-dialog/add-physical-book-dialog.component.spec.ts --config vitest.config.ts`
  - result: `1` file passed, `10` tests passed
  - proposed subject: `test(book): cover add physical book dialog flows`
- `covswarm-bookcomp5`
  - file: `frontend/src/app/features/book/components/book-sender/book-sender.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn ng test --watch=false --include src/app/features/book/components/book-sender/book-sender.component.spec.ts`
  - result: `1` file passed, `6` tests passed
  - proposed subject: `test(book): cover book sender component flows`
- `covswarm-stats2`
  - file: `frontend/src/app/features/stats/component/library-stats/charts/language-chart/language-chart.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/stats/component/library-stats/charts/language-chart/language-chart.component.spec.ts`
  - result: `1` file passed, `4` tests passed
  - proposed subject: `test(stats): cover language chart aggregation`
- `covswarm-stats3`
  - file: `frontend/src/app/features/stats/component/library-stats/charts/metadata-score-chart/metadata-score-chart.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/stats/component/library-stats/charts/metadata-score-chart/metadata-score-chart.component.spec.ts --config vitest.config.ts`
  - result: `1` file passed, `4` tests passed
  - proposed subject: `test(stats): cover metadata score chart states`
- `covswarm-bookcomp6`
  - file: `frontend/src/app/features/book/components/book-reviews/book-reviews.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/book/components/book-reviews/book-reviews.component.spec.ts --config vitest.config.ts`
  - result: `1` file passed, `10` tests passed
  - proposed subject: `test(book): cover book reviews component flows`
- `covswarm-bookcomp7`
  - file: `frontend/src/app/features/book/components/book-file-attacher/book-file-attacher.component.spec.ts`
  - targeted validation: `yarn vitest run src/app/features/book/components/book-file-attacher/book-file-attacher.component.spec.ts`
  - result: `1` file passed, `8` tests passed
  - proposed subject: `test(book): cover book file attacher flows`
- `covswarm-stats4`
  - file: `frontend/src/app/features/stats/component/user-stats/charts/reading-progress-chart/reading-progress-chart.component.spec.ts`
  - targeted validation: `env YARN_ENABLE_GLOBAL_CACHE=0 YARN_CACHE_FOLDER=../.yarn/cache corepack yarn vitest run src/app/features/stats/component/user-stats/charts/reading-progress-chart/reading-progress-chart.component.spec.ts`
  - result: `1` file passed, `5` tests passed
  - proposed subject: `test(stats): cover reading progress chart bucketing`

Next accumulation lanes:
- `features/stats/component/user-stats/charts/read-status-chart/read-status-chart.component.spec.ts`
- `features/stats/component/library-stats/charts/publication-trend-chart/publication-trend-chart.component.spec.ts`
- `features/book/components/duplicate-merger/duplicate-merger.component.spec.ts`

Current blocked seams:
- full `author-browser.component` shell remains high-risk and deferred
- browser-font-specific service testing remains deferred
- full reader runtime seams remain deferred
