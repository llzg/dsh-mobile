# CI 平台工程化收尾与防复发治理报告

> 时间：2026-08-23 · 背景：dsh-mobile 接入暴露的机制缺陷（cron 提前开启 → 幂等失效 → pipeline 洪泛 → 单 agent 队列堆积 → debug 日志暴涨 → Docker json.log 吃满磁盘 978GB）

## 治理前状态（审计确认）

| 项 | 治理前 | 治理后 |
|---|---|---|
| WOODPECKER_LOG_LEVEL | debug（server+agent） | **info**（运行容器 env 实测） |
| Docker logging | json-file 无上限 → **978GB 事故** | json-file **max-size=50m max-file=3**（实测生效） |
| Agent capacity | 1 | 1（单 agent，串行） |
| auto-verify cron（dsh-mobile） | */1 盲目创建 pipeline | **已停用**（由新 commit 触发器接管） |
| mirror-sync cron（dsh-mobile） | 仅同步 | 同步 + **新 commit 触发 verify** |
| 幂等 | artifact glob（脆弱） | **Woodpecker API + state 文件**（repo+commit+pipeline） |
| 触发锁 | 无 | **flock**（持久卷 lockfile） |
| 洪泛守卫 | 无 | **pending > 10 → SKIP**（单 agent 保守值） |
| 磁盘守卫 | 无 | **>=95% STOP / >=90% CRITICAL / >=80% WARNING** |
| 队列审计 | 无 | scripts/ops/audit-ci-queue.sh（重复触发检测） |
| 日志审计 | 无 | scripts/ops/audit-docker-logs.sh（unlimited → RISK） |
| 缓存治理 | 无上限 | scripts/ci/cache-gc.sh（默认 4GB cap） |
| 接入流程 | 边调边开 cron | **docs/ci-onboarding-sop.md**（manual bring-up → 3 PASS → 预检 → enable cron） |

## 触发链（治理后）

```
Git push → Gitea mirror（宿主 mirror-sync.timer，每分钟；未改动）
  → Woodpecker mirror-sync cron（每分钟；sync + ci-trigger.sh）
      → 检测 mirror HEAD 与 state 文件差异（flock + disk + flood guard）
          → 有未处理 commit → 创建 1 个 verify pipeline（API POST）
          → 无新 commit → NO_NEW_COMMIT（不创建）
  → verify pipeline 内 guard 步骤（API 幂等，双保险）
      → 同 SHA 已有 pipeline → SKIP（.ci-skip 秒级退出）
```

## 回归实测（Test SHA: 70c80dc）

- push 后 verify pipeline 数量（排除 mirror-sync）：**1**（#914）
- 连续 3+ 个 cron 周期：**无重复 verify pipeline**
- #914 端到端：guard TRIGGERED → unit-tests/lint/assemble/bench/release-CD 全绿
- 后续周期 ci-trigger 输出：NO_NEW_COMMIT

## 异常场景验证

| Case | 场景 | 结果 |
|---|---|---|
| 1 | pipeline running，cron 再扫描 | NO_NEW_COMMIT（state 机制，与状态无关）PASS |
| 2 | pipeline success，再扫描 | NO_NEW_COMMIT PASS |
| 3 | 并发两次触发 | 仅 1 个获锁，另 1 个 SKIPPED_LOCKED PASS |
| 4 | pending > 阈值 | SKIPPED_FLOOD pending=11 limit=10 PASS |

## 最终配置

```
WOODPECKER_LOG_LEVEL=info
LOG_MAX_SIZE=50m
LOG_MAX_FILE=3
AGENT_CAPACITY=1
CRON_INTERVAL=*/1（mirror-sync；auto-verify 已停用，改为事件驱动）
IDEMPOTENCY=API+state（scripts/ci/ci-trigger-guard.sh + ci-trigger.sh）
LOCK=flock -n
FLOOD_GUARD=pending>10 STOP
DISK_WARNING=80% / DISK_CRITICAL=90% / CI_STOP=95%
MANUAL_FIRST=YES（SOP 强制）
MIN_MANUAL_PASS=3
AUTO_ENABLE_GATE=preflight.sh CI_AUTOMATION_READY=YES
```

## 验收

```
WOODPECKER_LOG_GOVERNANCE_PASS = YES
CI_TRIGGER_IDEMPOTENT          = YES
CI_TRIGGER_LOCK_PASS           = YES
CI_FLOOD_GUARD_PASS            = YES
CI_DISK_GUARD_PASS             = YES
CI_ONBOARDING_SOP_READY        = YES
DSH_MOBILE_REGRESSION_PASS     = YES（70c80dc：1 个 verify pipeline，3+ 周期无重复，构建全绿）
CI_PLATFORM_HARDENED           = YES
```

## 遗留/建议

- invoice-agent 仍使用原生 */1 auto-verify cron + 其自有幂等（registry+API 双检查）；建议后续按 SOP 评估是否迁移到 ci-trigger 模式（未擅自改动其生产配置）。
- mirror-sync cron 每分钟产生 1 条 mirror-sync pipeline 记录（~20-30s no-op）——机制必要开销；如在意记录噪音，可将 mirror-sync 也移到宿主 timer（本次保持最小改动）。
- WOODPECKER_LOG_LEVEL 需要更细排障时可临时切回 debug（有轮转兜底）。
