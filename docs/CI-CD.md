# 本地 CI/CD 接入与验收（dsh-mobile）

## 复用现有链路（不另起一套）

\`\`\`
GitHub llzg/dsh-mobile（canonical source）
   │ push (T0)
   ▼
Gitea mirror（N5105 192.168.5.35:3000，1min 等效 mirror-sync 复用）(T1)
   ▼
Woodpecker（DXP6800 192.168.5.16:8010，cron 触发复用）(T2)
   ▼
verify pipeline（clone Gitea LAN mirror → JDK/SDK/Gradle cache → test → lint → assemble）(T3→T4)
   ▼
artifact publish（/volume1/docker/dsh-mobile/releases + Gitea release asset）(T5)
   ▼
release/CD pipeline（manual 门禁 → 签名 APK/latest.apk → deploy-status.json → healthcheck）(T6→T7)
\`\`\`

## 流水线

- \`.woodpecker/verify.yml\`：push / cron / manual。步骤：env-check → idempotency-check（同 SHA 产物已存在则 skip）
  → setup-java（temurin 17）→ setup-android-sdk（cmdline-tools + platform 35 + build-tools，缓存 \`/volume1/docker/dsh-mobile/ci-cache\`）
  → unit-tests（\`:core:test :mock-harness:test :app:testDebugUnitTest\`）→ lint（\`:app:lintDebug\`）
  → assemble（debug + release）→ publish-artifact（APK + SHA256SUMS + build-info-<sha7>.json，真实 CI 数据）
- \`.woodpecker/release.yml\`：manual（部署门禁）。步骤：release-check → setup-android-sdk → assemble-release（DSH_KEYSTORE* secrets 存在则签名）
  → cd-publish（latest.apk + deploy-status.json + Gitea release asset + 下载 URL healthcheck）

## 缓存策略（Android）

- Gradle：\`/volume1/docker/dsh-mobile/ci-cache/gradle\` → \`GRADLE_USER_HOME\`（caches + wrapper）
- Android SDK：\`/volume1/docker/dsh-mobile/ci-cache/android-sdk\` → \`/opt/android-sdk\`（components 增量）
- 记录 cold / warm 构建时间（见最终报告）

## CD 产物与下载

- 版本化 APK：\`dsh-mobile-<VER>+<sha7>.apk\`（release 签名版）/ \`-debug.apk\` / \`-release-unsigned.apk\`
- \`latest.apk\`（最新签名版）
- \`SHA256SUMS.txt\`、\`build-info-<sha7>.json\`、\`deploy-status.json\`（running revision = 最新发布）
- NAS 内部下载：Gitea release（\`http://192.168.5.35:3000/llzg/dsh-mobile/releases/download/...\`）或 artifact store 目录
- 移动端 CI/CD 状态页：\`nas-cd/status/index.html\`（读取上述真实 JSON，非 mock）

## Revision 一致性校验（验收项）

SOURCE_REVISION == CI_REVISION == ARTIFACT_REVISION == RUNNING_REVISION == UI_REVISION
（GitHub HEAD / CI_COMMIT_SHA / APK 文件名 sha7 / deploy-status.runningRevision / 状态页显示）

