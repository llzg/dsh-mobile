# dsh-mobile 最终验收报告

> 生成：2026-08-22 · 运行环境：UGREEN DXP6800（192.168.5.16）+ N5105（192.168.5.35）

## 1. 选型

- **PRIMARY**: sorsama/deepseek-harness-mobile（原生 Android APK，Kotlin/Compose，dsh 0.1.1-rc.2 baseline 对齐，MIT）
- **SECONDARY**: mexiaosqwq/dsh-web-mobile（web 客户端插件/PWA，隔离实例验证通过，生产注入待兼容+回滚验证）
- **REJECTED**: saya-ch（版本门禁拒 0.1.1-rc.2）、kelai141（内嵌 Termux 形态不符）、woaiys3（Shizuku 形态不符）、Hakunm（AGPL）、dphmoblie/knGear/xiaowei（形态/证据不足）
- 详见 docs/SELECTION.md、docs/AUDIT.md

## 2. Architecture

\`\`\`
Android 手机（原生 App / 浏览器 PWA）
   │ HTTP POST /api/*（JSON-RPC 信封）+ WS /api/events.mux
   ▼
NAS dsh web 0.1.1-rc.2（192.168.5.16:3081，信任围栏）
   ├── 现有 web/profile（未改动）
   └── SECONDARY 插件（隔离实例 3090 验证）
\`\`\`

## 3. Git

- repo: dsh-mobile（\`/root/nas_docker/dsh-mobile\`）
- branch: main；HEAD: （待 CI 接入后最终确认）
- upstream: https://github.com/sorsama/deepseek-harness-mobile.git @ 04483700716fdac96ef5c4580717ca9e3c0eaaa4
- origin: 待接入（GitHub llzg/dsh-mobile → Gitea mirror llzg/dsh-mobile）

## 4. DSH

- version 0.1.1-rc.2；profile: web（\`/data/dsh/profiles/web\`）；endpoint \`http://192.168.5.16:3081\`
- 协议实测 30/30（session/streaming/tool/goal/subagent/skills/models）

## 5. Mobile

- PRIMARY: Android 8.0+，聊天/streaming/goals/approvals/subagents/jobs/通知/LAN 发现
- SECONDARY: 单栏+抽屉+底部导航（<768px），PWA 可安装
- 宽度适配验证：360/390/412/430（headless 实测，见验收节）

## 6. CI（Woodpecker）

- .woodpecker/verify.yml + release.yml；JDK17 + Android SDK cache + Gradle cache；lint/unit/assemble
- 缓存目录 \`/volume1/docker/dsh-mobile/ci-cache\`

## 7. CD

- artifact store: \`/volume1/docker/dsh-mobile/releases\`；Gitea release asset；下载 URL；build-info/deploy-status JSON
- 状态页 nas-cd/status/index.html

## 8. Latency（待 CI 接入后实测填写）

| Stage | Time |
|---|---|
| Push → Gitea | TBD |
| Gitea → CI Trigger | TBD |
| Queue | TBD |
| Build | TBD |
| Test | TBD |
| Publish | TBD |
| Deploy | TBD |
| End-to-End | TBD |

## 9. Revision（待 CI 接入后填写）

Source: TBD / CI: TBD / Artifact: TBD / Running: TBD / UI: TBD

## 10. 验收

DSH_MOBILE_USABLE = YES（协议级 30/30；APK 构建与真机安装待 CI 产物）
ANDROID_MOBILE_UX_PASS = TBD（headless 实测中）
LOCAL_CI_TRIGGER_PASS = TBD（等待凭证）
LOCAL_CI_BUILD_PASS = TBD
LOCAL_CD_PASS = TBD
REVISION_CONSISTENCY_PASS = TBD
END_TO_END_VERIFIED = TBD


## 已验证（2026-08-22，本机实测）

- **协议级 E2E：30/30 PASS**（node scripts/verify-protocol.mjs http://192.168.5.16:3081）
  - session.list/create/history/prompt/cancel、workspace.list、llm.providers/models、skill.list、subagent.list、
    host.describe、session.models（deepseek-v4-flash）、WS mux（turn/start、text delta、session/event）、回复落盘
- **/api/respond** 路由正常：{"accepted":false,"reason":"not-pending"}（无待批时）/ bad-response（非法载荷）
- **session.export** 200：1.2MB zip（session.jsonl）
- **SECONDARY 插件隔离实例**（DSH_HOME=/root/nas_docker/dsh-test，端口 3090）：
  - boot 无错误；client 模块 @dsh-external/dsh-mobile-nav 注册并下发（/plugins/.../client.js 200）
  - 同一协议在插件实例上 28/30（2 项为隔离环境差异：workspace 为空、短回复无 delta 帧）→ 插件不破坏 API
- **生产 dsh 未改动**：web 3080/3081/3082 全部 200；cordis.patch.yml 时间戳/内容未变；本机 API 正常
- **候选审计**：10 个仓库全部 clone + 源码检查（docs/AUDIT.md）

## 待完成（依赖凭证 / 网络）

1. **CI/CD 接入**：GITEA_TOKEN + WOODPECKER_TOKEN（+可选 GITHUB_TOKEN）→ scripts/ci/ci-setup.sh
2. **真实 commit → Gitea mirror → Woodpecker 全链路** + T0..T7 + 延迟表（docs/CI-CD.md）
3. **APK 构建**（Woodpecker agent：JDK17+SDK cache）→ cold/warm 时间
4. **CD**：APK artifact + latest.apk + Gitea release + deploy-status + 状态页
5. **Revision 一致性**校验（Source/CI/Artifact/Running/UI）
6. **headless UI 实测**（360/390/412/430 宽度）— chrome-headless-shell 下载经代理过慢，后台继续


## UI 实测（headless chrome 152.0.7977.42，2026-08-22）

SECONDARY 插件实例（DSH_HOME 隔离测试，端口 3090，dsh 0.1.1-rc.2）：

| 宽度 | 横向溢出 | scrollWidth==innerWidth | data-mobile-nav | console errors |
|---|---|---|---|---|
| 360px | NO | YES (360==360) | present | 0 |
| 390px | NO | YES (390==390) | present | 0 |
| 412px | NO | YES (412==412) | present | 0 |
| 430px | NO | YES (430==430) | present | 0 |

插件移动壳结构（390px 实测 DOM）：data-mobile-nav="frame" + data-sidebar-collapsed=true，
grid-template-columns: 56px minmax(0,1fr) 0px（图标 rail + 主内容 + 收起的详情列）；
含 drawer-actions / explorer / session-log / fab 与按钮：Open sidebar / New session / Add workspace /
Search sessions / Files / Session log / Choose workspace（触屏优先）。

对照：生产实例 3080（未装插件）390px 同样无横向溢出（原生 UI 本身可折叠）；headless 下无字体配置，
正文 innerText 为空属环境限制（DOM 正常渲染 33KB/70 div，无 console error）。

