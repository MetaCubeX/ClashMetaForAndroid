---
title: "文档总览"
type: concept
status: accepted
phase: N/A
updated: 2026-10-16
summary: "docs/ 的人类导航：产品、架构、系统、模块与指南"
---

# 文档总览

> 返回 [开发向导](../AGENTS.md) | [完整索引](INDEX.md)

```
docs/
├── product/         用户可观察的承诺
├── architecture/    模块边界与进程
├── systems/         跨模块行为
├── modules/         模块导航
├── guides/          构建与测试
└── templates/       新文档模板
```

## 产品

| 文档 | 说明 |
|:-----|:-----|
| [产品入口](product/INDEX.md) | 阅读顺序与权威范围 |
| [愿景](product/vision.md) | 为谁、边界 |
| [需求](product/requirements/INDEX.md) | 每条 `PRD-NNN` 一个文件 |
| [追踪](product/traceability.md) | 承诺对应的代码位置 |

## 架构与系统

| 文档 | 说明 |
|:-----|:-----|
| [高层设计](../DESIGN.md) | 进程与模块职责 |
| [架构概览](architecture/overview.md) | 依赖关系 |
| [运行时](systems/runtime/INDEX.md) | VPN 服务、启停与 mihomo |
| [配置](systems/profiles/INDEX.md) | 配置的导入、提交与选择 |
| [访问控制](systems/access-control/INDEX.md) | 应用名单与分流策略 |
| [WebDAV](systems/webdav/INDEX.md) | 配置同步 |
| [界面](systems/ui/INDEX.md) | Activity 与 design |

## 模块设计

| 模块 | 设计 |
|:-----|:-----|
| [app](../app/docs/INDEX.md) | Activity、远程调用、日志捕获 |
| [service](../service/docs/INDEX.md) | 进程、文件提供者、运行时模块、配置库 |
| [core](../core/docs/INDEX.md) | Kotlin 模型、JNI 与 Go 边界 |
| [design](../design/docs/INDEX.md) | Compose 屏幕、请求通道、编辑器适配器 |
| [common](../common/docs/INDEX.md) | Store 与常量 |
| [hideapi](../hideapi/docs/INDEX.md) | ActivityThread 编译期存根 |

## 指南

| 文档 | 说明 |
|:-----|:-----|
| [构建、配置与测试](guides/getting-started/build.md) | JDK、flavor、子模块、签名 |
| [单元测试](guides/getting-started/testing.md) | service 与 design 的 JVM 测试任务 |
| [文档规范](DOCS-SPEC.md) | 目录约定 |
| [维护规则](DOCUMENTATION.md) | 提交前要更新什么 |

健康检查：

```bash
python3 scripts/check-docs-health.py
```

## 阅读路径

- 先看应用做什么 → [产品](product/INDEX.md) → [高层设计](../DESIGN.md)
- 要编译 → [构建指南](guides/getting-started/build.md)
- 改分流或访问控制 → [访问控制](systems/access-control/INDEX.md)
- 改同步 → [WebDAV](systems/webdav/INDEX.md)
