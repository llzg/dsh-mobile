#!/bin/sh
# ci-setup.sh — one-time CI/CD onboarding for dsh-mobile (run after .ci-tokens.env is populated)
# Requires env: GITEA_TOKEN, WOODPECKER_TOKEN, (optional) GITHUB_TOKEN
# Order: GitHub source repo → Gitea mirror → Woodpecker repo + cron + secrets
set -e
GITEA=${GITEA_URL:-http://192.168.5.35:3000}
WP=${WOODPECKER_URL:-http://192.168.5.16:8010}
OWNER=llzg
REPO=dsh-mobile
GH_API=https://api.github.com

echo "== 1/4 GitHub source repo (canonical) =="
if [ -n "$GITHUB_TOKEN" ]; then
  curl -sf -H "Authorization: token $GITHUB_TOKEN" -X POST "$GH_API/user/repos" \
    -H 'content-type: application/json' \
    -d "{\"name\":\"$REPO\",\"description\":\"DSH Mobile (Android APK) — local CI/CD acceptance\",\"private\":false}" \
    || echo "WARN: github repo create failed (may already exist)"
  git remote add origin "https://$GITHUB_TOKEN@github.com/$OWNER/$REPO.git" 2>/dev/null || git remote set-url origin "https://$GITHUB_TOKEN@github.com/$OWNER/$REPO.git"
  git push -u origin main || echo "WARN: push failed"
  echo "github repo ready: https://github.com/$OWNER/$REPO"
else
  echo "SKIP (GITHUB_TOKEN not set) — push manually or set token"
fi

echo "== 2/4 Gitea mirror (LAN) =="
if [ -n "$GITEA_TOKEN" ]; then
  # create a mirror repo on Gitea that pulls from GitHub
  curl -sf -H "Authorization: token $GITEA_TOKEN" -X POST "$GITEA/api/v1/user/repos" \
    -H 'content-type: application/json' \
    -d "{\"name\":\"$REPO\",\"mirror\":true,\"clone_addr\":\"https://github.com/$OWNER/$REPO.git\",\"private\":false}" \
    && echo "gitea mirror created: $GITEA/$OWNER/$REPO" \
    || echo "WARN: gitea mirror create failed"
else
  echo "SKIP (GITEA_TOKEN not set)"
fi

echo "== 3/4 Woodpecker repo registration =="
if [ -n "$WOODPECKER_TOKEN" ]; then
  # GitHub forge: register repo via CLI-style API (adjust to forge behavior after first run)
  curl -sf -H "Authorization: Bearer $WOODPECKER_TOKEN" -X POST "$WP/api/repos" \
    -H 'content-type: application/json' \
    -d "{\"forge_remote\":{\"url\":\"https://github.com/$OWNER/$REPO.git\",\"owner\":\"$OWNER\",\"name\":\"$REPO\"}}" \
    || echo "WARN: woodpecker repo registration failed — use UI/CLI (woodpecker-cli repo add $OWNER/$REPO)"
else
  echo "SKIP (WOODPECKER_TOKEN not set)"
fi

echo "== 4/4 secrets + cron (after repo id known) =="
echo "add secrets: REGISTRY_USER/REGISTRY_PASSWORD/GITEA_TOKEN (repo secrets) + cron */1 (reuse existing trigger pattern)"
echo "ci-setup done — next: push a commit, watch T0..T7 (docs/CI-CD.md)"

