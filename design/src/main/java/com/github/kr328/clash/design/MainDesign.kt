package com.github.kr328.clash.design

import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.R as MaterialR
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.SubscriptionUsage
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.databinding.BottomSheetMainModeBinding
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.HomeBackgroundStyle
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.util.UUID

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenNewProfile,
        OpenSettings,
        OpenThemeSettings,
        OpenAppSettings,
        OpenAbout,
        PatchModeDirect,
        PatchModeGlobal,
        PatchModeRule,
        OpenImportClipboard,
        OpenImportQr,
        CycleTheme,
        OpenLogs,
        OpenConnections,
        /** Routing rules list screen. */
        OpenRouting,
        /** Rule snippets / editor screen. */
        OpenRules,
        /** Proxy chain screen. */
        OpenProxyChain,
        /** Direct entry to per-app routing for quick access from the routing tab. */
        OpenPerAppRouting,
        /** Subscriptions / profiles list. */
        OpenProfiles,
    }

    private enum class MainTab {
        Home,
        Profiles,
        Routing,
        Settings,
    }

    /** Set by MainActivity to react to taps on the in-header update badge. */
    var onUpdateBadgeTap: (() -> Unit)? = null

    val profileActivateRequests = Channel<Profile>(Channel.UNLIMITED)
    val profileMenuRequests = Channel<Pair<Profile, View>>(Channel.UNLIMITED)
    val profileEditRequests = Channel<Profile>(Channel.UNLIMITED)
    val patchHomeProxyRequests = Channel<Triple<Profile, String, String>>(Channel.CONFLATED)
    val profilePingAllRequests = Channel<Triple<Profile, String, List<String>>>(Channel.UNLIMITED)
    val profileForceUpdateRequests = Channel<Profile>(Channel.UNLIMITED)
    val profileProxyYamlRequests = Channel<Triple<Profile, String, String>>(Channel.UNLIMITED)
    /** Fires when user expands/collapses any profile panel so the host can reload proxy previews. */
    val profileExpandChanged = Channel<Unit>(Channel.CONFLATED)
    val profileVisibleGroupChanged = Channel<Pair<Profile, String>>(Channel.CONFLATED)

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    private var clashRunningState: Boolean = false
    private var tunnelStartingState: Boolean = false
    private val uiStore = UiStore(context)
    private val expandedProfileUuids: LinkedHashSet<UUID> = linkedSetOf()
    private var currentModeSegment: TunnelState.Mode = TunnelState.Mode.Rule
    private var activeProfileForQuickActions: Profile? = null
    private var activeAnnouncementSupportUrl: String? = null
    private var activeSubscriptionUsage: SubscriptionUsage? = null
    private var activeAnnouncementOnOpenUrl: ((String) -> Unit)? = null
    private var activeAnnouncementOnSupport: (() -> Unit)? = null
    private var announcementCardCoversSupport: Boolean = false

    private class StaticPageAdapter(
        private val pages: List<View>,
    ) : RecyclerView.Adapter<StaticPageAdapter.Holder>() {
        class Holder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        private fun isBoundaryPage(position: Int): Boolean {
            val count = pages.size
            return position == 0 || position == count + 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return Holder(container)
        }

        private fun logicalPosition(position: Int): Int {
            val count = pages.size
            return when (position) {
                0 -> count - 1
                count + 1 -> 0
                else -> position - 1
            }
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.container.removeAllViews()
            val page = pages[logicalPosition(position)]
            if (isBoundaryPage(position)) {
                // Circular wrap placeholders: avoid drawToBitmap (memory churn / jank on low-RAM devices).
                val bg = FrameLayout(holder.container.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(context.resolveThemedColor(MaterialR.attr.colorSurface))
                }
                holder.container.addView(bg)
                return
            }

            (page.parent as? ViewGroup)?.removeView(page)
            page.visibility = View.VISIBLE
            holder.container.addView(
                page,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        override fun onViewRecycled(holder: Holder) {
            holder.container.removeAllViews()
        }

        override fun getItemCount(): Int = pages.size + 2
    }

    private val profileAdapter = ProfileAdapter(
        { profile -> profileActivateRequests.trySend(profile) },
        { profile, anchor -> profileMenuRequests.trySend(profile to anchor) },
        { profile -> toggleProfileExpand(profile) },
        { profile, group, proxyName ->
            patchHomeProxyRequests.trySend(Triple(profile, group, proxyName))
        },
        { profile, group, proxyNames -> profilePingAllRequests.trySend(Triple(profile, group, proxyNames)) },
        { profile -> profileForceUpdateRequests.trySend(profile) },
        { profile, group, proxy -> profileProxyYamlRequests.trySend(Triple(profile, group, proxy)) },
        { profile, group -> profileVisibleGroupChanged.trySend(profile to group) },
        expandOnProfileClick = true,
    )

    override val root: View
        get() = binding.root

    fun getExpandedProfileUuids(): Set<UUID> = expandedProfileUuids.toSet()

    /**
     * Pushes operator-pushed subscription metadata into the home UI and the active profile card.
     */
    suspend fun setAnnouncement(
        text: String?,
        url: String?,
        usage: SubscriptionUsage? = null,
        supportUrl: String? = null,
        onOpenUrl: ((String) -> Unit)? = null,
        onRefresh: (() -> Unit)? = null,
        onSupport: (() -> Unit)? = null,
        announcementCollapsed: Boolean = false,
        onToggleCollapsed: (() -> Unit)? = null,
    ) {
        withContext(Dispatchers.Main) {
            val message = text
                ?.takeIf { it.isNotBlank() }
                ?.let { com.github.kr328.clash.common.util.MaybeBase64.decode(it).trim() }
                .orEmpty()
            val announcementUrl = url?.takeIf { it.isNotBlank() }
            val support = supportUrl?.takeIf { it.isNotBlank() }
            val hasAnnouncement = message.isNotBlank()
            val useAnnouncementCard = hasAnnouncement && uiStore.announcementCardEnabled
            activeAnnouncementSupportUrl = support
            activeSubscriptionUsage = usage
            activeAnnouncementOnOpenUrl = onOpenUrl
            activeAnnouncementOnSupport = onSupport

            if (useAnnouncementCard) {
                binding.mainHeaderTitle.text = context.getString(R.string.launch_name_meta)
                binding.mainHeaderSummary.visibility = View.GONE
                binding.mainHeaderSummary.text = ""
                binding.mainHeaderSummary.setOnClickListener(null)
                binding.mainHeaderSummary.isClickable = false
            } else {
                binding.mainHeaderTitle.text = context.getString(
                    if (hasAnnouncement) R.string.announcement_settings else R.string.launch_name_meta,
                )
                binding.mainHeaderSummary.visibility = if (hasAnnouncement) View.VISIBLE else View.GONE
                binding.mainHeaderSummary.text = message
                binding.mainHeaderSummary.setOnClickListener {
                    val target = announcementUrl ?: return@setOnClickListener
                    onOpenUrl?.invoke(target)
                }
                binding.mainHeaderSummary.isClickable = announcementUrl != null
            }
            binding.mainHeaderSupport.visibility = View.GONE
            binding.mainHeaderSupport.setOnClickListener(null)

            val bodyCollapsed = useAnnouncementCard && announcementCollapsed
            announcementCardCoversSupport = useAnnouncementCard && hasAnnouncement && support != null

            binding.mainAnnouncementCard.visibility =
                if (useAnnouncementCard) View.VISIBLE else View.GONE
            binding.mainAnnouncementBodyScroll.visibility =
                if (useAnnouncementCard && !bodyCollapsed) View.VISIBLE else View.GONE
            binding.mainAnnouncementBody.visibility =
                if (useAnnouncementCard && !bodyCollapsed) View.VISIBLE else View.GONE

            binding.mainAnnouncementText.visibility =
                if (useAnnouncementCard && !bodyCollapsed && message.isNotBlank()) View.VISIBLE else View.GONE
            binding.mainAnnouncementText.text = message

            binding.mainAnnouncementStatsRow.visibility = View.GONE
            binding.mainAnnouncementUsageBar.visibility = View.GONE
            binding.mainAnnouncementUsageText.visibility = View.GONE
            binding.mainAnnouncementUnavailableRow.visibility =
                if (useAnnouncementCard && !bodyCollapsed && announcementUrl != null) View.VISIBLE else View.GONE

            binding.mainAnnouncementOpenLink.visibility =
                if (useAnnouncementCard && !bodyCollapsed && announcementUrl != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementOpenLink.setOnClickListener {
                val target = announcementUrl ?: return@setOnClickListener
                onOpenUrl?.invoke(target)
            }

            binding.mainAnnouncementSupport.visibility =
                if (useAnnouncementCard && !bodyCollapsed && support != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementSupport.setOnClickListener {
                val target = support ?: return@setOnClickListener
                onOpenUrl?.invoke(target) ?: onSupport?.invoke()
            }

            binding.mainAnnouncementRefresh.visibility =
                if (useAnnouncementCard && onRefresh != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementRefresh.setOnClickListener { onRefresh?.invoke() }

            val showCollapseControl = useAnnouncementCard && onToggleCollapsed != null
            binding.mainAnnouncementDismiss.visibility =
                if (showCollapseControl) View.VISIBLE else View.GONE
            if (bodyCollapsed) {
                binding.mainAnnouncementDismiss.setImageResource(R.drawable.ic_baseline_expand_more)
                binding.mainAnnouncementDismiss.contentDescription =
                    context.getString(R.string.announcement_expand)
            } else {
                binding.mainAnnouncementDismiss.setImageResource(R.drawable.ic_baseline_expand_less)
                binding.mainAnnouncementDismiss.contentDescription =
                    context.getString(R.string.announcement_collapse)
            }
            binding.mainAnnouncementDismiss.setOnClickListener {
                onToggleCollapsed?.invoke()
            }

            binding.mainAnnouncementCard.isClickable = bodyCollapsed
            binding.mainAnnouncementCard.isFocusable = bodyCollapsed
            binding.mainAnnouncementCard.setOnClickListener {
                if (bodyCollapsed) onToggleCollapsed?.invoke()
            }

            profileAdapter.setActiveAnnouncement(
                text = text,
                url = url,
                supportUrl = supportUrl,
                onOpenUrl = onOpenUrl,
                onSupport = onSupport,
            )
            renderActiveProfileCard(activeProfileForQuickActions)
        }
    }

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            clashRunningState = running
            binding.clashRunning = running
            if (running) {
                tunnelStartingState = false
                binding.tunnelStarting = false
            }
            applyPowerVisuals()
        }
    }

    suspend fun setTunnelStarting(starting: Boolean) {
        withContext(Dispatchers.Main) {
            tunnelStartingState = starting
            binding.tunnelStarting = starting
            applyPowerVisuals()
        }
    }

    suspend fun setPingingProfile(uuid: UUID?) {
        withContext(Dispatchers.Main) {
            profileAdapter.setPingingUuid(uuid)
        }
    }

    private fun applyPowerVisuals() {
        val button = binding.mainPowerCard
        val running = clashRunningState
        val starting = tunnelStartingState && !running
        val (bgAttr, iconAttr, elevationDp) = when {
            running -> Triple(
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorOnPrimary,
                6f,
            )
            starting -> Triple(
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                5f,
            )
            else -> Triple(
                com.google.android.material.R.attr.colorSurfaceVariant,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                4f,
            )
        }
        button.backgroundTintList = ColorStateList.valueOf(context.resolveThemedColor(bgAttr))
        button.iconTint = ColorStateList.valueOf(context.resolveThemedColor(iconAttr))
        button.setTextColor(context.resolveThemedColor(iconAttr))
        button.elevation = elevationDp * context.resources.displayMetrics.density

        val targetAlpha = when {
            running -> 0.34f
            starting -> 0.24f
            else -> 0.12f
        }
        val targetScale = when {
            running -> 1.08f
            starting -> 1.04f
            else -> 0.96f
        }
        binding.powerHalo.animate().cancel()
        binding.powerHalo.animate()
            .alpha(targetAlpha)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(260L)
            .start()
        binding.powerRingOuter.animate()
            .alpha(if (running || starting) 0.95f else 0.62f)
            .setDuration(220L)
            .start()
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            val normalized = normalizeMode(mode)
            currentModeSegment = normalized
            binding.mode = when (normalized) {
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    /**
     * Applies saved tunnel mode preference to the dashboard before the first [fetch],
     * so mode chips do not briefly show a stale default while the core state loads.
     */
    suspend fun applyInitialModeFromPreference(pref: String?) {
        val mode = when (pref) {
            TunnelState.Mode.Rule.name -> TunnelState.Mode.Rule
            TunnelState.Mode.Global.name -> TunnelState.Mode.Global
            TunnelState.Mode.Direct.name -> TunnelState.Mode.Rule
            else -> return
        }
        setMode(mode)
    }

    private fun normalizeMode(mode: TunnelState.Mode): TunnelState.Mode =
        if (mode == TunnelState.Mode.Direct) TunnelState.Mode.Rule else mode

    suspend fun syncThemeToggleIcon(mode: DarkMode) {
        withContext(Dispatchers.Main) {
            binding.btnThemeToggle.text = context.getString(
                when (mode) {
                    DarkMode.ForceLight -> R.string.main_emoji_moon
                    else -> R.string.main_emoji_sun
                },
            )
        }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        withContext(Dispatchers.Main) {
            binding.hasProfiles = profiles.isNotEmpty()
            val homeProfiles = profiles.filter { it.active }.ifEmpty { profiles.take(1) }
            activeProfileForQuickActions = profiles.firstOrNull { it.active } ?: profiles.firstOrNull()
            renderActiveProfileCard(activeProfileForQuickActions)
            profileAdapter.apply {
                val ids = homeProfiles.map { it.uuid }.toSet()
                expandedProfileUuids.retainAll { it in ids }
                patchDataSet(this::profiles, homeProfiles, id = { it.uuid })
            }
            profileAdapter.setExpandedUuids(expandedProfileUuids.toSet())
        }
    }

    private fun renderActiveProfileCard(profile: Profile?) {
        val p = profile
        binding.mainActiveProfileValue.text = p?.name.orEmpty().ifBlank { context.getString(R.string.not_selected) }
        binding.mainActiveProfileMeta.text = p?.let(::profileMetaLabel).orEmpty()
        binding.mainActiveProfileMeta.visibility = if (p != null) View.VISIBLE else View.GONE
        binding.mainActiveProfileUsage.text = p?.let(::usageLabel).orEmpty()
        binding.mainActiveProfileUsage.visibility = if (p != null) View.VISIBLE else View.GONE
        val showUpdate = p?.imported == true && p.type != Profile.Type.File
        binding.mainActiveProfileUpdate.visibility = if (showUpdate) View.VISIBLE else View.GONE
        val showSupport = !resolveSupportUrl().isNullOrBlank() && !announcementCardCoversSupport
        binding.mainActiveProfileSupport.visibility = if (showSupport) View.VISIBLE else View.GONE
    }

    private fun resolveSupportUrl(): String? =
        activeAnnouncementSupportUrl?.takeIf { it.isNotBlank() }
            ?: uiStore.supportUrl.takeIf { it.isNotBlank() }

    private fun profileMetaLabel(profile: Profile): String {
        val base = profileTypeLabel(profile)
        val daysLeft = daysLeftValue(profile) ?: return base
        return "$base • ${context.getString(R.string.sub_announcement_days_left)} $daysLeft"
    }

    private fun profileTypeLabel(profile: Profile): String = when (profile.type) {
        Profile.Type.Url -> context.getString(R.string.url)
        Profile.Type.File -> context.getString(R.string.file)
        Profile.Type.External -> context.getString(R.string.external)
    }

    private fun daysLeftValue(profile: Profile): Int? {
        val expireAt = profile.expire.takeIf { it > 0L } ?: return null
        val now = System.currentTimeMillis()
        if (expireAt <= now) return 0

        val millisLeft = expireAt - now
        return ((millisLeft + 86_400_000L - 1L) / 86_400_000L).toInt().coerceAtLeast(1)
    }

    private fun usageLabel(profile: Profile): String {
        val headerUsage = activeSubscriptionUsage.takeIf { profile.type == Profile.Type.Url }
        val used = headerUsage?.used ?: (profile.upload + profile.download)
        val usedText = used.toBytesString()
        val total = headerUsage?.total ?: profile.total
        if (total < 2L) return "$usedText / ${context.getString(R.string.sub_announcement_unlimited)}"
        return "$usedText / ${total.toBytesString()}"
    }

    suspend fun patchProxyGroups(
        names: List<String>,
        running: Boolean,
        mode: TunnelState.Mode?,
        lastGroupHint: String?,
        offlinePreviewByProfile: Map<UUID, Map<String, ProxyGroupPreviewRow>> = emptyMap(),
        activeProfileUuid: UUID? = null,
        offlineSelectionsByProfile: Map<UUID, Map<String, String>> = emptyMap(),
    ) {
        withContext(Dispatchers.Main) {
            profileAdapter.setProxyContext(
                names,
                running,
                mode,
                lastGroupHint,
                offlinePreviewByProfile,
                activeProfileUuid,
                offlineSelectionsByProfile,
            )
            profileAdapter.setExpandedUuids(expandedProfileUuids.toSet())
        }
    }

    suspend fun patchProxyDetails(details: Map<String, ProxyGroup>) {
        withContext(Dispatchers.Main) {
            profileAdapter.setProxyDetails(details)
        }
    }

    suspend fun clearProxyDetails() {
        withContext(Dispatchers.Main) {
            profileAdapter.clearProxyDetails()
        }
    }

    suspend fun markProxySelectionPending(profile: Profile, group: String, proxy: String) {
        withContext(Dispatchers.Main) {
            profileAdapter.setPendingProxySelection(profile.uuid, group, proxy)
        }
    }

    suspend fun clearStandalonePingForProfile(uuid: UUID) {
        withContext(Dispatchers.Main) {
            profileAdapter.clearStandalonePingDelays(uuid)
        }
    }

    suspend fun patchStandalonePingResults(uuid: UUID, results: Map<String, Int>) {
        withContext(Dispatchers.Main) {
            profileAdapter.setStandalonePingResults(uuid, results)
        }
    }

    private fun toggleProfileExpand(profile: Profile) {
        if (!profile.imported) {
            return
        }
        val hadGroups = profileAdapter.hasProxyGroupsFor(profile)
        val needsRefresh = expandedProfileUuids.add(profile.uuid)
        profileAdapter.setExpandedUuids(expandedProfileUuids.toSet())
        if (needsRefresh || !hadGroups) {
            profileExpandChanged.trySend(Unit)
        }
        openProxySheetWhenReady(profile)
    }

    private fun openProxySheetWhenReady(profile: Profile, attempt: Int = 0) {
        // Was 180ms x 15 ≈ 2.7s of recursive postDelayed wakeups while waiting for
        // proxy groups to load. 400ms x 6 ≈ 2.4s but with ~2.5x fewer wakeups.
        if (profileAdapter.hasProxyGroupsFor(profile) || attempt >= 6) {
            profileAdapter.showProxySheet(context, profile)
            return
        }
        binding.profileList.postDelayed({
            openProxySheetWhenReady(profile, attempt + 1)
        }, 400L)
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                profileEditRequests.trySend(profile)
            }
        }
    }

    fun updateElapsed() {
        profileAdapter.updateElapsed()
    }

    suspend fun showAbout(
        versionName: String,
        coreVersion: String,
        onCheckUpdates: (((Boolean) -> Unit, (String?) -> Unit) -> Unit)? = null
    ) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
                this.coreVersion = coreVersion
                runCatching {
                    aboutAppIcon.setImageDrawable(context.packageManager.getApplicationIcon(context.packageName))
                }
            }
            val dialog = AppBottomSheetDialog(context, fitContentHeight = true)
            dialog.setContentView(binding.root)

            binding.aboutGithubIcon.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    runCatching {
                        val url = context.getString(R.string.clashfest_repo_url)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            val support = uiStore.supportUrl.takeIf { it.isNotBlank() }
            if (support != null) {
                binding.aboutSupportButton.apply {
                    visibility = View.VISIBLE
                    text = context.getString(R.string.about_support)
                    icon = null
                    setOnClickListener {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(support))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }

            if (onCheckUpdates != null) {
                binding.aboutCheckUpdatesButton.apply {
                    visibility = View.VISIBLE
                    var statusText: String? = null

                    fun render(loading: Boolean) {
                        isEnabled = !loading
                        alpha = if (loading) 0.65f else 1f
                        text = when {
                            loading -> context.getString(R.string.about_checking_updates)
                            !statusText.isNullOrBlank() -> statusText
                            else -> context.getString(R.string.about_check_updates)
                        }
                    }

                    fun setLoading(loading: Boolean) {
                        render(loading)
                    }

                    fun setStatus(text: String?) {
                        statusText = text
                        render(loading = false)
                    }

                    setOnClickListener {
                        if (!isEnabled) return@setOnClickListener
                        statusText = null
                        render(loading = true)
                        onCheckUpdates(::setLoading, ::setStatus)
                    }
                }
            }

            dialog.show()
        }
    }

    private fun showModeSheet() {
        val sheet = BottomSheetMainModeBinding.inflate(context.layoutInflater)
        val dialog = AppBottomSheetDialog(context, fitContentHeight = true)
        val isGlobal = currentModeSegment == TunnelState.Mode.Global

        sheet.modeRuleSelected.visibility = if (isGlobal) View.INVISIBLE else View.VISIBLE
        sheet.modeGlobalSelected.visibility = if (isGlobal) View.VISIBLE else View.INVISIBLE
        sheet.modeRuleRow.setOnClickListener {
            requests.trySend(Request.PatchModeRule)
            dialog.dismiss()
        }
        sheet.modeGlobalRow.setOnClickListener {
            requests.trySend(Request.PatchModeGlobal)
            dialog.dismiss()
        }

        dialog.setContentView(sheet.root)
        dialog.show()
    }

    private fun setupMainPager() {
        val pages = listOf(
            binding.mainHomePage,
            binding.mainProfilesPage,
            binding.mainRoutingPage,
            binding.mainSettingsPage,
        )
        pages.forEach { page ->
            (page.parent as? ViewGroup)?.removeView(page)
            page.visibility = View.VISIBLE
        }

        binding.mainPager.adapter = StaticPageAdapter(pages)
        binding.mainPager.offscreenPageLimit = pages.lastIndex
        binding.mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return
                val count = MainTab.values().size
                when (binding.mainPager.currentItem) {
                    0 -> binding.mainPager.setCurrentItem(count, false)
                    count + 1 -> binding.mainPager.setCurrentItem(1, false)
                }
            }

            override fun onPageSelected(position: Int) {
                mainTabForPagerPosition(position)?.let(::renderMainTab)
            }
        })
        binding.mainPager.setCurrentItem(1, false)
        renderMainTab(MainTab.Home)
        binding.mainPager.post { tweakViewPagerHorizontalSwipeTolerance(binding.mainPager) }
    }

    /**
     * Nested home content (announcement + profiles) grows vertically; on narrow or very wide
     * aspect ratios, small diagonal motion while scrolling can be absorbed as a horizontal
     * page swipe. Raising RecyclerView touch slop reduces accidental tab changes.
     */
    private fun tweakViewPagerHorizontalSwipeTolerance(viewPager: ViewPager2) {
        runCatching {
            val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
            val field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            field.isAccessible = true
            val slop = field.getInt(recyclerView)
            field.setInt(recyclerView, slop * 3)
        }.onFailure {
            Log.w("ViewPager2 touch slop tweak skipped: ${it.message}")
        }
    }

    private fun mainTabForPagerPosition(position: Int): MainTab? {
        val tabs = MainTab.values()
        val size = tabs.size
        val logical = when (position) {
            0 -> size - 1
            size + 1 -> 0
            else -> position - 1
        }
        return tabs.getOrNull(logical)
    }

    /**
     * Toggle the in-header update indicator. MainActivity polls
     * [AppUpdateChecker.isUpdateAvailable] from onResume and from background
     * opportunistic checks, then calls this to reflect the result.
     */
    fun setUpdateBadgeVisible(visible: Boolean) {
        binding.mainHeaderUpdateBadge.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun selectMainTab(tab: MainTab) {
        val targetItem = tab.ordinal + 1
        if (binding.mainPager.currentItem == targetItem) {
            pageForMainTab(tab).smoothScrollTo(0, 0)
            return
        }
        binding.mainPager.setCurrentItem(targetItem, true)
    }

    private fun renderMainTab(tab: MainTab) {
        MainTab.values().forEach {
            navForMainTab(it).isSelected = it == tab
        }
    }

    private fun pageForMainTab(tab: MainTab) = when (tab) {
        MainTab.Home -> binding.mainHomePage
        MainTab.Profiles -> binding.mainProfilesPage
        MainTab.Routing -> binding.mainRoutingPage
        MainTab.Settings -> binding.mainSettingsPage
    }

    private fun navForMainTab(tab: MainTab): View = when (tab) {
        MainTab.Home -> binding.mainNavHome
        MainTab.Profiles -> binding.mainNavProfiles
        MainTab.Routing -> binding.mainNavRouting
        MainTab.Settings -> binding.mainNavSettings
    }

    init {
        binding.self = this
        binding.tunnelStarting = false
        binding.hasProfiles = false
        applyHomeBackgroundStyle()
        setupMainPager()

        binding.profileList.also {
            it.applyLinearAdapter(context, profileAdapter)
            (it.layoutManager as? LinearLayoutManager)?.stackFromEnd = false
            it.setHasFixedSize(true)
            // Change-animations were running on every traffic tick across all visible cards
            // (~2–8s cadence) and added measurable GPU/CPU load. Add/remove animations are kept,
            // but per-item rebind animations are disabled.
            it.itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                changeDuration = 0
                moveDuration = 0
            }
        }

        val card = binding.mainPowerCard
        card.setOnClickListener {
            requests.trySend(Request.ToggleStatus)
        }
        card.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
            }
            false
        }

        binding.tunnelStarting = false
        applyPowerVisuals()

        binding.mainNavHome.setOnClickListener { selectMainTab(MainTab.Home) }
        binding.mainNavProfiles.setOnClickListener { selectMainTab(MainTab.Profiles) }
        binding.mainNavRouting.setOnClickListener { selectMainTab(MainTab.Routing) }
        binding.mainNavSettings.setOnClickListener { selectMainTab(MainTab.Settings) }
        binding.mainHeaderUpdateBadge.setOnClickListener { onUpdateBadgeTap?.invoke() }
        binding.mainModeRow.setOnClickListener { showModeSheet() }
        binding.mainActiveProfileUpdate.setOnClickListener {
            activeProfileForQuickActions?.let { profile ->
                if (profile.imported && profile.type != Profile.Type.File) {
                    profileForceUpdateRequests.trySend(profile)
                }
            }
        }
        binding.mainActiveProfileSupport.setOnClickListener {
            val target = resolveSupportUrl() ?: return@setOnClickListener
            activeAnnouncementOnOpenUrl?.invoke(target) ?: activeAnnouncementOnSupport?.invoke()
        }
    }

    private fun applyHomeBackgroundStyle() {
        when (uiStore.homeBackgroundStyle) {
            HomeBackgroundStyle.MaterialYou -> binding.root.setBackgroundColor(
                context.resolveThemedColor(com.google.android.material.R.attr.colorSurface),
            )
            HomeBackgroundStyle.Plain -> binding.root.setBackgroundColor(
                context.resolveThemedColor(android.R.attr.colorBackground),
            )
            HomeBackgroundStyle.Preview -> binding.root.setBackgroundResource(
                context.resolveThemedResourceId(R.attr.mainDashboardBackground),
            )
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
