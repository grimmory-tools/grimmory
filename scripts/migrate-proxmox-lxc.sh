#!/usr/bin/env bash
set -Eeuo pipefail
umask 022

# Run on the Proxmox host as root. See docs/migration-proxmox-lxc.md.
: "${SOURCE_SSH:?Set SOURCE_SSH to the Docker host SSH destination}"
: "${CTID:?Set CTID to an unused Proxmox container ID}"
: "${STORAGE:?Set STORAGE to the Proxmox root filesystem storage}"
: "${TEMPLATE:?Set TEMPLATE to a Debian 13 standard template volume}"

DISK_GB="${DISK_GB:-32}"
MEMORY_MB="${MEMORY_MB:-4096}"
CORES="${CORES:-2}"
BRIDGE="${BRIDGE:-vmbr0}"
IP_CONFIG="${IP_CONFIG:-dhcp}"
GRIMMORY_HOSTNAME="${GRIMMORY_HOSTNAME:-grimmory}"
RESUME_CT="${RESUME_CT:-0}"
NFS_MOUNT="${NFS_MOUNT:-/mnt/pve/Book}"
SOURCE_KEY="${SOURCE_KEY:-/root/.ssh/grimmory-migration}"
SOURCE_TAR_IMAGE="${SOURCE_TAR_IMAGE:-pgvector/pgvector:pg18}"
NFS_EXPORT="192.168.0.4:/volume1/Livres"
BACKUP_MOUNT="/mnt/pve/nas"
BACKUP_EXPORT="192.168.0.4:/volume1/PROXMOX"
BACKUP_ROOT="$BACKUP_MOUNT/dump"
SOURCE_DIR="/home/docker/grimmory"
SOURCE_STOP_REQUESTED=0
TARGET_STARTED=0
CT_CREATE_REQUESTED=0
BACKUP_DIR=""

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
note() { printf '%s\n' "$*"; }
source_ssh() {
  ssh -n -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 \
    -o IdentitiesOnly=yes -i "$SOURCE_KEY" "$SOURCE_SSH" "$@"
}
source_script() {
  ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 \
    -o IdentitiesOnly=yes -i "$SOURCE_KEY" "$SOURCE_SSH" 'bash -s'
}
on_exit() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    if (( SOURCE_STOP_REQUESTED && ! TARGET_STARTED )); then
      note "Migration failed before the target started; attempting to restart the source." >&2
      source_ssh docker start "$SOURCE_CONTAINER_ID" >/dev/null \
        || note "Could not restart the source; check it manually." >&2
    elif (( TARGET_STARTED )); then
      note "The target may have written to the NAS. Both installations require manual inspection; the source was not restarted." >&2
    fi
    if (( CT_CREATE_REQUESTED )); then
      note "The container was not removed; inspect it before retrying." >&2
    fi
    if [[ -n $BACKUP_DIR ]]; then
      note "Backup directory retained (possibly incomplete): $BACKUP_DIR" >&2
    fi
  fi
  exit "$status"
}
trap on_exit EXIT

