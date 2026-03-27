# Frontend 90% Coverage Controller Plan

## Summary

Drive the frontend to an honest `>=90%` on statements, branches, functions, and lines by attacking the coldest branch-deficit areas first.

Current baseline on `chore/expand-frontend-tests` after `just ui typecheck`, `just ui lint`, `just ui test`, `just ui coverage`, and `just ui coverage-summary`:
- Global: statements `19.98%`, branches `15.25%`, functions `16.57%`, lines `36.00%`
- Lane ranking by uncovered branches:
  - `metadata + settings + stats`: `3200`
  - `book + author + series + notebook`: `2194`
  - `readers`: `904`
  - `shared`: `640`
  - `core/auth/routes`: `51`

Operating rules:
- No Playwright during the main coverage push
- No runtime code changes unless explicitly escalated as a narrow exception
- Test workers do not perform Git operations
- A dedicated Git/integration worker owns all Git and plan-doc mutations
- A dedicated notifier agent owns Pushover notifications
- The controller is orchestration-only: it does not generate tests, edit code, commit, integrate changes, or send notifications
- Progress toward branch coverage is the primary metric; low-yield churn is grounds for stopping and reassigning work

## Roles

### Controller
Owns orchestration only:
- run pre-flight checks
- spawn workers
- assign or reassign lanes and target files
- poll status
- detect anomalies
- order stop-all reviews
- decide whether a bucket is accepted, narrowed, restarted, or discarded
- set coordination flags consumed by the notifier agent

The controller does not:
- edit tests
- edit runtime code
- update the plan doc directly
- run Git commands
- salvage large weak buckets by hand
- call the Pushover helper directly

### Git/Integration Worker
Owns all repo mutations outside test generation:
- create and maintain `.worktrees/`
- create worker branches and worktrees
- keep `.worktrees/` ignored if needed
- update `docs/plans/frontend-90-coverage-plan.md`
- inspect `git status`, `git diff --stat`, and worktree health across all worker trees
- integrate accepted buckets one at a time
- create controller-approved commits with Conventional Commit messages and grouped markdown bodies
- prune or recreate discarded worktrees when instructed

### Notifier Agent
Owns all watch/phone notifications through the `pushover-notify` skill:
- validate Pushover delivery in pre-flight
- send a periodic high-level status summary every 5 minutes
- send immediate human-needed alerts only when the controller explicitly marks the run as requiring the user to return
- never send noisy per-test spam
- never inspect or mutate Git state directly
- repeating summary and escalation behavior should be driven by one controller-owned loop, not repeated per-agent commands; use a temporary local script under `local-scripts/` when a cadence is needed

### Test Workers
Own one lane each and only touch tests plus test-local helpers within owned paths unless explicitly escalated:
1. `core/auth/routes`
2. `book + author + series + notebook`
3. `metadata + settings + stats`
4. `readers`
5. `shared`

Test workers do not:
- run Git commands
- edit the plan doc
- integrate other workers’ work
- drift into Playwright or harness churn

## Pre-Flight Phase

Before any worker launch, the controller must prove the run can proceed unattended.

### Pre-flight checks
1. Confirm the branch and repo state are readable and sane.
- `git -C /Users/james/Projects/grimmory/grimmory status --short --branch`
- `git -C /Users/james/Projects/grimmory/grimmory worktree list`
- record any pre-existing untracked or unrelated files so they are not mistaken for worker drift

2. Confirm the durable plan path exists.
- `docs/plans/frontend-90-coverage-plan.md` must be present and writable by the Git/integration worker

3. Confirm worktree support is usable.
- `.gitignore` must contain `.worktrees/`
- if `.worktrees/` does not yet exist, that is acceptable; the Git worker creates it later

4. Confirm Git capabilities are available without interactive approval.
- repo-local `git -C /Users/james/Projects/grimmory/grimmory ...` commands must succeed
- if branch creation or worktree creation requires approval, do not launch workers

