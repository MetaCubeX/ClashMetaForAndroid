HTML_BODY = """
  <!-- Top Demo Control Bar -->
  <header class="demo-control-bar">
    <div class="demo-title-group">
      <h1>
        <span>ClashMetaX</span>
        <span class="demo-tag">UI 2.0 FULL</span>
      </h1>
      <div style="display:flex; gap:6px; flex-wrap:wrap; margin-top:4px;">
        <span class="demo-feature-pill">保留应用分流策略</span>
        <span class="demo-feature-pill">访问控制应用集</span>
        <span class="demo-feature-pill">WebDAV 云同步</span>
        <span class="demo-feature-pill">提供者/文件/age密钥</span>
      </div>
    </div>

    <div class="demo-actions">
      <button class="demo-btn" id="btnThemeToggle">
        <span id="themeIcon">☀️</span> <span>主题</span>
      </button>
      <button class="demo-btn" id="btnPhoneMode">
        <span>📱</span> <span id="phoneModeText">全屏平铺</span>
      </button>
      <button class="demo-btn" id="btnRandomSpike">
        <span>⚡</span> <span>突发流量</span>
      </button>
    </div>
  </header>

  <!-- Mobile Phone Mockup -->
  <div class="device-wrapper">
    <div class="device-phone" id="devicePhone">
      
      <!-- Toast Container -->
      <div class="toast-container" id="toastContainer"></div>

      <!-- Phone Status Bar -->
      <div class="status-bar">
        <span id="clockDisplay">09:41</span>
        <div class="status-notch">
          <div class="status-camera"></div>
          <span class="status-island-icon active" id="islandVpnIcon">🛡️ VPN</span>
        </div>
        <div class="status-right">
          <span style="font-size:11px; font-weight:700;">5G</span>
          <svg width="15" height="11" viewBox="0 0 15 11" fill="currentColor">
            <path d="M1 10h2V6H1v4zm4 0h2V4H5v6zm4 0h2V2H9v8zm4 0h2V0h-2v10z"/>
          </svg>
          <svg width="18" height="11" viewBox="0 0 18 11" fill="currentColor">
            <rect x="0.5" y="0.5" width="14" height="10" rx="2.5" stroke="currentColor" fill="none"/>
            <rect x="2" y="2" width="10" height="7" rx="1.5" fill="currentColor"/>
            <path d="M16 3.5v4" stroke="currentColor" stroke-linecap="round"/>
          </svg>
        </div>
      </div>

      <!-- App Screen Scrollable Area -->
      <main class="app-screen" id="appScreen">

        <!-- ================= TAB 1: 仪表盘 DASHBOARD ================= -->
        <section class="page-view active" id="page-dashboard">
          <div class="page-header">
            <div>
              <h2 class="page-title">ClashMetaX</h2>
              <div style="font-size:11px; color:var(--text-secondary); margin-top:1px;">Mihomo Core · TIIEHenry Fork</div>
            </div>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnFastLogs" title="实时日志">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 17l6-6-6-6M12 19h8"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnFastReload" title="重载内核">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Hero Master Connect -->
          <div class="hero-connect-card running" id="heroCard">
            <div class="connect-top-row">
              <div class="connect-status-badge running" id="connectBadge">
                <div class="pulse-dot"></div>
                <span id="connectStatusText">运行中 (VPN)</span>
              </div>
              <span class="uptime-counter" id="uptimeDisplay">01:24:32</span>
            </div>

            <div class="connect-center">
              <button class="power-button running" id="powerBtn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/>
                  <line x1="12" y1="2" x2="12" y2="12"/>
                </svg>
              </button>
              <div class="power-hint" id="powerHintText">安全网络已守护</div>
              <div class="power-subhint" id="powerSubhintText">生效配置：香港 IEPL 专线 · 规则模式</div>
            </div>

            <!-- Mode Selector -->
            <div class="mode-segmented-control">
              <div class="mode-tab active" data-mode="Rule">规则分流</div>
              <div class="mode-tab" data-mode="Global">全局代理</div>
              <div class="mode-tab" data-mode="Direct">直接连接</div>
            </div>
          </div>

          <!-- Outbound IP & Current Node Perception Card -->
          <div class="outbound-card card-interactive" id="outboundCard">
            <div class="outbound-top">
              <span class="outbound-label">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/>
                  <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
                </svg>
                出口感知 · 点击快捷切节点
              </span>
              <span class="outbound-latency-pill" id="outboundPing">38 ms</span>
            </div>
            <div class="outbound-node-name">
              <span id="outboundFlag">🇭🇰</span>
              <span id="outboundName">香港 BGP 01 [IEPL专线]</span>
            </div>
            <div class="outbound-meta-row">
              <span>出站 IP: <span class="outbound-ip-tag" id="outboundIp">104.28.198.42</span></span>
              <span>数据中心: <b id="outboundGeo">Cloudflare HK</b></span>
            </div>
          </div>

          <!-- Traffic Waveform & Rates -->
          <div class="traffic-card">
            <div class="traffic-header">
              <div class="traffic-stat">
                <span class="traffic-label">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--accent-blue)" stroke-width="2.5"><path d="M12 5v14M19 12l-7 7-7-7"/></svg>
                  实时下行
                </span>
                <span class="traffic-rate down" id="downRate">2.4 MB/s</span>
              </div>
              <div class="traffic-stat" style="align-items:flex-end;">
                <span class="traffic-label">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--accent-green)" stroke-width="2.5"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
                  实时上行
                </span>
                <span class="traffic-rate up" id="upRate">340.2 KB/s</span>
              </div>
            </div>

            <div class="canvas-container">
              <canvas id="trafficCanvas"></canvas>
            </div>

            <div class="traffic-totals">
              <span>累计下载: <b id="totalDown">1.42 GB</b></span>
              <span>累计上传: <b id="totalUp">280.4 MB</b></span>
            </div>
          </div>

          <!-- Bento 4 Features (Retaining all CMFA core abilities) -->
          <div class="dashboard-features-grid">
            <!-- Feature 1: Apps Strategy (Fork Core) -->
            <div class="feature-bento-item" onclick="switchTab('apps')">
              <div class="feature-bento-header">
                <span class="feature-bento-title">应用分流策略</span>
                <span style="font-size:10px; color:var(--accent-green); font-weight:700;" id="dashStrategyStatus">已生效</span>
              </div>
              <div class="feature-bento-desc" id="dashStrategyDesc">
                当前：<b>海外应用加速</b><br>
                7 款已选 · 白名单代理
              </div>
            </div>

            <!-- Feature 2: WebDAV Sync (Fork Core) -->
            <div class="feature-bento-item" onclick="openWebdavModal()">
              <div class="feature-bento-header">
                <span class="feature-bento-title">WebDAV 同步</span>
                <span style="font-size:10px; color:var(--accent-blue); font-weight:700;">自动</span>
              </div>
              <div class="feature-bento-desc">
                已同步 2 份配置<br>
                今天 08:30 已备份
              </div>
            </div>

            <!-- Feature 3: Providers -->
            <div class="feature-bento-item" onclick="switchTab('proxies', 'providers')">
              <div class="feature-bento-header">
                <span class="feature-bento-title">提供者 (Providers)</span>
                <span style="font-size:10px; color:var(--accent-orange); font-weight:700;">3 组正常</span>
              </div>
              <div class="feature-bento-desc">
                节点集 48 个 · 规则集 3<br>
                点击一键健康检查
              </div>
            </div>

            <!-- Feature 4: External Files & Age -->
            <div class="feature-bento-item" onclick="switchTab('profiles', 'files')">
              <div class="feature-bento-header">
                <span class="feature-bento-title">文件与 Geo</span>
                <span style="font-size:10px; color:var(--text-secondary); font-weight:700;">4 个</span>
              </div>
              <div class="feature-bento-desc">
                GeoIP / GeoSite.dat<br>
                age 密钥已就绪
              </div>
            </div>
          </div>
        </section>

        <!-- ================= TAB 2: 节点与提供者 PROXIES & PROVIDERS ================= -->
        <section class="page-view" id="page-proxies">
          <div class="page-header">
            <h2 class="page-title">节点与提供者</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnUrlTest" title="一键并发测速">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnToggleLayout" title="切换视图">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                  <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Sub Switcher: Proxies vs Providers -->
          <div class="sub-page-switcher">
            <div class="sub-switch-btn active" id="subTabProxies" onclick="toggleProxySubTab('proxies')">节点选择</div>
            <div class="sub-switch-btn" id="subTabProviders" onclick="toggleProxySubTab('providers')">提供者集合 (Providers)</div>
          </div>

          <!-- Section A: Proxies -->
          <div id="proxySectionA">
            <div class="filter-scroll-row" id="groupFilterRow">
              <div class="filter-chip active" data-group="PROXY">🚀 节点选择 (12)</div>
              <div class="filter-chip" data-group="AUTO">⚡ 自动优选 (8)</div>
              <div class="filter-chip" data-group="MEDIA">🎬 流媒体 (6)</div>
              <div class="filter-chip" data-group="AI">🤖 AI 智能 (5)</div>
              <div class="filter-chip" data-group="DIRECT">🎯 全球直连</div>
            </div>

            <div class="proxy-toolbar" style="margin-bottom:12px; display:flex; gap:8px;">
              <div style="flex:1; background:var(--card-subtle); border:1px solid var(--card-border); border-radius:var(--radius-md); padding:8px 12px; display:flex; align-items:center; gap:8px;">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <input type="text" id="proxySearchInput" placeholder="搜索节点、协议或机房..." style="background:transparent; border:none; outline:none; color:var(--text-primary); font-size:13px; width:100%;">
              </div>
            </div>

            <div class="proxy-grid" id="nodeContainer"></div>
          </div>

          <!-- Section B: Providers -->
          <div id="proxySectionB" style="display:none;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
              <span style="font-size:13px; color:var(--text-secondary); font-weight:600;">已加载 3 个提供者源</span>
              <button class="demo-btn" style="padding:4px 10px; font-size:12px;" onclick="updateAllProviders()">全量健康检查</button>
            </div>
            
            <div id="providersListContainer"></div>
          </div>
        </section>

        <!-- ================= TAB 3: 订阅配置与外部文件 PROFILES & FILES ================= -->
        <section class="page-view" id="page-profiles">
          <div class="page-header">
            <h2 class="page-title">配置与文件</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnProfileNew" onclick="openNewProfileModal()" title="新建 / 导入配置">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnUpdateAllProfiles" title="批量更新全部订阅">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="sub-page-switcher">
            <div class="sub-switch-btn active" id="subTabProfiles" onclick="toggleProfileSubTab('profiles')">配置订阅 (Profiles)</div>
            <div class="sub-switch-btn" id="subTabFiles" onclick="toggleProfileSubTab('files')">外部资源与文件 (Files)</div>
          </div>

          <!-- Section A: Profiles -->
          <div id="profileSectionA">
            <div class="profile-card active-profile" id="profCard1" onclick="openProfileProps('香港 IEPL 专线 · 年付', 'https://sub.airport.io/api/v1/subscribe', 1440, true)">
              <div class="profile-header">
                <div>
                  <div style="font-size:15px; font-weight:700; display:flex; align-items:center; gap:6px;">
                    <span>香港 IEPL 专线 · 年付</span>
                    <span style="font-size:10px; font-weight:700; background:var(--accent-blue); color:#fff; padding:2px 6px; border-radius:6px;">生效中</span>
                  </div>
                  <div style="font-size:11px; color:var(--text-secondary); margin-top:2px;">
                    https://sub.airport.io/api/v1/subscribe...
                  </div>
                </div>
                <button class="icon-btn" onclick="updateSingleProfile(event, 'profCard1')" title="刷新">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
                </button>
              </div>
              <div class="progress-bar-track">
                <div class="progress-bar-fill" style="width: 28.4%;"></div>
              </div>
              <div class="profile-meta-row">
                <span>已用 142 GB / 500 GB (28.4%)</span>
                <span class="expiry-tag">18天后到期 (2026-11-08)</span>
              </div>
              <div class="profile-meta-row" style="margin-top:6px; font-size:11px;">
                <span>包含 48 个节点 · age 加密已保护</span>
                <span>更新于 10分钟前</span>
              </div>
            </div>

            <div class="profile-card" id="profCard2" onclick="activateProfile('profCard2')">
              <div class="profile-header">
                <div>
                  <div style="font-size:15px; font-weight:700;">备用自建 VLESS 节点</div>
                  <div style="font-size:11px; color:var(--text-secondary); margin-top:2px;">
                    文件配置 · 离线存储卡导入
                  </div>
                </div>
                <button class="icon-btn" onclick="updateSingleProfile(event, 'profCard2')">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
                </button>
              </div>
              <div class="profile-meta-row" style="margin-top:6px;">
                <span>包含 6 个自建节点 · 无用量上限</span>
                <span>3天前更新</span>
              </div>
            </div>
          </div>

          <!-- Section B: Files Management -->
          <div id="profileSectionB" style="display:none;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
              <span style="font-size:13px; color:var(--text-secondary); font-weight:600;">配置目录 (/clash/...)</span>
              <button class="demo-btn" style="padding:4px 10px; font-size:12px;" onclick="showToast('新建自定义配置或规则文件')">+ 新建文件</button>
            </div>
            
            <div class="file-row">
              <div>
                <div style="font-size:13px; font-weight:700;">config.yaml</div>
                <div style="font-size:11px; color:var(--text-secondary);">主配置文件 · 24.5 KB · 今天 11:20</div>
              </div>
              <span style="font-size:11px; color:var(--accent-blue); font-weight:600; cursor:pointer;" onclick="showToast('打开文本编辑器')">编辑</span>
            </div>
            <div class="file-row">
              <div>
                <div style="font-size:13px; font-weight:700;">country.mmdb (GeoIP)</div>
                <div style="font-size:11px; color:var(--text-secondary);">MetaCubeX 官方源 · 6.8 MB · 昨天</div>
              </div>
              <span style="font-size:11px; color:var(--text-secondary);">已加载</span>
            </div>
            <div class="file-row">
              <div>
                <div style="font-size:13px; font-weight:700;">geosite.dat</div>
                <div style="font-size:11px; color:var(--text-secondary);">域名规则集 · 18.2 MB · 3天前</div>
              </div>
              <span style="font-size:11px; color:var(--text-secondary);">已加载</span>
            </div>
            <div class="file-row">
              <div>
                <div style="font-size:13px; font-weight:700;">age-key.txt</div>
                <div style="font-size:11px; color:var(--text-secondary);">订阅非对称解密私钥 · 450 B</div>
              </div>
              <span style="font-size:11px; color:var(--accent-green); font-weight:600;">密钥有效</span>
            </div>
          </div>
        </section>

        <!-- ================= TAB 4: 应用分流策略与访问控制 APPS STRATEGY & ACCESS CONTROL ================= -->
        <section class="page-view" id="page-apps">
          <div class="page-header">
            <div>
              <h2 class="page-title">应用分流与名单</h2>
              <div style="font-size:11px; color:var(--text-secondary); margin-top:1px;">TIIEHenry Fork 核心特性 · PRD-003 & PRD-004 规范实现</div>
            </div>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnActiveConn" title="活跃连接实时监控">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="sub-page-switcher">
            <div class="sub-switch-btn active" id="subTabStrategyList" onclick="toggleStrategySubTab('strategies')">策略配置方案</div>
            <div class="sub-switch-btn" id="subTabAppSets" onclick="toggleStrategySubTab('appsets')">应用集合 (可编辑)</div>
            <div class="sub-switch-btn" id="subTabAccessList" onclick="toggleStrategySubTab('accesslist')">名单勾选配置</div>
          </div>

          <!-- Section A: Strategies Configs List (PRD-004) -->
          <div id="strategySectionA">
            <!-- Global Fallback Card -->
            <div class="strategy-card" id="globalAccessCard" onclick="selectActiveStrategy(null)">
              <div style="display:flex; justify-content:space-between; align-items:flex-start;">
                <div>
                  <div style="display:flex; align-items:center; gap:8px;">
                    <div style="font-size:14px; font-weight:700;">全局访问控制名单</div>
                    <span class="demo-tag" style="background:rgba(255,255,255,0.08);">默认基础</span>
                  </div>
                  <div style="font-size:11px; color:var(--text-secondary); margin-top:3px;" id="globalAccessDesc">模式：仅允许选中的应用 (白名单) · 已选 8 个</div>
                </div>
                <div class="radio-indicator" id="globalAccessRadio">●</div>
              </div>
              <div style="display:flex; justify-content:space-between; align-items:center; margin-top:10px; padding-top:8px; border-top:1px solid var(--card-border);">
                <span style="font-size:11px; color:var(--text-tertiary);">未启用任何策略时由全局名单接管</span>
                <button class="demo-btn" style="padding:4px 10px; font-size:11px;" onclick="event.stopPropagation(); editStrategyApps(null)">编辑名单</button>
              </div>
            </div>

            <!-- Custom Named Strategies Header -->
            <div style="display:flex; justify-content:space-between; align-items:center; margin:16px 2px 10px;">
              <span style="font-size:13px; color:var(--text-secondary); font-weight:700;">多策略方案 (单选生效)</span>
              <button class="demo-btn" style="padding:4px 10px; font-size:12px; background:var(--accent-blue);" onclick="openNewStrategyModal()">+ 新建策略</button>
            </div>

            <div id="strategyConfigsContainer"></div>

            <div style="font-size:11px; color:var(--text-tertiary); line-height:1.5; margin-top:14px; padding:0 4px;">
              💡 <b>说明</b>：当单选激活某份分流策略时，内核将优先由该策略接管分流路由；停用所有策略时自动回退为全局名单。离开应用名单列表时立即自动保存并执行热重载。
            </div>
          </div>

                    <!-- Section S: Dedicated App Sets Management (PRD-003 Editable Sets) -->
          <div id="strategySectionSets" style="display:none;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin:4px 2px 10px;">
              <div>
                <span style="font-size:13px; color:var(--text-secondary); font-weight:700;">应用集合管理 (APP SETS)</span>
                <div style="font-size:11px; color:var(--text-tertiary); margin-top:2px;">支持创建自定义集合、增删包名及跨策略快速复用</div>
              </div>
              <button class="demo-btn" style="padding:4px 10px; font-size:12px; background:var(--accent-blue);" onclick="openNewAppSetModal()">+ 新建应用集</button>
            </div>

            <div id="appSetsManagementContainer"></div>

            <div style="font-size:11px; color:var(--text-tertiary); line-height:1.5; margin-top:14px; padding:0 4px;">
              💡 <b>说明</b>：系统应用与国内应用为内置集合，国内目录由 <code>domestic_packages.txt</code> 提供并支持用户追加。自定义应用集（如海外社交、流媒体、游戏等）可自由编辑包名并在任意分流策略中一键勾选。
            </div>
          </div>

          <!-- Section B: Detailed Access Control (PRD-003) -->
          <div id="strategySectionB" style="display:none;">
            <!-- Target Banner -->
            <div class="card" style="padding:12px 14px; background:var(--card-subtle); margin-bottom:12px;">
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <div>
                  <div style="display:flex; align-items:center; gap:8px;">
                    <button class="demo-btn" style="padding:3px 8px; font-size:11px;" onclick="toggleStrategySubTab('strategies')">← 返回</button>
                    <div style="font-size:13px; font-weight:700;" id="currentEditingStrategyTitle">海外应用代理 · 应用名单</div>
                  </div>
                  <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;" id="currentEditingStrategyModeDesc">当前模式：仅代理已选应用 (白名单)</div>
                </div>
                <span class="demo-feature-pill" id="currentEditingStrategyActiveBadge" style="color:var(--accent-green); background:var(--accent-green-subtle);">生效中</span>
              </div>
            </div>

            <!-- Search Field -->
            <div class="form-group" style="margin-bottom:10px;">
              <input type="text" id="appSearchInput" class="form-input" placeholder="🔍 搜索应用名称或包名 (例如 telegram, bili, com.android...)" style="padding:9px 12px; font-size:13px;" oninput="onAppSearch(this.value)">
            </div>

            <!-- Filter Switch Row: Hide System Apps -->
            <div class="settings-item" style="padding:10px 12px; border-radius:var(--radius-sm); margin-bottom:12px; background:var(--card-subtle);">
              <div class="settings-main">
                <div class="settings-title" style="font-size:13px;">隐藏系统应用</div>
                <div class="settings-desc">仅隐藏列表中的系统行，不影响集合计数与实际分流勾选</div>
              </div>
              <label class="switch"><input type="checkbox" id="hideSystemSwitch" onchange="toggleHideSystem()"><span class="slider"></span></label>
            </div>

            <!-- App Sets Header & Dynamic Cards (PRD-003 & Editable Sets) -->
            <div style="margin-bottom:14px;">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                <div style="font-size:12px; font-weight:700; color:var(--text-secondary);">
                  应用集合联动 (点击一键全选/取消 · 支持直接编辑)
                </div>
                <button class="demo-btn" style="padding:2px 8px; font-size:11px;" onclick="toggleStrategySubTab('appsets')">
                  管理集合
                </button>
              </div>
              
              <!-- Container for all dynamic app set cards with tri-state selection & edit button -->
              <div id="appSetsQuickBarContainer"></div>
            </div>

            <!-- App List Action Row -->
            <div style="display:flex; justify-content:space-between; align-items:center; margin:12px 2px 8px;">
              <span style="font-size:12px; font-weight:700; color:var(--text-secondary);">
                应用名单 (<span id="selectedAppCount">0</span> / <span id="totalAppCount">15</span> 已选)
              </span>
              <div style="display:flex; gap:12px; font-size:12px; font-weight:600; color:var(--accent-blue);">
                <span style="cursor:pointer;" onclick="selectAllApps(true)">全选</span>
                <span style="cursor:pointer;" onclick="selectAllApps(false)">全不选</span>
                <span style="cursor:pointer;" onclick="invertAppSelection()">反选</span>
              </div>
            </div>

            <!-- Apps List -->
            <div id="appListContainer"></div>
          </div>
        </section>
        
        <!-- ================= TAB 5: 设置 SETTINGS (WEB-DAV / OVERRIDE / META / NETWORK) ================= -->
        <section class="page-view" id="page-settings">
          <div class="page-header">
            <h2 class="page-title">系统与进阶</h2>
          </div>

          <!-- WebDAV Cloud Sync Group -->
          <div class="settings-group">
            <div class="settings-group-title">WebDAV 云端同步 (TIIEHenry 特性)</div>
            <div class="settings-item" onclick="openWebdavModal()">
              <div class="settings-main">
                <div class="settings-title">WebDAV 同步控制台</div>
                <div class="settings-desc">https://dav.jianguoyun.com/dav/ · 自动双向同步</div>
              </div>
              <span style="font-size:12px; color:var(--accent-blue); font-weight:600;">已就绪 →</span>
            </div>
          </div>

          <!-- Network & TUN Settings -->
          <div class="settings-group">
            <div class="settings-group-title">网络与 TUN 核心 (Network)</div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">TUN 虚拟网卡模式</div>
                <div class="settings-desc">接管整机 TCP/UDP 流量，游戏与低延迟保障</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
            <div class="settings-item" onclick="openNetworkStackModal()">
              <div class="settings-main">
                <div class="settings-title">网络协议栈 (Network Stack)</div>
                <div class="settings-desc">当前：gVisor (推荐) · 兼容 Android VPN</div>
              </div>
              <span style="font-size:12px; color:var(--text-secondary);" id="currentStackLabel">gVisor →</span>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">自动绕过本地局域网</div>
                <div class="settings-desc">192.168.x.x / 10.x.x 等私网 IP 不经代理</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">DNS 智能防污染与嗅探</div>
                <div class="settings-desc">嗅探 TLS SNI 与 HTTP Host，修正分流匹配</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
          </div>

          <!-- Override & Meta Features -->
          <div class="settings-group">
            <div class="settings-group-title">覆写与 Meta 特性 (Override & Meta)</div>
            <div class="settings-item" onclick="openOverrideModal()">
              <div class="settings-main">
                <div class="settings-title">端口与嗅探策略覆写</div>
                <div class="settings-desc">Mixed: 7890 · SOCKS: 7891 · 纯嗅探与重定向</div>
              </div>
              <span style="font-size:12px; color:var(--text-secondary);">配置 →</span>
            </div>
            <div class="settings-item" onclick="openMetaFeaturesModal()">
              <div class="settings-main">
                <div class="settings-title">Geo 数据库与外部控制</div>
                <div class="settings-desc">MetaCubeX GeoIP / GeoSite · REST 端口 9090</div>
              </div>
              <span style="font-size:12px; color:var(--text-secondary);">配置 →</span>
            </div>
          </div>

          <!-- Logs & System -->
          <div class="settings-group">
            <div class="settings-group-title">日志与系统</div>
            <div class="settings-item" onclick="openLogSheet()">
              <div class="settings-main">
                <div class="settings-title">实时终端日志 (Logcat)</div>
                <div class="settings-desc">实时调度、连接分流判定、报错与调试</div>
              </div>
              <span style="color:var(--text-secondary);">→</span>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">通知栏快捷磁贴 (Quick Tile)</div>
                <div class="settings-desc">从下拉菜单一键启停和切模式</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
            <div class="settings-item" onclick="showAboutDialog()">
              <div class="settings-main">
                <div class="settings-title">关于 ClashMetaX</div>
                <div class="settings-desc">版本 2.11.35 · Mihomo Core 90a5afcc</div>
              </div>
              <span style="font-size:12px; color:var(--text-secondary);">v2.11.35</span>
            </div>
          </div>
        </section>

      </main>

      <!-- Bottom Sheet Modal (Quick Node Switcher) -->
      <div class="bottom-sheet-backdrop" id="sheetBackdrop" onclick="closeSheet()"></div>
      <div class="bottom-sheet" id="nodeSwitcherSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">快速切换节点</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content" id="quickNodeList"></div>
      </div>

      <!-- Realtime Logs Bottom Sheet -->
      <div class="bottom-sheet" id="logSheet" style="max-height:85%;">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">实时内核日志流 (Logcat)</div>
          <div style="display:flex; gap:8px;">
            <button class="demo-btn" style="padding:4px 8px; font-size:11px;" onclick="clearLogs()">清空</button>
            <button class="icon-btn" onclick="closeSheet()">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>
        <div class="log-box" id="logBox"></div>
      </div>

      <!-- Active Connections Bottom Sheet -->
      <div class="bottom-sheet" id="connSheet" style="max-height:85%;">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">活跃连接实时监控 (<span id="connCount">4</span>)</div>
          <button class="demo-btn" style="padding:4px 10px; font-size:11px;" onclick="clearAllConnections()">全部切断</button>
        </div>
        <div class="sheet-content" id="connListContainer"></div>
      </div>

      <!-- WebDAV Full Settings Bottom Sheet -->
      <div class="bottom-sheet" id="webdavSheet" style="max-height:88%;">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">WebDAV 同步设置</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">WebDAV 服务器地址 (URL)</label>
            <input type="text" class="form-input" value="https://dav.jianguoyun.com/dav/clash-sync/">
          </div>
          <div class="form-group">
            <label class="form-label">备用局域网地址 (LAN URL，可选)</label>
            <input type="text" class="form-input" placeholder="http://192.168.1.100:8080/dav/">
          </div>
          <div class="form-group">
            <label class="form-label">用户名</label>
            <input type="text" class="form-input" value="user@example.com">
          </div>
          <div class="form-group">
            <label class="form-label">应用专属密码</label>
            <input type="password" class="form-input" value="••••••••••••">
          </div>
          <div class="form-group">
            <label class="form-label">远程备份路径</label>
            <input type="text" class="form-input" value="/clash-meta-backups/">
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:14px;">
            <div class="settings-main">
              <div class="settings-title">启动与配置变更时自动同步</div>
              <div class="settings-desc">自动双向比对并合并多设备订阅</div>
            </div>
            <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:14px;">
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="testWebdav()">测试连接</button>
            <button class="demo-btn" style="justify-content:center; padding:10px; background:var(--accent-blue);" onclick="syncWebdavNow()">立即同步 (Sync)</button>
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="pullWebdav()">云端拉取 (Pull)</button>
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="pushWebdav()">本地推送 (Push)</button>
          </div>
        </div>
      </div>

      <!-- Profile Properties Modal -->
      <div class="bottom-sheet" id="profilePropsSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title" id="propsSheetTitle">配置属性</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">订阅链接 (URL)</label>
            <input type="text" id="propUrlInput" class="form-input" value="">
          </div>
          <div class="form-group">
            <label class="form-label">自动更新间隔 (分钟)</label>
            <input type="number" id="propIntervalInput" class="form-input" value="1440">
          </div>
          <div class="form-group">
            <label class="form-label">age 敏感配置加密密钥</label>
            <div style="display:flex; justify-content:space-between; align-items:center; background:var(--card-subtle); padding:10px 12px; border-radius:var(--radius-sm); border:1px solid var(--card-border);">
              <span id="ageKeyStatus">已设置 (age1ql3z7h68...)</span>
              <button class="demo-btn" style="padding:4px 8px; font-size:11px;" onclick="showToast('已更新 age 加密配置密钥')">变更</button>
            </div>
          </div>
          <button class="demo-btn" style="width:100%; justify-content:center; padding:12px; margin-top:10px; background:var(--accent-blue);" onclick="showToast('配置属性已保存'); closeSheet();">保存配置属性</button>
        </div>
      </div>

      <!-- Network Stack Selector Sheet -->
      <div class="bottom-sheet" id="networkStackSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">网络协议栈 (Network Stack)</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div style="font-size:12px; color:var(--text-secondary); margin-bottom:14px;">
            选择 Android TUN 虚拟网卡底层运行的 TCP/IP 协议栈实现：
          </div>
          <div class="card" style="margin-bottom:10px; padding:12px; cursor:pointer;" onclick="setNetworkStack('gVisor')">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span style="font-weight:700; font-size:14px;">gVisor (推荐)</span>
              <span class="demo-tag" style="background:var(--accent-blue-subtle); color:var(--accent-blue);">默认</span>
            </div>
            <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;">Google 用户态网络栈，稳定兼容 Android 10-15，高并发与游戏防丢包首选。</div>
          </div>
          <div class="card" style="margin-bottom:10px; padding:12px; cursor:pointer;" onclick="setNetworkStack('System')">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span style="font-weight:700; font-size:14px;">System (系统原生栈)</span>
            </div>
            <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;">利用 Android Linux 内核网络栈转发，CPU 开销稍低，个别设备可能存在 UDP 阻断。</div>
          </div>
          <div class="card" style="margin-bottom:10px; padding:12px; cursor:pointer;" onclick="setNetworkStack('Mixed')">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span style="font-weight:700; font-size:14px;">Mixed (混合模式)</span>
            </div>
            <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;">TCP 采用系统原生栈，UDP 走 gVisor 用户栈，兼顾吞吐量与稳定性。</div>
          </div>
        </div>
      </div>

      <!-- Configuration Override Sheet -->
      <div class="bottom-sheet" id="overrideSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">配置覆写 (Config Overrides)</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">混合入站端口 (Mixed Port)</label>
            <input type="number" id="overrideMixedPort" class="form-input" value="7890">
          </div>
          <div class="form-group">
            <label class="form-label">SOCKS5 入站端口</label>
            <input type="number" id="overrideSocksPort" class="form-input" value="7891">
          </div>
          <div class="form-group">
            <label class="form-label">RESTful 控制 API 端口</label>
            <input type="number" id="overrideRestPort" class="form-input" value="9090">
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:10px;">
            <div class="settings-main">
              <div class="settings-title">允许来自局域网的连接 (Allow LAN)</div>
              <div class="settings-desc">共享热点或局域网设备代理</div>
            </div>
            <label class="switch"><input type="checkbox" id="overrideAllowLan"><span class="slider"></span></label>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:10px;">
            <div class="settings-main">
              <div class="settings-title">全局域名嗅探 (Sniffer)</div>
              <div class="settings-desc">嗅探 TLS SNI 与 HTTP Host，防止 IP 分流误判</div>
            </div>
            <label class="switch"><input type="checkbox" id="overrideSniffer" checked><span class="slider"></span></label>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:14px;">
            <div class="settings-main">
              <div class="settings-title">DNS Fake-IP 模式 (198.18.0.0/16)</div>
              <div class="settings-desc">秒级并发 DNS 解析与防污染</div>
            </div>
            <label class="switch"><input type="checkbox" id="overrideFakeIp" checked><span class="slider"></span></label>
          </div>
          <button class="demo-btn" style="width:100%; justify-content:center; padding:12px; background:var(--accent-blue);" onclick="saveOverrides()">保存并应用覆写</button>
        </div>
      </div>

      <!-- Meta Features Sheet -->
      <div class="bottom-sheet" id="metaFeaturesSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">Meta 内核特性 (Mihomo Core)</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">GeoIP / GeoSite 数据库源</label>
            <div style="display:flex; gap:8px;">
              <button class="demo-btn" id="geoBtnMeta" style="flex:1; justify-content:center; background:var(--accent-blue);" onclick="setGeoSource('MetaCubeX')">MetaCubeX (官方)</button>
              <button class="demo-btn" id="geoBtnLoyal" style="flex:1; justify-content:center;" onclick="setGeoSource('Loyalsoldier')">Loyalsoldier (增强)</button>
            </div>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:10px;">
            <div class="settings-main">
              <div class="settings-title">进程包名提取 (FindProcess)</div>
              <div class="settings-desc">允许规则直接使用 PROCESS-NAME 匹配连接应用</div>
            </div>
            <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:10px;">
            <div class="settings-main">
              <div class="settings-title">TCP 并发连接 (TCP Concurrent)</div>
              <div class="settings-desc">并发多 IP 测速首包连接，大幅降低建连延迟</div>
            </div>
            <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:14px;">
            <div class="settings-main">
              <div class="settings-title">统一 RTT 延迟算法 (Unified Delay)</div>
              <div class="settings-desc">剔除握手偏差，提供高精度真实延迟排序</div>
            </div>
            <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
          </div>
          <button class="demo-btn" style="width:100%; justify-content:center; padding:12px; background:var(--accent-blue);" onclick="showToast('Meta 内核特性配置已应用'); closeSheet();">完成</button>
        </div>
      </div>

      <!-- New Profile Modal Sheet -->
      <div class="bottom-sheet" id="newProfileSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">新建 / 导入配置档案</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">档案名称</label>
            <input type="text" id="newProfName" class="form-input" placeholder="例如：我的高速订阅">
          </div>
          <div class="form-group">
            <label class="form-label">订阅 URL 地址</label>
            <input type="text" id="newProfUrl" class="form-input" placeholder="https://airport.com/sub?token=...">
          </div>
          <div class="form-group">
            <label class="form-label">自动更新间隔 (分钟)</label>
            <input type="number" id="newProfInterval" class="form-input" value="1440">
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:12px;">
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="showToast('正在打开摄像头扫描二维码...')">📷 扫二维码</button>
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="showToast('打开文件管理器导入本地 YAML...')">📁 导入文件</button>
          </div>
          <button class="demo-btn" style="width:100%; justify-content:center; padding:12px; margin-top:14px; background:var(--accent-blue);" onclick="submitNewProfile()">确认导入并保存</button>
        </div>
      </div>

      <!-- New App Strategy Modal Sheet -->
      <div class="bottom-sheet" id="newStrategySheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">新建应用分流策略 (TIIEHenry)</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">分流策略名称</label>
            <input type="text" id="newStratName" class="form-input" placeholder="例如：办公协作分流、影音媒体分流">
          </div>
          <div class="form-group">
            <label class="form-label">分流策略模式</label>
            <select id="newStratMode" class="form-input">
              <option value="AcceptSelected">仅代理所选应用 (AcceptSelected / 白名单)</option>
              <option value="DenySelected">仅绕过所选应用 (DenySelected / 黑名单)</option>
              <option value="AcceptAll">代理全部应用 (AcceptAll / 全局)</option>
            </select>
          </div>
          <div class="settings-item" style="border-radius:var(--radius-sm); margin-bottom:14px;">
            <div class="settings-main">
              <div class="settings-title">立即启用该策略</div>
              <div class="settings-desc">在核心分流规则链中激活</div>
            </div>
            <label class="switch"><input type="checkbox" id="newStratActive" checked><span class="slider"></span></label>
          </div>
          <button class="demo-btn" style="width:100%; justify-content:center; padding:12px; background:var(--accent-blue);" onclick="submitNewStrategy()">保存策略并配置应用</button>
        </div>
      </div>

      <!-- About Modal Sheet -->
      <div class="bottom-sheet" id="aboutSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">关于 ClashMetaX UI 2.0</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div style="text-align:center; padding:10px 0 16px;">
            <div style="font-size:32px; margin-bottom:4px;">⚡</div>
            <div style="font-size:17px; font-weight:800;">ClashMetaX · TIIEHenry Fork</div>
            <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">版本: v2.10.8-meta · 内核: Mihomo v1.18.9</div>
          </div>
          <div class="card" style="padding:12px; font-size:12px; line-height:1.6; margin-bottom:12px; background:var(--card-subtle);">
            <div style="font-weight:700; margin-bottom:6px; color:var(--accent-blue);">完整保留的 Fork 核心特性：</div>
            <div>✅ <b>应用分流策略 (Apps Strategy)</b>：支持多配置方案与白名单/黑名单模式切换</div>
            <div>✅ <b>访问控制应用集 (Access Control)</b>：系统/国内应用筛选、一键反选与包名信息</div>
            <div>✅ <b>WebDAV 全量云同步</b>：局域网/公网双地址容灾、双向比对自动同步</div>
            <div>✅ <b>age 敏感配置加密</b>：支持安全密文存储与密钥热更新</div>
            <div>✅ <b>全特性提供者与文件管理</b>：Proxy/Rule Providers 批量健康检查与配置编辑</div>
          </div>
          <div style="text-align:center; font-size:11px; color:var(--text-tertiary);">
            UI 2.0 原型系统 · Bento Grid 状态看板 · 零延迟无缝切流
          </div>
        </div>
      </div>


      <!-- Strategy Edit / Delete Sheet (PRD-004) -->
      <div class="bottom-sheet" id="strategyEditSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">编辑分流策略</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">策略名称</label>
            <input type="text" id="editStratName" class="form-input">
          </div>
          <div class="form-group">
            <label class="form-label">分流路由模式</label>
            <select id="editStratMode" class="form-input">
              <option value="AcceptSelected">仅允许选中的应用进入隧道 (AcceptSelected / 白名单)</option>
              <option value="DenySelected">仅禁止选中的应用进入隧道 (DenySelected / 黑名单直连)</option>
              <option value="AcceptAll">全部应用允许进入隧道 (AcceptAll / 全局模式)</option>
            </select>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:14px;">
            <button class="demo-btn" style="justify-content:center; padding:10px; color:var(--accent-red); border-color:var(--accent-red-subtle);" onclick="deleteCurrentStrategy()">删除此策略</button>
            <button class="demo-btn" style="justify-content:center; padding:10px; background:var(--accent-blue);" onclick="saveStrategyEdits()">保存修改</button>
          </div>
        </div>
      </div>

            <!-- Sheet: New App Set Modal -->
      <div class="bottom-sheet" id="newAppSetSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">新建自定义应用集</div>
          <button class="icon-btn" onclick="closeSheet('newAppSetSheet')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">集合名称</label>
            <input type="text" id="newAppSetName" class="form-input" placeholder="例如：海外社交、国服网游、办公通讯">
          </div>
          <div style="display:grid; grid-template-columns:80px 1fr; gap:10px;">
            <div class="form-group">
              <label class="form-label">图标 Emoji</label>
              <input type="text" id="newAppSetIcon" class="form-input" value="📦" style="text-align:center; font-size:16px;">
            </div>
            <div class="form-group">
              <label class="form-label">集合说明</label>
              <input type="text" id="newAppSetDesc" class="form-input" placeholder="简短描述该集合用途">
            </div>
          </div>
          <div style="margin-top:10px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
              <label class="form-label" style="margin-bottom:0;">勾选包含的应用 (<span id="newAppSetPickCount" style="color:var(--accent-blue); font-weight:700;">0</span> 已选)</label>
              <input type="text" id="newAppSetSearchInput" class="form-input" placeholder="过滤应用..." style="width:120px; padding:3px 6px; font-size:11px;" oninput="onFilterNewAppSetPicker(this.value)">
            </div>
            <div class="app-pick-list" id="newAppSetPickList"></div>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:14px;">
            <button class="demo-btn" style="justify-content:center; padding:10px;" onclick="closeSheet('newAppSetSheet')">取消</button>
            <button class="demo-btn" style="justify-content:center; padding:10px; background:var(--accent-blue);" onclick="submitNewAppSet()">确认创建</button>
          </div>
        </div>
      </div>

      <!-- Sheet: Edit App Set Modal -->
      <div class="bottom-sheet" id="editAppSetSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title" id="editAppSetSheetTitle">编辑应用集</div>
          <button class="icon-btn" onclick="closeSheet('editAppSetSheet')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="sheet-content">
          <div class="form-group">
            <label class="form-label">集合名称</label>
            <input type="text" id="editAppSetName" class="form-input">
          </div>
          <div style="display:grid; grid-template-columns:80px 1fr; gap:10px;">
            <div class="form-group">
              <label class="form-label">图标 Emoji</label>
              <input type="text" id="editAppSetIcon" class="form-input" style="text-align:center; font-size:16px;">
            </div>
            <div class="form-group">
              <label class="form-label">集合说明</label>
              <input type="text" id="editAppSetDesc" class="form-input">
            </div>
          </div>
          <div style="margin-top:10px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
              <label class="form-label" style="margin-bottom:0;">成员应用勾选 (<span id="editAppSetPickCount" style="color:var(--accent-blue); font-weight:700;">0</span> 已包含)</label>
              <input type="text" id="editAppSetSearchInput" class="form-input" placeholder="过滤应用..." style="width:120px; padding:3px 6px; font-size:11px;" oninput="onFilterEditAppSetPicker(this.value)">
            </div>
            <div class="app-pick-list" id="editAppSetPickList"></div>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:14px;">
            <button class="demo-btn" id="btnDeleteAppSet" style="justify-content:center; padding:10px; color:var(--accent-red); border-color:var(--accent-red-subtle);" onclick="deleteCurrentAppSet()">删除此集合</button>
            <button class="demo-btn" style="justify-content:center; padding:10px; background:var(--accent-blue);" onclick="saveAppSetEdits()">保存修改</button>
          </div>
        </div>
      </div>

      <!-- Bottom Tab Navigation Bar -->
      <nav class="bottom-nav">
        <div class="nav-item active" onclick="switchTab('dashboard')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
          <span>仪表盘</span>
        </div>
        <div class="nav-item" onclick="switchTab('proxies')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/>
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          </svg>
          <span>节点</span>
        </div>
        <div class="nav-item" onclick="switchTab('profiles')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
          </svg>
          <span>配置</span>
        </div>
        <div class="nav-item" onclick="switchTab('apps')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
            <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
            <line x1="6" y1="6" x2="6.01" y2="6"/>
            <line x1="6" y1="18" x2="6.01" y2="18"/>
          </svg>
          <span>分流</span>
        </div>
        <div class="nav-item" onclick="switchTab('settings')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          <span>设置</span>
        </div>
        <div class="nav-indicator"></div>
      </nav>

    </div>
  </div>
"""