[[ $EUID -eq 0 ]] || fail "Run this script as root on the Proxmox host."
[[ $SOURCE_SSH =~ ^[a-zA-Z0-9_-]+@[a-zA-Z0-9._-]+$ ]] || fail "SOURCE_SSH must be user@hostname or user@IPv4."
[[ $CTID =~ ^[0-9]+$ && $CTID -ge 100 ]] || fail "CTID must be a numeric Proxmox ID of at least 100."
[[ $DISK_GB =~ ^[0-9]+$ && $DISK_GB -ge 20 ]] || fail "DISK_GB must be at least 20 GiB; allow extra space for growth."
[[ $MEMORY_MB =~ ^[0-9]+$ && $CORES =~ ^[0-9]+$ ]] || fail "MEMORY_MB and CORES must be numeric."
[[ $STORAGE =~ ^[a-zA-Z0-9_.-]+$ && $BRIDGE =~ ^[a-zA-Z0-9_.-]+$ ]] || fail "Invalid storage or bridge name."
[[ $GRIMMORY_HOSTNAME =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || fail "Invalid container hostname."
[[ $RESUME_CT == 0 || $RESUME_CT == 1 ]] || fail "RESUME_CT must be 0 or 1."
[[ $NFS_MOUNT =~ ^/[a-zA-Z0-9_./-]+$ && $NFS_MOUNT != / && $NFS_MOUNT != *..* ]] || fail "Invalid NFS mount path."
[[ -r $SOURCE_KEY ]] || fail "The dedicated source SSH key is not readable."
[[ $SOURCE_TAR_IMAGE =~ ^[a-zA-Z0-9._:/@-]+$ ]] || fail "Invalid source tar image."
[[ $IP_CONFIG == dhcp || $IP_CONFIG =~ ^[0-9.]+/[0-9]+$ ]] || fail "IP_CONFIG must be dhcp or an IPv4 CIDR."
[[ $TEMPLATE == *debian-13-standard* ]] || fail "Use a Debian 13 standard Proxmox template."
for command in pct pvesm ssh python3 findmnt mountpoint openssl tar gzip sha256sum; do
  command -v "$command" >/dev/null || fail "Missing command: $command"
done
if [[ $RESUME_CT == 0 ]]; then
  [[ ! -e /etc/pve/lxc/$CTID.conf ]] || fail "Container $CTID already exists."
else
  [[ -e /etc/pve/lxc/$CTID.conf ]] || fail "The container to resume does not exist."
  pct config "$CTID" | python3 -c '
import sys
ctid, storage, hostname, mount, disk = sys.argv[1:]
config = dict(line.rstrip("\n").split(": ", 1) for line in sys.stdin if ": " in line)
checks = [
    config.get("hostname") == hostname,
    config.get("unprivileged", "0") == "0",
    "nesting=1" in config.get("features", "").split(","),
    config.get("mp0") == mount + ",mp=/books",
    config.get("rootfs") == f"{storage}:{ctid}/vm-{ctid}-disk-0.raw,size={disk}G",
    config.get("onboot", "0") == "0",
]
if not all(checks) or any(k.startswith("mp") and k != "mp0" for k in config):
    sys.exit("Existing container configuration does not match the migration target.")
' "$CTID" "$STORAGE" "$GRIMMORY_HOSTNAME" "$NFS_MOUNT" "$DISK_GB" \
    || fail "Cannot safely resume this container."
  [[ $(pct status "$CTID") == "status: running" ]] || fail "The container to resume must be running."
  pct exec "$CTID" -- sh -c '
    [ "$(stat -c %a /etc)" = 755 ] &&
    ! getent passwd grimmory >/dev/null &&
    [ ! -e /app/data ] && [ ! -e /bookdrop ] &&
    [ ! -e /opt/grimmory ] && [ ! -e /etc/grimmory ] &&
    [ ! -e /etc/systemd/system/grimmory.service ]
  ' || fail "The container is not in the expected initial preparation state."
fi
pvesm path "$TEMPLATE" >/dev/null || fail "The template is not available on this Proxmox host."
source_ssh 'docker info >/dev/null' || fail "SSH and Docker access to the source are required."
mountpoint -q "$NFS_MOUNT" || fail "The Proxmox NFS storage is not mounted at $NFS_MOUNT."
[[ $(findmnt -rn -o SOURCE,FSTYPE --mountpoint "$NFS_MOUNT") == "$NFS_EXPORT nfs4" ]] \
  || fail "A different filesystem is mounted at $NFS_MOUNT."
check_backup_mount() {
  mountpoint -q "$BACKUP_MOUNT" || fail "The backup NAS is not mounted at $BACKUP_MOUNT."
  [[ $(findmnt -rn -o SOURCE,FSTYPE --mountpoint "$BACKUP_MOUNT") == "$BACKUP_EXPORT nfs4" ]] \
    || fail "A different filesystem is mounted at $BACKUP_MOUNT."
  [[ -d $BACKUP_ROOT && ! -L $BACKUP_ROOT && -w $BACKUP_ROOT ]] \
    || fail "The backup directory is not a writable directory."
  [[ $(findmnt -rn -o TARGET --target "$BACKUP_ROOT") == "$BACKUP_MOUNT" ]] \
    || fail "The backup directory is not on the expected NAS mount."
}
check_backup_mount
SOURCE_TAR_IMAGE_ID=$(source_ssh docker image inspect --format='{{.Id}}' "$SOURCE_TAR_IMAGE")
[[ $SOURCE_TAR_IMAGE_ID =~ ^sha256:[0-9a-f]{64}$ ]] || fail "The local source tar image was not found."
tar_version=$(source_ssh docker run --rm --network none --user 0:0 --entrypoint tar "$SOURCE_TAR_IMAGE_ID" --version)
[[ $tar_version == "tar (GNU tar)"* ]] || fail "The source tar image must contain GNU tar."

# Refuse a changed source layout rather than silently omitting data.
source_ssh docker inspect grimmory | python3 -c '
import json, sys
c = json.load(sys.stdin)[0]
expected = {"/books": "/home/docker/grimmory/livres", "/app/data": "/home/docker/grimmory/data", "/bookdrop": "/home/docker/grimmory/bookdrop"}
actual = {m["Destination"]: m["Source"] for m in c["Mounts"] if m["Type"] == "bind"}
if actual != expected or len(c["Mounts"]) != len(expected) or not c["State"]["Running"]:
    sys.exit("Unexpected source mounts or the source container is not running.")
allowed = {"DATABASE_USERNAME", "DATABASE_PASSWORD", "DATABASE_URL", "API_DOCS_ENABLED",
           "DISK_TYPE", "USER_ID", "GROUP_ID", "TZ", "PATH", "JAVA_HOME", "LANG",
           "LANGUAGE", "LC_ALL", "JAVA_VERSION", "JAVA_TOOL_OPTIONS", "APP_VERSION", "APP_REVISION"}
unknown = {entry.split("=", 1)[0] for entry in c["Config"]["Env"]} - allowed
if unknown:
    sys.exit("Unexpected source environment variables require review: " + ", ".join(sorted(unknown)))
' || fail "Source inspection failed."
SOURCE_CONTAINER_ID=$(source_ssh docker inspect --format='{{.Id}}' grimmory)
DB_CONTAINER_ID=$(source_ssh docker inspect --format='{{.Id}}' grimmory-mariadb-1)
[[ $SOURCE_CONTAINER_ID =~ ^[0-9a-f]{64}$ && $DB_CONTAINER_ID =~ ^[0-9a-f]{64}$ ]] \
  || fail "Could not identify the source containers."
source_nfs=$(source_ssh findmnt -rn -o SOURCE,FSTYPE --mountpoint "$SOURCE_DIR/livres") \
  || fail "The source book directory is not a mount point."
[[ $source_nfs == "$NFS_EXPORT nfs4" ]] || fail "The source NFS export differs from $NFS_EXPORT."
[[ $(source_ssh uname -m) == "$(uname -m)" ]] \
  || fail "Source and Proxmox CPU architectures differ; the bundled kepubify cannot be reused."
case $(uname -m) in
  x86_64) JAVA_ARCH=x64 ;;
  aarch64) JAVA_ARCH=aarch64 ;;
  *) fail "Unsupported CPU architecture." ;;
