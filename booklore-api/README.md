# Grimmory API

The `booklore-api` project is the Spring Boot backend for Grimmory. It owns the HTTP API, database migrations, background processing, authentication, and the packaged runtime jar that the production container runs.

## Stack

- Java 25
- Spring Boot 4
- Gradle Wrapper
- Spring Data JPA + Flyway
- MariaDB in production, H2 for selected tests
- JUnit 5, Mockito, AssertJ, JaCoCo

## Local Command Surface

Use [`Justfile`](Justfile) when possible. It is the primary backend command surface for both humans and agents.

```bash
just                 # List backend recipes
just run             # Start bootRun with the dev profile
just test            # Run the backend test suite
just coverage        # Run tests and generate JaCoCo output
just check           # Run the Gradle check lifecycle
just tasks           # Show available Gradle tasks
```

The repository root also exposes the same recipes through namespacing:

```bash
just api test
just api run
just api coverage
```

## Running Locally

The backend expects a MariaDB instance and a local `application-dev.yml` with your database and storage paths. The higher-level setup lives in [../CONTRIBUTING.md](../CONTRIBUTING.md), but the common backend loop is:

```bash
cd booklore-api
just run
```

If you need a different Spring profile:

```bash
just run profile=local
```

## Build and Test

```bash
just build
just test
just coverage
just check
```

The frontend bundle is consumed during packaging when the UI build output exists. For normal backend-only development and test runs, the frontend resources are optional.

## Packaging Notes

- The production container image is built from the repository root `Dockerfile`.
- That Docker build compiles the UI first and passes the resolved frontend dist path into the backend build.
- The backend jar remains the packaged application artifact for the current all-in-one runtime model.

## More Detail

Backend-specific contributor rules and conventions live in [CONTRIBUTING.md](CONTRIBUTING.md).
