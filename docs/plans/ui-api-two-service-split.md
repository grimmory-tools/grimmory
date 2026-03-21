# UI/API Runtime Split Plan

## Summary

Step 0: write this plan to `docs/plans/ui-api-two-service-split.md`.

After the first stable Grimmory release that stays faithful to the original Booklore deployment shape, evaluate a runtime split that stops serving the Angular SPA from the Spring Boot jar.

The target shape is:

- one container that serves the prebuilt frontend and acts as the reverse proxy,
- one container that runs the Spring Boot API,
- no requirement to support a three-container `ui + api + proxy` deployment model,
- and no requirement to split repositories or release cadence immediately.

This is intentionally a phase-two simplification plan, not a first-release blocker.

## Why Consider It

- The frontend is already a precompiled Angular SPA, not a server-rendered Node application.
- The current packaging path is brittle because the frontend bundle must be injected into the backend jar at build time.
- Spring is currently doing two jobs:
  - serving API and websocket traffic,
  - and pretending to be the static web server for the SPA.
- A proxy-plus-static-frontend container is a more conventional place to handle:
  - SPA fallback,
  - caching headers,
  - compression,
  - and static asset delivery.

## What This Is Not

- Not a repo split.
- Not a forced `ui + api + proxy` topology.
- Not a first-release requirement.
- Not a decision to abandon the existing all-in-one image immediately.

## Proposed Target Architecture

### Runtime Topology

- `grimmory-web`
  - serves the compiled Angular frontend
  - handles SPA fallback to `index.html`
  - proxies `/api/**`, `/komga/**`, and `/ws` to the API service
- `grimmory-api`
  - runs the Spring Boot application
  - exposes backend endpoints only
  - no longer owns static SPA asset serving

### Likely Technology Choice

- Prefer `caddy` or `nginx` inside the `grimmory-web` image.
- Do not introduce `pm2` unless the frontend becomes a real Node runtime application later.
- Keep the frontend static.

## Pros

- Removes a fake coupling between the UI artifact and the backend jar.
- Makes frontend packaging failures easier to isolate.
- Simplifies the backend responsibility to API, websocket, auth, and domain logic.
- Makes the frontend delivery path more conventional for caching, compression, and routing.
- Reduces the need for Gradle to know anything about frontend build artifacts.
- Creates a cleaner base for future CDN or edge caching if that ever matters.

## Cons

- Changes the deployment contract for self-hosters.
- Introduces an extra service for operators to understand.
- Requires careful websocket and auth proxy behavior.
- Requires documentation updates for Compose, Helm, and Podman examples.
- Risks mixing an architectural cleanup into the same milestone as the first Grimmory continuation release.

## Validation Against Current App Shape

This split is feasible because the production frontend already behaves like a separate SPA:

- it uses `window.location.origin` for API calls,
- it derives websocket URLs from the browser origin,
- and the browser OIDC callback route already lives in the frontend.

That means the system already wants a same-origin reverse proxy setup. The main issue is packaging and delivery, not application semantics.

## What Would Need To Change

### Backend

- Remove SPA static resource fallback from Spring MVC.
- Keep only API, websocket, auth callback, OPDS, and Komga endpoints.
- Verify same-origin or explicit-origin CORS behavior for split deployments.

### Frontend

- Build the Angular app as a standalone static artifact.
- Ship that artifact inside a web/proxy image.
- Keep the current `window.location.origin` production behavior unless there is a strong reason to externalize API base URLs.

### Proxy/Web Image

- Serve static files.
- Route unknown frontend paths to `index.html`.
- Proxy:
  - `/api/**`
  - `/komga/**`
  - `/ws`
- Handle websocket upgrades correctly.

### Deployment Surfaces

- Add two-service Compose examples.
- Add two-service Helm values and templates.
- Decide whether Podman examples should keep parity or stay all-in-one.

## Packaging and Release Strategy Options

### Option A: Replace the Single Image

- Publish `grimmory-web` and `grimmory-api` only.
- Simplest architecture.
- Highest operator-facing change.

### Option B: Keep a Compatibility Image

- Publish:
  - `grimmory`
  - `grimmory-web`
  - `grimmory-api`
- The compatibility image keeps the existing single-container story alive while the split deployment matures.
- Higher maintenance burden, but smoother migration path.

### Recommended Direction

- Do not change the first Grimmory release shape.
- After that release, evaluate whether:
  - the split should become the default deployment shape,
  - and whether a compatibility image is worth the extra maintenance.

## Sequencing Recommendation

1. Ship the first Grimmory release in the current all-in-one shape.
2. Stabilize the current Docker and Gradle packaging path enough that the release is trustworthy.
3. Prototype the split runtime in a branch:
   - separate API image,
   - separate web/proxy image,
   - updated Compose example.
4. Validate:
   - login and OIDC callback flow,
   - websocket behavior,
   - SPA deep-link refreshes,
   - healthchecks,
   - and upgrade ergonomics for self-hosters.
5. Decide whether the split becomes:
   - the new default,
   - or an optional deployment mode with a compatibility image retained.

## Test Plan

- Confirm the frontend loads through the proxy/web container with deep-link refresh support.
- Confirm `/api/**`, `/komga/**`, and `/ws` proxy correctly to the backend.
- Confirm websocket connections survive proxying.
- Confirm OIDC login, callback, and logout still work with the split topology.
- Confirm the existing database and volume layout do not need to change for operators.
- Confirm a self-hoster can move from the all-in-one image to the split deployment with a documented, low-friction path.

## Assumptions

- The first Grimmory release should remain close to the original Booklore operational model.
- Self-hosters are the primary audience, so deployment simplicity still matters more than architectural purity.
- A two-service model is acceptable; a mandatory three-container model is not the target.
- The frontend remains a static SPA rather than a server-rendered Node application.
