# Grimmory repository instructions

## Repository shape

Grimmory is a self-hosted digital library delivered as one production container:

- `backend/` is a Java 25 Spring Boot 4 application. It owns the HTTP API, authentication and
  authorization, JPA persistence, Flyway migrations, background jobs, metadata/file processing,
  reader APIs, and the packaged runtime jar. The Java package root is `org.booklore`.
- `frontend/` is an Angular 22 standalone application. It owns routing, UI state, readers,
  localization, and browser-side integrations. The Angular build output is copied into the backend
  static resources during packaging.
- `deploy/`, `packaging/`, `Dockerfile`, and `dev.docker-compose.yml` describe deployment and the
  all-in-one runtime. Do not treat generated `backend/build/`, `frontend/dist/`, caches, or
  `node_modules/` as source.

Read `DEVELOPMENT.md` first for repository workflow, then the relevant
`backend/DEVELOPMENT.md` or `frontend/DEVELOPMENT.md`. `CONTRIBUTING.md` covers issue/PR policy and
`AI_POLICY.md` requires disclosure and human verification of AI-assisted work.

## Preferred commands

Use the root `Justfile` and its `api`/`ui` submodules when a recipe exists. The expected toolchain is
Java 25, Node.js 26+, pnpm 12.11.2, and Docker for the container workflow.

```bash
just bootstrap                 # Install UI dependencies and verify backend Gradle
just check                    # Backend + frontend verification
just test                     # Backend + frontend tests
just build                    # Backend + production frontend builds
just dev-up                   # Docker development stack
just dev-down
```

Backend commands:

```bash
just api run                   # Spring profile dev
just api build
just api test
just api test-class test_class=org.booklore.service.BookServiceTest
just api coverage
just api check
just api openapi-export
```

The backend recipes invoke `backend/gradlew`; from `backend/`, the equivalent single-test command is
`./gradlew test --tests "org.booklore.service.BookServiceTest"`. Backend tests use JUnit/Mockito/
AssertJ and normally run against H2 where appropriate; MariaDB is used by the development stack.

Frontend commands:

```bash
just ui install
just ui install-ci              # Frozen lockfile, as in CI
just ui dev
just ui build
just ui test
just ui coverage
just ui check
just ui ci-check
just ui lint                    # Oxlint + ESLint + Stylelint
just ui typecheck
just ui e2e-install
just ui e2e
just ui e2e-file spec=tests/example.spec.ts
```

To run one frontend unit-test file without the full suite, run
`pnpm -C frontend exec vitest run src/app/path/to/example.spec.ts`. To run one test by name, append
`-t "test name"`. Playwright supports the equivalent focused command through
`just ui e2e-file spec=tests/example.spec.ts`.

## Architecture and data flow

The backend follows a conventional Spring layering: controllers expose REST/integration endpoints,
services coordinate business workflows and background tasks, repositories access JPA entities, and
MapStruct mappers convert entities to API DTOs. `config/security/` contains JWT, OIDC, remote-auth,
Kobo/KOReader/OPDS authentication, and access annotations/aspects. `exception/` centralizes API
errors and global response handling. File/media readers and metadata providers are deliberately
backend concerns because libraries and storage are mounted into the runtime container.

MariaDB is the production database. Flyway applies the ordered SQL files in
`backend/src/main/resources/db/migration/`; Spring configuration is environment-variable driven
through `backend/src/main/resources/application*.yaml`. The frontend talks to the API through the
shared/core data services, uses an authenticated HTTP interceptor, and receives live notifications
over STOMP/WebSocket. Server state is managed with TanStack Query (`queryOptions()`/
`mutationOptions()` plus `injectQuery()`/`injectMutation()`), while local/UI state is signal-based.
The app is zoneless and bootstrapped with `bootstrapApplication`; routes are guarded and many feature
pages are lazy-loaded.

The UI contains both newer Grimmory UI surfaces and older Booklore/Optimus UI surfaces. New shared
UI uses Angular Aria, native elements, Tailwind, Lucide, and Signal Forms; existing areas may use
Optimus UI and SCSS. Follow the conventions of the feature being changed rather than attempting an
unrelated migration.

## Codebase-specific conventions

### Backend

- Prefer Spring Data repository methods or JPQL. Do not add native SQL unless explicitly approved.
- Use Lombok constructor injection (`@AllArgsConstructor`) and `@Slf4j`; do not add `@Autowired` or
  manually instantiate loggers.
- Entity classes use the `*Entity` suffix. Use MapStruct for entity/DTO mapping.
- Return API-facing failures through the existing `ApiError`/exception handling path, not raw
  `RuntimeException`.
- Reuse existing security patterns such as `@PreAuthorize("@securityUtil.isAdmin()")` and
  `@CheckBookAccess`/`@CheckLibraryAccess`.
- Add schema changes as a new `V<number>__Description.sql` migration. Never edit a released
  migration; use idempotent guards where the MariaDB dialect permits them.
- Keep UTC behavior and existing storage/path abstractions intact when changing persistence or file
  processing.

### Frontend

- All components are standalone; do not introduce NgModules. Use `inject()` for dependency
  injection.
- Use writable `signal`s for local state, `computed()`/`linkedSignal()` for derived state, and
  `toSignal()` rather than manually subscribing just to copy an Observable into a signal.
- Keep server data in TanStack Query services. Query services return query/mutation options; consuming
  components read query signals directly instead of duplicating them into component state. Pass
  TanStack cancellation signals through the existing query transport helpers.
- User-visible text belongs in Transloco under `frontend/src/i18n/en.json`; preserve interpolation
  placeholders and HTML tags. Do not hand-edit non-English locale files for normal code changes.
- Keep new UI keyboard-accessible, responsive, and consistent with the relevant old/new design
  system. Use Grimmory naming in visible labels.
- Existing findings are tracked in `frontend/oxlint-suppressions.json` and
  `frontend/eslint-suppressions.json`. Fix new findings; only run `just ui lint-prune` after fixing
  findings already recorded in those baselines.

## Contribution constraints

Work from an accepted issue; feature work targets `develop`, not `main`. Use branch prefixes such as
`feat/`, `fix/`, `refactor/`, and `docs/`. Commits and PR titles use Conventional Commits
(`feat(scope): subject`, `fix(scope): subject`, etc.). PRs must include the repository template,
link the issue, record exact manual testing, disclose AI usage, and run both `just ui check` and
`just api check` when applicable.
