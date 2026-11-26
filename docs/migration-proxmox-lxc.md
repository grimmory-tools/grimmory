# Migration to a native Proxmox LXC

`scripts/migrate-proxmox-lxc.sh` is tailored to the current Grimmory Docker
installation. It creates a **privileged Debian 13 LXC with nesting enabled**, installs MariaDB and
Java natively, and copies the running application's JAR, database, app data
and bookdrop. It first saves and verifies these data on the backup NAS, then
restores the target from those same archives. It binds the existing Proxmox NFS mount to `/books`; books are
**not copied**. The target uses the same UID 1035 and GID 101 as the source
for NAS access. Privileged containers with nesting provide less isolation than
unprivileged containers.

On the current Proxmox server, test LXC 106 booted with nesting enabled.
The copied application JAR started against a new, empty MariaDB database and
applied 150 migrations. The NAS was also mounted and tested read-only, then
detached. The complete migration of production data has not been executed or
validated end-to-end. The migration backup does not include books; keep a
separate, restorable backup of the NAS library. Stop other writers to the
database, bookdrop, app data and NFS share for the entire migration.

## Prerequisites

- Run as root on the Proxmox host, with `pct`, `pvesm`, Python 3 and OpenSSL
  installed. The dedicated key `/root/.ssh/grimmory-migration` has been
  authorized for `docker@192.168.0.8`, and the source host key verified. Docker
  access is effectively root access to the source host; protect this key.
- The source must retain its local `pgvector/pgvector:pg18` image with GNU tar.
  The script uses it in temporary, network-isolated containers with read-only
  mounts to preserve file ownership, ACLs and extended attributes.
- The source must have the current `grimmory` and `grimmory-mariadb-1`
  containers and their current bind mounts. The books mount must be
  `192.168.0.4:/volume1/Livres` over NFSv4. The existing Proxmox storage
  `Book` must be active at `/mnt/pve/Book`. The NAS must allow the Proxmox
  host and UID/GID to read and write the export.
- Download a Debian 13 standard template to Proxmox beforehand. The LXC needs
  Internet access for Debian packages and the verified Eclipse Temurin 25 JRE.
  The source and target CPU architectures must match.
- Provide enough container storage for app data, MariaDB and future growth.
  The current app data alone is approximately 5.6 GiB; the default root disk
  is 32 GiB. NAS books are stored separately.
- The NFSv4 export `192.168.0.4:/volume1/PROXMOX` must be mounted at
  `/mnt/pve/nas`, with a writable `/mnt/pve/nas/dump` directory and sufficient
  space for the compressed app data, bookdrop and database backup.

Example, executed on Proxmox after copying the script there:

```bash
SOURCE_SSH=docker@192.168.0.8 \
CTID=120 \
STORAGE=nas \
TEMPLATE=local:vztmpl/debian-13-standard_13.6-1_amd64.tar.zst \
DISK_GB=32 \
bash migrate-proxmox-lxc.sh
```

Replace the example ID, storage and template with values from the Proxmox
server. Optional variables are `MEMORY_MB` (4096), `CORES` (2), `BRIDGE`
(`vmbr0`), `IP_CONFIG` (`dhcp` or an IPv4 CIDR), `GRIMMORY_HOSTNAME` (`grimmory`),
`SOURCE_KEY` (`/root/.ssh/grimmory-migration`), `SOURCE_TAR_IMAGE`
(`pgvector/pgvector:pg18`) and `NFS_MOUNT` (`/mnt/pve/Book`). The current
`local-lvm` storage does not have enough free space for a 32 GiB root disk.
For a static IP, configure a gateway inside the container if needed. The script
asks for explicit confirmation before modifying Proxmox or stopping the source.

The host NFS mount is managed by Proxmox; the script does not add an
`/etc/fstab` entry. The LXC service refuses to start unless `/books` is an NFSv4
mount of the expected export, checking the effective filesystem when mounts are
stacked. This prevents an empty local directory from being mistaken for the library
when the NAS is unavailable. The container is created with Proxmox autostart
disabled; enable it only after verifying the mount and startup behavior.
The application service uses `C.UTF-8` so Java can access book filenames
containing accented characters even if no additional locale is installed.

## Completion and recovery

After stopping the source application, the script creates a private, uniquely
named directory under `/mnt/pve/nas/dump`. It verifies the compressed archives
and their SHA-256 checksums before restoring them to the new container. The
directory is retained on success or failure; after a failure, it may contain
incomplete files. Check `SHA256SUMS` and test a restore before relying on it.
This backup excludes the NAS books and does not replace a separate library backup.

The script compares source and target book, author and user counts and checks
the target health endpoint. Verify the application, logins, metadata, library
paths and actual NAS write access before directing users to the LXC. The source
application is left stopped; its MariaDB container is not stopped or removed.
Do not run both applications against the shared NAS: their databases would
diverge.

If failure occurs before the target application starts, the script attempts to
restart the source. Once target startup has been attempted, it leaves the
source stopped: the target might already have changed shared files. Inspect
the LXC and NAS before deciding how to recover. The script does not delete the
LXC on failure. Never retry with the same container ID without inspecting the
partial installation first.

If package installation failed before the application user or any application
directories were created, `RESUME_CT=1` can resume preparation of that running
container. The script checks its expected hostname, privileged/nesting
settings, books mount, 32 GiB raw NAS disk and empty application state; it
refuses other configurations. Correct the failure first and use the same
container ID and settings. This option is not for resuming after data transfer.
