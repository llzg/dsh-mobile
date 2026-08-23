# Workflow Event Policy — nas-ci-platform（CRON IS INFRASTRUCTURE-ONLY）

原则：**cron 只能用于 mirror/sync/maintenance**；业务 workflow
（verify/release/bench）不得继承 cron。release 必须经显式人工门禁。

## 规则表（语义 workflow 名 = .woodpecker 文件名 stem）

| workflow | when.event 允许 | when.event 禁止 | 门禁 |
|---|---|---|---|
| mirror-sync | cron（必含）、manual（可选） | push、pull_request | 无（基础设施） |
| verify | push、pull_request、manual | **cron** | 无（自动） |
| release | **deployment**（必含，人工门禁） | cron、push、pull_request、manual | deployment event（显式人工 deploy） |
| bench | manual | cron、push、pull_request | 无（受控执行） |

## Release 人工门禁（Woodpecker 3.17.0 实测能力）

- 3.17.0 **没有 workflow-level approval**（yaml schema 无 `require: approval` 字段）。
- 官方原生显式动作 = **deployment event**：人类对某条已验证流水线执行
  "deploy"（UI）或 `POST /api/repos/{id}/pipelines/{n}?event=deployment&deploy_to=<env>`。
- 因此 release 只匹配 deployment；普通 manual / cron / push / pull_request
  流水线**不会创建** release workflow（比 pending 更严格：不存在即不执行）。
- `NATIVE_APPROVAL_UNAVAILABLE=true`，`FALLBACK_GATE=deployment-event`。
- 非 prod deploy target 的 release 产物标记 `-test-<env>` + draft（Gitea release），
  不污染正式版本序列；仅 `deploy_to=prod`（或缺省）产生正式 release。

## 审计与门禁

- 静态审计：`scripts/ci/audit-workflow-events.sh`（PyYAML 解析 when.event，
  支持 inline `[a, b]` 与 block list 两种写法）→ `WORKFLOW_EVENT_POLICY_PASS=YES/NO`。
- 接入点：
  1. `scripts/ci/preflight.sh`（check #11）—— FAIL → `CI_AUTOMATION_READY=NO`；
  2. `verify.yml` 早期 `workflow-policy` 步骤 —— 策略违规在 build 前数秒内失败。
- 严重级别：
  - CRITICAL：release 含 cron
  - HIGH：verify 含 cron；release 含 push/pull_request；release 缺人工门禁
  - MEDIUM：bench 含 cron；dead mirror target
  - INFO：manual event 存在（符合受控执行语义）

## 2026-08-23 治理记录

- release.yml：`event: [manual]` → `event: [deployment]`（repo allow_deploy=true）。
- verify.yml：新增早期 `workflow-policy` 步骤。
- mirror-sync.sh：2026-08-23 曾误判 `llzg/invoice-agent-ui` 为 dead（root token 对
  私有 mirror 返回 404 是权限假象）。已修正：Gitea mirror 存在且为私有（admin token
  可见），目标已恢复；audit-mirror-targets.sh 改用 admin token 判定存在性。机制不变。
- 新增 `scripts/ops/audit-mirror-targets.sh`（MIRROR_TARGET_AUDIT=PASS/FAIL）。
