package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.design.McpSettingsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import universe.agentic.device.clash.McpService
import universe.agentic.device.clash.newMcpToken

class McpSettingsActivity : BaseActivity<McpSettingsDesign>() {
    override suspend fun main() {
        val design = McpSettingsDesign(this)
        setContentDesign(design)
        while (isActive) {
            when (val request = design.requests.receive()) {
                is McpSettingsDesign.Request.Enabled -> setEnabled(design, request.on)
                McpSettingsDesign.Request.CopyEndpoint -> copy(design, design.endpoint())
                McpSettingsDesign.Request.CopyToken -> copy(design, design.token())
            }
        }
    }

    private suspend fun setEnabled(design: McpSettingsDesign, on: Boolean) {
        val store = ServiceStore(this)
        if (on && store.mcpToken.isBlank()) store.mcpToken = newMcpToken()
        store.mcpEnabled = on
        if (on) {
            startForegroundServiceCompat(Intent(this, McpService::class.java))
        } else {
            stopService(Intent(this, McpService::class.java))
        }
        delay(400)
        design.refresh()
    }

    private suspend fun copy(design: McpSettingsDesign, text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mcp", text))
        design.showToast(com.github.kr328.clash.design.R.string.copied, ToastDuration.Short)
    }
}