esac
APP_UID=$(source_ssh docker exec grimmory printenv USER_ID)
APP_GID=$(source_ssh docker exec grimmory printenv GROUP_ID)
APP_TZ=$(source_ssh docker exec grimmory printenv TZ)
APP_VERSION=$(source_ssh docker exec grimmory printenv APP_VERSION)
APP_REVISION=$(source_ssh docker exec grimmory printenv APP_REVISION)
APP_JAVA_OPTIONS=$(source_ssh docker exec grimmory printenv JAVA_TOOL_OPTIONS)
APP_DB_URL=$(source_ssh docker exec grimmory printenv DATABASE_URL)
APP_DISK_TYPE=$(source_ssh docker exec grimmory printenv DISK_TYPE)
APP_API_DOCS=$(source_ssh docker exec grimmory printenv API_DOCS_ENABLED)
DB_NAME=$(source_ssh docker exec grimmory-mariadb-1 printenv MYSQL_DATABASE)
[[ $APP_UID == 1035 && $APP_GID == 101 ]] || fail "Source UID/GID changed; review NAS permissions before migrating."
[[ $DB_NAME =~ ^[a-zA-Z0-9_]+$ ]] || fail "Unsupported database name."
[[ $APP_DB_URL =~ ^jdbc:mariadb://mariadb(:3306)?/$DB_NAME(\?|$) ]] || fail "The application is not using the expected database."
unset APP_DB_URL
[[ $APP_DISK_TYPE == LOCAL && ( $APP_API_DOCS == true || $APP_API_DOCS == false ) ]] \
  || fail "Unexpected source disk type or API documentation setting."
[[ $APP_TZ =~ ^[a-zA-Z0-9_+/-]+$ && $APP_VERSION =~ ^[a-zA-Z0-9_.-]+$ && $APP_REVISION =~ ^[a-zA-Z0-9_.-]+$ ]] \
  || fail "Unsupported source environment values."
case $APP_JAVA_OPTIONS in
  *$'\n'*|*'"'*|*\\*) fail "Unsupported Java options format." ;;
esac

db_check=$(source_script <<'REMOTE'
set -e
docker exec grimmory-mariadb-1 sh -c '
  export MYSQL_PWD="$MYSQL_PASSWORD"
  mariadb -u "$MYSQL_USER" -N "$MYSQL_DATABASE" -e "SELECT (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='\''BASE TABLE'\'' AND ENGINE <> '\''InnoDB'\'') + (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()) + (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()) + (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE())"
  mariadb-dump -u "$MYSQL_USER" --single-transaction --no-data --routines --events --triggers --no-tablespaces "$MYSQL_DATABASE" > /dev/null
'
REMOTE
) || fail "The database cannot be backed up with the application account."
[[ $db_check == 0 ]] || fail "Non-InnoDB tables, triggers, routines or events need a separately reviewed migration."

if [[ $RESUME_CT == 1 ]]; then
  note "This resumes preparation of privileged LXC $CTID and reuses $NFS_MOUNT."
else
  note "This creates privileged LXC $CTID with nesting and reuses $NFS_MOUNT."
fi
note "It stops the source after preparation, shares the existing NAS books, and leaves the source stopped on success."
note "A private backup of the database, app data and bookdrop will be retained under $BACKUP_ROOT."
note "Stop external writers to the NAS, database, app data and bookdrop first. Books are not included in this backup."
read -r -p "Type MIGRATE to continue: " confirmation
[[ $confirmation == MIGRATE ]] || fail "Migration cancelled."

mountpoint -q "$NFS_MOUNT" || fail "The Proxmox NFS storage is no longer mounted."
[[ $(findmnt -rn -o SOURCE,FSTYPE --mountpoint "$NFS_MOUNT") == "$NFS_EXPORT nfs4" ]] \
  || fail "The Proxmox NFS storage changed."
check_backup_mount

CT_CREATE_REQUESTED=1
if [[ $RESUME_CT == 0 ]]; then
  pct create "$CTID" "$TEMPLATE" --hostname "$GRIMMORY_HOSTNAME" --unprivileged 0 --features nesting=1 \
    --rootfs "$STORAGE:$DISK_GB" --memory "$MEMORY_MB" --cores "$CORES" \
    --net0 "name=eth0,bridge=$BRIDGE,ip=$IP_CONFIG" \
    --mp0 "$NFS_MOUNT,mp=/books" --onboot 0
  pct start "$CTID"
fi
pct exec "$CTID" -- runuser -u nobody -- test -r /etc/resolv.conf \
  || fail "Unprivileged users cannot read the target DNS configuration."

note "Installing native MariaDB, Java 25 and runtime dependencies in the new LXC."
pct exec "$CTID" -- bash -s -- "$JAVA_ARCH" "$APP_UID" "$APP_GID" <<'TARGET'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get -o APT::Update::Error-Mode=any update
apt-get install -y --no-install-recommends ca-certificates curl python3 mariadb-server ffmpeg libarchive-tools
arch=$1
app_uid=$2
app_gid=$3
if ! getent group "$app_gid" >/dev/null; then groupadd -g "$app_gid" grimmory; fi
if getent passwd "$app_uid" >/dev/null || getent passwd grimmory >/dev/null; then
  echo "The application UID or username is already in use." >&2
  exit 1
fi
useradd --system --no-create-home --uid "$app_uid" --gid "$app_gid" --shell /usr/sbin/nologin grimmory
install -d -m 755 /opt/grimmory /opt/grimmory/java /app/data /bookdrop /usr/local/libexec
install -d -m 700 /etc/grimmory
api="https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=$arch&heap_size=normal&image_type=jre&os=linux&vendor=eclipse"
metadata=$(curl -fsSL "$api")
mapfile -t package < <(printf '%s' "$metadata" | python3 -c '
import json, sys
entries = json.load(sys.stdin)
if len(entries) != 1:
    sys.exit("Expected one Temurin Java 25 JRE package.")
p = entries[0]["binary"]["package"]
if not p["link"].startswith("https://github.com/adoptium/temurin25-binaries/releases/download/") or len(p["checksum"]) != 64:
    sys.exit("Unexpected Temurin package metadata.")
print(p["link"])
print(p["checksum"])
')
[[ ${#package[@]} == 2 ]] || { echo "Invalid Java package metadata." >&2; exit 1; }
archive=$(mktemp)
trap 'rm -f "$archive"' EXIT
curl -fsSL "${package[0]}" -o "$archive"
printf '%s  %s\n' "${package[1]}" "$archive" | sha256sum -c -
tar -xzf "$archive" -C /opt/grimmory/java --strip-components=1
/opt/grimmory/java/bin/java -version
systemctl enable --now mariadb
TARGET

source_ssh docker cp grimmory:/app/app.jar - \
  | pct exec "$CTID" -- tar -xf - -C /opt/grimmory
source_ssh docker cp grimmory:/usr/local/bin/kepubify - \
  | pct exec "$CTID" -- tar -xf - -C /usr/local/bin
source_jar=$(source_ssh docker exec grimmory sha256sum /app/app.jar)
target_jar=$(pct exec "$CTID" -- sha256sum /opt/grimmory/app.jar)
[[ ${source_jar%% *} == "${target_jar%% *}" ]] || fail "Copied application JAR checksum mismatch."
pct exec "$CTID" -- runuser -u grimmory -- test -w /books \
  || fail "UID $APP_UID cannot write to the NAS inside the LXC."

check_backup_mount
umask 077
BACKUP_DIR=$(mktemp -d "$BACKUP_ROOT/grimmory-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX") \
  || fail "Could not create the backup directory."
chmod 700 "$BACKUP_DIR"
[[ $(stat -c %a "$BACKUP_DIR") == 700 ]] \
  || fail "The NAS did not enforce private backup directory permissions."
note "Backup directory: $BACKUP_DIR"
note "Stopping the source application; MariaDB remains running for the consistent dump."
[[ $(source_ssh docker inspect --format='{{.Id}}' grimmory) == "$SOURCE_CONTAINER_ID" ]] \
  || fail "The source application container changed during preparation."
[[ $(source_ssh docker inspect --format='{{.Id}}' grimmory-mariadb-1) == "$DB_CONTAINER_ID" ]] \
  || fail "The source database container changed during preparation."
SOURCE_STOP_REQUESTED=1
source_ssh docker stop "$SOURCE_CONTAINER_ID" >/dev/null

source_counts=$(source_script <<'REMOTE'
set -e
docker exec grimmory-mariadb-1 sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mariadb -u "$MYSQL_USER" -N "$MYSQL_DATABASE" -e "SELECT (SELECT COUNT(*) FROM book), (SELECT COUNT(*) FROM author), (SELECT COUNT(*) FROM users)"'
REMOTE
)
for directory in data bookdrop; do
  source_ssh docker run --rm --network none --user 0:0 --entrypoint tar \
    --mount "type=bind,source=$SOURCE_DIR/$directory,target=/source,readonly" \
    "$SOURCE_TAR_IMAGE_ID" --numeric-owner --acls --xattrs -C /source -cpf - . \
    | gzip -c > "$BACKUP_DIR/$directory.tar.gz"
  gzip -t "$BACKUP_DIR/$directory.tar.gz"
  tar -tzf "$BACKUP_DIR/$directory.tar.gz" >/dev/null
done
source_script <<'REMOTE' | gzip -c > "$BACKUP_DIR/database.sql.gz"
set -e
docker exec grimmory-mariadb-1 sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mariadb-dump -u "$MYSQL_USER" --single-transaction --routines --events --triggers --no-tablespaces --databases "$MYSQL_DATABASE"'
REMOTE
[[ $(source_ssh docker inspect --format='{{.Id}}' grimmory-mariadb-1) == "$DB_CONTAINER_ID" ]] \
  || fail "The source database container changed during the dump."
gzip -t "$BACKUP_DIR/database.sql.gz"
printf '%s\n' "$source_counts" > "$BACKUP_DIR/source-counts.txt"
(cd "$BACKUP_DIR" && sha256sum data.tar.gz bookdrop.tar.gz database.sql.gz source-counts.txt > SHA256SUMS
  sha256sum -c SHA256SUMS)
chmod 600 "$BACKUP_DIR"/data.tar.gz "$BACKUP_DIR"/bookdrop.tar.gz \
  "$BACKUP_DIR"/database.sql.gz "$BACKUP_DIR"/source-counts.txt "$BACKUP_DIR"/SHA256SUMS
for backup_file in data.tar.gz bookdrop.tar.gz database.sql.gz source-counts.txt SHA256SUMS; do
  [[ $(stat -c %a "$BACKUP_DIR/$backup_file") == 600 ]] \
    || fail "The NAS did not enforce private permissions on $backup_file."
done
umask 022
note "Backup verified; restoring the target from $BACKUP_DIR."
for directory in data bookdrop; do
  target="/app/data"
  [[ $directory != bookdrop ]] || target="/bookdrop"
  gzip -dc "$BACKUP_DIR/$directory.tar.gz" \
    | pct exec "$CTID" -- tar --numeric-owner --acls --xattrs -C "$target" -xpf -
done
gzip -dc "$BACKUP_DIR/database.sql.gz" | pct exec "$CTID" -- mariadb
target_counts=$(pct exec "$CTID" -- mariadb -N "$DB_NAME" -e 'SELECT (SELECT COUNT(*) FROM book), (SELECT COUNT(*) FROM author), (SELECT COUNT(*) FROM users)')
[[ $source_counts == "$target_counts" ]] || fail "Database row counts do not match."

DB_PASSWORD=$(openssl rand -hex 32)
{
  printf 'CREATE USER '\''grimmory'\''@'\''localhost'\'' IDENTIFIED BY '\''%s'\'';\n' "$DB_PASSWORD"
  printf 'CREATE USER '\''grimmory'\''@'\''127.0.0.1'\'' IDENTIFIED BY '\''%s'\'';\n' "$DB_PASSWORD"
  printf 'GRANT ALL PRIVILEGES ON `%s`.* TO '\''grimmory'\''@'\''localhost'\'';\n' "$DB_NAME"
  printf 'GRANT ALL PRIVILEGES ON `%s`.* TO '\''grimmory'\''@'\''127.0.0.1'\'';\n' "$DB_NAME"
} | pct exec "$CTID" -- mariadb
{
  printf 'DATABASE_URL=jdbc:mariadb://127.0.0.1:3306/%s?createDatabaseIfNotExist=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true\n' "$DB_NAME"
  printf 'DATABASE_USERNAME=grimmory\nDATABASE_PASSWORD=%s\n' "$DB_PASSWORD"
  printf 'TZ=%s\nAPP_VERSION=%s\nAPP_REVISION=%s\n' "$APP_TZ" "$APP_VERSION" "$APP_REVISION"
  printf 'DISK_TYPE=%s\nAPI_DOCS_ENABLED=%s\n' "$APP_DISK_TYPE" "$APP_API_DOCS"
  printf 'JAVA_TOOL_OPTIONS="%s"\n' "$APP_JAVA_OPTIONS"
} | pct exec "$CTID" -- sh -c 'umask 077; cat > /etc/grimmory/environment'
unset DB_PASSWORD

pct exec "$CTID" -- bash -s -- "$APP_UID" "$APP_GID" <<'TARGET'
set -Eeuo pipefail
chown "$1:$2" /app/data /bookdrop
cat > /usr/local/libexec/grimmory-check-nfs <<'CHECK'
#!/bin/sh
set -eu
device=$(python3 -c 'import os; d = os.stat("/books").st_dev; print(f"{os.major(d)}:{os.minor(d)}")')
findmnt -rn -o MAJ:MIN,SOURCE,FSTYPE --mountpoint /books \
  | grep -Fqx "$device 192.168.0.4:/volume1/Livres nfs4" || {
  echo "The books NFS mount is unavailable; refusing to start." >&2
  exit 1
}
CHECK
chmod 755 /usr/local/libexec/grimmory-check-nfs /usr/local/bin/kepubify
cat > /etc/systemd/system/grimmory.service <<'SERVICE'
[Unit]
Description=Grimmory
Requires=mariadb.service
After=network-online.target mariadb.service
Wants=network-online.target

[Service]
Type=simple
User=grimmory
Environment=LANG=C.UTF-8 LC_ALL=C.UTF-8
EnvironmentFile=/etc/grimmory/environment
ExecStartPre=/usr/local/libexec/grimmory-check-nfs
ExecStart=/opt/grimmory/java/bin/java --enable-native-access=ALL-UNNAMED --enable-preview -XX:-UseBASE64Intrinsics -jar /opt/grimmory/app.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
SERVICE
systemctl daemon-reload
systemctl enable grimmory
TARGET

TARGET_STARTED=1
pct exec "$CTID" -- systemctl start grimmory
healthy=0
for (( attempt=0; attempt<60; attempt++ )); do
  if pct exec "$CTID" -- curl -fsS http://127.0.0.1:6060/api/v1/healthcheck \
    | python3 -c 'import json, sys; data=json.load(sys.stdin).get("data", {}); sys.exit(0 if data.get("status") == "UP" and data.get("version") == sys.argv[1] else 1)' "$APP_VERSION" 2>/dev/null; then
    healthy=1
    break
  fi
  sleep 5
done
(( healthy )) || fail "Target health check failed. Inspect systemctl status and journalctl in the LXC."
note "Migration completed. The source application remains stopped and MariaDB remains running."
note "Verified backup: $BACKUP_DIR"
note "Validate books, metadata, login and NAS write access before enabling container autostart or retiring the source."
note "Container addresses:"
pct exec "$CTID" -- hostname -I
