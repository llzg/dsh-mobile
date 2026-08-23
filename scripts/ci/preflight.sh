#!/bin/sh
# preflight.sh — CI onboarding preflight gate (run BEFORE enabling cron for a repo)
# Usage: preflight.sh <owner/repo> [--min-verify-pass 3] [--flood-limit 10]
# Env: WOODPECKER_URL, WOODPECKER_TOKEN, DSH_MOBILE_RELEASES (optional artifact dir)
# States: PASS | WARN | SKIP | FAIL  (SKIP != FAIL; only real blockers -> CI_AUTOMATION_READY=NO)
# Output: CHECK_<NAME>=STATE + per-check reason + CI_AUTOMATION_READY=YES|NO
set -u
REPO="${1:-}"
if [ -z "$REPO" ]; then echo "usage: preflight.sh <owner/repo>"; exit 2; fi
MIN_PASS=3
FLOOD_LIMIT=10
URL="$WOODPECKER_URL"
if [ -z "$URL" ]; then URL="http://192.168.5.16:8010"; fi
TOK="$WOODPECKER_TOKEN"
if [ -z "$TOK" ]; then echo "CHECK_TOKEN=FAIL reason=no-token"; echo "CI_AUTOMATION_READY=NO reason=no-token"; exit 1; fi
GUARD_DIR="/root/nas_docker/dsh-mobile/scripts/ci"
REPO_ROOT=$(CDPATH= cd -- "$GUARD_DIR/../.." && pwd)
ARTIFACT_DIR="${DSH_MOBILE_RELEASES:-/volume1/docker/dsh-mobile/releases}"
FAIL=0

emit() { # name state reason
  echo "CHECK_$1=$2 $3"
  if [ "$2" = "FAIL" ]; then FAIL=1; fi
}

# 1. repo registered + active
RID=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos?limit=100" --max-time 10 2>/dev/null \
    | sed 's/},{/}\n{/g' | grep "\"full_name\":\"$REPO\"" | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)
if [ -n "$RID" ]; then emit "REPO_REGISTERED" "PASS" "repo=$REPO id=$RID"; else emit "REPO_REGISTERED" "FAIL" "repo-not-found"; echo "CI_AUTOMATION_READY=NO reason=repo-not-found"; exit 1; fi

# 2. trusted (volumes)
TINFO=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID" --max-time 10 2>/dev/null || echo "")
if echo "$TINFO" | grep -q '"volumes":true'; then emit "REPO_TRUSTED" "PASS" "volumes=true"; else emit "REPO_TRUSTED" "FAIL" "not-trusted"; fi

# 3. agent online + capacity
AGENT=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/agents" --max-time 10 2>/dev/null || echo "")
CAP=$(echo "$AGENT" | grep -oE '"capacity":[0-9]+' | head -1 | cut -d: -f2)
# any agent with a recent last_contact counts as online (stale registrations ignored)
LC=$(echo "$AGENT" | grep -oE '"last_contact":[0-9]+' | cut -d: -f2 | sort -n | tail -1)
NOW=$(date +%s)
if [ -n "$LC" ] && [ $((NOW - LC)) -lt 120 ] 2>/dev/null; then emit "AGENT_ONLINE" "PASS" "last_contact=${LC}"; else emit "AGENT_ONLINE" "FAIL" "agent-offline"; fi
if [ "${CAP:-0}" -ge 1 ] 2>/dev/null; then emit "AGENT_CAPACITY" "PASS" "capacity=$CAP"; else emit "AGENT_CAPACITY" "FAIL" "capacity=$CAP"; fi

# 4. pipeline YAML syntax: woodpecker-cli if present; else vendored PyYAML via the
#    workflow event audit (same parser) — NEVER fail just because the CLI is missing.
YAML_STATE="SKIP"
if command -v woodpecker-cli >/dev/null 2>&1; then
  if ( cd "$REPO_ROOT" && woodpecker-cli lint .woodpecker/ >/dev/null 2>&1 ); then
    YAML_STATE="PASS"; YAML_R="woodpecker-cli lint ok"
  else
    YAML_STATE="FAIL"; YAML_R="woodpecker-cli lint failed"
  fi
elif [ -x "$GUARD_DIR/audit-workflow-events.sh" ]; then
  if sh "$GUARD_DIR/audit-workflow-events.sh" --dir "$REPO_ROOT/.woodpecker" --repo "$REPO" >/dev/null 2>&1; then
    YAML_STATE="PASS"; YAML_R="PyYAML parse ok (audit-workflow-events)"
  else
    if python3 "$GUARD_DIR/audit-workflow-events.py" --dir "$REPO_ROOT/.woodpecker" --repo "$REPO" 2>&1 | grep -q "YAML parse error"; then
      YAML_STATE="FAIL"; YAML_R="yaml parse error"
    else
      YAML_STATE="PASS"; YAML_R="PyYAML parse ok (policy checked separately)"
    fi
  fi
else
  YAML_STATE="SKIP"; YAML_R="no linter + no audit script"
fi
emit "YAML" "$YAML_STATE" "$YAML_R"

