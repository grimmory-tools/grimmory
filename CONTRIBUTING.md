# Contributing to Booklore

🎉 Thanks for your interest in contributing to **Booklore**, a modern, self-hostable digital library system for books and comics. Whether you're fixing bugs, adding features, improving documentation, or asking questions - your contribution matters!

---

## 📚 Overview

**Booklore** is a self-hostable platform designed to manage and read books and comics. It includes:

- **Frontend**: Angular 20, TypeScript, PrimeNG 19, Tailwind CSS
- **Backend**: Java 21, Spring Boot 3.5
- **Authentication**: Local JWT + optional OIDC (e.g. Authentik)
- **Database**: MariaDB
- **Deployment**: Docker-compatible, reverse proxy-ready

---

## 📦 Project Structure

```
booklore/
├── booklore-ui/       # Angular frontend
├── booklore-api/      # Spring Boot backend
├── assets/            # Shared assets
```

---

## 🚀 Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:

```bash
git clone https://github.com/adityachandelgit/BookLore.git
cd booklore
```

---

## 🧱 Local Development Setup

### 1. Prerequisites

- **Java 21+**
- **Node.js 18+**
- **MariaDB**
- **Docker and Docker Compose**

---

### 2. Frontend Setup

To set up the Angular frontend:

```bash
cd booklore-ui
npm install
ng serve
```

The dev server runs at `http://localhost:4200/`.

> ⚠️ Use `--force` with `npm install` only as a last resort for dependency conflicts.

---

### 3. Backend Setup

#### a. Configure `application-dev.yml`

Create or edit `booklore-api/src/main/resources/application-dev.yml`:

```yaml
app:
  path-book: '/path/to/booklore/books'      # Directory for book/comic files
  path-config: '/path/to/booklore/config'   # Directory for thumbnails, metadata, etc.

spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://localhost:3333/booklore?createDatabaseIfNotExist=true
    username: root
    password: Password123
```

> 🔧 Replace `/path/to/...` with actual local paths

#### b. Run the Backend

```bash
cd booklore-api
./gradlew bootRun
```

---

## 🧪 Testing

### Frontend

Run unit tests using:

```bash
cd booklore-ui
ng test
```

### Backend

Run backend tests using:

```bash
cd booklore-api
./gradlew test
```

---

## 🛠️ Contributing Guidelines

### 💡 Bug Reports

- Check [existing issues](https://github.com/adityachandelgit/BookLore/issues)
- Include reproduction steps, expected vs. actual behavior, and logs if possible

### 🌟 Feature Requests

- Clearly explain the use case and benefit
- Label the issue with `feature`

### 🔃 Code Contributions

- Create a feature branch:

```bash
git checkout -b feat/my-feature
```

- For bug fixes:

```bash
git checkout -b fix/my-fix
```

- Follow code conventions, keep PRs focused and scoped
- Link the relevant issue in your PR
- Test your changes
- Target the `develop` branch when opening PRs

---

## 🧼 Code Style & Conventions

- **Angular**: Follow the [official style guide](https://angular.io/guide/styleguide)
- **Java**: Use modern features (Java 17+), clean structure
- **Format**: Use linters and Prettier where applicable
- **UI**: Use Tailwind CSS and PrimeNG components consistently

---

## 📝 Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

Examples:

- `feat: add column visibility setting to book table`
- `fix: correct metadata locking behavior`
- `docs: improve contributing instructions`

---

## 🙏 Code of Conduct

Please be respectful, inclusive, and collaborative. Harassment, abuse, or discrimination of any kind will not be tolerated.

---

## 💬 Community & Support

- Use [GitHub Discussions](https://github.com/adityachandelgit/BookLore/discussions)
- Discord server: https://discord.gg/Ee5hd458Uz

---

## 📄 License

Booklore is open-source and licensed under the MIT License. See [`LICENSE`](./LICENSE) for details.

---

Happy contributing!