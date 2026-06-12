# Upstream-Friendly Maintenance

These rules keep fork-specific GrimmLink and OPF work reviewable and reduce
conflicts when changes are rebased onto upstream.

## Code Boundaries

- Keep backend feature work isolated under `org.booklore.grimmlink` and
  `org.booklore.opf`.
- Avoid expanding `/api/koreader/**`; new public GrimmLink work belongs under
  `/api/grimmlink/v1`.
- Avoid frontend changes in backend sync branches.
- Avoid workflow changes in backend sync branches.
- Avoid package manager changes in backend sync branches.

## Database Migrations

- Do not rename or edit released upstream migrations.
- Add a new migration for every schema change.
- Follow the fork migration range documented in the repository README.

## Branch And Review Policy

- Keep CI changes in a separate fork-specific branch.
- Keep documentation changes in a separate docs branch when possible.
- Treat `codex/clean-grimmlink-api-island` as reference only, not as a base
  branch.
- Start upstream-friendly backend work from `upstream/main`.
- Keep backend, frontend, CI, release, and documentation changes separated
  unless a task genuinely requires a cross-surface update.

Before handoff, run targeted tests for the changed behavior and then the wider
suite for every affected surface. Document any suite that could not be run and
the reason it was skipped.