5. Confirm the frontend command surface is usable.
- `just ui typecheck`
- `just ui lint`
- `just ui test`
- `just ui coverage`
- `just ui coverage-summary`
- any failure here blocks launch until the controller re-baselines and updates the plan

6. Confirm coordination transport is available.
- preferred: `valkey-cli ping` must return `PONG`
- fallback: if Valkey is unavailable, switch to controller polling via agent messages only
- if neither Valkey nor reliable agent polling is available, do not launch unattended

7. Confirm notifier capability is available and working.
- `PUSHOVER_APP_TOKEN` and `PUSHOVER_USER_KEY` must be present
- notifier agent runs the skill-local helper once with `--dry-run`
- notifier agent then sends one real low-noise pre-flight notification
- if real delivery cannot be confirmed, unattended launch is blocked

8. Confirm worker lanes are disjoint and target lists are current.
- regenerate the branch-deficit ranking from current coverage output
- verify each initial target file belongs to exactly one lane

9. Confirm the Git/integration worker can own the repo-mutating path end to end.
- create worktrees
- inspect diffs
- update plan doc
- commit accepted buckets
- if any of these are uncertain, do not start the swarm

### Pre-flight output
The controller must produce a launch record in the plan doc via the Git/integration worker containing:
- timestamp
- baseline coverage totals
- lane ranking by uncovered branches
- capability results for Git, Just, Valkey, and Pushover
- known pre-existing repo noise
- explicit `launch approved` or `launch blocked`

### Launch blockers
Do not launch workers if any of the following are true:
- Git repo-local commands are not usable without approval
- the plan doc cannot be updated
- frontend baseline commands are failing
- coordination transport is unavailable
- Pushover delivery cannot be validated
- lane ownership is ambiguous
- current repo state is too noisy to distinguish worker drift from pre-existing changes

## Launch Record

- Status: pre-flight completed, swarm narrowed after incident review
- Branch: `chore/expand-frontend-tests`
- Repo state: after cleanup, root checkout is expected to show only the plan-doc modification below
- Current HEAD: `b652260a`
- Worktree list:
  - `/Users/james/Projects/grimmory/grimmory`
  - `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-book-author-series-notebook`
  - `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-core-auth-routes`
  - `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-metadata-settings-stats`
  - `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-readers`
  - `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-shared`
- `.worktrees/` ignore rule: present in `.gitignore`
- Historical pre-existing repo noise to record: `booklore-ui-migration-prompt.md`
- Notification state: paused after false-positive human-needed alerts; resume only through the single local loop under `local-scripts/`
- Latest validated coverage checkpoint from the surviving lane:
  - global: statements `20.6%`, branches `15.76%`, functions `17.38%`, lines `37.12%`
  - `core`: statements `90.83%`, branches `93.04%`, functions `76.77%`, lines `100%`

### Incident Log

- A nonstandard initial worktree setup was detected during launch prep.
- The root checkout remained normal throughout the incident and was not modified outside standard repo-local git operations.
- Malformed `.worktrees/` admin copies were removed safely.
- The lane worktrees were recreated successfully with standard `git worktree add` flows under `/Users/james/Projects/grimmory/grimmory/.worktrees/`.
- The first notifier path generated false-positive "return to machine" alerts; notifications are now paused until they are restarted through the single local status loop.
- The book lane used direct `yarn install`, which violates the required `just` command surface and makes the batch non-integratable.
- The metadata lane modified `metadata-viewer.component.ts`, which violates the no-runtime-change rule for this coverage push.
- The readers and shared lanes introduced install churn (`node_modules/` or `yarn.lock`) instead of staying within narrow test-only ownership.

### Stop-All Review

- Malformed initial worktree setup incident: nonstandard `.worktrees/` admin copies were detected and repaired before lane launch continued.
- Second incident: shared lane edits landed in the root checkout instead of the shared worktree, which polluted the root with untracked shared specs.
- Decision:
  - discard the shared batch
  - preserve the core/auth worktree edits for integration after fresh validation
  - discard the book batch until it is rerun without direct `yarn`
  - discard the metadata batch because it changed runtime code
  - discard the readers batch because it introduced install churn instead of a clean test bucket
  - keep the remaining worktrees only as quarantined scratch state until they are either cleaned or recreated
  - restart later only with stricter worktree-path instructions, tighter drift checks, and the notifier still paused unless the local loop is explicitly started

