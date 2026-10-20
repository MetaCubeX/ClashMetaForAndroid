---
title: "其余屏幕换成 Compose — 并行任务看板"
type: roadmap
status: completed
phase: N/A
updated: 2026-10-16
summary: "T1 到 T10 已完成。屏幕换成 Compose，不再 inflate 的页面布局已删除。没有提交，没有装到手机上"
---

# 其余屏幕换成 Compose — 并行任务看板

> 关联方案：[其余屏幕的分组交互](../../plans/compose-remaining-screens.md)
> 创建时间：2026-10-16

## 总览

| 指标 | 值 |
|:-----|:---|
| 总任务数 | 10 |
| 已完成 | 10 |
| 进行中 | 0 |
| 待启动 | 0 |
| 阻塞中 | 0 |

---

## 任务列表

| ID | 任务描述 | 范围（文件/模块） | 负责人 | 状态 | 依赖 | 阻塞项 | Commit |
|:---|:---------|:------------------|:-------|:-----|:-----|:-------|:-------|
| T1 | 复制列表、偏好行、开关、搜索，接到 `Ios*` | `design/.../ios/`、`nota/ui/`、`universe/agentic/ui/search/` | 父任务 | ✅ 完成 | — | — | — |
| T2 | 代理页和提供者 | Proxy、Providers、Main 的 OpenProxy | 代理 | ✅ 完成 | T1 | — | — |
| T3 | 配置、新建、属性、文件 | 四个 Design 和 Activity | 配置 | ✅ 完成 | T1 | — | — |
| T4 | 日志列表和详情 | Logs、Logcat | 日志 | ✅ 完成 | T1 | — | — |
| T5 | 分流策略和访问控制 | AppsStrategy、AccessControl | 访问控制 | ✅ 完成 | T1 | — | — |
| T6 | 应用设置和网络设置 | AppSettings、NetworkSettings | 设置 | ✅ 完成 | T1 | — | — |
| T7 | 覆写和 Meta | OverrideSettings、MetaFeatureSettings | 覆写 | ✅ 完成 | T1 | — | — |
| T8 | WebDAV 设置 | WebdavSettings | WebDAV | ✅ 完成 | T1 | — | — |
| T9 | 帮助、损坏页、崩溃页 | Help、ApkBroken、AppCrashed | 帮助 | ✅ 完成 | T1 | — | — |
| T10 | 删除不再 inflate 的旧布局 | `design/src/main/res/layout/` | 父任务 | ✅ 完成 | T2–T9 | — | — |

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
   ├──→ T5
   ├──→ T6
   ├──→ T7
   ├──→ T8
   └──→ T9 ──→ T10
```

T1 的 `:design:compileMetaDebugKotlin` 已通过。T2 到 T9 不跑 Gradle。屏幕落地后由父任务编译 `design` 和 `app`。T10 在这些屏幕不再 inflate 旧布局之后删除页面 XML。

---

## 执行日志

| 时间 | 任务 | 事件 | 备注 |
|:-----|:-----|:-----|:-----|
| 2026-10-16 | T1 | 完成 | 复制 AgenticUILibrary。`:design:compileMetaDebugKotlin` 通过。没有装到手机上 |
| 2026-10-16 | T2–T9 | 启动 | 分头改屏幕。不提交，不删 XML |
| 2026-10-16 | T2–T10 | 完成 | 屏幕根视图改为 ComposeView。页面级布局、只服务这些布局的适配器和顶栏已删除。关于、文本编辑器和拉取进度留下。未提交，未装到手机 |

---

## 风险与降级

| 风险 | 影响 | 缓解措施 |
|:-----|:-----|:---------|
| 多个任务同时改 `strings.xml` 或共享 ios 文件 | 互相覆盖 | 只改本任务的 Design、Activity 和新建的 Screen。不改 `Ios*`、`nota/ui`、文档和字符串资源 |
| 并行 Gradle | 内存被杀掉 | 只有父任务在最后编译一次 |
| 空组、日志快照、WebDAV 确认、DNS 文案按旧方案实现 | 行为回退或崩溃 | 以方案 2026-10-16 修订为准 |

---

## 收尾检查

- [x] 所有任务状态已更新为最终状态
- [x] 所有 commit 已记录（本次不提交，Commit 列保持 —）
- [x] 阻塞项已解决或明确降级方案
- [x] 本文件移入 `dev/parallel/archive/`
- [x] `dev/parallel/INDEX.md` 已更新
- [x] 父任务编译 `design` 与 `app` 的 debug Kotlin，并更新系统文档
