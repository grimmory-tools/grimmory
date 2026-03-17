
# <p align="center">
<picture>
<source media="(prefers-color-scheme: dark)" srcset="assets/logo-with-text-dark.svg">
<source media="(prefers-color-scheme: light)" srcset="assets/logo-with-text-light.svg">
<img src="assets/logo-with-text-light.svg" alt="Grimmory" height="80" />
</picture>

</p>

<p align="center"><strong>Your books deserve a home. This is it.</strong></p>

<p align="center">
Grimmory is a self-hosted app that brings your entire book collection under one roof.<br/>
Organize, read, annotate, sync across devices, and share, all without relying on third-party services.
</p>

<p align="center">
  <a href="https://github.com/grimmory-tools/grimmory/releases"><img src="https://img.shields.io/github/v/release/grimmory-tools/grimmory?color=818CF8&style=flat-square&logo=github" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/grimmory-tools/grimmory?color=fab005&style=flat-square" alt="License" /></a>
  <a href="https://hub.docker.com/r/grimmory/grimmory"><img src="https://img.shields.io/docker/pulls/grimmory/grimmory?color=2496ED&style=flat-square&logo=docker&logoColor=white" alt="Docker Pulls" /></a>
  <a href="https://github.com/grimmory-tools/grimmory/stargazers"><img src="https://img.shields.io/github/stars/grimmory-tools/grimmory?style=flat-square&color=ffd43b" alt="Stars" /></a>
  <a href="https://discord.gg/FwqHeFWk"><img src="https://img.shields.io/badge/Discord-5865F2?style=flat-square&logo=discord&logoColor=white" alt="Discord" /></a>
  <a href="https://opencollective.com/grimmory"><img src="https://img.shields.io/opencollective/all/grimmory?style=flat-square&color=7FADF2&logo=opencollective" alt="Open Collective" /></a>
  <!-- <a href="https://hosted.weblate.org/engage/grimmory/"><img src="https://img.shields.io/weblate/progress/grimmory?style=flat-square&logo=weblate&logoColor=white&color=2ECCAA" alt="Translate" /></a> -->
</p>

<p align="center">
  <a href="https://grimmory.org/">🌐 Website</a> ·
  <a href="https://grimmory.org/docs/getting-started">📖 Docs</a> ·
  <!-- <a href="#-live-demo">🎮 Demo</a> · -->
  <a href="#-quick-start">🚀 Quick Start</a> ·
  <a href="https://discord.gg/FwqHeFWk">💬 Discord</a>
</p>

<p align="center">
  <img src="assets/demo.gif" alt="Grimmory Demo" width="800" />
</p>

---

## ✨ Features

|     | Feature                | Description                                                                                                         |
| :-: | :--------------------- | :------------------------------------------------------------------------------------------------------------------ |
| 📚  | **Smart Shelves**      | Custom and dynamic shelves that organize themselves with rule-based Magic Shelves, filters, and full-text search    |
| 🔍  | **Automatic Metadata** | Covers, descriptions, reviews, and ratings pulled from Google Books, Open Library, and Amazon, all editable         |
| 📖  | **Built-in Reader**    | Open PDFs, EPUBs, and comics right in the browser with annotations, highlights, and reading progress                |
| 🔄  | **Device Sync**        | Connect your Kobo, use any OPDS-compatible app, or sync progress with KOReader. Your library follows you everywhere |
| 👥  | **Multi-User Ready**   | Individual shelves, progress, and preferences per user with local or OIDC authentication                            |
| 📥  | **BookDrop**           | Drop files into a watched folder and Grimmory detects, enriches, and queues them for import automatically           |
| 📧  | **One-Click Sharing**  | Send any book to a Kindle, an email address, or a friend instantly                                                  |

---

## 🚀 Quick Start

