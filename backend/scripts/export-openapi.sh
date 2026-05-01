#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <java-executable> <classpath> <output-file>" >&2
  exit 1
fi

java_executable="$1"
classpath="$2"
output_file="$3"
output_dir="$(dirname "$output_file")"
log_file="${OPENAPI_EXPORT_LOG_FILE:-$output_dir/export-openapi.log}"

mkdir -p "$output_dir"
: > "$log_file"

port="$(
  python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)"

endpoint="http://127.0.0.1:${port}/api/openapi.json"

"$java_executable" \
  --enable-native-access=ALL-UNNAMED \
  --enable-preview \
  -Dspring.profiles.active=openapi-export \
  -Dserver.port="$port" \
  -cp "$classpath" \
  org.booklore.BookloreApplication \
  >>"$log_file" 2>&1 &
app_pid=$!

cleanup() {
  if kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

for _ in $(seq 1 60); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "OpenAPI export application exited before serving ${endpoint}. See ${log_file}." >&2
    exit 1
  fi

  if curl --silent --show-error --fail --max-time 5 "$endpoint" -o "$output_file"; then
    if grep -q '"openapi"' "$output_file"; then
      exit 0
    fi
  fi

  sleep 1
done

echo "Timed out waiting for ${endpoint}. See ${log_file}." >&2
exit 1
