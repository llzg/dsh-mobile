# 环境审计与候选项目调研（2026-08-22）

## 1. 环境审计结果（实测，非假定）

| 项 | 值 |
|---|---|
| dsh 安装目录 | \`/usr/local/lib/node_modules/@deepseek-ai/dsh\`（全局 npm），二进制 \`/usr/local/bin/dsh\` |
| dsh 版本 | \`0.1.1-rc.2\`（\`dsh --version\`） |
| DSH_HOME | \`/data/dsh\`（\`env DSH_HOME\`；无 \`~/.dsh\`，默认路径不存在） |
| web profile | \`/data/dsh/profiles/web\`（cordis.yml + cordis.patch.yml + pnpm workspace） |
| profile bundles | dsh-base, dsh-web-app, dsh-subagent-codex, dsh-subagent-claude-code |
| 现有插件 | dsh-subagent-codex / dsh-subagent-claude-code（web profile）；无移动端插件 |
| dsh web 启动 | 容器 PID1：\`node /usr/local/bin/dsh --profile web --trusted-host 192.168.5.16 --no-open\`（0.0.0.0:3080，宿主机 3081） |
| 版本信息页 | 3082 端口（/opt/version-server.js，镜像内嵌） |
| Docker | 本容器无 docker CLI/未挂 docker.sock（nas-docker-ops 技能已知） |
| systemd/compose | NAS 宿主：\`/volume1/docker/deepseek-harness\`（compose 项目 deepseek-harness，/root/nas_docker/nas/docker-compose.yml 为镜像构建侧参考） |
| Gitea | \`http://192.168.5.35:3000\`（N5105，v1.22.6；匿名 API 受限） |
| Woodpecker | \`http://192.168.5.16:8010\`（DXP6800；GitHub forge，UI+API；18001 为另一入口） |
| Local Registry | \`192.168.5.35:5050\`（N5105；\`/v2/\` 401 → 需登录） |
| 代理 | \`http://192.168.5.36:7893\`（容器 git 已配置；CI 步骤沿用） |
| 现有 CI/CD 工作流 | GitHub（source of truth）→ Gitea mirror（N5105，1min 等效同步）→ Woodpecker cron 触发 → 本地构建 → Registry artifact → 手动部署门禁（phase344 已 cutover） |

## 2. dsh Web 客户端协议（实测确认）

- 非 /api 路径 404；POST \`/api/<method>\`（JSON-RPC 信封 \`{type, rpcId, method, payload}\`）→ \`{type:"server-response", rpcId, result:{ok,value|error}}\`
- 事件流：WS \`/api/events.mux\`（浏览器用 WebSocket；GET 无 upgrade 返回 426）
- 审批响应：POST \`/api/respond\`（client-response 信封）
- **信任围栏**：/api 路由校验 Host ∈ (loopback ∪ trustedHosts=192.168.5.16) 且浏览器 Origin == Host；\`settings.* / credentials.* / llm.discoverModels / host.openPath / host.pickDirectory / agentPreset.*\` 钉死 loopback（LAN 403）
- 实测 30/30 PASS（scripts/verify-protocol.mjs）：session.list/create/prompt/history、workspace.list、llm.providers/models、skill.list、subagent.list、host.describe、WS mux（turn/start、delta、session/event）、session.cancel

## 3. 候选项目 GitHub 审计（全部实际 clone + 源码检查）

| 项目 | Star | License | 语言 | 最后提交 | 形态 | 结论 |
|---|---|---|---|---|---|---|
| sorsama/deepseek-harness-mobile | 31 | MIT | Kotlin | 2026-08-22 | 原生 Android App（Compose）+ 可选 dsh-relay 插件 | **兼容 0.1.1-rc.2（baseline 明确）** |
| mexiaosqwq/dsh-web-mobile | 42 | MIT | TS | 2026-08-22 | web 客户端插件（client bundle + reconciler） | v2.0.0 peer 范围含 0.1.1-rc；隔离实例实测可加载 |
| jasondu/dsh-ui-mobile | 4 | MIT | TS | 2026-08-18 | web 客户端插件（drawer/bottom-nav/PWA/WebPush） | 与 mexiaosqwq 同族，star/功能较少 |
| saya-ch/dsh-mobile | 106 | Apache-2.0 | TS | 2026-08-19 | Host+Client+Android WebView 壳插件 | alpha；官方兼容表只到 0.1.0-rc.7，版本门禁会拒 0.1.1-rc.2 |
| kelai141/dsh-mobile-apk | 119 | MIT | Kotlin | 2026-08-22 | WebView 壳 APK + 内嵌 Termux 运行时 | 形态是把 Harness 跑在手机上，与本 NAS 场景不符 |
| woaiys3/deepseek-harness-android-app | 88 | MIT | Java | 2026-08-21 | APK + mobile-patch 注入 | 侧重 Shizuku 控制手机，形态不符 |
| Hakunm/dsh-android-app | 7 | AGPL-3.0 | Kotlin | 2026-08-14 | 原生客户端 | AGPL 许可 + 不成熟 |
| dphmoblie/deepseek-harness-android | 4 | MIT | TS | 2026-08-20 | Capacitor 壳 | 管理"手机上的 Harness"，形态不符 |
| knGear/dsh-mobile | 0 | MIT | JS | 2026-08-21 | 未明 | 无文档无证据 |
| xiaowei2025cqu23phy/dsh-desktop | 1 | 无 | TS | 2026-08-20 | 桌面端 | 与移动端目标无关 |

## 4. 关键兼容性证据

- sorsama COMPATIBILITY.md：0.8.0 ↔ 0.1.1-rc.2（Supported baseline）；协议子集记录在 PROTOCOL.md（含 commands/execute 的 rc.8 参数差异处理）
- sorsama release v0.8.0 含 app-release.apk（14.8MB）+ SHA256SUMS
- mexiaosqwq：隔离 DSH_HOME 测试 profile 实测 boot 无错误、client 模块注册并正常下发（/plugins/@dsh-external/dsh-mobile-nav/client.js 200）
- saya-ch 自带 check-dsh-compatibility 且 README 兼容表只到 0.1.0-rc.7 → 当前版本不可用（会拒绝启动）

