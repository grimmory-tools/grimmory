# Contributing to Grimmory

Thanks for your interest in contributing to Grimmory! Whether you're fixing bugs, adding features, improving documentation, or asking questions, every contribution helps.

## What is Grimmory?

**Grimmory** is a self-hostable digital library platform for managing and reading books and comics. It is the community-maintained successor to Booklore.

**Tech Stack:**

- **Frontend:** Angular 20, TypeScript, PrimeNG 19
- **Backend:** Java 25, Spring Boot 3.5
- **Authentication:** Local JWT + optional OIDC (e.g., Authentik)
- **Database:** MariaDB
- **Deployment:** Docker-compatible, reverse proxy-ready

## Table of Contents

- [Before You Start](#before-you-start)
- [Where to Start](#where-to-start)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Running Tests](#running-tests)
- [Building the Production Docker Image](#building-the-production-docker-image)
- [Making Changes](#making-changes)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Backend Conventions](#backend-conventions)
- [Frontend Conventions](#frontend-conventions)
- [Reporting Bugs](#reporting-bugs)
- [Community & Support](#community--support)
- [Code of Conduct](#code-of-conduct)
- [License](#license)

---

## Before You Start

> **Issue first, PR second.** Every pull request must be linked to an approved issue. If you want to work on something, [open an issue](https://github.com/grimmory-tools/grimmory/issues/new) (or find an existing one) and wait for a maintainer to approve it before writing code. PRs submitted without a linked, approved issue will be closed.

This protects both your time and ours. It ensures that the work is actually wanted and that you're heading in the right direction before you invest effort.

**What will get your PR closed immediately:**
- No linked issue
- No screenshots or screen recording proving the change works
- No test output pasted in the PR
- Bulk AI-generated changes that clearly haven't been reviewed or tested
- Unsolicited refactors, cleanups, or "improvements" nobody asked for
- PRs with 1000+ changed lines (split them up)

## Where to Start

Not sure where to begin? Look for issues labeled:

- [`good first issue`](https://github.com/grimmory-tools/grimmory/labels/good%20first%20issue) - small, well-scoped tasks ideal for newcomers
- [`help wanted`](https://github.com/grimmory-tools/grimmory/labels/help%20wanted) - tasks where maintainers would appreciate a hand

---

## Getting Started

### Fork and Clone

First, [fork the repository](https://github.com/grimmory-tools/grimmory/fork) on GitHub, then clone your fork locally:

```bash
git clone https://github.com/<your-username>/grimmory.git
cd grimmory
git remote add upstream https://github.com/grimmory-tools/grimmory.git
```

### Keep Your Fork in Sync

Before starting new work, pull the latest changes from upstream:

```bash
git fetch upstream
git checkout develop
git merge upstream/develop
git push origin develop
```

> **Note:** Feature work targets `develop`. `main` is reserved for stable releases and release tags.

---

## Development Setup

### Project Structure

```
grimmory/
├── booklore-ui/             # Angular frontend (TypeScript, PrimeNG)
├── booklore-api/            # Spring Boot backend (Java 25, Gradle)
├── deploy/                  # Compose, Helm, and Podman deployment examples
├── packaging/docker/        # Container runtime assets used by the Docker build
├── dev.docker-compose.yml   # Development Docker stack
├── assets/                  # Shared assets (logos, icons)
└── local/                   # Local development helpers
```

### Option 1: Docker Development Stack (Recommended)

The fastest way to get a working environment. No local toolchain required.

```bash
docker compose -f dev.docker-compose.yml up
```

This starts:

| Service    | URL / Port            |
|------------|-----------------------|
| Frontend   | http://localhost:4200 |
| Backend    | http://localhost:6060 |
| MariaDB    | localhost:3366        |
| Debug port | localhost:5005        |

All ports are configurable via environment variables (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `REMOTE_DEBUG_PORT`) in the compose file.

```bash
# To stop
docker compose -f dev.docker-compose.yml down
```

### Option 2: Manual Setup

For full control over each component or IDE integration (debugging, hot-reload, etc.).

#### Prerequisites

| Tool          | Version | Download                                     |
|---------------|---------|----------------------------------------------|
| Java          | 25+     | [Adoptium](https://adoptium.net/)            |
| Node.js + npm | 20+     | [nodejs.org](https://nodejs.org/)            |
| MariaDB       | 10.6+   | [mariadb.org](https://mariadb.org/download/) |
| Git           | latest  | [git-scm.com](https://git-scm.com/)         |

#### 1. Database

Start MariaDB and create the database:

```sql
CREATE DATABASE IF NOT EXISTS booklore;
CREATE USER 'booklore_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON booklore.* TO 'booklore_user'@'localhost';
FLUSH PRIVILEGES;
```

> **Tip:** You can also spin up MariaDB via Docker: `docker compose -f local/docker-compose-maria.yml up -d`

#### 2. Backend

Create a dev config at `booklore-api/src/main/resources/application-dev.yml`:

```yaml
app:
  path-book: '/path/to/booklore-data/books'
  path-config: '/path/to/booklore-data/config'

spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://localhost:3306/booklore?createDatabaseIfNotExist=true
    username: booklore_user
    password: your_password
```

Replace the paths with actual directories on your system and ensure they exist with read/write permissions.

```bash
cd booklore-api
./gradlew bootRun --args='--spring.profiles.active=dev'

# Verify
curl http://localhost:8080/actuator/health
```

#### 3. Frontend

```bash
cd booklore-ui
npm install
ng serve
```

The UI will be available at http://localhost:4200 with hot-reload enabled.

> If you hit dependency issues, try `npm ci --force`.

---

## Running Tests

Always run tests before submitting a pull request.

**Frontend (Vitest):**

```bash
cd booklore-ui
ng test               # Run all tests
ng test --coverage    # With coverage report (output: coverage/)
```

**Backend (JUnit + Gradle):**

```bash
cd booklore-api
./gradlew test                                                        # Run all tests
./gradlew test --tests "com.booklore.api.service.BookServiceTest"     # Specific class
./gradlew test jacocoTestReport                                       # Coverage report
```

---

## Building the Production Docker Image

To verify your changes in an environment identical to production, build and run the full Docker image locally. The multi-stage `Dockerfile` in the project root compiles both the Angular frontend and the Spring Boot backend, so you don't need any local toolchain beyond Docker.

> **Note:** The first build downloads dependencies and caches them in Docker layers. Subsequent builds are significantly faster.

### Run

You need a MariaDB instance accessible to the container. The easiest way is to use the existing Docker Compose database from the dev stack, or bring your own:

```bash

# From the repository root
docker build -t grimmory:local .

# Start only the database from the dev stack
docker compose -f dev.docker-compose.yml up -d backend_db

# Run the production image against it, reusing the dev stack's data
docker run --rm -it \
  --name grimmory-local \
  --network host \
  -e "SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3366/booklore?createDatabaseIfNotExist=true" \
  -e "SPRING_DATASOURCE_USERNAME=booklore" \
  -e "SPRING_DATASOURCE_PASSWORD=booklore" \
  -v ./shared/data:/app/data \
  -v ./shared/books:/books \
  -v ./shared/bookdrop:/bookdrop \
  -p 6060:6060 \
  grimmory:local
```

The application will be available at http://localhost:6060.

### Cleanup

```bash
# Stop and remove the container
docker stop grimmory-local

# Stop the database service when done
docker compose -f dev.docker-compose.yml down
```

### Cross-platform build (optional)

If you want to test a different architecture (e.g., ARM64 on an x86 machine):

```bash
docker buildx build --platform linux/arm64 -t grimmory:local-arm64 --load .
```

### Tips

- **Memory:** The image defaults to 60% of container RAM. Limit with `--memory=512m` or similar to simulate constrained environments.
- **Volumes:** Mount your book directories to `/books`, data/config to `/app/data`, and an optional bookdrop folder to `/bookdrop`. The example above reuses the dev stack's `shared/` folders so your existing library and covers are available.
- **Logs:** Application logs are written to stdout. Use `docker logs -f grimmory-local` to follow them.

---

## Making Changes

### Branch Naming

Create branches from `develop` using these prefixes:

| Prefix      | Use for            | Example                      |
|-------------|--------------------|------------------------------|
| `feat/`     | New features       | `feat/epub-reader-support`   |
| `fix/`      | Bug fixes          | `fix/book-import-validation` |
| `refactor/` | Code restructuring | `refactor/auth-flow`         |
| `docs/`     | Documentation      | `docs/update-install-guide`  |

```bash
git checkout develop
git checkout -b feat/your-feature-name
```

### Commit Messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`

Examples:

```
feat(reader): add keyboard navigation for page turning
fix(api): resolve memory leak in book scanning service
feat(auth)!: migrate to OAuth 2.1

BREAKING CHANGE: OAuth 2.0 is no longer supported
```

PR titles targeting `develop` or `main` must use the same format. Feature work is squash-merged into `develop`, and stable releases are computed from the resulting commit history on `main`.

Release impact is derived from commit type:

- `feat` => minor release
- `fix`, `perf`, `refactor` => patch release
- `docs`, `ci`, `build`, `chore`, `test`, `style` => changelog-only unless paired with a release-triggering commit

### Workflow

1. Create a branch from `develop`
2. Make your changes in small, logical commits
3. Run both frontend and backend tests
4. Update documentation if your changes affect usage
5. Run the linter and fix any issues
6. Push to your fork and open a PR targeting `develop`

Maintainers promote cleaned `develop` history to `main` for stable releases. Do not expect a second squash step from `develop` to `main`; release automation depends on preserved conventional commit history.

---

## Submitting a Pull Request

Before opening your PR:

- [ ] PR is linked to an **approved** issue (PRs without a linked issue will be closed)
- [ ] All tests pass (`./gradlew test` and `ng test`)
- [ ] Actual test output is pasted in the PR description
- [ ] Code follows project conventions (see [Backend Conventions](#backend-conventions), [Frontend Conventions](#frontend-conventions))
- [ ] No lint errors
- [ ] Branch is up-to-date with `develop`
- [ ] You ran the full stack locally and manually verified the change works
- [ ] PR description includes a screen recording or screenshots proving it works
- [ ] PR description explains *what* changed and *why*
- [ ] PR contains a single logical change (one bug fix OR one feature)
- [ ] No unrelated refactors, style changes, or "improvements" are bundled in
- [ ] **PR is reasonably sized.** PRs with 1000+ changed lines will be closed without review. Break large changes into small, focused PRs.
- [ ] **For user-facing features:** include the required docs updates in this repo or in the active Grimmory docs surface

> When you open your PR on GitHub, a **PR template** will appear. Fill it out completely, including test output and screenshots.

### AI-Assisted Contributions

Contributions using AI tools (Copilot, Claude, ChatGPT, etc.) are welcome, but the quality bar is the same as human-written code. **If you ship it, you own it.**

We've seen a sharp increase in AI-generated PRs where the contributor clearly never ran the code, didn't test it, and can't explain what it does. These waste maintainer time and will be closed on sight.

**If you use AI to help write code, you must still:**

- **Run the code yourself.** Build the project, start the full stack, and manually verify the change works. Trusting the AI's output without running it is not acceptable.
- **Review every line.** You must be able to explain any part of your change during review. If asked "why did you do X?" and your answer is "the AI suggested it," the PR will be closed.
- **Keep PRs focused.** One feature, one fix, or one refactor per PR. Do not submit a dump of everything the AI suggested.
- **Scrutinize AI-generated tests.** They often pass trivially without asserting anything meaningful. Tests that don't validate real behavior will be rejected.
- **Clean up.** Remove dead code, placeholder comments, empty catch blocks, and unnecessary boilerplate.
- **Stay in scope.** Do not submit refactors, style changes, or "improvements" the AI suggested that are outside the scope of the linked issue.

---

## Backend Conventions

- Use Spring Data JPA repository methods or JPQL. No native queries unless explicitly approved by a maintainer.
- Constructor injection via Lombok `@AllArgsConstructor`. No `@Autowired`.
- Logging via Lombok `@Slf4j`. No manual `LoggerFactory.getLogger(...)`.
- Throw errors via `ApiError` enum (`ApiError.SOME_ERROR.createException()`). No raw `RuntimeException`.
- Entities use `*Entity` suffix; DTOs drop it (e.g., `BookEntity` vs `Book`).
- Use MapStruct for entity-to-DTO mapping. No hand-written mapping code.
- Security: `@PreAuthorize("@securityUtil.isAdmin()")` or `@CheckBookAccess`. No `@Secured` or `@RolesAllowed`.
- Testing: JUnit 5 + Mockito + AssertJ. `@ExtendWith(MockitoExtension.class)` for unit tests, `@SpringBootTest` only for integration tests.
- Use modern Java features (records, sealed classes, pattern matching, text blocks, etc.).
- No fully qualified class names inline. Always use imports.
- Flyway migrations go in `booklore-api/src/main/resources/db/migration/` with naming `V<number>__<Description>.sql`.
- Never modify a released migration. Always create a new migration file for changes.
- Use idempotent guards in migrations (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `DROP ... IF EXISTS` before re-creating).

---

## Frontend Conventions

- Follow the [Angular style guide](https://angular.dev/style-guide).
- All components are standalone. No NgModules.
- Use `inject()` for dependency injection. No constructor injection.
- PrimeNG for UI components. SCSS for styling. Transloco for i18n.
- Testing with Vitest (not Karma/Jasmine).
- All UI features must be responsive (desktop + mobile).

---

## Reporting Bugs

1. **Search [existing issues](https://github.com/grimmory-tools/grimmory/issues)** to avoid duplicates.
2. **Open a new issue** with the `bug` label including:
   - Clear, descriptive title (e.g., "Book import fails with PDF files over 100MB")
   - Steps to reproduce
   - Expected vs. actual behavior
   - Screenshots or error logs (if applicable)
   - Environment details (OS, browser, Grimmory version)

**Example:**

```
Title: Book metadata not updating after manual edit

Steps to Reproduce:
1. Navigate to any book detail page
2. Click "Edit Metadata"
3. Change the title and click "Save"
4. Refresh the page

Expected: Title should persist after refresh
Actual: Title reverts to original value

Environment: Chrome 120, macOS 14.2, Grimmory 1.2.0
```

---

## Community & Support

- **Discord:** [Join the server](https://discord.gg/Ee5hd458Uz) for questions and discussion
- **GitHub Issues:** [Report bugs or request features](https://github.com/grimmory-tools/grimmory/issues)

---

## Code of Conduct

We're committed to providing a welcoming and inclusive environment for everyone.

**Do:**
- Be respectful and considerate
- Welcome newcomers and help them learn
- Accept constructive criticism gracefully
- Focus on what's best for the community

**Don't:**
- Harass, troll, or discriminate
- Make personal attacks or insults
- Publish others' private information

Instances of unacceptable behavior may result in temporary or permanent ban from the project.

---

## License

Grimmory is licensed under the [AGPL-3.0 License](./LICENSE). By contributing, you agree that your contributions will be licensed under the same terms.

---

Thank you for being part of the Grimmory community!