> [!TIP]
> Looking for OIDC setup, advanced config, or upgrade guides? See the [full documentation](https://grimmory.org/docs/getting-started).

All you need is [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).

<details>
<summary><strong>📦 Image Repositories</strong></summary>

| Registry                  | Image                             |
| ------------------------- | --------------------------------- |
| Docker Hub                | `grimmory/grimmory`               |
| GitHub Container Registry | `ghcr.io/grimmory-tools/grimmory` |

> Legacy images at `ghcr.io/adityachandelgit/grimmory-tools` remain available but won't receive updates.

</details>

### Step 1: Environment Configuration

Create a `.env` file:

```ini
# Application
APP_USER_ID=1000
APP_GROUP_ID=1000
TZ=Etc/UTC

# Database
DATABASE_URL=jdbc:mariadb://mariadb:3306/grimmory
DB_USER=grimmory
DB_PASSWORD=ChangeMe_GrimmoryApp_2025!

# Storage: LOCAL (default) or NETWORK (for NFS/SMB, disables file reorganization)
DISK_TYPE=LOCAL

# MariaDB
DB_USER_ID=1000
DB_GROUP_ID=1000
MYSQL_ROOT_PASSWORD=ChangeMe_MariaDBRoot_2025!
MYSQL_DATABASE=grimmory
```

### Step 2: Docker Compose

Create a `docker-compose.yml`:

```yaml
services:
  grimmory:
    image: grimmory/grimmory:latest
    # Alternative: ghcr.io/grimmory-tools/grimmory:latest
    container_name: grimmory
    environment:
      - USER_ID=${APP_USER_ID}
      - GROUP_ID=${APP_GROUP_ID}
      - TZ=${TZ}
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DB_USER}
      - DATABASE_PASSWORD=${DB_PASSWORD}
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - ./books:/books
      - ./bookdrop:/bookdrop
    healthcheck:
      test: wget -q -O - http://localhost:6060/api/v1/healthcheck
      interval: 60s
      retries: 5
      start_period: 60s
      timeout: 10s
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.5
    container_name: mariadb
    environment:
      - PUID=${DB_USER_ID}
      - PGID=${DB_GROUP_ID}
      - TZ=${TZ}
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=${MYSQL_DATABASE}
      - MYSQL_USER=${DB_USER}
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - ./mariadb/config:/config
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mariadb-admin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 5s
      retries: 10
```

### Step 3: Launch

```bash
docker compose up -d
```

Open **<http://localhost:6060>**, create your admin account, and start building your library.

---

## 📥 BookDrop: Zero-Effort Import

Drop book files into a folder. Grimmory picks them up, pulls metadata, and queues everything for your review.

```mermaid
graph LR
    A[📁 Drop Files] --> B[🔍 Auto-Detect]
    B --> C[📊 Extract Metadata]
    C --> D[✅ Review & Import]
```

| Step          | What Happens                                           |
| :------------ | :----------------------------------------------------- |
| 1. **Watch**  | Grimmory monitors the BookDrop folder around the clock |
| 2. **Detect** | New files are picked up and parsed automatically       |
| 3. **Enrich** | Metadata is fetched from Google Books and Open Library |
| 4. **Import** | You review, tweak if needed, and add to your library   |

Mount the volume in `docker-compose.yml`:

```yaml
volumes:
  - ./bookdrop:/bookdrop
```

---

## 🤝 Community & Support

|                               |                                                                                                         |
| :---------------------------- | :------------------------------------------------------------------------------------------------------ |
| 🐞 **Something not working?** | [Report a Bug](https://github.com/grimmory-tools/grimmory/issues/new?template=bug_report.yml)           |
| 💡 **Got an idea?**           | [Request a Feature](https://github.com/grimmory-tools/grimmory/issues/new?template=feature_request.yml) |
| 🛠️ **Want to help build?**    | [Contributing Guide](CONTRIBUTING.md)                                                                   |
| 💬 **Come hang out**          | [Discord Server](https://discord.gg/Ee5hd458Uz)                                                         |

> [!WARNING]
> **Before opening a PR:** Open an issue first and get maintainer approval. PRs without a linked issue, without screenshots/video proof, or without pasted test output will be closed. All code must follow project [backend](CONTRIBUTING.md#backend-conventions) and [frontend](CONTRIBUTING.md#frontend-conventions) conventions. AI-assisted contributions are welcome, but you must run, test, and understand every line you submit. See the [Contributing Guide](CONTRIBUTING.md) for full details.

---

## 💜 Support Grimmory

Grimmory is free, open source, and built with care. Here's how you can give back:

| Action                     | How                                                                                             |
| :------------------------- | :---------------------------------------------------------------------------------------------- |
| ⭐ **Star this repo**      | It's the simplest way to help others find Grimmory                                              |
| 💰 **Sponsor development** | [Open Collective](https://opencollective.com/grimmory) funds hosting, testing, and new features |
| 📢 **Tell someone**        | Share Grimmory with a friend, a subreddit, or your local book club                              |


---
<!-- 
## 🌍 Translations

Grimmory is used by readers around the world. Help make it accessible in your language on [Weblate](https://hosted.weblate.org/engage/grimmory/).

<a href="https://hosted.weblate.org/engage/grimmory/">
    <img src="https://hosted.weblate.org/widget/grimmory/multi-auto.svg?v=1" alt="Translation status" />
</a>
-->

---
<!--
## 📊 Project Analytics

![Repository Activity](https://repobeats.axiom.co/api/embed/44a04220bfc5136e7064181feb07d5bf0e59e27e.svg)
-->

### ⭐ Star History

<a href="https://www.star-history.com/#grimmory-tools/grimmory&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=grimmory-tools/grimmory&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=grimmory-tools/grimmory&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=grimmory-tools/grimmory&type=date&legend=top-left" width="600" />
 </picture>
</a>

---

## 👥 Contributors

[![Contributors](https://contrib.rocks/image?repo=grimmory-tools/grimmory)](https://github.com/grimmory-tools/grimmory/graphs/contributors)

Every contribution matters. [See how you can help →](CONTRIBUTING.md)