## Current Integration Checkpoint

- Accepted candidate bucket: `core/auth/routes`
- Changed files:
  - `frontend/src/app/app.routes.spec.ts`
  - `frontend/src/app/core/custom-reuse-strategy.spec.ts`
  - `frontend/src/app/core/security/auth-interceptor.service.spec.ts`
  - `frontend/src/app/core/security/secure-src.directive.spec.ts`
  - `frontend/src/app/core/services/loading.service.spec.ts`
  - `frontend/src/app/core/security/oauth2-management/authentication-settings.component.spec.ts`
- Validation rerun in `/Users/james/Projects/grimmory/grimmory/.worktrees/f90-core-auth-routes`:
  - `just ui typecheck` passed
  - `just ui lint` passed
  - `just ui test` passed
  - `just ui coverage` passed
  - `just ui coverage-summary` passed
- Residual non-blocking noise:
  - existing Angular optional-chain warnings in `app.topbar.component.html`
  - existing Vitest stderr from intentional error-path assertions
- Proposed Conventional Commit subject: `test(core-auth-routes): add branch coverage for auth settings and route helpers`
- Proposed grouped commit body:
  - Core auth settings:
    - cover provider persistence, group sync, force-only handling, and mapping dialog flows
    - cover delete-group-mapping success and failure paths
  - Core utilities:
    - cover route reuse null-handle and missing scroll-position branches
    - cover secure image fallback and object URL cleanup
    - cover loading overlay custom-message and multi-loader cursor behavior
  - Validation:
    - `just ui typecheck`
    - `just ui lint`
    - `just ui test`
    - `just ui coverage`
    - `just ui coverage-summary`

## Lane Ownership And First Targets

### `metadata + settings + stats`
Highest-yield first wave.
Primary targets:
- `features/metadata/component/book-metadata-center/metadata-editor/metadata-editor.component.ts`
- `features/metadata/component/book-metadata-center/metadata-viewer/metadata-viewer.component.ts`
- `features/metadata/component/book-metadata-center/metadata-searcher/metadata-searcher.component.ts`
- `features/metadata/component/book-metadata-center/metadata-picker/metadata-picker.component.ts`
- `features/settings/task-management/task-management.component.ts`
- `features/stats/component/user-stats/charts/series-progress-chart/series-progress-chart.component.ts`
- `features/stats/component/library-stats/charts/author-universe-chart/author-universe-chart.component.ts`
- `features/settings/view-preferences-parent/view-preferences/view-preferences.component.ts`

### `book + author + series + notebook`
Includes book-adjacent cold areas.
Primary targets:
- `features/bookdrop/component/bookdrop-file-review/bookdrop-file-review.component.ts`
- `features/book/components/book-browser/book-card/book-card.component.ts`
- `features/author-browser/components/author-browser/author-browser.component.ts`
- `features/book/components/book-browser/book-browser.component.ts`
- `features/book/components/series-page/series-page.component.ts`
- `features/book/components/book-browser/book-table/book-table.component.ts`
- `features/library-creator/library-creator.component.ts`

### `readers`
Prioritize control-flow-heavy reader surfaces before shallow shells.
Primary targets:
- `features/readers/cbx-reader/cbx-reader.component.ts`
- `features/readers/audiobook-player/audiobook-player.component.ts`
- `features/readers/ebook-reader/core/event.service.ts`
- `features/readers/ebook-reader/ebook-reader.component.ts`
- `features/readers/ebook-reader/features/selection/selection.service.ts`
- `features/readers/pdf-reader/pdf-reader.component.ts`

### `shared`
Keep as an explicit lane because it is still materially behind.
Primary targets:
- `shared/components/file-mover/file-mover-component.ts`
- `shared/components/book-uploader/book-uploader.component.ts`
- `shared/layout/component/layout-menu/app.menu.component.ts`
- `shared/service/url-helper.service.ts`
- `shared/components/icon-picker/icon-picker-component.ts`
- `shared/service/reading-session.service.ts`
- `shared/service/audiobook-session.service.ts`

