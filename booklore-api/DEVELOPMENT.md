# Grimmory API Development

This document is the canonical development guide for `booklore-api`. It covers the local command
surface, setup expectations, build and test workflows, and backend-specific conventions.

For repository-wide contribution policy, branch strategy, PR requirements, and release semantics, start
with [../CONTRIBUTING.md](../CONTRIBUTING.md). For repo-level environment setup and Docker workflows,
see [../DEVELOPMENT.md](../DEVELOPMENT.md).

## Project Scope

The `booklore-api` project is the Spring Boot backend for Grimmory. It owns the HTTP API, database
migrations, background processing, authentication, and the packaged runtime jar used by the production
container.

## Stack

- Java 25
- Spring Boot 4
- Gradle Wrapper
- Spring Data JPA + Flyway
- MariaDB in production, H2 for selected tests
- JUnit 5, Mockito, AssertJ, JaCoCo

## Preferred Command Surface

Use [`Justfile`](Justfile) when possible:

```bash
just                 # List backend recipes
just run             # Start the backend with the dev profile
just test            # Run backend tests
just coverage        # Generate JaCoCo coverage output
just check           # Run the Gradle check lifecycle
just build           # Build the backend jar
just tasks           # Show available Gradle tasks
```

From the repository root, the same recipes are available through the `api` namespace:

```bash
just api run
just api test
just api coverage
just api check
```

## Running Locally

The backend expects a MariaDB instance and a local `application-dev.yml` with your database and
storage paths. The common backend loop is:

```bash
cd booklore-api
just run
```

If you need a different Spring profile:

```bash
just run profile=local
```

Use the repo-level guide in [../DEVELOPMENT.md](../DEVELOPMENT.md) for the full Docker stack, MariaDB
setup, and cross-project local workflows.

## Build and Test

```bash
just build
just test
just coverage
just check
```

The frontend bundle is consumed during packaging when the UI build output exists. For normal
backend-only development and test runs, frontend resources are optional.

## API Docs (`API_DOCS_ENABLED`)

- `application.yaml` binds `app.api-docs.enabled` to `API_DOCS_ENABLED` (default `false`).
- `application.yaml` binds `springdoc.api-docs.enabled` to `app.api-docs.enabled`.
- `application-dev.yml` enables docs for local dev profile runs by default.
- For runtime or container profiles, set `API_DOCS_ENABLED=true` to expose:
  - `/api/openapi.json`
  - `/api/docs`

## Packaging Notes

- The production container image is built from the repository root `Dockerfile`.
- That Docker build compiles the UI first and passes the resolved frontend dist path into the backend
  build.
- The backend jar remains the packaged application artifact for the current all-in-one runtime model.

## Backend Conventions

- Use Spring Data JPA repository methods or JPQL. Do not introduce native queries unless a maintainer
  has explicitly approved them.
- Prefer constructor injection via Lombok `@AllArgsConstructor`. Do not add `@Autowired`.
- Use Lombok `@Slf4j` for logging. Do not instantiate loggers manually.
- Throw API-facing errors via `ApiError` helpers instead of raw `RuntimeException`.
- Entities use the `*Entity` suffix. DTOs and API models do not.
- Use MapStruct for entity-to-DTO mapping instead of hand-written mapping code.
- Security checks should use the existing project patterns such as
  `@PreAuthorize("@securityUtil.isAdmin()")` or `@CheckBookAccess`.
- Use modern Java language features where they clarify the code.
- Keep imports explicit. Do not inline fully-qualified class names.

## Migrations

- Flyway migrations live in `src/main/resources/db/migration/`.
- Name migrations `V<number>__<Description>.sql`.
- Never modify a released migration. Add a new migration instead.
- Prefer idempotent guards such as `IF EXISTS` and `IF NOT EXISTS` when the SQL dialect allows them.

## Testing

- Unit tests should normally use JUnit 5 + Mockito + AssertJ.
- Use `@ExtendWith(MockitoExtension.class)` for unit tests.
- Use `@SpringBootTest` only when you need a real Spring integration context.
- Keep tests behavior-oriented. Avoid shallow tests that only exercise getters, setters, or mocks
  without asserting meaningful outcomes.

## Validation Before Opening a PR

Run the backend checks locally before sending a PR:

```bash
just test
just check
```

If your change affects migrations, persistence, or API behavior, also run the full stack locally and
verify the behavior manually.
