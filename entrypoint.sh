#!/bin/sh
set -e

USER_ID="${USER_ID:-1000}"
GROUP_ID="${GROUP_ID:-1000}"

# Create group and user if they don't exist
if ! getent group "$GROUP_ID" >/dev/null 2>&1; then
    addgroup -g "$GROUP_ID" -S booklore
fi
if ! getent passwd "$USER_ID" >/dev/null 2>&1; then
    adduser -u "$USER_ID" -G "$(getent group "$GROUP_ID" | cut -d: -f1)" -S -D booklore
fi

# ---------------------------------------------------------------------------
# Optional one-shot database migration for the opds_sort column.
#
# Set APPLY_OPDS_TABLE_MIGRATION=true in your environment to run:
#   ALTER TABLE magic_shelf ADD COLUMN opds_sort VARCHAR(20) NULL;
#
# This migration was removed from the Flyway scripts to maintain parity with
# the upstream repo. Enable it only if your database pre-dates the column.
# The statement is guarded by IF NOT EXISTS so it is safe to run repeatedly.
# ---------------------------------------------------------------------------
if [ "${APPLY_OPDS_TABLE_MIGRATION:-false}" = "true" ]; then
    echo "[entrypoint] APPLY_OPDS_TABLE_MIGRATION=true — running opds_sort migration..."

    # Derive connection details from individual env vars (preferred) or
    # fall back to parsing the JDBC DATABASE_URL.
    DB_HOST_VAL="${DATABASE_HOST:-${DB_HOST:-mariadb}}"
    DB_PORT_VAL="${DATABASE_PORT:-3306}"
    DB_NAME_VAL="${DATABASE_NAME:-booklore}"
    DB_USER_VAL="${DATABASE_USERNAME:-root}"
    DB_PASS_VAL="${DATABASE_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"

    # If DATABASE_URL is set, parse host/port/dbname from the JDBC URL.
    # Format: jdbc:mariadb://HOST:PORT/DBNAME?...
    if [ -n "${DATABASE_URL:-}" ]; then
        _url="${DATABASE_URL#jdbc:mariadb://}"          # strip jdbc:mariadb://
        _hostport="${_url%%/*}"                         # HOST:PORT
        _rest="${_url#*/}"                              # DBNAME?params
        DB_HOST_VAL="${_hostport%%:*}"
        _port_candidate="${_hostport##*:}"
        if [ "$_port_candidate" != "$_hostport" ]; then
            DB_PORT_VAL="$_port_candidate"
        fi
        DB_NAME_VAL="${_rest%%\?*}"
    fi

    SQL="ALTER TABLE magic_shelf ADD COLUMN IF NOT EXISTS opds_sort VARCHAR(20) NULL;"

    if command -v mariadb > /dev/null 2>&1; then
        _client="mariadb"
    elif command -v mysql > /dev/null 2>&1; then
        _client="mysql"
    else
        echo "[entrypoint] ERROR: neither 'mariadb' nor 'mysql' client found. Cannot run migration." >&2
        exit 1
    fi

    "$_client" \
        -h "$DB_HOST_VAL" \
        -P "$DB_PORT_VAL" \
        -u "$DB_USER_VAL" \
        --password="$DB_PASS_VAL" \
        "$DB_NAME_VAL" \
        -e "$SQL"

    echo "[entrypoint] opds_sort migration complete."
fi

exec su-exec "$USER_ID:$GROUP_ID" "$@"
