JS_BLOCK = r"""
    const state = {
      theme: "dark",
      fullscreenPhone: false,
      running: true,
      mode: "Rule",
      uptimeSeconds: 5072,
      networkStack: "gVisor",
      geoipSource: "MetaCubeX",
      mixedPort: 7890,
      socksPort: 7891,
      restPort: 9090,
      sniffer: true,
      fakeIp: true,
      allowLan: false,

      // Active Outbound Node
      currentNode: {
        id: "hk01",
        group: "PROXY",
        flag: "🇭🇰",
        name: "香港 BGP 01 [IEPL专线]",
        type: "SS",
        ip: "104.28.198.42",
        geo: "Cloudflare HK",
        ping: 38
      },
      currentGroup: "PROXY",
      trafficDown: 2.4 * 1024 * 1024,
      trafficUp: 340 * 1024,
      totalDown: 1420 * 1024 * 1024,
      totalUp: 280 * 1024 * 1024,
      samples: Array.from({ length: 40 }, () => ({ up: 200 + Math.random() * 300, down: 1200 + Math.random() * 2000 })),
      isGrid: true,

      // Proxies List
      nodes: [
        { id: "hk01", group: "PROXY", flag: "🇭🇰", name: "香港 BGP 01 [IEPL专线]", type: "SS", ping: 38, ip: "104.28.198.42", geo: "Cloudflare HK" },
        { id: "hk02", group: "PROXY", flag: "🇭🇰", name: "香港 BGP 02 [直连优化]", type: "Vmess", ping: 45, ip: "104.28.198.50", geo: "HKIX" },
        { id: "hk03", group: "PROXY", flag: "🇭🇰", name: "香港 03 [Netflix/Disney+]", type: "Trojan", ping: 52, ip: "103.21.244.12", geo: "HK Datacenter" },
        { id: "jp01", group: "PROXY", flag: "🇯🇵", name: "日本 东京 01 [低延迟专线]", type: "SS", ping: 68, ip: "172.67.142.11", geo: "Tokyo Equinix" },
        { id: "jp02", group: "PROXY", flag: "🇯🇵", name: "日本 大阪 02 [原生IP]", type: "VLESS", ping: 75, ip: "172.67.142.88", geo: "Osaka Sakura" },
        { id: "sg01", group: "PROXY", flag: "🇸🇬", name: "新加坡 01 [BGP多线]", type: "SS", ping: 62, ip: "104.21.33.19", geo: "Singapore Singtel" },
        { id: "sg02", group: "PROXY", flag: "🇸🇬", name: "新加坡 02 [流媒体解锁]", type: "Hysteria2", ping: 59, ip: "104.21.33.20", geo: "Singapore AWS" },
        { id: "us01", group: "PROXY", flag: "🇺🇸", name: "美国 洛杉矶 01 [GIA优质线路]", type: "SS", ping: 142, ip: "198.41.214.10", geo: "Los Angeles Cera" },
        { id: "us02", group: "PROXY", flag: "🇺🇸", name: "美国 硅谷 02 [ChatGPT专用]", type: "Trojan", ping: 156, ip: "198.41.214.22", geo: "San Jose Equinix" },
        { id: "tw01", group: "PROXY", flag: "🇹🇼", name: "台湾 台北 01 [动画疯解锁]", type: "SS", ping: 48, ip: "104.28.156.12", geo: "Taipei Chunghwa" },
        { id: "de01", group: "🇩🇪", flag: "🇩🇪", name: "德国 法兰克福 01 [欧洲节点]", type: "Vmess", ping: 210, ip: "188.114.97.10", geo: "Frankfurt" },
        { id: "kr01", group: "PROXY", flag: "🇰🇷", name: "韩国 首尔 01 [低延迟]", type: "Trojan", ping: 64, ip: "104.28.12.90", geo: "Seoul KINX" }
      ],

      // PRD-003 App Definitions (Package Name, Label, isSystem, isDomestic)
      apps: [
        { id: "tg", name: "Telegram", pkg: "org.telegram.messenger", icon: "✈️", isSystem: false, isDomestic: false },
        { id: "yt", name: "YouTube", pkg: "com.google.android.youtube", icon: "▶️", isSystem: false, isDomestic: false },
        { id: "tw", name: "X (Twitter)", pkg: "com.twitter.android", icon: "🐦", isSystem: false, isDomestic: false },
        { id: "chrome", name: "Google Chrome", pkg: "com.android.chrome", icon: "🌐", isSystem: true, isDomestic: false },
        { id: "netflix", name: "Netflix", pkg: "com.netflix.mediaclient", icon: "🎬", isSystem: false, isDomestic: false },
        { id: "discord", name: "Discord", pkg: "com.discord", icon: "💬", isSystem: false, isDomestic: false },
        { id: "github", name: "GitHub", pkg: "com.github.android", icon: "🐙", isSystem: false, isDomestic: false },
        { id: "spotify", name: "Spotify", pkg: "com.spotify.music", icon: "🎵", isSystem: false, isDomestic: false },
        { id: "wx", name: "微信", pkg: "com.tencent.mm", icon: "💬", isSystem: false, isDomestic: true },
        { id: "alipay", name: "支付宝", pkg: "com.eg.android.AlipayGphone", icon: "💳", isSystem: false, isDomestic: true },
        { id: "bili", name: "哔哩哔哩", pkg: "tv.danmaku.bili", icon: "📺", isSystem: false, isDomestic: true },
        { id: "taobao", name: "手机淘宝", pkg: "com.taobao.taobao", icon: "🛍️", isSystem: false, isDomestic: true },
        { id: "settings", name: "系统设置", pkg: "com.android.settings", icon: "⚙️", isSystem: true, isDomestic: false },
        { id: "play", name: "Google Play 商店", pkg: "com.android.vending", icon: "🏪", isSystem: true, isDomestic: false },
        { id: "systemui", name: "系统界面 (SystemUI)", pkg: "com.android.systemui", icon: "📱", isSystem: true, isDomestic: false }
      ],

      // PRD-003 Global Access Control Packages
      globalPackages: ["tg", "yt", "tw", "chrome", "netflix", "discord", "github", "spotify"],

      // PRD-003 App Sets (Customizable & Editable App Collections)
      appSets: [
        {
          id: "system",
          name: "系统应用集合",
          icon: "⚙️",
          isBuiltin: true,
          description: "Android 系统底层及系统服务组件 (自动按系统标识归类)",
          packages: ["chrome", "settings", "play", "systemui"]
        },
        {
          id: "domestic",
          name: "国内常用软件",
          icon: "🇨🇳",
          isBuiltin: true,
          description: "基于 domestic_packages.txt 目录，可扩充自定义包名",
          packages: ["wx", "alipay", "bili", "taobao"]
        },
        {
          id: "social",
          name: "海外社交媒体",
          icon: "💬",
          isBuiltin: false,
          description: "Telegram, X (Twitter), Discord 等国际即时通讯与社群",
          packages: ["tg", "tw", "discord"]
        },
        {
          id: "streaming",
          name: "影音流媒体",
          icon: "🎬",
          isBuiltin: false,
          description: "YouTube, Netflix, Spotify 视听娱乐服务",
          packages: ["yt", "netflix", "spotify"]
        },
        {
          id: "developer",
          name: "开发者与办公",
          icon: "💻",
          isBuiltin: false,
          description: "GitHub, Chrome 等工作协同与代码托管工具",
          packages: ["github", "chrome"]
        }
      ],
      editingAppSetId: null,
      newAppSetTempPackages: [],
      editAppSetTempPackages: [],
      appSetPickerKeyword: "",

      // PRD-004 Apps Strategies
      strategies: [
        { id: "s1", name: "海外应用加速", mode: "AcceptSelected", modeDesc: "仅允许选中的应用 (白名单)", packages: ["tg", "yt", "tw", "chrome", "netflix", "discord", "github"] },
        { id: "s2", name: "国内直连绕过", mode: "DenySelected", modeDesc: "仅禁止选中的应用 (黑名单直连)", packages: ["wx", "alipay", "bili", "taobao"] },
        { id: "s3", name: "办公工作区", mode: "AcceptAll", modeDesc: "全部应用允许进入隧道 (全局)", packages: [] }
      ],

      // Currently Active Strategy (UUID or null for global access control fallback)
      activeStrategyUuid: "s1",

      // Currently Editing Target: null (global access control) or strategy ID ("s1", "s2", etc.)
      currentEditingTarget: "s1",

      // Filter States
      hideSystemApps: false,
      appSearchKeyword: "",
      editingStrategyId: null,

      // Providers List
      providers: [
        { id: "p1", name: "海外流媒体分流规则集", type: "Rule Provider", vehicle: "HTTP", count: 1420, updated: "10分钟前", format: "yaml", status: "有效" },
        { id: "p2", name: "广告与追踪过滤规则集", type: "Rule Provider", vehicle: "HTTP", count: 38500, updated: "2小时前", format: "text", status: "有效" },
        { id: "p3", name: "备用专线节点提供者", type: "Proxy Provider", vehicle: "HTTP", count: 18, updated: "昨天 19:30", format: "yaml", status: "有效" }
      ],

      // Active Connections
      connections: [
        { host: "api.openai.com:443", process: "ChatGPT", group: "🤖 AI 智能", node: "香港 BGP 01", speed: "128 KB/s" },
        { host: "rr3---sn-4g5ednks.googlevideo.com:443", process: "YouTube", group: "🎬 流媒体", node: "香港 BGP 02", speed: "3.2 MB/s" },
        { host: "api.telegram.org:443", process: "Telegram", group: "🚀 节点选择", node: "新加坡 01", speed: "14 KB/s" },
        { host: "gateway.discord.gg:443", process: "Discord", group: "🚀 节点选择", node: "日本 东京 01", speed: "42 KB/s" }
      ]
    };

    // DOM Elements
    const heroCard = document.getElementById("heroCard");
    const powerBtn = document.getElementById("powerBtn");
    const connectBadge = document.getElementById("connectBadge");
    const connectStatusText = document.getElementById("connectStatusText");
    const powerHintText = document.getElementById("powerHintText");
    const powerSubhintText = document.getElementById("powerSubhintText");
    const uptimeDisplay = document.getElementById("uptimeDisplay");
    const islandVpnIcon = document.getElementById("islandVpnIcon");
    const outboundCard = document.getElementById("outboundCard");
    const outboundFlag = document.getElementById("outboundFlag");
    const outboundName = document.getElementById("outboundName");
    const outboundPing = document.getElementById("outboundPing");
    const outboundIp = document.getElementById("outboundIp");
    const outboundGeo = document.getElementById("outboundGeo");
    const downRate = document.getElementById("downRate");
    const upRate = document.getElementById("upRate");
    const totalDown = document.getElementById("totalDown");
    const totalUp = document.getElementById("totalUp");
    const toastContainer = document.getElementById("toastContainer");

    // Formatters
    function formatBytes(bytes) {
      if (bytes < 1024) return bytes.toFixed(0) + " B/s";
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB/s";
      return (bytes / (1024 * 1024)).toFixed(2) + " MB/s";
    }

    function formatTotal(bytes) {
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
      if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
      return (bytes / (1024 * 1024 * 1024)).toFixed(2) + " GB";
    }

    function formatTime(sec) {
      const h = Math.floor(sec / 3600).toString().padStart(2, "0");
      const m = Math.floor((sec % 3600) / 60).toString().padStart(2, "0");
      const s = (sec % 60).toString().padStart(2, "0");
      return `${h}:${m}:${s}`;
    }

    // Toast
    function showToast(msg) {
      const toast = document.createElement("div");
      toast.className = "toast";
      toast.innerHTML = `<span>⚡</span><span>${msg}</span>`;
      toastContainer.appendChild(toast);
      setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(-10px)";
        toast.style.transition = "all 0.25s";
        setTimeout(() => toast.remove(), 250);
      }, 2600);
    }

    // Sheet Controls
    function openSheet(id) {
      const sheet = document.getElementById(id);
      const backdrop = document.getElementById("sheetBackdrop");
      if (sheet && backdrop) {
        backdrop.classList.add("show");
        sheet.classList.add("show");
      }
    }

    function closeSheet() {
      document.querySelectorAll(".bottom-sheet").forEach(s => s.classList.remove("show"));
      const backdrop = document.getElementById("sheetBackdrop");
      if (backdrop) backdrop.classList.remove("show");
    }

    // Power Toggle
    powerBtn.addEventListener("click", () => {
      if (state.running) {
        state.running = false;
        powerBtn.classList.remove("running");
        heroCard.classList.remove("running");
        connectBadge.classList.remove("running");
        connectStatusText.textContent = "已断开";
        powerHintText.textContent = "点击连接网络";
        powerSubhintText.textContent = "核心已停止工作";
        islandVpnIcon.classList.remove("active");
        showToast("VPN 隧道已断开");
        addLog("[Core] Clash.Meta tunnel stopped", "warn");
      } else {
        powerBtn.classList.add("connecting");
        powerHintText.textContent = "正在建立安全隧道...";
        setTimeout(() => {
          powerBtn.classList.remove("connecting");
          powerBtn.classList.add("running");
          heroCard.classList.add("running");
          connectBadge.classList.add("running");
          connectStatusText.textContent = "运行中 (VPN)";
          powerHintText.textContent = "安全网络已保护";
          powerSubhintText.textContent = `模式：${state.mode === "Rule" ? "规则分流" : state.mode === "Global" ? "全局代理" : "直接连接"}`;
          islandVpnIcon.classList.add("active");
          state.running = true;
          showToast("已连接到 " + state.currentNode.name);
          addLog("[Core] Clash.Meta tunnel established successfully", "info");
        }, 650);
      }
    });

    // Mode Selector
    document.querySelectorAll(".mode-tab").forEach(tab => {
      tab.addEventListener("click", () => {
        document.querySelectorAll(".mode-tab").forEach(t => t.classList.remove("active"));
        tab.classList.add("active");
        state.mode = tab.dataset.mode;
        showToast(`分流模式已切换为：${tab.textContent}`);
        if (state.running) {
          powerSubhintText.textContent = `模式：${tab.textContent}`;
        }
        addLog(`[Mode] Routing mode switched to [${tab.textContent}]`);
      });
    });

    // Node Switcher
    outboundCard.addEventListener("click", openQuickNodeSwitcher);

    function openQuickNodeSwitcher() {
      const container = document.getElementById("quickNodeList");
      container.innerHTML = state.nodes.map(n => `
        <div class="node-card ${n.id === state.currentNode.id ? "selected" : ""}" style="margin-bottom:8px; cursor:pointer;" onclick="selectNode('${n.id}')">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div class="node-name" style="margin-bottom:0;">
              ${n.flag} ${n.name}
            </div>
            <div class="node-ping ${n.ping < 80 ? "fast" : n.ping < 160 ? "medium" : "slow"}">
              ${n.ping} ms
            </div>
          </div>
          <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;">
            ${n.type} · ${n.geo} (${n.ip})
          </div>
        </div>
      `).join("");

      openSheet("nodeSwitcherSheet");
    }

    function selectNode(nodeId) {
      const node = state.nodes.find(n => n.id === nodeId);
      if (node) {
        state.currentNode = node;
        outboundFlag.textContent = node.flag;
        outboundName.textContent = node.name;
        outboundPing.textContent = node.ping + " ms";
        outboundIp.textContent = node.ip;
        outboundGeo.textContent = node.geo;
        closeSheet();
        renderProxies();
        showToast(`已切换至: ${node.name}`);
        addLog(`[Proxy] Switched outbound node to [${node.name}] (${node.ip})`);
      }
    }

    // Main Tab Switching
    function switchTab(tabId, subTab) {
      document.querySelectorAll(".page-view").forEach(p => p.classList.remove("active"));
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      
      const targetPage = document.getElementById("page-" + tabId);
      if (targetPage) targetPage.classList.add("active");

      const navIndices = { dashboard: 0, proxies: 1, profiles: 2, apps: 3, settings: 4 };
      const items = document.querySelectorAll(".nav-item");
      if (items[navIndices[tabId]]) {
        items[navIndices[tabId]].classList.add("active");
      }
      
      if (tabId === "proxies") {
        if (subTab === "providers") toggleProxySubTab("providers");
        else toggleProxySubTab("proxies");
      }
      if (tabId === "profiles") {
        if (subTab === "files") toggleProfileSubTab("files");
        else toggleProfileSubTab("profiles");
      }
      if (tabId === "apps") {
        if (subTab === "accesslist") toggleStrategySubTab("accesslist");
        else toggleStrategySubTab("strategies");
      }
    }

    // SubTabs
    function toggleProxySubTab(subTab) {
      const tabA = document.getElementById("subTabProxies");
      const tabB = document.getElementById("subTabProviders");
      const secA = document.getElementById("proxySectionA");
      const secB = document.getElementById("proxySectionB");

      if (subTab === "providers") {
        tabA.classList.remove("active");
        tabB.classList.add("active");
        secA.style.display = "none";
        secB.style.display = "block";
        renderProviders();
      } else {
        tabA.classList.add("active");
        tabB.classList.remove("active");
        secA.style.display = "block";
        secB.style.display = "none";
        renderProxies();
      }
    }

    function toggleProfileSubTab(subTab) {
      const tabA = document.getElementById("subTabProfiles");
      const tabB = document.getElementById("subTabFiles");
      const secA = document.getElementById("profileSectionA");
      const secB = document.getElementById("profileSectionB");

      if (subTab === "files") {
        tabA.classList.remove("active");
        tabB.classList.add("active");
        secA.style.display = "none";
        secB.style.display = "block";
      } else {
        tabA.classList.add("active");
        tabB.classList.remove("active");
        secA.style.display = "block";
        secB.style.display = "none";
      }
    }

    function toggleStrategySubTab(subTab) {
      const tabA = document.getElementById("subTabStrategyList");
      const tabS = document.getElementById("subTabAppSets");
      const tabB = document.getElementById("subTabAccessList");
      const secA = document.getElementById("strategySectionA");
      const secS = document.getElementById("strategySectionSets");
      const secB = document.getElementById("strategySectionB");

      [tabA, tabS, tabB].forEach(t => t && t.classList.remove("active"));
      [secA, secS, secB].forEach(s => s && (s.style.display = "none"));

      if (subTab === "appsets") {
        if (tabS) tabS.classList.add("active");
        if (secS) secS.style.display = "block";
        renderAppSetsManagement();
      } else if (subTab === "accesslist") {
        if (tabB) tabB.classList.add("active");
        if (secB) secB.style.display = "block";
        renderApps();
      } else {
        if (tabA) tabA.classList.add("active");
        if (secA) secA.style.display = "block";
        renderStrategies();
      }
    }

    // Proxies Render
    function renderProxies() {
      const container = document.getElementById("nodeContainer");
      const search = (document.getElementById("proxySearchInput").value || "").toLowerCase();
      
      const filtered = state.nodes.filter(n => {
        return n.name.toLowerCase().includes(search) || n.type.toLowerCase().includes(search) || n.geo.toLowerCase().includes(search);
      });

      container.className = "proxy-grid" + (state.isGrid ? "" : " list-view");
      container.innerHTML = filtered.map(n => {
        const isSel = (n.name === state.currentNode.name);
        const pingClass = n.ping < 80 ? "fast" : n.ping < 160 ? "medium" : "slow";
        return `
          <div class="node-card ${isSel ? "selected" : ""}" onclick="selectNode('${n.id}')">
            <div class="node-name">${n.flag} ${n.name}</div>
            <div class="node-footer">
              <span class="node-type-pill">${n.type}</span>
              <span class="node-ping ${pingClass}">
                <span style="font-size:9px;">●</span> ${n.ping} ms
              </span>
            </div>
          </div>
        `;
      }).join("");
    }

    document.getElementById("proxySearchInput").addEventListener("input", renderProxies);

    document.getElementById("btnToggleLayout").addEventListener("click", () => {
      state.isGrid = !state.isGrid;
      renderProxies();
      showToast(state.isGrid ? "已切换至网格卡片视图" : "已切换至紧凑列表视图");
    });

    document.getElementById("btnUrlTest").addEventListener("click", () => {
      showToast("正在并发测速全部 12 个节点...");
      addLog("[Test] Initiated parallel URL-test across 12 endpoints");
      
      document.querySelectorAll(".node-ping").forEach(el => {
        el.innerHTML = `<span style="font-size:10px;">⏳ 测速中</span>`;
      });

      setTimeout(() => {
        state.nodes.forEach(n => {
          n.ping = Math.floor(28 + Math.random() * 190);
        });
        state.nodes.sort((a, b) => a.ping - b.ping);
        renderProxies();
        showToast("测速完成，节点已按延迟自动升序排列！");
      }, 850);
    });

    // Providers Render
    function renderProviders() {
      const container = document.getElementById("providersListContainer");
      if (!container) return;
      container.innerHTML = state.providers.map((p, idx) => `
        <div class="card" style="margin-bottom:12px; padding:14px;">
          <div style="display:flex; justify-content:space-between; align-items:flex-start;">
            <div>
              <div style="font-size:14px; font-weight:700;">${p.name}</div>
              <div style="font-size:11px; color:var(--text-secondary); margin-top:3px;">
                类型：${p.type} · 载体：${p.vehicle} (${p.format})
              </div>
            </div>
            <span class="demo-feature-pill" style="color:var(--accent-green); background:var(--accent-green-subtle);">
              ${p.status}
            </span>
          </div>

          <div style="display:flex; justify-content:space-between; align-items:center; margin-top:10px; padding-top:8px; border-top:1px solid var(--card-border); font-size:11px;">
            <div style="color:var(--text-tertiary);">
              规则/节点数: <b style="color:var(--text-primary);">${p.count}</b> 条 · 更新于 ${p.updated}
            </div>
            <div style="display:flex; gap:6px;">
              <button class="demo-btn" style="padding:4px 8px; font-size:11px;" onclick="updateSingleProvider('${p.id}')">
                健康检查
              </button>
              <button class="demo-btn" style="padding:4px 8px; font-size:11px; background:var(--accent-blue-subtle); color:var(--accent-blue);" onclick="updateSingleProvider('${p.id}', true)">
                更新
              </button>
            </div>
          </div>
        </div>
      `).join("");
    }

    function updateSingleProvider(id, isForce = false) {
      const p = state.providers.find(item => item.id === id);
      if (p) {
        showToast(`正在更新提供者 [${p.name}]...`);
        addLog(`[Provider] Updating provider ${p.name} (${p.type})`);
        setTimeout(() => {
          p.updated = "刚刚";
          renderProviders();
          showToast(`[${p.name}] 更新成功，已拉取 ${p.count} 条规则`);
        }, 600);
      }
    }

    function updateAllProviders() {
      showToast("正在全量更新所有 Providers...");
      addLog("[Provider] Bulk updating all Rule & Proxy Providers", "warn");
      setTimeout(() => {
        state.providers.forEach(p => p.updated = "刚刚");
        renderProviders();
        showToast("全量提供者更新完毕，核心规则树已重构");
      }, 900);
    }

    // =========================================================================
    // APPS STRATEGY & ACCESS CONTROL (PRD-003 & PRD-004 TIIEHenry Fork Core)
    // =========================================================================

    function selectActiveStrategy(uuid) {
      state.activeStrategyUuid = uuid;
      renderStrategies();
      
      const sDesc = document.getElementById("settingsStrategyDesc");
      if (uuid === null) {
        showToast("已切换为【全局访问控制名单】，内核已热重载");
        addLog("[Strategy] Deactivated all named strategies, falling back to global accessControlPackages", "info");
        if (sDesc) sDesc.textContent = "当前使用全局访问控制名单";
      } else {
        const s = state.strategies.find(x => x.id === uuid);
        showToast(`已激活分流策略【${s.name}】(内核 TUN 已热重载)`);
        addLog(`[Strategy] Active strategy set to [${s.name}] (${s.mode}), reloading TUN filter with ${s.packages.length} apps`, "info");
        if (sDesc) sDesc.textContent = `当前由分流策略 [${s.name}] 接管`;
      }
    }

    function renderStrategies() {
      // 1. Global Card
      const globalCard = document.getElementById("globalAccessCard");
      const globalRadio = document.getElementById("globalAccessRadio");
      const globalDesc = document.getElementById("globalAccessDesc");
      
      const isGlobalActive = (state.activeStrategyUuid === null);
      if (globalCard) {
        globalCard.classList.toggle("active-strat", isGlobalActive);
      }
      if (globalRadio) {
        globalRadio.textContent = isGlobalActive ? "●" : "";
      }
      if (globalDesc) {
        globalDesc.textContent = `模式：仅允许选中的应用进入隧道 (白名单) · 已选 ${state.globalPackages.length} 个应用`;
      }

      // Update Dashboard Bento card
      const dashStatus = document.getElementById("dashStrategyStatus");
      const dashDesc = document.getElementById("dashStrategyDesc");
      if (dashDesc) {
        if (state.activeStrategyUuid === null) {
          if (dashStatus) dashStatus.textContent = "全局名单";
          dashDesc.innerHTML = `当前：<b>全局基础名单</b><br>${state.globalPackages.length} 款已选 · 白名单`;
        } else {
          const activeS = state.strategies.find(s => s.id === state.activeStrategyUuid);
          if (activeS) {
            if (dashStatus) dashStatus.textContent = "已生效";
            const modeLabel = activeS.mode === "AcceptSelected" ? "白名单代理" : activeS.mode === "DenySelected" ? "黑名单直连" : "全局放行";
            dashDesc.innerHTML = `当前：<b>${activeS.name}</b><br>${activeS.packages.length} 款已选 · ${modeLabel}`;
          }
        }
      }

      // 2. Custom Named Strategies
      const container = document.getElementById("strategyConfigsContainer");
      if (!container) return;

      container.innerHTML = state.strategies.map((strat) => {
        const isActive = (state.activeStrategyUuid === strat.id);
        const modeBadgeClass = strat.mode === "AcceptSelected" ? "var(--accent-blue)" : strat.mode === "DenySelected" ? "var(--accent-orange)" : "var(--accent-green)";
        const modeBadgeBg = strat.mode === "AcceptSelected" ? "var(--accent-blue-subtle)" : strat.mode === "DenySelected" ? "var(--accent-orange-subtle)" : "var(--accent-green-subtle)";
        
        // Find matched app sets
        const matchedSetNames = state.appSets.filter(set => {
          return set.packages.length > 0 && set.packages.every(p => strat.packages.includes(p));
        }).map(s => s.icon + " " + s.name);

        const setHint = matchedSetNames.length > 0 ? ` · 覆盖: ${matchedSetNames.slice(0, 2).join(", ")}` : "";

        return `
          <div class="strategy-card ${isActive ? "active-strat" : ""}" onclick="selectActiveStrategy('${strat.id}')">
            <div style="display:flex; justify-content:space-between; align-items:flex-start;">
              <div>
                <div style="display:flex; align-items:center; gap:8px;">
                  <div style="font-size:14px; font-weight:700;">${strat.name}</div>
                  <span class="demo-tag" style="background:${modeBadgeBg}; color:${modeBadgeClass}; font-weight:700;">
                    ${strat.mode === "AcceptSelected" ? "白名单" : strat.mode === "DenySelected" ? "黑名单直连" : "全局放行"}
                  </span>
                  ${isActive ? '<span class="demo-tag" style="background:rgba(48,209,88,0.15); color:var(--accent-green); font-weight:700;">● 生效中</span>' : ''}
                </div>
                <div style="font-size:11px; color:var(--text-secondary); margin-top:3px;">${strat.modeDesc}</div>
              </div>
              <div class="radio-indicator">${isActive ? "●" : ""}</div>
            </div>

            <div style="display:flex; justify-content:space-between; align-items:center; margin-top:12px; padding-top:8px; border-top:1px solid var(--card-border); font-size:11px;">
              <span style="color:var(--text-tertiary);">
                已勾选 <b style="color:var(--text-primary);">${strat.packages.length}</b> 款应用${setHint}
              </span>
              <div style="display:flex; gap:6px;">
                <button class="demo-btn" style="padding:4px 8px; font-size:11px;" onclick="event.stopPropagation(); openEditStrategyModal('${strat.id}')">
                  编辑/管理
                </button>
                <button class="demo-btn" style="padding:4px 8px; font-size:11px; background:var(--accent-blue); color:#fff;" onclick="event.stopPropagation(); editStrategyApps('${strat.id}')">
                  配置名单
                </button>
              </div>
            </div>
          </div>
        `;
      }).join("");
    }

    // Dedicated App Sets Management Screen (PRD-003)
    function renderAppSetsManagement() {
      const container = document.getElementById("appSetsManagementContainer");
      if (!container) return;

      container.innerHTML = state.appSets.map(set => {
        const memberApps = set.packages.map(pkgId => state.apps.find(a => a.id === pkgId)).filter(Boolean);
        const previewPills = memberApps.slice(0, 6).map(a => `
          <span class="app-set-member-pill">${a.icon} ${a.name}</span>
        `).join("");
        const moreCount = memberApps.length > 6 ? `<span class="app-set-member-pill">+${memberApps.length - 6}</span>` : "";

        return `
          <div class="app-set-mgmt-card">
            <div class="app-set-mgmt-header">
              <div class="app-set-mgmt-title-group">
                <div class="app-set-mgmt-icon">${set.icon}</div>
                <div>
                  <div style="display:flex; align-items:center; gap:6px;">
                    <span class="app-set-mgmt-title">${set.name}</span>
                    <span class="demo-tag" style="background:${set.isBuiltin ? 'rgba(255,255,255,0.08)' : 'rgba(191,90,242,0.15); color:var(--accent-purple)'};">
                      ${set.isBuiltin ? '内置基础集合' : '自定义集合'}
                    </span>
                  </div>
                  <div class="app-set-mgmt-desc">${set.description}</div>
                </div>
              </div>
            </div>

            <div class="app-set-member-preview">
              ${previewPills || '<span style="font-size:11px; color:var(--text-tertiary);">暂无成员应用，请点击编辑添加</span>'}
              ${moreCount}
            </div>

            <div class="app-set-mgmt-footer">
              <span>共包含 <b style="color:var(--text-primary);">${set.packages.length}</b> 款应用</span>
              <div style="display:flex; gap:6px;">
                <button class="app-set-edit-btn" onclick="openEditAppSetModal('${set.id}')">
                  ✏️ 编辑成员与属性
                </button>
                ${!set.isBuiltin ? `
                  <button class="app-set-edit-btn" style="color:var(--accent-red); border-color:var(--accent-red-subtle);" onclick="deleteAppSetDirect('${set.id}')">
                    🗑️ 删除
                  </button>
                ` : ''}
              </div>
            </div>
          </div>
        `;
      }).join("");
    }

    // Open/Close and Manage App Set Modals
    function openNewAppSetModal() {
      state.newAppSetTempPackages = [];
      document.getElementById("newAppSetName").value = "";
      document.getElementById("newAppSetIcon").value = "📦";
      document.getElementById("newAppSetDesc").value = "";
      document.getElementById("newAppSetSearchInput").value = "";
      state.appSetPickerKeyword = "";
      renderNewAppSetPickList("");
      openSheet("newAppSetSheet");
    }

    function onFilterNewAppSetPicker(kw) {
      state.appSetPickerKeyword = kw.trim().toLowerCase();
      renderNewAppSetPickList(state.appSetPickerKeyword);
    }

    function renderNewAppSetPickList(kw) {
      const container = document.getElementById("newAppSetPickList");
      if (!container) return;

      const filtered = state.apps.filter(a => {
        if (!kw) return true;
        return a.name.toLowerCase().includes(kw) || a.pkg.toLowerCase().includes(kw);
      });

      const countEl = document.getElementById("newAppSetPickCount");
      if (countEl) countEl.textContent = state.newAppSetTempPackages.length;

      if (filtered.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:20px; font-size:11px; color:var(--text-tertiary);">无匹配应用</div>`;
        return;
      }

      container.innerHTML = filtered.map(app => {
        const isSelected = state.newAppSetTempPackages.includes(app.id);
        return `
          <div class="app-pick-row" onclick="toggleNewAppSetPick('${app.id}')">
            <div class="app-pick-info">
              <span>${app.icon}</span>
              <div>
                <div class="app-pick-name">${app.name}</div>
                <div class="app-pick-pkg">${app.pkg}</div>
              </div>
            </div>
            <div class="checkbox ${isSelected ? 'checked' : ''}">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
          </div>
        `;
      }).join("");
    }

    function toggleNewAppSetPick(appId) {
      const idx = state.newAppSetTempPackages.indexOf(appId);
      if (idx !== -1) {
        state.newAppSetTempPackages.splice(idx, 1);
      } else {
        state.newAppSetTempPackages.push(appId);
      }
      renderNewAppSetPickList(state.appSetPickerKeyword);
    }

    function submitNewAppSet() {
      const name = document.getElementById("newAppSetName").value.trim();
      const icon = document.getElementById("newAppSetIcon").value.trim() || "📦";
      const desc = document.getElementById("newAppSetDesc").value.trim() || "自定义应用集合";

      if (!name) {
        showToast("请输入应用集合名称");
        return;
      }

      const newSet = {
        id: "set_" + Date.now().toString(36),
        name: name,
        icon: icon,
        isBuiltin: false,
        description: desc,
        packages: [...state.newAppSetTempPackages]
      };

      state.appSets.push(newSet);
      closeSheet("newAppSetSheet");
      showToast(`已成功创建应用集合【${name}】(含 ${newSet.packages.length} 款应用)`);
      addLog(`[AppSet] Created custom app set [${name}] with ${newSet.packages.length} packages`, "info");
      
      renderAppSetsManagement();
      renderApps();
      renderStrategies();
    }

    function openEditAppSetModal(setId) {
      const set = state.appSets.find(s => s.id === setId);
      if (!set) return;

      state.editingAppSetId = setId;
      state.editAppSetTempPackages = [...set.packages];
      state.appSetPickerKeyword = "";

      const titleEl = document.getElementById("editAppSetSheetTitle");
      if (titleEl) titleEl.textContent = `编辑应用集 · ${set.name}`;

      const nameInput = document.getElementById("editAppSetName");
      const iconInput = document.getElementById("editAppSetIcon");
      const descInput = document.getElementById("editAppSetDesc");
      const searchInput = document.getElementById("editAppSetSearchInput");
      const delBtn = document.getElementById("btnDeleteAppSet");

      if (nameInput) nameInput.value = set.name;
      if (iconInput) iconInput.value = set.icon;
      if (descInput) descInput.value = set.description;
      if (searchInput) searchInput.value = "";

      // Built-in sets cannot be deleted
      if (delBtn) {
        delBtn.style.display = set.isBuiltin ? "none" : "block";
      }

      renderEditAppSetPickList("");
      openSheet("editAppSetSheet");
    }

    function onFilterEditAppSetPicker(kw) {
      state.appSetPickerKeyword = kw.trim().toLowerCase();
      renderEditAppSetPickList(state.appSetPickerKeyword);
    }

    function renderEditAppSetPickList(kw) {
      const container = document.getElementById("editAppSetPickList");
      if (!container) return;

      const filtered = state.apps.filter(a => {
        if (!kw) return true;
        return a.name.toLowerCase().includes(kw) || a.pkg.toLowerCase().includes(kw);
      });

      const countEl = document.getElementById("editAppSetPickCount");
      if (countEl) countEl.textContent = state.editAppSetTempPackages.length;

      if (filtered.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:20px; font-size:11px; color:var(--text-tertiary);">无匹配应用</div>`;
        return;
      }

      container.innerHTML = filtered.map(app => {
        const isSelected = state.editAppSetTempPackages.includes(app.id);
        return `
          <div class="app-pick-row" onclick="toggleEditAppSetPick('${app.id}')">
            <div class="app-pick-info">
              <span>${app.icon}</span>
              <div>
                <div class="app-pick-name">${app.name}</div>
                <div class="app-pick-pkg">${app.pkg}</div>
              </div>
            </div>
            <div class="checkbox ${isSelected ? 'checked' : ''}">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
          </div>
        `;
      }).join("");
    }

    function toggleEditAppSetPick(appId) {
      const idx = state.editAppSetTempPackages.indexOf(appId);
      if (idx !== -1) {
        state.editAppSetTempPackages.splice(idx, 1);
      } else {
        state.editAppSetTempPackages.push(appId);
      }
      renderEditAppSetPickList(state.appSetPickerKeyword);
    }

    function saveAppSetEdits() {
      const set = state.appSets.find(s => s.id === state.editingAppSetId);
      if (!set) return;

      const name = document.getElementById("editAppSetName").value.trim();
      const icon = document.getElementById("editAppSetIcon").value.trim();
      const desc = document.getElementById("editAppSetDesc").value.trim();

      if (!name) {
        showToast("集合名称不可为空");
        return;
      }

      set.name = name;
      if (icon) set.icon = icon;
      set.description = desc;
      set.packages = [...state.editAppSetTempPackages];

      closeSheet("editAppSetSheet");
      showToast(`已保存应用集【${set.name}】修改 (成员已更新为 ${set.packages.length} 款)`);
      addLog(`[AppSet] Updated set [${set.name}], total members: ${set.packages.length}`, "info");

      renderAppSetsManagement();
      renderApps();
      renderStrategies();
    }

    function deleteCurrentAppSet() {
      if (!state.editingAppSetId) return;
      deleteAppSetDirect(state.editingAppSetId);
      closeSheet("editAppSetSheet");
    }

    function deleteAppSetDirect(setId) {
      const set = state.appSets.find(s => s.id === setId);
      if (!set || set.isBuiltin) {
        showToast("内置基础集合不支持删除");
        return;
      }

      if (confirm(`确定要删除自定义应用集【${set.name}】吗？此操作不会删除已安装应用。`)) {
        state.appSets = state.appSets.filter(s => s.id !== setId);
        showToast(`已删除应用集【${set.name}】`);
        addLog(`[AppSet] Deleted custom app set [${set.name}]`, "warn");
        renderAppSetsManagement();
        renderApps();
        renderStrategies();
      }
    }

    function editStrategyApps(targetId) {
      state.currentEditingTarget = targetId;
      toggleStrategySubTab("accesslist");

      const titleEl = document.getElementById("currentEditingStrategyTitle");
      const descEl = document.getElementById("currentEditingStrategyModeDesc");
      const activeBadge = document.getElementById("currentEditingStrategyActiveBadge");

      if (targetId === null) {
        titleEl.textContent = "全局访问控制名单";
        descEl.textContent = "当前模式：仅允许选中的应用进入隧道 (白名单)";
        activeBadge.style.display = (state.activeStrategyUuid === null) ? "inline-block" : "none";
      } else {
        const s = state.strategies.find(x => x.id === targetId);
        if (s) {
          titleEl.textContent = `${s.name} · 名单配置`;
          descEl.textContent = `当前模式：${s.modeDesc}`;
          activeBadge.style.display = (state.activeStrategyUuid === s.id) ? "inline-block" : "none";
        }
      }

      state.appSearchKeyword = "";
      const searchInput = document.getElementById("appSearchInput");
      if (searchInput) searchInput.value = "";

      renderApps();
    }

    function getCurrentTargetSet() {
      if (state.currentEditingTarget === null) {
        return state.globalPackages;
      }
      const s = state.strategies.find(x => x.id === state.currentEditingTarget);
      return s ? s.packages : state.globalPackages;
    }

    // PRD-003 Dynamic App Sets & App List Rendering
    function renderApps() {
      const currentSet = getCurrentTargetSet();

      // 1. Render Dynamic App Sets Quick-Bar (PRD-003 & App Sets)
      const setsContainer = document.getElementById("appSetsQuickBarContainer");
      if (setsContainer) {
        setsContainer.innerHTML = state.appSets.map(set => {
          const totalMembers = set.packages.length;
          const selectedMembers = set.packages.filter(p => currentSet.includes(p)).length;
          
          let stateClass = "";
          if (totalMembers > 0 && selectedMembers === totalMembers) {
            stateClass = "checked";
          } else if (selectedMembers > 0) {
            stateClass = "indeterminate";
          }

          return `
            <div class="app-set-card" onclick="toggleAppSet('${set.id}')">
              <div class="app-set-info">
                <div class="app-set-icon" style="background:var(--card-subtle);">${set.icon}</div>
                <div>
                  <div class="app-set-title">
                    ${set.name}
                    ${set.isBuiltin ? '' : '<span class="demo-tag" style="background:rgba(191,90,242,0.12); color:var(--accent-purple); font-size:9px; margin-left:4px;">自定义</span>'}
                  </div>
                  <div class="app-set-subtitle">成员共 ${totalMembers} 款 · 当前已勾选 ${selectedMembers} 款</div>
                </div>
              </div>
              <div class="app-set-actions" onclick="event.stopPropagation()">
                <button class="app-set-edit-btn" onclick="openEditAppSetModal('${set.id}')" title="编辑该集合成员与属性">
                  ✏️ 编辑
                </button>
                <div class="checkbox ${stateClass}" onclick="toggleAppSet('${set.id}')">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
              </div>
            </div>
          `;
        }).join("");
      }

      // 2. Count Totals
      const countEl = document.getElementById("selectedAppCount");
      const totalEl = document.getElementById("totalAppCount");
      if (countEl) countEl.textContent = currentSet.length;
      if (totalEl) totalEl.textContent = state.apps.length;

      // 3. Filter Apps List
      const container = document.getElementById("appListContainer");
      if (!container) return;

      const filtered = state.apps.filter(a => {
        if (state.hideSystemApps && a.isSystem) return false;
        if (state.appSearchKeyword) {
          const kw = state.appSearchKeyword;
          return a.name.toLowerCase().includes(kw) || a.pkg.toLowerCase().includes(kw);
        }
        return true;
      });

      if (filtered.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-secondary); font-size:12px;">无匹配应用</div>`;
        return;
      }

      container.innerHTML = filtered.map(app => {
        const isChecked = currentSet.includes(app.id);
        const matchedSets = state.appSets.filter(s => s.packages.includes(app.id));
        const setBadges = matchedSets.map(s => `
          <span class="app-tag-pill" style="background:rgba(255,255,255,0.06); color:var(--text-secondary);">${s.icon} ${s.name}</span>
        `).join("");

        return `
          <div class="app-item-row" onclick="toggleApp('${app.id}')">
            <div class="app-info">
              <div class="app-icon">${app.icon}</div>
              <div>
                <div class="app-name">
                  ${app.name}
                  ${app.isSystem ? '<span class="app-tag-pill app-tag-system">系统</span>' : ''}
                  ${app.isDomestic ? '<span class="app-tag-pill app-tag-domestic">国内</span>' : ''}
                  ${setBadges}
                </div>
                <div class="app-pkg">${app.pkg}</div>
              </div>
            </div>
            <div class="checkbox ${isChecked ? "checked" : ""}">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
          </div>
        `;
      }).join("");
    }

    // PRD-003 applyAppSetToggle on dynamic sets
    function toggleAppSet(setId) {
      const currentSet = getCurrentTargetSet();
      const targetSet = state.appSets.find(s => s.id === setId);
      if (!targetSet || targetSet.packages.length === 0) {
        showToast("该应用集合内暂无成员应用");
        return;
      }

      const memberIds = targetSet.packages;
      const allSelected = memberIds.every(id => currentSet.includes(id));

      if (allSelected) {
        // Checking a fully selected set clears only those members
        memberIds.forEach(id => {
          const idx = currentSet.indexOf(id);
          if (idx !== -1) currentSet.splice(idx, 1);
        });
        showToast(`已取消【${targetSet.name}】的勾选`);
        addLog(`[AccessControl] Deselected ${memberIds.length} apps from set [${targetSet.name}]`, "info");
      } else {
        // Checking a partial or empty selection selects every current member
        memberIds.forEach(id => {
          if (!currentSet.includes(id)) currentSet.push(id);
        });
        showToast(`已全选【${targetSet.name}】(${memberIds.length}款应用)`);
        addLog(`[AccessControl] Selected all ${memberIds.length} apps in set [${targetSet.name}]`, "info");
      }

      renderApps();
      renderStrategies();
    }

    function toggleApp(id) {
      const currentSet = getCurrentTargetSet();
      const idx = currentSet.indexOf(id);
      const app = state.apps.find(a => a.id === id);

      if (idx !== -1) {
        currentSet.splice(idx, 1);
        showToast(`${app ? app.name : id} 已从分流名单移除`);
      } else {
        currentSet.push(id);
        showToast(`${app ? app.name : id} 已加入分流名单`);
      }

      renderApps();
      renderStrategies();
    }

    function selectAllApps(select) {
      const currentSet = getCurrentTargetSet();
      if (select) {
        state.apps.forEach(a => {
          if (!currentSet.includes(a.id)) currentSet.push(a.id);
        });
        showToast("已全选当前所有应用名单");
        addLog("[AccessControl] Selected all installed applications", "info");
      } else {
        currentSet.length = 0;
        showToast("已清空所有已选应用");
        addLog("[AccessControl] Cleared all application selections", "info");
      }
      renderApps();
      renderStrategies();
    }

    function invertAppSelection() {
      const currentSet = getCurrentTargetSet();
      const allIds = state.apps.map(a => a.id);
      
      allIds.forEach(id => {
        const idx = currentSet.indexOf(id);
        if (idx !== -1) {
          currentSet.splice(idx, 1);
        } else {
          currentSet.push(id);
        }
      });

      showToast("已反向选择应用名单");
      addLog("[AccessControl] Inverted app selection", "info");
      renderApps();
      renderStrategies();
    }

    function onAppSearch(kw) {
      state.appSearchKeyword = kw.trim().toLowerCase();
      renderApps();
    }

    function toggleHideSystem() {
      const sw = document.getElementById("hideSystemSwitch");
      state.hideSystemApps = sw ? sw.checked : false;
      renderApps();
      showToast(state.hideSystemApps ? "已隐藏系统自带应用行" : "已显示系统自带应用行");
    }

    function openNewStrategyModal() {
      openSheet("newStrategySheet");
    }

    function submitNewStrategy() {
      const name = document.getElementById("newStratName").value.trim() || "新建分流策略";
      const mode = document.getElementById("newStratMode").value;
      const active = document.getElementById("newStratActive").checked;
      const modeDesc = mode === "AcceptSelected" ? "仅允许选中的应用 (白名单)" : mode === "DenySelected" ? "仅禁止选中的应用 (黑名单直连)" : "全部应用允许进入隧道 (全局)";

      const newId = "s_" + Date.now();
      state.strategies.push({
        id: newId,
        name: name,
        mode: mode,
        modeDesc: modeDesc,
        packages: []
      });

      if (active) {
        state.activeStrategyUuid = newId;
      }

      closeSheet();
      renderStrategies();
      showToast(`分流策略【${name}】创建成功，现可配置应用名单`);
      addLog(`[Strategy] Created new strategy: ${name} (${mode})`, "info");
      
      // Auto open access list for the new strategy
      editStrategyApps(newId);
    }

    function openEditStrategyModal(stratId) {
      state.editingStrategyId = stratId;
      const s = state.strategies.find(x => x.id === stratId);
      if (!s) return;

      document.getElementById("editStratName").value = s.name;
      document.getElementById("editStratMode").value = s.mode;
      openSheet("strategyEditSheet");
    }

    function saveStrategyEdits() {
      const s = state.strategies.find(x => x.id === state.editingStrategyId);
      if (!s) return;

      const newName = document.getElementById("editStratName").value.trim() || s.name;
      const newMode = document.getElementById("editStratMode").value;

      s.name = newName;
      s.mode = newMode;
      s.modeDesc = newMode === "AcceptSelected" ? "仅允许选中的应用 (白名单)" : newMode === "DenySelected" ? "仅禁止选中的应用 (黑名单直连)" : "全部应用允许进入隧道 (全局)";

      closeSheet();
      renderStrategies();
      showToast(`分流策略【${s.name}】修改已生效`);
      addLog(`[Strategy] Updated strategy ${s.id}: name=${s.name}, mode=${s.mode}`);
    }

    function deleteCurrentStrategy() {
      const s = state.strategies.find(x => x.id === state.editingStrategyId);
      if (!s) return;

      state.strategies = state.strategies.filter(x => x.id !== s.id);
      if (state.activeStrategyUuid === s.id) {
        state.activeStrategyUuid = null;
        showToast(`已删除策略【${s.name}】，已自动回退为全局名单`);
      } else {
        showToast(`策略【${s.name}】已成功删除`);
      }

      closeSheet();
      renderStrategies();
      addLog(`[Strategy] Deleted strategy ${s.id}`, "warn");
    }

    // Profiles Operations
    function updateSingleProfile(e, profId) {
      if (e) e.stopPropagation();
      const btn = e ? e.currentTarget : null;
      if (btn) {
        btn.style.transform = "rotate(360deg)";
        btn.style.transition = "transform 0.6s ease";
      }
      showToast("正在拉取最新订阅规则与节点配置...");
      addLog("[Profile] Fetching remote subscription update", "info");
      setTimeout(() => {
        if (btn) btn.style.transform = "";
        showToast("订阅更新成功！节点列表已刷新");
      }, 700);
    }

    function activateProfile(profId) {
      document.querySelectorAll(".profile-card").forEach(c => c.classList.remove("active-profile"));
      const p = document.getElementById(profId);
      if (p) p.classList.add("active-profile");
      showToast("已切换生效配置档案");
      addLog(`[Profile] Active configuration switched to [${profId}]`);
    }

    document.getElementById("btnUpdateAllProfiles").addEventListener("click", () => {
      showToast("正在批量并发同步所有订阅配置...");
      addLog("[Profile] Bulk updating all active subscriptions");
      setTimeout(() => {
        showToast("全部订阅已成功更新至最新版本！");
      }, 900);
    });

    function openProfileProps(title, url, interval, isSub) {
      document.getElementById("propsSheetTitle").textContent = `配置属性 · ${title}`;
      document.getElementById("propUrlInput").value = url;
      document.getElementById("propIntervalInput").value = interval;
      openSheet("profilePropsSheet");
    }

    // WebDAV Controls (TIIEHenry Fork Core)
    function openWebdavModal() {
      openSheet("webdavSheet");
    }

    function testWebdav() {
      showToast("正在连接 WebDAV 服务器...");
      addLog("[WebDAV] Testing PROPFIND https://dav.jianguoyun.com/dav/clash-sync/");
      setTimeout(() => {
        showToast("✅ WebDAV 连接测试成功，认证有效！");
        addLog("[WebDAV] Connection test passed (HTTP 207 Multi-Status)", "info");
      }, 700);
    }

    function syncWebdavNow() {
      showToast("正在进行 WebDAV 双向云同步...");
      addLog("[WebDAV] Initiating two-way sync with remote timestamp comparison");
      setTimeout(() => {
        showToast("✅ 双向同步完成：3 个配置与分流策略已保持一致");
        addLog("[WebDAV] Sync complete. Local and remote files synchronized", "info");
        closeSheet();
      }, 950);
    }

    function pullWebdav() {
      showToast("正在从 WebDAV 云端拉取配置...");
      addLog("[WebDAV] Pulling remote configuration and rule sets");
      setTimeout(() => {
        showToast("云端配置拉取完成，已覆盖本地缓存");
        closeSheet();
      }, 800);
    }

    function pushWebdav() {
      showToast("正在向 WebDAV 云端推送备份...");
      addLog("[WebDAV] Pushing local settings, strategies, and age cipher");
      setTimeout(() => {
        showToast("本地配置已成功推送到坚果云 WebDAV");
        closeSheet();
      }, 800);
    }

    // Network Stack
    function openNetworkStackModal() {
      openSheet("networkStackSheet");
    }

    function setNetworkStack(stack) {
      state.networkStack = stack;
      const el = document.getElementById("currentStackLabel");
      if (el) el.textContent = `${stack} (${stack === 'gVisor' ? '推荐' : stack === 'System' ? '系统原生' : '混合栈'})`;
      closeSheet();
      showToast(`网络协议栈已切换为: ${stack}`);
      addLog(`[Network] TUN virtual stack reconfigured to [${stack}]`, "warn");
    }

    // Overrides
    function openOverrideModal() {
      document.getElementById("overrideMixedPort").value = state.mixedPort;
      document.getElementById("overrideSocksPort").value = state.socksPort;
      document.getElementById("overrideRestPort").value = state.restPort;
      document.getElementById("overrideAllowLan").checked = state.allowLan;
      document.getElementById("overrideSniffer").checked = state.sniffer;
      document.getElementById("overrideFakeIp").checked = state.fakeIp;
      openSheet("overrideSheet");
    }

    function saveOverrides() {
      state.mixedPort = parseInt(document.getElementById("overrideMixedPort").value) || 7890;
      state.socksPort = parseInt(document.getElementById("overrideSocksPort").value) || 7891;
      state.restPort = parseInt(document.getElementById("overrideRestPort").value) || 9090;
      state.allowLan = document.getElementById("overrideAllowLan").checked;
      state.sniffer = document.getElementById("overrideSniffer").checked;
      state.fakeIp = document.getElementById("overrideFakeIp").checked;
      closeSheet();
      showToast("配置覆写已生效，端口与嗅探器已更新");
      addLog(`[Override] Mixed:${state.mixedPort}, SOCKS:${state.socksPort}, Sniffer:${state.sniffer}`);
    }

    // Meta Features
    function openMetaFeaturesModal() {
      openSheet("metaFeaturesSheet");
    }

    function setGeoSource(source) {
      state.geoipSource = source;
      document.getElementById("geoBtnMeta").style.background = (source === 'MetaCubeX') ? 'var(--accent-blue)' : '';
      document.getElementById("geoBtnLoyal").style.background = (source === 'Loyalsoldier') ? 'var(--accent-blue)' : '';
      showToast(`GeoIP 数据库源已设置为: ${source}`);
      addLog(`[Geo] Database provider set to [${source}]`);
    }

    // New Profile Modal
    function openNewProfileModal() {
      openSheet("newProfileSheet");
    }

    function submitNewProfile() {
      const name = document.getElementById("newProfName").value || "新建订阅配置";
      closeSheet();
      showToast(`配置 [${name}] 已成功创建！`);
      addLog(`[Profile] Created new configuration profile: ${name}`);
    }

    // About Dialog
    function showAboutDialog() {
      openSheet("aboutSheet");
    }

    // Logs
    function addLog(text, level = "info") {
      const box = document.getElementById("logBox");
      if (!box) return;
      const time = new Date().toTimeString().split(" ")[0];
      const div = document.createElement("div");
      div.className = `log-line log-${level}`;
      div.textContent = `[${time}] ${text}`;
      box.appendChild(div);
      box.scrollTop = box.scrollHeight;
    }

    function openLogSheet() {
      openSheet("logSheet");
    }

    function clearLogs() {
      const box = document.getElementById("logBox");
      if (box) box.innerHTML = "";
      showToast("日志已清空");
    }

    document.getElementById("btnFastLogs").addEventListener("click", openLogSheet);
    document.getElementById("btnFastReload").addEventListener("click", () => {
      showToast("正在重新加载 Mihomo 配置文件...");
      addLog("[Core] Reloading configuration with live state persistence", "warn");
      setTimeout(() => showToast("内核重载完成，无缝平滑切流"), 500);
    });

    // Initial Logs
    addLog("[Core] Clash.Meta v1.18.9 initiated with Android TUN stack");
    addLog("[TUN] gVisor virtual network device bound to tun0 at 198.18.0.1/16");
    addLog("[Strategy] Active strategy: [海外应用加速] (AcceptSelected: 7 apps)");
    addLog("[DNS] Sniffing enabled for TLS SNI and HTTP Host header");
    addLog("[Rule] Matching api.openai.com -> Route to [🤖 AI 智能]");

    // Active Connections
    document.getElementById("btnActiveConn").addEventListener("click", () => {
      const list = document.getElementById("connListContainer");
      
      let activeStrat = null;
      let activePackages = state.globalPackages;
      let activeMode = "AcceptSelected";
      let activeName = "全局基础名单";

      if (state.activeStrategyUuid) {
        activeStrat = state.strategies.find(s => s.id === state.activeStrategyUuid);
        if (activeStrat) {
          activePackages = activeStrat.packages;
          activeMode = activeStrat.mode;
          activeName = activeStrat.name;
        }
      }

      const simulatedRaw = [
        { host: "api.openai.com:443", appId: "tg", process: "Telegram", speed: "142 KB/s" },
        { host: "rr3---sn-4g5ednks.googlevideo.com:443", appId: "yt", process: "YouTube", speed: "3.4 MB/s" },
        { host: "api.twitter.com:443", appId: "tw", process: "X (Twitter)", speed: "84 KB/s" },
        { host: "szextshort.weixin.qq.com:443", appId: "wx", process: "微信", speed: "12 KB/s" },
        { host: "mobilegw.alipay.com:443", appId: "alipay", process: "支付宝", speed: "8 KB/s" },
        { host: "api.bilibili.com:443", appId: "bili", process: "哔哩哔哩", speed: "2.1 MB/s" },
        { host: "api.github.com:443", appId: "github", process: "GitHub", speed: "45 KB/s" }
      ];

      const simulated = simulatedRaw.map(c => {
        let isProxied = false;
        let routeReason = "";

        if (state.mode === "Global") {
          isProxied = true;
          routeReason = "全局代理模式 (全部走隧道)";
        } else if (state.mode === "Direct") {
          isProxied = false;
          routeReason = "全局直连模式 (全部绕过)";
        } else {
          const inPackages = activePackages.includes(c.appId);
          if (activeMode === "AcceptSelected") {
            isProxied = inPackages;
            routeReason = inPackages ? `白名单命中代理 [${activeName}]` : `未在白名单中 (直连绕过)`;
          } else if (activeMode === "DenySelected") {
            isProxied = !inPackages;
            routeReason = inPackages ? `黑名单命中 (直连绕过) [${activeName}]` : `走代理隧道`;
          } else {
            isProxied = true;
            routeReason = `全局放行隧道 [${activeName}]`;
          }
        }

        return {
          host: c.host,
          process: c.process,
          speed: c.speed,
          isProxied: isProxied,
          node: isProxied ? state.currentNode.name : "DIRECT (直连)",
          reason: routeReason
        };
      });

      document.getElementById("connCount").textContent = simulated.length;
      list.innerHTML = simulated.map(c => `
        <div class="card" style="margin-bottom:8px; padding:12px; background:var(--card-subtle);">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="demo-tag" style="background:${c.isProxied ? 'var(--accent-blue-subtle)' : 'rgba(255,255,255,0.08)'}; color:${c.isProxied ? 'var(--accent-blue)' : 'var(--text-secondary)'}; font-weight:700;">
                ${c.isProxied ? '代理' : '直连'}
              </span>
              <b style="font-size:13px;">${c.host}</b>
            </div>
            <span style="font-size:11px; color:var(--accent-green); font-weight:700;">${c.speed}</span>
          </div>
          <div style="display:flex; justify-content:space-between; align-items:center; font-size:11px; color:var(--text-secondary); margin-top:6px;">
            <span>应用：<b>${c.process}</b> · 出站：${c.node}</span>
            <span style="font-size:10px; color:var(--text-tertiary);">${c.reason}</span>
          </div>
        </div>
      `).join("");

      openSheet("connSheet");
    });

    function clearAllConnections() {
      state.connections = [];
      document.getElementById("connCount").textContent = 0;
      document.getElementById("connListContainer").innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-secondary);">暂无活跃网络连接</div>`;
      showToast("所有活跃连接已重置");
      addLog("[Core] Closed all active sockets", "warn");
    }

    // Canvas Traffic Waveform
    const canvas = document.getElementById("trafficCanvas");
    const ctx = canvas.getContext("2d");

    function resizeCanvas() {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width * window.devicePixelRatio;
      canvas.height = rect.height * window.devicePixelRatio;
      ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    }
    window.addEventListener("resize", resizeCanvas);
    setTimeout(resizeCanvas, 100);

    function drawWave() {
      const w = canvas.getBoundingClientRect().width;
      const h = canvas.getBoundingClientRect().height;
      ctx.clearRect(0, 0, w, h);

      if (!state.running) {
        ctx.strokeStyle = "rgba(142, 142, 147, 0.2)";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(0, h - 10);
        ctx.lineTo(w, h - 10);
        ctx.stroke();
        return;
      }

      const points = state.samples;
      const step = w / (points.length - 1);

      // Download Wave (Blue)
      ctx.beginPath();
      ctx.moveTo(0, h);
      points.forEach((p, idx) => {
        const x = idx * step;
        const normY = Math.min(1, p.down / 4000);
        const y = h - normY * (h - 15) - 4;
        if (idx === 0) ctx.lineTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.lineTo(w, h);
      ctx.closePath();
      
      const gradDown = ctx.createLinearGradient(0, 0, 0, h);
      gradDown.addColorStop(0, "rgba(10, 132, 255, 0.28)");
      gradDown.addColorStop(1, "rgba(10, 132, 255, 0.0)");
      ctx.fillStyle = gradDown;
      ctx.fill();

      ctx.beginPath();
      points.forEach((p, idx) => {
        const x = idx * step;
        const normY = Math.min(1, p.down / 4000);
        const y = h - normY * (h - 15) - 4;
        if (idx === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = "#0a84ff";
      ctx.lineWidth = 2;
      ctx.stroke();

      // Upload Wave (Green)
      ctx.beginPath();
      points.forEach((p, idx) => {
        const x = idx * step;
        const normY = Math.min(1, p.up / 1200);
        const y = h - normY * (h - 30) - 2;
        if (idx === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = "#30d158";
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    // Tick Loop
    setInterval(() => {
      if (state.running) {
        state.uptimeSeconds++;
        uptimeDisplay.textContent = formatTime(state.uptimeSeconds);

        const downJitter = (Math.random() - 0.48) * 400 * 1024;
        state.trafficDown = Math.max(120 * 1024, Math.min(6 * 1024 * 1024, state.trafficDown + downJitter));
        const upJitter = (Math.random() - 0.48) * 80 * 1024;
        state.trafficUp = Math.max(20 * 1024, Math.min(1.2 * 1024 * 1024, state.trafficUp + upJitter));

        state.totalDown += state.trafficDown;
        state.totalUp += state.trafficUp;

        downRate.textContent = formatBytes(state.trafficDown);
        upRate.textContent = formatBytes(state.trafficUp);
        totalDown.textContent = formatTotal(state.totalDown);
        totalUp.textContent = formatTotal(state.totalUp);

        state.samples.shift();
        state.samples.push({
          up: state.trafficUp / 1024,
          down: state.trafficDown / 1024
        });
      } else {
        downRate.textContent = "0.0 KB/s";
        upRate.textContent = "0.0 KB/s";
      }

      drawWave();
    }, 1000);

    // Clock
    setInterval(() => {
      const now = new Date();
      const h = now.getHours().toString().padStart(2, "0");
      const m = now.getMinutes().toString().padStart(2, "0");
      document.getElementById("clockDisplay").textContent = `${h}:${m}`;
    }, 1000);

    // Demo Bar Actions
    document.getElementById("btnThemeToggle").addEventListener("click", () => {
      state.theme = state.theme === "dark" ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", state.theme);
      document.getElementById("themeIcon").textContent = state.theme === "dark" ? "☀️" : "🌙";
      showToast(`已切换至 ${state.theme === "dark" ? "深色暗夜" : "浅色明亮"} 模式`);
    });

    document.getElementById("btnPhoneMode").addEventListener("click", () => {
      state.fullscreenPhone = !state.fullscreenPhone;
      const phone = document.getElementById("devicePhone");
      phone.classList.toggle("fullscreen-mode", state.fullscreenPhone);
      document.getElementById("phoneModeText").textContent = state.fullscreenPhone ? "真机外壳" : "全屏平铺";
      setTimeout(resizeCanvas, 360);
    });

    document.getElementById("btnRandomSpike").addEventListener("click", () => {
      if (!state.running) {
        showToast("请先点击主开关启动 VPN 连接");
        return;
      }
      state.trafficDown = 18.5 * 1024 * 1024;
      state.trafficUp = 3.2 * 1024 * 1024;
      showToast("🔥 模拟突发流量飙升: 18.5 MB/s");
      addLog("[Traffic] Inbound traffic burst detected: 18.5 MB/s via [香港 BGP 01]", "warn");
    });

    // Initial render
    renderProxies();
    renderProviders();
    renderStrategies();
    renderApps();
    drawWave();

    // Deep Linking Support (URL Query / Hash)
    function applyDeepLink() {
      const params = new URLSearchParams(window.location.search);
      if (params.get('theme') === 'light') {
        state.theme = 'light';
        document.documentElement.setAttribute('data-theme', 'light');
        const icon = document.getElementById('themeIcon');
        if (icon) icon.textContent = '🌙';
      }
      const hash = window.location.hash.replace('#', '');
      const tab = params.get('tab') || (hash ? hash.split('/')[0] : null);
      const sub = params.get('sub') || (hash && hash.includes('/') ? hash.split('/')[1] : null);
      if (tab) {
        switchTab(tab);
        if (sub) toggleStrategySubTab(sub);
        const modal = params.get('modal');
        if (modal === 'editAppSet') {
          const setId = params.get('id') || 'domestic';
          openEditAppSetModal(setId);
        } else if (modal === 'newAppSet') {
          openNewAppSetModal();
        } else if (modal === 'connections') {
          document.getElementById('btnActiveConn').click();
        }
      }
    }
    applyDeepLink();
"""