### `core/auth/routes`
Smallest lane, good candidate for an early clean win.
Primary targets:
- `core/security/oauth2-management/authentication-settings.component.ts`
- any remaining branch gaps in `core/custom-reuse-strategy.ts`
- any remaining branch gaps in `core/services/loading.service.ts`
- any remaining branch gaps in `core/security/auth-interceptor.service.ts`
- any remaining branch gaps in `core/security/secure-src.directive.ts`

## Status And Communication Protocol

Use a controller-visible heartbeat protocol so unattended runs can be supervised without passive waiting.

### Status transport
Default:
- each test worker writes heartbeat and progress records to Valkey using a finite TTL namespace like `codex:grimmory:f90:<lane>:status`
- the Git/integration worker writes per-worktree Git snapshots to `codex:grimmory:f90:git:<lane>`
- the controller writes orchestration state to keys like:
  - `codex:grimmory:f90:controller:summary`
  - `codex:grimmory:f90:controller:human_needed`
  - `codex:grimmory:f90:controller:launch_state`
- the notifier agent reads only those shared coordination keys plus lane summaries
- if periodic polling or notification must run continuously, prefer a single controller-owned temporary script over repeated agent shell calls

Fallback if Valkey is unavailable:
- workers send progress messages to the controller thread and the controller relays summary state to the notifier agent through direct agent messages

### Test worker heartbeat payload
Emit on start, after target selection, after each meaningful test batch, after each validation run, on block, and before handoff.
Required fields:
- `lane`
- `owned_paths`
- `current_targets`
- `state`: one of `surveying`, `writing_tests`, `running_targeted`, `running_validation`, `ready_for_handoff`, `blocked`, `aborted`
- `spec_files_touched`
- `last_targeted_command`
- `last_targeted_result`
- `last_validation_commands`
- `last_validation_results`
- `latest_observed_coverage_signal`
- `residual_risks`
- `updated_at`

Coverage signal should be lightweight and frequent:
- at minimum, report target-file count and targeted-test pass/fail
- after any substantial batch, report owned-file coverage movement from local coverage output if available
- do not run expensive full coverage repeatedly just to emit heartbeats

### Git worker snapshot payload
Emit on a fixed polling cadence for every worktree.
Required fields:
- `lane`
- `worktree_path`
- `branch`
- `git_status_short`
- `diff_stat`
- `changed_files`
- `plan_doc_state`
- `updated_at`

### Notifier behavior
Periodic summary:
- every 5 minutes
- low-noise, high-level only
- include current global coverage if available, active lanes, lanes ready for handoff, stalled lanes, and whether the run is healthy or in review
- prefer compact multiline messages through stdin; use `--title "Codex"` and `--monospace` when formatting helps

Immediate human-needed alerts:
- send only when the controller explicitly marks `human_needed=true`
- examples:
  - pre-flight blocked
  - notification path broken
  - Git/worktree operations unexpectedly require approval
  - ambiguous repo state needs human judgment
  - all viable lanes stalled after stop-all review
  - a runtime-code exception appears necessary
- use a more attention-grabbing notification than the periodic summary, but do not use emergency retry mode unless absolutely necessary

### Polling cadence
- Controller polls heartbeats every 5 minutes
- Git worker snapshots each worktree every 3 to 5 minutes
- Notifier agent sends a periodic summary every 5 minutes
- Any worker heartbeat older than 10 minutes is stale
- Any lane with repeated stale heartbeats triggers review

## Acceptance Criteria Per Test Worker Bucket

A bucket is handoff-ready only if all conditions hold:
- touched files stay within owned lane
- no runtime code change unless explicitly escalated and isolated
- targeted tests for the owned area pass via the Just command surface
- `just ui typecheck` passes
- `just ui lint` passes
- `just ui test` passes
- worker emits final handoff payload including:
  - changed files
  - commands run and results
  - residual risks or known gaps
  - proposed Conventional Commit subject
  - proposed grouped markdown commit body

