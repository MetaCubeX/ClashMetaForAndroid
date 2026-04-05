package com.github.kr328.clash.design.adapter

import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

class ProfileAdapter(
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile, View) -> Unit,
    private val onExpandToggle: (Profile) -> Unit = {},
    private val onProxyNodeSelected: (Profile, String, String) -> Unit = { _, _, _ -> },
    private val onPingAll: (Profile, String, List<String>) -> Unit = { _, _, _ -> },
    private val onForceUpdate: (Profile) -> Unit = {},
    private val onProxyYamlDetail: (profile: Profile, groupName: String, proxyName: String) -> Unit =
        { _, _, _ -> },
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root) {
        var ignoreSpinner: Boolean = false
        var activePulse: ObjectAnimator? = null
    }

    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()

    private var proxyGroupNames: List<String> = emptyList()
    private var proxyDetails: Map<String, ProxyGroup> = emptyMap()
    private var activeProfileUuid: UUID? = null
    private var clashRunning: Boolean = false
    private var tunnelMode: TunnelState.Mode? = null
    private var lastGroupHint: String? = null
    private var expandedUuids: Set<UUID> = emptySet()
    /** Offline proxy groups per profile (expanded cards that are not using live engine data). */
    private var offlinePreviewByProfile: Map<UUID, Map<String, List<String>>> = emptyMap()
    private var offlineSelectionsByProfile: Map<UUID, Map<String, String>> = emptyMap()
    private val selectedGroupIndex = mutableMapOf<UUID, Int>()
    /** Per-node ms when core is off: key `uuid|proxyName`. */
    private val standalonePingDelays: MutableMap<String, Int> = mutableMapOf()

    fun updateElapsed() {
        notifyDataSetChanged()
    }

    fun setPingingUuid(uuid: UUID?) {
        if (states.pingingUuid == uuid) return
        states.pingingUuid = uuid
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: Holder) {
        holder.activePulse?.cancel()
        holder.activePulse = null
        holder.binding.activeStrip.alpha = 1f
        holder.binding.pingProgress.visibility = View.GONE
        super.onViewRecycled(holder)
    }

    fun setProxyContext(
        names: List<String>,
        running: Boolean,
        mode: TunnelState.Mode?,
        lastGroupHint: String?,
        offlinePreviewByProfile: Map<UUID, Map<String, List<String>>> = emptyMap(),
        activeProfileUuid: UUID? = null,
        offlineSelectionsByProfile: Map<UUID, Map<String, String>> = emptyMap(),
    ) {
        if (names == proxyGroupNames && running == clashRunning && mode == tunnelMode &&
            lastGroupHint == this.lastGroupHint &&
            offlinePreviewByProfile == this.offlinePreviewByProfile &&
            activeProfileUuid == this.activeProfileUuid &&
            offlineSelectionsByProfile == this.offlineSelectionsByProfile
        ) {
            return
        }
        proxyGroupNames = names
        this.offlinePreviewByProfile = offlinePreviewByProfile
        this.offlineSelectionsByProfile = offlineSelectionsByProfile
        this.activeProfileUuid = activeProfileUuid
        clashRunning = running
        tunnelMode = mode
        this.lastGroupHint = lastGroupHint
        notifyDataSetChanged()
    }

    fun setProxyDetails(details: Map<String, ProxyGroup>) {
        if (details == proxyDetails) return
        proxyDetails = details
        notifyDataSetChanged()
    }

    fun clearStandalonePingDelays(uuid: UUID) {
        val prefix = "${uuid}|"
        if (standalonePingDelays.keys.none { it.startsWith(prefix) }) return
        standalonePingDelays.keys.removeAll { it.startsWith(prefix) }
        val i = profiles.indexOfFirst { it.uuid == uuid }
        if (i >= 0) notifyItemChanged(i)
    }

    fun setStandalonePingResults(uuid: UUID, results: Map<String, Int>) {
        val prefix = "${uuid}|"
        standalonePingDelays.keys.removeAll { it.startsWith(prefix) }
        for ((name, ms) in results) {
            standalonePingDelays["${uuid}|$name"] = ms
        }
        val i = profiles.indexOfFirst { it.uuid == uuid }
        if (i >= 0) notifyItemChanged(i)
    }

    fun setExpandedUuids(uuids: Set<UUID>) {
        if (expandedUuids == uuids) return
        val old = expandedUuids
        expandedUuids = uuids
        for (i in profiles.indices) {
            val id = profiles[i].uuid
            if ((id in old) != (id in uuids)) {
                notifyItemChanged(i)
            }
        }
    }

    /** Engine data applies only when the expanded card is the active profile and VPN is on. */
    private fun useEngineFor(profile: Profile): Boolean =
        clashRunning &&
            profile.active &&
            profile.uuid == activeProfileUuid &&
            proxyGroupNames.isNotEmpty()

    private fun effectiveGroupsForProfile(profile: Profile): List<String> {
        if (profile.uuid !in expandedUuids || !profile.imported) {
            return emptyList()
        }
        return if (useEngineFor(profile)) {
            proxyGroupNames
        } else {
            offlinePreviewByProfile[profile.uuid]?.keys?.toList().orEmpty()
        }
    }

    private fun proxyGroupForRow(profile: Profile, groupName: String): ProxyGroup? {
        if (useEngineFor(profile) && proxyDetails.isNotEmpty()) {
            return proxyDetails[groupName]
        }
        val offline = offlinePreviewByProfile[profile.uuid] ?: return null
        val names = offline[groupName] ?: return null
        val now = offlineSelectionsByProfile[profile.uuid]?.get(groupName).orEmpty()
        return ProxyGroup(
            Proxy.Type.Selector,
            names.map { n ->
                Proxy(n, n, "", Proxy.Type.Unknown, -1)
            },
            now,
        )
    }

    private fun applyActiveVisuals(holder: Holder, profile: Profile) {
        holder.activePulse?.cancel()
        holder.activePulse = null
        val strip = holder.binding.activeStrip
        holder.binding.profileCard.strokeWidth = 0
        if (profile.active) {
            strip.alpha = 1f
            holder.activePulse =
                ObjectAnimator.ofFloat(strip, View.ALPHA, 0.38f, 1f).apply {
                    duration = 1000L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
        } else {
            strip.alpha = 1f
        }
    }

    private fun bindUsageAndProgress(holder: Holder, profile: Profile) {
        val binding = holder.binding
        val used = profile.upload + profile.download
        val showTraffic =
            profile.imported &&
                !profile.pending &&
                (used > 0L || profile.total >= 2L)
        if (showTraffic) {
            binding.usageSummary.visibility = View.VISIBLE
            binding.usageSummary.text = formatUsageLine(profile)
        } else {
            binding.usageSummary.visibility = View.GONE
        }
        if (profile.imported && !profile.pending && profile.total >= 2L) {
            binding.usageProgress.visibility = View.VISIBLE
            binding.usageProgress.max = 1000
            val total = profile.total.coerceAtLeast(1L)
            val frac = (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            binding.usageProgress.progress = (frac * 1000).toInt()
        } else {
            binding.usageProgress.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterProfileBinding.inflate(parent.context.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = profiles[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.profile = current
        binding.setClicked {
            onClicked(current)
        }
        binding.menuView.setOnClickListener { v ->
            onMenuClicked(current, v)
        }

        applyActiveVisuals(holder, current)
        bindUsageAndProgress(holder, current)

        val groupNames = effectiveGroupsForProfile(current)
        val expanded = current.uuid in expandedUuids && current.imported

        val showChevron = current.imported
        // Keep three fixed slots for imported profiles so expand/collapse does not move the chevron.
        val reserveActionStrip = showChevron
        binding.forceUpdateSlot.visibility = if (reserveActionStrip) View.VISIBLE else View.GONE
        binding.pingSlot.visibility = if (reserveActionStrip) View.VISIBLE else View.GONE
        binding.chevronSlot.visibility = if (reserveActionStrip) View.VISIBLE else View.GONE
        binding.chevronView.rotation = if (expanded) 180f else 0f

        val showPing = expanded && current.imported
        val pinging = states.pingingUuid == current.uuid && showPing
        if (pinging) {
            binding.pingProgress.visibility = View.VISIBLE
            binding.pingAllView.visibility = View.INVISIBLE
        } else {
            binding.pingProgress.visibility = View.GONE
            binding.pingAllView.visibility = if (showPing) View.VISIBLE else View.INVISIBLE
        }
        binding.pingAllView.isClickable = false
        binding.pingAllView.isFocusable = false
        binding.pingSlot.isClickable = reserveActionStrip
        binding.pingSlot.setOnClickListener {
            if (!showPing) return@setOnClickListener
            val ix = selectedGroupIndex[current.uuid] ?: 0
            val groupName = groupNames.getOrNull(ix) ?: return@setOnClickListener
            val pg = proxyGroupForRow(current, groupName) ?: return@setOnClickListener
            onPingAll(current, groupName, pg.proxies.map { it.name })
        }

        val showForceUpdate = expanded && current.type != Profile.Type.File
        binding.forceUpdateView.visibility = if (showForceUpdate) View.VISIBLE else View.INVISIBLE
        binding.forceUpdateView.isClickable = false
        binding.forceUpdateView.isFocusable = false
        binding.forceUpdateSlot.isClickable = reserveActionStrip
        binding.forceUpdateSlot.setOnClickListener {
            if (showForceUpdate) onForceUpdate(current)
        }

        binding.chevronView.visibility = View.VISIBLE
        if (showChevron) {
            binding.chevronView.isClickable = true
            binding.chevronView.setOnClickListener {
                onExpandToggle(current)
            }
        } else {
            binding.chevronView.setOnClickListener(null)
            binding.chevronView.isClickable = false
        }

        binding.proxyExpandPanel.visibility = if (expanded) View.VISIBLE else View.GONE

        if (!expanded) {
            return
        }

        if (groupNames.isEmpty()) {
            binding.proxyGroupSpinner.visibility = View.GONE
            binding.proxyGroupSpinner.onItemSelectedListener = null
            binding.proxyNodesList.removeAllViews()
            return
        }
        binding.proxyGroupSpinner.visibility = View.VISIBLE

        val spinAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            groupNames,
        )
        binding.proxyGroupSpinner.adapter = spinAdapter

        val preferred = lastGroupHint?.takeIf { groupNames.contains(it) }
        var idx = selectedGroupIndex[current.uuid]
            ?: preferred?.let { groupNames.indexOf(it).takeIf { i -> i >= 0 } }
            ?: 0
        if (idx >= groupNames.size) idx = 0

        holder.ignoreSpinner = true
        binding.proxyGroupSpinner.setSelection(idx)
        fillProxyRows(holder, current, groupNames, idx)
        binding.proxyGroupSpinner.post {
            holder.ignoreSpinner = false
        }

        binding.proxyGroupSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    pos: Int,
                    id: Long,
                ) {
                    if (holder.ignoreSpinner) return
                    selectedGroupIndex[current.uuid] = pos
                    fillProxyRows(holder, current, groupNames, pos)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun fillProxyRows(
        holder: Holder,
        profile: Profile,
        groupNames: List<String>,
        groupIndex: Int,
    ) {
        val binding = holder.binding
        val list = binding.proxyNodesList
        list.removeAllViews()

        val groupName = groupNames.getOrNull(groupIndex) ?: return
        val pg = proxyGroupForRow(profile, groupName) ?: return
        val inflater = binding.root.context.layoutInflater

        for (p in pg.proxies) {
            val row = inflater.inflate(R.layout.adapter_home_proxy_node, list, false)
            row.findViewById<TextView>(R.id.proxy_title).text = p.title.ifBlank { p.name }
            row.findViewById<TextView>(R.id.proxy_subtitle).text =
                p.subtitle.ifBlank { p.type.name }
            val key = "${profile.uuid}|${p.name}"
            val standalone = standalonePingDelays[key]
            val delayMs = when {
                p.delay >= 0 -> p.delay
                standalone != null -> standalone
                else -> p.delay
            }
            row.findViewById<TextView>(R.id.proxy_delay).text = formatDelay(delayMs)
            val selected = p.name.isNotEmpty() && p.name == pg.now
            row.findViewById<View>(R.id.selected_bar).visibility =
                if (selected) View.VISIBLE else View.INVISIBLE
            val mainHit = row.findViewById<View>(R.id.proxy_row_main_hit)
            mainHit.setOnClickListener {
                if (clashRunning && useEngineFor(profile)) {
                    onProxyNodeSelected(profile, groupName, p.name)
                } else if (profile.imported && profile.uuid == activeProfileUuid) {
                    onProxyNodeSelected(profile, groupName, p.name)
                } else {
                    onProxyYamlDetail(profile, groupName, p.name)
                }
            }
            mainHit.setOnLongClickListener {
                onProxyYamlDetail(profile, groupName, p.name)
                true
            }
            list.addView(row)
        }
    }

    private fun formatUsageLine(p: Profile): String {
        val used = (p.download + p.upload).toBytesString()
        return if (p.total < 2) {
            "$used / ∞"
        } else {
            "$used / ${p.total.toBytesString()}"
        }
    }

    private fun formatDelay(delayMs: Int): String =
        when {
            delayMs in 0..Short.MAX_VALUE -> "${delayMs}ms"
            else -> "—"
        }

    override fun getItemCount(): Int = profiles.size
}
