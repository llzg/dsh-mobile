#!/bin/sh
# ci-trigger-guard.sh — platform trigger governance (idempotency + lock + flood guard + disk guard)
# Usage: ci-trigger-guard.sh [--repo <owner/name>] [--commit <sha>] [--pipeline-name <name>]
#        [--self-pipeline <number>] [--lockfile <path>] [--flood-limit <n>] [--disk <mount>]
#        [--in-pipeline]  (touch .ci-skip when blocked/already-built; exit 0)
# Env: WOODPECKER_URL, WOODPECKER_TOKEN (required)
# Output: GUARD_STATUS=TRIGGERED|SKIPPED_ALREADY_EXISTS|SKIPPED_RUNNING|FLOOD_GUARD_ACTIVE|
#         DISK_GUARD_ACTIVE|LOCKED|ERROR   (+ machine-readable details)

set -u
URL="${WOODPECKER_URL:-http://192.168.5.16:8010}"
TOK="${WOODPECKER_TOKEN:-}"
REPO="${CI_REPO:-}"
SHA="${CI_COMMIT_SHA:-}"
SELF="${CI_PIPELINE_NUMBER:-0}"
PIPELINE_NAME=""
LOCKFILE="/run/woodpecker-trigger.lock"
FLOOD_LIMIT=10
DISK_MOUNTS="/volume1 /"
IN_PIPELINE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --repo) REPO="$2"; shift 2;;
    --commit) SHA="$2"; shift 2;;
    --pipeline-name) PIPELINE_NAME="$2"; shift 2;;
    --self-pipeline) SELF="$2"; shift 2;;
    --lockfile) LOCKFILE="$2"; shift 2;;
    --flood-limit) FLOOD_LIMIT="$2"; shift 2;;
    --disk) DISK_MOUNTS="$2"; shift 2;;
    --in-pipeline) IN_PIPELINE=1; shift;;
    *) echo "GUARD_STATUS=ERROR reason=unknown-arg:$1"; exit 1;;
  esac
done

guard_status() {
  echo "GUARD_STATUS=$1"
  if [ "$#" -ge 2 ]; then echo "guard-detail=$2"; fi
}
skip() {
  if [ "$IN_PIPELINE" = "1" ]; then touch .ci-skip; guard_status "$1" "$2"; exit 0; fi
  guard_status "$1" "$2"; exit 1
}

# --- 0. token check ---
if [ -z "$TOK" ]; then echo "GUARD_STATUS=ERROR reason=no-token"; exit 1; fi

# --- 1. flock: only one trigger scan at a time ---
LOCKDIR=$(dirname "$LOCKFILE"); mkdir -p "$LOCKDIR" 2>/dev/null || true
if ! flock -n "$LOCKFILE" true 2>/dev/null; then
  skip "LOCKED" "another trigger scan in progress"
fi

# --- 2. disk guard: >=95% stop, >=90% critical warn, >=80% warn ---
DISK_BLOCKED=0
for m in $DISK_MOUNTS; do
  [ -d "$m" ] || continue
  P=$(df -P "$m" 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}' 2>/dev/null || echo 0)
  P=${P:-0}
  if [ "$P" -ge 95 ] 2>/dev/null; then DISK_BLOCKED=1; echo "DISK_GUARD mount=$m used=${P}% level=STOP";
  elif [ "$P" -ge 90 ] 2>/dev/null; then echo "DISK_GUARD mount=$m used=${P}% level=CRITICAL";
  elif [ "$P" -ge 80 ] 2>/dev/null; then echo "DISK_GUARD mount=$m used=${P}% level=WARNING"; fi
done
if [ "$DISK_BLOCKED" = "1" ]; then skip "DISK_GUARD_ACTIVE" "disk >=95% — auto trigger stopped"; fi

# --- 3. queue flood guard: pending > limit → stop auto trigger ---
PENDING=0; RUNNING=0; REPO_ID=""
if [ -n "$REPO" ]; then
  REPO_ID=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos?limit=100" --max-time 10 2>/dev/null \
    | sed 's/},{/}\n{/g' | grep "\"full_name\":\"$REPO\"" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
  if [ -z "$REPO_ID" ]; then echo "GUARD_STATUS=ERROR reason=repo-not-found:$REPO"; exit 1; fi
  PIPE_JSON=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$REPO_ID/pipelines?limit=100" --max-time 10 2>/dev/null || echo "")
  PENDING=$(echo "$PIPE_JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"pending"' 2>/dev/null || echo 0)
  RUNNING=$(echo "$PIPE_JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"running"' 2>/dev/null || echo 0)
  PENDING=${PENDING:-0}
  if [ "$PENDING" -gt "$FLOOD_LIMIT" ] 2>/dev/null; then
    skip "FLOOD_GUARD_ACTIVE" "pending=$PENDING running=$RUNNING capacity=1 flood-limit=$FLOOD_LIMIT"
  fi
fi

# --- 4. idempotency: same repo+commit+pipeline already exists (any non-error state) → skip ---
if [ -n "$REPO" ] && [ -n "$SHA" ] && [ -n "$REPO_ID" ]; then
  FOUND=0; FOUND_STATUS=""
  echo "$PIPE_JSON" | sed 's/},{/}\n{/g' | while IFS= read -r pl; do
    echo "$pl" | grep -q "\"commit\":\"$SHA\"" || continue
    N=$(echo "$pl" | grep -oE '"number":[0-9]+' | head -1 | cut -d: -f2)
    [ "$N" = "$SELF" ] && continue
    if [ -n "$PIPELINE_NAME" ]; then
      echo "$pl" | grep -q "\"cron\":\"$PIPELINE_NAME\"" || continue
    fi
    ST=$(echo "$pl" | grep -oE '"status":"[a-z]+"' | head -1 | cut -d'"' -f4)
    if [ -n "$ST" ] && [ "$ST" != "error" ]; then
      echo "1" > /tmp/guard-found
      echo "$ST" > /tmp/guard-found-status
    fi
  done
  FOUND=$(cat /tmp/guard-found 2>/dev/null || echo 0)
  FOUND_STATUS=$(cat /tmp/guard-found-status 2>/dev/null || echo "")
  rm -f /tmp/guard-found /tmp/guard-found-status
  if [ "$FOUND" = "1" ]; then
    if [ "$FOUND_STATUS" = "running" ]; then skip "SKIPPED_RUNNING" "repo=$REPO commit=$SHA pipeline=$PIPELINE_NAME"; fi
    skip "SKIPPED_ALREADY_EXISTS" "repo=$REPO commit=$SHA pipeline=$PIPELINE_NAME status=$FOUND_STATUS"
  fi
fi

guard_status TRIGGERED "repo=$REPO commit=$SHA pipeline=$PIPELINE_NAME"
exit 0
