---
title: "审查方案 r12 clash-common"
type: plan
status: implemented
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "hold 5、drop 0、weaken 0"
---

# 审查方案 r12 clash-common

核对范围是 `core/src/foss/golang/clash/common`。五条引用的行为都还在，行号与发现稿一致。结论是 hold 5、drop 0、weaken 0。

## r12-1

- 问题类：并发 `Pop` 或 `Last` 在锁外看见非空后，队列可能已被取空，锁内再按下标读取就会 panic。
- 不变量：`Pop` 和 `Last` 对 `items` 的长度判断与按下标读取持有同一把锁。长度为 0 时返回该类型的零值，不读取下标。并发 `Put`、`Pop`、`Last` 不因队列被取空而 panic，也不再无锁读取切片头。单线程时 `Pop` 仍按先入先出取出并缩短队列，`Last` 仍只返回队尾且不删除。
- 要动的符号或文件：`Queue.Pop`（`queue/queue.go` 第 25–35 行，空判断在第 26–28 行，`q.items[0]` 在第 31 行）和 `Queue.Last`（第 38–46 行，空判断在第 39–41 行，`len-1` 在第 44 行）。空判断移入已有的 `Lock` / `RLock` 临界区；空队列直接返回。类型注释仍写明这是并发安全队列（第 7 行）。
- 明确不改什么：`Put`、`Copy`、`Len`、`New` 的签名和加锁方式。空队列返回零值。`TestQueueConcurrency` 里消费者直接持有 `q.lock` 改 `items` 的那段可以保留，它不是 `Pop` 的实现。
- 怎么本地验证：已有 `TestQueuePop`、`TestQueuePopEmpty`、`TestQueueLast`、`TestQueuePut`、`TestQueueLen` 继续通过。应新增断言：放入若干互不相同的元素后，多个 goroutine 同时 `Pop` 直到取空，过程不 panic，取出的多重集合与放入的一致；一个 goroutine `Pop` 最后一个元素的同时另一个调用 `Last`，不 panic，`Last` 要么是该元素要么是零值。对 `queue` 包跑 `go test -race`。

## r12-2

- 问题类：hysteria2 把百分号编码后的 userinfo 写进口令；socks/http 再对这串做 base64，纯文本用户名会被解开，带保留字符的密码仍是编码串。
- 不变量：hysteria2 非 realm 分支的 `password` 是解码后的整段 userinfo。只有用户名时等于 `Username()`，所以 `hysteria2://p%40ss@host:443` 的口令是 `p@ss`。`url.Parse` 已分出密码时，口令是 `Username() + ":" + Password()`，所以 `hysteria2://user:p%40ss@host:443` 的口令是 `user:p@ss`，`hysteria2://user:pass@host:443` 的口令仍是 `user:pass`。socks/http 在已分出密码时直接使用 `Username()` 与 `Password()`，`user:p%40ss` 得到用户名 `user`、密码 `p@ss`，`user:p%3Ass` 得到密码 `p:ss`。没有密码时，只有整段用户名经现有 `DecodeBase64` 解开后恰好是一段非空 `user:pass`（一个冒号）才拆成账号和密码，因此 `socks5://dXNlcjpwYXNz@host:1080` 仍得到 `user` / `pass`。否则用户名保持 `Username()`。`user`、`root`、`test`、`pass`、`bob` 保持原样；`p%40ss` 这种只有用户名的链接得到 `p@ss`。百分号只由 `net/url` 解码一次。
- 要动的符号或文件：`ConvertsV2Ray` 里 hysteria2 非 realm 分支（`convert/converter.go` 第 112–113 行，`urlHysteria2.User.String()`）和 socks/http 分支（第 564–575 行，`link.User.String()` 之后 `DecodeBase64` 再 `strings.Split`）。第 570 行的 `QueryUnescape` 注释随这段一起去掉。
- 明确不改什么：`DecodeBase64` 本身（`convert/base64.go` 第 16–33 行），包括订阅正文第 17 行的整段解码，以及解码失败时退回原字节。realm 分支第 105–111 行：token 仍是 `PathUnescape(User.String())`，口令仍来自 query `auth`。tuic（第 136–142 行）、trojan（第 176 行）、anytls（第 604–608 行）、mieru（第 653–654 行）已经使用 `Username()` / `Password()`。不要对 `Username()` 或 `Password()` 的结果再做 `QueryUnescape` 或 `PathUnescape`。
- 怎么本地验证：已有 `TestConvertsV2Ray_normal` 的口令 `letmein`、`TestConvertsV2Ray_hysteria2RealmScheme` 的 query `auth` 继续通过。应新增断言：`hysteria2://p%40ss@host:443` 的 `password` 为 `p@ss`；`hysteria2://user:p%40ss@host:443` 的 `password` 为 `user:p@ss`；`socks5://user@host:1080`、`root`、`test`、`pass`、`bob` 的 `username` 仍是这些单词且 `password` 为空；`socks5://user:p%40ss@host:1080` 与 `http://user:p%3Ass@host:8080` 得到解码后的用户名和密码；`socks5://dXNlcjpwYXNz@host:1080` 仍得到 `user` / `pass`。

## r12-3

