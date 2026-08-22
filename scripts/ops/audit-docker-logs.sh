#!/bin/sh
# audit-docker-logs.sh — audit docker logging config for CI containers
set -u
if [ $# -ge 1 ]; then CONTAINERS="$@"; else CONTAINERS="woodpecker-server woodpecker-agent"; fi
RISK=0
for c in $CONTAINERS; do
  if ! docker inspect "$c" >/dev/null 2>&1; then echo "$c | MISSING"; continue; fi
  DRIVER=$(docker inspect "$c" --format '{{.HostConfig.LogConfig.Type}}' 2>/dev/null)
  MAXS=$(docker inspect "$c" --format '{{index .HostConfig.LogConfig.Config "max-size"}}' 2>/dev/null)
  MAXF=$(docker inspect "$c" --format '{{index .HostConfig.LogConfig.Config "max-file"}}' 2>/dev/null)
  LOGPATH=$(docker inspect "$c" --format '{{.LogPath}}' 2>/dev/null)
  SIZE=$(du -sh "$LOGPATH" 2>/dev/null | cut -f1 || echo "?")
  if [ "$DRIVER" = "json-file" ] && [ -n "$MAXS" ]; then
    echo "$c | $SIZE | $DRIVER | $MAXS | $MAXF"
  else
    echo "$c | $SIZE | $DRIVER | $MAXS | $MAXF | RISK"
    RISK=1
  fi
done
if [ "$RISK" = "0" ]; then echo "AUDIT_DOCKER_LOGS=PASS"; else echo "AUDIT_DOCKER_LOGS=RISK_FOUND"; fi
exit $RISK