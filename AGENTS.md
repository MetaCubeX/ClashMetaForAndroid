# AGENTS.md — 开发向导

> 本文件是 AgenticClash（上游 ClashMetaForAndroid）的开发向导。`CLAUDE.md` 只指向这里。

AgenticClash 是 [mihomo](https://github.com/MetaCubeX/mihomo) 的 Android 图形界面。本仓库是 TIIEHenry fork，在上游应用上保留应用分流策略、访问控制应用集合和 WebDAV 同步。

- `docs/` 保存当前需求、设计和模块契约；`dev/` 保存计划、看板和协调快照；历史在配置选定的 `dev/archive/`。检索历史时按任务打开，不把归档正文当成现行规范。
- 文档开发流程以 [scripts/wiki/workflow.md](scripts/wiki/workflow.md) 为准。本文件只保留忽略后会直接做错的规则。已有流程里和共享流程重复或冲突的要求，已从原入口删掉，不两套一起执行。
- 开始任务时运行 `python3 scripts/wiki/wiki.py remind --root .`。单次 `docs-not-updated` 是 `[info]`，未达累计阈值时不要求复核或写 trailer；已知正文失实时仍在本次修改里更新。累计复核的 `[agent]`：更新对应文档，或在提交信息最后一段写 `Docs-Reviewed: <名称> <理由>`。`[user]` 先告诉用户。阈值见 `scripts/wiki/reference.md` 的「提交时的文档提醒」。不得用 `--no-verify` 跳过钩子。每个克隆启用钩子的命令是 `git config core.hooksPath scripts/wiki/hooks`，代理不改 git config。
- 按改动范围运行 `python3 scripts/wiki/wiki.py verify --root .`。命令只登记在 [docs/wiki.json](docs/wiki.json) 的 `verify`。`docs-health` 检查 frontmatter、模块索引和快照行数；`wiki-check` 检查链接、状态和需求编号。两套输入不同，都要通过。
- 新建文档路径不得含空格、`#`、`%` 或括号。新建前运行 `python3 scripts/wiki/wiki.py check-new <路径> --root .`。
- 整页归档到 `dev/archive/<原相对路径>`。用 `python3 scripts/wiki/wiki.py archive <路径> --root .` 预览，再加 `--apply --expect <plan_sha256>` 执行。不另建仓库根的 `archive/`。
- 状态行、核对行和需求条目的写法以 [scripts/wiki/formats.md](scripts/wiki/formats.md) 为准。本仓库用 frontmatter 的 `status` 作为没有状态行时的状态来源，登记在 `docs/wiki.json` 的 `status_frontmatter`。两处都写时以状态行为准。
- 核对人、负责人和签收人写成 `human:<名字>` 或 `agent:<名字>`。代理不得把自己写成 `human:`。
- 只暂存本次工作相关的文件。不还原、清理或 stash 其他人和其他代理的未提交改动。
- AI 工具或技能生成的计划、报告和草稿放进 `dev/`，不能写进 `docs/`。
- 稳定知识写清对象、条件和行为。控件尺寸和调用顺序放在模块文档，不堆进本文件。
- `dev/progress/status.md` 只保留跨计划的协调事实，保持 200 行以内。任务步骤写在对应计划或看板里，不因每次提交改这份快照。溢出后注用 `bash scripts/archive-status-overflow.sh`。
- 不要把设备序列号、签名指纹、密钥口令写进仓库文档。WebDAV 默认常量以 `service` 模块源码为准，文档只链接，不另抄一套。
- 根 `README.md` 用英文写项目介绍、构建入口和社区链接。Fork 的架构与构建约束写在 `docs/`。
- `core/src/foss/golang/clash` 的修改在提交或推送前要经过子代理审查。审查对象是这次内核 diff，不把父仓库的其它改动算进去。

## 模块与构建

Gradle 模块由 `settings.gradle.kts` 决定：`app`、`core`、`service`、`design`、`common`、`hideapi`。导航在 [docs/modules/](docs/modules/)。

依赖方向：`app` 使用 `core`、`service`、`design`、`common`，并 `compileOnly` `hideapi`。`service` 与 `design` 使用 `core` 和 `common`。`common` `compileOnly` `hideapi`。

```bash
git submodule update --init --recursive
./gradlew :app:assembleMetaRelease
```

默认 flavor 是 `alpha`。已安装到手机上的包使用 `meta` flavor，两个 flavor 的包名都是 `universe.agentic.device.clash`。JDK 目标是 21。没有 `signing.properties` 时 release 使用 debug 签名。细节与内核 pin 见 [构建指南](docs/guides/getting-started/build.md)。

编码前读相关的 [docs/systems/](docs/systems/) 或模块 `docs/`。接续已有任务先读 [开发入口](dev/INDEX.md)。

按本项目的实际任务读取技能，不把它们作为每次任务或提交的必做步骤；按路径读取不代表宿主已原生安装技能。

| 任务 | 本地技能路径 |
|:-----|:-------------|
| 补齐未完成的 ProjectWiki 接入 | `scripts/wiki/skills/adopt-projectwiki/SKILL.md` |
| 对照实现审查指定文档 | `scripts/wiki/skills/review-docs/SKILL.md` |
| 整理指定范围的文档 | `scripts/wiki/skills/organize-docs/SKILL.md` |
| 把已确认结论写回现行知识 | `scripts/wiki/skills/capture-knowledge/SKILL.md` |
| 用户要求同步文档并提交、收尾提交 | `scripts/wiki/skills/sync-docs-and-commit/SKILL.md` |

知识与阅读条件见 [知识入口](docs/INDEX.md)。本仓库仍保留的结构约定见 [文档规范](docs/DOCS-SPEC.md) 和 [项目适配](docs/DOCUMENTATION.md)。
