---
title: "审查方案 r05 build-meta"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 3、drop 0、weaken 0"
---

# 审查方案 r05 build-meta

核对范围只覆盖发现稿点名的 `build.gradle.kts`、`gradle/libs.versions.toml`、`scripts/archive-status-overflow.sh`、`scripts/check-docs-health.py`。三条引用行上的行为都还在。

## r05-1

- 结论：hold
- 严重度：中
- 实际行号：`build.gradle.kts` 67–75、183–193；`gradle/libs.versions.toml` 的 `agp` 为 `8.8.0`（第 2 行）
- 问题类：app 的 ABI 分包比 native 过滤多三个 ABI，对应分包里没有 so。
- 不变量：`defaultConfig.ndk.abiFilters`、`defaultConfig.externalNativeBuild.cmake.abiFilters`、app 的 `splits.abi.include` 使用同一 ABI 集合；该集合之外的 ABI 不产生分包。
- 要动的符号或文件：`build.gradle.kts` 里 app 分支的 `splits.abi.include`。把 `include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")` 收成与上面两处过滤相同的 `arm64-v8a`。
- 明确不改什么：`ndk` 与 `cmake` 里已经生效的 `arm64-v8a` 过滤，以及被注释掉的其余 ABI；`isEnable`、`reset()`、`isUniversalApk`；flavor、签名、`minSdk`、`versionCode`、资源 packaging；`gradle/libs.versions.toml`、`settings.gradle.kts`、`gradle.properties`。
- 怎么本地验证：仓库里没有针对 ABI 分包的测试。应新增的断言意图：三处 ABI 集合都只含 `arm64-v8a`；`assembleMetaRelease` 的产物里没有 `armeabi-v7a`、`x86`、`x86_64` 分包；留下的每个 ABI 分包都含有 `lib/<该 ABI>/` 下的 native 库。

## r05-2

- 结论：hold
- 严重度：中
- 实际行号：`scripts/archive-status-overflow.sh` 75–98。续行收集在 77–83 行，按行切开在 94–95 行（`keep_count = len(note_idxs) - overflow`，`move_idxs = note_idxs[keep_count:]`）。
- 问题类：溢出归档按行数切片，多行后注会从标题和续行中间断开。
- 不变量：一次取舍以整条后注为单位。整条指从某一行 `> **后注` 到该连续 `>` 续行结束。快照总行数不超过上限；标题和它的续行不同时分居快照与归档。
- 要动的符号或文件：`scripts/archive-status-overflow.sh` 内嵌 Python 的 `note_idxs` 消费方式。先把 75–83 行扫到的行号收成一条条后注，再从文档顺序的尾部整条移走，直到 `status.md` 行数不超过 `max_lines`。
- 明确不改什么：frontmatter 缺失或含后注时的退出；后注行数不够覆盖溢出时的 ERROR 退出；`--dry-run` 与 `--max-lines`；新后注留在快照、旧后注进归档的方向；非 `>` 后注行一律留在快照正文。
- 怎么本地验证：仓库里没有这个脚本的测试。应新增的断言意图：用一份超过上限的 status 文本，最旧一条后注含标题和至少一行 `>` 续行，且溢出行数落在该条内部。运行后该标题与全部续行要么都在快照，要么都在归档；归档新增部分不以失去标题的 `>` 续行开头；快照行数不超过上限；后注以外的正文行仍在快照里。

## r05-3

- 结论：hold
- 严重度：低
- 实际行号：`scripts/check-docs-health.py` 74–90 的 `ROOT_RELATIVE_LINK_PREFIXES`，以及 565–569 行的 `resolve_link`。裸文件名在 75–80 行；`docs/`、`dev/` 和模块目录前缀在 81–89 行。
- 问题类：裸文件名在仓库根已有同名文件时被解析到根上，子目录里的断链不会报。
- 不变量：没有目录前缀、也没有前导 `/` 的链接，按当前 Markdown 所在目录解析。带上述目录前缀的链接，以及前导 `/` 的链接，从仓库根解析。根上存在同名文件时，子目录裸文件名仍然按当前目录判断是否存在。
- 要动的符号或文件：`ROOT_RELATIVE_LINK_PREFIXES` 与 `resolve_link`。从该元组去掉 `AGENTS.md`、`CLAUDE.md`、`DESIGN.md`、`README.md`、`CONTRIBUTING.md`、`PRIVACY_POLICY.md`，让 565–569 行的根路径短路只对目录前缀生效。
- 明确不改什么：81–89 行的目录前缀；561–564 行对仓库绝对路径和前导 `/` 的处理；`./` 与 `../` 的相对解析；链接跳过规则、frontmatter、`status.md` 行数上限和其他健康检查。
- 怎么本地验证：仓库里没有 `resolve_link` 或 `link_findings` 的测试。应新增的断言意图：源文件在子目录、目标为裸 `README.md`、根上 `README.md` 存在而子目录文件不存在时，解析结果落在该子目录，并报 broken local link；裸 `AGENTS.md`、`CLAUDE.md`、`DESIGN.md` 同样按源文件目录解析。目标 `docs/...` 和以 `/` 开头的链接仍解析到仓库根；目标 `./README.md` 仍按源文件目录解析。
