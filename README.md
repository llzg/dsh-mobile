# DSH Mobile (NAS 本地交付)

DeepSeek Harness 的 Android / 移动端界面，本地 CI/CD 端到端验收项目。

- **PRIMARY**：原生 Android APK（来自 upstream `sorsama/deepseek-harness-mobile`，
  当前基线 dsh `0.1.1-rc.2`）
- **SECONDARY**：移动端 web 插件（`mexiaosqwq/dsh-web-mobile`，隔离实例验证）
- **upstream**：https://github.com/sorsama/deepseek-harness-mobile.git
  HEAD `04483700716fdac96ef5c4580717ca9e3c0eaaa4`（2026-08-22）

> 上游仓库保留完整（本仓库 = 上游镜像 + 本地 CI/CD 层）。上游的 README 见
> [docs/UPSTREAM-README.md](docs/UPSTREAM-README.md)（构建、连接、功能说明）。

## 快速导航

- [docs/AUDIT.md](docs/AUDIT.md) — 环境审计与候选项目调研
- [docs/SELECTION.md](docs/SELECTION.md) — 选型 PRIMARY/SECONDARY/REJECTED
- [docs/CI-CD.md](docs/CI-CD.md) — 本地 CI/CD 接入与验收
- [scripts/verify-protocol.mjs](scripts/verify-protocol.mjs) — 协议级 E2E 验证
- [.woodpecker/](.woodpecker/) — Woodpecker 流水线
