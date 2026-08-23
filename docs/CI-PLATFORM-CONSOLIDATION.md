# CI Platform Consolidation — nas-ci-platform

本仓库的本地 CI/CD 由统一 Compose 项目 `nas-ci-platform` 管理（2026-08-23）：

- **宿主机 192.168.5.16 (DXP6800PRO)**：`woodpecker-server` + `woodpecker-agent`
  统一于 `/opt/docker/infra/nas-ci-platform/compose.yaml`（project name: `nas-ci-platform`）。
- **宿主机 192.168.5.35 (Synology)**：Gitea (:3000) + Registry (:5050) 保持原部署，
  参考配置见 `nas-ci-platform/hosts/syno-192.168.5.35/`。
- 数据全部 bind 复用（`/opt/docker/infra/woodpecker/{data,agent}`），零迁移；
  禁止 `docker compose down -v`。
- 镜像固定为已验证 digest（woodpecker 3.17.0），本轮不做版本升级。
- 触发链路不变：GitHub push → Gitea mirror → Woodpecker cron(mirror-sync)
  → `ci-trigger.sh` → 每个新 commit 恰好 1 条流水线。
- `verify.yml` 触发集 = push / pull_request / manual（cron 仅跑 mirror-sync，
  防止队列淹没）。release/bench 仅在 manual 事件运行（自动触发时会运行，
  产物为未签名 APK，签名密钥不在 CI 内）。

## Event Governance（2026-08-23 追加）

- CRON IS INFRASTRUCTURE-ONLY：cron → mirror/sync/maintenance only；
  verify = push/pull_request/manual；release = deployment（显式人工门禁）；bench = manual。
- 静态审计：`scripts/ci/audit-workflow-events.sh`（WORKFLOW_EVENT_POLICY_PASS），
  接入 preflight（FAIL→CI_AUTOMATION_READY=NO）与 verify 早期 workflow-policy 步骤。
- release 人工门禁：3.17.0 无 workflow-level approval → FALLBACK_GATE=deployment-event
  （NATIVE_APPROVAL_UNAVAILABLE）；非 prod deploy 产物标记 -test + draft。
- 本 commit 后执行事件门禁验证回归（见流水线：verify 唯一、cron 不触发 release/bench）。

## Event-driven trigger（2026-08-23 PoC）

- 目标：Gitea mirror 更新 → webhook → relay → Woodpecker API（EVENT primary，
  POLLING fallback）。本 commit 用于验证 mirror 更新是否产生 webhook push 事件。
- event-trigger 验证 2（hook 存在时 mirror 更新）
- event-primary trigger 上线验证（sync 后立即 invoke ci-trigger）
- polling fallback 验证（event path down 时 cron 补触发）
