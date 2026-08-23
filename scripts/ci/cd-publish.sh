#!/bin/sh
# cd-publish.sh — CD: publish APK + deploy-status.json + Gitea release asset
set -e
TAG=$(echo "$CI_COMMIT_SHA" | cut -c1-7)
REV="$CI_COMMIT_SHA"
# VER derived from the actual built APK filename (gradle DSH_VERSION_NAME may be set in another step)
VER=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 | sed -E 's/.*dsh-mobile-([0-9]+\.[0-9]+\.[0-9]+).*/\1/' || echo "0.8.0")
REL=/volume1/docker/dsh-mobile/releases
mkdir -p "$REL"

# pick the release APK: signed when available
APK_SRC=""
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
  APK_SRC=app/build/outputs/apk/release/app-release.apk
  echo "[cd] signed release APK available"
elif [ -f app/build/outputs/apk/release/app-release-unsigned.apk ]; then
  # PRODUCTION signing gate: never silently publish unsigned as production
  if [ -z "$CI_PIPELINE_DEPLOY_TARGET" ] || [ "$CI_PIPELINE_DEPLOY_TARGET" = "production" ]; then
    echo "FAIL_SIGNING_REQUIRED production-without-signed-apk"
    exit 1
  fi
  APK_SRC=app/build/outputs/apk/release/app-release-unsigned.apk
  echo "[cd] WARN: unsigned APK published for target=$CI_PIPELINE_DEPLOY_TARGET (test only)"
else
  echo "FAIL_SIGNING_REQUIRED no-release-apk"
  exit 1
fi
if [ -n "$APK_SRC" ] && [ -f "$APK_SRC" ]; then
  cp "$APK_SRC" "$REL/dsh-mobile-${VER}+${TAG}.apk"
  cp "$APK_SRC" "$REL/latest.apk"
  echo "[cd] release APK published: dsh-mobile-${VER}+${TAG}.apk"
fi

cd "$REL"
sha256sum dsh-mobile-*.apk latest.apk 2>/dev/null > SHA256SUMS.txt || true

# deploy status (real CD data — Running Revision = this release)
DEPLOY_TARGET="${CI_PIPELINE_DEPLOY_TARGET:-}"
case "$DEPLOY_TARGET" in
  ""|production|prod) TEST_MARK=""; DRAFT=false ;;
  *) TEST_MARK="-test-${DEPLOY_TARGET}"; DRAFT=true ;;
esac
RELEASE_NAME="v${VER}-build${CI_BUILD_NUMBER:-0}-${TAG}${TEST_MARK}"
DL="$GITEA_URL/llzg/dsh-mobile/releases/download/$RELEASE_NAME/dsh-mobile-${VER}+${TAG}.apk"
printf '{"runningRevision":"%s","shortRevision":"%s","version":"%s","buildNumber":"%s","deployedAt":"%s","health":"%s","downloadUrl":"%s","artifactStore":"/volume1/docker/dsh-mobile/releases"}\n' \
  "$REV" "$TAG" "$VER" "${CI_BUILD_NUMBER:-0}" "$(date -u +%FT%TZ)" "pending" "$DL" > deploy-status.json
echo "[cd] deploy-status.json:"
cat deploy-status.json

# Gitea release asset
if [ -n "$GITEA_TOKEN" ] && [ -f "$REL/dsh-mobile-${VER}+${TAG}.apk" ]; then
  curl -sf -H "Authorization: token $GITEA_TOKEN" -X POST "$GITEA_URL/api/v1/repos/llzg/dsh-mobile/releases" \
    -H 'content-type: application/json' -d "{\"tag_name\":\"$RELEASE_NAME\",\"name\":\"$RELEASE_NAME\",\"draft\":$DRAFT}" >/tmp/release.json || echo "[cd] gitea release create failed"
  RID=$(jq -r '.id // empty' /tmp/release.json 2>/dev/null || echo "")
  if [ -n "$RID" ]; then
    curl -sf -H "Authorization: token $GITEA_TOKEN" -X POST "$GITEA_URL/api/v1/repos/llzg/dsh-mobile/releases/$RID/assets?name=dsh-mobile-${VER}%2B${TAG}.apk" -H 'content-type: application/vnd.android.package-archive' --data-binary @"$REL/dsh-mobile-${VER}+${TAG}.apk" || echo "[cd] asset upload failed"
    echo "[cd] gitea release $RELEASE_NAME published"
  fi
fi

# healthcheck: artifact reachable
if curl -sf -o /dev/null --max-time 10 "$DL" 2>/dev/null; then
  echo "[cd] HEALTH_OK download URL reachable"
else
  echo "[cd] HEALTH_WARN download URL not yet reachable"
fi
