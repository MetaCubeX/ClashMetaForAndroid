---
title: "审查方案 r09 clash-adapter"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r09 clash-adapter

五条均 hold。行号与发现稿一致，行为仍在。drop 0，weaken 0。

## r09-1

- 问题类：fallback 在固定节点探活失败时清掉固定选择，且不从列表头重找存活节点，拨号和状态查询都会落到已死的第一个节点。
- 不变量：`selected` 只由 `Set` / `ForceSet` 写入。固定节点对 `testUrl` 仍存活时返回它；否则从 `GetProxies` 的列表头返回第一个存活代理，并保留原来的 `selected`。全员不存活时仍返回 `proxies[0]`。`Now` 与 `MarshalJSON` 不得改变 `selected`，序列化里的 `fixed` 仍是用户的固定名。
- 要动的符号或文件：`outboundgroup/fallback.go` 的 `(*Fallback).findAliveProxy`（105–124 行）。同一函数被 `Now`（26–28 行）、`DialContext`、`ListenPacketContext`、`SupportUDP`、`Unwrap` 调用；`MarshalJSON`（86–92 行）先 `Now()` 再读 `fixed`。只改这一处查找。
- 明确不改什么：不改 `Set` 在节点未存活时主动 `URLTest` 的行为（140–145 行）。不给 `selected` 加锁。不改 url-test 的 `selected`。不改「无人存活则返回第一个节点」。
- 怎么本地验证：`adapter` 内没有 fallback 测试。应新增断言：列表为 `[A 死亡, B 存活, C 被固定且死亡]` 时 `findAliveProxy` 返回 B，且 `selected` 仍为 C；`MarshalJSON` 之后 `fixed` 仍为 C；C 恢复存活后返回 C；全员死亡时仍返回 A。

## r09-2

- 问题类：url-test 的当前节点位于成员列表第一位时，容差被跳过，后面出现更低延迟就会立刻换节点。
- 不变量：`fastNode` 只要仍在完整成员列表中且对 `testUrl` 存活，仅当它的 `LastDelayForTestUrl` 大于本轮最低延迟加 `tolerance` 时才换成新节点。列表第一位必须参与「是否仍在列表中」的判断。
- 要动的符号或文件：`outboundgroup/urltest.go` 的 `(*URLTest).fast`（118–141 行）。`fastNotExist` 初值为 true，存在性只在 `proxies[1:]` 里清除。`fastSingle` 的 10 秒缓存在 213 行，`healthCheck` 会 `Reset` 它（97–100 行）；缓存本身不动。
- 明确不改什么：不改 `selected` 非空时提前返回的路径（106–116 行）。不改容差不等式。不改 `fastSingle` 的缓存时长，不改 `healthCheck` 的重置。不改「以 `proxies[0]` 为初始候选、死亡节点延迟按最大值参与比较」的扫描方式。
- 怎么本地验证：`adapter` 内没有 url-test 测试。应新增断言：当前节点是 `proxies[0]`、延迟 100、下一名 90、`tolerance` 50 时仍返回当前节点；下一名 40 时才切换；当前节点不在列表中，或已不存活时仍切换；当前节点不是第一位且差值在容差内时不切换。

## r09-3

- 问题类：分组写了 `expected-status`，但有效探活 URL 与提供者默认 URL 相同时，这次探活仍只用提供者自己的期望状态码。
- 不变量：用户写出了 `expected-status` 时，分组实际使用的那个探活 URL 若与某提供者的 `hc.url` 相同，该提供者的默认探活必须使用分组解析出的范围。分组没写 `expected-status` 时，提供者原有范围保持不变。`AliveForTestUrl(分组 testUrl)` 反映的是这次探活用的范围。
- 要动的符号或文件：`outboundgroup/parser.go` 138–151 行（分组没写 `url` 时抄提供者 URL，且不注册 `expectedStatus`）；`provider/healthcheck.go` 的 `(*HealthCheck).registerHealthCheckTask`（68–72 行，`url == hc.url` 直接返回）。默认探活仍在 `check` 134–136 行读取 `hc.expectedStatus`，该读取保持为默认范围的唯一来源。用户是否写了范围，用 `parser.go` 121 行解析前的原始字符串判断；126–130 行会把空字符串改成展示用的 `*`，覆盖判断不能用改写后的展示值。
- 明确不改什么：不把「分组没写 url」改成给所有提供者追加一条新 URL 探活。URL 不同的 extra 注册（`addTestUrlToProviders` 与 `registerHealthCheckTask` 98–100 行）保持原样。不改内联代理走 `NewHealthCheck` 时已经传入 `expectedStatus` 的路径（`parser.go` 176 行）。不把同一 URL 拆成按分组各存一份存活标记。不改 `check` 的锁和调度。
- 怎么本地验证：`adapter` 内没有这条路径的测试。应新增断言：分组 `expected-status` 只接受 204，且分组 URL 与提供者 URL 相同，或分组省略 URL 因而抄用该 URL 时，默认探活把 204 传给 `URLTest`；响应对 200 时 `AliveForTestUrl` 为 false，对 204 时为 true。分组未写 `expected-status` 时，提供者原范围不被改成接受全部。分组 URL 与提供者 URL 不同时，extra 任务仍携带分组范围。

