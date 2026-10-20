---
title: "UI 2.0 重构方案与交互式 Web 原型"
type: plan
status: proposed
phase: N/A
updated: 2026-10-20
summary: "网络感知优先、Bento 仪表盘、极速切节点与 5 Tab 架构方案，附带本地可运行 Web Demo"
---

# UI 2.0 重构方案与交互式 Web 原型

## 1. 痛点分析与重构动机

当前 ClashMetaForAndroid (ClashMetaX) 正在从早期的 Material 列表逐步迁移到 Jetpack Compose。虽然已在首页和设置中尝试了类似 iOS HIG 的卡片分组，但在真实使用体验中仍存在以下局限：

1. **信息架构层级深、路径长**：
   - 用户核心高频操作是“切节点”、“测速”、“看当前走了哪个出口”。目前选节点需要点击进入代理二级页、在多级策略组里翻找，操作链路繁琐。
2. **状态感知不足**：
   - 现有的上下行数据只有数值和简单折线，缺乏对网络健康度（出口 IP 真实归属、ISP 数据中心、实时延迟色阶、分流规则命中）的即时感知。
3. **视觉语言与现代移动端质感差距**：
   - 纯列表堆叠缺少层次感。现代顶级代理工具（Surge、Loon、Clash Verge Rev）普遍采用“Bento 仪表盘 + 沉浸流体状态”的现代美学。

## 2. UI 2.0 设计原则与四大核心支柱

### 2.1 状态感知优先 (Perception-First Dashboard)
- **呼吸式主控按键**：断开（灰静）、连接中（波纹环）、运行中（翡翠绿微光呼吸），一眼可知 VPN 状态与连续运行时长。
- **动态平滑波形图**：实时 60fps 动态正弦噪声 Canvas 折线，直观呈现瞬时吞吐与流量尖峰。
- **出站网络信息卡 (Outbound Perception)**：高亮展示当前生效出站节点、国家/地区国旗、出口公网 IP、机房归属及毫秒级延迟指示灯。

### 2.2 零路径极速操作 (Zero-Latency Switching)
- **首页极速切节点抽屉**：点击首页出口卡片直接从底部滑出 BottomSheet 节点快切列表，点选即时生效，无须跳转多级页面。
- **模式切换胶囊分段器**：规则 (Rule) / 全局 (Global) / 直连 (Direct) 采用 Segmented Control 触控反馈。

### 2.3 移动端 5 大核心 Tab 导航结构
- **⚡ 仪表盘 (Dashboard)**：状态主控、出站卡片、实时流量波形、订阅余量与分流快捷卡片。
- **🌐 节点选择 (Proxies)**：策略组横向滑动 Tab、Grid 宫格与 List 列表切换、一键并发测速与延迟重排。
- **📑 配置订阅 (Profiles)**：多订阅卡片管理、剩余流量进度条、到期倒计时预警、一键批量更新。
- **🔀 应用分流与访问控制 (Apps Strategy & Access Control)**：
  - **PRD-004 多策略方案**：多份命名策略独立存储，单选指定生效驱动 TUN 隧道；未启用策略时无缝回退至全局名单；支持新建、重命名、删除与白名单 (AcceptSelected) / 黑名单 (DenySelected) / 全局 (AcceptAll) 模式切换。
  - **PRD-003 应用集合 (App Sets)**：顶部系统应用与国内应用 (基于 domestic_packages.txt) 双集合；支持 Checked / Unchecked / Indeterminate (半选横杠) 三态呈现；点选集合未全选时选中全部当前成员，已全选时只取消该集合成员；隐藏系统应用仅影响行显示不改变集合成员。
- **⚙️ 系统设置 (Settings)**：TUN 模式、DNS 嗅探与防污染、开机自启、WebDAV 同步及实时终端日志 (Logcat)。

### 2.4 现代质感与双主题适配
- **暗黑暗夜 (OLED Dark)**：深邃背景色 `#0C0D11`、卡片 `#16181F`，配合 0.5px 精细微光描边。
- **明亮浅色 (Clean Light)**：柔和灰底 `#F2F4F8`、纯白卡片，清晰护眼。

## 3. Web 原型与验证方式

原型位于 `ui-web-demo/` 目录，单文件零构建依赖，可直接运行：
```bash
# 启动本地预览服务
python3 ui-web-demo/serve.py 8080
```
原型提供：
- 完整 5 个 Tab 页面无缝切换
- 真机外壳 (iPhone/Pixel 模态) 与全屏平铺切换
- 浅色/深色主题实时切换
- 模拟点击启停、并发测速、动态波形刷新、突发流量测试与实时日志流

## 4. Compose 落地技术路线

1. **Tokens 抽取**：将 CSS 变量对齐到 `design/src/main/java/com/github/kr328/clash/design/ios/IosTheme.kt`（扩展 `IosColors` 与 `IosType`）。
2. **仪表盘 Bento 重构**：在 `HomeScreen.kt` 中拆分 `HeroConnectCard`、`OutboundPerceptionCard`、`TrafficWaveformCard`。
3. **底部导航集成**：使用 Compose `NavigationBar` 串联五大 Tab，替代旧版仅在首页列表的跳转。
