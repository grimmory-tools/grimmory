> [!NOTE]
> This project is in early development. Some links (website, documentation, demo, repositories) are not yet active and will come online as infrastructure is set up.

> [!NOTE]
> Grimmory is the successor of booklore.


Grimmory is a self-hosted application for managing your entire book collection in one place.
Organize, read, annotate, sync across devices, and share without relying on third-party services.

[![Release](https://img.shields.io/github/v/release/grimmory-tools/grimmory?color=818CF8&style=flat-square&logo=github)](https://github.com/grimmory-tools/grimmory/releases)
[![License](https://img.shields.io/github/license/grimmory-tools/grimmory?color=fab005&style=flat-square)](LICENSE)
[![Docker Pulls](https://img.shields.io/docker/pulls/grimmory/grimmory?color=2496ED&style=flat-square&logo=docker&logoColor=white)](https://hub.docker.com/r/grimmory/grimmory)
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/9YJ7HB4n8T)


[Website](https://grimmory.org/) | [Documentation](https://grimmory.org/docs/getting-started) | [Quick Start](#quick-start) | [Discord](https://discord.gg/9YJ7HB4n8T)

![Grimmory Demo](assets/demo.gif)

---

## Features

| Feature | Description |
| :--- | :--- |
| **Smart Shelves** | Custom and dynamic shelves with rule-based filtering, tagging, and full-text search |
| **Metadata Lookup** | Covers, descriptions, reviews, and ratings pulled from Google Books, Open Library, and Amazon, all editable |
| **Built-in Reader** | Read PDFs, EPUBs, and comics in the browser with annotations, highlights, and reading progress tracking |
| **Device Sync** | Connect a Kobo, use any OPDS-compatible app, or sync progress with KOReader |
| **Multi-User** | Separate shelves, progress, and preferences per user with local or OIDC authentication |
| **BookDrop** | Drop files into a watched folder and Grimmory detects, enriches, and queues them for import automatically |
| **One-Click Sharing** | Send any book to a Kindle, an email address, or another user directly from the interface |

### Supported Formats

| Category | Formats |
| :--- | :--- |
| eBooks | EPUB, MOBI, AZW, AZW3, FB2 |
| Documents | PDF |
| Comics | CBZ, CBR, CB7 |
| Audiobooks | M4B, M4A, MP3, OPUS |

---

## Quick Start

> [!TIP]
> For OIDC setup, advanced configuration, or upgrade guides, see the [full documentation](https://grimmory.org/docs/getting-started).

Requirements: [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/).

<details>
<summary><strong>Image Repositories</strong></summary>

| Registry | Image |
| --- | --- |
| Docker Hub | `grimmory/grimmory` |
| GitHub Container Registry | `ghcr.io/grimmory-tools/grimmory` |

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
DB_PASSWORD=ChangeMe_Grimmory_2025!

# Storage: LOCAL (default) or NETWORK (disables file operations; see Network Storage section)
DISK_TYPE=LOCAL

# MariaDB
DB_USER_ID=1000
DB_GROUP_ID=1000
MYSQL_ROOT_PASSWORD=ChangeMe_MariaDBRoot_2025!
MYSQL_DATABASE=grimmory
```

### Step 2: Docker Compose

Stable images are published from semantic-release tags on `main` as `vX.Y.Z` plus `latest`. Nightly images are built from `develop` and tagged `nightly`.

> [!NOTE]
> Upgrading from an existing Booklore container? Keep your current service name, `container_name`, database name/user, ports, and mounted volumes exactly as they are and replace only the `image:` line with `grimmory/grimmory:<tag>` or `ghcr.io/grimmory-tools/grimmory:<tag>`.

```yaml
services:
  booklore:
    image: grimmory/grimmory:v2.2.1
```

Create a `docker-compose.yml` or copy and adapt [`deploy/compose/docker-compose.yml`](deploy/compose/docker-compose.yml):

```yaml
services:
  grimmory:
    image: grimmory/grimmory:latest
    # Convenience tag:
    # image: grimmory/grimmory:<release-version>
    # Alternative: ghcr.io/grimmory-tools/grimmory:<release-version>
    # To build from source instead: comment out 'image' and uncomment below
    # build: .
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

Open http://localhost:6060, create your admin account, and start building your library.

Additional deployment examples:

- Docker Compose: [`deploy/compose/docker-compose.yml`](deploy/compose/docker-compose.yml)
- Helm: [`deploy/helm/grimmory/Chart.yaml`](deploy/helm/grimmory/Chart.yaml)
- Podman Quadlet: [`deploy/podman/quadlet/README.md`](deploy/podman/quadlet/README.md)

---

## Developer Surfaces

This repository contains two application projects:

- Backend: [`booklore-api/README.md`](booklore-api/README.md)
- Frontend: [`booklore-ui/README.md`](booklore-ui/README.md)

Contributor workflow, PR policy, and release semantics live in [CONTRIBUTING.md](CONTRIBUTING.md). Component-specific implementation guidance lives in:

- [`booklore-api/CONTRIBUTING.md`](booklore-api/CONTRIBUTING.md)
- [`booklore-ui/CONTRIBUTING.md`](booklore-ui/CONTRIBUTING.md)

The root [`Justfile`](Justfile) is the primary local command surface and mirrors the folder-local `booklore-api/Justfile` and `booklore-ui/Justfile` entrypoints.

```bash
just               # Show root + api + ui recipes
just test          # Run backend and frontend tests
just api test      # Run backend tests only
just ui dev        # Start the frontend dev server
```

---

## BookDrop

Drop book files into a watched folder. Grimmory picks them up, pulls metadata, and queues them for your review.

```mermaid
graph LR
    A[Drop Files] --> B[Auto-Detect]
    B --> C[Extract Metadata]
    C --> D[Review and Import]
```

| Step | What Happens |
| --- | --- |
| 1. Watch | Grimmory monitors the BookDrop folder continuously |
| 2. Detect | New files are picked up and parsed automatically |
| 3. Enrich | Metadata is fetched from Google Books and Open Library |
| 4. Import | You review, adjust if needed, and add to your library |

Mount the volume in `docker-compose.yml`:

```yaml
volumes:
  - ./bookdrop:/bookdrop
```

---

## Network Storage

Set `DISK_TYPE=NETWORK` in your `.env` to run Grimmory against a network-mounted file system (NFS, SMB, etc.).
In this mode, direct file operations (delete, move, rename from the UI) are disabled to avoid destructive changes on shared mounts.
All other features — reading, metadata, sync — remain fully functional.

---

## Community and Support

| Channel | |
| :--- | :--- |
| Report a bug | [Open an issue](https://github.com/grimmory-tools/grimmory/issues/new?template=bug_report.yml) |
| Request a feature | [Open an issue](https://github.com/grimmory-tools/grimmory/issues/new?template=feature_request.yml) |
| Contribute | [Contributing Guide](CONTRIBUTING.md) |
| Join the discussion | [Discord Server](https://discord.gg/9YJ7HB4n8T) |

> [!WARNING]
> Before opening a pull request, open an issue first and get maintainer approval. Pull requests without a linked issue, without screenshots or video proof, or without pasted test output will be closed. All code must follow the project [backend](CONTRIBUTING.md#backend-conventions) and [frontend](CONTRIBUTING.md#frontend-conventions) conventions. AI-assisted contributions are welcome, but you must run, test, and understand every line you submit. See the [Contributing Guide](CONTRIBUTING.md) for full details.

--- 

## License

Distributed under the terms of the [LICENSE](LICENSE) file in this repository.
