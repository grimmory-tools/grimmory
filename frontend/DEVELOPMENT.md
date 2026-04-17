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

- Angular 21
- TypeScript
- PrimeNG + PrimeIcons
- Transloco for i18n
- Vitest for unit tests
- Angular ESLint
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

The production output is written to `dist/grimmory/`. The backend packaging flow consumes that bundle
when building the all-in-one production image.

Use `just install-ci` and `just ci-check` when you need the stricter CI-style dependency and audit
flow from a clean install.

## Frontend Conventions

- Follow the Angular style guide.
- All components are standalone. Do not add NgModules.
- Use `inject()` for dependency injection instead of constructor injection.
- Prefer PrimeNG components and existing project styling patterns over custom one-off UI primitives.
- Use SCSS for styling and keep the visual language consistent with the existing application.
- Use Transloco for user-facing strings. New strings belong under `src/i18n/`.
- Keep UI changes responsive for desktop and mobile layouts.
- Tests should use Vitest, not Karma or Jasmine.

## i18n and UI Copy

- Update all relevant locale files when adding or renaming translation keys.
- Keep translation-key changes separate from bulk JSON reformatting whenever practical.
- Prefer Grimmory naming for UI-visible labels and keys, while preserving compatibility shims only when
  they are still required by existing backend or migration behavior.

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
