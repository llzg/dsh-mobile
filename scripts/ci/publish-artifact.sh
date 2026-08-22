#!/bin/sh
# publish-artifact.sh — publish APKs + build-info to the NAS artifact store (/volume1/docker/dsh-mobile/releases)
set -e
TAG=$(echo "$CI_COMMIT_SHA" | cut -c1-7)
REV="$CI_COMMIT_SHA"
VER=$(grep -oE 'val dshVersionName.*"([^"]+)"' app/build.gradle.kts | sed -E 's/.*"([^"]+)"/\1/' || echo "0.8.0")
REL=/volume1/docker/dsh-mobile/releases
mkdir -p "$REL"
cp app/build/outputs/apk/debug/app-debug.apk "$REL/dsh-mobile-${VER}+${TAG}-debug.apk" 2>/dev/null || true
cp app/build/outputs/apk/release/app-release-unsigned.apk "$REL/dsh-mobile-${VER}+${TAG}-release-unsigned.apk" 2>/dev/null || true
cd "$REL"
sha256sum dsh-mobile-*.apk > SHA256SUMS.txt
printf '{"revision":"%s","shortRevision":"%s","version":"%s","buildNumber":"%s","builtAt":"%s","status":"built"}\n' "$REV" "$TAG" "$VER" "${CI_BUILD_NUMBER:-0}" "$(date -u +%FT%TZ)" > "build-info-${TAG}.json"
cp "build-info-${TAG}.json" build-info.json
echo "[publish] artifact store:"
ls -la "$REL" | tail -10

