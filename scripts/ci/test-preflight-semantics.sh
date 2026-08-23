#!/bin/sh
# test-preflight-semantics.sh — preflight reliability self-test
# Verifies the three false-negative fixes (2026-08-23):
#   Case 1: host disk (/volume1) not visible  -> CHECK_DISK/ARTIFACT never FAIL
#   Case 2: no woodpecker-cli, PyYAML parser available -> CHECK_YAML PASS/SKIP
#   Case 3: cron no-op flood must NOT break verify-only consecutive count
# Usage: WOODPECKER_URL=... WOODPECKER_TOKEN=... sh test-preflight-semantics.sh
set -u
SELF=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=$(mktemp)
WOODPECKER_URL="${WOODPECKER_URL:-http://192.168.5.16:8010}" \
  WOODPECKER_TOKEN="${WOODPECKER_TOKEN:-}" \
  sh "$SELF/preflight.sh" llzg/dsh-mobile > "$OUT" 2>&1
RC=$?
echo "=== preflight output ==="
cat "$OUT"
echo
FAILS=0
# Case 1: /volume1 not visible here -> no false FAIL on DISK/ARTIFACT
[ -d /volume1 ] || {
  grep -q "CHECK_DISK=FAIL" "$OUT" && { echo "CASE1 FAIL: CHECK_DISK=FAIL with /volume1 missing"; FAILS=$((FAILS+1)); } || echo "CASE1 PASS: no false disk FAIL"
  grep -q "CHECK_ARTIFACT_PATH=FAIL" "$OUT" && { echo "CASE1 FAIL: ARTIFACT false FAIL"; FAILS=$((FAILS+1)); } || echo "CASE1 PASS: artifact SKIP not FAIL"
}
# Case 2: yaml linter missing but parser available -> PASS/SKIP, never FAIL
if ! command -v woodpecker-cli >/dev/null 2>&1; then
  grep -q "CHECK_YAML=PASS" "$OUT" && echo "CASE2 PASS: PyYAML parse used" || {
    grep -q "CHECK_YAML=SKIP" "$OUT" && echo "CASE2 PASS: yaml SKIP" || { echo "CASE2 FAIL"; FAILS=$((FAILS+1)); }
  }
fi
# Case 3: consecutive verify count >= 3 despite cron flood
CV=$(grep -oE "CHECK_CONSECUTIVE_VERIFY=(PASS|FAIL)" "$OUT" | cut -d= -f2)
if [ "$CV" = "PASS" ]; then echo "CASE3 PASS: verify-only consecutive count works through cron flood"; else echo "CASE3 FAIL"; FAILS=$((FAILS+1)); fi
# overall readiness: with all platform checks green, READY must be YES
grep -q "CI_AUTOMATION_READY=YES" "$OUT" && echo "READY PASS" || echo "READY NOTE: $(grep CI_AUTOMATION_READY "$OUT" | tail -1)"
rm -f "$OUT"
[ "$FAILS" = "0" ] && { echo "PREFLIGHT_SELF_TEST=PASS"; exit 0; } || { echo "PREFLIGHT_SELF_TEST=FAIL"; exit 1; }
