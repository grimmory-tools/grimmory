# Deploying The OPF Branch On Raspberry Pi

This guide uses the OPF-enabled branch:

- Repository: `https://github.com/captainboto/grimmory.git`
- Branch: `codex/opf-support`

It is written for a Raspberry Pi running Grimmory with Docker Compose.

## First Deploy On Pi

Clone the fork and switch to the OPF branch:

```bash
git clone -b codex/opf-support https://github.com/captainboto/grimmory.git
cd grimmory
```

Create a `.env` file if you do not already have one:

```ini
APP_USER_ID=1000
APP_GROUP_ID=1000
TZ=Asia/Bangkok

DATABASE_URL=jdbc:mariadb://mariadb:3306/grimmory
DB_USER=grimmory
DB_PASSWORD=change_me
API_DOCS_ENABLED=false

# Keep NETWORK if your books are on NAS / SMB / NFS
DISK_TYPE=NETWORK

DB_USER_ID=1000
DB_GROUP_ID=1000
MYSQL_ROOT_PASSWORD=change_me_root
MYSQL_DATABASE=grimmory
```

Create a `docker-compose.yml` next to the repo, or adapt your existing one.
The important part is to build from source instead of using the published image:

```yaml
services:
  grimmory:
    build: .
    container_name: grimmory
    environment:
      - USER_ID=${APP_USER_ID}
      - GROUP_ID=${APP_GROUP_ID}
      - TZ=${TZ}
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DB_USER}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - API_DOCS_ENABLED=${API_DOCS_ENABLED}
      - DISK_TYPE=${DISK_TYPE}
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - /media/QNAP_Books/Books:/books
      - ./bookdrop:/bookdrop
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.8
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

Build and start:

```bash
docker compose build
docker compose up -d
```

Watch logs:

```bash
docker compose logs -f grimmory
```

Then open:

```text
http://<your-pi-ip>:6060
```

## Updating Later

When upstream Grimmory changes and you want to keep your OPF branch updated:

```bash
cd grimmory
git fetch upstream
git switch develop
git merge --ff-only upstream/develop
git switch codex/opf-support
git merge develop
git push
```

Then rebuild on the Pi:

```bash
docker compose build
docker compose up -d
```

## Updating Only From Your Fork

If you already merged upstream changes on another machine and pushed them to your fork,
the Pi can update with just:

```bash
cd grimmory
git fetch origin
git switch codex/opf-support
git pull
docker compose build
docker compose up -d
```

## Notes

- Keep `DISK_TYPE=NETWORK` if your library path is a NAS mount.
- This branch reads adjacent OPF files during scan. You do not need sidecar import for OPF metadata.
- If Grimmory was previously running from `image: grimmory/grimmory:...`, replace that with `build: .` or the Pi will keep using the old release image.
