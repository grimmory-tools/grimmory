# Developing Grimmory

This document covers the technical side of working in the Grimmory codebase: environment setup, build
commands, testing, and conventions. If you haven't already, read [CONTRIBUTING.md](CONTRIBUTING.md)
first — it covers the process side: how issues become PRs, what gets a PR closed, and how to submit
work.

## Project Structure

```
grimmory/
├── frontend/                # Angular frontend (TypeScript, PrimeNG)
├── booklore-api/            # Spring Boot backend (Java 25, Gradle)
├── deploy/                  # Compose, Helm, and Podman deployment examples
├── packaging/docker/        # Container runtime assets used by the Docker build
├── dev.docker-compose.yml   # Development Docker stack
├── assets/                  # Shared assets (logos, icons)
└── local/                   # Local development helpers
```

## Preferred Command Surface

Use the root [`Justfile`](Justfile) as the primary local command surface. It wraps the commands below
into a consistent interface for both humans and agents.

```bash
just                       # Show all root, api, and ui recipes
just check                 # Run the local verification pass
just test                  # Run backend + frontend tests
just api run               # Start the backend with the dev profile
just ui dev                # Start the frontend dev server
just dev-up                # Start the Docker dev stack
just image-build           # Build the production image locally
```

> **Tip:** Agents and automation should prefer `just` recipes where a suitable recipe exists, so local
> workflows and documented commands stay aligned.

> **Tip:** Set `GRIMMORY_COMPOSE_FILE=/path/to/compose.yml` if you need the root `just` recipes to
> target a different development compose file.

## Component Guides

Use this document for repo-level workflow, then drop into the component-specific guides when working
inside a project:

- Backend: [booklore-api/DEVELOPMENT.md](booklore-api/DEVELOPMENT.md)
- Frontend: [frontend/DEVELOPMENT.md](frontend/DEVELOPMENT.md)

---

## Development Setup

### Option 1: Docker Development Stack (Recommended)

The fastest way to get a working environment. No local toolchain required.

```bash
just dev-up
```

This starts:

| Service    | URL / Port            |
| ---------- | --------------------- |
| Frontend   | <http://localhost:4200> |
| Backend    | <http://localhost:6060> |
| MariaDB    | localhost:3366        |
| Debug port | localhost:5005        |

All ports are configurable via environment variables (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`,
`REMOTE_DEBUG_PORT`) in the compose file.

```bash
just dev-down   # Stop the stack
```

### Option 2: Manual Setup

For full control over each component or IDE integration (debugging, hot-reload, etc.).

#### Prerequisites

| Tool                    | Version | Download                                     |
| ----------------------- | ------- | -------------------------------------------- |
| Java                    | 25+     | [Adoptium](https://adoptium.net/)            |
| Node.js + Corepack/Yarn | 24+     | [nodejs.org](https://nodejs.org/)            |
| MariaDB                 | 10.6+   | [mariadb.org](https://mariadb.org/download/) |
| Git                     | latest  | [git-scm.com](https://git-scm.com/)          |

#### 1. Database

Start MariaDB and create the database:

```sql
CREATE DATABASE IF NOT EXISTS grimmory;
CREATE USER 'grimmory_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON grimmory.* TO 'grimmory_user'@'localhost';
FLUSH PRIVILEGES;
```

> **Tip:** You can also start just the database with `just db-up`.

#### 2. Backend


```bash
just api run
```

#### 3. Frontend

```bash
just ui install
just ui dev   # Available at http://localhost:4200 with hot-reload
```

---

## API Reference Docs

When enabled, API documentation is available at:

- Scalar UI: `http://localhost:6060/api/docs`
- OpenAPI JSON: `http://localhost:6060/api/openapi.json`

---

## Running Tests

Always run tests before submitting a pull request.

**Frontend (Vitest):**

```bash
just ui test        # Run all tests
just ui coverage    # With coverage report (output: coverage/)
```

**Backend (JUnit + Gradle):**

```bash
just api test                                                              # Run all tests
just api test-class test_class=org.booklore.service.BookServiceTest        # Specific class
just api coverage                                                          # Coverage report
```

---

## Building the Production Docker Image

To verify your changes in an environment identical to production, build and run the full Docker image
locally. The multi-stage `Dockerfile` compiles both the Angular frontend and the Spring Boot backend,
so no local toolchain is needed beyond Docker.

> **Note:** The first build downloads and caches dependencies in Docker layers. Subsequent builds are
> significantly faster.

```bash
just image-build          # Build the image
just db-up                # Start only the database from the dev stack
just image-run            # Run the production image against it
```

The application will be available at <http://localhost:6060>.

```bash
just db-down    # Stop the database when done
just dev-down   # Or stop the full stack if started with `just dev-up`
```

### Cross-platform builds

```bash
just image-build linux/arm64 grimmory:local-arm64
```

### Tips

- **Memory:** The image defaults to 60% of container RAM. Use `--memory=512m` to simulate constrained
  environments.
- **Volumes:** Mount book directories to `/books`, data/config to `/app/data`, and an optional bookdrop
  folder to `/bookdrop`.
- **Logs:** Use `docker logs -f grimmory-local` to follow application logs.

---

## Branch Naming

Create branches from `develop` using these prefixes:

| Prefix      | Use for            | Example                      |
| ----------- | ------------------ | ---------------------------- |
| `feat/`     | New features       | `feat/epub-reader-support`   |
| `fix/`      | Bug fixes          | `fix/book-import-validation` |
| `refactor/` | Code restructuring | `refactor/auth-flow`         |
| `docs/`     | Documentation      | `docs/update-install-guide`  |

```bash
git checkout develop
git checkout -b feat/your-feature-name
```

> **Note:** Feature work targets `develop`. `main` is reserved for stable releases and release tags.

---

## Commit Messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`

**Examples:**

```
feat(reader): add keyboard navigation for page turning
fix(api): resolve memory leak in book scanning service
feat(auth)!: migrate to OAuth 2.1

BREAKING CHANGE: OAuth 2.0 is no longer supported
```

PR titles targeting `develop` or `main` must follow the same format. Release impact is derived from
commit type:

| Type                                            | Release impact |
| ----------------------------------------------- | -------------- |
| `feat`                                          | Minor          |
| `fix`, `perf`, `refactor`                       | Patch          |
| `docs`, `ci`, `build`, `chore`, `test`, `style` | Changelog only |

Feature work is squash-merged into `develop`. Do not expect a second squash from `develop` to `main` —
release automation depends on preserved conventional commit history.
