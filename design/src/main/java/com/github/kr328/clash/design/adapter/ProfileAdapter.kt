package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.FlagParser
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
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
    private val onVisibleGroupChanged: (Profile, String) -> Unit = { _, _ -> },
    private val expandOnProfileClick: Boolean = false,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root) {
        var ignoreGroupChip: Boolean = false
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
    private val lastReportedVisibleGroup = mutableMapOf<UUID, String>()
    private val pendingProxySelections = mutableMapOf<String, String>()
    /** Per-node ms when core is off: key `uuid|proxyName`. */
    private val standalonePingDelays: MutableMap<String, Int> = mutableMapOf()

    /** Operator-pushed announcement, rendered inline on the active profile card. */
    private var announcementText: String? = null
    private var announcementUrl: String? = null
    private var announcementSupportUrl: String? = null
    private var announcementOnOpenUrl: ((String) -> Unit)? = null
    private var announcementOnSupport: (() -> Unit)? = null

    fun setActiveAnnouncement(
        text: String?,
        url: String?,
        supportUrl: String?,
        onOpenUrl: ((String) -> Unit)?,
        onSupport: (() -> Unit)?,
    ) {
        val raw = text?.takeIf { it.isNotBlank() }
        val decoded = raw?.let {
            com.github.kr328.clash.common.util.MaybeBase64.decode(it).takeIf { d -> d.isNotBlank() }
        }
        val changed =
            decoded != announcementText ||
                url != announcementUrl ||
                supportUrl != announcementSupportUrl
        announcementText = decoded
        announcementUrl = url?.takeIf { it.isNotBlank() }
        announcementSupportUrl = supportUrl?.takeIf { it.isNotBlank() }
        announcementOnOpenUrl = onOpenUrl
        announcementOnSupport = onSupport
        if (changed) {
            val active = activeProfileUuid ?: return
            val i = profiles.indexOfFirst { it.uuid == active }
            if (i >= 0) notifyItemChanged(i)
        }
    }

    fun updateElapsed() {
        notifyDataSetChanged()
    }

    fun setPingingUuid(uuid: UUID?) {
        if (states.pingingUuid == uuid) return
        states.pingingUuid = uuid
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: Holder) {
        holder.binding.activeStatusChip.alpha = 1f
        holder.binding.pingProgress.visibility = View.GONE
        holder.binding.pingGroupProgress.visibility = View.GONE
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
        val runtimeChanged =
            names != proxyGroupNames ||
                running != clashRunning ||
                activeProfileUuid != this.activeProfileUuid
        if (runtimeChanged) {
            lastReportedVisibleGroup.clear()
            proxyDetails = emptyMap()
            pendingProxySelections.clear()
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
        if (details.isEmpty()) return
        val merged = proxyDetails.toMutableMap().apply {
            putAll(details)
        }
        val active = activeProfileUuid
        val hasPendingForDetails = active != null &&
            details.keys.any { group -> proxySelectionKey(active, group) in pendingProxySelections }
        if (merged == proxyDetails && !hasPendingForDetails) return
        proxyDetails = merged
        active?.let { uuid ->
            for (group in details.keys) {
                pendingProxySelections.remove(proxySelectionKey(uuid, group))
            }
        }
        active ?: return
        val i = profiles.indexOfFirst { it.uuid == active }
        if (i >= 0) {
            notifyItemChanged(i)
        }
    }

    fun clearProxyDetails() {
        if (proxyDetails.isEmpty()) return
        proxyDetails = emptyMap()
        activeProfileUuid?.let { uuid ->
            val i = profiles.indexOfFirst { it.uuid == uuid }
            if (i >= 0) notifyItemChanged(i)
        } ?: notifyDataSetChanged()
    }

    fun setPendingProxySelection(uuid: UUID, groupName: String, proxyName: String) {
        if (groupName.isBlank() || proxyName.isBlank()) return
        val key = proxySelectionKey(uuid, groupName)
        if (pendingProxySelections[key] == proxyName) return
        pendingProxySelections[key] = proxyName
        val i = profiles.indexOfFirst { it.uuid == uuid }
        if (i >= 0) notifyItemChanged(i)
    }

    fun clearStandalonePingDelays(uuid: UUID) {
        val prefix = "${uuid}|"
        if (standalonePingDelays.keys.none { it.startsWith(prefix) }) return
        standalonePingDelays.keys.removeAll { it.startsWith(prefix) }
        val i = profiles.indexOfFirst { it.uuid == uuid }
        if (i >= 0) notifyItemChanged(i)
    }

    fun setStandalonePingResults(uuid: UUID, results: Map<String, Int>) {
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
        if (useEngineFor(profile)) {
            proxyDetails[groupName]?.let { return it.withPendingSelection(profile.uuid, groupName) }
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
        ).withPendingSelection(profile.uuid, groupName)
    }

    private fun proxySelectionKey(uuid: UUID, groupName: String): String =
        "${uuid}|${groupName}"

    private fun ProxyGroup.withPendingSelection(uuid: UUID, groupName: String): ProxyGroup {
        val pending = pendingProxySelections[proxySelectionKey(uuid, groupName)] ?: return this
        if (pending == now || proxies.none { it.name == pending }) return this
        return copy(now = pending)
    }

    private fun applyActiveVisuals(holder: Holder, profile: Profile) {
        val chip = holder.binding.activeStatusChip
        val context = chip.context
        holder.binding.profileCard.strokeWidth = 0
        if (profile.active) {
            chip.text = context.getString(R.string.profile_active_status)
            chip.setBackgroundResource(R.drawable.bg_m3_status_chip)
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnPrimaryContainer))
        } else {
            chip.text = context.getString(R.string.profile_inactive_status)
            chip.setBackgroundResource(R.drawable.bg_m3_status_chip_neutral)
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
    }

    private fun bindAnnouncement(holder: Holder, profile: Profile) {
        val view = holder.binding.announcementInline
        val text = announcementText
        val showHere = !text.isNullOrBlank() &&
            profile.imported &&
            profile.uuid == activeProfileUuid
        if (!showHere) {
            view.visibility = View.GONE
            view.setOnClickListener(null)
            return
        }
        view.visibility = View.VISIBLE
        view.text = text
        val openTarget = announcementUrl ?: announcementSupportUrl
        if (openTarget != null) {
            view.setOnClickListener {
                announcementOnOpenUrl?.invoke(openTarget)
                    ?: announcementOnSupport?.invoke()
            }
        } else {
            view.setOnClickListener(null)
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
            if (expandOnProfileClick && current.imported) {
                onExpandToggle(current)
            } else {
                onClicked(current)
            }
        }
        binding.menuView.setOnClickListener { v ->
            onMenuClicked(current, v)
        }
        val hasSupport = !announcementSupportUrl.isNullOrBlank() && current.imported && current.uuid == activeProfileUuid
        binding.supportSlot.visibility = if (hasSupport) View.VISIBLE else View.GONE
        binding.supportView.setOnClickListener {
            announcementSupportUrl?.let { url ->
                announcementOnOpenUrl?.invoke(url) ?: announcementOnSupport?.invoke()
            }
        }
        binding.activateButton.text = context.getString(R.string.profile_use)
        binding.activateButton.visibility = if (current.active) View.GONE else View.VISIBLE
        binding.activateButton.isEnabled = true
        binding.activateButton.setOnClickListener {
            if (!current.active) onClicked(current)
        }

        applyActiveVisuals(holder, current)
        bindUsageAndProgress(holder, current)
        bindAnnouncement(holder, current)

        val groupNames = effectiveGroupsForProfile(current)
        val expanded = current.uuid in expandedUuids && current.imported

        val showChevron = current.imported
        val reserveActionStrip = showChevron
        binding.forceUpdateSlot.visibility = if (reserveActionStrip) View.VISIBLE else View.GONE
        binding.pingSlot.visibility = View.GONE
        binding.chevronSlot.visibility = View.GONE
        binding.chevronView.rotation = if (expanded) 180f else 0f

        val showPing = expanded && current.imported
        binding.pingGroupSlot.visibility = if (showPing) View.VISIBLE else View.GONE
        val pinging = states.pingingUuid == current.uuid && showPing
        if (pinging) {
            binding.pingGroupProgress.visibility = View.VISIBLE
            binding.pingGroupView.visibility = View.INVISIBLE
        } else {
            binding.pingGroupProgress.visibility = View.GONE
            binding.pingGroupView.visibility = if (showPing) View.VISIBLE else View.INVISIBLE
        }
        binding.pingGroupView.isClickable = false
        binding.pingGroupView.isFocusable = false
        binding.pingGroupSlot.isClickable = showPing
        binding.pingGroupSlot.setOnClickListener {
            if (!showPing) return@setOnClickListener
            val ix = selectedGroupIndex[current.uuid] ?: 0
            val groupName = groupNames.getOrNull(ix) ?: return@setOnClickListener
            val pg = proxyGroupForRow(current, groupName) ?: return@setOnClickListener
            onPingAll(current, groupName, pg.proxies.map { it.name })
        }

        val showForceUpdate = current.imported && current.type != Profile.Type.File
        binding.forceUpdateView.visibility = if (showForceUpdate) View.VISIBLE else View.INVISIBLE
        binding.forceUpdateView.isClickable = false
        binding.forceUpdateView.isFocusable = false
        binding.forceUpdateSlot.isClickable = reserveActionStrip
        binding.forceUpdateSlot.setOnClickListener {
            if (showForceUpdate) onForceUpdate(current)
        }

        binding.chevronView.visibility = View.GONE
        if (showChevron) {
            binding.chevronView.isClickable = false
            binding.chevronView.setOnClickListener(null)
        } else {
            binding.chevronView.setOnClickListener(null)
            binding.chevronView.isClickable = false
        }

        binding.proxyExpandPanel.visibility = if (expanded) View.VISIBLE else View.GONE

        if (!expanded) {
            return
        }

        if (groupNames.isEmpty()) {
            binding.proxyGroupChips.visibility = View.GONE
            binding.proxyGroupTypeLabel.visibility = View.GONE
            binding.proxyNodesList.removeAllViews()
            return
        }
        binding.proxyGroupChips.visibility = View.VISIBLE

        val preferred = lastGroupHint?.takeIf { groupNames.contains(it) }
        var idx = selectedGroupIndex[current.uuid]
            ?: preferred?.let { groupNames.indexOf(it).takeIf { i -> i >= 0 } }
            ?: 0
        if (idx >= groupNames.size) idx = 0
        selectedGroupIndex[current.uuid] = idx

        val groupType = proxyGroupForRow(current, groupNames.getOrNull(idx) ?: "")?.type
        if (groupType != null && groupType.group) {
            binding.proxyGroupTypeLabel.visibility = View.VISIBLE
            binding.proxyGroupTypeLabel.text = groupType.name
        } else {
            binding.proxyGroupTypeLabel.visibility = View.GONE
        }

        holder.ignoreGroupChip = true
        renderGroupChips(holder, current, groupNames, idx)
        fillProxyRows(holder, current, groupNames, idx)
        reportVisibleGroup(current, groupNames[idx])
        binding.proxyGroupChips.post {
            holder.ignoreGroupChip = false
        }
    }

    private fun renderGroupChips(
        holder: Holder,
        profile: Profile,
        groupNames: List<String>,
        selectedIndex: Int,
    ) {
        val binding = holder.binding
        val context = binding.root.context
        binding.proxyGroupChips.removeAllViews()
        groupNames.forEachIndexed { index, groupName ->
            val chip = Chip(context).apply {
                text = displayGroupName(groupName)
                isCheckable = true
                isChecked = index == selectedIndex
                setEnsureMinTouchTargetSize(false)
                minHeight = context.dp(30)
                maxWidth = context.dp(200)
                chipMinHeight = context.dp(30).toFloat()
                chipStartPadding = context.dp(9).toFloat()
                chipEndPadding = context.dp(9).toFloat()
                textStartPadding = 0f
                textEndPadding = 0f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextAppearance(R.style.TextAppearance_App_LabelMedium)
                setOnClickListener {
                    if (holder.ignoreGroupChip) return@setOnClickListener
                    selectedGroupIndex[profile.uuid] = index
                    fillProxyRows(holder, profile, groupNames, index)
                    reportVisibleGroup(profile, groupName, force = true)
                }
            }
            binding.proxyGroupChips.addView(chip)
        }
    }

    private fun reportVisibleGroup(profile: Profile, groupName: String, force: Boolean = false) {
        if (!useEngineFor(profile)) return
        if (!force && lastReportedVisibleGroup[profile.uuid] == groupName) return
        lastReportedVisibleGroup[profile.uuid] = groupName
        onVisibleGroupChanged(profile, groupName)
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
        val context = binding.root.context

        if (pg.proxies.isEmpty()) {
            val tv = TextView(context).apply {
                text = context.getString(R.string.proxy_nodes_empty_connect_vpn)
                val pad = (8 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            list.addView(tv)
            return
        }

        for (p in pg.proxies) {
            val row = inflater.inflate(R.layout.adapter_home_proxy_node, list, false)

            val title = p.title.ifBlank { p.name }
            val flag = FlagParser.parse(title)
            row.findViewById<TextView>(R.id.proxy_title).text = flag?.let {
                title.removePrefix(it.emoji)
                    .trimStart(' ', '|', '-', '_', '.', ':')
                    .ifBlank { title }
            } ?: title
            val flagCard = row.findViewById<View>(R.id.proxy_flag_card)
            val flagText = row.findViewById<TextView>(R.id.proxy_flag)
            if (flag != null) {
                flagCard.visibility = View.VISIBLE
                flagText.text = flag.emoji
            } else {
                flagCard.visibility = View.GONE
            }

            val typeBadge = row.findViewById<TextView>(R.id.proxy_type_badge)
            val typeName = p.type.name
            val showBadge = typeName != "Unknown" && !p.type.group
            if (showBadge) {
                typeBadge.visibility = View.VISIBLE
                typeBadge.text = typeName
            } else {
                typeBadge.visibility = View.GONE
            }

            val subtitle = p.subtitle
                .takeIf { it.isNotBlank() && !it.equals(typeName, ignoreCase = true) }
            row.findViewById<TextView>(R.id.proxy_subtitle).apply {
                visibility = if (subtitle == null) View.GONE else View.VISIBLE
                text = subtitle.orEmpty()
            }

            val key = "${profile.uuid}|${p.name}"
            val standalone = standalonePingDelays[key]
            val nested = nestedGroupDelay(profile.uuid, p.name)
            val delayMs = when {
                p.delay >= 0 -> p.delay
                nested >= 0 -> nested
                standalone != null -> standalone
                else -> p.delay
            }

            val delayView = row.findViewById<TextView>(R.id.proxy_delay)
            delayView.text = formatDelay(delayMs)
            applyDelayStyle(delayView, delayMs, context)

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

    private fun nestedGroupDelay(uuid: UUID, proxyName: String): Int {
        val seen = linkedSetOf<String>()

        fun resolve(groupName: String): Int {
            if (!seen.add(groupName)) return -1

            proxyDetails[groupName]?.let { group ->
                val selectedDelay = group.proxies
                    .firstOrNull { it.name == group.now && it.delay >= 0 }
                    ?.delay
                if (selectedDelay != null) return selectedDelay

                return group.proxies.asSequence()
                    .map { proxy ->
                        when {
                            proxy.delay >= 0 -> proxy.delay
                            proxy.name in proxyDetails -> resolve(proxy.name)
                            else -> standalonePingDelays["$uuid|${proxy.name}"] ?: -1
                        }
                    }
                    .filter { it >= 0 }
                    .minOrNull() ?: -1
            }

            val offline = offlinePreviewByProfile[uuid]?.get(groupName) ?: return -1
            return offline.asSequence()
                .map { name ->
                    val direct = standalonePingDelays["$uuid|$name"]
                    direct ?: resolve(name)
                }
                .filter { it >= 0 }
                .minOrNull() ?: -1
        }

        return resolve(proxyName)
    }

    private fun applyDelayStyle(view: TextView, delayMs: Int, context: android.content.Context) {
        when {
            delayMs in 0..200 -> {
                view.setTextColor(ContextCompat.getColor(context, R.color.delay_good))
                view.setBackgroundResource(R.drawable.bg_delay_good)
            }
            delayMs in 201..500 -> {
                view.setTextColor(ContextCompat.getColor(context, R.color.delay_medium))
                view.setBackgroundResource(R.drawable.bg_delay_medium)
            }
            delayMs in 501..Short.MAX_VALUE -> {
                view.setTextColor(ContextCompat.getColor(context, R.color.delay_bad))
                view.setBackgroundResource(R.drawable.bg_delay_bad)
            }
            else -> {
                view.setTextColor(ContextCompat.getColor(context, R.color.delay_timeout))
                view.setBackgroundResource(R.drawable.bg_delay_timeout)
            }
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

    private fun displayGroupName(groupName: String): String =
        groupName.trim().replace(Regex("\\s+"), " ")

    override fun getItemCount(): Int = profiles.size
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
