# Grimmory UI Development

This document is the canonical development guide for `frontend`. It covers the local command surface,
setup, build and test workflows, and frontend-specific conventions.

For repository-wide contribution policy, branch strategy, PR requirements, and release semantics, start
with [../CONTRIBUTING.md](../CONTRIBUTING.md). For repo-level environment setup and Docker workflows,
see [../DEVELOPMENT.md](../DEVELOPMENT.md).

## Project Scope

The `frontend` project is the Angular browser application for Grimmory. It owns client-side routing,
stateful UI interactions, component styling, localization, and the compiled bundle packaged into the
production image.

## Stack

- Angular 22
- TypeScript
- TanStack Query
- Angular Aria + Tailwind CSS + Lucide icons
- Transloco for i18n
- Vitest for unit tests
- Oxlint + Angular ESLint
- SCSS

## Preferred Command Surface

Use [`Justfile`](Justfile) when possible:

```bash
just                 # List frontend recipes
just install         # Install or update dependencies for local development
just install-ci      # Install dependencies exactly as CI does
just dev             # Start the local dev server
just test            # Run frontend tests
just coverage        # Run tests with coverage output
just check           # Run the standard local verification pass
just ci-check        # Run the stricter CI-style verification pass
just lint            # Run the frontend linter
just lint-prune      # Remove fixed findings from the lint baselines
just build           # Build the production bundle
```

From the repository root, the same recipes are available through the `ui` namespace:

```bash
just ui install
just ui install-ci
just ui dev
just ui test
just ui coverage
just ui check
just ui lint
```

## Running Locally

For the normal frontend loop:

```bash
cd frontend
just install
just dev
```

The dev server runs on `http://localhost:4200` by default.

If you need the full application stack, start the backend and database from the repository root with
`just dev-up` or use the repo-level manual setup in [../DEVELOPMENT.md](../DEVELOPMENT.md).

## Build and Test

```bash
just build
just test
just coverage
just check
just lint
```

Existing lint findings are recorded in `oxlint-suppressions.json` and `eslint-suppressions.json`. Fix new
lint findings rather than adding them to the baselines. After fixing recorded findings, run
`just lint-prune` and commit the updated baseline files.

The production output is written to `dist/grimmory/`. The backend packaging flow consumes that bundle
when building the all-in-one production image.

Use `just install-ci` and `just ci-check` when you need the stricter CI-style dependency and audit
flow from a clean install.

## Frontend Conventions

- Follow the Angular style guide.
- All components are standalone. Do not add NgModules.
- Use `inject()` for dependency injection instead of constructor injection.
- New Grimmory UI uses Angular Aria, native elements, Tailwind, Lucide icons and Signal Forms, with its shared
  components in `src/app/shared/ui/`. The older Booklore UI uses Optimus UI, SCSS and its existing forms. Use
  both as appropriate.
- Bind writable signals directly to Optimus form components: `[(ngModel)]="selection"`.
- Keep state in signals, which is what re-renders the view in this zoneless app; use plain RxJS `Subject`s
  for event streams.
- Turn Observables into state with `toSignal()` rather than subscribing and setting a signal; debounce a
  signal with `debouncedSignal()` from `shared/util/`.
- Derive state with `computed()`, or `linkedSignal()` when it can also be set locally. Effects sync signal
  state to non-signal APIs such as the DOM, storage or third-party libraries.
- Use Grimmory naming in UI-visible labels.
- Use Transloco for user-facing strings. New strings belong under `src/i18n/`.
- Keep UI changes responsive for desktop and mobile layouts.
- Make UI keyboard accessible, with accessible names, visible focus, appropriate focus movement and
  restoration, and WCAG AA contrast.
- Use Vitest for unit tests.

## Server Data

Server data goes through TanStack Query, following its documented practice, with
`src/app/features/book/data/` as the reference. Query services return `queryOptions()` and
`mutationOptions()`, and the component that uses the data calls `injectQuery()` or `injectMutation()` with
them. Services don't hold queries or expose server data as signals. Read query and mutation signals such
as `data()` and `isPending()` directly, deriving from them with `computed()` rather than copying them into
component state.
Query functions can pass their `signal` to the request through `httpGet()` or `toAbortablePromise()` in
`core/data/query-transport.ts`, so TanStack can cancel requests it no longer needs.

## Validation Before Opening a PR

Run the frontend checks locally before sending a PR:

```bash
just install
just check
```

If your change affects layout, responsive behavior, or interaction design, also run the full stack
locally and capture screenshots or a short screen recording for the PR.

If you specifically want to mirror the stricter CI dependency policy, run:

```bash
just install-ci
just ci-check
```
