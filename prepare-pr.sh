#!/bin/bash
# PR 准备脚本

echo "🔧 准备 PR 分支..."

# 1. 创建新的功能分支
git checkout -b feature/broadcast-receiver-automation

# 2. 只添加核心功能文件
git add app/src/main/java/com/github/kr328/clash/ExternalControlReceiver.kt
git add app/src/main/AndroidManifest.xml
git add TASKER_GUIDE.md

# 3. 提交
git commit -m "feat: Add BroadcastReceiver for background automation control

Add ExternalControlReceiver for Tasker/automation tools to control
Clash service completely in background without triggering any UI.

This solves the issue where ExternalControlActivity causes screen
flash/popup on some ROMs (Flyme, MIUI, ColorOS, etc.) when triggered
by automation tools.

Features:
- Works on all ROMs without triggering UI
- No screen flash or popup
- Same actions as ExternalControlActivity (START/STOP/TOGGLE)
- Complete Tasker configuration guide included

Usage in Tasker:
- Action: com.github.metacubex.clash.meta.action.START_CLASH
- Target: Broadcast Receiver
- Package: com.github.metacubex.clash.meta

Files changed:
- app/src/main/java/.../ExternalControlReceiver.kt (new)
- app/src/main/AndroidManifest.xml (register receiver)
- TASKER_GUIDE.md (user documentation)
"

echo "✅ 提交完成！"
echo ""
echo "📋 查看提交内容："
git show --stat

echo ""
echo "🚀 推送到远程（如果需要）："
echo "git push origin feature/broadcast-receiver-automation"
