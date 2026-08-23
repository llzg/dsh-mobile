#!/bin/sh
# ci-status.sh — CI platform health snapshot (queue/capacity/dup/disk/log/cache)
# Env: WOODPECKER_URL, WOODPECKER_TOKEN (host: docker for log/cache checks)
set -u
URL="$WOODPECKER_URL"
if [ -z "$URL" ]; then URL="http://192.168.5.16:8010"; fi
TOK="$WOODPECKER_TOKEN"
if [ -z "$TOK" ]; then echo "ERROR: WOODPECKER_TOKEN required"; exit 1; fi
echo "== agents:"
curl -sf -H "Authorization: Bearer $TOK" "$URL/api/agents" --max-time 8 2>/dev/null \
  | grep -oE '"capacity":[0-9]+|"backend":"[a-z]+"' | tr "\n" " "
echo; echo "== queue (dsh-mobile):"
sh /root/nas_docker/dsh-mobile/scripts/ops/audit-ci-queue.sh llzg/dsh-mobile 2>/dev/null | tail -2
echo "== disk:"
df -h /volume1 / 2>/dev/null | awk 'NR>1 {print $6": "$5" used"}'
echo "== docker logs:"
sh /root/nas_docker/dsh-mobile/scripts/ops/audit-docker-logs.sh 2>/dev/null | tail -3
echo "== cache:"
du -sh /volume1/docker/dsh-mobile/ci-cache 2>/dev/null || echo "no cache dir"