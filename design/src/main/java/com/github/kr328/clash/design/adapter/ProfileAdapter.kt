package com.github.kr328.clash.design.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.FlagDrawableLoader
import com.github.kr328.clash.design.util.FlagParser
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.databinding.BottomSheetProxyGroupsBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.common.branding.BrandManifest
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import com.github.kr328.clash.service.model.ProxyTransportInfo
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
    private val showServerChooserInCard: Boolean = true,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root)

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
    private var offlinePreviewByProfile: Map<UUID, Map<String, ProxyGroupPreviewRow>> = emptyMap()
    private var offlineSelectionsByProfile: Map<UUID, Map<String, String>> = emptyMap()
    private var transportInfoByProfile: Map<UUID, Map<String, ProxyTransportInfo>> = emptyMap()
    private val cachedOfflinePreviewByProfile = mutableMapOf<UUID, Map<String, ProxyGroupPreviewRow>>()
    private val cachedOfflineSelectionsByProfile = mutableMapOf<UUID, Map<String, String>>()
    private val selectedGroupIndex = mutableMapOf<UUID, Int>()
    private val lastReportedVisibleGroup = mutableMapOf<UUID, String>()
    private val pendingProxySelections = mutableMapOf<String, String>()
    /** Per-node ms when core is off: key `uuid|proxyName`. */
    private val standalonePingDelays: MutableMap<String, Int> = mutableMapOf()
    /** Last time ping-all was triggered per profile (System.currentTimeMillis()). */
    private val lastPingAllAt: MutableMap<UUID, Long> = mutableMapOf()

    /** Operator-supplied brand manifest, used by v2+ surfaces (Renew tap, max-devices chip). */
    private var brandManifest: BrandManifest = BrandManifest.EMPTY
    private var onOpenBrandUrl: ((String) -> Unit)? = null

    /** Operator-pushed announcement, rendered inline on the active profile card. */
    private var announcementText: String? = null
    private var announcementUrl: String? = null
    private var announcementSupportUrl: String? = null
    private var announcementOnOpenUrl: ((String) -> Unit)? = null
    private var announcementOnSupport: (() -> Unit)? = null
    private val profileEmojiPool = listOf(
        "🐱", "🐶", "🦊", "🐼", "🐻", "🐨", "🐯", "🦁",
        "🐸", "🐵", "🐙", "🦄", "🐧", "🐺", "🐹", "🐰",
    )

    private enum class ProxyPickerSort {
        Config,
        Delay,
        Name,
    }

    private sealed class ProxyPickerFilter {
        object All : ProxyPickerFilter()
        object CurrentGroup : ProxyPickerFilter()
        object Selected : ProxyPickerFilter()
        object Available : ProxyPickerFilter()
        data class Provider(val name: String) : ProxyPickerFilter()
    }

    private data class ProxyPickerRow(
        val groupName: String,
        val groupIndex: Int,
        val proxy: Proxy,
        val configIndex: Int,
        val delayMs: Int,
        val selected: Boolean,
        val provider: String?,
    ) {
        val available: Boolean
            get() = delayMs >= 0
    }

    /**
     * Push the latest operator brand into the adapter so v2 surfaces
     * (critical-expiry chip Renew tap, max-devices chip, etc.) can react.
     */
    fun setBrandManifest(manifest: BrandManifest, onOpenBrandUrl: (String) -> Unit) {
        val changed = manifest != brandManifest
        brandManifest = manifest
        this.onOpenBrandUrl = onOpenBrandUrl
        if (changed) notifyDataSetChanged()
    }

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

    fun moveProfile(from: Int, to: Int): Boolean {
        if (from !in profiles.indices || to !in profiles.indices || from == to) return false
        profiles = profiles.toMutableList().apply {
            add(to, removeAt(from))
        }
        notifyItemMoved(from, to)
        return true
    }

    fun setPingingUuid(uuid: UUID?) {
        val prev = states.pingingUuid
        if (prev == uuid) return
        states.pingingUuid = uuid
        // Targeted notify instead of full-list rebind: ping spinners only affect at most
        // two cards (the one that was pinging and the one that just started).
        prev?.let { id ->
            val i = profiles.indexOfFirst { it.uuid == id }
            if (i >= 0) notifyItemChanged(i)
        }
        uuid?.let { id ->
            val i = profiles.indexOfFirst { it.uuid == id }
            if (i >= 0) notifyItemChanged(i)
        }
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
        offlinePreviewByProfile: Map<UUID, Map<String, ProxyGroupPreviewRow>> = emptyMap(),
        activeProfileUuid: UUID? = null,
        offlineSelectionsByProfile: Map<UUID, Map<String, String>> = emptyMap(),
        transportInfoByProfile: Map<UUID, Map<String, ProxyTransportInfo>> = emptyMap(),
    ) {
        if (names == proxyGroupNames && running == clashRunning && mode == tunnelMode &&
            lastGroupHint == this.lastGroupHint &&
            offlinePreviewByProfile == this.offlinePreviewByProfile &&
            activeProfileUuid == this.activeProfileUuid &&
            offlineSelectionsByProfile == this.offlineSelectionsByProfile &&
            transportInfoByProfile == this.transportInfoByProfile
        ) {
            return
        }
        this.transportInfoByProfile = transportInfoByProfile
        // Only reset runtime proxies when identity actually changes. The core may return the
        // same selector list in a different order after patchSelector; treating that like a
        // full reset cleared pending selections and made the home card look like the tap lost.
        val groupSetChanged = names.toSet() != proxyGroupNames.toSet()
        val identityChanged =
            activeProfileUuid != this.activeProfileUuid || groupSetChanged
        val runningChanged = running != clashRunning
        val shouldResetRuntimeProxyState = runningChanged || identityChanged
        if (shouldResetRuntimeProxyState) {
            lastReportedVisibleGroup.clear()
            proxyDetails = emptyMap()
            // Keep pending across VPN off→on: engine `now` is wrong until applyPostLoad
            // finishes; pending matches the user's choice and avoids a first-frame flash.
            if (identityChanged) {
                pendingProxySelections.clear()
            }
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
        val hasFuzzyPending = active != null &&
            details.keys.any { group -> pendingMapValueForGroup(active, group) != null }
        val needsDiskOverlay = active != null && details.keys.any { group ->
            val d = merged[group] ?: return@any false
            val disk = offlineSelectedForGroup(active, group)
            disk.isNotBlank() && disk != d.now &&
                (d.proxies.isEmpty() || d.proxies.any { it.name == disk })
        }
        if (merged == proxyDetails && !hasFuzzyPending && !needsDiskOverlay) return
        proxyDetails = merged
        active?.let { uuid ->
            for ((group, detail) in details) {
                val prefix = "${uuid}|"
                val removeKeys = mutableListOf<String>()
                for (key in pendingProxySelections.keys) {
                    if (!key.startsWith(prefix)) continue
                    val g = key.substring(prefix.length)
                    if (!groupsMatchKey(group, g)) continue
                    val pending = pendingProxySelections[key] ?: continue
                    val pendingApplied = detail.now == pending
                    val pendingInvalid = detail.proxies.isNotEmpty() &&
                        detail.proxies.none { it.name == pending }
                    if (pendingApplied || pendingInvalid) {
                        removeKeys.add(key)
                    }
                }
                for (k in removeKeys) {
                    pendingProxySelections.remove(k)
                }
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
        val alreadyPending = pendingProxySelections[key] == proxyName
        pendingProxySelections[key] = proxyName
        // Also mirror the choice into in-memory offline cache. Without this, after pending
        // is cleared by setProxyDetails the desiredUiSelection() falls back to a stale
        // offline value (the prior on-disk pick), causing the row highlight to bounce back.
        val perProfile = (cachedOfflineSelectionsByProfile[uuid]?.toMutableMap() ?: mutableMapOf())
        perProfile[groupName] = proxyName
        cachedOfflineSelectionsByProfile[uuid] = perProfile.toMap()
        offlineSelectionsByProfile = cachedOfflineSelectionsByProfile.toMap()
        if (alreadyPending) return
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
        // proxyGroupNames is the visible-only list (from queryProxyGroupNames).
        // offlinePreviewByProfile keys include hidden too (parseProxyGroupsPreview
        // is called with includeHidden=true), so we filter back to visible here
        // and then surface any hidden auto-subgroups that are *direct members*
        // of a visible group — a 1-hop traversal. Without this, kaso.fyi-style
        // subscriptions (one visible select root + a hidden url-test/fallback
        // tree) collapse to a single pill and a 300+ flat node list. Going
        // deeper than 1 hop would flood the pill bar with internal infra
        // groups (PRIMARY/FALLBACK/LAST_RESORT) the user shouldn't have to
        // think about.
        val offlinePreview = offlinePreviewByProfile[profile.uuid]
        val visible = if (useEngineFor(profile)) {
            proxyGroupNames
        } else {
            offlinePreview?.entries
                ?.filterNot { (_, row) -> row.hidden }
                ?.map { (k, _) -> k }
                ?.toList()
                .orEmpty()
        }
        if (offlinePreview.isNullOrEmpty()) return visible

        val visibleSet = visible.toHashSet()
        val extras = LinkedHashSet<String>()
        for (vname in visible) {
            val row = offlinePreview[vname]
                ?: offlinePreview.entries.firstOrNull { groupsMatchKey(vname, it.key) }?.value
                ?: continue
            for (memberName in row.members) {
                if (memberName in visibleSet || memberName in extras) continue
                val memberRow = offlinePreview[memberName]
                    ?: offlinePreview.entries.firstOrNull { groupsMatchKey(memberName, it.key) }?.value
                    ?: continue
                if (!memberRow.hidden) continue
                // Only auto types help routing — Selector/Unknown hidden roots
                // (e.g. mihomo's auto GLOBAL when the config defines its own)
                // would just add noise.
                if (memberRow.type != Proxy.Type.URLTest &&
                    memberRow.type != Proxy.Type.Fallback &&
                    memberRow.type != Proxy.Type.LoadBalance
                ) continue
                extras.add(memberName)
            }
        }
        return if (extras.isEmpty()) visible else visible + extras.toList()
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
        val uuid = profile.uuid
        val kept = selectedGroupIndex[uuid]?.takeIf { it in groups.indices }?.let { groups[it] }
        if (kept != null) {
            return kept
        }
        val picked = resolvePreferredGroupFromList(profile, groups)
        val index = groups.indexOf(picked).takeIf { it >= 0 } ?: 0
        selectedGroupIndex[uuid] = index.coerceIn(0, groups.lastIndex)
        return groups[selectedGroupIndex[uuid]!!]
    }

    private fun formatSelectionSummaryForHome(groupName: String): String = displayGroupName(groupName)

    private fun formatSelectionSummaryForProfiles(
        context: Context,
        profile: Profile,
        groupName: String,
    ): String {
        val groupDisplay = displayGroupName(groupName)
        val selectedProxy = resolvedSelectedProxyName(profile, groupName)
            ?.let(::displayGroupName)
        return if (selectedProxy.isNullOrBlank()) {
            context.getString(R.string.main_selected_group_fmt, groupDisplay)
        } else {
            context.getString(R.string.main_selected_route_fmt, groupDisplay, selectedProxy)
        }
    }

    private fun proxyGroupForRow(profile: Profile, groupName: String): ProxyGroup? {
        if (useEngineFor(profile)) {
            val live = proxyDetails[groupName]
                ?: proxyDetails.entries.firstOrNull { groupsMatchKey(groupName, it.key) }?.value
            if (live != null) {
                // mihomo expands `include-all-providers` / `include-all-proxies` / `include-all`
                // groups at routing time but Clash.queryGroup() only returns the statically
                // declared `proxies:` list. If our offline YAML parse shows a wider member set,
                // it's almost certainly one of those dynamic flags — merge the offline names
                // into the live group while preserving the live Proxy entries (delay, type,
                // current selection) for any names that overlap.
                val offlineRow = offlinePreviewByProfile[profile.uuid]?.let { map ->
                    map[groupName] ?: map.entries.firstOrNull { groupsMatchKey(groupName, it.key) }?.value
                }
                if (offlineRow != null && offlineRow.members.size > live.proxies.size) {
                    val liveByName = live.proxies.associateBy { it.name }
                    val seen = HashSet<String>(offlineRow.members.size + live.proxies.size)
                    val merged = buildList {
                        offlineRow.members.forEach { name ->
                            if (seen.add(name)) {
                                add(liveByName[name] ?: Proxy(name, name, "", Proxy.Type.Unknown, -1))
                            }
                        }
                        // Trailing live-only proxies (DIRECT/REJECT/etc. that aren't in the
                        // subscription's leaf set but mihomo still injects) stay where they were.
                        live.proxies.forEach { p ->
                            if (seen.add(p.name)) add(p)
                        }
                    }
                    return ProxyGroup(live.type, merged, live.now).withSelectionOverlay(profile.uuid, groupName)
                }
                return live.withSelectionOverlay(profile.uuid, groupName)
            }
            val offlineMap = offlinePreviewByProfile[profile.uuid]
            val row = offlineMap?.get(groupName)
                ?: offlineMap?.entries?.firstOrNull { groupsMatchKey(groupName, it.key) }?.value
            val now = offlineSelectedForGroup(profile.uuid, groupName)
            val type = row?.type ?: Proxy.Type.Selector
            // Engine cache may not yet hold a sibling group the user just tapped (the prior
            // fetch only walked the tree rooted at the previously visible group). Render the
            // subscription's member names as a placeholder so the row is usable immediately;
            // live engine data overwrites it on the next setProxyDetails tick.
            val placeholderMembers = row?.members.orEmpty().map { n ->
                Proxy(n, n, "", Proxy.Type.Unknown, -1)
            }
            return ProxyGroup(
                type,
                placeholderMembers,
                now,
            ).withSelectionOverlay(profile.uuid, groupName)
        }
        val offline = offlinePreviewByProfile[profile.uuid] ?: return null
        val row = offline[groupName]
            ?: offline.entries.firstOrNull { (k, _) -> groupsMatchKey(groupName, k) }?.value
            ?: return null
        val names = row.members
        val now = offlineSelectedForGroup(profile.uuid, groupName)
        return ProxyGroup(
            row.type,
            names.map { n ->
                Proxy(n, n, "", Proxy.Type.Unknown, -1)
            },
            now,
        ).withSelectionOverlay(profile.uuid, groupName)
    }

    private fun proxySelectionKey(uuid: UUID, groupName: String): String =
        "${uuid}|${groupName}"

    private fun groupsMatchKey(engineName: String, storedName: String): Boolean {
        if (engineName == storedName) return true
        return displayGroupName(engineName) == displayGroupName(storedName)
    }

    private fun hasLiveProxyDetail(profile: Profile, groupName: String): Boolean {
        if (!useEngineFor(profile)) return false
        if (proxyDetails.containsKey(groupName)) return true
        return proxyDetails.keys.any { groupsMatchKey(groupName, it) }
    }

    private fun resolvePreferredGroupFromList(profile: Profile, groupNames: List<String>): String {
        if (groupNames.isEmpty()) return ""
        lastGroupHint?.let { hint ->
            groupNames.firstOrNull { groupsMatchKey(it, hint) }?.let { return it }
        }
        offlineSelectionsByProfile[profile.uuid]?.forEach { (g, sel) ->
            if (sel.isBlank()) return@forEach
            groupNames.firstOrNull { groupsMatchKey(it, g) }?.let { return it }
        }
        val prefix = "${profile.uuid}|"
        for (key in pendingProxySelections.keys) {
            if (!key.startsWith(prefix)) continue
            val g = key.substring(prefix.length)
            if (g.isBlank()) continue
            groupNames.firstOrNull { groupsMatchKey(it, g) }?.let { return it }
        }
        return groupNames.first()
    }

    private fun offlineSelectedForGroup(uuid: UUID, engineGroupName: String): String {
        val m = offlineSelectionsByProfile[uuid] ?: return ""
        m[engineGroupName]?.takeIf { it.isNotBlank() }?.let { return it }
        return m.entries.firstOrNull { (g, _) -> groupsMatchKey(engineGroupName, g) }
            ?.value.orEmpty()
    }

    private fun pendingMapValueForGroup(uuid: UUID, engineGroupName: String): String? {
        val exact = proxySelectionKey(uuid, engineGroupName)
        pendingProxySelections[exact]?.takeIf { it.isNotBlank() }?.let { return it }
        val prefix = "${uuid}|"
        for ((k, v) in pendingProxySelections) {
            if (!k.startsWith(prefix)) continue
            if (v.isBlank()) continue
            val g = k.substring(prefix.length)
            if (groupsMatchKey(engineGroupName, g)) return v
        }
        return null
    }

    private fun desiredUiSelection(uuid: UUID, engineGroupName: String): String? {
        pendingMapValueForGroup(uuid, engineGroupName)?.let { return it }
        offlineSelectedForGroup(uuid, engineGroupName).takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun ProxyGroup.withSelectionOverlay(uuid: UUID, engineGroupName: String): ProxyGroup {
        val target = desiredUiSelection(uuid, engineGroupName) ?: return this
        if (target == now) return this
        if (proxies.isNotEmpty() && proxies.none { it.name == target }) return this
        return copy(now = target)
    }

    private fun applyActiveVisuals(holder: Holder, profile: Profile) {
        val chip = holder.binding.activeStatusChip
        val context = chip.context
        holder.binding.profileCard.strokeWidth = context.dp(1)
        if (profile.active) {
            chip.visibility = View.VISIBLE
            chip.text = context.getString(R.string.profile_active_status)
            chip.setBackgroundResource(R.drawable.bg_m3_status_chip)
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnPrimaryContainer))
        } else {
            chip.visibility = View.GONE
            chip.text = context.getString(R.string.profile_inactive_status)
            chip.setBackgroundResource(R.drawable.bg_m3_status_chip_neutral)
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
    }


    fun showProxySheet(context: Context, profile: Profile) {
        if (!profile.imported) return

        val sheet = BottomSheetProxyGroupsBinding.inflate(context.layoutInflater)
        val dialog = AppBottomSheetDialog(context)
        val groupNames = effectiveGroupsForProfile(profile)

        // Subscription pill
        sheet.proxySheetSubName.text = profile.name
        bindSubscriptionStatus(sheet, profile, context)
        bindSubscriptionExpiry(sheet, profile, context)

        if (groupNames.isEmpty()) {
            sheet.proxySheetGroupSegments.visibility = View.GONE
            sheet.proxySheetTestedDot.visibility = View.GONE
            sheet.proxySheetTestedText.visibility = View.GONE
            sheet.proxySheetPingSlot.visibility = View.GONE
            addEmptyProxyHint(sheet.proxySheetNodesList, context)
        } else {
            val picked = resolvePreferredGroupFromList(profile, groupNames)
            var idx = selectedGroupIndex[profile.uuid]
                ?: groupNames.indexOf(picked).takeIf { i -> i >= 0 }
                ?: 0
            if (idx >= groupNames.size) idx = 0
            selectedGroupIndex[profile.uuid] = idx
            var query = ""
            var sort = ProxyPickerSort.Config
            var filter: ProxyPickerFilter = ProxyPickerFilter.CurrentGroup
            var selectedScrolledGroupIndex: Int? = null
            // Per-sheet row cache: buildProxyPickerRows walks every group of the profile,
            // which can be hundreds of nodes for big subscriptions. Invalidated whenever
            // we know live state may have changed (URL-test, ping cycle, group switch).
            var cachedRows: List<ProxyPickerRow>? = null
            var cachedRowsGroupIndex: Int = -1
            var pendingSearch: Runnable? = null

            fun rowsForCurrentGroup(selectedIndex: Int): List<ProxyPickerRow> {
                val cached = cachedRows
                if (cached != null && cachedRowsGroupIndex == selectedIndex) return cached
                val fresh = buildProxyPickerRows(profile, groupNames, selectedIndex)
                cachedRows = fresh
                cachedRowsGroupIndex = selectedIndex
                return fresh
            }

            fun invalidateRowCache() {
                cachedRows = null
                cachedRowsGroupIndex = -1
            }

            fun sortLabel(s: ProxyPickerSort): String = context.getString(
                when (s) {
                    ProxyPickerSort.Config -> R.string.profile_proxy_sort_config
                    ProxyPickerSort.Delay -> R.string.profile_proxy_sort_delay
                    ProxyPickerSort.Name -> R.string.profile_proxy_sort_name
                },
            )

            fun filterLabel(f: ProxyPickerFilter): String = when (f) {
                ProxyPickerFilter.All -> context.getString(R.string.profile_proxy_filter_all)
                ProxyPickerFilter.CurrentGroup -> context.getString(R.string.profile_proxy_filter_current)
                ProxyPickerFilter.Selected -> context.getString(R.string.profile_proxy_filter_selected)
                ProxyPickerFilter.Available -> context.getString(R.string.profile_proxy_filter_available)
                is ProxyPickerFilter.Provider ->
                    context.getString(R.string.profile_proxy_filter_provider, f.name)
            }

            fun updateControlLabels() {
                sheet.proxySheetSortButton.text = sortLabel(sort)
                sheet.proxySheetFilterButton.text = filterLabel(filter)
            }

            fun render(selectedIndex: Int) {
                selectedGroupIndex[profile.uuid] = selectedIndex
                renderGroupSegmentsInto(
                    sheet.proxySheetGroupSegments,
                    profile,
                    groupNames,
                    selectedIndex,
                ) { index, group ->
                    render(index)
                    reportVisibleGroup(profile, group, force = true)
                }
                bindGroupType(sheet.proxySheetGroupTypeLabel, profile, groupNames.getOrNull(selectedIndex).orEmpty())
                val rows = applyProxyPickerControls(
                    rows = rowsForCurrentGroup(selectedIndex),
                    query = query,
                    sort = sort,
                    filter = filter,
                    currentGroupIndex = selectedIndex,
                )
                fillProxyRowsInto(sheet.proxySheetNodesList, profile, rows)
                bindTestedAgo(sheet, profile, context)
                if (selectedScrolledGroupIndex != selectedIndex) {
                    selectedScrolledGroupIndex = selectedIndex
                    scrollProxyPickerToSelected(sheet, rows)
                }
            }

            fun visibleRows(): List<ProxyPickerRow> {
                val currentIndex = (selectedGroupIndex[profile.uuid] ?: 0).coerceIn(0, groupNames.lastIndex)
                return applyProxyPickerControls(
                    rows = rowsForCurrentGroup(currentIndex),
                    query = query,
                    sort = sort,
                    filter = filter,
                    currentGroupIndex = currentIndex,
                )
            }

            var wasPinging = states.pingingUuid == profile.uuid
            val refreshRunnable = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    val pingingNow = states.pingingUuid == profile.uuid
                    val currentIndex = (selectedGroupIndex[profile.uuid] ?: 0)
                        .coerceIn(0, groupNames.lastIndex)

                    when {
                        pingingNow -> {
                            // Live ping: update ONLY the delay capsules on the
                            // existing row views. The list structure stays
                            // frozen so the user can keep scrolling and tapping
                            // nodes — the previous full re-inflate every 260ms
                            // made both impossible (jank + missed taps).
                            refreshProxyDelaysInPlace(sheet.proxySheetNodesList, profile)
                        }
                        wasPinging -> {
                            // Ping just finished — one full render so Delay
                            // sort and the Available filter pick up the fresh
                            // results and the list re-orders exactly once.
                            invalidateRowCache()
                            render(currentIndex)
                        }
                        // else — steady idle: nothing rebuilds. The picker has
                        // nothing that changes on its own between ping runs;
                        // only the lightweight "tested ago" / status text below
                        // ticks.
                    }
                    wasPinging = pingingNow

                    bindSubscriptionStatus(sheet, profile, context)
                    bindTestedAgo(sheet, profile, context)
                    sheet.proxySheetPingProgress.visibility = if (pingingNow) View.VISIBLE else View.GONE
                    sheet.proxySheetPingButton.visibility = if (pingingNow) View.INVISIBLE else View.VISIBLE
                    // 260ms while pinging keeps the live delay capsules fresh.
                    // Idle interval is 2500ms — only the "tested ago" label
                    // ticks, and the sheet may stay open for a while.
                    if (pingingNow) {
                        sheet.root.postDelayed(this, 260L)
                    } else {
                        sheet.root.postDelayed(this, 2500L)
                    }
                }
            }

            val pinging = states.pingingUuid == profile.uuid
            sheet.proxySheetPingProgress.visibility = if (pinging) View.VISIBLE else View.GONE
            sheet.proxySheetPingButton.visibility = if (pinging) View.INVISIBLE else View.VISIBLE
            sheet.proxySheetPingButton.setOnClickListener {
                val currentIndex = selectedGroupIndex[profile.uuid] ?: 0
                val groupName = groupNames.getOrNull(currentIndex) ?: return@setOnClickListener
                val names = visibleRows()
                    .filter { it.groupIndex == currentIndex }
                    .map { it.proxy.name }
                    .ifEmpty {
                        proxyGroupForRow(profile, groupName)
                            ?.proxies
                            ?.filterNot { shouldHideProxyOption(groupName, it) }
                            ?.map { it.name }
                            .orEmpty()
                    }
                if (names.isEmpty()) return@setOnClickListener
                lastPingAllAt[profile.uuid] = System.currentTimeMillis()
                onPingAll(profile, groupName, names)
                sheet.root.post(refreshRunnable)
            }

            sheet.proxySheetSearch.addTextChangedListener { editable ->
                val newQuery = editable?.toString().orEmpty()
                sheet.proxySheetSearchClear.visibility =
                    if (newQuery.isNotEmpty()) View.VISIBLE else View.GONE
                pendingSearch?.let(sheet.proxySheetSearch::removeCallbacks)
                val runnable = Runnable {
                    query = newQuery
                    render((selectedGroupIndex[profile.uuid] ?: 0).coerceIn(0, groupNames.lastIndex))
                }
                pendingSearch = runnable
                sheet.proxySheetSearch.postDelayed(runnable, 200L)
            }
            sheet.proxySheetSearchClear.setOnClickListener {
                sheet.proxySheetSearch.setText("")
            }
            // Sort dropdown — three fixed options.
            sheet.proxySheetSortButton.setOnClickListener { anchor ->
                android.widget.PopupMenu(context, anchor).apply {
                    val options = listOf(
                        ProxyPickerSort.Config,
                        ProxyPickerSort.Delay,
                        ProxyPickerSort.Name,
                    )
                    options.forEachIndexed { i, opt -> menu.add(0, i, i, sortLabel(opt)) }
                    setOnMenuItemClickListener { item ->
                        sort = options.getOrElse(item.itemId) { ProxyPickerSort.Config }
                        updateControlLabels()
                        render((selectedGroupIndex[profile.uuid] ?: 0).coerceIn(0, groupNames.lastIndex))
                        true
                    }
                }.show()
            }
            // Filter dropdown — four fixed options plus one entry per provider
            // present in the current group. The provider list is rebuilt at
            // click time so it always reflects the group currently shown.
            sheet.proxySheetFilterButton.setOnClickListener { anchor ->
                val currentIndex = (selectedGroupIndex[profile.uuid] ?: 0)
                    .coerceIn(0, groupNames.lastIndex)
                val providers = rowsForCurrentGroup(currentIndex)
                    .mapNotNull { it.provider }
                    .distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                android.widget.PopupMenu(context, anchor).apply {
                    val fixed = listOf(
                        ProxyPickerFilter.All,
                        ProxyPickerFilter.CurrentGroup,
                        ProxyPickerFilter.Selected,
                        ProxyPickerFilter.Available,
                    )
                    fixed.forEachIndexed { i, opt -> menu.add(0, i, i, filterLabel(opt)) }
                    // Provider entries use ids offset by 100 to never collide
                    // with the fixed-option ids above.
                    providers.forEachIndexed { i, p ->
                        menu.add(
                            0, 100 + i, 100 + i,
                            context.getString(R.string.profile_proxy_filter_provider, p),
                        )
                    }
                    setOnMenuItemClickListener { item ->
                        filter = if (item.itemId >= 100) {
                            providers.getOrNull(item.itemId - 100)
                                ?.let(ProxyPickerFilter::Provider)
                                ?: ProxyPickerFilter.All
                        } else {
                            fixed.getOrElse(item.itemId) { ProxyPickerFilter.All }
                        }
                        updateControlLabels()
                        render((selectedGroupIndex[profile.uuid] ?: 0).coerceIn(0, groupNames.lastIndex))
                        true
                    }
                }.show()
            }

            updateControlLabels()
            render(idx)
            reportVisibleGroup(profile, groupNames[idx])

            sheet.root.post(refreshRunnable)
            dialog.setOnDismissListener {
                sheet.root.removeCallbacks(refreshRunnable)
                pendingSearch?.let(sheet.proxySheetSearch::removeCallbacks)
                pendingSearch = null
            }
        }

        dialog.setContentView(sheet.root)
        dialog.show()
    }

    private fun bindSubscriptionStatus(
        sheet: BottomSheetProxyGroupsBinding,
        profile: Profile,
        context: Context,
    ) {
        val connected = clashRunning && useEngineFor(profile)
        sheet.proxySheetSubStatus.text = context.getString(
            if (connected) R.string.proxy_sheet_status_connected
            else R.string.proxy_sheet_status_disconnected
        )
        val tint = if (connected) {
            MaterialColors.getColor(sheet.root, com.google.android.material.R.attr.colorPrimary)
        } else {
            MaterialColors.getColor(sheet.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        sheet.proxySheetSubStatus.setTextColor(tint)
        sheet.proxySheetSubStatusDot.backgroundTintList = ColorStateList.valueOf(tint)
    }

    private fun bindSubscriptionExpiry(
        sheet: BottomSheetProxyGroupsBinding,
        profile: Profile,
        context: Context,
    ) {
        val text = formatExpiryLeft(profile.expire, context)
        if (text == null) {
            sheet.proxySheetSubExpiry.visibility = View.GONE
        } else {
            sheet.proxySheetSubExpiry.visibility = View.VISIBLE
            sheet.proxySheetSubExpiry.text = text
        }
    }

    private fun formatExpiryLeft(expireMs: Long, context: Context): String? {
        if (expireMs <= 0L) return null
        val now = System.currentTimeMillis()
        val diff = expireMs - now
        if (diff <= 0L) return context.getString(R.string.proxy_sheet_expiry_expired)
        val totalHours = diff / 3_600_000L
        val days = totalHours / 24L
        val hours = totalHours % 24L
        return if (days > 0) {
            context.getString(R.string.proxy_sheet_expiry_days_hours, days.toInt(), hours.toInt())
        } else {
            context.getString(R.string.proxy_sheet_expiry_hours, hours.toInt().coerceAtLeast(1))
        }
    }

    private fun bindTestedAgo(
        sheet: BottomSheetProxyGroupsBinding,
        profile: Profile,
        context: Context,
    ) {
        val triggered = lastPingAllAt[profile.uuid]
        if (triggered == null) {
            sheet.proxySheetTestedDot.visibility = View.GONE
            sheet.proxySheetTestedText.text = context.getString(R.string.proxy_sheet_tested_never)
            return
        }
        sheet.proxySheetTestedDot.visibility = View.VISIBLE
        val elapsedSec = ((System.currentTimeMillis() - triggered) / 1000L).coerceAtLeast(0L)
        sheet.proxySheetTestedText.text = when {
            elapsedSec < 3 -> context.getString(R.string.proxy_sheet_tested_now)
            elapsedSec < 60 -> context.getString(R.string.proxy_sheet_tested_seconds_ago, elapsedSec.toInt())
            else -> context.getString(R.string.proxy_sheet_tested_minutes_ago, (elapsedSec / 60).toInt())
        }
    }

    private fun renderGroupSegmentsInto(
        container: ViewGroup,
        profile: Profile,
        groupNames: List<String>,
        selectedIndex: Int,
        onSelected: (Int, String) -> Unit,
    ) {
        val context = container.context
        val inflater = context.layoutInflater
        // Re-inflate only when the group set actually changes; otherwise update in place so
        // the per-second refresh tick doesn't tear down + rebuild N segments (felt janky with
        // many groups and broke scroll inertia).
        val structureTag = groupNames.joinToString("|")
        val needsRebuild = container.tag != structureTag || container.childCount != groupNames.size
        if (needsRebuild) {
            container.removeAllViews()
            for (groupName in groupNames) {
                container.addView(inflater.inflate(R.layout.item_proxy_group_segment, container, false))
            }
            container.tag = structureTag
        }
        groupNames.forEachIndexed { index, groupName ->
            val segment = container.getChildAt(index) ?: return@forEachIndexed
            val nameView = segment.findViewById<TextView>(R.id.segment_name)
            val countView = segment.findViewById<TextView>(R.id.segment_count)
            val displayName = displayGroupName(groupName)
            if (nameView.text?.toString() != displayName) nameView.text = displayName
            val count = (proxyGroupForRow(profile, groupName)?.proxies?.size ?: 0).toString()
            if (countView.text?.toString() != count) countView.text = count
            segment.isSelected = index == selectedIndex
            segment.setOnClickListener {
                if (segment.isSelected) return@setOnClickListener
                for (i in 0 until container.childCount) {
                    container.getChildAt(i).isSelected = i == index
                }
                onSelected(index, groupName)
            }
        }
        // Keep the active segment visible in the scroll area, but only when selection
        // actually changes (or on first render). Without this guard the per-second refresh
        // tick yanks scroll back to the selected pill mid-drag, making it impossible to
        // browse far-away groups.
        val scroller = container.parent as? android.widget.HorizontalScrollView ?: return
        val lastIdxTagKey = R.id.proxy_sheet_group_segments
        val lastIdx = scroller.getTag(lastIdxTagKey) as? Int
        if (lastIdx == selectedIndex) return
        scroller.setTag(lastIdxTagKey, selectedIndex)
        val target = container.getChildAt(selectedIndex) ?: return
        scroller.post {
            val viewLeft = target.left
            val viewRight = target.right
            val scrollX = scroller.scrollX
            val visibleRight = scrollX + scroller.width
            when {
                viewLeft < scrollX -> scroller.smoothScrollTo(maxOf(0, viewLeft - 24), 0)
                viewRight > visibleRight ->
                    scroller.smoothScrollTo(viewRight - scroller.width + 24, 0)
            }
        }
    }

    private fun bindExpiryChip(holder: Holder, profile: Profile, context: Context) {
        val view = holder.binding.expiryChip
        if (profile.expire <= 0L || !profile.imported || profile.pending) {
            view.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        val diff = profile.expire - now
        val expired = diff <= 0L
        val totalHours = if (expired) 0L else diff / 3_600_000L
        val days = totalHours / 24L
        val hours = totalHours % 24L
        val critical = expired || days < 3L
        if (!critical) {
            view.visibility = View.GONE
            return
        }
        view.text = when {
            expired -> context.getString(R.string.proxy_sheet_expiry_expired)
            days > 0 -> context.getString(R.string.proxy_sheet_expiry_days_hours, days.toInt(), hours.toInt())
            else -> context.getString(R.string.proxy_sheet_expiry_hours, hours.toInt().coerceAtLeast(1))
        }
        view.visibility = View.VISIBLE
        view.setBackgroundResource(R.drawable.bg_m3_expiry_chip_warning)
        view.setTextColor(
            MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnErrorContainer),
        )
        // Operator may supply X-Brand-Renew-URL; when set, critical-expiry chip
        // becomes the user's primary "renew this" action. When absent we leave
        // the chip with its layout defaults (non-clickable TextView) untouched.
        val renew = brandManifest.renewUrl?.takeIf { it.isNotBlank() }
        if (renew != null) {
            view.isClickable = true
            view.isFocusable = true
            view.setOnClickListener { onOpenBrandUrl?.invoke(renew) }
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
        bindExpiryChip(holder, current, context)
        if (compactHomeCard) {
            binding.usageSummary.visibility = View.GONE
            binding.usageProgress.visibility = View.GONE
        }

        val groupNames = effectiveGroupsForProfile(current)
        val expanded = current.uuid in expandedUuids && current.imported

        binding.pingSlot.visibility = View.GONE
        val showServerChooser = !compactHomeCard && showServerChooserInCard && current.imported
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
        val selectedGroup = selectedGroupForSummary(current)
        val selectionSummary = selectedGroup?.let { group ->
            if (compactHomeCard) {
                formatSelectionSummaryForHome(group)
            } else {
                formatSelectionSummaryForProfiles(context, current, group)
            }
        }
        if (compactHomeCard) {
            val profileTitle = resolveCurrentNodeDisplayName(current)
                ?: context.getString(R.string.not_selected)
            val (titleText, emoji) = formatNodeHeadline(profileTitle, context)
            val groupSuffix = selectedGroup
                ?.takeIf { resolveCurrentNodeDisplayName(current) != null }
                ?.let { " · " + displayGroupName(it) }
                .orEmpty()
            binding.profileTitle.text = titleText + groupSuffix
            val iconEmoji = emoji ?: extractLeadingEmoji(profileTitle)
            binding.profileIconEmoji.visibility = if (iconEmoji != null) View.VISIBLE else View.GONE
            binding.profileIcon.visibility = if (iconEmoji != null) View.GONE else View.VISIBLE
            if (iconEmoji != null) {
                binding.profileIconEmoji.text = iconEmoji
            }
        } else {
            binding.profileTitle.text = current.name
            binding.profileIconEmoji.visibility = View.VISIBLE
            binding.profileIcon.visibility = View.GONE
            binding.profileIconEmoji.text = profileEmoji(current)
        }

        binding.serverSelectionSummary.visibility = when {
            compactHomeCard -> View.GONE
            !selectionSummary.isNullOrBlank() -> View.VISIBLE
            else -> View.GONE
        }
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

        val picked = resolvePreferredGroupFromList(current, groupNames)
        var idx = selectedGroupIndex[current.uuid]
            ?: groupNames.indexOf(picked).takeIf { i -> i >= 0 }
            ?: 0
        if (idx >= groupNames.size) idx = 0
        selectedGroupIndex[current.uuid] = idx

        bindGroupType(binding.proxyGroupTypeLabel, current, groupNames.getOrNull(idx).orEmpty())

        renderGroupChips(holder, current, groupNames, idx)
        fillProxyRows(holder, current, groupNames, idx)
        reportVisibleGroup(current, groupNames[idx])
    }

    private fun renderGroupChips(
        holder: Holder,
        profile: Profile,
        groupNames: List<String>,
        selectedIndex: Int,
    ) {
        renderGroupChipsInto(holder.binding.proxyGroupChips, profile, groupNames, selectedIndex) { index, groupName ->
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
            }
            container.addView(chip)
        }
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i) as Chip
            val index = i
            val groupName = groupNames[index]
            chip.setOnClickListener {
                for (j in 0 until container.childCount) {
                    (container.getChildAt(j) as Chip).isChecked = j == index
                }
                onSelected(index, groupName)
            }
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
        val context = list.context
        val pendingChoice = pendingMapValueForGroup(profile.uuid, groupName)?.takeIf { it.isNotBlank() }
        val effectiveNow = pendingChoice ?: pg.now
        val rows = pg.proxies
            .mapIndexedNotNull { index, proxy ->
                if (shouldHideProxyOption(groupName, proxy)) return@mapIndexedNotNull null
                ProxyPickerRow(
                    groupName = groupName,
                    groupIndex = groupIndex,
                    proxy = proxy,
                    configIndex = index,
                    delayMs = resolveProxyDelay(profile.uuid, proxy),
                    selected = proxy.name.isNotEmpty() && proxy.name == effectiveNow,
                    provider = providerNameForProxy(proxy.name),
                )
            }

        if (rows.isEmpty()) {
            val hintRes = when {
                useEngineFor(profile) && !hasLiveProxyDetail(profile, groupName) ->
                    R.string.proxy_nodes_loading
                useEngineFor(profile) ->
                    R.string.proxy_group_empty_runtime
                else ->
                    R.string.proxy_nodes_empty_connect_vpn
            }
            addEmptyProxyHint(list, context, hintRes)
            return
        }

        fillProxyRowsInto(list, profile, rows)
    }

    private fun fillProxyRowsInto(
        list: ViewGroup,
        profile: Profile,
        rows: List<ProxyPickerRow>,
    ) {
        list.removeAllViews()

        val inflater = list.context.layoutInflater
        val context = list.context

        if (rows.isEmpty()) {
            addEmptyProxyHint(list, context, R.string.profile_proxy_empty_filtered)
            return
        }

        val showGroupInSubtitle = rows.map { it.groupName }.distinct().size > 1

        for (pickerRow in rows) {
            val p = pickerRow.proxy
            val groupName = pickerRow.groupName
            val row = inflater.inflate(R.layout.adapter_home_proxy_node, list, false)

            val title = p.title.ifBlank { p.name }
            val flag = FlagParser.parse(title)
            val cleanTitle = flag?.let {
                title.removePrefix(it.emoji)
                    .trimStart(' ', '|', '-', '_', '.', ':')
                    .ifBlank { title }
            } ?: title
            val (namePart, cityPart) = splitNameAndCity(cleanTitle)
            row.findViewById<TextView>(R.id.proxy_title).text = namePart
            row.findViewById<TextView>(R.id.proxy_city).apply {
                if (cityPart != null) {
                    text = cityPart
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            val flagCard = row.findViewById<View>(R.id.proxy_flag_card)
            val flagImage = row.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.proxy_flag_image)
            val flagText = row.findViewById<TextView>(R.id.proxy_flag)
            if (flag != null) {
                flagCard.visibility = View.VISIBLE
                val sizePx = context.dp(28)
                val svgDrawable = FlagDrawableLoader.load(context, flag.code, sizePx)
                if (svgDrawable != null) {
                    flagImage.setImageDrawable(svgDrawable)
                    flagImage.visibility = View.VISIBLE
                    flagText.visibility = View.GONE
                } else {
                    flagImage.visibility = View.GONE
                    flagText.text = flag.emoji
                    flagText.visibility = View.VISIBLE
                }
            } else {
                flagCard.visibility = View.GONE
            }

            val typeBadge = row.findViewById<TextView>(R.id.proxy_type_badge)
            val typeName = p.type.name
            val showBadge = typeName != "Unknown" && !p.type.group
            if (showBadge) {
                typeBadge.visibility = View.VISIBLE
                typeBadge.text = typeName.uppercase()
                typeBadge.setTextColor(
                    ContextCompat.getColor(context, protocolFamilyColor(typeName))
                )
            } else {
                typeBadge.visibility = View.GONE
            }

            val transportInfo = if (showBadge) transportInfoByProfile[profile.uuid]?.get(p.name) else null
            val transportBadge = row.findViewById<TextView>(R.id.proxy_transport_badge)
            val transportLabel = transportLabel(transportInfo)
            if (transportLabel != null) {
                transportBadge.visibility = View.VISIBLE
                transportBadge.text = transportLabel
                transportBadge.setTextColor(ContextCompat.getColor(context, R.color.proto_transport))
            } else {
                transportBadge.visibility = View.GONE
            }
            val realityBadge = row.findViewById<TextView>(R.id.proxy_reality_badge)
            if (transportInfo?.reality == true) {
                realityBadge.visibility = View.VISIBLE
                realityBadge.text = "REALITY"
                realityBadge.setTextColor(ContextCompat.getColor(context, R.color.proto_reality))
            } else {
                realityBadge.visibility = View.GONE
            }

            val rawSubtitle = p.subtitle
                .takeIf { it.isNotBlank() && !it.equals(typeName, ignoreCase = true) }
            val subtitle = buildList {
                rawSubtitle?.let(::add)
                if (showGroupInSubtitle) add(displayGroupName(groupName))
            }.joinToString(" · ").takeIf { it.isNotBlank() }
            row.findViewById<TextView>(R.id.proxy_subtitle).apply {
                visibility = if (subtitle == null) View.GONE else View.VISIBLE
                text = subtitle.orEmpty()
            }
            row.findViewById<View>(R.id.proxy_subtitle_sep).visibility =
                if (showBadge && subtitle != null) View.VISIBLE else View.GONE

            val delayMs = pickerRow.delayMs

            val capsule = row.findViewById<View>(R.id.latency_capsule)
            val dot = row.findViewById<View>(R.id.latency_dot)
            val delayView = row.findViewById<TextView>(R.id.proxy_delay)
            delayView.text = formatDelay(delayMs)
            applyDelayStyle(capsule, dot, delayView, delayMs, context)

            val selected = pickerRow.selected
            row.isSelected = selected
            row.findViewById<View>(R.id.selected_bar).visibility =
                if (selected) View.VISIBLE else View.INVISIBLE
            row.findViewById<View>(R.id.selected_check).visibility =
                if (selected) View.VISIBLE else View.GONE
            val mainHit = row.findViewById<View>(R.id.proxy_row_main_hit)
            val tryPickNode: () -> Boolean = {
                val canPickLive = clashRunning && useEngineFor(profile)
                val canPickOffline = profile.imported && profile.uuid == activeProfileUuid
                if (canPickLive || canPickOffline) {
                    setPendingProxySelection(profile.uuid, groupName, p.name)
                    markRowSelected(list, row)
                    onProxyNodeSelected(profile, groupName, p.name)
                    true
                } else {
                    false
                }
            }
            mainHit.setOnClickListener {
                if (!tryPickNode()) {
                    onProxyYamlDetail(profile, groupName, p.name)
                }
            }
            mainHit.setOnLongClickListener {
                onProxyYamlDetail(profile, groupName, p.name)
                true
            }
            // Tag the row with its Proxy so refreshProxyDelaysInPlace can
            // update just the latency capsule during a live ping without
            // rebuilding the whole list.
            row.tag = p
            list.addView(row)
        }
    }

    /**
     * Updates only the latency capsule (delay text + colour) on each already-
     * inflated proxy row, leaving the list structure untouched. Used during a
     * live ping so the user can keep scrolling and tapping nodes — a full
     * [fillProxyRowsInto] re-inflate on every 260ms tick made the list jank
     * and swallowed taps. Re-sorting / re-filtering by the new delays happens
     * once, after the ping finishes (see the refresh runnable).
     */
    private fun refreshProxyDelaysInPlace(list: ViewGroup, profile: Profile) {
        val context = list.context
        for (row in list.children) {
            val proxy = row.tag as? Proxy ?: continue
            val delayMs = resolveProxyDelay(profile.uuid, proxy)
            val capsule = row.findViewById<View>(R.id.latency_capsule) ?: continue
            val dot = row.findViewById<View>(R.id.latency_dot) ?: continue
            val delayView = row.findViewById<TextView>(R.id.proxy_delay) ?: continue
            delayView.text = formatDelay(delayMs)
            applyDelayStyle(capsule, dot, delayView, delayMs, context)
        }
    }

    private fun buildProxyPickerRows(
        profile: Profile,
        groupNames: List<String>,
        currentGroupIndex: Int,
    ): List<ProxyPickerRow> {
        val selectedIndex = currentGroupIndex.coerceIn(0, groupNames.lastIndex)
        return groupNames.flatMapIndexed { groupIndex, groupName ->
            val pg = proxyGroupForRow(profile, groupName) ?: return@flatMapIndexed emptyList()
            val pendingChoice = pendingMapValueForGroup(profile.uuid, groupName)
                ?.takeIf { it.isNotBlank() }
            val effectiveNow = pendingChoice ?: pg.now
            pg.proxies.mapIndexedNotNull { proxyIndex, proxy ->
                if (shouldHideProxyOption(groupName, proxy)) return@mapIndexedNotNull null
                ProxyPickerRow(
                    groupName = groupName,
                    groupIndex = groupIndex,
                    proxy = proxy,
                    configIndex = groupIndex * 100_000 + proxyIndex,
                    delayMs = resolveProxyDelay(profile.uuid, proxy),
                    selected = proxy.name.isNotEmpty() && proxy.name == effectiveNow,
                    provider = providerNameForProxy(proxy.name),
                )
            }
        }.let { rows ->
            val selectedGroupRows = rows.filter { it.groupIndex == selectedIndex }
            rows.filterNot { it.groupIndex == selectedIndex } + selectedGroupRows
        }.sortedBy { it.configIndex }
    }

    private fun applyProxyPickerControls(
        rows: List<ProxyPickerRow>,
        query: String,
        sort: ProxyPickerSort,
        filter: ProxyPickerFilter,
        currentGroupIndex: Int,
    ): List<ProxyPickerRow> {
        val normalizedQuery = query.trim()
        val filtered = rows.asSequence()
            .filter { row ->
                normalizedQuery.isBlank() ||
                    row.proxy.name.contains(normalizedQuery, ignoreCase = true) ||
                    row.proxy.title.contains(normalizedQuery, ignoreCase = true) ||
                    row.groupName.contains(normalizedQuery, ignoreCase = true) ||
                    displayGroupName(row.groupName).contains(normalizedQuery, ignoreCase = true) ||
                    row.provider?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .filter { row ->
                when (filter) {
                    ProxyPickerFilter.All -> true
                    ProxyPickerFilter.CurrentGroup -> row.groupIndex == currentGroupIndex
                    ProxyPickerFilter.Selected -> row.selected
                    ProxyPickerFilter.Available -> row.available
                    is ProxyPickerFilter.Provider -> row.provider == filter.name
                }
            }
            .toList()

        return when (sort) {
            ProxyPickerSort.Config -> filtered.sortedBy { it.configIndex }
            ProxyPickerSort.Delay -> filtered.sortedWith(
                compareBy<ProxyPickerRow> { if (it.delayMs >= 0) 0 else 1 }
                    .thenBy { if (it.delayMs >= 0) it.delayMs else Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { displayGroupName(it.groupName) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.proxy.title.ifBlank { it.proxy.name } },
            )
            ProxyPickerSort.Name -> filtered.sortedWith(
                compareBy<ProxyPickerRow, String>(String.CASE_INSENSITIVE_ORDER) { row ->
                    row.proxy.title.ifBlank { row.proxy.name }
                }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { displayGroupName(it.groupName) }
                    .thenBy { it.configIndex },
            )
        }
    }

    private fun resolveProxyDelay(uuid: UUID, proxy: Proxy): Int {
        val key = "${uuid}|${proxy.name}"
        val standalone = standalonePingDelays[key]
        val nested = nestedGroupDelay(uuid, proxy.name)
        return when {
            proxy.delay >= 0 -> proxy.delay
            nested >= 0 -> nested
            standalone != null -> standalone
            else -> proxy.delay
        }
    }

    private fun providerNameForProxy(proxyName: String): String? {
        val trimmed = proxyName.trimStart()
        if (!trimmed.startsWith("[")) return null
        val end = trimmed.indexOf(']')
        if (end <= 1) return null
        return trimmed.substring(1, end).takeIf { it.isNotBlank() }
    }

    /** Clear all rows' selected-state ornaments, then mark [target] as selected. */
    private fun markRowSelected(list: ViewGroup, target: View) {
        for (i in 0 until list.childCount) {
            val r = list.getChildAt(i)
            r.isSelected = false
            r.findViewById<View>(R.id.selected_bar)?.visibility = View.INVISIBLE
            r.findViewById<View>(R.id.selected_check)?.visibility = View.GONE
        }
        target.isSelected = true
        target.findViewById<View>(R.id.selected_bar).visibility = View.VISIBLE
        target.findViewById<View>(R.id.selected_check).visibility = View.VISIBLE
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

    private fun shouldHideProxyOption(groupName: String, proxy: Proxy): Boolean {
        if (!groupName.equals("GLOBAL", ignoreCase = true)) return false
        if (proxy.type == Proxy.Type.Direct || proxy.type == Proxy.Type.Reject) return true
        return proxy.name.equals("DIRECT", ignoreCase = true) ||
            proxy.name.equals("REJECT", ignoreCase = true)
    }

    private fun shouldHideProxyName(groupName: String, proxyName: String): Boolean {
        if (!groupName.equals("GLOBAL", ignoreCase = true)) return false
        return proxyName.equals("DIRECT", ignoreCase = true) ||
            proxyName.equals("REJECT", ignoreCase = true)
    }

    private fun scrollProxyPickerToSelected(
        sheet: BottomSheetProxyGroupsBinding,
        rows: List<ProxyPickerRow>,
    ) {
        if (rows.none { it.selected }) return
        sheet.proxySheetNodesList.post {
            val row = sheet.proxySheetNodesList.children.firstOrNull { child ->
                child.findViewById<View>(R.id.selected_bar)?.visibility == View.VISIBLE
            } ?: return@post
            sheet.proxySheetScroll.smoothScrollTo(0, row.top)
        }
    }

    private fun addEmptyProxyHint(
        list: ViewGroup,
        context: Context,
        messageRes: Int = R.string.proxy_nodes_empty_connect_vpn,
    ) {
        val tv = TextView(context).apply {
            text = context.getString(messageRes)
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
            return offline.members.asSequence()
                .map { name ->
                    val direct = standalonePingDelays["$uuid|$name"]
                    direct ?: resolve(name)
                }
                .filter { it >= 0 }
                .minOrNull() ?: -1
        }

        return resolve(proxyName)
    }

    private fun splitNameAndCity(text: String): Pair<String, String?> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed to null
        val lastSpace = trimmed.lastIndexOf(' ')
        if (lastSpace <= 0) return trimmed to null
        val head = trimmed.substring(0, lastSpace).trim()
        val tail = trimmed.substring(lastSpace + 1).trim()
        if (head.isEmpty() || tail.length < 3) return trimmed to null
        val tailIsCityLike = tail.first().isLetter() && tail.all { it.isLetter() || it == '-' || it == '\'' }
        val headHasIdShape = head.any { it.isDigit() || it == '-' || it == '_' || it == '#' }
        return if (tailIsCityLike && headHasIdShape) head to tail else trimmed to null
    }

    /** "TCP" / "WS" / "GRPC" / "XHTTP" / "H2" / "HTTP"; null when YAML had no transport info. */
    private fun transportLabel(info: ProxyTransportInfo?): String? {
        if (info == null) return null
        return when (val net = info.network.lowercase()) {
            "" -> "TCP"
            "h2", "http2" -> "H2"
            else -> net.uppercase()
        }
    }

    private fun protocolFamilyColor(typeName: String): Int = when (typeName.lowercase()) {
        "vmess", "vless" -> R.color.proto_vless
        "trojan" -> R.color.proto_trojan
        "hysteria", "hysteria2" -> R.color.proto_hysteria
        "tuic", "anytls", "masque" -> R.color.proto_tuic
        "shadowsocks", "shadowsocksr", "snell", "socks5" -> R.color.proto_shadowsocks
        "http" -> R.color.proto_http
        "wireguard", "trusttunnel" -> R.color.proto_wireguard
        "direct" -> R.color.proto_tcp
        else -> R.color.proto_default
    }

    private fun applyDelayStyle(
        capsule: View,
        dot: View,
        text: TextView,
        delayMs: Int,
        context: Context,
    ) {
        val (bgRes, colorRes) = when {
            delayMs in 0..200 -> R.drawable.bg_delay_good to R.color.delay_good
            delayMs in 201..500 -> R.drawable.bg_delay_medium to R.color.delay_medium
            delayMs in 501..Short.MAX_VALUE -> R.drawable.bg_delay_bad to R.color.delay_bad
            else -> R.drawable.bg_delay_timeout to R.color.delay_timeout
        }
        val color = ContextCompat.getColor(context, colorRes)
        capsule.setBackgroundResource(bgRes)
        dot.backgroundTintList = ColorStateList.valueOf(color)
        text.setTextColor(color)
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
            val selected = resolvedSelectedProxyName(profile, group)
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

    private fun resolvedSelectedProxyName(profile: Profile, groupName: String): String? =
        resolvedSelectedProxyName(profile, groupName, linkedSetOf())

    private fun resolvedSelectedProxyName(
        profile: Profile,
        groupName: String,
        seen: MutableSet<String>,
    ): String? {
        if (!seen.add(groupName)) return null

        val selected = proxyGroupForRow(profile, groupName)
            ?.now
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (shouldHideProxyName(groupName, selected)) return null

        if (selectedLooksLikeGroup(profile, groupName, selected)) {
            resolvedSelectedProxyName(profile, selected, seen)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return selected
    }

    private fun selectedLooksLikeGroup(profile: Profile, groupName: String, selected: String): Boolean {
        val selectedProxy = proxyGroupForRow(profile, groupName)
            ?.proxies
            ?.firstOrNull { it.name == selected }
        return if (useEngineFor(profile)) {
            selectedProxy?.type?.group == true || selected in proxyDetails
        } else {
            selected in offlinePreviewByProfile[profile.uuid].orEmpty()
        }
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

    private fun profileEmoji(profile: Profile): String {
        val index = (profile.uuid.hashCode() and Int.MAX_VALUE) % profileEmojiPool.size
        return profileEmojiPool[index]
    }

    override fun getItemCount(): Int = profiles.size
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
