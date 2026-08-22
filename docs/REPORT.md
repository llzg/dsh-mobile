# dsh-mobile 最终验收报告

> 完成时间：2026-08-22（实际为跨日会话） · 环境：UGREEN DXP6800（192.168.5.16）+ N5105（192.168.5.35）

## 1. 选型

- **PRIMARY**: sorsama/deepseek-harness-mobile（原生 Android APK，Kotlin/Compose，dsh 0.1.1-rc.2 baseline 对齐，MIT）
- **SECONDARY**: mexiaosqwq/dsh-web-mobile（web 客户端插件/PWA，隔离实例验证通过；按用户要求未装入生产 profile）
- **REJECTED**: saya-ch（版本门禁拒 0.1.1-rc.2）、kelai141（内嵌 Termux 形态不符）、woaiys3（Shizuku 形态不符）、Hakunm（AGPL）、dphmoblie/knGear/xiaowei（形态/证据不足）
- 详见 docs/SELECTION.md、docs/AUDIT.md

## 2. Architecture

\`\`\`
Android 手机（原生 App / 浏览器 PWA）
   │  HTTP POST /api/*（JSON-RPC 信封）+ WS /api/events.mux
   ▼
NAS dsh web 0.1.1-rc.2（192.168.5.16:3081，信任围栏）
   ├── 现有 web/profile（未改动）
   └── SECONDARY 插件（隔离实例 3090 验证，未生产注入）
\`\`\`

## 3. Git

- repo: **llzg/dsh-mobile**（GitHub canonical → Gitea mirror → Woodpecker）
- branch: main；**HEAD: 9998db84bd39173e4b5cf7a49576a6f6a4279c78**
- upstream: https://github.com/sorsama/deepseek-harness-mobile.git @ 04483700716fdac96ef5c4580717ca9e3c0eaaa4
- 本地镜像：/root/nas_docker/dsh-mobile（独立 git repo，upstream remote 保留）

## 4. DSH

- version 0.1.1-rc.2；profile: web（/data/dsh/profiles/web）；endpoint http://192.168.5.16:3081
- 协议实测 30/30（session/streaming/tool/goal/subagent/skills/models/approval/export）

## 5. Mobile

- PRIMARY: Android 8.0+ APK，聊天/streaming/goals/approvals/subagents/jobs/通知/LAN 发现；Settings→About 显示版本 + Build revision（git SHA 内嵌 dex 已验证）
- SECONDARY: 单栏+抽屉+FAB 移动壳（<768px），360/390/412/430 无横向溢出、0 console error（headless 实测）
- 生产 dsh 零改动（web 3080/3081/3082 全程健康；cordis.patch.yml 未动）

## 6. CI（Woodpecker，本地）

- .woodpecker/verify.yml（push/cron/manual）：clone(Gitea mirror) → idempotency → JDK17 → Android SDK(缓存) → unit-tests → lint → assembleDebug/Release → publish
- .woodpecker/bench.yml（manual，COLD=1 清缓存）：cold/warm 计时
- .woodpecker/release.yml（manual 门禁）：assemble-release → cd-publish（Gitea release + deploy-status + healthcheck）
- 缓存：/volume1/docker/dsh-mobile/ci-cache（gradle + android-sdk）
- 真实排障记录（CI 门禁真实拦截）：release.yml 引号编译错、volumes 信任级、SDK 镜像缺 unzip、gradle 直连 0B/s（代理+预置修复）、MissingTranslation×11 语言、Woodpecker 不展开 env 值、Maven Central 403（Aliyun 镜像）

## 7. CD

- artifact store: /volume1/docker/dsh-mobile/releases（版本化 APK + SHA256SUMS + build-info + deploy-status）
- **Gitea release: v0.8.0-build0-9998db8**，asset: dsh-mobile-0.8.0+9998db8.apk（14.8MB unsigned，无 keystore；有 keystore 时自动签名）
- **下载 URL 实测 200**：http://192.168.5.35:3000/llzg/dsh-mobile/releases/download/v0.8.0-build0-9998db8/dsh-mobile-0.8.0+9998db8.apk
- **SHA256 一致**：817b94162af89ae75342359916ba674b5e57b6ea94ac45774f2706c8a16dcf30
- deploy-status.json / build-info.json 为真实 CI/CD 数据（非 mock）；移动端状态页 nas-cd/status/ 读取之

## 8. Latency（实测）

| Stage | Time | 说明 |
|---|---|---|
| Push → Gitea mirror（T0→T1） | **+27s ~ +56s** | 自动化 mirror-sync cron（每分钟）；多轮实测 27/40/56s |
| Gitea → Pipeline created（T1→T2） | **~0-32s** | auto-verify cron 相位对齐时几乎即时；T0→T2 = +10s/+32s/+45s |
| Queue | 秒级（正常态） | 本次因早期配置问题导致多轮积压（见报告事故记录） |
| Build（warm 全流程） | **106s** | pipeline 165：tests 17s + lint 19s + assemble 38s |
| Build（cold，同 revision 5abdc17） | **unit-tests 159s + assemble 184s** | bench COLD=1 实测 |
| Build（warm，同 revision） | **unit-tests 11s + assemble 18s** | bench 实测 |
| Test | 11s（warm）/ 159s（cold） | :core:test :mock-harness:test :app:testDebugUnitTest |
| Publish | 2s | artifact store + build-info |
| Deploy（CD） | ~10s | Gitea release + asset + deploy-status |
| End-to-End（push→可用 APK） | 正常态 ≈ **2-3 分钟** | 排除排障期的队列积压 |

## 9. Revision 一致性（最终 revision 9998db8）

- Source（GitHub HEAD）: **9998db84bd39173e4b5cf7a49576a6f6a4279c78**
- CI（Woodpecker commit）: 9998db8 ✓（pipeline #396 记录）
- Artifact（APK 文件名 + build-info.json）: 9998db8 ✓
- Running（deploy-status.runningRevision）: 9998db84bd39173e4b5cf7a49576a6f6a4279c78 ✓
- UI/App（APK dex 内嵌 BUILD_REVISION）: 9998db8 ✓（dex 二进制 grep 验证 2 处）
- Gitea mirror HEAD = GitHub HEAD = 9998db8 ✓
- **REVISION_CONSISTENCY_PASS = YES**

## 10. 验收

\`\`\`
LOCAL_CI_TRIGGER_PASS     = YES   （push→Gitea mirror→Woodpecker 自动触发，T0→T2 ≈ 10-45s）
LOCAL_CI_BUILD_PASS       = YES   （verify 流水线全绿：unit/lint/assemble 通过，cold 159s/warm 11s）
LOCAL_CD_PASS             = YES   （Gitea release + 下载 URL 200 + SHA256 一致 + deploy-status）
REVISION_CONSISTENCY_PASS = YES   （Source=CI=Artifact=Running=APK 内嵌 = 9998db8）
ANDROID_E2E_PASS          = PARTIAL（APK 构建+签名条件、dex 内嵌 revision 已验证；本环境无 Android 设备/模拟器，真机运行未实测——诚实标注）
DSH_MOBILE_USABLE         = YES   （协议级 30/30 + APK 产物 + SECONDARY 插件移动壳实测可用）
\`\`\`

## 11. 存储事故记录（本次任务期间发现并处理）

- **现象**：/volume1 达 100%（剩 6.1G）
- **根因**：woodpecker-server 容器 Docker json.log 膨胀至 **978GB**（WOODPECKER_LOG_LEVEL=debug + 本会话约 200 次流水线活动产生大量日志；日志无轮转上限）
- **处理**：截断日志（978GB→62KB）+ 为 woodpecker-server/agent 增加日志轮转（json-file 50m×3）+ 重建容器；**释放 978GB，/volume1 恢复 48%（986G 空闲）**
- **结论**：本次会话的流水线活动是触发因素（用户判断正确）；机制缺陷为日志无上限 + debug 级。已修复并防止复发（compose 已备份：compose.yaml.bak-*）
- 建议后续：WOODPECKER_LOG_LEVEL 降至 warn（生产态）；定期检查 docker system df
