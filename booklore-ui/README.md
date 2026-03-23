# Grimmory UI

The `booklore-ui` project is the Angular frontend for Grimmory. It owns the browser application, component styling, client-side routing, stateful UI interactions, and the compiled bundle that is packaged into the current production image.

## Stack

- Angular 21
- TypeScript
- PrimeNG + PrimeIcons
- Transloco for i18n
- Vitest for unit tests
- Angular ESLint

## Local Command Surface

Use [`Justfile`](Justfile) when possible. It is the primary frontend command surface for both humans and agents.

```bash
just                 # List frontend recipes
just install         # Install or update dependencies for local development
just install-ci      # Install dependencies exactly as CI does
just dev             # Start the Angular dev server
just test            # Run the frontend test suite
just check           # Run the standard local verification pass
just lint            # Run the frontend linter
just build           # Build the production bundle
```

The repository root exposes the same recipes through the `ui` namespace:

```bash
just ui install
just ui install-ci
just ui dev
just ui test
just ui check
just ui lint
```

## Running Locally

```bash
cd booklore-ui
just install
just dev
```

The development server runs on port `4200` by default.

## Build and Test

```bash
just build
just check
just test
just coverage
just lint
```

The production output is written to `dist/grimmory/` and is consumed by the backend packaging flow when building the all-in-one production image.

Use `just ci-check` when you want the stricter CI-style flow from a clean install, including the severity-gated audit step.

## UI-Specific Notes

- This project uses standalone Angular components.
- Dependency injection should use `inject()`.
- Styling uses SCSS.
- UI strings live in `src/i18n/`.
- The current production bundle is still packaged into the backend jar during the root Docker build.

## More Detail

Frontend-specific contributor rules and conventions live in [CONTRIBUTING.md](CONTRIBUTING.md).
