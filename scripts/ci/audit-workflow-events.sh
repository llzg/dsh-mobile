#!/bin/sh
# audit-workflow-events.sh — static workflow event policy audit (wrapper)
# Usage: audit-workflow-events.sh [--dir <path>] [--repo <owner/name>]
# Exit: 0 = policy pass, 1 = violations, 2 = tooling error
SELF=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIR=".woodpecker"
REPO=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dir) DIR="$2"; shift 2;;
    --repo) REPO="$2"; shift 2;;
    *) shift;;
  esac
done
PY="$SELF/audit-workflow-events.py"
if [ ! -f "$PY" ]; then echo "ERROR: missing $PY"; echo "WORKFLOW_EVENT_POLICY_PASS=NO"; exit 2; fi
if ! command -v python3 >/dev/null 2>&1; then echo "ERROR: python3 not found"; echo "WORKFLOW_EVENT_POLICY_PASS=NO"; exit 2; fi
if [ -n "$REPO" ]; then python3 "$PY" --dir "$DIR" --repo "$REPO"; else python3 "$PY" --dir "$DIR"; fi
exit $?
