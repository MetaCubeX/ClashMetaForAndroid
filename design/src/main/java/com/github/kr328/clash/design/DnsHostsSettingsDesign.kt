package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.util.DnsHostsConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

/**
 * Per-profile DNS & Hosts editor. Lists (nameservers) and hosts are edited as
 * one-per-line text for v1 simplicity. The master toggle owns whether the
 * feature manages this profile; turning it off triggers a clean teardown
 * (handled by the activity).
 */
class DnsHostsSettingsDesign(context: Context) : Design<DnsHostsSettingsDesign.Request>(context) {
    sealed class Request {
        object Save : Request()
        data class MasterToggle(val on: Boolean) : Request()
    }

    private val rootView: View = context.layoutInflater.inflate(
        R.layout.design_dns_hosts_settings, context.root, false,
    )
    override val root: View get() = rootView

    private val scroll: NestedScrollView = rootView.findViewById(R.id.dns_hosts_scroll)
    private val profileName: TextView = rootView.findViewById(R.id.profile_name)
    private val noProfileNotice: TextView = rootView.findViewById(R.id.no_profile_notice)
    private val masterCard: View = rootView.findViewById(R.id.master_card)
    private val content: View = rootView.findViewById(R.id.content)
    private val masterToggle: MaterialSwitch = rootView.findViewById(R.id.master_toggle)
    private val dnsEnable: MaterialSwitch = rootView.findViewById(R.id.dns_enable)
    private val modeGroup: MaterialButtonToggleGroup = rootView.findViewById(R.id.mode_group)
    private val cacheGroup: MaterialButtonToggleGroup = rootView.findViewById(R.id.cache_group)
    private val listenInput: TextInputEditText = rootView.findViewById(R.id.listen_input)
    private val nsProxy: TextInputEditText = rootView.findViewById(R.id.ns_proxy)
    private val nsDirect: TextInputEditText = rootView.findViewById(R.id.ns_direct)
    private val nsProxyServer: TextInputEditText = rootView.findViewById(R.id.ns_proxy_server)
    private val nsBootstrap: TextInputEditText = rootView.findViewById(R.id.ns_bootstrap)
    private val hostsInput: TextInputEditText = rootView.findViewById(R.id.hosts_input)
    private val statusText: TextView = rootView.findViewById(R.id.status_text)
    private val btnSave: MaterialButton = rootView.findViewById(R.id.btn_save)

    init {
        rootView.findViewById<TextView>(R.id.screen_title).text =
            context.getString(R.string.dns_hosts_title)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPaddingRelative(bars.left, 0, bars.right, 0)
            scroll.setPadding(scroll.paddingLeft, bars.top, scroll.paddingRight, bars.bottom + 16)
            insets
        }
        rootView.post { ViewCompat.requestApplyInsets(rootView) }

        masterToggle.setOnClickListener {
            requests.trySend(Request.MasterToggle(masterToggle.isChecked))
        }
        btnSave.setOnClickListener { requests.trySend(Request.Save) }
    }

    /** No active profile: hide everything but the notice. */
    fun showNoProfile() {
        noProfileNotice.visibility = View.VISIBLE
        masterCard.visibility = View.GONE
        content.visibility = View.GONE
        profileName.visibility = View.GONE
    }

    fun bind(profile: String, managed: Boolean, config: DnsHostsConfig) {
        noProfileNotice.visibility = View.GONE
        masterCard.visibility = View.VISIBLE
        content.visibility = View.VISIBLE
        profileName.visibility = View.VISIBLE
        profileName.text = profile

        masterToggle.isChecked = managed
        dnsEnable.isChecked = config.enable == true
        checkMode(config.enhancedMode)
        checkCache(config.cacheAlgorithm)
        listenInput.setText(config.listen.orEmpty())
        nsProxy.setText(config.nameserver.joinToString("\n"))
        nsDirect.setText(config.directNameserver.joinToString("\n"))
        nsProxyServer.setText(config.proxyServerNameserver.joinToString("\n"))
        nsBootstrap.setText(config.defaultNameserver.joinToString("\n"))
        hostsInput.setText(config.hosts.entries.joinToString("\n") { "${it.key} = ${it.value}" })

        setContentEnabled(managed)
    }

    /** Reads the form into a model. */
    fun readModel(): DnsHostsConfig = DnsHostsConfig(
        enable = if (dnsEnable.isChecked) true else null,
        enhancedMode = modeValue(),
        listen = listenInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        cacheAlgorithm = cacheValue(),
        nameserver = lines(nsProxy),
        directNameserver = lines(nsDirect),
        proxyServerNameserver = lines(nsProxyServer),
        defaultNameserver = lines(nsBootstrap),
        hosts = parseHosts(hostsInput),
    )

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

    private fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) setEnabledRecursive(view.getChildAt(i), enabled)
        }
    }

    private fun lines(input: TextInputEditText): List<String> =
        input.text?.toString().orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseHosts(input: TextInputEditText): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (raw in input.text?.toString().orEmpty().split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val sep = line.indexOfFirst { it == '=' || it == ':' }
            val (k, v) = if (sep >= 0) {
                line.substring(0, sep) to line.substring(sep + 1)
            } else {
                val ws = line.indexOfFirst { it == ' ' || it == '\t' }
                if (ws < 0) continue else line.substring(0, ws) to line.substring(ws + 1)
            }
            val key = k.trim()
            val value = v.trim()
            if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
        }
        return out
    }

    private fun modeValue(): String? = when (modeGroup.checkedButtonId) {
        R.id.mode_off -> "normal"
        R.id.mode_redir -> "redir-host"
        R.id.mode_fakeip -> "fake-ip"
        else -> null
    }

    private fun checkMode(value: String?) {
        when (value) {
            "normal" -> modeGroup.check(R.id.mode_off)
            "redir-host" -> modeGroup.check(R.id.mode_redir)
            "fake-ip" -> modeGroup.check(R.id.mode_fakeip)
            else -> modeGroup.clearChecked()
        }
    }

    private fun cacheValue(): String? = when (cacheGroup.checkedButtonId) {
        R.id.cache_lru -> "lru"
        R.id.cache_arc -> "arc"
        else -> null
    }

    private fun checkCache(value: String?) {
        when (value) {
            "lru" -> cacheGroup.check(R.id.cache_lru)
            "arc" -> cacheGroup.check(R.id.cache_arc)
            else -> cacheGroup.check(R.id.cache_default)
        }
    }
}
