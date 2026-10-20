---
title: "审查发现 r12 clash-common"
type: reference
status: draft
phase: N/A
created: 2026-10-20
updated: 2026-10-20
summary: "clash/common 有 5 处确定缺陷：并发队列会 panic，订阅账号被改写或丢弃，重名会碰撞，超位宽整数会被截断。"
---

# 审查发现 r12 clash-common

范围只包括 `core/src/foss/golang/clash/common`。下面 5 条都来自这次读到的源码。

## r12-1

- 编号：r12-1
- 严重度：中
- 症状：并发调用 `Pop` 或 `Last` 时，队列会在还有元素的检查通过之后变成空队列，随后按空切片取下标并 panic。

```25:34:core/src/foss/golang/clash/common/queue/queue.go
func (q *Queue[T]) Pop() (head T) {
	if len(q.items) == 0 {
		return
	}

	q.lock.Lock()
	head = q.items[0]
	q.items = q.items[1:]
	q.lock.Unlock()
	return head
}
```

```38:46:core/src/foss/golang/clash/common/queue/queue.go
func (q *Queue[T]) Last() (last T) {
	if len(q.items) == 0 {
		return
	}

	q.lock.RLock()
	last = q.items[len(q.items)-1]
	q.lock.RUnlock()
	return last
}
```

类型注释写的是并发安全队列，但长度判断在锁外，读的又是会被 `Put`/`Pop` 改掉的切片头。两个 `Pop` 可以同时看见非空，第一个把最后一个元素取走后，第二个在锁内执行 `q.items[0]`，直接越界。`Last` 同样会在加读锁之后对已经空的切片做 `len-1`。同文件的并发测试没有调用 `Pop`，而是自己在锁里改 `items`，说明这条路径会被触发。

方向：把空队列判断放进同一把锁里，空了就返回零值。

## r12-2

- 编号：r12-2
- 严重度：中
- 症状：socks/http 的用户名会被改成另一串字节，带保留字符的 socks、http、hysteria2 密码会变成百分号编码，节点按错误凭证去连。

```112:113:core/src/foss/golang/clash/common/convert/converter.go
			} else if auth := urlHysteria2.User.String(); auth != "" {
				hysteria2["password"] = auth
			}
```

```564:574:core/src/foss/golang/clash/common/convert/converter.go
			encodeStr := link.User.String()
			var username, password string
			if encodeStr != "" {
				decodeStr := string(DecodeBase64([]byte(encodeStr)))
				splitStr := strings.Split(decodeStr, ":")

				// todo: should use url.QueryUnescape ?
				username = splitStr[0]
				if len(splitStr) == 2 {
					password = splitStr[1]
				}
			}
```

`url.User.String()` 返回的是编码后的 userinfo。`hysteria2://p%40ss@host:443` 里 `Username()` 已是 `p@ss`，`String()` 仍是 `p%40ss`，这里把后者写进口令。socks/http 还对这串再做 `DecodeBase64`。没有冒号的用户名只要落在 Raw 标准 base64 里就会被解开，例如 `user`、`root`、`test`、`pass`、`bob` 都会变成另一组字节；有密码时冒号让 base64 失败，但 `%40`、`%3A` 不会被还原，密码仍是编码串。

方向：账号和密码改用 `Username()` 与 `Password()`；只有整段 userinfo 明确是 base64 凭证时才解码。

## r12-3

- 编号：r12-3
- 严重度：中
- 症状：订阅里出现重名时，自动加的 `-01` 后缀会和已有节点同名，导入结果里两个代理共用一个名字。

```719:728:core/src/foss/golang/clash/common/convert/converter.go
func uniqueName(names map[string]int, name string) string {
	if index, ok := names[name]; ok {
		index++
		names[name] = index
		name = fmt.Sprintf("%s-%02d", name, index)
	} else {
		index = 0
		names[name] = index
	}
	return name
}
```

计数只记在原始名字上，生成出来的新名字不会再占一个键。第一条 `HK` 得到 `HK`，第二条 `HK` 得到 `HK-01`；如果订阅里本来就有一条叫 `HK-01`，它会原样返回 `HK-01`。两条代理同名，后面按名字建索引时会互相覆盖，其中一条节点配置丢失。

方向：把最终名字也写进 `names`，仍然冲突就继续递增。

## r12-4

- 编号：r12-4
- 严重度：中
- 症状：带端口跳跃的 hysteria2 IPv6 链接整条被跳过，订阅里看不到这个节点。

```405:407:core/src/foss/golang/clash/common/convert/v.go
	if strings.Contains(auth, "]") {
		return line, ""
	}
```

```74:77:core/src/foss/golang/clash/common/convert/converter.go
			hopLine, ports := splitHysteria2Ports(line)
			urlHysteria2, err := url.Parse(hopLine)
			if err != nil {
				continue
			}
```

主机部分一旦含有 `]`，函数把原链接原样返回，端口列表不会被拆开。`hysteria2://letmein@[2001:db8::1]:443,5000-6000/?sni=example.com#hop` 这种地址的 host 是 `[2001:db8::1]:443,5000-6000`，`url.Parse` 失败，调用方 `continue`，节点不会进入结果。不带逗号或连字符的普通 IPv6 链接仍能解析，所以丢掉的是端口跳跃这一类。

方向：IPv6 同样把 `]` 后面的端口列表拆开，只把第一个端口放回 URL 再解析。

## r12-5

- 编号：r12-5
- 严重度：低
- 症状：用比 `uint64`/`int64` 更窄的类型解析区间时，超出位宽的数字被截断成另一个合法值，规则会去匹配错误的端口或状态码。

```89:103:core/src/foss/golang/clash/common/utils/range.go
func parseUnsigned[T constraints.Unsigned](s string) (T, error) {
	if val, err := strconv.ParseUint(s, 10, 64); err == nil {
		return T(val), nil
	} else {
		return 0, err
	}
}

func parseSigned[T constraints.Signed](s string) (T, error) {
	if val, err := strconv.ParseInt(s, 10, 64); err == nil {
		return T(val), nil
	} else {
		return 0, err
	}
}
```

`ParseUint` / `ParseInt` 的位宽写死为 64，成功后再 `T(val)`。`T` 是 `uint16` 时，`65536` 变成 `0`，`70000` 变成 `4464`，函数仍然返回 nil。`NewUnsignedRanges[uint16]` 会把这个值收成区间。调用方以为写的是越界数字，实际匹配的是截断后的端口。

方向：按 `T` 的位宽解析，超出范围时返回错误。
