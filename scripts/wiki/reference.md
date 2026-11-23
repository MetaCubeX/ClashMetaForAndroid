# Wiki 检查工具的配置与规则

本页列出配置字段、每条规则的含义和级别，以及各命令的详细行为。它是一份登记表，会随规则增加而变长：按规则名或字段名搜索，只读命中的小节，例如 `rg -n 'trace-no-test' scripts/wiki/reference.md`，不要整篇读入。常用命令和安装步骤见[工具入口](README.md)，状态行、核对行、需求条目这些固定写法见[工具读取的写法](formats.md)。

## 命令选项

`--root` 选择项目根目录，配置默认是该目录下的 `docs/wiki.json`。可以用 `--config` 指定另一个相对配置路径。配置中的所有路径都相对于项目根目录。

`--json` 输出结构化报告；`baseline` 和不带选项的 `index` 本来就向标准输出打印 JSON。若配置声明了 `generated_index`，`index --check` 比较该文件与当前目录结果，文件不存在或内容不同时报告 `index-outdated` 并失败；`index --write` 把结果写进这个文件，内容没变时不动文件，报告里的 `written` 说明是否改动。相同输入产生相同目录，不加入运行日期。

只有 `archive --apply`、`index --write` 和 `export-okf` 会写文件，`verify` 会运行配置里登记的命令；其余命令只读。

## 配置

最小配置包含 `version`、`roots` 和 `entrypoints`：

```json
{
  "version": 1,
  "project_name": "Example",
  "roots": [
    {"path": "docs", "role": "knowledge"},
    {"path": "dev", "role": "process"},
    {"path": "archive", "role": "archive"}
  ],
  "entrypoints": ["README.md", "AGENTS.md", "docs/INDEX.md", "dev/INDEX.md"],
  "modules": [
    {"name": "notes", "path": "notes", "entry": "notes/README.md", "docs": "notes/docs"}
  ],
  "pages": [
    {"path": "AGENTS.md", "role": "entry", "max_chars": 8000}
  ],
  "exclude": ["tmp/**", "**/__pycache__/**"]
}
```

`modules`、`pages` 和 `exclude` 可以省略。示例中的 8000 是演示值，应根据实际任务调整。完整的例子是 ProjectWiki 仓库里 NoteBox 参考项目的 `demo/docs/wiki.json`。

全部字段：

