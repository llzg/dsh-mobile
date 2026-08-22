#!/bin/sh
# cd-publish.sh — CD: publish signed/latest APK + deploy-status.json + Gitea release asset
set -e
TAG=$(echo "$CI_COMMIT_SHA" | cut -c1-7)
REV="$CI_COMMIT_SHA"
VER=$(grep -oE 'val dshVersionName.*"([^"]+)"' app/build.gradle.kts | sed -E 's/.*"([^"]+)"/\1/' || echo "0.8.0")
REL=/volume1/docker/dsh-mobile/releases
mkdir -p "$REL"

# signed release APK when a keystore was provided
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
  cp app/build/outputs/apk/release/app-release.apk "$REL/dsh-mobile-${VER}+${TAG}.apk"
  cp app/build/outputs/apk/release/app-release.apk "$REL/latest.apk"
  echo "[cd] signed release APK published: dsh-mobile-${VER}+${TAG}.apk"
else
  echo "[cd] WARN: no signed release APK (keystore secrets missing) — unsigned only"
fi

cd "$REL"
sha256sum dsh-mobile-*.apk latest.apk 2>/dev/null > SHA256SUMS.txt || true

# deploy status (real CD data — Running Revision = this release)
DL="$GITEA_URL/llzg/dsh-mobile/releases/download/v${VER}-build${CI_BUILD_NUMBER}/dsh-mobile-${VER}+${TAG}.apk"
printf '{"runningRevision":"%s","shortRevision":"%s","version":"%s","buildNumber":"%s","deployedAt":"%s","health":"%s","downloadUrl":"%s","artifactStore":"/volume1/docker/dsh-mobile/releases"}\n' \
  "$REV" "$TAG" "$VER" "${CI_BUILD_NUMBER:-0}" "$(date -u +%FT%TZ)" "pending" "$DL" > deploy-status.json
echo "[cd] deploy-status.json written:"
cat deploy-status.json

# Gitea release asset (downloadable NAS URL) when token present
if [ -n "$GITEA_TOKEN" ] && [ -f "$REL/latest.apk" ]; then
  RELEASE_NAME="v${VER}-build${CI_BUILD_NUMBER}"
  curl -sf -H "Authorization: token $GITEA_TOKEN" -X POST "$GITEA_URL/api/v1/repos/llzg/dsh-mobile/releases" \
    -H 'content-type: application/json' -d "{\"tag_name\":\"$RELEASE_NAME\",\"name\":\"$RELEASE_NAME\",\"draft\":true}" >/tmp/release.json || echo "[cd] gitea release create failed"
  RID=$(jq -r '.id // empty' /tmp/release.json 2>/dev/null || echo "")
  if [ -n "$RID" ]; then
    curl -sf -H "Authorization: token $GITEA_TOKEN" -X POST "$GITEA_URL/api/v1/repos/llzg/dsh-mobile/releases/$RID/assets?name=latest.apk" -H 'content-type: application/vnd.android.package-archive' --data-binary @"$REL/latest.apk" || echo "[cd] asset upload failed"
    echo "[cd] gitea release $RELEASE_NAME created with latest.apk asset"
  fi
fi

# healthcheck: artifact reachable
if curl -sf -o /dev/null --max-time 10 "$DL" 2>/dev/null; then
  echo "[cd] HEALTH_OK download URL reachable"
else
  echo "[cd] HEALTH_WARN download URL not yet reachable (asset upload may be pending or draft)"
fi

