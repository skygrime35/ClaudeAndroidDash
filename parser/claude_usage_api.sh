#!/usr/bin/env bash
# claude_usage_api.sh — fetch account-wide Claude usage straight from the
# Anthropic API and write the widget JSON, with NO live Claude Code session.
#
# The 5h / 7d limits are account-scoped: a 1-token request returns them in the
# anthropic-ratelimit-unified-* response headers, identical to what the status
# bar shows on any device. This is the fallback for when the user runs Claude
# elsewhere and only the widget is used on this phone.
#
# Runs inside the PRoot distro (where ~/.claude/.credentials.json lives).
set -u

CREDS="${CLAUDE_CREDS:-$HOME/.claude/.credentials.json}"
OUT="${CD_OUT:-/sdcard/Download/claude_usage.json}"
TMP="${OUT}.tmp"
MODEL="claude-haiku-4-5-20251001"
CLIENT_ID="9d1c250a-e61b-44d9-88ed-5944d1962f5e"
TOKEN_URL="https://api.anthropic.com/v1/oauth/token"
API_URL="https://api.anthropic.com/v1/messages"

now_ms() { echo $(( $(date +%s) * 1000 )); }
read_cred() { jq -r ".claudeAiOauth.$1 // empty" "$CREDS" 2>/dev/null; }

# Exchange the refresh token for a fresh access token and persist atomically,
# preserving every other field in the credentials file.
refresh_token() {
  local rt resp at rtok exp expat
  rt=$(read_cred refreshToken)
  [ -z "$rt" ] && return 1
  resp=$(curl -sS -X POST "$TOKEN_URL" -H 'content-type: application/json' \
    -d "{\"grant_type\":\"refresh_token\",\"refresh_token\":\"$rt\",\"client_id\":\"$CLIENT_ID\"}" \
    --max-time 30)
  echo "$resp" | jq -e '.access_token' >/dev/null 2>&1 || { echo "refresh failed: $resp" >&2; return 1; }
  at=$(echo "$resp" | jq -r '.access_token')
  rtok=$(echo "$resp" | jq -r '.refresh_token // empty')
  exp=$(echo "$resp" | jq -r '.expires_in // 28800')
  expat=$(( $(now_ms) + exp * 1000 ))
  jq --arg at "$at" --arg rt "$rtok" --argjson ea "$expat" '
    .claudeAiOauth.accessToken = $at
    | (if $rt != "" then .claudeAiOauth.refreshToken = $rt else . end)
    | .claudeAiOauth.expiresAt = $ea' "$CREDS" > "${CREDS}.tmp" \
    && mv -f "${CREDS}.tmp" "$CREDS"
}

# Refresh proactively if the access token is expired (60s safety buffer).
EXPAT=$(read_cred expiresAt)
if [ -n "$EXPAT" ] && [ "$(now_ms)" -ge "$(( EXPAT - 60000 ))" ]; then
  refresh_token || true
fi

TOK=$(read_cred accessToken)
[ -z "$TOK" ] && { echo "no access token in $CREDS" >&2; exit 1; }

fetch() {
  curl -sS -D "$1" -o /dev/null -X POST "$API_URL" \
    -H "authorization: Bearer $TOK" \
    -H "anthropic-version: 2023-06-01" \
    -H "anthropic-beta: oauth-2025-04-20" \
    -H "content-type: application/json" \
    -d "{\"model\":\"$MODEL\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" \
    --max-time 30 -w '%{http_code}'
}

HDR=$(mktemp)
CODE=$(fetch "$HDR")
if [ "$CODE" = "401" ]; then
  refresh_token && TOK=$(read_cred accessToken) && CODE=$(fetch "$HDR")
fi
[ "$CODE" = "200" ] || { echo "api http $CODE" >&2; rm -f "$HDR"; exit 1; }

hdr() { grep -i "^$1:" "$HDR" | sed "s/^[^:]*: *//I" | tr -d '\r' | head -1; }
FH_U=$(hdr 'anthropic-ratelimit-unified-5h-utilization')
FH_R=$(hdr 'anthropic-ratelimit-unified-5h-reset')
SD_U=$(hdr 'anthropic-ratelimit-unified-7d-utilization')
SD_R=$(hdr 'anthropic-ratelimit-unified-7d-reset')
rm -f "$HDR"

pct() { awk -v u="${1:-0}" 'BEGIN{ printf("%d", (u * 100) + 0.5) }'; }
NOW_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)

jq -n --arg ua "$NOW_ISO" \
  --argjson fhp "$(pct "$FH_U")" --argjson fhr "${FH_R:-0}" \
  --argjson sdp "$(pct "$SD_U")" --argjson sdr "${SD_R:-0}" \
  '{updated_at: $ua, source: "api",
    five_hour: {used_pct: $fhp, resets_at: $fhr},
    seven_day: {used_pct: $sdp, resets_at: $sdr}}' > "$TMP" \
  && mv -f "$TMP" "$OUT"
echo "wrote $OUT"
