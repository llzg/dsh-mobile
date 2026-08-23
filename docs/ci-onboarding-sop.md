# CI Onboarding SOP（本地 CI/CD 新项目接入规范）

> 目标：任何新 repo 接入本地 Woodpecker CI 必须遵循"先手动、后自动"两阶段，
> 禁止边调试 pipeline 边开启每分钟自动 cron。
> 依据：dsh-mobile 实战事故（cron 提前开启 → 幂等失效 → pipeline 洪泛 → 单 agent 队列堆积 → 日志暴涨 → 磁盘打满）。

## 总原则

```
MANUAL BRING-UP → STABLE → IDEMPOTENCY VERIFIED → FLOOD GUARD VERIFIED → ENABLE AUTOMATION
```

- **cron 默认 OFF**，只有全部门禁通过才开启。
- 幂等判断使用 **Woodpecker API**（repo_id + commit_sha + pipeline），禁止脆弱 glob / 文本匹配。
- 触发脚本必须带 **flock 锁 + 洪泛阈值 + 磁盘阈值**（scripts/ci/ci-trigger-guard.sh）。

## Stage A — Bring-up（cron = disabled）

1. **repo 注册**：GitHub 建仓 → Gitea mirror → Woodpecker `repo add <forge-id>`；
   配置 clone 指向 Gitea mirror（secret GITEA_CLONE_URL）、仓库 trusted（volumes）。
2. **cron 保持 disabled**（`repo cron add --enabled=false` 或先不加）。
3. **manual bring-up**：`woodpecker-cli pipeline create <repo> --branch main` 手动触发，验证：
   - YAML 编译（`woodpecker-cli lint`）
   - 权限（trusted volumes/network）
   - SDK/依赖（镜像工具、网络路径：gradle 发行包走代理、Maven Central 用镜像）
   - lint / unit test / build / artifact
   - CD（release 产物、下载 URL、checksum）
   - revision consistency（Source=CI=Artifact=Running=App）
4. **连续 >= 3 次 manual PASS**（scripts/ci/preflight.sh 检查 consecutive-success >= 3）。
5. **幂等测试**：同一 commit 重复触发 guard → 必须 `SKIPPED_ALREADY_EXISTS`。
6. **洪泛测试**：pending > 阈值时 guard 必须 `FLOOD_GUARD_ACTIVE`。

## Stage B — Automated（cron = enabled）

全部满足才 `repo cron update --enabled=true`：

- manual PASS >= 3
- idempotency test PASS
- flood guard PASS
- artifact PASS
- `preflight.sh` 输出 `CI_AUTOMATION_READY=YES`

开启后首日观察：

- 每个新 commit 只产生 **1 个** verify pipeline（其余 cron 周期 SKIP）
- queue 深度 <= 2-3（无洪泛）
- 磁盘使用稳定（日志轮转生效）

## 平台守卫（所有 repo 通用）

| 守卫 | 实现 | 阈值 |
|---|---|---|
| 幂等 | scripts/ci/ci-trigger-guard.sh（API 查询 repo+commit+pipeline） | pending/running/success/failure/killed 均 SKIP |
| 锁 | flock -n（lockfile 持久卷/宿主 /run） | 并发扫描仅 1 个执行 |
| 洪泛 | 同上（pending 计数） | pending > 10 → STOP（单 agent 保守值） |
| 磁盘 | 同上（df） | >=95% STOP / >=90% CRITICAL / >=80% WARNING |
| 日志 | docker json-file max-size=50m max-file=3 | 全部 CI 容器强制 |
| 缓存 | 见 scripts/ci/cache-gc.sh | Gradle/SDK 上限 + 定期 GC |

## 运维脚本

- `scripts/ops/audit-docker-logs.sh` — 容器日志配置审计（unlimited → RISK）
- `scripts/ops/audit-ci-queue.sh` — 队列深度 / 重复触发检测
- `scripts/ci/preflight.sh` — 开 cron 前预检（CI_AUTOMATION_READY）
- `scripts/ci/cache-gc.sh` — 缓存清理（见缓存治理）

## 禁止事项

- 禁止 cron 开启状态下调试 pipeline 配置（先 manual）。
- 禁止用 artifact glob / 日志 grep 做幂等（用 API）。
- 禁止把 WOODPECKER_LOG_LEVEL 设为 debug 作为长期配置（info 即可，需更细排障时临时开）。
- 禁止无日志轮转运行 CI 容器。
- **CRON IS INFRASTRUCTURE-ONLY**：新项目开启 cron 自动化前，`verify/release/bench`
  不得包含 cron 事件；release 必须 deployment 门禁；preflight check #11
  （audit-workflow-events.sh）FAIL → CI_AUTOMATION_READY=NO。
- 禁止"一个 manual/cron event 顺手把全部 workflow 跑起来"——每个 workflow 必须
  显式声明允许事件集。
- **触发模型（2026-08-23 起）**：PRIMARY_TRIGGER=EVENT（systemd mirror-sync.sh：Gitea
  mirror sync 成功后立即调用 ci-trigger.sh）；FALLBACK_TRIGGER=POLLING（Woodpecker cron
  mirror-sync，flock+state+API 三重幂等，正常输出 NO_NEW_COMMIT）。注意：Gitea
  pull-mirror 更新不产生 webhook（实测），故不用 webhook relay，sync 后直接 invoke。
- 新增项目接入时保持同一 trigger-core（scripts/ci/ci-trigger.sh），JSON 解析用 tr
  （busybox 兼容），禁用手写 sed 换行。
