# 自定义订阅解析器 (Parsers) 使用指南

本功能允许在拉取远程订阅配置时，通过 URL 参数 `parsers` 指定一个自定义的 YAML 配置文件地址，从而在原始订阅配置的基础上动态插入或追加**节点 (`proxies`)**、**代理组 (`proxy-groups`)**、**指定代理组内的节点**以及**分流规则 (`rules`)**。

---

## 1. 如何使用

### 第一步：编写并托管 Parser 配置文件
编写一个符合下方语法规范的 YAML 文件（例如 `my-parser.yaml`），并将其托管到可以通过 HTTP/HTTPS 访问的地址（例如 GitHub Gist、私有 OSS、Nginx 或自建文件服务）。

### 第二步：在客户端配置 Parser 链接（支持两种方式）

#### 方式一（推荐）：在订阅属性页独立填写「远程脚本链接」
1. 在 Clash Meta For Android 中新建或编辑一个 **URL 类型**的订阅配置（Profile）。
2. 在 **URL（订阅链接）** 下方找到独立的 **「远程脚本链接」（Parser URL）** 配置项。
3. 点击并填入你托管的 Parser 文件地址（如 `https://example.com/my-parser.yaml`），保存并更新订阅即可。
   > **注**：该配置会独立保存在当前订阅目录下的 `parser.url` 文件中，无需污染原订阅链接，且优先级高于方式二。

#### 方式二（兼容旧版）：在订阅 URL 中附加 `parsers` 参数
如果你习惯通过单条链接导入，也可以直接在原本的订阅 URL 后面加上 `parsers=<你的Parser文件URL>` 查询参数（当未填写方式一的「远程脚本链接」时自动生效）：

- **原订阅链接不含参数（无 `?`）：**
  ```text
  https://example.com/subscribe?parsers=https://example.com/my-parser.yaml
  ```
- **原订阅链接已含参数（已有 `?`）：**
  ```text
  https://example.com/subscribe?token=xxxx&parsers=https://example.com/my-parser.yaml
  ```
  > **提示**：如果 `parsers` 的 URL 本身带有查询参数（如 `?raw=true` 或 `?token=xxx`），使用方式一时可直接填写；使用方式二时建议先对 `parsers` 的 URL 进行 URL Encode（百分号编码），避免参数解析冲突。

---

## 2. 支持的操作指令

所有的操作指令均定义在 `parsers.yaml` 节点下，目前支持以下 **8 种**指令（按执行顺序排列）：

| 指令名称 | 作用目标 | 插入位置 | 说明 |
| :--- | :--- | :--- | :--- |
| `prepend-proxies` | `proxies` | **头部** | 向节点列表的最前面插入自定义节点 |
| `append-proxies` | `proxies` | **尾部** | 向节点列表的最后面追加自定义节点 |
| `prepend-proxy-groups` | `proxy-groups` | **头部** | 向代理组列表的最前面插入自定义代理组 |
| `append-proxy-groups` | `proxy-groups` | **尾部** | 向代理组列表的最后面追加自定义代理组 |
| `prepend-rules` | `rules` | **头部** | 向分流规则列表的最前面插入高优先级规则 |
| `append-rules` | `rules` | **尾部** | 向分流规则列表的最后面追加兜底/低优先级规则 |
| `prepend-to-group` | 指定 `proxy-group` 的 `proxies` | **头部** | 按名称 (`group`) 或索引 (`index`) 定位代理组，向其节点列表最前面插入节点 |
| `append-to-group` | 指定 `proxy-group` 的 `proxies` | **尾部** | 按名称 (`group`) 或索引 (`index`) 定位代理组，向其节点列表最后面追加节点 |

> **执行顺序与 `group` / `index` 匹配机制**：
> 1. **执行顺序**：`prepend-proxy-groups` 和 `append-proxy-groups` 会在 `prepend-to-group` 和 `append-to-group` **之前**执行。
> 2. **三种定位方式**（`prepend-to-group` 与 `append-to-group` 均支持）：
>    - **仅指定 `group`**：按代理组名称精确匹配（如 `group: "Proxy"`）。
>    - **仅指定 `index`**：按代理组在 `proxy-groups` 中的下标位置匹配（**从 `0` 开始计数**，且**基于插入新代理组之后**的顺序计算。例如前面通过 `prepend-proxy-groups` 插入了 1 个新代理组，则原订阅的第 1 个代理组此时下标为 `index: 1`）。
>    - **同时指定 `group` 和 `index`（推荐通用写法）**：**优先按 `group` 名称查找**；如果当前订阅中不存在该名称的代理组，则**自动使用 `index` 兜底**。非常适合同一份 Parser 跨多个不同命名的机场订阅通用。

---

## 3. 完整配置示例

