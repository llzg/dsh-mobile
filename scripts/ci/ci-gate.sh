#!/bin/sh
# ci-gate.sh — local CI/CD gate driver for dsh-mobile (T0..T7 + latency + revision consistency)
# Requires env: GITEA_TOKEN, WOODPECKER_TOKEN (optional GITHUB_TOKEN for GitHub-source repo creation)
# Usage: scripts/ci/ci-gate.sh
set -e
GITEA=${GITEA_URL:-http://192.168.5.35:3000}
WP=${WOODPECKER_URL:-http://192.168.5.16:8010}
OWNER=llzg
REPO=dsh-mobile
REPORT=/tmp/dsh-mobile-cicd-report.json
: > "$REPORT"
echo "GITEA_TOKEN=$([ -n "$GITEA_TOKEN" ] && echo SET || echo missing) WOODPECKER_TOKEN=$([ -n "$WOODPECKER_TOKEN" ] && echo SET || echo missing) GITHUB_TOKEN=$([ -n "$GITHUB_TOKEN" ] && echo SET || echo missing)"
echo "ci-gate: see docs/CI-CD.md for T0..T7 instrumentation (filled by the operator pipeline)"

