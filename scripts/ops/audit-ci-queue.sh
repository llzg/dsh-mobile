#!/bin/sh
# audit-ci-queue.sh — CI queue health audit (Woodpecker API based)
# Usage: audit-ci-queue.sh [repo1 repo2 ...]   (env: WOODPECKER_URL, WOODPECKER_TOKEN)
set -u
URL="$WOODPECKER_URL"
if [ -z "$URL" ]; then URL="http://192.168.5.16:8010"; fi
TOK="$WOODPECKER_TOKEN"
if [ -z "$TOK" ]; then echo "ERROR: WOODPECKER_TOKEN required"; exit 1; fi
REPOS="$@"
if [ -z "$REPOS" ]; then REPOS="llzg/dsh-mobile llzg/invoice-agent-ui"; fi
TOTAL_PENDING=0; TOTAL_RUNNING=0; DUPS=0
for repo in $REPOS; do
  RID=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos?limit=100" --max-time 10 2>/dev/null \
    | sed 's/},{/}\n{/g' | grep "\"full_name\":\"$repo\"" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
  if [ -z "$RID" ]; then echo "$repo | not-found"; continue; fi
  JSON=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID/pipelines?limit=100" --max-time 10 2>/dev/null || echo "")
  P=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"pending"' 2>/dev/null || echo 0)
  R=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"running"' 2>/dev/null || echo 0)
  P=$(echo "$P" | grep -oE "[0-9]+" | head -1); [ -z "$P" ] && P=0; R=$(echo "$R" | grep -oE "[0-9]+" | head -1); [ -z "$R" ] && R=0; TOTAL_PENDING=$((TOTAL_PENDING + P)); TOTAL_RUNNING=$((TOTAL_RUNNING + R))
  D=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -v '"status":"canceled"' | grep -v '"status":"error"' | grep -v '"cron":"mirror-sync"' \
    | grep -oE '"commit":"[0-9a-f]{7,}"' | sort | uniq -d | wc -l)
  DUPS=$((DUPS + D))
  echo "$repo | pending=$P running=$R dup-commits=$D"
done
echo "TOTAL | pending=$TOTAL_PENDING running=$TOTAL_RUNNING"
echo "DUPLICATE_TRIGGER=$DUPS"
if [ "$DUPS" -gt 0 ]; then echo "AUDIT_CI_QUEUE=DUPLICATES_FOUND"; else echo "AUDIT_CI_QUEUE=OK"; fi