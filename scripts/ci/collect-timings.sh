#!/bin/sh
# collect-timings.sh — gather T0..T7 + pipeline step timings into docs/REPORT.md data
# Usage: collect-timings.sh <commit> <t0-ms>
set -e
TARGET=$1
T0=$2
export XDG_CONFIG_HOME=/root/nas_docker/.wpcli-config
export WOODPECKER_SERVER=http://192.168.5.16:8010
export WOODPECKER_TOKEN=$(grep '^WOODPECKER_TOKEN=' /root/nas_docker/.ci-tokens.env | cut -d= -f2-)
cd /tmp
P=$(./woodpecker-cli pipeline ls llzg/dsh-mobile 2>/dev/null | grep "$TARGET" | head -1 | awk '{print $1}' | tr -d '#')
echo "pipeline for $TARGET: #$P"
if [ -n "$P" ]; then
  ./woodpecker-cli pipeline ps llzg/dsh-mobile "$P" 2>&1 | grep -E 'Step:|Started:|Stopped:|State:' | paste - - - - | sed 's/Step: //;s/Started: //;s/Stopped: //;s/State: //' | awk -v t0=$T0 '{print $0, " dur="($3-$2)"ms rel="($2-t0)/1000"s"}'
fi