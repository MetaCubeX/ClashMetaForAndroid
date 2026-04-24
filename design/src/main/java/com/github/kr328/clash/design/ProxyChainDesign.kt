package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ProxyChainDesign(
    context: Context,
) : Design<ProxyChainDesign.Request>(context) {
    sealed class Request {
        /** Save dialer-proxy to YAML, switch to Global, select outbound (desktop-style). */
        object Connect : Request()
        object Apply : Request()
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

    private val toolbar: MaterialToolbar = rootView.findViewById(R.id.toolbar)
    private val diskChainsDetail: TextView = rootView.findViewById(R.id.disk_chains_detail)
    private val diskChainSpinner: AutoCompleteTextView = rootView.findViewById(R.id.disk_chain_spinner)
    private val runtimeOfflineNotice: TextView = rootView.findViewById(R.id.runtime_offline_notice)
    private val runtimeProxySection: View = rootView.findViewById(R.id.runtime_proxy_section)
    val outboundGroupSpinner: AutoCompleteTextView = rootView.findViewById(R.id.outbound_group_spinner)
    val outboundProxySpinner: AutoCompleteTextView = rootView.findViewById(R.id.outbound_proxy_spinner)
    val dialerGroupSpinner: AutoCompleteTextView = rootView.findViewById(R.id.dialer_group_spinner)
    val dialerProxySpinner: AutoCompleteTextView = rootView.findViewById(R.id.dialer_proxy_spinner)
    private val chainStatusCard: MaterialCardView = rootView.findViewById(R.id.chain_status_card)
    private val chainStatusText: TextView = rootView.findViewById(R.id.chain_status_text)
    private val btnConnect: MaterialButton = rootView.findViewById(R.id.btn_connect)
    private val btnApply: MaterialButton = rootView.findViewById(R.id.btn_apply)
    private val connectLabel: String = rootView.context.getString(R.string.proxy_chain_connect)

    private var diskRows: List<DiskRow> = emptyList()
    private var runtimeGroups: List<String> = emptyList()

    override val root: View
        get() = rootView

    init {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }

        btnConnect.setOnClickListener {
            requests.trySend(Request.Connect)
        }
        btnApply.setOnClickListener {
            requests.trySend(Request.Apply)
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

    fun bindRuntime(
        groups: List<String>,
        proxiesByGroup: Map<String, List<String>>,
    ) {
        val ctx = rootView.context
        if (groups.isEmpty()) {
            runtimeGroups = emptyList()
            runtimeOfflineNotice.visibility = View.VISIBLE
            runtimeProxySection.visibility = View.GONE
            return
        }
        runtimeGroups = groups
        runtimeOfflineNotice.visibility = View.GONE
        runtimeProxySection.visibility = View.VISIBLE

        outboundGroupSpinner.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, groups))
        dialerGroupSpinner.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, groups))

        fun fillProxies(sp: AutoCompleteTextView, group: String) {
            val names = proxiesByGroup[group].orEmpty()
            sp.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
        }

        outboundGroupSpinner.setOnItemClickListener { _, _, position, _ ->
            val name = groups.getOrNull(position) ?: return@setOnItemClickListener
            fillProxies(outboundProxySpinner, name)
        }
        dialerGroupSpinner.setOnItemClickListener { _, _, position, _ ->
            val name = groups.getOrNull(position) ?: return@setOnItemClickListener
            fillProxies(dialerProxySpinner, name)
        }

        if (groups.isNotEmpty()) {
            fillProxies(outboundProxySpinner, groups[0])
            fillProxies(dialerProxySpinner, groups[0])
        }
    }

    fun selectedOutboundGroupName(): String? {
        if (runtimeGroups.isEmpty()) return null
        return outboundGroupSpinner.text?.toString()?.takeIf { it in runtimeGroups }
    }

    fun selectedOutboundProxyName(): String? {
        val adapter = outboundProxySpinner.adapter ?: return null
        return outboundProxySpinner.text?.toString()?.takeIf { (adapter as ArrayAdapter<String>).getPosition(it) >= 0 }
    }

    fun selectedDialerProxyName(): String? {
        val adapter = dialerProxySpinner.adapter ?: return null
        return dialerProxySpinner.text?.toString()?.takeIf { (adapter as ArrayAdapter<String>).getPosition(it) >= 0 }
    }

    enum class ChainStatusKind {
        Progress,
        Success,
        Warning,
        Error,
    }

    /** Shows last connect/save outcome; persists until the next action. */
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
        btnConnect.isEnabled = !busy
        btnApply.isEnabled = !busy
        btnConnect.text =
            if (busy) rootView.context.getString(R.string.proxy_chain_connecting) else connectLabel
    }
}
