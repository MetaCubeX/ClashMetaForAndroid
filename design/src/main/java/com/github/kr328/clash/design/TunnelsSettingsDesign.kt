package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.util.TunnelEntry
import com.github.kr328.clash.service.util.TunnelsConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Per-profile `tunnels:` editor. Each row is a static loopback forward routed
 * through a chosen proxy or group. The master toggle owns whether this profile
 * is managed (preserved on subscription refresh); OFF triggers clean teardown
 * in the activity.
 */
class TunnelsSettingsDesign(context: Context) : Design<TunnelsSettingsDesign.Request>(context) {
    sealed class Request {
        object Save : Request()
        object AddEntry : Request()
        data class MasterToggle(val on: Boolean) : Request()
    }

    private val rootView: View = context.layoutInflater.inflate(
        R.layout.design_tunnels_settings, context.root, false,
    )
    override val root: View get() = rootView

    private val scroll: NestedScrollView = rootView.findViewById(R.id.tunnels_scroll)
    private val profileName: TextView = rootView.findViewById(R.id.profile_name)
    private val noProfileNotice: TextView = rootView.findViewById(R.id.no_profile_notice)
    private val masterCard: View = rootView.findViewById(R.id.master_card)
    private val content: View = rootView.findViewById(R.id.content)
    private val masterToggle: MaterialSwitch = rootView.findViewById(R.id.master_toggle)
    private val recycler: RecyclerView = rootView.findViewById(R.id.entries_recycler)
    private val statusText: TextView = rootView.findViewById(R.id.status_text)
    private val btnSave: MaterialButton = rootView.findViewById(R.id.btn_save)
    private val btnAdd: MaterialButton = rootView.findViewById(R.id.btn_add)

    private val rows = mutableListOf<TunnelEntry>()
    private val adapter = TunnelEntryRowsAdapter(rows, emptyList()) { }

    init {
        rootView.findViewById<TextView>(R.id.screen_title).text =
            context.getString(R.string.tunnels_title)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPaddingRelative(bars.left, 0, bars.right, 0)
            scroll.setPadding(scroll.paddingLeft, bars.top, scroll.paddingRight, bars.bottom + 16)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        masterToggle.setOnClickListener {
            requests.trySend(Request.MasterToggle(masterToggle.isChecked))
        }
        btnSave.setOnClickListener { requests.trySend(Request.Save) }
        btnAdd.setOnClickListener { requests.trySend(Request.AddEntry) }
    }

    fun showNoProfile() {
        noProfileNotice.visibility = View.VISIBLE
        masterCard.visibility = View.GONE
        content.visibility = View.GONE
        profileName.visibility = View.GONE
    }

    fun bind(
        profile: String,
        managed: Boolean,
        config: TunnelsConfig,
        proxyOptions: List<String>,
    ) {
        noProfileNotice.visibility = View.GONE
        masterCard.visibility = View.VISIBLE
        content.visibility = View.VISIBLE
        profileName.visibility = View.VISIBLE
        profileName.text = profile

        masterToggle.isChecked = managed
        adapter.setProxyOptions(proxyOptions)
        adapter.setItems(config.entries)

        setContentEnabled(managed)
    }

    fun readModel(): TunnelsConfig = TunnelsConfig(entries = adapter.snapshot())

    fun addEntry() {
        adapter.addEmptyRow()
        scrollToLastEntry()
    }

    fun setContentEnabled(enabled: Boolean) {
        content.alpha = if (enabled) 1f else 0.5f
        setEnabledRecursive(content, enabled)
    }

    fun setSaveBusy(busy: Boolean) {
        btnSave.isEnabled = !busy
    }

    fun showStatus(message: String?, isError: Boolean) {
        if (message.isNullOrBlank()) {
            statusText.visibility = View.GONE
            return
        }
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(
                statusText,
                if (isError) com.google.android.material.R.attr.colorError
                else com.google.android.material.R.attr.colorPrimary,
            ),
        )
    }

    private fun scrollToLastEntry() {
        val n = adapter.itemCount
        if (n > 0) recycler.smoothScrollToPosition(n - 1)
    }

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) setEnabledRecursive(view.getChildAt(i), enabled)
        }
    }
}
