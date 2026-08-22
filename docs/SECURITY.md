# 安全说明（dsh-mobile / 移动访问）

## 现状（审计结论）

- dsh web 已在 LAN 绑定 0.0.0.0:3080（宿主 3081）——这是生产既有配置（cordis.patch.yml），非本次引入
- \`/api\` 信任围栏：Host ∈ (loopback ∪ trustedHost 192.168.5.16) 且浏览器 Origin==Host；
  高风险方法（settings/credentials/llm.discoverModels/host.openPath/host.pickDirectory/agentPreset）钉死 loopback
- **无应用层鉴权**：LAN 内任意设备可驱动会话（生产既有状态）。手机 App 的"Local network"直连即基于此。
  仅在可信家庭/办公 Wi-Fi 使用；不要暴露公网。

## 本项目的安全边界

- 不新增端口/监听；不修改 dsh 核心；SECONDARY 插件在隔离实例验证，未通过前不装生产
- CI secrets（GITEA_TOKEN / WOODPECKER_TOKEN / DSH_KEYSTORE*）只存 Woodpecker secrets 与 600 权限文件，不入 Git
- CD 只写 \`/volume1/docker/dsh-mobile/\*（artifact/cache）\`，不动生产 dsh 数据

## 建议（后续可选项，非本次范围）

1. 生产安装 \`dsh-relay\`（sorsama 配套）→ 认证 + 证书固定，替代裸 LAN 直连
2. 或前置 HTTPS 反向代理（仅加密，不鉴权——需配合 relay/其他认证）
3. 手机丢失时撤销配对设备（relay 场景）

