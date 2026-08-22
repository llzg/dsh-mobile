# 选型（PRIMARY / SECONDARY / REJECTED）

## PRIMARY — sorsama/deepseek-harness-mobile（原生 Android APK）

- **唯一与当前 dsh 0.1.1-rc.2 基线明确对齐的候选**（COMPATIBILITY.md 0.8.0 ↔ 0.1.1-rc.2 Supported baseline）
- 原生 Kotlin + Jetpack Compose，Android 8.0+；完整功能面：会话/streaming、goals（phase/round/pause/resume）、
  approvals、questions、subagents、background jobs、plan mode、模型选择、通知（前台服务）、LAN 自动发现、11 语言
- 零改动 dsh 核心：纯客户端；可选 dsh-relay 插件做鉴权（不装也不影响 LAN 直连）
- 许可 MIT；上游活跃（最后提交 2026-08-22）；release 含可下载 APK + SHA256SUMS
- 协议耦合由上游 baseline 政策管理（每次 dsh 发版跟进）；本仓库镜像保留 upstream，升级 = merge upstream
- CI/CD 形态与验收目标完全匹配：JDK + Android SDK + Gradle cache + lint + unit test + assemble + APK artifact

## SECONDARY — mexiaosqwq/dsh-web-mobile（web 客户端插件 / PWA）

- 手机浏览器直接用（无安装 APK），同源（dsh web origin）通过信任围栏
- v2.0.0 显式支持 0.1.1 rc（peer 范围 \`>=0.1.1-rc.0 <0.2.0\`）
- **隔离实例实测**：测试 profile boot 无错误、client 模块注册并下发（本仓库 docs/ 记录）
- 按用户要求：完成兼容性 + 回滚验证前**不修改生产 web profile**
- 插件形态 → 不 fork 核心，dsh 升级维护成本低（仅 peer 依赖范围检查）

## REJECTED（附原因）

| 候选 | 原因 |
|---|---|
| saya-ch/dsh-mobile（106★） | alpha；官方兼容表只到 0.1.0-rc.7，自带版本门禁会在 0.1.1-rc.2 拒绝启动 |
| kelai141/dsh-mobile-apk（119★） | 内嵌 Termux 运行时把 Harness 跑在手机上；与本 NAS 场景不符，维护面大 |
| woaiys3/deepseek-harness-android-app（88★） | 侧重 Shizuku 免 Root 控制手机；形态不符 |
| Hakunm/dsh-android-app（7★） | AGPL-3.0 + 不成熟（最后提交早） |
| dphmoblie/deepseek-harness-android | 管理"手机上的 Harness"，形态不符 |
| jasondu/dsh-ui-mobile（4★） | 与 mexiaosqwq 同族但更小；mexiaosqwq 有显式 0.1.1-rc 支持与更大覆盖 |
| knGear/dsh-mobile（0★） | 无文档无证据 |
| xiaowei2025cqu23phy/dsh-desktop | 桌面端，与移动目标无关 |

## 架构（非侵入）

\`\`\`
Android 手机（原生 App / 浏览器）
   │  HTTP POST /api/*  +  WS /api/events.mux
   ▼
NAS DSH web（0.1.1-rc.2，3081；信任围栏：Host∈trusted + Origin==Host）
   ├── existing web/profile（不变）
   └── SECONDARY: dsh-web-mobile 客户端插件（隔离验证后可选注入）
\`\`\`

## 为什么不是"fork dsh + 大改官方 web"

- 无任何候选需要改 dsh 核心；web 客户端插件族证明官方已把 UI 做成可插拔（client modules + slots）
- fork 会引入每次升级手工 merge 的成本，与本项目"复用社区轮子 + 本地 CI 验收"目标相悖

