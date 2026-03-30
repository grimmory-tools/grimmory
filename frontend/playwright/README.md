## Playwright Harness Rules

This directory is the browser-test harness for the frontend. Treat it as a
strictly controlled surface.

### Execution rules

- Never hard-code port `4200` in specs or fixtures.
- In parallel runs, every worker must set a unique `PLAYWRIGHT_PORT`.
- Use `PLAYWRIGHT_BASE_URL` only when attaching to an already-running app.
- Default local command:

```bash
PLAYWRIGHT_PORT=4301 just --justfile frontend/Justfile --working-directory frontend e2e-file playwright/login-and-books.spec.ts
```

### Authoring rules

- Start from the existing scenario fixture instead of creating ad hoc route
  handlers inside a spec.
- Keep one scenario fixture per spec file. Do not build one giant catch-all fake
  backend for unrelated areas.
- Mock only the endpoints required by the scenario. Unexpected API calls should
  fail fast.
- Prefer role, label, placeholder, and stable text selectors. Use CSS classes
  only when the UI has no better semantic hook.
- Do not add reader/browser flows until they have their own dedicated scenario
  fixture and port-safe validation command.

### Agent rules

- Do not invent a new Playwright config per worker.
- Do not change the web server port in code for one-off runs; use env vars.
- Do not add browser specs that depend on broad route shims copied from other
  scenarios.
- Use the `frontend/Justfile` entrypoints for browser install, server startup,
  and spec execution. Do not invoke Yarn directly from docs or ad hoc commands.
