#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
    echo "Run this installer as root." >&2
    exit 1
fi

if [ "$#" -ne 3 ]; then
    echo "Usage: install-grimmory-syno-acl-helper.sh ALLOWED_ROOT SSH_USER HELPER_SOURCE" >&2
    exit 2
fi

allowed_root=$1
ssh_user=$2
helper_source=$3
case "$ssh_user" in
    ""|*[!A-Za-z0-9_-]*)
        echo "Invalid SSH user." >&2
        exit 2
        ;;
esac
case "$allowed_root" in
    /volume1/Grimmory-ACL-Test) ;;
    *)
        echo "Only the isolated ACL test share is allowed until validation is complete." >&2
        exit 2
        ;;
esac

resolved_root=$(realpath -e "$allowed_root")
if [ "$resolved_root" != "$allowed_root" ] || [ ! -d "$resolved_root" ]; then
    echo "Allowed root must be an existing canonical directory." >&2
    exit 2
fi
if [ ! -f "$helper_source" ] || [ -L "$helper_source" ]; then
    echo "Helper source must be a regular, non-symlink file." >&2
    exit 2
fi
visudo=$(command -v visudo 2>/dev/null || true)
sudo_bin=$(command -v sudo 2>/dev/null || true)
if [ -z "$sudo_bin" ] || [ ! -x "$sudo_bin" ] \
    || { [ -n "$visudo" ] && [ ! -x "$visudo" ]; }; then
    echo "A sudoers validator is required (visudo or sudo policy listing)." >&2
    exit 1
fi

helper=/usr/local/libexec/grimmory-syno-acl-helper.py
sudoers=/etc/sudoers.d/grimmory-syno-acl-helper
root_config=/etc/grimmory/synology-acl-root
if [ -e "$helper" ] || [ -e "$sudoers" ] || [ -e "$root_config" ]; then
    echo "Refusing to overwrite an existing helper installation." >&2
    exit 1
fi

install -d -o root -g root -m 0755 /usr/local/libexec /etc/grimmory /etc/sudoers.d
install -o root -g root -m 0755 "$helper_source" "$helper"
printf '%s\n' "$allowed_root" > "$root_config"
chown root:root "$root_config"
chmod 0644 "$root_config"
printf '%s ALL=(root) NOPASSWD: %s \"\"\n' "$ssh_user" "$helper" > "$sudoers"
chown root:root "$sudoers"
chmod 0440 "$sudoers"

validation_failed=0
if [ -n "$visudo" ] && ! "$visudo" -c; then
    validation_failed=1
fi
if [ "$validation_failed" -eq 0 ] \
    && ! "$sudo_bin" -n -l -U "$ssh_user" -u root "$helper"; then
    validation_failed=1
fi
if [ "$validation_failed" -ne 0 ]; then
    rm -f "$sudoers" "$root_config" "$helper"
    echo "Sudoers validation failed; helper files were removed." >&2
    exit 1
fi

echo "Installed restricted ACL helper for $ssh_user under $allowed_root."
echo "The active sudo policy grants only the helper command; add its forced-command SSH key next."
