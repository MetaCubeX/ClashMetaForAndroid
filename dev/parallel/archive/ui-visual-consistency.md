---
title: "界面视觉收口 — 并行任务看板"
type: roadmap
status: completed
phase: N/A
updated: 2026-10-16
summary: "T1 到 T7 已完成。分组行、弹层、空态和对话框主题已收口。没有提交，没有装到手机上"
---

# 界面视觉收口 — 并行任务看板

> 关联方案：[界面视觉收口](../../plans/ui-visual-consistency.md)
> 创建时间：2026-10-16

## 总览

| 指标 | 值 |
|:-----|:---|
| 总任务数 | 7 |
| 已完成 | 7 |
| 进行中 | 0 |
| 待启动 | 0 |
| 阻塞中 | 0 |

---

## 任务列表

| ID | 任务描述 | 范围（文件/模块） | 负责人 | 状态 | 依赖 | 阻塞项 | Commit |
|:---|:---------|:------------------|:-------|:-----|:-----|:-------|:-------|
| T1 | 分组行、弹层、确认色、进度、导航图标、三条文案 | `ios/IosComponents.kt`、`IosControls.kt`、`IosTheme.kt`、`HomeScreen.kt`、`SettingsScreen.kt`、`ShellIosSpecs.kt`、`design` 的 `values*/strings.xml` | 共享 | ✅ 完成 | — | — | — |
| T2 | 代理空态、菜单改 `IosSheet`、导航图标 | `ProxyScreen.kt` | 代理 | ✅ 完成 | T1 | — | — |
| T3 | 配置和文件的空态、用量条、取消 | `ProfilesScreen.kt`、`FilesScreen.kt` | 配置 | ✅ 完成 | T1 | — | — |
| T4 | 日志空态和导航图标 | `LogsScreen.kt`、`LogcatScreen.kt` | 日志 | ✅ 完成 | T1 | — | — |
| T5 | 选择标记、反序、取消 | `AccessControlScreen.kt`、`AppsStrategyScreen.kt` | 访问控制 | ✅ 完成 | T1 | — | — |
| T6 | 对话框主题；简单确认改走 `iosConfirm` | `themes.xml`、`Design.kt`、六个 `*Design.kt`、`dialog/`、`preference/Editable*`、`SubscriptionQr.kt`、`Toast.kt`、`MetaFeatureSettingsActivity.kt` | 对话框 | ✅ 完成 | — | — | — |
| T7 | 编译 `design` 与 `app`，更新界面文档和快照 | `docs/systems/ui/INDEX.md`、`design/docs/compose.md`、`status.md` | 父任务 | ✅ 完成 | T1–T6 | — | — |

### 状态图例

- ⏳ 待启动 — 尚未开始
- 🔄 进行中 — 正在执行
- ✅ 完成 — 已验证并 commit
- ❌ 失败 — 执行失败，需重新分配或调整方案
- ⏸️ 阻塞 — 因外部依赖暂停

---

## 执行时序

```
T1 ──→ T2
   ├──→ T3
   ├──→ T4
   └──→ T5 ──→ T7
T6 ──────────→ T7
```

T1 与 T6 同时开始。T2 到 T5 在 T1 写入工作区之后开始，四条同时进行。T6 不改 `ios/` 屏幕，不等 T1。T2 到 T6 不跑 Gradle。T7 编译 `:design:compileMetaDebugKotlin` 和 `:app:compileMetaDebugKotlin`。

每个任务只改方案里列出的文件。三条文案由 T1 写入全部 `values*`，其他任务只引用 `back`、`search_empty`、`list_empty`。

---

## 执行日志

| 时间 | 任务 | 事件 | 备注 |
|:-----|:-----|:-----|:-----|
| 2026-10-16 | — | 建立看板 | 方案已写，实现未开始 |
| 2026-10-16 | T1–T7 | 完成 | `:design:compileMetaDebugKotlin` 与 `:app:compileMetaDebugKotlin` 通过。未提交，未装到手机 |

---

## 风险与降级

| 风险 | 影响 | 缓解措施 |
|:-----|:-----|:---------|
| T2–T5 与 T1 同时改 `Ios*` | 默认参数或调用对不上 | T2–T5 等 T1 落地。签名以方案里的默认值为准 |
| T6 与 T1 同时改 `iosConfirm` | 确认框颜色被盖掉 | `iosConfirm` 只由 T1 改。T6 只调用 |
| 并行 Gradle | 内存被杀掉 | 只有 T7 编译 |
| 空态误用 `empty` | 中文显示「置空」 | 新句子只用 T1 增加的键 |

---

## 收尾检查

- [x] 所有任务状态已更新为最终状态
- [x] 所有 commit 已记录（本次不提交，Commit 列保持 —）
- [x] `dev/progress/status.md` 已更新
- [x] 本文件 frontmatter `status` 改为 `completed`
- [x] 本文件移入 `dev/parallel/archive/`
- [x] `dev/parallel/INDEX.md` 已更新

---

*最后更新：2026-10-16*