Test workers should prefer root commands:
- `just ui ...`
Or, when working inside the frontend subtree:
- `just --justfile frontend/Justfile --working-directory frontend ...`

No direct Yarn commands in worker instructions.

## Controller Intervention Rules

The controller should over-index on intervention rather than waiting for an unobtainable goal.

Trigger a stop-all review when any of these occur:
- a worker heartbeat goes stale
- Git snapshot shows cross-lane file drift
- a worker accumulates a large diff without passing validation
- a worker reports repeated validation failures on the same bucket
- a worker has multiple test batches with no credible coverage movement
- Git state looks anomalous or inconsistent across worktrees
- a worker starts chasing decorative render coverage instead of branch-bearing logic

Default low-yield threshold:
- if two successive meaningful batches do not materially reduce the lane’s target-file branch deficit, stop and review
- “materially” means visible improvement in the current target cluster, not just more tests added

Stop-all review sequence:
1. Controller interrupts all test workers.
2. Git worker captures final status and diff snapshots for every worktree.
3. Controller classifies each lane as:
   - `keep and integrate`
   - `keep but narrow`
   - `restart with new targets`
   - `discard`
4. Git worker updates the plan doc with the review outcome.
5. Controller updates the shared summary state.
6. Notifier agent sends the next periodic summary reflecting the review outcome.
7. If human judgment is required, controller sets `human_needed=true` and notifier sends an immediate alert.
8. Controller restarts only the lanes that still have a defensible next move.

Throwing away weak progress is acceptable. Avoid salvage rabbit holes.

## Integration Workflow

The Git/integration worker, under controller direction, performs all integration.

Per accepted bucket:
1. verify handoff payload completeness
2. inspect worktree Git state and changed-file scope
3. integrate one bucket only
4. create one Conventional Commit for that bucket
5. run:
   - `just ui typecheck`
   - `just ui lint`
   - `just ui test`
6. after every two integrations, or sooner for a large bucket, run:
   - `just ui coverage`
   - `just ui coverage-summary`
7. update `docs/plans/frontend-90-coverage-plan.md` with:
   - current totals
   - lane totals
   - accepted bucket
   - discarded or restarted buckets
   - next target shortlist
8. publish the updated high-level status for the notifier agent

Default integration order by expected branch gain:
1. `metadata + settings + stats`
2. `book + author + series + notebook`
3. `readers`
4. `shared`
5. `core/auth/routes`

## Plan Doc Requirements

The plan doc is durable session state and must always be resumable without ambiguity.

After baseline, after pre-flight, and after every accepted integration or stop-all review, it must contain:
- latest global coverage numbers
- latest lane ranking by uncovered branches
- active workers and owned paths
- current top target files per lane
- accepted buckets and commit subjects
- rejected buckets and why they were rejected
- open blockers and whether any runtime exception was approved
- latest launch record
- latest notification state
- next immediate actions

The Git/integration worker owns these updates. The controller decides the content.

## Next Immediate Actions

1. Commit the durable plan update as the controller checkpoint.
2. Integrate the validated `core/auth/routes` bucket and rerun `just ui typecheck`, `just ui lint`, and `just ui test` in the root checkout.
3. Re-run `just ui coverage` and `just ui coverage-summary` after the first integration so the root checkout becomes the source of truth for the improved totals.
4. Clean or recreate the rejected worktrees before any lane restarts.
5. Keep notifications paused unless the single local loop is deliberately started with tighter incident-only thresholds.

## Assumptions

- The new `default.rules` entries are sufficient for repo-local Git operations under `git -C /Users/james/Projects/grimmory/grimmory`
- No escalated privileges will be available during the unattended run
- Test workers can rely on Just-based validation only
- Valkey is preferred for transient coordination and is currently available
- The `pushover-notify` skill helper is available and Pushover credentials are present in the environment
- The controller remains orchestration-only for the full run, including during failures and reviews
