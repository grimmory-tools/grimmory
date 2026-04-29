#!/bin/sh
set -eu

USER_ID="${USER_ID:-1000}"
GROUP_ID="${GROUP_ID:-1000}"
APP_USER="${APP_USER:-booklore}"

if getent group "$APP_USER" >/dev/null 2>&1; then
    existing_group_id="$(getent group "$APP_USER" | cut -d: -f3)"
    if [ "$existing_group_id" != "$GROUP_ID" ]; then
        echo "ERROR: APP_USER group '$APP_USER' already exists with GID $existing_group_id, expected $GROUP_ID." >&2
        exit 1
    fi
fi

# Create group and user if they don't exist
if ! getent group "$GROUP_ID" >/dev/null 2>&1; then
    addgroup -g "$GROUP_ID" -S "$APP_USER"
fi

if getent passwd "$APP_USER" >/dev/null 2>&1; then
    existing_user_id="$(getent passwd "$APP_USER" | cut -d: -f3)"
    if [ "$existing_user_id" != "$USER_ID" ]; then
        echo "ERROR: APP_USER '$APP_USER' already exists with UID $existing_user_id, expected $USER_ID." >&2
        exit 1
    fi
fi

if ! getent passwd "$USER_ID" >/dev/null 2>&1; then
    adduser -u "$USER_ID" -G "$(getent group "$GROUP_ID" | cut -d: -f1)" -S -D "$APP_USER"
fi

# Ensure data, bookdrop, and books directories exist and are writable by the target user
mkdir -p /app/data /bookdrop /books
chown "$USER_ID:$GROUP_ID" /app/data /bookdrop /books 2>/dev/null || true

start_caddy_proxy=false
if [ "${ENABLE_CADDY:-true}" = "true" ] && [ "$#" -gt 0 ] && [ "$1" = "java" ]; then
    start_caddy_proxy=true
fi

if [ "$start_caddy_proxy" = "true" ]; then
    public_port="${GRIMMORY_HTTP_PORT:-${BOOKLORE_PORT:-6060}}"
    backend_port="${GRIMMORY_BACKEND_PORT:-8080}"

    if [ "$backend_port" = "$public_port" ]; then
        echo "WARNING: GRIMMORY_HTTP_PORT and GRIMMORY_BACKEND_PORT both resolved to $public_port; shifting backend port to avoid collision." >&2
        if [ "$public_port" = "8080" ]; then
            backend_port="8081"
        else
            backend_port="8080"
        fi
    fi

    export GRIMMORY_HTTP_PORT="$public_port"
    export GRIMMORY_BACKEND_PORT="$backend_port"
    export XDG_CONFIG_HOME=/tmp/caddy/config
    export XDG_DATA_HOME=/tmp/caddy/data

    mkdir -p "$XDG_CONFIG_HOME" "$XDG_DATA_HOME"
    chown -R "$USER_ID:$GROUP_ID" /tmp/caddy 2>/dev/null || true

    echo "Starting Grimmory with Caddy proxy on :$public_port and backend on :$backend_port" >&2

    su-exec "$USER_ID:$GROUP_ID" caddy run --config /etc/caddy/Caddyfile --adapter caddyfile &
    caddy_pid=$!

    env BOOKLORE_PORT="$backend_port" su-exec "$USER_ID:$GROUP_ID" "$@" &
    app_pid=$!

    shutdown_children() {
        if kill -0 "$caddy_pid" 2>/dev/null; then
            kill -TERM "$caddy_pid" 2>/dev/null || true
        fi
        if kill -0 "$app_pid" 2>/dev/null; then
            kill -TERM "$app_pid" 2>/dev/null || true
        fi
    }

    trap 'shutdown_children' INT TERM

    while :; do
        if ! kill -0 "$caddy_pid" 2>/dev/null; then
            if wait "$caddy_pid"; then
                caddy_status=0
            else
                caddy_status=$?
            fi
            echo "ERROR: Caddy exited unexpectedly with status $caddy_status." >&2
            shutdown_children
            wait "$app_pid" 2>/dev/null || true
            exit "$caddy_status"
        fi

        if ! kill -0 "$app_pid" 2>/dev/null; then
            if wait "$app_pid"; then
                app_status=0
            else
                app_status=$?
            fi
            app_status="${app_status:-1}"
            shutdown_children
            wait "$caddy_pid" 2>/dev/null || true
            exit "$app_status"
        fi

        sleep 1
    done
fi

exec su-exec "$USER_ID:$GROUP_ID" "$@"
