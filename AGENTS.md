# AGENTS.md — 开发向导

> 本文件是 ClashMetaForAndroid 的开发向导。`CLAUDE.md` 只指向这里。

ClashMetaX 是 [mihomo](https://github.com/MetaCubeX/mihomo) 的 Android 图形界面。本仓库是 TIIEHenry fork，在上游应用上保留应用分流策略、访问控制应用集合和 WebDAV 同步。

> **新会话必读顺序：**
> 1. 本文件
> 2. [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md) — 文档维护规则
> 3. [dev/progress/status.md](dev/progress/status.md) — 当前快照

编码前再看相关的 [docs/systems/](docs/systems/) 或 [docs/modules/](docs/modules/)。构建、签名和内核子模块的约束见 [构建指南](docs/guides/getting-started/build.md)。

## 文档导航

| 文档 | 说明 |
|:-----|:-----|
| [文档索引](docs/INDEX.md) | 全局入口 |
| [产品需求](docs/product/INDEX.md) | 用户可观察的承诺 |
| [高层设计](DESIGN.md) | 进程与模块边界 |
| [架构概览](docs/architecture/overview.md) | 模块依赖 |
| [文档规范](docs/DOCS-SPEC.md) | 目录与归属 |
| [维护规则](docs/DOCUMENTATION.md) | frontmatter、提交前文档门禁 |
| [术语表](docs/glossary.md) | 本仓库用词 |

## 模块

Gradle 模块由 `settings.gradle.kts` 决定：`app`、`core`、`service`、`design`、`common`、`hideapi`。导航在 [docs/modules/](docs/modules/)。

依赖方向：`app` 使用 `core`、`service`、`design`、`common`，并 `compileOnly` `hideapi`。`service` 与 `design` 使用 `core` 和 `common`。`common` `compileOnly` `hideapi`。

## 构建

```bash
git submodule update --init --recursive
./gradlew :app:assembleMetaRelease
```

默认 flavor 是 `alpha`。已安装到手机上的包使用 `meta` flavor，包名 `com.github.metacubex.clash.meta`。JDK 目标是 21。没有 `signing.properties` 时 release 使用 debug 签名。细节与内核 pin 见 [构建指南](docs/guides/getting-started/build.md)。

## 开发流程

1. 行为或契约变更先写进 `dev/plans/` 或对应 `docs/systems/`，需要取舍时写 ADR。
2. 编码。
3. 提交前按 [规则 3a](docs/DOCUMENTATION.md#规则-3a提交前文档门禁) 更新行动层；用户可见行为再更新知识层。
4. 只 `git add` 本次主题的文件。工作区里其他未提交改动留在原地。

单行且无行为变化的修改可以只提交代码。

## 约束

- 过时文档移入 `dev/archive/`，不删除。
- `status.md` 保持 200 行以内的当前快照。溢出后注用 `bash scripts/archive-status-overflow.sh` 滚入归档。
- 结构变更后运行 `python3 scripts/check-docs-health.py`。
- 不要把设备序列号、签名指纹、密钥口令写进仓库文档。WebDAV 默认常量以 `service` 模块源码为准，文档只链接，不另抄一套。
- 根 `README.md` 保持上游英文介绍。Fork 的架构与构建说明写在 `docs/`。
