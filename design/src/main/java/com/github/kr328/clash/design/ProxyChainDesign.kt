package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ProxyChainDesign(
    context: Context,
) : Design<ProxyChainDesign.Request>(context) {
    sealed class Request {
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
    private val diskChainSpinner: Spinner = rootView.findViewById(R.id.disk_chain_spinner)
    private val runtimeOfflineNotice: TextView = rootView.findViewById(R.id.runtime_offline_notice)
    private val runtimeProxySection: View = rootView.findViewById(R.id.runtime_proxy_section)
    val outboundGroupSpinner: Spinner = rootView.findViewById(R.id.outbound_group_spinner)
    val outboundProxySpinner: Spinner = rootView.findViewById(R.id.outbound_proxy_spinner)
    val dialerGroupSpinner: Spinner = rootView.findViewById(R.id.dialer_group_spinner)
    val dialerProxySpinner: Spinner = rootView.findViewById(R.id.dialer_proxy_spinner)

    private var diskRows: List<DiskRow> = emptyList()

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

        rootView.findViewById<MaterialButton>(R.id.btn_apply).setOnClickListener {
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
            diskChainSpinner.adapter =
                ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
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
        diskChainSpinner.adapter =
            ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels)
        diskChainSpinner.isEnabled = true
        rootView.findViewById<MaterialButton>(R.id.btn_clear_selected_disk_chain).isEnabled = true
        rootView.findViewById<MaterialButton>(R.id.btn_clear_all_disk_chains).isEnabled = true
    }

    fun selectedDiskChainTarget(): String? {
        if (diskRows.isEmpty()) return null
        val ix = diskChainSpinner.selectedItemPosition
        if (ix < 0 || ix >= diskRows.size) return null
        return diskRows[ix].targetYamlName
    }

    fun bindRuntime(
        groups: List<String>,
        proxiesByGroup: Map<String, List<String>>,
    ) {
        val ctx = rootView.context
        if (groups.isEmpty()) {
            runtimeOfflineNotice.visibility = View.VISIBLE
            runtimeProxySection.visibility = View.GONE
            return
        }
        runtimeOfflineNotice.visibility = View.GONE
        runtimeProxySection.visibility = View.VISIBLE

        val gAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, groups)
        outboundGroupSpinner.adapter = gAdapter
        dialerGroupSpinner.adapter =
            ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, groups)

        fun fillProxies(sp: Spinner, group: String) {
            val names = proxiesByGroup[group].orEmpty()
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, names)
        }

        outboundGroupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val name = groups.getOrNull(position) ?: return
                fillProxies(outboundProxySpinner, name)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        dialerGroupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val name = groups.getOrNull(position) ?: return
                fillProxies(dialerProxySpinner, name)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        if (groups.isNotEmpty()) {
            fillProxies(outboundProxySpinner, groups[0])
            fillProxies(dialerProxySpinner, groups[0])
        }
    }

    fun selectedOutboundProxyName(): String? {
        val adapter = outboundProxySpinner.adapter ?: return null
        val ix = outboundProxySpinner.selectedItemPosition
        if (ix < 0 || ix >= adapter.count) return null
        return adapter.getItem(ix)?.toString()
    }

    fun selectedDialerProxyName(): String? {
        val adapter = dialerProxySpinner.adapter ?: return null
        val ix = dialerProxySpinner.selectedItemPosition
        if (ix < 0 || ix >= adapter.count) return null
        return adapter.getItem(ix)?.toString()
    }
}
