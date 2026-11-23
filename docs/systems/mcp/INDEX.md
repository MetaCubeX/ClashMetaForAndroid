---
title: "本地 MCP"
type: index
status: accepted
phase: N/A
updated: 2026-10-24
summary: "本机 127.0.0.1 上的 MCP 服务，用令牌保护，可查询和启停隧道"
---

# 本地 MCP

实现在 `service/src/main/java/universe/agentic/device/clash/`，包名 `universe.agentic.device.clash`。设置页是 `McpSettingsActivity`，入口在设置 > 高级。页面依次是监听开关、一段用途说明、地址、令牌，以及八个工具的用途列表。这不是 mihomo 的 external-controller。应用仍会清掉配置里的外部控制器。

## 监听

`McpService` 跑在 `:background`，是 `specialUse` 前台服务，通知重要性为低。只绑定 `127.0.0.1`。默认端口和路径在 `McpProtocol`：`19090` 与 `POST /mcp`。其它方法返回 405。请求体上限 256KB，响应带 `Connection: close`。

打开监听时如果令牌还是空的，用 24 字节随机数写成十六进制，写进 `ServiceStore.mcpToken`。令牌不进日志，也不进通知。请求必须带 `Authorization: Bearer`。`Origin` 缺省时放行；带了就必须是 `127.0.0.1`、`localhost` 或 `[::1]`，否则 403。

开关、端口、令牌和状态在 `ServiceStore`：`mcp_enabled`、`mcp_port`、`mcp_token`、`mcp_status`。状态为 `listening` 表示正在监听；绑定失败时留下错误文本，服务随后退出。界面进程启动时，以及 `ProfileReceiver` 处理开机、换包和对时广播时，只要开关开着就拉起这个服务。

协议是 JSON-RPC。`initialize` 接受 `2025-06-18` 和 `2024-11-05`，并回显客户端给出的那一个；其它版本用 `2025-06-18`。没有 `id` 的通知返回 HTTP 202，不执行工具。解析失败是 HTTP 400、`-32700`。未知方法是 `-32601`。未知工具是 `-32602`。工具抛错时 HTTP 仍是 200，结果里 `isError` 为真。

## 工具

| 工具 | 作用 |
|:-----|:-----|
| `status` | 隧道是否在跑。在跑时带模式和上传、下载字节 |
| `list_proxies` | 代理组和当前选中的节点 |
| `select_proxy` | 参数 `group`、`name`。写入选择并让内核切换 |
| `list_profiles` | 已保存配置的 UUID、名称和类型。不含订阅地址 |
| `set_profile` | 参数 `uuid`。隧道停着也可以切换当前配置 |
| `reload` | 向本包广播重载 |
| `start` | 按界面里的 VPN 开关启动 `TunService` 或 `ClashService` |
| `stop` | 向本包广播停止 |

`status` 在隧道停着时返回 `running: false`。`list_proxies`、`select_proxy` 和 `reload` 要求隧道已在跑。`start` 在 VPN 模式下若 `VpnService.prepare` 还要用户同意，返回「先在 AgenticClash 里连接一次」，不会自己弹出系统同意框。从后台拉起前台服务若被系统拒绝，错误文本原样返回。

## 测试

`service/src/test/java/universe/agentic/device/clash/` 覆盖协议分支，以及回环 HTTP 的令牌、方法和 `Origin`。