- 问题类：`uniqueName` 只在原始名字上计数，生成的 `-01` 后缀不占名，会和订阅里已有的同名节点撞车。
- 不变量：同一次 `ConvertsV2Ray` 里，`uniqueName` 返回的每个名字都是第一次出现。原始名字第一次出现时原样返回，并记入 `names`。再次出现时仍从该原始名字的计数生成 `name-01`、`name-02`……，候选名已经在 `names` 里就继续递增，选中的最终名字也写入 `names`。因此先有 `HK`、又有 `HK-01`、再来一条 `HK` 时，两条原始 `HK` 不会都变成 `HK-01`；三条结果两两不同，第一条 `HK` 保持 `HK`。
- 要动的符号或文件：`uniqueName`（`convert/converter.go` 第 719–728 行）。调用点继续把返回值当作节点名，不用逐个调用点改。
- 明确不改什么：后缀格式仍是 `fmt.Sprintf("%s-%02d", name, index)`，第一条仍不追加后缀。空名字、fragment 的来源、各协议怎么取显示名都不改。
- 怎么本地验证：仓库里没有 `uniqueName` 的测试。已有 `TestConvertsV2Ray_normal`、`TestConvertsV2Ray_hysteria2PortHopping`、`TestConvertsV2RayMieru` 使用各不相同的名字，应继续通过。应新增断言：同一批输入里原始名 `HK` 出现两次且另有一条已叫 `HK-01` 时，三个输出名字两两不同，第一次出现的 `HK` 仍叫 `HK`；只有重复原名、没有预先存在的后缀名时，仍得到原名、`原名-01`、`原名-02`。

## r12-4

- 问题类：带端口跳跃的 hysteria2 IPv6 链接在 `]` 处整段放弃拆分，`url.Parse` 因端口非法失败，节点被 `continue` 丢掉。
- 不变量：`splitHysteria2Ports` 对 `[2001:db8::1]:443,5000-6000` 这种地址，把 `]` 之后的端口列表拆开，URL 里只留第一个端口，返回值的第二段仍是完整端口列表。`hysteria2://letmein@[2001:db8::1]:443,5000-6000/?sni=example.com#hop` 解析为一个节点：`server` 为 `2001:db8::1`，`port` 为 `443`，`ports` 为 `443,5000-6000`，query 和 fragment 保留。方括号内的 zone 留在主机字面量里。没有逗号或连字符的普通 IPv6（例如 `[2001:db8::1]:443`）仍把原链接交给 `url.Parse`。主机名端口跳跃的结果与现在相同。
- 要动的符号或文件：`splitHysteria2Ports`（`convert/v.go` 第 405–407 行，`auth` 含 `]` 时 `return line, ""`）。调用方 `ConvertsV2Ray` 第 74–77 行已经用返回值再 `url.Parse`，拆分正确后不必改 `continue`。
- 明确不改什么：先按 `/?#` 截断、再按最后一个 `@` 分开 userinfo 与 host 的顺序（第 398–404 行）。不含 `]` 的主机名分支（第 408–423 行），包括第一个端口为空时退回原链接。`url.Parse` 失败仍 `continue`。
- 怎么本地验证：已有 `TestConvertsV2Ray_hysteria2PortHopping`（主机名 `example.com` 的 `443,5000-6000`）继续通过。应新增断言：上面的 IPv6 端口跳跃链接得到一个代理，`server`、`port`、`ports` 如上，而不是空结果或 `convert v2ray subscribe error`；`hysteria2://letmein@[2001:db8::1]:443/?sni=example.com#hop` 仍得到 `server` `2001:db8::1` 和 `port` `443`，且没有 `ports`。

## r12-5

- 问题类：`parseUnsigned` / `parseSigned` 固定按 64 位解析，再转成更窄的 `T`，超出位宽的数字被截断且错误为 nil。
- 不变量：解析位宽等于 `T` 的位宽（`unsafe.Sizeof` 的字节数乘 8，对应 strconv 接受的 8、16、32、64）。超出 `T` 时返回错误，不返回截断值。`NewUnsignedRanges[uint16]("65536")` 和 `("70000")` 失败，不再得到 `0` 或 `4464`。`65535` 仍成功，端点是 `65535`。`NewSignedRanges[int16]("32768")` 和 `("-32769")` 失败；`32767` 与 `-32768` 成功。能放进 `uint64` / `int64` 的边界值仍成功。
- 要动的符号或文件：`parseUnsigned` 与 `parseSigned`（`utils/range.go` 第 89–103 行）。`NewUnsignedRange`、`NewSignedRange`、`NewUnsignedRanges`、`NewSignedRanges`、`NewUnsignedRangesFromList`、`NewSignedRangesFromList` 继续把这两个函数传给 `newIntRange` / `newIntRanges`，签名不变。
- 明确不改什么：`newIntRange` 对空字符串返回 `0` 到 `0`、按 `-` 拆两端、多于两段就报错（第 65–86 行）。`IntRanges` 的 `Merge`、`Check`、`Range`。`TestMergeRanges` 的现有区间数据。
- 怎么本地验证：已有 `TestMergeRanges` 继续通过。应新增断言：`NewUnsignedRanges[uint16]("65536")` 与 `("70000")` 返回错误；`("65535")` 成功且区间端点为 `65535`；`NewSignedRanges[int16]("32768")` 与 `("-32769")` 返回错误；`("32767")` 与 `("-32768")` 成功；`NewUnsignedRanges[uint64]` 对 `uint64` 最大值的十进制字符串仍成功。