以下是一个包含全部常用场景的 `parser.yaml` 模板（所有地址与密钥均为示例占位符）：

```yaml
parsers:
  yaml:
    # 1. 在节点列表头部插入自定义节点
    prepend-proxies:
      - name: "custom-node-alpha"
        type: "trojan"
        server: "203.0.113.10"
        port: 41234
        password: "your_trojan_password_here"
        sni: "sni.example.com"
        skip-cert-verify: false
        udp: true
      - name: "custom-node-beta"
        type: "wireguard"
        private-key: "YOUR_WIREGUARD_PRIVATE_KEY_BASE64="
        server: "wg.example.com"
        port: 37492
        ip: "10.0.0.2"
        public-key: "YOUR_WIREGUARD_PUBLIC_KEY_BASE64="
        persistent-keepalive: 30

    # 2. 在节点列表尾部追加备用节点（可选）
    append-proxies:
      - name: "custom-node-gamma"
        type: "ss"
        server: "198.51.100.20"
        port: 29518
        cipher: "aes-256-gcm"
        password: "your_ss_password_here"

    # 3. 在代理组列表头部插入自定义代理组（执行后该组位于 index: 0，原订阅第一个组顺延为 index: 1）
    prepend-proxy-groups:
      - name: "Custom-Group-A"
        type: "select"
        proxies:
          - "custom-node-beta"
          - "DIRECT"

    # 4. 在代理组列表尾部追加自定义代理组（可选）
    append-proxy-groups:
      - name: "Custom-Group-B"
        type: "url-test"
        url: "http://www.gstatic.com/generate_204"
        interval: 300
        proxies:
          - "custom-node-alpha"
          - "custom-node-gamma"

    # 5. 将指定节点插入到目标代理组【头部】
    prepend-to-group:
      # 写法 A（通用推荐）：优先找名为 "Proxy" 的组；若订阅中没有该名字，则兜底插入到 index 为 1 的组
      - group: "Proxy"
        index: 1
        proxies:
          - "custom-node-alpha"
          - "Custom-Group-B"

    # 6. 将指定节点追加到目标代理组【尾部】
    append-to-group:
      # 写法 B：仅通过 index 指定（例如直接指定下标为 1 的代理组）
      - index: 1
        proxies:
          - "custom-node-gamma"
          - "DIRECT"
      # 写法 C：仅通过 group 名称指定
      - group: "Streaming"
        proxies:
          - "custom-node-alpha"

    # 7. 在规则列表头部插入自定义高优先级规则
    prepend-rules:
      - IP-CIDR,203.0.113.10/32,DIRECT,no-resolve
      - IP-CIDR,10.0.0.0/24,Custom-Group-A
      - DOMAIN-SUFFIX,corp.example.com,Custom-Group-A

    # 8. 在规则列表尾部追加自定义规则（注意：如果原订阅最后一条是 MATCH/FINAL 规则，尾部的规则可能不会被命中）
    append-rules:
      - DOMAIN-SUFFIX,internal.example.org,Custom-Group-A
```

---

## 4. 常见问题与调试

1. **为什么 `append-rules` 追加的规则没有生效？**
   绝大多数机场订阅的 `rules` 列表最后一条都是 `MATCH,...`（兜底规则）。由于 Clash 规则是自上而下按顺序匹配的，追加在 `MATCH` 之后的规则永远不会被触发。自定义分流规则通常建议使用 `prepend-rules` 插入到头部。
2. **如何计算 `prepend-to-group` / `append-to-group` 中的 `index`？**
   `index` 从 `0` 开始，且是**在 `prepend-proxy-groups` 和 `append-proxy-groups` 执行完毕之后**计算的：
   - 如果你没有使用 `prepend-proxy-groups`，那么原订阅的第 1 个代理组就是 `index: 0`。
   - 如果你在 `prepend-proxy-groups` 中插入了 `N` 个代理组，那么原订阅的第 1 个代理组下标就会顺延变成 `index: N`。
3. **如果指定的 `group` 不存在或 `index` 超出范围会怎样？**
   - 同时配置了 `group` 和 `index` 时：先按 `group` 名称匹配；找不到时自动 fallback 按 `index` 匹配。
   - 若仅配置了 `group` 但名称不存在，或 fallback 的 `index` 超出了当前 `proxy-groups` 的长度范围，该条规则会被安全忽略并在 Debug 日志中输出提示，不会导致订阅更新报错。
4. **如何排查 Parser 是否生效？**
   在开启 Debug 日志的情况下，可以在日志中搜索关键字 `[ApplyParsers]`，即可查看从拉取 `parsers` URL、匹配各个操作指令（按 `group` 还是按 `index` 命中）到最终生成完整 YAML 的详细过程。
