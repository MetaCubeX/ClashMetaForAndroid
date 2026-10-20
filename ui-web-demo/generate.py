import os

html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>ClashMetaX UI Redesign - Interactive Prototype</title>
  <style>
    :root {
      --bg: #0c0d11;
      --card-bg: #16181f;
      --card-subtle: #1c1f28;
      --card-border: rgba(255, 255, 255, 0.08);
      --card-border-active: rgba(10, 132, 255, 0.45);
      --text-primary: #f5f5f7;
      --text-secondary: #8e8e93;
      --text-tertiary: #545458;
      --accent-blue: #0a84ff;
      --accent-blue-subtle: rgba(10, 132, 255, 0.15);
      --accent-green: #30d158;
      --accent-green-subtle: rgba(48, 209, 88, 0.15);
      --accent-orange: #ff9f0a;
      --accent-orange-subtle: rgba(255, 159, 10, 0.15);
      --accent-red: #ff453a;
      --accent-red-subtle: rgba(255, 69, 58, 0.15);
      --accent-purple: #bf5af2;
      --nav-bg: rgba(22, 24, 31, 0.88);
      --nav-border: rgba(255, 255, 255, 0.06);
      --sheet-bg: #1c1e26;
      --badge-bg: rgba(255, 255, 255, 0.08);
      --shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
      --radius-sm: 8px;
      --radius-md: 14px;
      --radius-lg: 20px;
      --radius-xl: 28px;
      --font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
    }

    [data-theme="light"] {
      --bg: #f2f4f8;
      --card-bg: #ffffff;
      --card-subtle: #f6f8fb;
      --card-border: rgba(0, 0, 0, 0.06);
      --card-border-active: rgba(0, 122, 255, 0.35);
      --text-primary: #1c1c1e;
      --text-secondary: #8a8a8e;
      --text-tertiary: #c7c7cc;
      --accent-blue: #007aff;
      --accent-blue-subtle: rgba(0, 122, 255, 0.1);
      --accent-green: #34c759;
      --accent-green-subtle: rgba(52, 199, 89, 0.1);
      --accent-orange: #ff9500;
      --accent-orange-subtle: rgba(255, 149, 0, 0.1);
      --accent-red: #ff3b30;
      --accent-red-subtle: rgba(255, 59, 48, 0.1);
      --accent-purple: #af52de;
      --nav-bg: rgba(255, 255, 255, 0.9);
      --nav-border: rgba(0, 0, 0, 0.06);
      --sheet-bg: #ffffff;
      --badge-bg: rgba(0, 0, 0, 0.05);
      --shadow: 0 8px 28px rgba(0, 0, 0, 0.06);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-tap-highlight-color: transparent;
      user-select: none;
    }

    body {
      background: #050608;
      color: var(--text-primary);
      font-family: var(--font-family);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      overflow-x: hidden;
      padding: 16px 10px 40px;
    }

    /* Top Demo Header Bar */
    .demo-control-bar {
      width: 100%;
      max-width: 900px;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 20px;
      margin-bottom: 20px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border-radius: var(--radius-md);
      color: #fff;
    }

    .demo-title-group h1 {
      font-size: 16px;
      font-weight: 700;
      letter-spacing: -0.2px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .demo-tag {
      font-size: 11px;
      font-weight: 600;
      padding: 2px 7px;
      background: var(--accent-blue);
      border-radius: 6px;
      letter-spacing: 0.5px;
    }

    .demo-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .demo-btn {
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #fff;
      font-size: 13px;
      font-weight: 500;
      padding: 6px 14px;
      border-radius: 8px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
    }

    .demo-btn:hover {
      background: rgba(255, 255, 255, 0.18);
    }

    /* Device Container */
    .device-wrapper {
      position: relative;
      transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .device-phone {
      width: 412px;
      height: 870px;
      background: var(--bg);
      border-radius: 48px;
      box-shadow: 0 25px 60px rgba(0, 0, 0, 0.6), 0 0 0 12px #20222a, 0 0 0 14px #363945;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      position: relative;
    }

    .device-phone.fullscreen-mode {
      width: 100vw;
      max-width: 480px;
      height: 92vh;
      border-radius: 24px;
      box-shadow: var(--shadow);
    }

    /* Phone Status Bar */
    .status-bar {
      height: 44px;
      padding: 0 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
      z-index: 50;
      position: relative;
    }

    .status-notch {
      position: absolute;
      top: 10px;
      left: 50%;
      transform: translateX(-50%);
      width: 110px;
      height: 24px;
      background: #000;
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 0 8px;
    }

    .status-camera {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #111;
      border: 1px solid #222;
    }

    .status-island-icon {
      font-size: 10px;
      color: var(--accent-green);
      opacity: 0;
      transition: opacity 0.3s;
    }

    .status-island-icon.active {
      opacity: 1;
    }

    .status-right {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    /* App View Container */
    .app-screen {
      flex: 1;
      overflow-y: auto;
      overflow-x: hidden;
      padding: 8px 18px 90px;
      scroll-behavior: smooth;
      position: relative;
    }

    .app-screen::-webkit-scrollbar {
      display: none;
    }

    /* Tab Page Views */
    .page-view {
      display: none;
      animation: fadeIn 0.25s ease-out;
    }

    .page-view.active {
      display: block;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Navigation Bar */
    .bottom-nav {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 80px;
      background: var(--nav-bg);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border-top: 1px solid var(--nav-border);
      display: flex;
      justify-content: space-around;
      align-items: flex-start;
      padding-top: 10px;
      z-index: 40;
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      color: var(--text-secondary);
      font-size: 11px;
      font-weight: 500;
      cursor: pointer;
      width: 64px;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .nav-item svg {
      width: 24px;
      height: 24px;
      transition: transform 0.2s;
    }

    .nav-item:active svg {
      transform: scale(0.88);
    }

    .nav-item.active {
      color: var(--accent-blue);
      font-weight: 600;
    }

    .nav-indicator {
      position: absolute;
      bottom: 6px;
      left: 50%;
      transform: translateX(-50%);
      width: 130px;
      height: 4px;
      background: var(--text-primary);
      opacity: 0.3;
      border-radius: 2px;
    }

    /* Common Card Styles */
    .card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 16px;
      margin-bottom: 14px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
      transition: transform 0.18s, border-color 0.2s;
    }

    .card-interactive:active {
      transform: scale(0.985);
    }

    /* Header in Page */
    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
      padding: 0 4px;
    }

    .page-title {
      font-size: 26px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }

    .page-header-actions {
      display: flex;
      gap: 8px;
    }

    .icon-btn {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--card-subtle);
      border: 1px solid var(--card-border);
      color: var(--text-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
    }

    .icon-btn:active {
      transform: scale(0.92);
      background: var(--badge-bg);
    }

    /* Master Connect Hero Card */
    .hero-connect-card {
      background: linear-gradient(145deg, var(--card-bg), var(--card-subtle));
      border: 1px solid var(--card-border);
      border-radius: var(--radius-xl);
      padding: 22px 20px;
      position: relative;
      overflow: hidden;
      margin-bottom: 16px;
    }

    .hero-connect-card::after {
      content: "";
      position: absolute;
      top: -40%;
      right: -20%;
      width: 180px;
      height: 180px;
      background: radial-gradient(circle, var(--accent-blue-subtle) 0%, transparent 70%);
      pointer-events: none;
      transition: background 0.5s;
    }

    .hero-connect-card.running::after {
      background: radial-gradient(circle, var(--accent-green-subtle) 0%, transparent 70%);
    }

    .connect-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 18px;
    }

    .connect-status-badge {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 6px 12px;
      border-radius: 30px;
      font-size: 12px;
      font-weight: 600;
      background: var(--card-subtle);
      border: 1px solid var(--card-border);
      color: var(--text-secondary);
      transition: all 0.3s;
    }

    .connect-status-badge.running {
      background: var(--accent-green-subtle);
      border-color: rgba(48, 209, 88, 0.3);
      color: var(--accent-green);
    }

    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--text-tertiary);
      transition: all 0.3s;
    }

    .connect-status-badge.running .pulse-dot {
      background: var(--accent-green);
      box-shadow: 0 0 10px var(--accent-green);
      animation: pulseAnim 1.6s infinite ease-in-out;
    }

    @keyframes pulseAnim {
      0%, 100% { transform: scale(1); opacity: 0.9; }
      50% { transform: scale(1.4); opacity: 0.5; }
    }

    .uptime-counter {
      font-size: 13px;
      font-variant-numeric: tabular-nums;
      color: var(--text-secondary);
      font-weight: 500;
    }

    .connect-center {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px 0 18px;
    }

    .power-button {
      width: 86px;
      height: 86px;
      border-radius: 50%;
      background: var(--card-subtle);
      border: 2px solid var(--card-border);
      color: var(--text-secondary);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      position: relative;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .power-button svg {
      width: 38px;
      height: 38px;
      transition: all 0.3s;
    }

    .power-button:active {
      transform: scale(0.93);
    }

    .power-button.running {
      background: linear-gradient(135deg, var(--accent-green), #249d42);
      border-color: rgba(255, 255, 255, 0.3);
      color: #fff;
      box-shadow: 0 10px 30px rgba(48, 209, 88, 0.45);
    }

    .power-button.connecting {
      border-color: var(--accent-blue);
      color: var(--accent-blue);
      animation: spinBorder 1.2s linear infinite;
    }

    @keyframes spinBorder {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .power-hint {
      margin-top: 14px;
      font-size: 15px;
      font-weight: 700;
      color: var(--text-primary);
    }

    .power-subhint {
      font-size: 12px;
      color: var(--text-secondary);
      margin-top: 2px;
    }

    /* Mode Selector (Segment Control) */
    .mode-segmented-control {
      background: var(--card-subtle);
      border: 1px solid var(--card-border);
      border-radius: 12px;
      display: flex;
      padding: 3px;
      margin-top: 8px;
      position: relative;
    }

    .mode-tab {
      flex: 1;
      text-align: center;
      padding: 7px 0;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-secondary);
      border-radius: 9px;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      position: relative;
      z-index: 2;
    }

    .mode-tab.active {
      background: var(--card-bg);
      color: var(--accent-blue);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    }

    [data-theme="light"] .mode-tab.active {
      background: #fff;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
    }

    /* Bento Grid System */
    .bento-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
      margin-bottom: 14px;
    }

    .bento-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 14px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      transition: transform 0.18s, border-color 0.2s;
    }

    /* Outbound Perception Card */
    .outbound-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 14px 16px;
      margin-bottom: 14px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .outbound-card:hover {
      border-color: var(--card-border-active);
    }

    .outbound-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .outbound-label {
      font-size: 12px;
      font-weight: 600;
      color: var(--text-secondary);
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .outbound-latency-pill {
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 10px;
      background: var(--accent-green-subtle);
      color: var(--accent-green);
    }

    .outbound-node-name {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-primary);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .outbound-meta-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 10px;
      padding-top: 8px;
      border-top: 1px solid var(--card-border);
      font-size: 12px;
      color: var(--text-secondary);
    }

    .outbound-ip-tag {
      font-family: monospace;
      font-size: 11px;
      background: var(--badge-bg);
      padding: 2px 6px;
      border-radius: 4px;
      color: var(--text-primary);
    }

    /* Traffic Waveform Card */
    .traffic-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 16px;
      margin-bottom: 14px;
    }

    .traffic-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .traffic-stat {
      display: flex;
      flex-direction: column;
    }

    .traffic-rate {
      font-size: 17px;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
    }

    .traffic-rate.down {
      color: var(--accent-blue);
    }

    .traffic-rate.up {
      color: var(--accent-green);
    }

    .traffic-label {
      font-size: 11px;
      font-weight: 500;
      color: var(--text-secondary);
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .canvas-container {
      width: 100%;
      height: 70px;
      margin: 8px 0;
      position: relative;
    }

    canvas#trafficCanvas {
      width: 100%;
      height: 100%;
      display: block;
    }

    .traffic-totals {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: var(--text-secondary);
      padding-top: 8px;
      border-top: 1px solid var(--card-border);
    }

    /* Proxies Tab */
    .filter-scroll-row {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding: 4px 2px 14px;
      margin: 0 -4px;
      scrollbar-width: none;
    }

    .filter-scroll-row::-webkit-scrollbar {
      display: none;
    }

    .filter-chip {
      white-space: nowrap;
      padding: 6px 14px;
      border-radius: 20px;
      background: var(--card-subtle);
      border: 1px solid var(--card-border);
      font-size: 13px;
      font-weight: 600;
      color: var(--text-secondary);
      cursor: pointer;
      transition: all 0.2s;
    }

    .filter-chip.active {
      background: var(--accent-blue);
      color: #fff;
      border-color: var(--accent-blue);
      box-shadow: 0 2px 10px var(--accent-blue-subtle);
    }

    .proxy-toolbar {
      display: flex;
      gap: 8px;
      margin-bottom: 12px;
    }

    .search-box {
      flex: 1;
      background: var(--card-subtle);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-md);
      padding: 8px 12px;
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--text-primary);
    }

    .search-box input {
      background: transparent;
      border: none;
      outline: none;
      color: var(--text-primary);
      font-size: 14px;
      width: 100%;
    }

    .search-box input::placeholder {
      color: var(--text-secondary);
    }

    .proxy-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }

    .proxy-grid.list-view {
      grid-template-columns: 1fr;
    }

    .node-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-md);
      padding: 12px;
      cursor: pointer;
      transition: all 0.18s;
      position: relative;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    .node-card:active {
      transform: scale(0.975);
    }

    .node-card.selected {
      border-color: var(--accent-blue);
      background: linear-gradient(145deg, var(--card-bg), var(--card-subtle));
      box-shadow: 0 0 0 1px var(--accent-blue);
    }

    .node-name {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 8px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      line-height: 1.35;
    }

    .node-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .node-type-pill {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 2px 6px;
      border-radius: 4px;
      background: var(--badge-bg);
      color: var(--text-secondary);
    }

    .node-ping {
      font-size: 11px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .node-ping.fast { color: var(--accent-green); }
    .node-ping.medium { color: var(--accent-orange); }
    .node-ping.slow { color: var(--accent-red); }

    /* Profiles Tab */
    .profile-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-lg);
      padding: 16px;
      margin-bottom: 12px;
      position: relative;
      transition: all 0.2s;
    }

    .profile-card.active-profile {
      border-color: var(--accent-blue);
      box-shadow: 0 0 0 1px var(--accent-blue);
    }

    .profile-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 12px;
    }

    .profile-title {
      font-size: 16px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .active-badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      background: var(--accent-blue);
      color: #fff;
      border-radius: 6px;
    }

    .progress-bar-track {
      width: 100%;
      height: 8px;
      background: var(--card-subtle);
      border-radius: 4px;
      overflow: hidden;
      margin: 8px 0;
    }

    .progress-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--accent-blue), #5ac8fa);
      border-radius: 4px;
      transition: width 0.5s ease;
    }

    .profile-meta-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--text-secondary);
      margin-top: 6px;
    }

    .expiry-tag {
      color: var(--accent-orange);
      font-weight: 600;
    }

    /* Apps Tab */
    .app-item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 14px;
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: var(--radius-md);
      margin-bottom: 8px;
      cursor: pointer;
      transition: all 0.15s;
    }

    .app-item-row:active {
      transform: scale(0.985);
    }

    .app-info {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .app-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      background: var(--card-subtle);
    }

    .app-name {
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .app-pkg {
      font-size: 11px;
      color: var(--text-secondary);
    }

    .checkbox {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      border: 2px solid var(--card-border);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .checkbox.checked {
      background: var(--accent-blue);
      border-color: var(--accent-blue);
    }

    .checkbox svg {
      width: 14px;
      height: 14px;
      color: #fff;
      display: none;
    }

    .checkbox.checked svg {
      display: block;
    }

    /* Settings Tab */
    .settings-group {
      margin-bottom: 20px;
    }

    .settings-group-title {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-secondary);
      margin-left: 6px;
      margin-bottom: 8px;
    }

    .settings-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 16px;
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      margin-bottom: -1px;
      cursor: pointer;
      transition: background 0.15s;
    }

    .settings-item:first-of-type {
      border-top-left-radius: var(--radius-md);
      border-top-right-radius: var(--radius-md);
    }

    .settings-item:last-of-type {
      border-bottom-left-radius: var(--radius-md);
      border-bottom-right-radius: var(--radius-md);
      margin-bottom: 0;
    }

    .settings-item:active {
      background: var(--card-subtle);
    }

    .settings-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .settings-title {
      font-size: 15px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .settings-desc {
      font-size: 12px;
      color: var(--text-secondary);
    }

    /* Switch Component */
    .switch {
      position: relative;
      display: inline-block;
      width: 46px;
      height: 26px;
    }

    .switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: var(--badge-bg);
      border: 1px solid var(--card-border);
      transition: 0.3s;
      border-radius: 26px;
    }

    .slider:before {
      position: absolute;
      content: "";
      height: 20px;
      width: 20px;
      left: 2px;
      bottom: 2px;
      background-color: white;
      transition: 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      border-radius: 50%;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }

    input:checked + .slider {
      background-color: var(--accent-green);
      border-color: var(--accent-green);
    }

    input:checked + .slider:before {
      transform: translateX(20px);
    }

    /* Modal / Bottom Sheet */
    .bottom-sheet-backdrop {
      position: absolute;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      backdrop-filter: blur(4px);
      -webkit-backdrop-filter: blur(4px);
      z-index: 90;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.25s ease;
    }

    .bottom-sheet-backdrop.show {
      opacity: 1;
      pointer-events: auto;
    }

    .bottom-sheet {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      max-height: 80%;
      background: var(--sheet-bg);
      border-top-left-radius: 28px;
      border-top-right-radius: 28px;
      padding: 18px 20px 36px;
      z-index: 100;
      transform: translateY(100%);
      transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      display: flex;
      flex-direction: column;
      box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.3);
    }

    .bottom-sheet.show {
      transform: translateY(0);
    }

    .sheet-handle {
      width: 40px;
      height: 4px;
      background: var(--text-tertiary);
      border-radius: 2px;
      margin: 0 auto 16px;
      opacity: 0.4;
    }

    .sheet-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .sheet-title {
      font-size: 18px;
      font-weight: 700;
    }

    .sheet-content {
      overflow-y: auto;
      max-height: 55vh;
      padding-right: 4px;
    }

    /* Toast Notification */
    .toast-container {
      position: absolute;
      top: 54px;
      left: 50%;
      transform: translateX(-50%);
      z-index: 120;
      display: flex;
      flex-direction: column;
      gap: 8px;
      pointer-events: none;
      width: 90%;
      max-width: 340px;
    }

    .toast {
      background: rgba(28, 30, 38, 0.94);
      color: #fff;
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      padding: 10px 16px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 10px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.1);
      animation: toastIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    [data-theme="light"] .toast {
      background: rgba(255, 255, 255, 0.95);
      color: #1c1c1e;
      border-color: rgba(0,0,0,0.08);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
    }

    @keyframes toastIn {
      from { opacity: 0; transform: translateY(-12px) scale(0.95); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }

    /* Log Console Drawer */
    .log-box {
      background: #000;
      color: #30d158;
      font-family: monospace;
      font-size: 11px;
      line-height: 1.5;
      padding: 12px;
      border-radius: 10px;
      height: 260px;
      overflow-y: auto;
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .log-line {
      margin-bottom: 4px;
      word-break: break-all;
    }

    .log-info { color: #5ac8fa; }
    .log-warn { color: #ff9f0a; }
  </style>
</head>
<body>

  <!-- Top Demo Control Bar -->
  <header class="demo-control-bar">
    <div class="demo-title-group">
      <h1>
        <span>ClashMetaX</span>
        <span class="demo-tag">UI 2.0 REDESIGN</span>
      </h1>
      <span style="font-size:12px; color:#aaa;">交互式高保真 Web 原型</span>
    </div>

    <div class="demo-actions">
      <button class="demo-btn" id="btnThemeToggle" title="切换暗黑/浅色模式">
        <span id="themeIcon">☀️</span> <span>主题切换</span>
      </button>
      <button class="demo-btn" id="btnPhoneMode" title="切换真机框 / 满屏">
        <span>📱</span> <span id="phoneModeText">全屏平铺</span>
      </button>
      <button class="demo-btn" id="btnRandomSpike" title="模拟流量波动">
        <span>⚡</span> <span>模拟突发流量</span>
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
            <h2 class="page-title">ClashMetaX</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnFastLogs" title="实时日志">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 17l6-6-6-6M12 19h8"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnFastReload" title="重载内核配置">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Hero Power Button & Status -->
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
              <div class="power-hint" id="powerHintText">安全网络已保护</div>
              <div class="power-subhint" id="powerSubhintText">配置：香港专线集群 · 规则模式</div>
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
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="2" y1="12" x2="22" y2="12"/>
                  <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
                </svg>
                出口节点 · 点击快速切换
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

          <!-- Bento Quick Navigation Grid -->
          <div class="bento-grid">
            <div class="bento-card card-interactive" onclick="switchTab('profiles')">
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <span style="font-size:12px; font-weight:700; color:var(--text-secondary);">订阅配置</span>
                <span style="font-size:11px; color:var(--accent-orange); font-weight:700;">18天到期</span>
              </div>
              <div style="margin:10px 0 6px;">
                <div style="font-size:15px; font-weight:700;">专线节点集群</div>
                <div style="font-size:12px; color:var(--text-secondary);">剩余 358 GB / 500 GB</div>
              </div>
              <div class="progress-bar-track">
                <div class="progress-bar-fill" style="width: 28%;"></div>
              </div>
            </div>

            <div class="bento-card card-interactive" onclick="switchTab('apps')">
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <span style="font-size:12px; font-weight:700; color:var(--text-secondary);">应用分流</span>
                <span style="font-size:11px; color:var(--accent-green); font-weight:700;">已生效</span>
              </div>
              <div style="margin:10px 0 4px;">
                <div style="font-size:15px; font-weight:700;">14 款应用代理</div>
                <div style="font-size:12px; color:var(--text-secondary);">绕过国内应用与支付</div>
              </div>
              <span style="font-size:11px; color:var(--accent-blue); font-weight:600;">管理应用名单 →</span>
            </div>
          </div>
        </section>

        <!-- ================= TAB 2: 节点 PROXIES ================= -->
        <section class="page-view" id="page-proxies">
          <div class="page-header">
            <h2 class="page-title">节点选择</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnUrlTest" title="并发一键测速">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnToggleLayout" title="切换视图">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="7" height="7"/>
                  <rect x="14" y="3" width="7" height="7"/>
                  <rect x="14" y="14" width="7" height="7"/>
                  <rect x="3" y="14" width="7" height="7"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Proxy Group Filter Tabs -->
          <div class="filter-scroll-row" id="groupFilterRow">
            <div class="filter-chip active" data-group="PROXY">🚀 节点选择 (12)</div>
            <div class="filter-chip" data-group="AUTO">⚡ 自动优选 (8)</div>
            <div class="filter-chip" data-group="MEDIA">🎬 流媒体 (6)</div>
            <div class="filter-chip" data-group="AI">🤖 AI 智能 (5)</div>
            <div class="filter-chip" data-group="DIRECT">🎯 全球直连</div>
          </div>

          <!-- Search & Filter Bar -->
          <div class="proxy-toolbar">
            <div class="search-box">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input type="text" id="proxySearchInput" placeholder="搜索节点、国家或协议...">
            </div>
          </div>

          <!-- Node List / Grid Container -->
          <div class="proxy-grid" id="nodeContainer"></div>
        </section>

        <!-- ================= TAB 3: 订阅 PROFILES ================= -->
        <section class="page-view" id="page-profiles">
          <div class="page-header">
            <h2 class="page-title">配置订阅</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnUpdateAllProfiles" title="更新全部订阅">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
              </button>
              <button class="icon-btn" id="btnAddProfile" title="添加新订阅">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Active Profile Card -->
          <div class="profile-card active-profile" id="prof1">
            <div class="profile-header">
              <div>
                <div class="profile-title">
                  <span>香港 IEPL 专线 · 年付</span>
                  <span class="active-badge">生效中</span>
                </div>
                <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">
                  https://sub.airport.io/api/v1/client/subscribe...
                </div>
              </div>
              <button class="icon-btn" onclick="updateSingleProfile(event, 'prof1')" title="刷新订阅">
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
            <div class="profile-meta-row" style="margin-top:8px; font-size:11px;">
              <span>包含 48 个节点</span>
              <span>更新于 10分钟前</span>
            </div>
          </div>

          <!-- Second Profile Card -->
          <div class="profile-card" id="prof2" onclick="activateProfile('prof2')">
            <div class="profile-header">
              <div>
                <div class="profile-title">
                  <span>备用自建 VLESS 节点</span>
                </div>
                <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">
                  本地文件 / 存储卡导入
                </div>
              </div>
              <button class="icon-btn" onclick="updateSingleProfile(event, 'prof2')">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
              </button>
            </div>
            <div class="profile-meta-row" style="margin-top:6px;">
              <span>包含 6 个节点</span>
              <span>更新于 3天前</span>
            </div>
          </div>

          <!-- WebDAV Cloud Sync Card -->
          <div class="card" style="margin-top:16px;">
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div>
                <div style="font-size:15px; font-weight:700;">WebDAV 云端同步</div>
                <div style="font-size:12px; color:var(--text-secondary); margin-top:2px;">上次同步：今天 08:30 · 已启用自动同步</div>
              </div>
              <button class="demo-btn" style="padding:6px 12px; font-size:12px;" onclick="showToast('已触发 WebDAV 立即备份同步')">立即备份</button>
            </div>
          </div>
        </section>

        <!-- ================= TAB 4: 分流与应用 APPS & RULES ================= -->
        <section class="page-view" id="page-apps">
          <div class="page-header">
            <h2 class="page-title">应用分流</h2>
            <div class="page-header-actions">
              <button class="icon-btn" id="btnActiveConn" title="活跃连接实时监控">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Strategy Mode Card -->
          <div class="card">
            <div style="font-size:14px; font-weight:700; margin-bottom:10px;">分流接管模式</div>
            <div class="mode-segmented-control">
              <div class="mode-tab active" data-appmode="bypass-cn">绕过大陆</div>
              <div class="mode-tab" data-appmode="whitelist">仅代理选定</div>
              <div class="mode-tab" data-appmode="blacklist">绕过选定</div>
            </div>
            <div style="font-size:12px; color:var(--text-secondary); margin-top:8px;" id="appModeDesc">
              国内应用直连，海外应用通过规则策略智能分流。
            </div>
          </div>

          <!-- App Whitelist Header -->
          <div style="display:flex; justify-content:space-between; align-items:center; margin:16px 4px 10px;">
            <span style="font-size:13px; font-weight:700; color:var(--text-secondary);">已安装应用 (<span id="selectedAppCount">5</span> 已选)</span>
            <span style="font-size:12px; color:var(--accent-blue); font-weight:600; cursor:pointer;" onclick="toggleSelectAllApps()">全选 / 反选</span>
          </div>

          <!-- App List Rows -->
          <div id="appListContainer"></div>
        </section>

        <!-- ================= TAB 5: 设置 SETTINGS ================= -->
        <section class="page-view" id="page-settings">
          <div class="page-header">
            <h2 class="page-title">系统设置</h2>
          </div>

          <div class="settings-group">
            <div class="settings-group-title">核心网络 (TUN & DNS)</div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">TUN 虚拟网卡模式</div>
                <div class="settings-desc">接管整机流量，支持 UDP 代理与游戏低延迟</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">自动绕过本地局域网</div>
                <div class="settings-desc">192.168.x.x 等私有 IP 不经过代理内核</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">DNS 智能防污染与嗅探</div>
                <div class="settings-desc">嗅探 TLS SNI 与 HTTP Host，修正分流规则</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
          </div>

          <div class="settings-group">
            <div class="settings-group-title">系统集成与体验</div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">开机自动启动代理</div>
                <div class="settings-desc">设备重启后自动在后台拉起 VPN 隧道</div>
              </div>
              <label class="switch"><input type="checkbox"><span class="slider"></span></label>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">通知栏快速磁贴 (Tile)</div>
                <div class="settings-desc">从下拉快捷控制面板一键启停和切模式</div>
              </div>
              <label class="switch"><input type="checkbox" checked><span class="slider"></span></label>
            </div>
          </div>

          <div class="settings-group">
            <div class="settings-group-title">进阶诊断与工具</div>
            <div class="settings-item" onclick="openLogSheet()">
              <div class="settings-main">
                <div class="settings-title">实时终端日志 (Logcat)</div>
                <div class="settings-desc">查看内核实时调度、连接分流判定与报错</div>
              </div>
              <span style="color:var(--text-secondary);">→</span>
            </div>
            <div class="settings-item" onclick="showToast('外部控制 API 端口: 127.0.0.1:9090 已就绪')">
              <div class="settings-main">
                <div class="settings-title">外部控制 RESTful API</div>
                <div class="settings-desc">监听 127.0.0.1:9090，支持 yacd / metacubexd</div>
              </div>
              <span style="font-size:12px; color:var(--accent-blue); font-weight:600;">9090 端口</span>
            </div>
            <div class="settings-item">
              <div class="settings-main">
                <div class="settings-title">关于 ClashMetaX</div>
                <div class="settings-desc">版本 2.11.35 · Mihomo Core 90a5afcc</div>
              </div>
              <span style="font-size:12px; color:var(--text-secondary);">v2.11</span>
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
          <div class="sheet-title">实时内核日志流</div>
          <button class="icon-btn" onclick="closeSheet()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="log-box" id="logBox"></div>
      </div>

      <!-- Active Connections Bottom Sheet -->
      <div class="bottom-sheet" id="connSheet" style="max-height:85%;">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
          <div class="sheet-title">活跃连接监控 (<span id="connCount">4</span>)</div>
          <button class="demo-btn" style="padding:4px 10px; font-size:11px;" onclick="clearAllConnections()">全部断开</button>
        </div>
        <div class="sheet-content" id="connListContainer"></div>
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
            <circle cx="12" cy="12" r="10"/>
            <line x1="2" y1="12" x2="22" y2="12"/>
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

  <script>
    const state = {
      theme: "dark",
      fullscreenPhone: false,
      running: true,
      mode: "Rule",
      uptimeSeconds: 5072,
      currentNode: {
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
        { id: "de01", group: "PROXY", flag: "🇩🇪", name: "德国 法兰克福 01 [欧洲节点]", type: "Vmess", ping: 210, ip: "188.114.97.10", geo: "Frankfurt" },
        { id: "kr01", group: "PROXY", flag: "🇰🇷", name: "韩国 首尔 01 [低延迟]", type: "Trojan", ping: 64, ip: "104.28.12.90", geo: "Seoul KINX" }
      ],
      apps: [
        { id: "tg", name: "Telegram", pkg: "org.telegram.messenger", icon: "✈️", checked: true },
        { id: "yt", name: "YouTube", pkg: "com.google.android.youtube", icon: "▶️", checked: true },
        { id: "tw", name: "X (Twitter)", pkg: "com.twitter.android", icon: "🐦", checked: true },
        { id: "chrome", name: "Google Chrome", pkg: "com.android.chrome", icon: "🌐", checked: true },
        { id: "netflix", name: "Netflix", pkg: "com.netflix.mediaclient", icon: "🎬", checked: true },
        { id: "spotify", name: "Spotify", pkg: "com.spotify.music", icon: "🎵", checked: false },
        { id: "wx", name: "微信", pkg: "com.tencent.mm", icon: "💬", checked: false },
        { id: "alipay", name: "支付宝", pkg: "com.eg.android.AlipayGphone", icon: "💳", checked: false },
        { id: "bili", name: "哔哩哔哩", pkg: "tv.danmaku.bili", icon: "📺", checked: false }
      ],
      connections: [
        { host: "api.openai.com:443", process: "ChatGPT", group: "🤖 AI 智能", node: "香港 BGP 01", speed: "128 KB/s" },
        { host: "rr3---sn-4g5ednks.googlevideo.com", process: "YouTube", group: "🎬 流媒体", node: "香港 BGP 02", speed: "3.2 MB/s" },
        { host: "api.telegram.org:443", process: "Telegram", group: "🚀 节点选择", node: "新加坡 01", speed: "14 KB/s" },
        { host: "gateway.discord.gg:443", process: "Discord", group: "🚀 节点选择", node: "日本 东京 01", speed: "42 KB/s" }
      ]
    };

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

    // Toggle Connect
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
      });
    });

    // Quick Node Switcher Bottom Sheet
    outboundCard.addEventListener("click", () => {
      openQuickNodeSwitcher();
    });

    function openQuickNodeSwitcher() {
      const sheet = document.getElementById("nodeSwitcherSheet");
      const backdrop = document.getElementById("sheetBackdrop");
      const container = document.getElementById("quickNodeList");
      
      container.innerHTML = state.nodes.map(n => `
        <div class="node-card ${n.id === state.currentNode.id ? "selected" : ""}" style="margin-bottom:8px;" onclick="selectNode('${n.id}')">
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

      backdrop.classList.add("show");
      sheet.classList.add("show");
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

    function closeSheet() {
      document.querySelectorAll(".bottom-sheet").forEach(s => s.classList.remove("show"));
      document.getElementById("sheetBackdrop").classList.remove("show");
    }

    // Tab Navigation
    function switchTab(tabId) {
      document.querySelectorAll(".page-view").forEach(p => p.classList.remove("active"));
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      
      const targetPage = document.getElementById("page-" + tabId);
      if (targetPage) targetPage.classList.add("active");

      const navIndices = { dashboard: 0, proxies: 1, profiles: 2, apps: 3, settings: 4 };
      const items = document.querySelectorAll(".nav-item");
      if (items[navIndices[tabId]]) {
        items[navIndices[tabId]].classList.add("active");
      }
      
      if (tabId === "proxies") renderProxies();
      if (tabId === "apps") renderApps();
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

    // Toggle Grid / List
    document.getElementById("btnToggleLayout").addEventListener("click", () => {
      state.isGrid = !state.isGrid;
      renderProxies();
      showToast(state.isGrid ? "已切换至网格卡片视图" : "已切换至紧凑列表视图");
    });

    // Concurrent URL Test
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

    // Apps Render
    function renderApps() {
      const container = document.getElementById("appListContainer");
      container.innerHTML = state.apps.map(app => `
        <div class="app-item-row" onclick="toggleApp('${app.id}')">
          <div class="app-info">
            <div class="app-icon">${app.icon}</div>
            <div>
              <div class="app-name">${app.name}</div>
              <div class="app-pkg">${app.pkg}</div>
            </div>
          </div>
          <div class="checkbox ${app.checked ? "checked" : ""}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </div>
        </div>
      `).join("");
      
      updateAppCount();
    }

    function toggleApp(id) {
      const app = state.apps.find(a => a.id === id);
      if (app) {
        app.checked = !app.checked;
        renderApps();
        showToast(`${app.name} 已${app.checked ? "加入分流代理名单" : "移出名单"}`);
      }
    }

    function updateAppCount() {
      const count = state.apps.filter(a => a.checked).length;
      document.getElementById("selectedAppCount").textContent = count;
    }

    function toggleSelectAllApps() {
      const allChecked = state.apps.every(a => a.checked);
      state.apps.forEach(a => a.checked = !allChecked);
      renderApps();
      showToast(allChecked ? "已取消所有已选应用" : "已全选所有应用");
    }

    // Profiles Operations
    function updateSingleProfile(e, profId) {
      e.stopPropagation();
      const btn = e.currentTarget;
      btn.style.transform = "rotate(360deg)";
      btn.style.transition = "transform 0.6s ease";
      showToast("正在拉取最新订阅规则与节点配置...");
      setTimeout(() => {
        btn.style.transform = "";
        showToast("订阅更新成功！节点列表已刷新");
      }, 700);
    }

    function activateProfile(profId) {
      document.querySelectorAll(".profile-card").forEach(c => c.classList.remove("active-profile"));
      const p = document.getElementById(profId);
      if (p) p.classList.add("active-profile");
      showToast("已切换生效配置档案");
    }

    document.getElementById("btnUpdateAllProfiles").addEventListener("click", () => {
      showToast("正在批量并发同步所有订阅配置...");
      setTimeout(() => {
        showToast("全部订阅已成功更新至最新版本！");
      }, 900);
    });

    document.getElementById("btnAddProfile").addEventListener("click", () => {
      showToast("支持：粘贴订阅链接 / 扫描二维码 / 导入本地 YAML 文件");
    });

    // Realtime Logs
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
      const sheet = document.getElementById("logSheet");
      const backdrop = document.getElementById("sheetBackdrop");
      backdrop.classList.add("show");
      sheet.classList.add("show");
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
    addLog("[DNS] Sniffing enabled for TLS SNI and HTTP Host header");
    addLog("[Rule] Matching api.openai.com -> Route to [🤖 AI 智能]");

    // Active Connections
    document.getElementById("btnActiveConn").addEventListener("click", () => {
      const sheet = document.getElementById("connSheet");
      const backdrop = document.getElementById("sheetBackdrop");
      const list = document.getElementById("connListContainer");
      
      list.innerHTML = state.connections.map(c => `
        <div class="card" style="margin-bottom:8px; padding:12px;">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <b style="font-size:13px;">${c.host}</b>
            <span style="font-size:11px; color:var(--accent-blue); font-weight:700;">${c.speed}</span>
          </div>
          <div style="font-size:11px; color:var(--text-secondary); margin-top:4px;">
            应用: ${c.process} · 策略: ${c.group} -> ${c.node}
          </div>
        </div>
      `).join("");

      backdrop.classList.add("show");
      sheet.classList.add("show");
    });

    function clearAllConnections() {
      state.connections = [];
      document.getElementById("connListContainer").innerHTML = `<div style="text-align:center; padding:30px; color:var(--text-secondary);">暂无活跃网络连接</div>`;
      document.getElementById("connCount").textContent = "0";
      showToast("已重置并切断全部活动套接字连接");
      closeSheet();
    }

    // Traffic Waveform Canvas Animation
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

      // Draw Download Wave (Blue)
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

      // Draw Upload Wave (Green)
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
    renderApps();
    drawWave();
  </script>
</body>
</html>
"""

with open("ui-web-demo/index.html", "w", encoding="utf-8") as f:
    f.write(html)
print("ui-web-demo/index.html written successfully, bytes:", len(html))
