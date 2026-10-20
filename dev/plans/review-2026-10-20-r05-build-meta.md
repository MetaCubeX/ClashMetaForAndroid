---
title: "审查发现 r05 build-meta"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "3 条：app 会打出没有对应 native 库的 ABI 分包，后注归档会从多行笔记中间切开，裸文件名链接会被算到仓库根。"
---

# 审查发现 r05 build-meta

## r05-1

- 编号：r05-1
- 严重度：中
- 症状：`assembleMetaRelease` 一类 app 构建会多出 `armeabi-v7a`、`x86`、`x86_64` 分包；这些包里没有对应 ABI 的 native 库，装上后加载 so 失败。

```67:75:build.gradle.kts
            ndk {
                abiFilters += listOf("arm64-v8a"/*, "armeabi-v7a", "x86", "x86_64"*/)
            }

            externalNativeBuild {
                cmake {
                    abiFilters("arm64-v8a"/*, "armeabi-v7a", "x86", "x86_64"*/)
                }
            }
```

```183:193:build.gradle.kts
        if (isApp) {
            this as AppExtension

            splits {
                abi {
                    isEnable = true
                    isUniversalApk = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                }
            }
        }
```

- 为什么是问题：同一条 `defaultConfig` 把 ndk 和 cmake 限制在 `arm64-v8a`，app 的 `splits.abi.include` 又要求四个 ABI，并且 `isUniversalApk = true`。AGP 8.8（`gradle/libs.versions.toml` 的 `agp`）在 universal APK 打开时不会因 ndk `abiFilters` 与 splits 冲突而失败，命令行构建也不会按 IDE 目标 ABI 丢掉多余输出，于是四个 ABI 分包都会生成。native 只进入 `arm64-v8a`，另外三个分包是缺 so 的错误产物。
- 方向：`splits.abi.include` 与 ndk、cmake 的 `abiFilters` 使用同一 ABI 集合。

## r05-2

- 编号：r05-2
- 严重度：中
- 症状：`status.md` 超过行数上限时，一条多行后注的标题会留在快照里，续行被单独搬进归档，两边都不再是完整后注。

```75:98:scripts/archive-status-overflow.sh
for i, ln in enumerate(lines):
    s = ln.lstrip()
    if s.startswith("> **后注"):
        note_idxs.append(i)
        in_run = True
    elif in_run and s.startswith(">"):
        note_idxs.append(i)
    else:
        in_run = False

if len(note_idxs) < overflow:
    print(
        f"ERROR: need to remove {overflow} lines but only {len(note_idxs)} "
        "post-note lines exist anywhere in the body. Rewrite the snapshot body instead.",
        file=sys.stderr,
    )
    sys.exit(1)

# Keep the newest notes (front of the run); archive the oldest (tail of the run).
keep_count = len(note_idxs) - overflow
move_idxs = note_idxs[keep_count:]
moved = [lines[j] for j in move_idxs]
keep_set = set(move_idxs)
```

- 为什么是问题：溢出量是 `总行数 - 上限`，切割下标是 `note_idxs[keep_count:]`，按行而不是按整条后注。脚本自己把 `> **后注` 之后的 `>` 续行算进同一条笔记，但归档不会对齐到笔记边界。多行后注被从中间切开后，快照只剩标题，归档里是没有标题的续行。
- 方向：只整条移动后注（从 `> **后注` 到该运行结束），切点落在笔记边界上。

## r05-3

- 编号：r05-3
- 严重度：低
- 症状：健康检查把子目录里的 `README.md`、`AGENTS.md`、`CLAUDE.md`、`DESIGN.md` 链接算成仓库根上的同名文件；根文件存在时，相对路径上的断链不会报错。

```74:80:scripts/check-docs-health.py
ROOT_RELATIVE_LINK_PREFIXES = (
    "AGENTS.md",
    "CLAUDE.md",
    "DESIGN.md",
    "README.md",
    "CONTRIBUTING.md",
    "PRIVACY_POLICY.md",
```

```565:569:scripts/check-docs-health.py
    if target_without_anchor.startswith(ROOT_RELATIVE_LINK_PREFIXES):
        repo_target = (REPO_ROOT / target_without_anchor).resolve()
        if repo_target.exists():
            return repo_target
    return (source.parent / target_without_anchor).resolve()
```

- 为什么是问题：`str.startswith` 收到这个元组时，只要目标以其中任一字符串开头就走仓库根。根上的 `README.md` 等文件存在时，函数直接返回根文件，不再按当前 Markdown 所在目录解析。子目录里指向本地 `README.md` 的链接，本地文件缺失也会被判成有效。
- 方向：裸文件名按当前文件的相对路径解析；只有带目录前缀或前导 `/` 的链接才从仓库根解析。
