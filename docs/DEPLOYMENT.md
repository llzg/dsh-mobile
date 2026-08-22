# 部署与回滚说明

## PRIMARY（原生 APK）

1. 构建：Woodpecker verify pipeline（push/cron 自动）→ assembleDebug + assembleRelease
2. 发布：release pipeline（manual 门禁）→ 签名 APK + latest.apk → Gitea release asset + artifact store
3. 手机安装：下载 \`latest.apk\`（或 \`dsh-mobile-<VER>+<sha7>.apk\`）→ 打开连接 \`http://192.168.5.16:3081\`（Local network 模式）
4. 回滚：保留历史版本 APK（版本化命名），装回上一版本即可；artifact store 保留全部 build-info

## SECONDARY（web 插件）

1. 隔离验证：\`/root/nas_docker/dsh-test\` 测试 profile（DSH_HOME 覆盖），通过后（兼容性+回滚验证完成）才允许进入生产
2. 生产安装（未来，需用户批准 + 先备份 cordis.patch.yml）：\`dsh plugin --profile web add <pkg>\` → 重启 web → 验证
3. 回滚：\`dsh plugin --profile web remove <pkg>\`（插件机制卸载，非修改源码）

## 生产 dsh 保护

- 本次所有操作不修改 \`/data/dsh/profiles/web/*\`；如需修改（未来装 SECONDARY），先备份 \`cordis.patch.yml\` + \`package.json\`，验证后提供 rollback

