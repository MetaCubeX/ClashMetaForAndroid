package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.widget.NestedScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ProxyChainDesign(
    context: Context,
) : Design<ProxyChainDesign.Request>(context) {
    sealed class Request {
        /** Write the chain only — no tunnel-mode / selection change. */
        object SaveChain : Request()
        /** Write the chain, switch tunnel to Global, select the proxy in GLOBAL (stated to the user). */
        object UseNow : Request()
        /** Show the pending YAML change (optional). */
        object Preview : Request()
        object Clear : Request()
        object ClearAllDiskChains : Request()
        object ClearSelectedDiskChain : Request()
    }

    private data class DiskRow(
        val targetYamlName: String,
        val dialer: String,
        val file: String,
    )

    private val rootView: View = context.layoutInflater.inflate(
        R.layout.design_proxy_chain,
        context.root,
        false,
    )

    private val scroll: NestedScrollView = rootView.findViewById(R.id.proxy_chain_scroll)
    private val diskChainsDetail: TextView = rootView.findViewById(R.id.disk_chains_detail)
    private val diskChainSpinner: AutoCompleteTextView = rootView.findViewById(R.id.disk_chain_spinner)
    private val runtimeOfflineNotice: TextView = rootView.findViewById(R.id.runtime_offline_notice)
    private val runtimeProxySection: View = rootView.findViewById(R.id.runtime_proxy_section)
    private val outboundProxySpinner: AutoCompleteTextView = rootView.findViewById(R.id.outbound_proxy_spinner)
    private val dialerProxySpinner: AutoCompleteTextView = rootView.findViewById(R.id.dialer_proxy_spinner)
    private val chainStatusCard: MaterialCardView = rootView.findViewById(R.id.chain_status_card)
    private val chainStatusText: TextView = rootView.findViewById(R.id.chain_status_text)
    private val btnUseNow: MaterialButton = rootView.findViewById(R.id.btn_use_now)
    private val btnSaveChain: MaterialButton = rootView.findViewById(R.id.btn_save_chain)
    private val useNowLabel: String = rootView.context.getString(R.string.proxy_chain_use_now)

    private var diskRows: List<DiskRow> = emptyList()
    private var allProxies: List<String> = emptyList()

    override val root: View
        get() = rootView

    init {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPaddingRelative(bars.left, 0, bars.right, 0)
            scroll.setPadding(scroll.paddingLeft, bars.top, scroll.paddingRight, bars.bottom + 16)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        btnUseNow.setOnClickListener { requests.trySend(Request.UseNow) }
        btnSaveChain.setOnClickListener { requests.trySend(Request.SaveChain) }
        rootView.findViewById<MaterialButton>(R.id.btn_preview).setOnClickListener {
            requests.trySend(Request.Preview)
        }
        rootView.findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener {
            requests.trySend(Request.Clear)
        }
        rootView.findViewById<MaterialButton>(R.id.btn_clear_all_disk_chains).setOnClickListener {
            requests.trySend(Request.ClearAllDiskChains)
        }
        rootView.findViewById<MaterialButton>(R.id.btn_clear_selected_disk_chain).setOnClickListener {
            requests.trySend(Request.ClearSelectedDiskChain)
        }
    }

    /**
     * @param rows target YAML name, dialer value, relative file path
     */
    fun bindDiskChains(rows: List<Triple<String, String, String>>) {
        diskRows = rows.map { (t, d, f) -> DiskRow(t, d, f) }
        val ctx = rootView.context
        if (diskRows.isEmpty()) {
            diskChainsDetail.text = ctx.getString(R.string.proxy_chain_saved_empty)
            diskChainSpinner.setAdapter(
                ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, emptyList<String>()))
            diskChainSpinner.isEnabled = false
            rootView.findViewById<MaterialButton>(R.id.btn_clear_selected_disk_chain).isEnabled = false
            rootView.findViewById<MaterialButton>(R.id.btn_clear_all_disk_chains).isEnabled = false
            return
        }
        val detail = diskRows.joinToString("\n") { r ->
            "• ${r.targetYamlName} → ${r.dialer}\n  (${r.file})"
        }
        diskChainsDetail.text = detail
        val labels = diskRows.map { r -> "${r.targetYamlName} → ${r.dialer}" }
        diskChainSpinner.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, labels))
        diskChainSpinner.isEnabled = true
        rootView.findViewById<MaterialButton>(R.id.btn_clear_selected_disk_chain).isEnabled = true
        rootView.findViewById<MaterialButton>(R.id.btn_clear_all_disk_chains).isEnabled = true
    }

    fun selectedDiskChainTarget(): String? {
        if (diskRows.isEmpty()) return null
        val ix = (diskChainSpinner.adapter as ArrayAdapter<String>).getPosition(diskChainSpinner.text.toString())
        if (ix < 0 || ix >= diskRows.size) return null
        return diskRows[ix].targetYamlName
    }

    /**
     * Bind both searchable proxy fields to one flat, de-duplicated list across all runtime groups —
     * the user picks proxies by name, not by group. Empty groups ⇒ offline notice.
     */
    fun bindRuntime(
        groups: List<String>,
        proxiesByGroup: Map<String, List<String>>,
    ) {
        val ctx = rootView.context
        if (groups.isEmpty()) {
            allProxies = emptyList()
            runtimeOfflineNotice.visibility = View.VISIBLE
            runtimeProxySection.visibility = View.GONE
            return
        }
        allProxies = proxiesByGroup.values.flatten().distinct().sorted()
        runtimeOfflineNotice.visibility = View.GONE
        runtimeProxySection.visibility = View.VISIBLE

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, allProxies)
        outboundProxySpinner.setAdapter(adapter)
        dialerProxySpinner.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, allProxies))
    }

    /** Top field — the FIRST hop (entry / intermediate). Becomes the `dialer-proxy` value. */
    fun selectedFirstHopName(): String? =
        outboundProxySpinner.text?.toString()?.trim()?.takeIf { it in allProxies }

    /** Bottom field — the EXIT (visible IP). The `dialer-proxy` is written onto this proxy. */
    fun selectedExitName(): String? =
        dialerProxySpinner.text?.toString()?.trim()?.takeIf { it in allProxies }

    enum class ChainStatusKind {
        Progress,
        Success,
        Warning,
        Error,
    }

    /** Shows last action outcome; persists until the next action. */
    fun showChainStatus(kind: ChainStatusKind, message: String) {
        chainStatusCard.visibility = View.VISIBLE
        chainStatusText.text = message
        val attr = when (kind) {
            ChainStatusKind.Progress ->
                com.google.android.material.R.attr.colorOnSurfaceVariant
            ChainStatusKind.Success ->
                com.google.android.material.R.attr.colorPrimary
            ChainStatusKind.Warning ->
                com.google.android.material.R.attr.colorSecondary
            ChainStatusKind.Error ->
                com.google.android.material.R.attr.colorError
        }
        chainStatusText.setTextColor(resolveChainStatusColor(attr))
    }

    /** [MaterialColors] throws if the theme omits an attr (e.g. colorSecondary on some OEM themes). */
    private fun resolveChainStatusColor(attr: Int): Int {
        return try {
            MaterialColors.getColor(chainStatusText, attr)
        } catch (_: IllegalArgumentException) {
            MaterialColors.getColor(
                chainStatusText,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            )
        }
    }

    fun setChainBusy(busy: Boolean) {
        btnUseNow.isEnabled = !busy
        btnSaveChain.isEnabled = !busy
        btnUseNow.text =
            if (busy) rootView.context.getString(R.string.proxy_chain_connecting) else useNowLabel
    }
}
