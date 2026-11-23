# Wiki 检查工具

这个脚本检查已登记的文档入口、引用、页面长度和文档身份，输出可定位的问题，并在提交时提醒同步文档。它不判断文章里的设计或业务结论是否正确。

需要 Python 3.10 或更新版本，不需要安装第三方库。检查暂存内容和提交提醒还需要 Git。

接入时把整个 `scripts/wiki/` 目录复制到目标仓库，以下说明随工具一起复制：

| 什么时候读 | 文档 |
|---|---|
| 运行命令、安装钩子 | 本页 |
| 用户要求给指定项目接入 ProjectWiki、补齐未完成的接入 | [接入技能](skills/adopt-projectwiki/SKILL.md) |
| 写状态行、核对行、需求条目、审查问题或待处理项 | [工具读取的写法](formats.md) |
| 填写 `docs/wiki.json`，或者查一条规则的含义和级别 | [配置与规则](reference.md)。这份是登记表，按规则名或字段名搜索，只读命中的小节，例如 `rg -n 'trace-no-test' scripts/wiki/reference.md` |
| 执行开发流程（需求到验证的顺序、决定变化时同步文档、审查分工、什么时候归档） | [开发流程规则](workflow.md) |
| 对照实现检查指定文档的准确性与遗漏 | [文档审查技能](skills/review-docs/SKILL.md) |
| 整理指定范围内的重复、失实或已结束文档 | [文档整理技能](skills/organize-docs/SKILL.md) |
| 把已确认的结论写回当前项目知识 | [知识沉淀技能](skills/capture-knowledge/SKILL.md) |
| 用户要求同步文档并提交、收尾提交 | [同步文档与提交技能](skills/sync-docs-and-commit/SKILL.md) |

## 常用命令

在项目根目录运行：

```bash
python3 scripts/wiki/wiki.py check --root .
python3 scripts/wiki/wiki.py check --root . --json
python3 scripts/wiki/wiki.py check --root . --staged
python3 scripts/wiki/wiki.py check --root . --require-repos
python3 scripts/wiki/wiki.py check-new docs/design/new-topic.md --root .
python3 scripts/wiki/wiki.py archive dev/plans/finished-work.md --root .
python3 scripts/wiki/wiki.py archive dev/plans/finished-work.md --root . --apply --expect <plan_sha256> --summary "一行说明"
python3 scripts/wiki/wiki.py move dev/decisions/engine.md docs/decisions/engine.md --root .
python3 scripts/wiki/wiki.py move dev/decisions/engine.md docs/decisions/engine.md --root . --apply --expect <plan_sha256>
python3 scripts/wiki/wiki.py index --root .
python3 scripts/wiki/wiki.py index --root . --write
python3 scripts/wiki/wiki.py verify --root .
python3 scripts/wiki/wiki.py doctor --root .
python3 scripts/wiki/wiki.py remind --root .
python3 scripts/wiki/wiki.py remind --root . --all
python3 scripts/wiki/wiki.py remind --root . --staged --message-file .git/COMMIT_EDITMSG
python3 scripts/wiki/wiki.py baseline --root . > docs/wiki-baseline.json
python3 scripts/wiki/wiki.py export-okf --root . --out /tmp/project-okf
```

| 命令 | 作用 |
|---|---|
| `check` | 检查结构、链接、状态行、需求追踪和审查记录；`--staged` 只读 Git 索引（以及登记的相邻仓库的 HEAD）；`--require-repos` 要求登记的相邻仓库都能读到 |
| `check-new` | 新建文档前检查路径、命名和对应历史 |
| `archive` | 预览整页或整个目录的归档；加 `--apply --expect` 执行 |
| `move` | 预览把一份文档或整个目录搬到另一个已登记位置并改写链接；加 `--apply --expect` 执行 |
| `index` | 输出机器目录；`--write` 写进配置的生成目录，`--check` 比较 |
| `verify` | 运行配置里登记的验证命令，输出验证记录 |
| `doctor` | 检查配置、搜索忽略规则和 Git 钩子 |
| `remind` | 汇总文档复核欠账和计划状态；`--staged` 在提交时运行；`--all` 逐篇列出接入前已有文件的生命周期提醒 |
| `baseline` | 打印当前错误的指纹，作为旧仓库的存量基线 |
| `export-okf` | 导出 Open Knowledge Format bundle |

只有 `archive --apply`、`move --apply`、`index --write` 和 `export-okf` 会写文件，`verify` 会运行配置里登记的命令；其余命令只读。工具不暂存、不提交，Git 钩子也不改文件。

## 安装 Git 钩子

`hooks/pre-commit` 运行 `check --staged`，`hooks/commit-msg` 运行 `remind --staged`。项目没有其他钩子时，每个 clone 执行一次：

```bash
git config core.hooksPath scripts/wiki/hooks
```

已经使用 husky、pre-commit 等钩子工具时，不要覆盖它们的配置，把下面两条命令加进现有的 pre-commit 与 commit-msg 钩子：

```bash
python3 scripts/wiki/wiki.py check --staged --root .
python3 scripts/wiki/wiki.py remind --staged --message-file "$1" --root .
```

钩子默认把仓库顶层作为项目根。项目根是子目录时设置 `WIKI_ROOT`；Python 不叫 `python3` 时设置 `PYTHON`。`doctor` 会检查当前生效的钩子目录里这两个钩子是否存在、可执行并调用了对应命令。它检查不了其他钩子管理器，也无法阻止 `git commit --no-verify`，所以 Agent 规则里要写明不要跳过钩子。

在 Git 工作区里运行 `remind`（不带 `--staged`）也会列出未生效的这两个钩子，提醒按上面的某一种方式装好。配置（`docs/wiki.json`）还没提交时，`check --staged` 和 `remind --staged` 只报 `config-not-committed` 警告、不执行检查、退出码为 0，所以可以把配置和钩子放进同一个接入提交。

配置里还没有 `remind` 时，commit-msg 输出警告 `remind-not-configured`，提交成功。写好配置后的阈值和退出码见[提交时的文档提醒](reference.md#提交时的文档提醒)。

## 退出码

- `0`：所执行的检查通过，可能带警告。
- `1`：检查发现确定错误。
- `2`：输入、配置或命令无法完成验证，报告为 incomplete。钩子要把 `1` 和 `2` 都当作失败。

## 测试

工具的行为测试在 ProjectWiki 仓库的 `tests/` 里，不随工具复制。在 ProjectWiki 仓库根目录运行：

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p 'test_*.py' -v
```

测试在临时目录建立示例项目和 Git 索引，不初始化或提交当前仓库。