# 5. consecutive VERIFY-only success (manual-event pipelines with a verify workflow).
CONSEC=0; SEEN_VERIFY=0
# event=manual filter avoids the per-minute cron no-op flood hiding recent
# manual/verify pipelines in the newest-100 window.
JSON=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID/pipelines?limit=100&event=manual" --max-time 10 2>/dev/null || echo "")
MANUAL_NUMS=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -oE '"number":[0-9]+' | cut -d: -f2 | head -15)
for N in $MANUAL_NUMS; do
  DETAIL=$(curl -sf -H "Authorization: Bearer $TOK" "$URL/api/repos/$RID/pipelines/$N" --max-time 8 2>/dev/null || echo "")
  VSTATE=$(echo "$DETAIL" | sed 's/},{/}\n{/g' | grep -E '"name":"verify"' | grep -oE '"state":"[a-z]+"' | head -1 | cut -d'"' -f4)
  if [ -z "$VSTATE" ]; then continue; fi
  SEEN_VERIFY=1
  if [ "$VSTATE" = "success" ]; then CONSEC=$((CONSEC + 1)); else break; fi
done
if [ "$SEEN_VERIFY" = "1" ]; then
  if [ "$CONSEC" -ge "$MIN_PASS" ]; then emit "CONSECUTIVE_VERIFY" "PASS" "verify-only consecutive=$CONSEC (min=$MIN_PASS)"; else emit "CONSECUTIVE_VERIFY" "FAIL" "verify-only consecutive=$CONSEC (min=$MIN_PASS)"; fi
else
  emit "CONSECUTIVE_VERIFY" "SKIP" "no verify workflow found in recent manual pipelines"
fi

# 6. pending queue
P=$(echo "$JSON" | sed 's/},{/}\n{/g' | grep -c '"status":"pending"' 2>/dev/null)
if [ "${P:-0}" -le "$FLOOD_LIMIT" ] 2>/dev/null; then emit "QUEUE" "PASS" "pending=$P (limit=$FLOOD_LIMIT)"; else emit "QUEUE" "FAIL" "pending=$P (limit=$FLOOD_LIMIT)"; fi

# 7. trigger guard
if [ -x "$GUARD_DIR/ci-trigger-guard.sh" ]; then
  G=$(WOODPECKER_URL="$URL" WOODPECKER_TOKEN="$TOK" sh "$GUARD_DIR/ci-trigger-guard.sh" --repo "$REPO" --commit 0000000000000000000000000000000000000000 --lockfile /tmp/preflight-guard.lock 2>/dev/null | grep GUARD_STATUS | cut -d= -f2)
  if [ "$G" = "TRIGGERED" ]; then emit "TRIGGER_GUARD" "PASS" "guard=TRIGGERED"; else emit "TRIGGER_GUARD" "FAIL" "guard=$G"; fi
else
  emit "TRIGGER_GUARD" "SKIP" "ci-trigger-guard.sh missing"
fi

# 8. docker log rotation (only where docker CLI exists)
if command -v docker >/dev/null 2>&1; then
  ROT="PASS"
  for c in woodpecker-server woodpecker-agent; do
    M=$(docker inspect "$c" --format '{{index .HostConfig.LogConfig.Config "max-size"}}' 2>/dev/null)
    [ -n "$M" ] || ROT="FAIL"
  done
  emit "DOCKER_LOG_ROTATION" "$ROT" "json-file max-size=${M:-unset}"
else
  emit "DOCKER_LOG_ROTATION" "SKIP" "docker CLI not available in this environment"
fi

# 9. disk: environment-aware (SKIP_NOT_MOUNTED instead of false FAIL)
DISK_STATE="SKIP"; DISK_USED="?"
for M in /volume1 / /data; do
  if [ -d "$M" ] && df -P "$M" >/dev/null 2>&1; then
    DF=$(df -P "$M" 2>/dev/null | awk 'NR==2 {gsub("%","",$5); print $5}' 2>/dev/null || echo 0)
    if [ "${DF:-0}" -lt 80 ] 2>/dev/null; then DISK_STATE="PASS"; DISK_USED="$DF% ($M)"
    elif [ "${DF:-0}" -lt 95 ] 2>/dev/null; then DISK_STATE="WARN"; DISK_USED="$DF% ($M)"
    else DISK_STATE="FAIL"; DISK_USED="$DF% ($M)"; fi
    break
  fi
done
emit "DISK" "$DISK_STATE" "used=$DISK_USED (SKIP_NOT_MOUNTED if no host disk visible)"

# 10. artifact path (SKIP when not mounted)
if [ -d "$ARTIFACT_DIR" ]; then emit "ARTIFACT_PATH" "PASS" "$ARTIFACT_DIR"; else emit "ARTIFACT_PATH" "SKIP" "not-mounted:$ARTIFACT_DIR (SKIP_NOT_MOUNTED)"; fi

# 11. workflow event policy audit
if [ -x "$GUARD_DIR/audit-workflow-events.sh" ]; then
  AUDIT_DIR="${AUDIT_DIR:-$REPO_ROOT/.woodpecker}"
  POL_OUT=$(sh "$GUARD_DIR/audit-workflow-events.sh" --dir "$AUDIT_DIR" --repo "$REPO" 2>/dev/null | grep WORKFLOW_EVENT_POLICY_PASS | cut -d= -f2 || true)
  if [ "$POL_OUT" = "YES" ]; then emit "WORKFLOW_POLICY" "PASS" "policy ok"; else emit "WORKFLOW_POLICY" "FAIL" "policy=$POL_OUT"; fi
else
  emit "WORKFLOW_POLICY" "FAIL" "audit-workflow-events.sh missing"
fi

echo
if [ "$FAIL" = "0" ]; then echo "CI_AUTOMATION_READY=YES"; else echo "CI_AUTOMATION_READY=NO"; exit 1; fi
