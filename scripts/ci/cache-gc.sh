#!/bin/sh
# cache-gc.sh — CI cache governance (Gradle / Android SDK)
# Policy: total cap (default 4G); when exceeded, prune re-downloadable parts
# (gradle daemon logs, wrapper dists, transforms) but KEEP dependency caches and SDK.
# Usage: cache-gc.sh [--cache-dir <path>] [--max-size-mb 4096] [--dry-run]
set -u
CACHE_DIR="/volume1/docker/dsh-mobile/ci-cache"
MAX_MB=4096
DRY=0
if [ $# -ge 1 ]; then
  while [ $# -gt 0 ]; do
    case "$1" in
      --cache-dir) CACHE_DIR="$2"; shift 2;;
      --max-size-mb) MAX_MB=$2; shift 2;;
      --dry-run) DRY=1; shift;;
      *) echo "unknown arg $1"; exit 2;;
    esac
  done
fi
if [ ! -d "$CACHE_DIR" ]; then echo "CACHE_GC=NO_DIR $CACHE_DIR"; exit 0; fi
TOTAL=$(du -sm "$CACHE_DIR" 2>/dev/null | cut -f1)
TOTAL=${TOTAL:-0}
echo "CACHE_GC total=${TOTAL}MB cap=${MAX_MB}MB"
if [ "$TOTAL" -le "$MAX_MB" ]; then echo "CACHE_GC=OK under-cap"; exit 0; fi
PRUNED=0
# 1. gradle daemon logs (re-created on demand)
GDAEMON="$CACHE_DIR/gradle/daemon"
if [ -d "$GDAEMON" ]; then
  SZ=$(du -sm "$GDAEMON" 2>/dev/null | cut -f1); SZ=${SZ:-0}
  if [ "$DRY" = "1" ]; then echo "CACHE_GC would-remove daemon ${SZ}MB";
  else rm -rf "$GDAEMON"; echo "CACHE_GC removed daemon ${SZ}MB"; fi
  PRUNED=1
fi
# 2. gradle wrapper dists older than the current one (re-downloadable via proxy)
GWRAP="$CACHE_DIR/gradle/wrapper/dists"
if [ -d "$GWRAP" ]; then
  SZ=$(du -sm "$GWRAP" 2>/dev/null | cut -f1); SZ=${SZ:-0}
  if [ "$DRY" = "1" ]; then echo "CACHE_GC would-remove wrapper-dists ${SZ}MB";
  else rm -rf "$GWRAP"; echo "CACHE_GC removed wrapper-dists ${SZ}MB"; fi
  PRUNED=1
fi
# 3. gradle transforms/caches-build (rebuilt on demand)
GTRANS="$CACHE_DIR/gradle/caches"
if [ -d "$GTRANS" ]; then
  # only remove build-cache-* and transforms-*, KEEP modules-2 (dependency cache)
  for t in "$GTRANS"/build-cache-* "$GTRANS"/transforms-*; do
    [ -e "$t" ] || continue
    SZ=$(du -sm "$t" 2>/dev/null | cut -f1); SZ=${SZ:-0}
    if [ "$DRY" = "1" ]; then echo "CACHE_GC would-remove $(basename "$t") ${SZ}MB";
    else rm -rf "$t"; echo "CACHE_GC removed $(basename "$t") ${SZ}MB"; fi
    PRUNED=1
  done
fi
if [ "$DRY" = "1" ]; then echo "CACHE_GC=dry-run"; else
  TOTAL2=$(du -sm "$CACHE_DIR" 2>/dev/null | cut -f1); TOTAL2=${TOTAL2:-0}
  echo "CACHE_GC after=${TOTAL2}MB pruned=$PRUNED"
fi