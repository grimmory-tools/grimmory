# Contributing to Grimmory API

This document covers backend-specific development and review expectations for `booklore-api`.

For repository-wide contribution policy, branch strategy, PR requirements, and release semantics, start with [../CONTRIBUTING.md](../CONTRIBUTING.md).

## Preferred Command Surface

Use [`Justfile`](Justfile) when possible:

```bash
just                 # List backend recipes
just run             # Start the backend with the dev profile
just test            # Run backend tests
just coverage        # Generate JaCoCo coverage output
just check           # Run the Gradle check lifecycle
```

From the repository root, the same recipes are available through the `api` namespace:

```bash
just api test
just api run
just api check
```

## API Docs (`API_DOCS_ENABLED`)

- `application.yaml` binds `app.api-docs.enabled` to `API_DOCS_ENABLED` (default `false`).
- `application.yaml` binds `springdoc.api-docs.enabled` to `app.api-docs.enabled`.
- `application-dev.yml` enables docs for local dev profile runs by default.
- For runtime/container profiles, set `API_DOCS_ENABLED=true` to expose:
  - `/api/openapi.json`
  - `/api/docs`

## Backend Conventions

- Use Spring Data JPA repository methods or JPQL. Do not introduce native queries unless a maintainer has explicitly approved them.
- Prefer constructor injection via Lombok `@AllArgsConstructor`. Do not add `@Autowired`.
- Use Lombok `@Slf4j` for logging. Do not instantiate loggers manually.
- Throw API-facing errors via `ApiError` helpers instead of raw `RuntimeException`.
- Entities use the `*Entity` suffix. DTOs and API models do not.
- Use MapStruct for entity-to-DTO mapping instead of hand-written mapping code.
- Security checks should use the existing project patterns such as `@PreAuthorize("@securityUtil.isAdmin()")` or `@CheckBookAccess`.
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
- Keep tests behavior-oriented. Avoid shallow tests that only exercise getters, setters, or mocks without asserting meaningful outcomes.

## Validation Before Opening a PR

Run the backend checks locally before sending a PR:

```bash
just test
just check
```

If your change affects migrations, persistence, or API behavior, also run the full stack locally and verify the behavior manually.