## r09-4

- 问题类：探活已经拿到 HTTP 响应，但状态码不在 `expected-status` 里时，默认存活标记、默认延迟记录和返回值仍按成功处理。
- 不变量：状态码不满足时，本次测试在写入任何存活标记或延迟记录之前就判失败：`URLTest` 返回非 nil 错误，返回延迟不作为成功值；`p.alive` 为 false，`p.history` 与该 URL 的 extra 记录都是失败（延迟 0、alive false）。`expectedStatus == nil` 时，有响应仍算成功。
- 要动的符号或文件：`adapter/adapter.go` 的 `(*Proxy).URLTest`。defer 在 169–199 行先按 `err == nil` 写 `p.alive` 和 `p.history`，之后才用 `satisfied` 改 extra。`satisfied` 在 276–278 行赋值，随后直接 `return`。失败判定前移到两处写入之前，并让返回的 `err` 为非 nil。
- 明确不改什么：不改 `GroupBase.URLTest` 只把 `err == nil` 放进延迟表的判断（`groupbase.go` 246–250 行）；节点会被排除是因为 `URLTest` 开始返回错误。不改连接失败、超时本来就 `err != nil` 的路径。不改 `unifiedDelay` 的第二次请求。不改 extra 按 URL 存储的结构。
- 怎么本地验证：`adapter` 内没有 `URLTest` 测试。应新增断言：状态码不在范围内时返回错误，`Alive()` 为 false，默认 `DelayHistory` 最后一条延迟为 0，同一 URL 的 `AliveForTestUrl` 为 false，`GroupBase.URLTest` 的结果 map 不含该节点。状态码在范围内时错误为 nil，默认 alive 与该 URL 的 alive 都为 true。

## r09-5

- 问题类：load-balance 在未设置 `disable-udp` 时接受 UDP，三种策略只按 HTTP 探活选成员，数据包会交给 `SupportUDP() == false` 的成员并直接失败。
- 不变量：UDP 选路（`ListenPacketContext`）在现有探活条件之上跳过 `SupportUDP() == false` 的成员；三种策略的全表兜底和 `return proxies[0]` 在 UDP 路径上也不能返回这种成员。没有任何存活且支持 UDP 的成员时，本次监听失败。TCP 拨号仍可选中仅不支持 UDP、但探活成功的成员。未设置 `disable-udp` 且至少有一个成员支持 UDP 时，分组 `SupportUDP()` 为 true；一个都不支持时为 false。
- 要动的符号或文件：`outboundgroup/loadbalance.go`。`ListenPacketContext` 146–154 行经 `Unwrap` 进入策略；`SupportUDP` 157–160 行只看 `disableUDP`。只判断 `AliveForTestUrl` 的位置：round-robin 183–192 行，consistent-hashing 204 行以及 210–216 行的全表扫描，sticky-sessions 237 行以及 248–249 行的兜底。策略函数要能区分本次是不是 UDP，不能无条件把 UDP 过滤加进 TCP 与 UDP 共用的选路。
- 明确不改什么：不改 `hash-key`、`getKey`、`jumpHash`。不改 TCP 的 `DialContext` 选路。不改 sticky 缓存：UDP 因能力不符另选时，不覆盖为 TCP 记下的下标。不改「监听因网络错误失败后不换下一个成员」。不改 round-robin 现有的 `idx` 推进方式，只是多一个与「不存活则跳过」同类的条件。已有测试继续覆盖 TCP/哈希：`TestLoadBalanceHashKeyInUserSurvivesADestinationChange`、`TestLoadBalanceHashKeyInUserSpreadsUsers`、`TestLoadBalanceHashKeyInUserSeparatesJobsSharingASourceAddress`、`TestLoadBalanceHashKeyInUserFallsBackWhenUnauthenticated`、`TestLoadBalanceHashKeyResolvesTheOptionName`、`TestLoadBalanceHashKeyRejectsUnusableConfigs`。
- 怎么本地验证：上述六个测试保持通过。应新增断言：HTTP 探活成功但 `SupportUDP` 为 false 的成员，在 round-robin、consistent-hashing、sticky-sessions 的 UDP 选路中都不会被返回；同列表另有存活且支持 UDP 的成员时返回该成员；全部不支持 UDP 时 UDP 选路失败，且 `SupportUDP()` 为 false；`disable-udp: true` 时 `SupportUDP()` 仍为 false；同一批成员的 TCP 选路仍可以选中不支持 UDP 的成员。
