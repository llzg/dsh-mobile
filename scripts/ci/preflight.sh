#!/bin/sh
# preflight.sh — CI onboarding preflight gate (run BEFORE enabling cron for a repo)
# Usage: preflight.sh <owner/repo> [--min-manual-pass 3] [--flood-limit 10]
# Env: WOODPECKER_URL, WOODPECKER_TOKEN, DSH_MOBILE_RELEASES (optional artifact dir)
# Output: per-check lines + CI_AUTOMATION_READY=YES|NO
set -u
REPO="${1:-}"
if [ -z "$REPO" ]; then echo "usage: preflight.sh <owner/repo>"; exit 2; fi
MIN_PASS=3
FLOOD_LIMIT=10
URL="$WOODPECKER_URL"
if [ -z "$URL" ]; then URL="http://192.168.5.16:8010"; fi
TOK="$WOODPECKER_TOKEN"
if [ -z "$TOK" ]; then echo "CI_AUTOMATION_READY=NO reason=no-token"; exit 1; fi
GUARD_DIR="/root/nas_docker/dsh-mobile/scripts/ci"
REPO_ROOT=$(CDPATH= cd -- "$GUARD_DIR/../.." && pwd)
ARTIFACT_DIR="${DSH_MOBILE_RELEASES:-/volume1/docker/dsh-mobile/releases}"
FAIL=0
check() {
  if [ "$2" = "OK" ]; then echo "PREFLIGHT $1 = PASS"; else echo "PREFLIGHT $1 = FAIL ($2)"; FAIL=1; fi
}

# 1. repo registered + active
RID=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos?limit=100" --max-time 10 2>/dev/null \
    | sed 's/},{/}\n{/g' | grep "\"full_name\":\"$REPO\"" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
check "repo-registered" "$([ -n "$RID" ] && echo OK || echo missing:$REPO)"
if [ -z "$RID" ]; then echo "CI_AUTOMATION_READY=NO reason=repo-not-found"; exit 1; fi

# 2. trusted (volumes) — required for cache/artifact mounts
TINFO=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID" --max-time 10 2>/dev/null || echo "")
check "repo-trusted" "$(echo "$TINFO" | grep -q "\"volumes\":true" && echo OK || echo not-trusted)"

# 3. agent online + capacity
AGENT=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/agents" --max-time 10 2>/dev/null || echo "")
CAP=$(echo "$AGENT" | grep -oE '"capacity":[0-9]+' | head -1 | cut -d: -f2)
LC=$(echo "$AGENT" | grep -oE '"last_contact":[0-9]+' | head -1 | cut -d: -f2)
NOW=$(date +%s)
AGENT_OK="NO"
if [ -n "$LC" ] && [ $((NOW - LC)) -lt 120 ] 2>/dev/null; then AGENT_OK="OK"; fi
check "agent-online" "$AGENT_OK"
check "agent-capacity>=1" "$([ "${CAP:-0}" -ge 1 ] 2>/dev/null && echo OK || echo capacity:$CAP)"

# 4. pipeline YAML valid via lint (if CLI available) or config presence
YAML_OK="NO"
if command -v woodpecker-cli >/dev/null 2>&1; then
  if woodpecker-cli lint .woodpecker/ 2>/dev/null >/dev/null; then YAML_OK="OK"; fi
else
  check "yaml-lint" "SKIP (no woodpecker-cli; check in repo CI instead)"
  YAML_OK="OK"
fi
check "pipeline-yaml" "$YAML_OK"

# 5. latest manual pipeline success + consecutive pass count
JSON=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID/pipelines?limit=100" --max-time 10 2>/dev/null || echo "")
CONSEC=0
for st in $(echo "$JSON" | sed 's/},{/}\n{/g' | grep -oE '"status":"[a-z]+"' | cut -d\" -f4 | head -20); do
  if [ "$st" = "success" ]; then CONSEC=$((CONSEC + 1)); else break; fi
done
check "consecutive-success>=$MIN_PASS" "$([ "$CONSEC" -ge "$MIN_PASS" ] && echo OK || echo got:$CONSEC)"

# 6. pending queue below flood limit
P=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"pending"' 2>/dev/null || echo 0)
check "pending<=$FLOOD_LIMIT" "$([ "${P:-0}" -le "$FLOOD_LIMIT" ] 2>/dev/null && echo OK || echo pending:$P)"

# 7. trigger guard script runs + API reachable (dummy commit → TRIGGERED)
if [ -x "$GUARD_DIR/ci-trigger-guard.sh" ]; then
  G=$(WOODPECKER_URL="$URL" WOODPECKER_TOKEN="$TOK" sh "$GUARD_DIR/ci-trigger-guard.sh" --repo "$REPO" --commit 0000000000000000000000000000000000000000 --lockfile /tmp/preflight-guard.lock 2>/dev/null | grep GUARD_STATUS | cut -d= -f2)
  check "trigger-guard" "$([ "$G" = "TRIGGERED" ] && echo OK || echo status:$G)"
else
  check "trigger-guard" "missing:$GUARD_DIR/ci-trigger-guard.sh"
fi

# 8. docker log rotation enforced (host)
if command -v docker >/dev/null 2>&1; then
  for c in woodpecker-server woodpecker-agent; do
    M=$(docker inspect "$c" --format '{{index .HostConfig.LogConfig.Config "max-size"}}' 2>/dev/null)
    check "docker-log-$c" "$([ -n "$M" ] && echo OK || echo unlimited)"
  done
fi

# 9. disk free
DF=$(df -P /volume1 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}' 2>/dev/null || echo 0)
check "disk-usage<95" "$([ "${DF:-0}" -lt 95 ] 2>/dev/null && echo OK || echo used:$DF%)"

# 10. artifact path
check "artifact-path" "$([ -d "$ARTIFACT_DIR" ] && echo OK || echo missing:$ARTIFACT_DIR)"

# 11. workflow event policy audit (static .woodpecker/*.yml gate)
# CRON IS INFRASTRUCTURE-ONLY: verify/release/bench must not inherit cron;
# release must be deployment-gated. Failure -> CI_AUTOMATION_READY=NO.
POL="NO"
if [ -x "$GUARD_DIR/audit-workflow-events.sh" ]; then
  AUDIT_DIR="${AUDIT_DIR:-$REPO_ROOT/.woodpecker}"
  POL_OUT=$(sh "$GUARD_DIR/audit-workflow-events.sh" --dir "$AUDIT_DIR" --repo "$REPO" 2>/dev/null | grep WORKFLOW_EVENT_POLICY_PASS | cut -d= -f2 || true)
  [ "$POL_OUT" = "YES" ] && POL="OK"
  check "workflow-event-policy" "$POL"
else
  check "workflow-event-policy" "missing:$GUARD_DIR/audit-workflow-events.sh"
fi

if [ "$FAIL" = "0" ]; then echo "CI_AUTOMATION_READY=YES"; else echo "CI_AUTOMATION_READY=NO"; exit 1; fi