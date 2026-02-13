#!/usr/bin/env bash
#
# Weblate Setup Script for BookLore
#
# Creates the Weblate project and all 25 translation components via the API.
# Run once after creating your Hosted Weblate account.
#
# Prerequisites:
#   1. Get your API token from https://hosted.weblate.org/accounts/profile/#api
#   2. Ensure your GitHub repo (booklore-app/booklore) is public (required for Libre plan)
#
# Usage:
#   WEBLATE_TOKEN=your-api-token ./scripts/weblate-setup.sh
#

set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
WEBLATE_URL="${WEBLATE_URL:-https://hosted.weblate.org}"
API="${WEBLATE_URL}/api"
TOKEN="${WEBLATE_TOKEN:?Set WEBLATE_TOKEN to your Weblate API token}"

PROJECT_NAME="BookLore"
PROJECT_SLUG="booklore"
PROJECT_WEB="https://github.com/booklore-app/booklore"

REPO_URL="https://github.com/booklore-app/booklore.git"
REPO_BRANCH="develop"
FILE_BASE="booklore-ui/src/i18n"

SOURCE_LANG="en"

# All languages the project supports
LANGUAGES="en es de fr it nl pl pt ru"

# All 25 translation domain files (without .json extension)
COMPONENTS=(
  common
  auth
  nav
  dashboard
  settings
  settings-email
  settings-reader
  settings-view
  settings-metadata
  settings-library-metadata
  settings-application
  settings-users
  settings-naming
  settings-opds
  settings-tasks
  settings-auth
  settings-device
  settings-profile
  app
  shared
  layout
  library-creator
  bookdrop
  metadata
)

# ─── Helpers ──────────────────────────────────────────────────────────────────
auth_header="Authorization: Token ${TOKEN}"
content_type="Content-Type: application/json"

api_post() {
  local endpoint="$1"
  local data="$2"
  local response
  response=$(curl -s -w "\n%{http_code}" -X POST "${API}${endpoint}" \
    -H "${auth_header}" \
    -H "${content_type}" \
    -d "${data}")
  local http_code
  http_code=$(echo "$response" | tail -1)
  local body
  body=$(echo "$response" | sed '$d')

  if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
    echo "  OK (${http_code})"
    return 0
  elif [[ "$http_code" == "400" ]] && echo "$body" | grep -q "already exists"; then
    echo "  Already exists, skipping"
    return 0
  else
    echo "  FAILED (${http_code}): ${body}"
    return 1
  fi
}

# Pretty name from slug: settings-email -> Settings Email
pretty_name() {
  echo "$1" | sed 's/-/ /g' | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) tolower(substr($i,2))}1'
}

# ─── Step 1: Create Project ──────────────────────────────────────────────────
echo "=== Creating project: ${PROJECT_NAME} ==="
api_post "/projects/" "$(cat <<EOF
{
  "name": "${PROJECT_NAME}",
  "slug": "${PROJECT_SLUG}",
  "web": "${PROJECT_WEB}",
  "source_language": {
    "code": "${SOURCE_LANG}"
  }
}
EOF
)"

# ─── Step 2: Create Components ───────────────────────────────────────────────
echo ""
echo "=== Creating ${#COMPONENTS[@]} translation components ==="
echo ""

first=true
for comp in "${COMPONENTS[@]}"; do
  name=$(pretty_name "$comp")
  file_mask="${FILE_BASE}/*/${comp}.json"
  source_file="${FILE_BASE}/${SOURCE_LANG}/${comp}.json"

  echo "Creating component: ${name} (${comp})"

  # The first component links to the VCS repo directly.
  # Subsequent components use "repo" linking to share the same repo checkout.
  if $first; then
    api_post "/projects/${PROJECT_SLUG}/components/" "$(cat <<EOF
{
  "name": "${name}",
  "slug": "${comp}",
  "file_format": "json-nested",
  "filemask": "${file_mask}",
  "template": "${source_file}",
  "repo": "${REPO_URL}",
  "branch": "${REPO_BRANCH}",
  "push": "",
  "source_language": {
    "code": "${SOURCE_LANG}"
  },
  "new_lang": "none",
  "manage_units": false,
  "check_flags": "python-brace-format",
  "language_regex": "^(en|es|de|fr|it|nl|pl|pt|ru)$"
}
EOF
)"
    first=false
  else
    # Link to the first component's repo to save disk space and API calls
    api_post "/projects/${PROJECT_SLUG}/components/" "$(cat <<EOF
{
  "name": "${name}",
  "slug": "${comp}",
  "file_format": "json-nested",
  "filemask": "${file_mask}",
  "template": "${source_file}",
  "repo": "weblate://${PROJECT_SLUG}/common",
  "source_language": {
    "code": "${SOURCE_LANG}"
  },
  "new_lang": "none",
  "manage_units": false,
  "check_flags": "python-brace-format",
  "language_regex": "^(en|es|de|fr|it|nl|pl|pt|ru)$"
}
EOF
)"
  fi
done

echo ""
echo "=== Setup complete ==="
echo ""
echo "Next steps:"
echo "  1. Visit ${WEBLATE_URL}/projects/${PROJECT_SLUG}/ to verify"
echo "  2. If you want Weblate to push translations back:"
echo "     - Add a deploy key with write access to your GitHub repo"
echo "     - Update the 'common' component push URL to: ${REPO_URL}"
echo "     - Set push branch to: weblate-translations"
echo "  3. Alternatively, enable the GitHub PR add-on for automatic PRs"
echo "  4. Share the translation URL with contributors!"
echo ""
