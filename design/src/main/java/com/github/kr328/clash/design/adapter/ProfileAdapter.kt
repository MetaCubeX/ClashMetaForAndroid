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
import com.github.kr328.clash.design.databinding.BottomSheetProxyGroupsBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
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
    private val cachedOfflinePreviewByProfile = mutableMapOf<UUID, Map<String, List<String>>>()
    private val cachedOfflineSelectionsByProfile = mutableMapOf<UUID, Map<String, String>>()
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
        if (offlinePreviewByProfile.isNotEmpty()) {
            cachedOfflinePreviewByProfile.putAll(offlinePreviewByProfile)
        }
        if (offlineSelectionsByProfile.isNotEmpty()) {
            cachedOfflineSelectionsByProfile.putAll(offlineSelectionsByProfile)
        }
        this.offlinePreviewByProfile = cachedOfflinePreviewByProfile.toMap()
        this.offlineSelectionsByProfile = cachedOfflineSelectionsByProfile.toMap()
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
        var changed = false
        for ((name, ms) in results) {
            val key = "${uuid}|$name"
            if (standalonePingDelays[key] != ms) {
                standalonePingDelays[key] = ms
                changed = true
            }
        }
        if (!changed) return
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

    fun hasProxyGroupsFor(profile: Profile): Boolean =
        effectiveGroupsForProfile(profile).isNotEmpty()

    private fun groupsForSelectionSummary(profile: Profile): List<String> {
        if (!profile.imported) return emptyList()
        return if (useEngineFor(profile)) {
            proxyGroupNames
        } else {
            offlinePreviewByProfile[profile.uuid]?.keys?.toList().orEmpty()
        }
    }

    private fun selectedGroupForSummary(profile: Profile): String? {
        val groups = groupsForSelectionSummary(profile)
        if (groups.isEmpty()) return null
        val preferred = lastGroupHint?.takeIf { groups.contains(it) }
        var index = selectedGroupIndex[profile.uuid]
            ?: preferred?.let { groups.indexOf(it).takeIf { i -> i >= 0 } }
            ?: 0
        if (index >= groups.size) index = 0
        selectedGroupIndex[profile.uuid] = index
        return groups[index]
    }

    private fun formatSelectionSummaryForHome(groupName: String): String = displayGroupName(groupName)

    private fun formatSelectionSummaryForProfiles(
        context: Context,
        profile: Profile,
        groupName: String,
    ): String {
        val groupDisplay = displayGroupName(groupName)
        val selectedProxy = proxyGroupForRow(profile, groupName)
            ?.now
            ?.takeIf { it.isNotBlank() }
            ?.let(::displayGroupName)
        return if (selectedProxy.isNullOrBlank()) {
            context.getString(R.string.main_selected_group_fmt, groupDisplay)
        } else {
            context.getString(R.string.main_selected_route_fmt, groupDisplay, selectedProxy)
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
        holder.binding.profileCard.strokeWidth = context.dp(1)
        chip.visibility = View.GONE
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
        view.visibility = View.GONE
        view.setOnClickListener(null)
    }

    fun showProxySheet(context: Context, profile: Profile) {
        if (!profile.imported) return

        val sheet = BottomSheetProxyGroupsBinding.inflate(context.layoutInflater)
        val dialog = AppBottomSheetDialog(context)
        val groupNames = effectiveGroupsForProfile(profile)
        sheet.proxySheetTitle.text = context.getString(R.string.profile_expand_proxies)
        sheet.proxySheetSubtitle.text = profile.name

        if (groupNames.isEmpty()) {
            sheet.proxySheetGroupChips.visibility = View.GONE
            sheet.proxySheetGroupTypeLabel.visibility = View.GONE
            sheet.proxySheetPingSlot.visibility = View.GONE
            addEmptyProxyHint(sheet.proxySheetNodesList, context)
        } else {
            val preferred = lastGroupHint?.takeIf { groupNames.contains(it) }
            var idx = selectedGroupIndex[profile.uuid]
                ?: preferred?.let { groupNames.indexOf(it).takeIf { i -> i >= 0 } }
                ?: 0
            if (idx >= groupNames.size) idx = 0
            selectedGroupIndex[profile.uuid] = idx

            fun render(selectedIndex: Int) {
                selectedGroupIndex[profile.uuid] = selectedIndex
                renderGroupChipsInto(sheet.proxySheetGroupChips, profile, groupNames, selectedIndex) { index, group ->
                    render(index)
                    reportVisibleGroup(profile, group, force = true)
                }
                bindGroupType(sheet.proxySheetGroupTypeLabel, profile, groupNames.getOrNull(selectedIndex).orEmpty())
                fillProxyRowsInto(sheet.proxySheetNodesList, profile, groupNames, selectedIndex)
            }

            val refreshRunnable = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    val currentIndex = selectedGroupIndex[profile.uuid] ?: 0
                    render(currentIndex.coerceIn(0, groupNames.lastIndex))
                    val pingingNow = states.pingingUuid == profile.uuid
                    sheet.proxySheetPingProgress.visibility = if (pingingNow) View.VISIBLE else View.GONE
                    sheet.proxySheetPingButton.visibility = if (pingingNow) View.INVISIBLE else View.VISIBLE
                    if (pingingNow) {
                        sheet.root.postDelayed(this, 260L)
                    }
                }
            }

            val pinging = states.pingingUuid == profile.uuid
            sheet.proxySheetPingProgress.visibility = if (pinging) View.VISIBLE else View.GONE
            sheet.proxySheetPingButton.visibility = if (pinging) View.INVISIBLE else View.VISIBLE
            sheet.proxySheetPingButton.setOnClickListener {
                val currentIndex = selectedGroupIndex[profile.uuid] ?: 0
                val groupName = groupNames.getOrNull(currentIndex) ?: return@setOnClickListener
                val pg = proxyGroupForRow(profile, groupName) ?: return@setOnClickListener
                onPingAll(profile, groupName, pg.proxies.map { it.name })
                sheet.root.post(refreshRunnable)
            }

            render(idx)
            reportVisibleGroup(profile, groupNames[idx])

            sheet.root.post(refreshRunnable)
            dialog.setOnDismissListener {
                sheet.root.removeCallbacks(refreshRunnable)
            }
        }

        dialog.setContentView(sheet.root)
        dialog.show()
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
        val compactHomeCard = expandOnProfileClick
        binding.rootView.minimumHeight = if (compactHomeCard) context.dp(68) else context.dp(78)
        binding.menuView.visibility = if (compactHomeCard) View.GONE else View.VISIBLE
        binding.menuView.isClickable = !compactHomeCard
        binding.menuView.isFocusable = !compactHomeCard
        val hasSupport = !announcementSupportUrl.isNullOrBlank() && current.imported && current.uuid == activeProfileUuid
        binding.supportSlot.visibility = if (!compactHomeCard && hasSupport) View.VISIBLE else View.GONE
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
        if (compactHomeCard) {
            binding.usageSummary.visibility = View.GONE
            binding.usageProgress.visibility = View.GONE
            binding.announcementInline.visibility = View.GONE
        }

        val groupNames = effectiveGroupsForProfile(current)
        val expanded = current.uuid in expandedUuids && current.imported

        binding.pingSlot.visibility = View.GONE
        val showServerChooser = !compactHomeCard && current.imported
        binding.chevronSlot.visibility = if (showServerChooser) View.VISIBLE else View.GONE
        binding.chevronView.visibility = if (showServerChooser) View.VISIBLE else View.GONE
        binding.chevronView.rotation = -90f
        binding.chevronSlot.isClickable = showServerChooser
        binding.chevronSlot.setOnClickListener {
            if (showServerChooser) onExpandToggle(current)
        }
        binding.serverButton.setOnClickListener {
            if (showServerChooser) onExpandToggle(current)
        }
        if (compactHomeCard) {
            val profileTitle = resolveCurrentNodeDisplayName(current)
                ?: context.getString(R.string.not_selected)
            val (titleText, emoji) = formatNodeHeadline(profileTitle, context)
            binding.profileTitle.text = titleText
            val iconEmoji = emoji ?: extractLeadingEmoji(profileTitle)
            binding.profileIconEmoji.visibility = if (iconEmoji != null) View.VISIBLE else View.GONE
            binding.profileIcon.visibility = if (iconEmoji != null) View.GONE else View.VISIBLE
            if (iconEmoji != null) {
                binding.profileIconEmoji.text = iconEmoji
            }
        } else {
            binding.profileTitle.text = current.name
            binding.profileIconEmoji.visibility = View.GONE
            binding.profileIcon.visibility = View.VISIBLE
        }

        val selectedGroup = selectedGroupForSummary(current)
        val selectionSummary = selectedGroup?.let { group ->
            if (compactHomeCard) {
                formatSelectionSummaryForHome(group)
            } else {
                formatSelectionSummaryForProfiles(context, current, group)
            }
        }
        binding.serverSelectionSummary.visibility =
            if (!selectionSummary.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.serverSelectionSummary.text = selectionSummary.orEmpty()
        if (useEngineFor(current) && selectedGroup != null) {
            reportVisibleGroup(current, selectedGroup)
        }

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

        val showForceUpdate = !compactHomeCard && current.imported && current.type != Profile.Type.File
        binding.forceUpdateSlot.visibility = if (showForceUpdate) View.VISIBLE else View.GONE
        binding.forceUpdateView.visibility = if (showForceUpdate) View.VISIBLE else View.INVISIBLE
        binding.forceUpdateView.isClickable = false
        binding.forceUpdateView.isFocusable = false
        binding.forceUpdateSlot.isClickable = showForceUpdate
        binding.forceUpdateSlot.setOnClickListener {
            if (showForceUpdate) onForceUpdate(current)
        }

        binding.chevronView.isClickable = false

        binding.proxyExpandPanel.visibility = View.GONE
        if (!expanded) return

        if (groupNames.isEmpty()) {
            binding.proxyGroupChips.visibility = View.GONE
            binding.proxyGroupTypeLabel.visibility = View.GONE
            if (binding.proxyNodesList.childCount == 0) {
                addEmptyProxyHint(binding.proxyNodesList, context)
            }
            return
        }
        binding.proxyGroupChips.visibility = View.VISIBLE

        val preferred = lastGroupHint?.takeIf { groupNames.contains(it) }
        var idx = selectedGroupIndex[current.uuid]
            ?: preferred?.let { groupNames.indexOf(it).takeIf { i -> i >= 0 } }
            ?: 0
        if (idx >= groupNames.size) idx = 0
        selectedGroupIndex[current.uuid] = idx

        bindGroupType(binding.proxyGroupTypeLabel, current, groupNames.getOrNull(idx).orEmpty())

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
        renderGroupChipsInto(holder.binding.proxyGroupChips, profile, groupNames, selectedIndex) { index, groupName ->
            if (holder.ignoreGroupChip) return@renderGroupChipsInto
            selectedGroupIndex[profile.uuid] = index
            fillProxyRows(holder, profile, groupNames, index)
            reportVisibleGroup(profile, groupName, force = true)
        }
    }

    private fun renderGroupChipsInto(
        container: ViewGroup,
        profile: Profile,
        groupNames: List<String>,
        selectedIndex: Int,
        onSelected: (Int, String) -> Unit,
    ) {
        val context = container.context
        container.removeAllViews()
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
                    onSelected(index, groupName)
                }
            }
            container.addView(chip)
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
        fillProxyRowsInto(holder.binding.proxyNodesList, profile, groupNames, groupIndex)
    }

    private fun fillProxyRowsInto(
        list: ViewGroup,
        profile: Profile,
        groupNames: List<String>,
        groupIndex: Int,
    ) {
        list.removeAllViews()

        val groupName = groupNames.getOrNull(groupIndex) ?: return
        val pg = proxyGroupForRow(profile, groupName) ?: return
        val inflater = list.context.layoutInflater
        val context = list.context

        if (pg.proxies.isEmpty()) {
            addEmptyProxyHint(list, context)
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

    private fun bindGroupType(view: TextView, profile: Profile, groupName: String) {
        val groupType = proxyGroupForRow(profile, groupName)?.type
        if (groupType != null && groupType.group) {
            view.visibility = View.VISIBLE
            view.text = groupType.name
        } else {
            view.visibility = View.GONE
        }
    }

    private fun addEmptyProxyHint(list: ViewGroup, context: Context) {
        val tv = TextView(context).apply {
            text = context.getString(R.string.proxy_nodes_empty_connect_vpn)
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            setTextColor(ContextCompat.getColor(context, R.color.delay_timeout))
        }
        list.addView(tv)
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

    private fun resolveCurrentNodeDisplayName(profile: Profile): String? {
        val groups = groupsForSelectionSummary(profile)
        if (groups.isEmpty()) return null

        val preferred = selectedGroupForSummary(profile)
        val orderedGroups = buildList {
            preferred?.let { add(it) }
            addAll(groups.filterNot { it == preferred })
        }

        for (group in orderedGroups) {
            val selected = proxyGroupForRow(profile, group)
                ?.now
                ?.takeIf { it.isNotBlank() }
                ?.let(::displayGroupName)
            if (!selected.isNullOrBlank()) return selected
        }

        val offlineSelected = offlineSelectionsByProfile[profile.uuid]
            ?.values
            ?.firstOrNull { it.isNotBlank() }
            ?.let(::displayGroupName)
        if (!offlineSelected.isNullOrBlank()) return offlineSelected

        return null
    }

    private fun formatNodeHeadline(raw: String, context: Context): Pair<String, String?> {
        val trimmed = raw.trim()
        val leadingEmoji = extractLeadingEmoji(trimmed)
        val stripped = if (leadingEmoji != null) {
            trimmed.removePrefix(leadingEmoji).trimStart(' ', '|', '-', '_', '.', ':')
        } else {
            trimmed
        }
        return stripped to leadingEmoji
    }

    private fun extractLeadingEmoji(raw: String): String? {
        val input = raw.trimStart()
        if (input.isEmpty()) return null

        val firstCp = input.codePointAt(0)
        val firstLen = Character.charCount(firstCp)

        // Handle country flags encoded as two regional-indicator symbols.
        if (isRegionalIndicator(firstCp) && input.length >= firstLen + 2) {
            val secondCp = input.codePointAt(firstLen)
            if (isRegionalIndicator(secondCp)) {
                return String(Character.toChars(firstCp)) + String(Character.toChars(secondCp))
            }
        }

        if (!isEmojiLike(firstCp)) return null
        val base = String(Character.toChars(firstCp))
        val remaining = input.substring(firstLen)
        val variant = if (remaining.startsWith("\uFE0F")) "\uFE0F" else ""
        return base + variant
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    private fun isEmojiLike(codePoint: Int): Boolean =
        codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF

    override fun getItemCount(): Int = profiles.size
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
