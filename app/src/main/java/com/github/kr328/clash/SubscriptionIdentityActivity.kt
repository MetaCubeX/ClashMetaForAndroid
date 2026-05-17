package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.github.kr328.clash.common.util.SubscriptionDeviceHeaders
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.SubscriptionIdentityDesign
import com.github.kr328.clash.design.ui.ToastDuration
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class SubscriptionIdentityActivity : BaseActivity<SubscriptionIdentityDesign>() {
    override suspend fun main() {
        val design = SubscriptionIdentityDesign(this)
        val requestHeaders = SubscriptionDeviceHeaders.headerMap(this)
        val hwid = requestHeaders[SubscriptionDeviceHeaders.HEADER_HWID].orEmpty()
        val schemes = buildSupportedSchemeText()
        val diagnostics = buildHwidDiagnosticsText(requestHeaders)

        design.setHwid(hwid)
        design.setSchemes(schemes)
        design.setHwidDiagnostics(diagnostics)
        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { request ->
                    when (request) {
                        SubscriptionIdentityDesign.Request.CopyHwid -> {
                            copyToClipboard("HWID", hwid)
                            design.showToast(R.string.copied, ToastDuration.Short)
                        }

                        SubscriptionIdentityDesign.Request.CopySchemes -> {
                            copyToClipboard("clashfest schemes", schemes)
                            design.showToast(R.string.copied, ToastDuration.Short)
                        }

                        SubscriptionIdentityDesign.Request.CopyHwidDiagnostics -> {
                            copyToClipboard("HWID diagnostics", diagnostics)
                            design.showToast(R.string.copied, ToastDuration.Short)
                        }

                        SubscriptionIdentityDesign.Request.OpenOperatorApiSpec -> {
                            runCatching {
                                startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(getString(R.string.operator_api_spec_url)),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
            ClipData.newPlainText(label, text),
        )
    }

    private fun buildSupportedSchemeText(): String {
        return "clashfest://installconfig?url=<encoded-url>"
    }

    private fun buildHwidDiagnosticsText(requestHeaders: Map<String, String>): String {
        fun parseBool(value: String): Boolean? = when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        val active = parseBool(uiStore.subscriptionHwidActive)
        val notSupported = parseBool(uiStore.subscriptionHwidNotSupported)
        val maxReached = parseBool(uiStore.subscriptionHwidMaxDevicesReached)
        val limit = parseBool(uiStore.subscriptionHwidLimit)

        val serverSummary = when {
            active == true && notSupported == true ->
                "Panel HWID: enabled, but x-hwid not accepted/detected."
            active == true && (maxReached == true || limit == true) ->
                "Panel HWID: device limit reached."
            active == true ->
                "Panel HWID: enabled and accepted."
            active == false ->
                "Panel HWID: disabled on panel side."
            else ->
                "Panel HWID: unknown (refresh subscription metadata once)."
        }

        return buildString {
            appendLine(serverSummary)
            appendLine()
            appendLine("Request headers sent by ClashFest:")
            appendLine("- x-hwid: ${requestHeaders["x-hwid"].orEmpty().ifBlank { "missing" }}")
            appendLine("- x-device-os: ${requestHeaders["x-device-os"].orEmpty().ifBlank { "missing" }}")
            appendLine("- x-ver-os: ${requestHeaders["x-ver-os"].orEmpty().ifBlank { "missing" }}")
            appendLine("- x-device-model: ${requestHeaders["x-device-model"].orEmpty().ifBlank { "missing" }}")
            appendLine("- x-app-version: ${requestHeaders["x-app-version"].orEmpty().ifBlank { "missing" }}")
            appendLine()
            appendLine("Last panel diagnostics headers:")
            appendLine("- x-hwid-active: ${uiStore.subscriptionHwidActive.ifBlank { "unknown" }}")
            appendLine("- x-hwid-not-supported: ${uiStore.subscriptionHwidNotSupported.ifBlank { "unknown" }}")
            appendLine("- x-hwid-max-devices-reached: ${uiStore.subscriptionHwidMaxDevicesReached.ifBlank { "unknown" }}")
            append("- x-hwid-limit: ${uiStore.subscriptionHwidLimit.ifBlank { "unknown" }}")
        }
    }
}
