#!/bin/sh
# ci-trigger.sh — discover NEW commits on the Gitea mirror and create exactly ONE pipeline
# This replaces blind every-minute cron: the trigger only fires when the mirror HEAD
# has moved to a commit that has not yet been triggered.
# Env: WOODPECKER_URL, WOODPECKER_TOKEN, GITEA_URL, GITEA_TOKEN (or GITEA_MIRROR_URL for git ls-remote)
#      STATE_DIR (persistent state dir), FLOOD_LIMIT (default 10)
# Usage: ci-trigger.sh <owner/repo>
# Output: CI_TRIGGER=NEW_COMMIT_PIPELINE_CREATED|NO_NEW_COMMIT|SKIPPED_FLOOD|SKIPPED_LOCKED|ERROR
set -u
REPO="${1:-}"
if [ -z "$REPO" ]; then echo "usage: ci-trigger.sh <owner/repo>"; exit 2; fi
URL="$WOODPECKER_URL"
if [ -z "$URL" ]; then URL="http://192.168.5.16:8010"; fi
TOK="$WOODPECKER_TOKEN"
if [ -z "$TOK" ]; then echo "CI_TRIGGER=ERROR reason=no-token"; exit 1; fi
GITEA="$GITEA_URL"
if [ -z "$GITEA" ]; then GITEA="http://192.168.5.35:3000"; fi
GT="$GITEA_TOKEN"
STATE_DIR="${STATE_DIR:-/var/lib/ci-trigger}"
FLOOD_LIMIT="${FLOOD_LIMIT:-10}"
mkdir -p "$STATE_DIR" 2>/dev/null || true
STATE_NAME=$(echo "$REPO" | tr '/' '_')
STATE_FILE="$STATE_DIR/$STATE_NAME.sha"
LOCK="$STATE_DIR/$STATE_NAME.lock"

# --- 1. lock ---
if ! flock -n "$LOCK" true 2>/dev/null; then echo "CI_TRIGGER=SKIPPED_LOCKED"; exit 0; fi

# --- 2. current mirror HEAD (Gitea API) ---
HEAD_SHA=$(curl -sf -H "Authorization: token $GT" "$GITEA/api/v1/repos/$REPO/git/refs/heads/main" --max-time 10 2>/dev/null \
  | grep -oE '"sha":"[0-9a-f]{40}"' | head -1 | cut -d\" -f4)
if [ -z "$HEAD_SHA" ]; then echo "CI_TRIGGER=ERROR reason=mirror-head-unreadable"; exit 1; fi

# --- 3. last triggered SHA ---
LAST=$(cat "$STATE_FILE" 2>/dev/null || echo "")
if [ "$LAST" = "$HEAD_SHA" ]; then echo "CI_TRIGGER=NO_NEW_COMMIT sha=$HEAD_SHA"; exit 0; fi

# --- 4. flood guard (pending count for this repo) ---
RID=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos?limit=100" --max-time 10 2>/dev/null \
  | sed 's/},{/}\n{/g' | grep "\"full_name\":\"$REPO\"" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
if [ -z "$RID" ]; then echo "CI_TRIGGER=ERROR reason=repo-not-found"; exit 1; fi
P=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID/pipelines?limit=100" --max-time 10 2>/dev/null \
  | sed 's/},{/}\n{/g' | grep -c '"status":"pending"' 2>/dev/null)
P=$(echo "$P" | grep -oE "[0-9]+" | head -1); [ -z "$P" ] && P=0
if [ "$P" -gt "$FLOOD_LIMIT" ] 2>/dev/null; then
  echo "CI_TRIGGER=SKIPPED_FLOOD pending=$P limit=$FLOOD_LIMIT"; exit 0
fi

# --- 5. disk guard ---
DF=$(df -P /volume1 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}' 2>/dev/null || echo 0)
if [ "${DF:-0}" -ge 95 ] 2>/dev/null; then echo "CI_TRIGGER=SKIPPED_DISK used=${DF}%"; exit 0; fi

# --- 6. create exactly one pipeline ---
CREATED=$(curl -sf -H "Authorization: Bearer $TOK" -H "content-type: application/json" \
  -X POST "$URL/api/repos/$RID/pipelines" -d '{"branch":"main"}' --max-time 15 2>/dev/null \
  | grep -oE '"number":[0-9]+' | head -1 | cut -d: -f2)
if [ -z "$CREATED" ]; then echo "CI_TRIGGER=ERROR reason=pipeline-create-failed"; exit 1; fi
echo "$HEAD_SHA" > "$STATE_FILE"
echo "CI_TRIGGER=NEW_COMMIT_PIPELINE_CREATED sha=$HEAD_SHA pipeline=$CREATED"
exit 0