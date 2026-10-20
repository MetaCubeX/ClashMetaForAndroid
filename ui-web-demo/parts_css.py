"""
parts_css.py - CSS styles for ClashMetaX (CMFA) UI 2.0 Web Demo
Theme: 现代极简原生 (Modern Minimalist Native)
Style Principles:
- True system dark & light grouped palettes (OLED #000000 / Grouped #F2F2F7)
- Clean flat surfaces, 0.5px/1px hairline separation, zero gaudy gradients or neon glow
- Native system ergonomics: Grouped lists, iOS/Material segmented controls, native switches
- Restrained, purposeful color accents (System Blue, System Emerald Green, Orange, Red)
- Clean typographic scale with native system font stacks
"""

CSS_BLOCK = """
    :root {
      /* OLED Dark System Palette */
      --bg: #000000;
      --card-bg: #15161a;
      --card-subtle: #1f2026;
      --card-border: rgba(255, 255, 255, 0.08);
      --card-border-active: #0a84ff;
      --card-border-green: #30d158;
      --text-primary: #ffffff;
      --text-secondary: #8e8e93;
      --text-tertiary: #636366;
      --accent-blue: #0a84ff;
      --accent-blue-subtle: rgba(10, 132, 255, 0.12);
      --accent-green: #30d158;
      --accent-green-subtle: rgba(48, 209, 88, 0.12);
      --accent-orange: #ff9f0a;
      --accent-orange-subtle: rgba(255, 159, 10, 0.12);
      --accent-red: #ff453a;
      --accent-red-subtle: rgba(255, 69, 58, 0.12);
      --accent-purple: #bf5af2;
      --nav-bg: rgba(21, 22, 26, 0.85);
      --nav-border: rgba(255, 255, 255, 0.06);
      --sheet-bg: #18191e;
      --badge-bg: rgba(255, 255, 255, 0.06);
      --shadow: none;
      --radius-xs: 6px;
      --radius-sm: 8px;
      --radius-md: 12px;
      --radius-lg: 16px;
      --radius-xl: 22px;
      --font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "Roboto", "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
      --font-mono: ui-monospace, "SF Mono", "Cascadia Code", Menlo, Monaco, Consolas, monospace;
    }

    [data-theme="light"] {
      /* Native Light Grouped Palette */
      --bg: #f2f2f7;
      --card-bg: #ffffff;
      --card-subtle: #e9e9eb;
      --card-border: rgba(0, 0, 0, 0.07);
      --card-border-active: #007aff;
      --card-border-green: #34c759;
      --text-primary: #000000;
      --text-secondary: #6c6c70;
      --text-tertiary: #aeaeb2;
      --accent-blue: #007aff;
      --accent-blue-subtle: rgba(0, 122, 255, 0.1);
      --accent-green: #34c759;
      --accent-green-subtle: rgba(52, 199, 89, 0.1);
      --accent-orange: #ff9500;
      --accent-orange-subtle: rgba(255, 149, 0, 0.1);
      --accent-red: #ff3b30;
      --accent-red-subtle: rgba(255, 59, 48, 0.1);
      --accent-purple: #af52de;
      --nav-bg: rgba(255, 255, 255, 0.88);
      --nav-border: rgba(0, 0, 0, 0.06);
      --sheet-bg: #ffffff;
      --badge-bg: rgba(0, 0, 0, 0.05);
      --shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }

    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; user-select: none; }
    body { background: #08080a; color: var(--text-primary); font-family: var(--font-family); min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; overflow-x: hidden; padding: 12px 10px 36px; -webkit-font-smoothing: antialiased; }
    
    /* Top Demo Control Header */
    .demo-control-bar { width: 100%; max-width: 900px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 18px; margin-bottom: 16px; background: rgba(255, 255, 255, 0.04); border: 1px solid rgba(255, 255, 255, 0.08); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); border-radius: var(--radius-md); color: #fff; }
    .demo-title-group h1 { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 8px; letter-spacing: -0.2px; }
    .demo-tag { font-size: 10px; font-weight: 600; padding: 2px 6px; background: var(--accent-blue); border-radius: 4px; letter-spacing: 0.2px; }
    .demo-feature-pill { font-size: 11px; padding: 2px 7px; background: rgba(255, 255, 255, 0.08); border-radius: 6px; color: #aaa; font-weight: 500; }
    .demo-actions { display: flex; align-items: center; gap: 8px; }
    .demo-btn { background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.12); color: #fff; font-size: 12px; font-weight: 500; padding: 5px 11px; border-radius: 7px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: all 0.15s ease; }
    .demo-btn:hover { background: rgba(255, 255, 255, 0.14); }
    .demo-btn:active { transform: scale(0.97); }

    /* Phone Shell (Ultra-Slim Minimalist Bezel) */
    .device-wrapper { position: relative; transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
    .device-phone { width: 400px; height: 860px; background: var(--bg); border-radius: 44px; box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6), 0 0 0 4px #1c1d22, 0 0 0 5px rgba(255,255,255,0.06); overflow: hidden; display: flex; flex-direction: column; position: relative; }
    .device-phone.fullscreen-mode { width: 100vw; max-width: 480px; height: 94vh; border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.3); border: 1px solid var(--card-border); }
    
    /* Native Status Bar */
    .status-bar { height: 42px; padding: 0 22px; display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: 600; color: var(--text-primary); z-index: 50; position: relative; }
    .status-notch { position: absolute; top: 8px; left: 50%; transform: translateX(-50%); width: 104px; height: 22px; background: #000; border-radius: 14px; display: flex; align-items: center; justify-content: center; gap: 6px; padding: 0 8px; }
    .status-camera { width: 9px; height: 9px; border-radius: 50%; background: #0d0d0f; border: 1px solid #1a1a1f; }
    .status-island-icon { font-size: 9px; color: var(--accent-green); opacity: 0; transition: opacity 0.25s; }
    .status-island-icon.active { opacity: 1; }

    /* App Screen & Pages */
    .app-screen { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 6px 16px 84px; scroll-behavior: smooth; position: relative; }
    .app-screen::-webkit-scrollbar { display: none; }
    .page-view { display: none; animation: fadeIn 0.2s ease-out; }
    .page-view.active { display: block; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

    /* Native Segmented Switcher (Top of Apps & elsewhere) */
    .sub-page-switcher { background: var(--card-subtle); border: 1px solid var(--card-border); border-radius: 9px; display: flex; padding: 2px; margin-bottom: 14px; }
    .sub-switch-btn { flex: 1; text-align: center; padding: 6px 0; font-size: 12px; font-weight: 500; color: var(--text-secondary); border-radius: 7px; cursor: pointer; transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1); }
    .sub-switch-btn.active { background: var(--card-bg); color: var(--text-primary); font-weight: 600; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12); border: 0.5px solid var(--card-border); }

    /* Native Bottom Tab Navigation Bar */
    .bottom-nav { position: absolute; bottom: 0; left: 0; right: 0; height: 74px; background: var(--nav-bg); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px); border-top: 0.5px solid var(--nav-border); display: flex; justify-content: space-around; align-items: flex-start; padding-top: 7px; z-index: 40; }
    .nav-item { display: flex; flex-direction: column; align-items: center; gap: 2px; color: var(--text-secondary); font-size: 10px; font-weight: 500; cursor: pointer; width: 58px; transition: color 0.15s; }
    .nav-item svg { width: 21px; height: 21px; stroke-width: 1.8; transition: transform 0.15s; }
    .nav-item:active svg { transform: scale(0.9); }
    .nav-item.active { color: var(--accent-blue); font-weight: 600; }
    .nav-indicator { position: absolute; bottom: 5px; left: 50%; transform: translateX(-50%); width: 120px; height: 4px; background: var(--text-primary); opacity: 0.22; border-radius: 2px; }

    /* Native Flat Cards */
    .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-lg); padding: 14px 16px; margin-bottom: 10px; box-shadow: var(--shadow); transition: transform 0.15s, border-color 0.15s; }
    .card-interactive:active { transform: scale(0.985); }
    
    /* Page Headers */
    .page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; padding: 2px 2px; }
    .page-title { font-size: 22px; font-weight: 700; letter-spacing: -0.5px; }
    .page-header-actions { display: flex; gap: 8px; }
    .icon-btn { width: 34px; height: 34px; border-radius: 50%; background: var(--card-subtle); border: 1px solid var(--card-border); color: var(--text-primary); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.15s; }
    .icon-btn:active { transform: scale(0.92); }

    /* Hero Master Connect Card (Clean Minimalist Native) */
    .hero-connect-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-xl); padding: 18px 16px; position: relative; overflow: hidden; margin-bottom: 12px; }
    .connect-top-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
    .connect-status-badge { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; background: var(--card-subtle); border: 1px solid var(--card-border); color: var(--text-secondary); transition: all 0.2s; }
    .connect-status-badge.running { background: var(--accent-green-subtle); border-color: rgba(48, 209, 88, 0.25); color: var(--accent-green); }
    .pulse-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--text-tertiary); transition: all 0.2s; }
    .connect-status-badge.running .pulse-dot { background: var(--accent-green); }
    .uptime-counter { font-size: 12px; font-family: var(--font-mono); color: var(--text-secondary); font-weight: 500; }
    
    /* Native Tactile Master Power Button */
    .connect-center { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4px 0 14px; }
    .power-button { width: 74px; height: 74px; border-radius: 50%; background: var(--card-subtle); border: 1.5px solid var(--card-border); color: var(--text-secondary); display: flex; align-items: center; justify-content: center; cursor: pointer; position: relative; transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1); }
    .power-button svg { width: 32px; height: 32px; transition: all 0.2s; }
    .power-button:active { transform: scale(0.92); }
    .power-button.running { background: var(--accent-green); border-color: var(--accent-green); color: #ffffff; box-shadow: 0 4px 16px rgba(48, 209, 88, 0.28); }
    .power-button.connecting { border-color: var(--accent-blue); color: var(--accent-blue); animation: spinBorder 1.2s linear infinite; }
    @keyframes spinBorder { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
    .power-hint { margin-top: 10px; font-size: 14px; font-weight: 600; color: var(--text-primary); letter-spacing: -0.2px; }
    .power-subhint { font-size: 11px; color: var(--text-secondary); margin-top: 2px; }

    /* Native iOS / Material Segmented Mode Picker */
    .mode-segmented-control { background: var(--card-subtle); border: 1px solid var(--card-border); border-radius: 9px; display: flex; padding: 2px; margin-top: 12px; }
    .mode-tab { flex: 1; text-align: center; padding: 6px 0; font-size: 12px; font-weight: 500; color: var(--text-secondary); border-radius: 7px; cursor: pointer; transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1); }
    .mode-tab.active { background: var(--card-bg); color: var(--text-primary); font-weight: 600; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12); border: 0.5px solid var(--card-border); }

    /* Outbound Node & Egress Perception Card */
    .outbound-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-lg); padding: 13px 15px; margin-bottom: 10px; cursor: pointer; transition: all 0.18s; }
    .outbound-card:hover { border-color: var(--card-border-active); }
    .outbound-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
    .outbound-label { font-size: 11px; font-weight: 600; color: var(--text-secondary); display: flex; align-items: center; gap: 5px; }
    .outbound-latency-pill { font-size: 11px; font-weight: 600; font-family: var(--font-mono); padding: 2px 7px; border-radius: 5px; background: var(--accent-green-subtle); color: var(--accent-green); }
    .outbound-node-name { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 7px; }
    .outbound-meta-row { display: flex; justify-content: space-between; align-items: center; margin-top: 9px; padding-top: 7px; border-top: 0.5px solid var(--card-border); font-size: 11px; color: var(--text-secondary); }
    .outbound-ip-tag { font-family: var(--font-mono); font-size: 11px; background: var(--badge-bg); padding: 1px 5px; border-radius: 4px; color: var(--text-primary); }

    /* Traffic Card (Clean Minimal Signal Line) */
    .traffic-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-lg); padding: 13px 15px; margin-bottom: 10px; }
    .traffic-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .traffic-stat { display: flex; flex-direction: column; gap: 2px; }
    .traffic-label { font-size: 11px; color: var(--text-secondary); display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .traffic-rate { font-size: 15px; font-weight: 700; font-family: var(--font-mono); }
    .traffic-rate.down { color: var(--accent-blue); }
    .traffic-rate.up { color: var(--accent-green); }
    .canvas-container { width: 100%; height: 56px; margin: 4px 0; position: relative; }
    canvas#trafficCanvas { width: 100%; height: 100%; display: block; }
    .traffic-totals { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-secondary); padding-top: 6px; border-top: 0.5px solid var(--card-border); }

    /* Bento Feature Row on Home */
    .dashboard-features-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; margin-bottom: 12px; }
    .feature-bento-item { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); padding: 11px 13px; cursor: pointer; display: flex; flex-direction: column; justify-content: space-between; transition: all 0.15s; }
    .feature-bento-item:active { transform: scale(0.98); }
    .feature-bento-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
    .feature-bento-title { font-size: 12px; font-weight: 600; color: var(--text-primary); }
    .feature-bento-desc { font-size: 11px; color: var(--text-secondary); line-height: 1.35; }

    /* Proxies Tab */
    .filter-scroll-row { display: flex; gap: 6px; overflow-x: auto; padding: 2px 2px 10px; margin: 0 -2px; scrollbar-width: none; }
    .filter-scroll-row::-webkit-scrollbar { display: none; }
    .filter-chip { white-space: nowrap; padding: 5px 12px; border-radius: 16px; background: var(--card-subtle); border: 1px solid var(--card-border); font-size: 12px; font-weight: 500; color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
    .filter-chip.active { background: var(--accent-blue); color: #fff; border-color: var(--accent-blue); font-weight: 600; }
    .proxy-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .proxy-grid.list-view { grid-template-columns: 1fr; }
    .node-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); padding: 11px 12px; cursor: pointer; transition: all 0.15s; position: relative; display: flex; flex-direction: column; justify-content: space-between; }
    .node-card:active { transform: scale(0.98); }
    .node-card.selected { border-color: var(--accent-blue); background: var(--card-bg); box-shadow: 0 0 0 1px var(--accent-blue); }
    .node-name { font-size: 12px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; line-height: 1.35; }
    .node-footer { display: flex; align-items: center; justify-content: space-between; }
    .node-type-pill { font-size: 9px; font-weight: 600; text-transform: uppercase; padding: 2px 5px; border-radius: 4px; background: var(--badge-bg); color: var(--text-secondary); }
    .node-ping { font-size: 11px; font-weight: 600; font-family: var(--font-mono); display: flex; align-items: center; gap: 4px; }
    .node-ping.fast { color: var(--accent-green); }
    .node-ping.medium { color: var(--accent-orange); }
    .node-ping.slow { color: var(--accent-red); }

    /* Provider & Profile Cards */
    .provider-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); padding: 12px 14px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
    .provider-info h4 { font-size: 13px; font-weight: 600; margin-bottom: 2px; display: flex; align-items: center; gap: 6px; }
    .provider-info p { font-size: 11px; color: var(--text-secondary); }
    .profile-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-lg); padding: 14px 16px; margin-bottom: 10px; position: relative; cursor: pointer; }
    .profile-card.active-profile { border-color: var(--accent-blue); box-shadow: 0 0 0 1px var(--accent-blue); }
    .profile-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
    .progress-bar-track { width: 100%; height: 5px; background: var(--card-subtle); border-radius: 3px; overflow: hidden; margin: 6px 0; }
    .progress-bar-fill { height: 100%; background: var(--accent-blue); border-radius: 3px; }
    .profile-meta-row { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-secondary); margin-top: 5px; }
    .expiry-tag { color: var(--accent-orange); font-weight: 600; }
    .file-row { display: flex; align-items: center; justify-content: space-between; padding: 11px 13px; background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); margin-bottom: 8px; }

    /* Strategy Cards & Radio (PRD-004) */
    .strategy-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); padding: 13px 15px; margin-bottom: 10px; transition: all 0.15s ease; cursor: pointer; position: relative; }
    .strategy-card.active-strat { border-color: var(--accent-green); background: var(--card-bg); box-shadow: 0 0 0 1px var(--accent-green); }
    .strategy-card.active-strat::before { content: ""; position: absolute; left: 0; top: 10px; bottom: 10px; width: 3px; border-radius: 0 3px 3px 0; background: var(--accent-green); }
    .radio-indicator { width: 20px; height: 20px; border-radius: 50%; border: 1.5px solid var(--card-border); display: flex; align-items: center; justify-content: center; font-size: 12px; color: transparent; transition: all 0.15s ease; }
    .strategy-card.active-strat .radio-indicator { border-color: var(--accent-green); background: var(--accent-green); color: #fff; }

    /* App Items & Native Checkbox */
    .app-item-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-sm); margin-bottom: 6px; cursor: pointer; transition: background 0.15s; }
    .app-item-row:hover { background: var(--card-subtle); }
    .checkbox { width: 20px; height: 20px; border-radius: 5px; border: 1.5px solid var(--card-border); display: flex; align-items: center; justify-content: center; transition: all 0.15s; background: transparent; }
    .checkbox.checked { background: var(--accent-blue); border-color: var(--accent-blue); }
    .checkbox svg { width: 13px; height: 13px; color: #fff; display: none; }
    .checkbox.checked svg { display: block; }
    .checkbox.indeterminate { background: var(--accent-blue); border-color: var(--accent-blue); }
    .checkbox.indeterminate svg { display: none; }
    .checkbox.indeterminate::after { content: ""; display: block; width: 9px; height: 2px; background: #fff; border-radius: 1px; }

    /* Native Grouped Settings (iOS / Material 3 Style) */
    .settings-group { margin-bottom: 16px; background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); overflow: hidden; }
    .settings-group-title { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-secondary); margin: 14px 4px 6px; }
    .settings-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; background: transparent; border: none; border-bottom: 0.5px solid var(--card-border); cursor: pointer; transition: background 0.12s; }
    .settings-item:last-child { border-bottom: none; }
    .settings-item:active { background: var(--card-subtle); }
    .settings-main { display: flex; flex-direction: column; gap: 2px; }
    .settings-title { font-size: 13px; font-weight: 500; color: var(--text-primary); }
    .settings-desc { font-size: 11px; color: var(--text-secondary); }

    /* Native Switch Control */
    .switch { position: relative; display: inline-block; width: 44px; height: 25px; }
    .switch input { opacity: 0; width: 0; height: 0; }
    .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: var(--card-subtle); border: 1px solid var(--card-border); transition: 0.25s cubic-bezier(0.16, 1, 0.3, 1); border-radius: 25px; }
    .slider:before { position: absolute; content: ""; height: 19px; width: 19px; left: 2px; bottom: 2px; background-color: white; transition: 0.25s cubic-bezier(0.16, 1, 0.3, 1); border-radius: 50%; box-shadow: 0 1px 3px rgba(0,0,0,0.2); }
    input:checked + .slider { background-color: var(--accent-green); border-color: var(--accent-green); }
    input:checked + .slider:before { transform: translateX(19px); }

    /* Native Bottom Sheets & Modals */
    .bottom-sheet-backdrop { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.55); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); z-index: 90; opacity: 0; pointer-events: none; transition: opacity 0.25s ease; }
    .bottom-sheet-backdrop.show { opacity: 1; pointer-events: auto; }
    .bottom-sheet { position: absolute; left: 0; right: 0; bottom: 0; max-height: 84%; background: var(--sheet-bg); border-top-left-radius: 24px; border-top-right-radius: 24px; padding: 14px 18px 32px; z-index: 100; transform: translateY(100%); transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1); display: flex; flex-direction: column; border-top: 1px solid var(--card-border); }
    .bottom-sheet.show { transform: translateY(0); }
    .sheet-handle { width: 36px; height: 4px; background: var(--text-tertiary); border-radius: 2px; margin: 0 auto 12px; opacity: 0.45; }
    .sheet-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .sheet-title { font-size: 16px; font-weight: 600; letter-spacing: -0.2px; }
    .sheet-content { overflow-y: auto; max-height: 60vh; padding-right: 2px; }

    /* Toast */
    .toast-container { position: absolute; top: 48px; left: 50%; transform: translateX(-50%); z-index: 120; display: flex; flex-direction: column; gap: 6px; pointer-events: none; width: 90%; max-width: 320px; }
    .toast { background: rgba(26, 27, 34, 0.94); color: #fff; backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); padding: 8px 14px; border-radius: 10px; font-size: 12px; font-weight: 500; display: flex; align-items: center; gap: 8px; border: 1px solid rgba(255, 255, 255, 0.08); animation: toastIn 0.25s cubic-bezier(0.16, 1, 0.3, 1); box-shadow: 0 4px 14px rgba(0,0,0,0.25); }
    [data-theme="light"] .toast { background: rgba(255, 255, 255, 0.95); color: #1c1c1e; border-color: rgba(0,0,0,0.08); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); }
    @keyframes toastIn { from { opacity: 0; transform: translateY(-10px) scale(0.96); } to { opacity: 1; transform: translateY(0); } }

    /* Form Inputs */
    .form-group { margin-bottom: 12px; }
    .form-label { font-size: 11px; font-weight: 600; color: var(--text-secondary); margin-bottom: 5px; display: block; }
    .form-input { width: 100%; background: var(--card-subtle); border: 1px solid var(--card-border); border-radius: var(--radius-sm); padding: 9px 11px; color: var(--text-primary); font-size: 13px; outline: none; transition: border-color 0.15s; }
    .form-input:focus { border-color: var(--accent-blue); }

    /* Log Box */
    .log-box { background: #000; color: #30d158; font-family: var(--font-mono); font-size: 11px; line-height: 1.5; padding: 10px; border-radius: 8px; height: 260px; overflow-y: auto; border: 1px solid rgba(255, 255, 255, 0.08); }
    .log-line { margin-bottom: 3px; word-break: break-all; }
    .log-info { color: #5ac8fa; }
    .log-warn { color: #ff9f0a; }
    .log-err  { color: #ff453a; }

    /* App Sets & Dedicated Management (PRD-003 & PRD-004) */
    .app-set-card { display: flex; justify-content: space-between; align-items: center; padding: 11px 13px; background: var(--card-subtle); border: 1px solid var(--card-border); border-radius: var(--radius-sm); margin-bottom: 7px; cursor: pointer; transition: all 0.15s ease; }
    .app-set-card:hover { border-color: rgba(255, 255, 255, 0.14); }
    .app-set-info { display: flex; align-items: center; gap: 10px; }
    .app-set-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 16px; background: var(--card-bg); border: 0.5px solid var(--card-border); }
    .app-set-title { font-size: 12px; font-weight: 600; color: var(--text-primary); }
    .app-set-subtitle { font-size: 11px; color: var(--text-secondary); margin-top: 1px; }

    .app-set-actions { display: flex; align-items: center; gap: 7px; }
    .app-set-edit-btn { padding: 3px 7px; font-size: 11px; border-radius: 5px; background: var(--card-bg); border: 1px solid var(--card-border); color: var(--text-secondary); cursor: pointer; transition: all 0.15s; display: inline-flex; align-items: center; gap: 3px; }
    .app-set-edit-btn:hover { color: var(--text-primary); border-color: var(--text-secondary); }

    .app-set-mgmt-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius-md); padding: 13px 15px; margin-bottom: 10px; transition: all 0.15s; }
    .app-set-mgmt-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 7px; }
    .app-set-mgmt-title-group { display: flex; align-items: center; gap: 9px; }
    .app-set-mgmt-icon { width: 32px; height: 32px; border-radius: 7px; display: flex; align-items: center; justify-content: center; font-size: 16px; background: var(--card-subtle); border: 0.5px solid var(--card-border); }
    .app-set-mgmt-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
    .app-set-mgmt-desc { font-size: 11px; color: var(--text-secondary); margin-top: 1px; }
    .app-set-member-preview { display: flex; flex-wrap: wrap; gap: 5px; margin: 9px 0; padding: 7px 9px; background: var(--card-subtle); border-radius: var(--radius-sm); border: 0.5px solid var(--card-border); }
    .app-set-member-pill { font-size: 10px; padding: 2px 7px; border-radius: 4px; background: var(--card-bg); color: var(--text-primary); display: flex; align-items: center; gap: 3px; border: 0.5px solid var(--card-border); }
    .app-set-mgmt-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 8px; border-top: 0.5px solid var(--card-border); font-size: 11px; color: var(--text-tertiary); }

    /* App Pick List in Modals */
    .app-pick-list { max-height: 220px; overflow-y: auto; display: flex; flex-direction: column; gap: 5px; margin-top: 8px; padding-right: 2px; }
    .app-pick-row { display: flex; align-items: center; justify-content: space-between; padding: 7px 9px; background: var(--card-subtle); border: 0.5px solid var(--card-border); border-radius: var(--radius-sm); cursor: pointer; transition: background 0.12s; }
    .app-pick-row:hover { background: rgba(255, 255, 255, 0.05); }
    .app-pick-info { display: flex; align-items: center; gap: 8px; font-size: 12px; }
    .app-pick-name { font-weight: 500; color: var(--text-primary); }
    .app-pick-pkg { font-size: 10px; color: var(--text-secondary); }

    /* App Tag Badges (Minimalist Flat Tints) */
    .app-tag-pill { font-size: 10px; font-weight: 600; padding: 1px 5px; border-radius: 3px; margin-left: 5px; }
    .app-tag-system { background: var(--accent-blue-subtle); color: var(--accent-blue); }
    .app-tag-domestic { background: var(--accent-orange-subtle); color: var(--accent-orange); }
    .app-tag-custom { background: rgba(191, 90, 242, 0.12); color: var(--accent-purple); }
    .app-tag-social { background: var(--accent-blue-subtle); color: var(--accent-blue); }
    .app-tag-media { background: var(--accent-red-subtle); color: var(--accent-red); }
    .app-tag-work { background: var(--accent-green-subtle); color: var(--accent-green); }
"""
