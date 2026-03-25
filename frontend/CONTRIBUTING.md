# Contributing to Grimmory UI

This document covers frontend-specific development and review expectations for `frontend`.

For repository-wide contribution policy, branch strategy, PR requirements, and release semantics, start with [../CONTRIBUTING.md](../CONTRIBUTING.md).

## Preferred Command Surface

Use [`Justfile`](Justfile) when possible:

```bash
just                 # List frontend recipes
just install         # Install or update dependencies for local development
just install-ci      # Install dependencies exactly as CI does
just dev             # Start the local dev server
just test            # Run frontend tests
just check           # Run the standard local verification pass
just lint            # Run the frontend linter
just build           # Build the production bundle
```

From the repository root, the same recipes are available through the `ui` namespace:

```bash
just ui install
just ui install-ci
just ui dev
just ui test
just ui check
just ui lint
```

## Frontend Conventions

- Follow the Angular style guide.
- All components are standalone. Do not add NgModules.
- Use `inject()` for dependency injection instead of constructor injection.
- Prefer PrimeNG components and project styling patterns over custom one-off UI primitives.
- Use SCSS for styling and keep the visual language consistent with the existing application.
- Use Transloco for user-facing strings. New strings belong under `src/i18n/`.
- Keep UI changes responsive for desktop and mobile layouts.
- Tests should use Vitest, not Karma or Jasmine.

## i18n and UI Copy

- Update all relevant locale files when adding or renaming translation keys.
- Keep translation-key changes separate from bulk JSON reformatting whenever practical.
- Prefer Grimmory naming for UI-visible labels and keys, while preserving compatibility shims only when they are still required by existing backend or migration behavior.

## Validation Before Opening a PR

Run the frontend checks locally before sending a PR:

```bash
just install
just check
```

If your change affects layout, responsive behavior, or interaction design, also run the full stack locally and capture screenshots or a short screen recording for the PR.

If you specifically want to mirror the stricter CI dependency policy, run:

```bash
just install-ci
just ci-check
```
