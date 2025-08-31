#!/usr/bin/env bash
# notify-discord.sh
# Sends a GitHub release notification to Discord
set -euo pipefail

# ===== CONFIGURE HERE =====
if [ -z "${1:-}" ]; then
  echo "ERROR: You must provide the release tag as the first argument."
  echo "Usage: $0 <release-tag> [discord-webhook-url]"
  exit 1
fi

NEW_TAG="$1"
DISCORD_WEBHOOK_URL="${2:-https://discord.com/api/webhooks/your_webhook_id/your_webhook_token}"

# Warn if using placeholder values
if [[ "$DISCORD_WEBHOOK_URL" == *"YOUR_WEBHOOK_ID"* ]] || [[ "$DISCORD_WEBHOOK_URL" == *"YOUR_WEBHOOK_TOKEN"* ]]; then
  echo "WARNING: DISCORD_WEBHOOK_URL contains placeholder values; payload will likely fail."
fi

echo "Preparing Discord notification for release $NEW_TAG"

# Fetch release data from GitHub
echo "Running gh release view..."
release_json=$(gh release view "$NEW_TAG" --json name,body,url)
echo "Release JSON fetched"

release_name=$(jq -r '.name' <<< "$release_json")
release_body=$(jq -r '.body' <<< "$release_json")
release_url=$(jq -r '.url'  <<< "$release_json")

dockerhub_image="https://hub.docker.com/r/booklore/booklore/tags/$NEW_TAG"
ghcr_image="https://github.com/booklore-app/booklore/pkgs/container/booklore/$NEW_TAG"

# Clean up body for Discord
clean_body=$(echo "$release_body" | tr -d '\r')
max_length=1800
if [ ${#clean_body} -gt $max_length ]; then
  clean_body="${clean_body:0:$((max_length-12))}… [truncated]"
fi

# Prepare Discord payload
payload=$(jq -n \
  --arg title "New Release: $release_name" \
  --arg url   "$release_url" \
  --arg desc  "$clean_body" \
  --arg hub   "[View image]($dockerhub_image)" \
  --arg gh    "[View image]($ghcr_image)" \
  '{
    content: null,
    embeds: [{
      title: $title,
      url: $url,
      description: $desc,
      color: 3066993,
      fields: [
        { name: "Docker Hub", value: $hub, inline: true },
        { name: "GHCR",       value: $gh,  inline: true }
      ]
    }]
  }')

# Debug: show payload
echo "=== Discord payload ==="
echo "$payload"
echo "======================="

# Send notification
echo "Sending notification to Discord..."
curl -i -H "Content-Type: application/json" -d "$payload" "$DISCORD_WEBHOOK_URL" \
  || echo "⚠️  Request completed with an error; check the above HTTP response"

echo "Notification sent!"