| 字段 | 含义 | 说明位置 |
|---|---|---|
| `version` | 配置版本，当前为 `1` | |
| `project_name` | 项目显示名称 | |
| `roots` | 文档根与角色：knowledge、process、archive | 本节 |
| `archive_root` | 可选，主归档目标，必须是已登记的 archive 角色目录 | [存量问题基线](#存量问题基线)末尾 |
| `modules` | 模块名称、源码根、入口，可选模块 `docs` | 本节 |
| `entrypoints` | 必须存在的入口文件 | 本节 |
| `pages` | 页面角色、字符预算、ID 或作用域 | 本节 |
| `exclude` | 不由当前项目检查的目录或文件模式 | 本节 |
| `orphan_ignore` | 可选，不检查有没有入口的路径 | [检查内容和边界](#检查内容和边界) |
| `status_values` | 可选，补充项目自己的状态值及其类别 | 本节 |
| `repos` | 可选，登记链接要指向的相邻仓库 | [相邻仓库和子模块](#相邻仓库和子模块) |
| `status_frontmatter` | 可选，已有 frontmatter 的项目登记状态字段名，例如 `"status"` | 本节 |
| `adopted_at` | 可选，接入时的提交；那时已有的文件不受只针对新文件的规则约束 | [接入起点](#接入起点) |
| `requirements` | 可选，需求文件与测试文件的位置；多套编号时写成分组数组 | [需求追踪](#需求追踪) |
| `verification` | 可选，要求写复核期限或要求人工核对的路径 | [核对记录与复核期限](#核对记录与复核期限) |
| `reconcile` | 可选，文档与代码的名单对账 | [文档与代码对账](#文档与代码对账) |
| `reviews` | 可选，审查入口 README 的位置 | [审查记录](#审查记录) |
| `dir_budgets` | 可选，目录模式到文件数上限 | [目录文件数预算](#目录文件数预算) |
| `size_limits` | 可选，页面和行的默认长度上限及忽略模式 | [页面大小](#页面大小) |
| `baseline` | 可选，存量问题基线的 JSON 路径 | [存量问题基线](#存量问题基线) |
| `generated_index` | 可选，JSON 生成目录的路径 | [命令选项](#命令选项) |
| `archive_index` | 可选，`archive --apply --summary` 追加记录的历史索引 | [归档：先预览，再执行](#归档先预览再执行) |
| `verify` | 可选，验证命令和要记录哈希的文件 | [验证记录](#验证记录) |
| `remind` | 可选，提交时的文档提醒 | [提交时的文档提醒](#提交时的文档提醒) |

配置按完整 JSON 解析。顶层，以及 `verification`、`requirements`、`verify`（含每条命令）、`reconcile`、`remind`（含 `watch` 条目、`threshold` 和 `block_at`）里写错的字段，报告 incomplete，不改用默认值。

- `roots` 登记知识、过程和历史目录。历史目录可以在第一次归档时创建，其余登记目录和必需入口必须存在。
- `modules` 登记模块路径和入口；小模块可以只有 README，省略 `docs` 字段。已有模块文档目录时再登记 `docs`。工具会发现未登记且未明确排除的 `docs/` 目录，独立前端或其他语言模块也需要登记。
- `pages` 为特定文件补充角色和字符预算。可选 `id` 与 `scope` 用于检查当前文档的身份冲突。省略 scope 时，模块内按模块归属，其余按项目归属；历史版本可以保留相同逻辑 ID。历史豁免按已登记历史根的路径判断，当前文件不能只把页面角色写成 `archive` 来降低检查级别。
- `exclude` 控制检查范围；它与给日常检索使用的 `.ignore` 是两套不同用途的规则。不要用检查排除规则把整个历史目录排除，否则无法核对历史链接。
- 工作区检查自动跳过 Git 忽略的未跟踪文件和目录，例如 Gradle 的 `build/` 和 `.gradle/`，这类目录不必再写进 `exclude`。已跟踪的文件不受忽略规则影响。链接指向被忽略的未跟踪文件时按目标不存在处理，因为其他克隆里没有这个文件。项目根不在 Git 工作区里、没有 Git 或项目根本身被忽略时，工具读取全部文件，报告的 `coverage.git_ignored` 写 `not-applied`；跳过时写 `git-ignored-skipped`，暂存检查写 `index`。

文档正文不要求 frontmatter，也不禁止操作指南使用复选框。

计划和决策记录在一级标题下第一行写状态，状态值归入 draft、active、blocked、current、finished、superseded、retired、open 几类；写法和默认值见[状态行](formats.md#计划决策记录和待处理项的状态行)。项目自己的状态值用 `status_values` 补充，例如 `"status_values": {"评审中": "draft"}`。计划默认是 `dev/plans/*`、审查目录的 `dev/reviews/*/README.md` 和 `dev/reviews/*/plan-*.md`，决策记录默认是 `docs/decisions/*` 与 `*/docs/decisions/*`，待处理项默认是 `dev/inbox/*/*.md`，可用 `remind.plans`、`remind.decisions`、`remind.inbox` 修改。

- 入口页链接到状态为 superseded 或 retired 的文档时报错 `links-replaced-document`；其他现行页面这样链接时只警告。决策记录互相引用不算，归档目录里的文档按历史处理。
- 计划或决策记录缺少状态行时警告 `status-missing`，状态值无法识别时警告 `status-unknown`。
- 计划状态是"阻塞"但状态行里没写原因时警告 `plan-blocked-no-reason`。阻塞要写明在等什么，格式见[原因](formats.md#计划决策记录和待处理项的状态行)：`状态：阻塞。原因：等待 X 的接口评审。`。接入起点之前的旧文件不报，和 `plan-no-status` 一致。
- 计划状态是"当前有效"时警告 `plan-status-current`。"当前有效"是决策记录的状态，计划描述一项会结束的工作：已完成的写"已完成"，被取代的写"被替代"；工作已落地、页面仍在描述现行行为时，把长期内容写回 `docs/` 再归档计划。frontmatter 经 `status_values` 映射成 current 的也报。接入起点之前的旧文件不报。
- 已经用 frontmatter 记状态的项目可以登记字段名，例如 `"status_frontmatter": "status"`。登记后，没有状态行的页面从这个字段取状态；两处都写了时以状态行为准，类别不同时警告 `status-mismatch`，字段值无法识别时同样警告 `status-unknown`。字段值和状态行共用 `status_values` 映射，例如 `{"accepted": "current", "in_progress": "active"}`。工具只读 frontmatter 第一层的这一个字段，不读其他字段。不登记时工具不读 frontmatter。
- 计划的状态行可以写负责人。写了但不是 `human:` 或 `agent:` 前缀时报错 `owner-format`。`remind` 会列出执行中和阻塞的计划及负责人，多个 Agent 同时工作时用来确认谁在改什么；阻塞的计划还会一并列出原因。

### 需求追踪

配置 `"requirements": {"files": ["docs/requirements/*.md"], "tests": ["tests/*"]}` 后，`check` 从 `files` 匹配的文件里读取需求条目，写法见[需求条目](formats.md#需求条目)。`tests` 默认是 `tests/*` 与 `*/tests/*`，匹配的任意文件都会被扫描编号。不配置 `requirements` 就不做这组检查。

需求编号默认匹配正则 `[A-Z][A-Z0-9]*-\d+`。已有需求用别的编号时，用 `id_pattern` 写出自己的格式，例如 `"id_pattern": "REQ-[A-Z]+-\\d{3}"` 匹配 `REQ-AUTH-001`，`"FR-\\d+(?:\\.\\d+)*"` 匹配 `FR-1.2`。验收编号总是需求编号加 `.` 和数字，例如 `REQ-AUTH-001.1`、`FR-1.2.1`。正则无效或能匹配空字符串时报告 incomplete。

一个仓库里有几套各自编号的需求时，例如引擎和终端客户端都从 `PRD-001` 编起，把 `requirements` 写成数组，每组一个编号空间：

```json
"requirements": [
  {"name": "engine", "files": ["docs/product/requirements.md"], "docs": ["docs/*", "core/*"], "tests": ["core/src/test/*"]},
  {"name": "tui", "files": ["tui/docs/product/requirements.md"], "docs": ["tui/*"], "tests": ["tui/test/*"]}
]
```

每组必须有唯一的 `name`，可以有自己的 `id_pattern`。重复编号只在组内检查。`docs` 列出哪些文档的引用归这一组，组自己的需求文件总是算在内；省略 `docs` 时所有文档都算。测试只按本组的 `tests` 扫描，所以要写明各组自己的测试位置，默认值 `*/tests/*` 会把其他组的测试也算进来。一份需求文件同时匹配两组的 `files` 时报告 incomplete。不需要分组的项目继续写成一个对象。

| 规则 | 级别 | 含义 |
|---|---|---|
| `trace-unknown-ref` | 错误 | 现行文档或测试引用了不存在的需求编号或验收编号 |
| `trace-duplicate-id` | 错误 | 同一需求编号定义了两次 |
| `trace-status-missing` | 错误 | 需求下面没有可识别的状态行 |
| `trace-acceptance-id` | 错误 | 验收子编号的前缀和所在需求不一致 |
| `trace-no-acceptance` | 警告 | 已接受及之后状态的需求没有验收条目 |
| `trace-no-test` | 警告 | 已实现或已验证的需求有没标"（人工验证）"的验收条目，没被任何测试引用 |
| `trace-manual-evidence` | 警告 | 验收条目标了"（人工验证）"，却没有对应的 `验收核对：<ID>，agent:<name>，YYYY-MM-DD。` 记录 |
| `trace-check-not-manual` | 警告 | 没标"（人工验证）"的验收条目下写了验收核对记录；这条记录不能代替测试 |
| `trace-no-evidence` | 警告 | 已验证的需求没有带链接的"证据："行 |
| `trace-plan-scope` | 警告 | 未结束的计划没有"覆盖需求："一行；维护类工作写"覆盖需求：无（原因）"。审查目录中的计划不检查 |
| `trace-requirements-location` | 警告 | 需求文件所在的根是过程或归档角色，需求在被废止前是现行知识，应放在知识根下 |
| `trace-all-manual` | 警告 | 已实现或已验证、至少 2 条验收的需求，其验收条目全部只有人工验证、没有测试引用 |

编号前缀是已定义编号中第一个数字之前的部分（`PW-11` 的前缀是 `PW-`），只有前缀相同的文字才会被当作引用，所以 `UTF-8`、`SHA-256` 不会误报。文档和过程记录中已有的引用都检查编号是否存在。工具不要求每条需求都有设计引用或计划；轻量变化可以只保留需求条目与测试，不需要增加豁免字段。是否需要设计和计划由改动影响判断。工具只确认编号引用存在，不判断测试是否真的覆盖了验收条件。

需求在被废止之前都是现行知识。`requirements.files` 匹配到的文件，所在根的角色是 process 或 archive 时，`check` 报 `trace-requirements-location`，提示把文件搬到知识根下，并用 `move` 命令搬迁。接入起点之前就在过程或归档根里的需求文件同样会报。

`check --json` 的 `coverage.requirements` 按需求分组给出每组的证据分布（没分组时组名是 `default`）：`requirements` 是需求条数，`acceptance` 是验收条数，`tested` 是至少被一个测试引用的验收条数，`manual` 是只有人工验证、没有测试引用的条数，`untested` 是两者都没有的条数，`status` 是各状态的需求条数。文本输出在汇总里加一行，例如 `requirements[default]: 13 requirements, 55 acceptance items: 40 with tests, 3 manual only, 12 untested`。某个需求状态为已实现或已验证、至少有 2 条验收、且这些验收全部只有人工验证没有测试引用时，`check` 报 `trace-all-manual`：人工验证只用于命令证明不了的条目，能写成测试的先写测试，其余再标人工验证。

### 核对记录与复核期限

知识页在一级标题下、第一个二级标题之前可以写核对行和复核期限，写法见[核对行与复核期限](formats.md#核对行与复核期限)。

| 规则 | 命令 | 级别 | 含义 |
|---|---|---|---|
| `verify-format` | check | 错误 | 核对条目不是"`human:`或`agent:`前缀的核对人，YYYY-MM-DD"；复核期限不是有效日期 |
| `verify-review-before-check` | check | 错误 | 复核期限早于最近一次核对 |
| `review-by-missing` | check | 警告 | 路径匹配 `verification.review_by_required`，却没有复核期限 |
| `verify-no-human` | check | 警告 | 路径匹配 `verification.human_required`，核对行里没有 `human:` 核对人 |
| `review-actor-format` | check | 错误 | 审查核对表的核对人写了前缀，但不是 `human:` 或 `agent:` |
| `review-no-human-check` | check | 警告 | 审查问题所在路径要求人工核对，核对表里没有 `human:` 核对人 |
| `knowledge-review-due` | remind | 警告 | 复核期限已到 |
| `verify-date-future` | remind | 警告 | 核对日期晚于今天 |

配置示例：`"verification": {"review_by_required": ["docs/guides/*"], "human_required": ["docs/requirements/*", "dev/reviews/*"]}`。`check` 只检查 `human:` 前缀写得对不对，无法确认真的是人写的；暂存的改动新增了 `human:` 名字时，`remind --staged` 给出 `[user]` 提醒 `human-claim-added`，见[提交时的文档提醒](#提交时的文档提醒)。`index` 为有核对行的页面输出 `trust`（`agent-checked` 或 `human-checked`）、`verified` 和 `review_by`。

### 导出 OKF

```bash
python3 scripts/wiki/wiki.py export-okf --root . --out /tmp/project-okf
```

把现行知识文档复制到 `--out`，保持相对路径，并在每篇开头加 Open Knowledge Format v0.2 的 frontmatter：`type`（按目录推断，例如 Decision、Requirements、Guide、Design）、`title`、`description`、`status`（当前有效为 stable，草案为 draft，被替代或废止为 deprecated；项目在 `status_values` 中配置的同义状态按对应类别处理）、`verified`、`stale_after`，以及记录原路径的 `okf_source`。

目标目录必须在项目根目录以外，且为空或不存在，否则不写任何文件并返回 2。过程目录（`dev/`）和归档目录不导出，非 Markdown 文件不复制，指向未导出文档的链接保持原样。已带 frontmatter 的文档原样复制并报告 `okf-existing-frontmatter`。文件名是 `index.md` 或 `log.md` 时报告 `okf-reserved-name`：OKF 把这两个名字当作目录页和日志，导出后会被别的工具这样解读。

### 文档与代码对账

规则名、配置项、命令参数这类能列成集合的内容，可以让 `check` 比较文档和代码里的两份名单：

```json
"reconcile": [
  {
    "name": "rules",
    "docs": ["scripts/wiki/README.md", "scripts/wiki/reference.md"],
    "doc_pattern": "`([a-z][a-z0-9]*(?:-[a-z0-9]+)+)`",
    "code": ["scripts/wiki/wiki.py"],
    "code_pattern": "Finding\\(\\s*\"([a-z][a-z0-9]*(?:-[a-z0-9]+)+)\"",
    "ignore": ["check-new", "working-tree"]
  }
]
```

`docs` 和 `code` 是路径模式，`doc_pattern` 和 `code_pattern` 是 Python 正则表达式，按多行模式在整个文件上匹配，有一个捕获组时取组里的文字，没有时取整个匹配。`ignore` 里的名称两边都不比较，用来排除文档里恰好符合格式、但不属于这份名单的词。上面是本仓库检查自身规范时用的配置：工具说明里所有带连字符、写在反引号里的名称，与 `wiki.py` 里报告的规则名对账，所以没有连字符的规则名（例如 `unreadable`）不在对账范围内。

| 规则 | 级别 | 含义 |
|---|---|---|
| `reconcile-missing-in-doc` | 错误 | 名称出现在代码里，文档里没有 |
| `reconcile-missing-in-code` | 错误 | 名称写在文档里，代码里已经没有 |
| `reconcile-empty` | 错误 | 某一侧没有匹配到文件或名称，通常是路径或正则写错了 |

正则只做文字匹配，不理解代码结构：被注释掉的代码、字符串里的同名文字都会算进去。名单的格式最好固定，例如固定写在表格第一列，这样正则可以写得更严。

### 审查记录

`reviews` 配置审查入口的匹配模式，默认 `["dev/reviews/*/README.md"]`。模式用于识别已有文件，不能直接作为新建路径；新建位置按[项目适配](workflow.md#共享流程与项目适配)确定。每个匹配的 README 所在目录算一次审查：目录里除 `plan-*.md` 以外的 Markdown 文件都是分路文件，README 本身也可以直接写问题。写法见[审查问题](formats.md#审查问题)。

工具把以问题编号开头的二级标题（例如 `## r01-1 …`）当作一条问题，读取它下面的第一行状态和"核对"表：

| 规则 | 级别 | 含义 |
|---|---|---|
| `review-duplicate-id` | 错误 | 同一编号在本次审查里出现两次 |
| `review-verdict-missing` | 错误 | 问题下面没有可识别的判定行（待核对、成立、收窄、不成立、已知、重复） |
| `review-duplicate-target` | 错误 | 判为重复，却没有写出本次审查里存在的另一个编号 |
| `review-known-target` | 错误 | 判为已知，却没有链接到已有的方案、缺口或决策 |
| `review-plan-unknown-id` | 错误 | `plan-*.md` 的"覆盖问题："列出了不存在的编号 |
| `review-unverified` | 警告 | 仍是待核对 |
| `review-check-missing` | 警告 | 已有判定，但核对表没有数据行 |
| `review-no-independent-check` | 警告 | 核对表的每一行核对人都是作者（按"作者：<名字>"识别） |
| `review-verdict-mismatch` | 警告 | 状态行和核对表最后一行的判定不同 |
| `review-plan-missing` | 警告 | 成立或收窄的问题既没有"方案"段，也没有被任何 `plan-*.md` 覆盖 |
| `review-plan-scope` | 警告 | `plan-*.md` 没有"覆盖问题："一行 |

归档目录里的审查不再检查。判定是否正确、依据是否充分，工具不判断。

### 目录文件数预算

`dir_budgets` 把目录模式映射到文件数上限，例如 `{"dev/*": 50, "dev/plans": 30, "dev/inbox/*": 30, "dev/reviews/*": 20}`。直接放在某个目录里的已登记 Markdown 文件超过上限时，报告 `dir-budget` 警告，提醒合并成小节或归档已结束的内容。不配置就不检查；项目模板和参考项目的配置默认登记了上面四项，其中 `dev/*` 给 `dev/` 下其他子目录（如 roadmap、reports、iterations）一个兜底预算。

一个目录可能同时命中多个模式。这时按最具体的模式判断：模式字符串最长的一个生效，这个目录只按它的上限报一次。例如 `dev/plans` 同时命中 `dev/*` 和 `dev/plans`，按更长的 `dev/plans` 的上限算。

### 存量问题基线

旧仓库接入时往往一次报出很多错误。配置 `"baseline": "docs/wiki-baseline.json"` 后：

```bash
python3 scripts/wiki/wiki.py baseline --root . > docs/wiki-baseline.json
```

`baseline` 把当前所有错误的指纹打印到标准输出，由操作者写入文件。指纹由规则、文件和链接目标组成，不含行号，所以在同一文件里插入文字不会让旧问题变成新问题。

之后 `check` 对基线里已有的错误不再判失败，报告中的 `baseline_accepted` 写明放过了多少条；不在基线里的错误照常失败。基线里有问题已经修好时，报告 `baseline-stale`，提示重新生成，让基线只减不增。暂存检查发现提交的基线比 HEAD 多了条目时，报告 `[user]` 级的 `baseline-grown`：新问题应当修掉，确实要放进基线需要用户确认。

接入旧仓库时，可把仍在使用的嵌套历史目录继续登记为 `archive` 角色；它们仍参与分类、历史检查和忽略规则提示。新归档默认写入已登记的根 `archive/`；如果主目录使用别的名称，用可选 `archive_root` 明确指定。没有根 `archive/` 且只登记一个历史根时，使用该根。多个其他历史根并存时必须明确主目标，工具不猜测。

没有登记任何 `archive` 角色目录时，`check`、`check --staged` 和 `check-new` 照常运行，只是不做历史匹配，`history_match` 为 `null`；`archive` 和 `archive --summary` 无法确定目的地，报告 `input-unavailable` 并返回 `incomplete`。

新建历史匹配只检查主目标下的完整原路径，不推断旧布局的对应关系。存在其他历史根时，`check-new` 及暂存新增页检查会明确提示仍需查询这些目录。

### 接入起点

基线只记录 `check` 的错误。另有一些规则，在旧仓库上会要求改写成百上千份旧文档：每份计划补状态行、给每篇旧页找入口、把上千篇计划压到目录预算以内。以前项目只能把新规则限在一个专门的子目录里，或者给已完成的工作换一个不触发提醒的状态。

配置 `"adopted_at": "<提交>"` 登记接入时的提交，可以写提交 ID、标签或分支名；工具把它解析成提交 ID，写进报告的 `coverage.adopted_at`。那个提交里已有的文件按路径算存量文件，下列规则对它们放宽：

| 规则 | 对存量文件 |
|---|---|
| `status-missing`、`plan-no-status`、`plan-blocked-no-reason`、`plan-status-current` | 不报 |
| `orphan-page`、`orphan-asset` | 不报 |
| `dir-budget` | 不计数，只数接入之后新增的文档 |
| `trace-plan-scope` | 不报 |
| 审查记录的格式规则 | 不检查；问题编号重复照常报 `review-duplicate-id` |
| `plan-finished-not-archived`、`plan-stale`、`plan-idle`、`inbox-finished-not-archived`、`inbox-stale`、`inbox-no-status` | 合并成一条 `legacy-lifecycle` 警告，写出每条规则的数量；`remind --all` 逐篇列出 |

链接、锚点、页面预算、状态值、`status-mismatch` 等其余规则对存量文件照常检查，错误照样可以记进基线。存量文件改名或换目录后按新路径算，不再是存量。`adopted_at` 不是当前仓库里的提交、没有 Git，或者浅克隆里缺这个提交时，报告 incomplete。

## 页面大小

会持续增长的登记表、状态历史和待处理清单如果不限制大小，单篇会膨胀到几 MB，单行会塞进几十万个字符。按关键词检索命中这样一行，就等于把整篇读进来。`check` 对每篇非归档文档按两个默认上限给出警告，不需要逐页登记 `max_chars`：

- 字符数超过 `page_chars`、且这篇没有在 `pages` 里登记自己的 `max_chars` 时，警告 `page-oversized`。登记了 `max_chars` 的页面按 [`page-budget`](#检查内容和边界) 判断，不再重复报。处理办法是登记表只留未关闭的条目，已关闭的整页归档；确实需要这个长度时，在 `pages` 里给它登记 `max_chars`。
- 任何一行超过 `line_chars` 时，每篇文档报一次 `line-oversized`，消息写出第一处超长行的行号、字符数，以及这篇共有几行超长。处理办法是一行写一条记录，不要把整张表塞进一行。

两条规则对接入起点（`adopted_at`）之前的旧文件照常报：存量大文件正是要被看见的对象。

默认值用 `size_limits` 调整，三个字段都可省略：

```json
"size_limits": {"page_chars": 100000, "line_chars": 10000, "ignore": ["docs/generated/*"]}
```

- `page_chars`（默认 100000）、`line_chars`（默认 10000）必须是正整数。
- `ignore` 是路径模式数组，命中的路径两条规则都不报，用于确实需要整篇留存的生成文件或导出件。

两条规则只读 `check` 已经载入的正文，不额外读文件。

两份入口文件内容重复时另有一条警告：同一目录里 `AGENTS.md` 和 `CLAUDE.md` 都存在、去掉首尾空白后逐字相同、且长度超过 200 个字符时，报告 `entry-duplicate`，建议保留一份、把另一份改成只含一行 `@AGENTS.md` 的导入。导入路径的大小写由 `broken-link` 一并检查。

| 规则 | 含义 |
|---|---|
| `page-oversized` | 未登记 `max_chars` 的非归档页面超过 `size_limits.page_chars` |
| `line-oversized` | 文档里有行超过 `size_limits.line_chars` |
| `entry-duplicate` | 同目录的 `AGENTS.md` 与 `CLAUDE.md` 内容逐字相同 |

### 相邻仓库和子模块

一个系统由几个并列检出的仓库组成时，文档会链接到别的仓库。这类链接写相对路径，在配置里登记目标仓库：

```json
"repos": [
  {"name": "FlymeAIEngine", "path": "../FlymeAIEngine"},
  {"name": "AgenticUILibrary", "path": "../../Agents/AgenticUILibrary"}
]
```

`name` 是字母、数字、`.`、`_`、`-` 组成的唯一名称，只用在报告里。`path` 相对项目根，必须在项目根以外，两个登记项的路径不能相同或互相包含。写错的登记项报告 incomplete。理由和备选做法见 ProjectWiki 仓库的 `docs/decisions/cross-repo-links.md`。

现行文档里的相对链接解析后：

| 目标位置 | 处理 |
|---|---|
| 在项目根内，且在没初始化的子模块里 | 不报错，计入这个子模块的未检查数量 |
| 在登记的仓库里，仓库已检出 | 检查文件或目录是否存在（区分大小写）和锚点，错误报 `broken-link`、`broken-anchor`，级别和仓库内链接相同 |
| 在登记的仓库里，仓库没检出 | 不报错，计入这个仓库的未检查数量 |
| 越出项目根，不在任何登记的仓库里 | `unsupported-link`，消息里写出能打开的改法，没有时提示登记仓库 |

**什么算已检出。** 登记仓库的目录存在且不为空。子模块在工作区里有自己的 `.git` 才算；暂存检查不读子模块的文件，索引只记录它的提交，所以暂存检查里子模块一律算没检出。

每个没检出的仓库和子模块给一条 `repo-not-checked-out` 警告，写出未检查的链接数。报告的 `coverage.repos` 列出每个登记仓库的路径、状态（`checked-out`、`not-checked-out`、`no-git-head`）、读取方式、读到的 HEAD 提交和链接数；有子模块时，`coverage.submodules` 列出每个子模块是否检出。默认不因此失败，因为持续集成通常只检出一个仓库。`check --require-repos` 要求所有登记仓库和有链接指向的子模块都能读到，读不到时报告 incomplete，退出码 2，适合检出了全部仓库的集成环境。

**读取方式。** 工作区检查读目标仓库的工作区，用于编辑反馈。`check --staged` 读目标仓库在检查开始时的 HEAD 提交，报告写出这个提交，结果可以复现；目录存在但没有 HEAD 提交时状态是 `no-git-head`，按没检出处理。只读被链接到的文件，不遍历整个仓库；指向仓库以外的符号链接不会被跟随，按目标不存在处理。

**没做的。** 孤儿页判断、需求追踪和归档的链接改写只看本仓库。`archive` 不改写相邻仓库里的链接。提供方改了契约时提醒使用方、读目标仓库自己的配置判断目标是否已被替代，都还没做。

## 检查内容和边界

当前支持：

- 必需入口、模块入口、登记目录与未登记的 `docs/` 目录。
- 常见内联链接、引用式链接和图片路径。相对路径只按当前文档位置解析，不回退到仓库根目录。脚注定义 `[^名称]: …` 不算引用式链接定义，脚注正文里的链接照常检查。
- 路径大小写、ATX 标题锚点、重复标题后缀、显式 HTML `id` 或 `name` 锚点。
- `AGENTS.md`、`CLAUDE.md` 中独立一行的 `@相对路径` 导入。其他宿主语法不在覆盖范围内。
- 页面总字符数，包括长行、表格和代码块。
- 同一作用域下重复的当前文档 ID。

代码块和行内代码中的链接示例不会被当成实际引用。源码和测试引用检查路径存在性；当前版本不核对源码符号。Markdown 锚点采用工具中固定的标题规则，并有重复标题、中文标题和显式锚点测试；不承诺支持所有渲染器扩展。

当前文档的断链、缺锚点、超预算和身份冲突会失败。相对本地链接逃出所选项目根时，当前文档报错，历史文档告警。本机绝对路径提示可移植性问题；必需导入无法验证时会失败。历史文档中的断链作为告警保留，因为旧源码或旧契约可能已不在当前版本中。

| 规则 | 含义 |
|---|---|
| `missing-entry` | 必需入口、模块入口或登记目录不存在 |
| `required-excluded` | 必需路径被 `exclude` 排除了 |
| `unregistered-docs` | 发现了未登记、也未排除的 `docs/` 目录 |
| `unregistered-markdown` | 警告：有 Markdown 文件不在任何登记的根、模块、页面或入口里，`check` 不读它们。报告的 `coverage.unregistered_markdown` 写出数量和前 20 个路径。登记它们所在的目录，或者排除 |
| `broken-link` | 链接目标不存在，或大小写不一致。多写或少写一层 `../` 就能打开时，消息里写出改后的链接；这条提示不进基线指纹 |
| `broken-anchor` | 目标文件里没有这个锚点 |
| `missing-reference` | 引用式链接没有对应的定义 |
| `unsupported-link` | 本机绝对路径、以 `/` 开头的路径，或越出项目根且不在任何登记仓库里的相对路径。消息里同样写出能打开的改法，或者提示登记仓库 |
| `repo-not-checked-out` | 警告：登记的相邻仓库或没初始化的子模块读不到，指向它的链接没有检查。消息写出链接数量 |
| `unsupported-import` | `AGENTS.md`、`CLAUDE.md` 里的 `@` 导入不是本地路径 |
| `unreadable-target` | 链接目标存在但读不出来，例如不是 UTF-8 |
| `page-budget` | 页面超出登记的字符预算 |
| `duplicate-current-id` | 同一作用域里两篇现行文档登记了相同 ID |
| `name-case-collision` | 同一目录中有只差大小写的 Markdown 文件名 |

除特别说明外，现行文档上是错误，归档文档上降为警告。文档本身读不出来时报告 `unreadable`。

另有两条警告，用来发现越积越多却没人能找到的文件：

| 规则 | 含义 |
|---|---|
| `orphan-page` | 现行知识文档没有被任何现行文档链接。入口页、模块入口和过程目录（`dev/`）里的文档不检查；只被归档文档链接不算有入口 |
| `orphan-asset` | 知识目录或模块文档目录里的非 Markdown 文件（截图、原型、线框等）没有被任何现行文档链接。链接到所在目录也算引用；配置文件、生成目录、基线文件和以 `.` 开头的文件不检查 |

确实不需要入口的路径登记在 `orphan_ignore` 里，例如 `"orphan_ignore": ["docs/assets/fonts/*"]`。

工具不检查外部网址是否可访问，不判断引用的业务含义，不验证任意执行脚本或业务契约，不把通过形式检查描述为知识完全正确。

## 暂存检查

`check --staged` 先读取 Git 索引中的路径、文件模式和对象 ID，再按捕获的对象 ID 读取配置、正文及链接目标。工作区里的未暂存修复或新增文件不会参与这次检查，随后发生的索引变化也不会替换已经捕获的输入。

工具同时捕获 HEAD 作为新增路径比较基线。没有首个提交时，基线为空，报告写明 `unborn`。对这次索引中新出现的文档，会在同一索引快照中查找 `archive/<完整原路径>`；命中时给出 `historical-path-match` 警告，不因同名阻止恢复或新建。还登记了其他历史目录时，另给一条 `legacy-history-unmatched`，提醒这些目录没有按路径比对。比较的是路径新增，不推断文本主题是否相同，也不把重命名自动视为新的知识身份。

配置文件既不在索引里，也不在 HEAD 里（包括仓库还没有首个提交）时，说明项目还没有提交接入，没有可对照的已提交基线。这时 `check --staged` 和 `remind --staged` 不执行检查，报告 `config-not-committed` 警告，`result` 为 `not_checked`，`coverage.not_checked` 写明没有检查任何内容，退出码为 0，提交照常完成。接入脚本因此可以在同一个提交里一起提交配置和钩子。配置在 HEAD 里有、索引里却删掉了（这次提交会移除接入）仍报 `incomplete` 和退出码 2；配置在索引里（包括这次新加的）走正常检查。

登记了相邻仓库（`repos`）时，暂存检查还读这些仓库在检查开始时的 HEAD 提交，不读它们的工作区，见[相邻仓库和子模块](#相邻仓库和子模块)。

已经提交过配置后，没有 Git 仓库、存在未合并索引项、配置没有暂存或必需输入无法读取时，命令返回 `incomplete` 和非零退出码。它不会退回工作区继续检查。子模块内部文档或其他不支持的文件模式也不会冒充已经验证。

报告包含输入快照、工具版本、配置哈希、覆盖目录和未检查事项。暂存快照使用索引清单的 SHA-256 标识；这不是 Git commit 或 tree ID。工作区检查标明 `working-tree`，用于编辑反馈，不声称工作区是原子快照。

## 新建和归档

`check-new` 检查当前目标是否已存在，存在时报告 `current-path-occupied`；再查找 `archive/<完整原路径>` 上的文件，命中时给出 `historical-path-match` 提示。对应父目录存在不算同名文档。历史同名供创建者判断恢复、延续还是新主题，不会仅因历史里出现过标准文档名就永久禁用该名称。

新建检查还处理文件名本身：路径含空格、`#`、`%` 或括号时报告 `name-unsafe-character` 错误，因为归档链接改写无法安全保留这些写法；`plan.md`、`decision.md`、`inbox-item.md` 这三个模板占位名报告 `name-template-placeholder` 警告（`requirements.md` 是推荐的文件名，不算占位名）；`draft`、`final`、`latest`、`done`、`completed` 或 `v2` 等状态、版本词报告 `name-status-or-version` 警告。警告只对 `check-new` 和暂存新增文档产生，已有存量文档不会因为风格建议反复告警。暂存检查不把这两类路径当作新名称：归档根下的路径，以及内容和文件名都不变、只换了目录的移动，所以归档或搬迁一份名字里带空格的旧文档不会被拦下；改成新文件名的重命名照常检查。工具不强制 ASCII、小写、短横线、中文或长度策略。

### 归档：先预览，再执行

不带选项的 `archive <路径>` 只预览，不写文件。路径可以是一份完整的 Markdown 文档，也可以是整个目录（例如一次审查）。预览给出：

- 目的地 `<主归档根>/<原相对路径>`，保留文件名和目录结构；主归档根按[配置](#存量问题基线)选择，默认布局下为 `archive/`。
- 要移动的文档、源内容哈希 `source_sha256`，以及整份操作计划的哈希 `plan_sha256`。归档目录时，`other_files_moved` 列出所有随目录移动的非 Markdown 文件，包括被 `exclude` 排除和未跟踪的文件；计划哈希覆盖目录里每一项的路径、类型、权限位和内容，空目录也算。
- 要改写的链接：别的文档指向被移动文档的入链，被移动文档指向外部的出链。目录内部互相引用的链接随目录一起移动，不用改，也不列出。

确认预览后执行：

```bash
python3 scripts/wiki/wiki.py archive dev/plans/finished-work.md --root . --apply --expect <plan_sha256> --summary "一行说明"
```

`--apply` 重新计算一遍计划，哈希和 `--expect` 一致才执行：移动文件或目录，改写预览列出的每个链接目标，文件的其余字节不变。被移动的文档内容没变，所以改写后的链接仍然指向原来那篇内容。给出 `--summary` 时，在 `archive_index` 指定的历史索引第一张表格末尾追加一行"标题链接 | 说明"；未配置时使用 `<主归档根>/INDEX.md`，默认布局下为 `archive/INDEX.md`。索引必须已存在并含有 Markdown 表格，工具不创建索引。省略 `--summary` 时不追加索引记录。

执行完自动对工作区运行一次 `check`，报告里的 `after_check` 是结果；检查有错误时返回 1。文件只移动，不暂存，提交前需要把删除和新路径一起暂存。执行前会把改写内容写入仓库内的临时文件，准备阶段失败时不移动源文件；提交阶段失败会尝试按相反顺序回滚，并在报告中列出失败或回滚不完整的原因。进程被强制终止时仍可能留下临时文件或中间状态，需按报告检查工作区。

下列情况拒绝执行，不写任何文件：

| 规则 | 级别 | 含义 |
|---|---|---|
| `archive-unsafe-path` | 错误 | 源、目标或改写路径含符号链接或越出项目根 |
| `archive-after-check-failed` | 错误 | 文件已经写入，但执行后的检查无法运行；报告保留已完成的写入 |
| `archive-collision` | 错误 | 目的地已存在（包括空目录），或目的地的某一级上级路径是文件；即使字节相同也不覆盖 |
| `archive-plan-changed` | 错误 | 计划哈希与 `--expect` 不同，要改写的文件在读取后又变了，或归档目录里有文件被增加、删除或修改；重新预览 |
| `archive-index-unavailable` | 错误 | 给了 `--summary`，但历史索引不存在、没有表格，或说明不是一行 |
| `archive-not-applied` | 错误 | 计划里有错误或 `archive-rewrite-manual`，所以没有执行 |
| `archive-rewrite-manual` | 警告 | 某个链接写法无法机械改写（转义字符、百分号编码、同一行无法区分），需要手工修改；有这一项时 `--apply` 拒绝 |
| `archive-prepare-failed` | 错误 | 准备临时改写文件失败，移动尚未开始 |
| `archive-apply-failed` | 错误 | 提交阶段失败，工具已尝试回滚 |
| `archive-rollback-failed` | 错误 | 回滚没有全部完成，需要按报告检查并手工处理工作区 |

预览和执行还会报告这些情况，但不阻止执行：

| 规则 | 含义 |
|---|---|
| `archive-entry-link` | 现行入口页的链接会被改到归档里；有当前文档时，应改为链接当前文档 |
| `archive-unresolved-link` | 被移动文档的出链本来就解析不了，保持原样 |
| `archive-unresolved-target` | 出链目标已经不存在，不给出新路径 |
| `archive-non-markdown` | 目录里有非 Markdown 文件随目录移动，其中的链接没有检查；完整清单见 `other_files_moved` |

源、目标和改写路径的任一级包含符号链接时拒绝；预览和执行前都会检查。搬迁使用不覆盖目标的原子移动；系统不支持时，普通文件可用硬链接移动，目录或跨文件系统无法安全移动时会失败。回滚只撤销本次完成的步骤，遇到其他写入者的文件不覆盖，并报告未完成的回滚和已写入的操作。工具没有全局锁，检查不能防止其他进程在检查后任意替换路径；执行期间应避免并发修改。

工具不判断主题是否已经退役，不新增跳转页，也找不到登记文档以外的引用（例如代码注释、排除目录里的文件）。切出活文件中的历史片段和批量迁移暂不实现。

## 搬迁文档

`move <源> <目标>` 把一份放错位置的现行文档或整个目录搬到另一个已登记的位置，并改写受影响的链接。它和 `archive` 共用同一套搬迁核心：同样的预览、计划哈希、入链与出链改写、冲突检测、回滚，以及执行后运行一次 `check`。区别是目标由你指定，可以是任意已登记的位置，而不是归档根下与原路径对应的那个点；`archive` 还会在历史索引里登记一行，`move` 不登记。

不带选项时只预览，不写文件：

```bash
python3 scripts/wiki/wiki.py move dev/decisions/engine.md docs/decisions/engine.md --root .
```

预览列出要移动的文件、`source_sha256`、整份计划的哈希 `plan_sha256`，以及要改写的链接（别的文档指向被移动文件的入链，和被移动文件里的出链）。目录内部互相引用的链接随目录一起移动，不用改。改写用的规则和 `archive` 一致，所以无法机械改写的链接会报 `archive-rewrite-manual`，解析不了的出链会报 `archive-unresolved-link`、`archive-unresolved-target`，目标冲突报 `archive-collision`，目录里的非 Markdown 文件报 `archive-non-markdown`。

确认后执行：

```bash
python3 scripts/wiki/wiki.py move dev/decisions/engine.md docs/decisions/engine.md --root . --apply --expect <plan_sha256>
```

`--apply` 要求哈希与 `--expect` 一致，目标已存在（包括空目录）就拒绝，不覆盖也不自动编号；之后移动文件、改写链接、运行一次 `check` 并在 `after_check` 报告结果，失败时按和 `archive` 相同的方式回滚。文件只移动，不暂存。`move` 没有 `--summary`。

目标文件名和源文件名相同、只换目录时，不做新建命名检查；改了文件名时，按新建规则检查目标路径，路径含空格、`#`、`%`、括号报 `name-unsafe-character` 错误。

下列情况 `move` 拒绝执行：

| 规则 | 级别 | 含义 |
|---|---|---|
| `move-destination-outside` | 错误 | 目标不在项目根内，或不在任何已登记的根（knowledge、process、archive）或模块文档目录之下 |
| `move-source-protected` | 错误 | 源是配置文件、基线文件、生成目录里的文件或历史索引，或目录包含受保护路径，不能搬迁；`archive` 同样拒绝 |
| `move-invalid` | 错误 | 源不存在、源与目标相同，或目标在源目录内部 |
| `move-use-archive` | 错误 | 目标在归档根里；改用 `archive`，它还会登记历史索引。从归档里搬出来（恢复）用 `move` |

搬迁如果让入口页链接的文档变成孤儿，或使 `generated_index` 过期，执行后的 `check` 会报出来。`generated_index` 覆盖了被移动的文件时，搬迁后运行 `index --write` 重新生成。

### 生成目录与 doctor

`index` 输出包含当前和历史页的全量机器目录，每条记录标注 `role`。生成当前知识导航或默认检索集合时，调用方应过滤历史角色。`check-new`、`archive`、`index`、`doctor`、`verify` 使用工作区；`check --staged` 和 `remind --staged` 使用暂存快照，输出会明确标识这一区别。`remind --staged` 的行数从捕获的索引和 HEAD 内容计算，可能与实时 `git diff --numstat` 在二进制文件或特殊换行上略有差异。

`doctor` 检查配置、明确的根历史忽略规则，以及当前 Git 钩子目录中的 ProjectWiki 钩子；其他钩子管理器和构建任务报告为 `unknown`，并给出 `hook-coverage` 说明。`.ignore` 没有排除已登记的历史根时报告 `archive-default-search`，钩子缺失或没有调用对应命令时报告 `hook-not-installed`，配置里没有 `remind` 时报告 `remind-not-configured`。`remind` 命令在同样情况下给出这条警告，退出码为 0。普通 `rg` 可以遵守 `.ignore`；任意 `grep`、`git grep` 或显式文件读取不受这项规则保证。这里的忽略是默认降噪，不是访问限制。

## 提交时的文档提醒

`remind` 提醒开发者或 Agent 在代码变化后核对文档。它不依赖任何 Agent 宿主：数据只来自 Git 历史、暂存区和提交信息，由 Git 钩子触发，所以无论用 Kiro、Claude Code、Codex、Cursor 还是手工 `git commit`，提交时都会看到同样的提醒。

在配置中加入 `remind`：

```json
"remind": {
  "watch": [
    {"name": "notes", "code": ["notes/src/*"], "docs": ["notes/docs/*", "notes/README.md"]}
  ],
  "threshold": {"commits": 10, "lines": 300},
  "block_at": {"commits": 30},
  "ask_user": ["docs/requirements.md", "docs/principles/*", "notes/docs/decisions/*"]
}
```

- `watch`：一组代码路径和对应文档。模式用 `fnmatch` 语法，`*` 可以跨目录。规则、模板和示例需要同步的项目可以再登记一组观察项，例如代码为 `docs/design/*`、`docs/principles/*`，文档为 `templates/*`、`demo/*` 和入口摘要。
- `threshold`：从上次更新或复核这些文档以来，累计提交次数或改动行数达到任一值时，要求核对文档。核对后更新正文，或用带理由的复核 trailer 清零；这条提醒本身不阻断提交。
- `block_at`：可选。累计量达到这个值后，新的提交如果继续改代码、又没有更新或复核文档，就会失败。不配置时永远只提醒。
- `ask_user`：改动这些文件时，提醒 Agent 先向用户确认，除非用户已经要求这项修改。
- `decisions`：可选，决策记录的位置，默认是 `docs/decisions/*` 与 `*/docs/decisions/*`。
- `plans`、`inbox`：可选，计划和待处理项的位置，见[计划和待处理项的生命周期提醒](#计划和待处理项的生命周期提醒)。
- `plan_stale_days`：可选，执行中的计划和待分诊的待处理项多少天没有提交就提醒，默认 14。
- `plan_idle_days`：可选，状态是草案或阻塞的计划多少天没有实质改动就提醒，默认 60。草案和阻塞不会结束工作，所以用一个更长的期限，避免正常搁置被频繁打扰。

计数的起点是最近一次满足下列任一条件的提交：对该组文档做了实质改动（见下面[什么算实质改动](#什么算实质改动)），或者提交信息带有 `Docs-Reviewed: <名称> <理由>` 这一行（Git trailer，写在提交信息最后一段）。确实复核过、文档不用改时，用这一行清零。没有写理由的 trailer 不清零。多个名称可用逗号分隔。

接入提交是例外。第一次把配置文件（`docs/wiki.json`，或命令行指定的配置）加入仓库的提交算接入提交；取最早的一个（`git log --diff-filter=A`）。配置了 `adopted_at` 时，它指向的提交也算接入提交。接入提交往往改到几乎每个组的文档，又一次性引入已有代码，所以它改文档不让该组清零，它改代码也不计入累计量；只有提交信息里 `Docs-Reviewed` 写了该组名才清零。扫描继续向更早的提交回溯，接入之前累计的欠账照常算。配置从没提交过时没有接入提交，行为和过去一样。

历史遍历包含各分支提交。合并提交按 Git 的 combined 差异判断：只有在所有父提交对应列均标为新增或均标为删除的行才作为合并新增的文本修改；从各父提交继承或拼合的内容不在合并提交重复计数或清零，父提交仍照常遍历。文档还须按完整内容判断是否实质，计划和待处理项的过期天数沿用这一合并判据。二进制或特殊文件类型的合并修改计入提交数、行数为零。重复内容的来源可能无法从提交对象确定；此规则不推断人工解决冲突时的意图。

#### 什么算实质改动

判断一次提交里某个文档的改动是不是"实质"，从提交对象读取改动路径和内容，只对命中某个仍未清零的组的 `docs` 的文件判断；配对移动时还读取可能配对的新增和删除文件；所需对象按提交批量读取，不逐文件启动 Git。`remind --staged` 对比捕获的 HEAD 与索引，历史和暂存使用同一判断规则：内容相同或只改链接、机械字段、状态行的删除和新增配对为纯移动，不清零；先配对完全相同内容，再配对机械改动，内容重复时按路径排序一对一配对。原文件仍在时新增的副本仍算新增。移动跨越观察组边界也按同一方式判断。

不算实质（不让该组清零）：

- 只改了工具识别的 Markdown 行内链接目标，链接文字和可见标题没变；转义后的普通文字、代码块和行内代码里的链接写法按普通内容判断。
- 只改了 frontmatter 顶层的 `updated`、`created`、`date`、`status`、`phase` 这些键的值，包括多行值；其他字段的多行内容按正文判断。
- 只改了 `状态：`/`Status:` 状态行（关键词和取值见[状态行](formats.md#计划决策记录和待处理项的状态行)，中英文写法都算）。
- 纯重命名或移动：忽略上面这些差别后内容相同。

算实质（清零）：其余一切，包括新增、删除、改正文和改变正文顺序。只有首行开启、并有结束分隔符的 frontmatter 内的指定字段按机械字段处理；正文中同名普通字段的改动算实质；代码块及其他位置的 `Status:` 或 `状态：` 文字也不是状态行。项目根位于 Git 仓库子目录时，历史和暂存路径都相对项目根判断。`核对：` 和 `复核期限：` 行的改动算实质，因为那是核对声明。

运行方式：

| 命令 | 用途 | 退出码 |
|---|---|---|
| `remind` | 会话开始时汇总各组累计量，并提醒安装未生效的 Git 钩子 | 只提醒，退出码为 0；没有 Git 时为 2 |
| `remind --staged --message-file <文件>` | 提交时检查暂存的改动和待提交的信息 | 超过 `block_at` 且本次继续改代码未复核时为 1，否则为 0 |

配置里没有 `remind` 时，两条命令都报告警告 `remind-not-configured`，退出码为 0。commit-msg 钩子同样以退出码 0 结束，提交完成。没有 Git 时 `remind` 报告 incomplete，退出码为 2；`remind --staged` 读不到索引时同样为 2。

`remind`（不带 `--staged`）在项目是 Git 工作区时还会检查当前生效的钩子目录里的 ProjectWiki 钩子：pre-commit 或 commit-msg 缺失、不可执行或没有调用对应命令时，给出 `[user]` 警告 `hooks-not-installed`，写明缺哪个钩子，并给两种装法——`git config core.hooksPath scripts/wiki/hooks`，或把 `check --staged` 和 `remind --staged --message-file "$1"` 加进现有钩子（见[安装 Git 钩子](README.md#安装-git-钩子)）。因为改 `git config` 应当由用户决定，这条是 `[user]` 提醒，Agent 不要自己改。它复用 `doctor` 的判断：不是 Git 工作区，或钩子目录无法按 ProjectWiki 钩子识别时，不给这条，避免对其他钩子管理器误报。`remind --staged`（即运行在钩子里时）不检查钩子。退出码仍为 0。

暂存模式在启动时捕获索引和 HEAD 的对象清单，本次改动的文件和行数都从这份清单计算：两边对象 ID 相同的路径不读取内容；符号链接、子模块和二进制文件计为改动的路径，但不计行数。行数用 Python 的 `difflib` 计算，只用于阈值提醒，可能与 `git diff --numstat` 略有差别。

提交时会报告：

- `docs-not-updated`：本次改了代码，对应文档没改，也没有复核 trailer。这条是 `[info]` 信息提示，未达累计阈值时不要求复核、改文档或补 trailer。
- `docs-review-due` / `docs-review-overdue`：累计量达到提醒值或拦截值，给出 `[agent]` 提醒要求复核；只有达到 `block_at` 且本次继续改代码未复核时才阻断。
- `decision-sync`：本次改了决策记录，列出链接到它、但本次没有改动的现行文档。没有链接、直接复述结论的文档列不出来，仍需按关键词搜索。
- `ask-user`：本次改了 `ask_user` 中的文件。
- `review-trailer-incomplete`：提交信息里的 `Docs-Reviewed` 没有写理由，不清零计数。
- `review-trailer-unknown-group`：`Docs-Reviewed` 写了理由，但名字不是任何 `remind.watch` 的 `name`。列出已配置的组名，说明这个名字不清零任何计数。一行用逗号写了多个名字时，只对不存在的那几个告警，存在的照常清零。
- `human-claim-added`：本次暂存的 Markdown 文件新增了写着 `human:<名字>` 的行，例如核对行、核对表、负责人或签收。Agent 不得把自己写成 `human:`，工具又分不出是谁写的，所以这条是 `[user]` 提醒：确认名字对应的人确实做了这件事，否则改成 `agent:`。代码块和行内代码里的格式示例不算。
- `remind-not-configured`：配置里没有 `remind`。提醒未启用，命令退出码为 0，提交继续完成。

需要处理的提醒以 `[agent]` 或 `[user]` 开头：前者由 Agent 按提示处理，后者需要告诉用户。`[info]` 不要求消除，也不要求填写复核 trailer。已知代码变化使文档中的行为、接口或规则失实时，仍须在同一次改动中更新正文，不能等累计阈值。`remind-not-configured` 只说明提醒未启用。工具不判断文档内容是否与代码一致。

### 计划和待处理项的生命周期提醒

`remind` 还会检查 `dev/` 下的计划页和审查目录的 README（路径默认 `dev/plans/*`、`dev/reviews/*/README.md`、`dev/reviews/*/plan-*.md`，可用 `remind.plans` 修改），状态从正文的状态行读取，写法见[状态行](formats.md#计划决策记录和待处理项的状态行)：

- `plan-stale`：状态是"执行中"，但超过 `remind.plan_stale_days`（默认 14 天）没有对这个文件做过实质改动。天数从最后一次实质改动算起，跳过接入提交和只改链接或机械字段的提交，所以接入导入或批量改 frontmatter 的提交不会把计划的天数清零。和观察组的计数不同，`状态：` 行的改动在这里算实质：重新确认状态是对计划的更新。往回最多看 30 次提交，30 次内没有实质改动时按窗口里最老那次提交算天数（被这么多机械提交盖住的计划至少有那么旧，报它过期比漏报安全）。`inbox-stale` 用同样的算法。需要计算天数的计划和待处理项最多并发读取 8 个文件；每个文件仍独立扫描最近 30 次提交，观察组的历史扫描上限仍为 2000 次提交。
- `plan-idle`：状态是"草案"或"阻塞"，但超过 `remind.plan_idle_days`（默认 60 天）没有实质改动。提醒决定开始做、取消，或写明仍在等什么。
- `plan-finished-not-archived`：状态已经是"已完成"，文件还留在 `dev/` 里；项目的"已取消"等同义值应在 `status_values` 中映射到 `finished`。
- `plan-no-status`：计划页没有可识别的状态行。

待处理项默认是 `dev/inbox/*/*.md`（`dev/inbox/README.md` 和各类型目录里的 `README.md` 不算），可用 `remind.inbox` 修改：

- `inbox-stale`：状态仍是"待分诊"，超过 `remind.plan_stale_days` 天没有提交改动过它。
- `inbox-finished-not-archived`：状态已经是"已完成"，文件还留在 `dev/` 里。写明去向后归档。
- `inbox-no-status`：没有可识别的状态行。
- `legacy-lifecycle`：配置了 `adopted_at` 时，接入前已有的计划和待处理项不逐篇提醒，上面几条合并成这一条，写出每条规则的数量；`remind --all` 逐篇列出。见[接入起点](#接入起点)。

这些都是提醒，不拦截提交。

安装钩子的步骤见[工具入口](README.md#安装-git-钩子)。

## 验证记录

计划里的"验证"一节不手写命令结果，用 `verify` 生成。先在配置里登记验证命令和要记录哈希的文件：

```json
"verify": {
  "commands": [
    {"name": "tests", "run": ["python3", "-m", "unittest", "discover", "-s", "tests"], "env": {"PYTHONDONTWRITEBYTECODE": "1"}},
    {"name": "check", "run": ["python3", "scripts/wiki/wiki.py", "check", "--root", "."], "summary_lines": 1, "summary_from": "start"},
    {"name": "demo-tests", "run": ["python3", "-m", "unittest", "discover", "-s", "notes/tests"], "cwd": "demo"}
  ],
  "files": ["scripts/wiki/wiki.py", "tests/test_wiki.py"]
}
```

- `run`：参数数组，直接执行，不经过 shell，所以管道、重定向和通配符都不生效。管道会吞掉前一条命令的退出码，这也是不用 shell 的原因之一。
- `cwd`：运行目录，相对项目根，默认是项目根，不能越出项目根。
- `env`：额外的环境变量。
- `timeout`：秒数，默认 900，超时后终止并记为失败。
- `summary_lines`：记录输出的几行，默认 2。
- `summary_from`：`end`（默认）记录最后几行，适合测试框架；`start` 记录前几行，适合把结论写在第一行的命令，例如 `wiki.py check`。
- `kind`：可选，`test`、`check` 或 `build` 之一，标明这条命令属于哪类验证；其他值是配置错误。不写 `kind` 的命令运行方式不变。

`verify` 的输出里有布尔字段 `code_tests_registered`：所运行的命令里有任意一条 `kind` 为 `test` 时为真。没有时为假，并在记录里写明"未登记代码测试：本记录只证明所登记的检查命令，不证明代码"，提醒这份记录没有证明代码本身跑过测试。

`verify` 依次运行全部命令，`verify tests check` 只运行列出的几条。输出是一段 Markdown，写明运行日期、Git HEAD、未提交文件数、配置文件和登记文件的 sha256 前缀，以及每条命令的目录、退出码、用时和输出摘要；只运行部分命令时列出没有运行的命令。把它原样放进计划的"验证"一节，再补充人工核对的内容。

任何一条命令失败或超时，返回 1，并报告 `verify-command-failed`。登记的文件在运行期间被修改时报告 `verify-files-changed`，记录里的哈希是运行前的状态；`files` 没有匹配到任何文件时报告 `verify-files-unmatched`。配置无效时返回 2，不运行任何命令。

`verify` 运行的是仓库配置里写好的命令，信任程度与运行项目自己的测试脚本相同。在别人的仓库里运行 `verify` 之前，先看一下其中登记了哪些命令。

## 退出码

退出码的含义见[工具入口](README.md#退出码)。输入、配置或命令无法完成验证时，报告 `input-unavailable` 并返回 2。
