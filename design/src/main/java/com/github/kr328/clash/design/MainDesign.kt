package com.github.kr328.clash.design

import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DefaultItemAnimator
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.adapter.ProfileAdapter
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
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.util.UUID

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenNewProfile,
        OpenSettings,
        OpenAbout,
        PatchModeDirect,
        PatchModeGlobal,
        PatchModeRule,
        OpenImportClipboard,
        OpenImportQr,
        CycleTheme,
        OpenLogs,
        OpenConnections,
        /** Hub screen combining YAML rules, routing rules and per-app routing. */
        OpenRouting,
        /** Subscriptions / profiles list. */
        OpenProfiles,
    }

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
     * Pushes operator-pushed subscription metadata into the per-profile UI.
     *
     * The legacy global "Koala-style" announcement card is now always hidden — operators'
     * announcement text is rendered inline on the active profile card instead, so it stays
     * tied to the subscription it belongs to.
     */
    suspend fun setAnnouncement(
        text: String?,
        url: String?,
        usage: com.github.kr328.clash.common.util.SubscriptionUsage? = null,
        supportUrl: String? = null,
        onOpenUrl: ((String) -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
        onRefresh: (() -> Unit)? = null,
        onSupport: (() -> Unit)? = null,
    ) {
        withContext(Dispatchers.Main) {
            val message = text?.trim().orEmpty()
            val announcementUrl = url?.takeIf { it.isNotBlank() }
            val support = supportUrl?.takeIf { it.isNotBlank() }
            val hasCardContent = message.isNotBlank() || announcementUrl != null || support != null

            binding.mainAnnouncementCard.visibility = if (hasCardContent) View.VISIBLE else View.GONE
            binding.mainAnnouncementText.visibility = if (message.isNotBlank()) View.VISIBLE else View.GONE
            binding.mainAnnouncementText.text = message

            binding.mainAnnouncementStatsRow.visibility = View.GONE
            binding.mainAnnouncementUsageBar.visibility = View.GONE
            binding.mainAnnouncementUsageText.visibility = View.GONE
            binding.mainAnnouncementUnavailableRow.visibility = View.GONE

            binding.mainAnnouncementOpenLink.visibility = if (announcementUrl != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementOpenLink.setOnClickListener {
                val target = announcementUrl ?: return@setOnClickListener
                onOpenUrl?.invoke(target)
            }

            binding.mainAnnouncementSupport.visibility = if (support != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementSupport.setOnClickListener {
                val target = support ?: return@setOnClickListener
                onOpenUrl?.invoke(target) ?: onSupport?.invoke()
            }

            binding.mainAnnouncementRefresh.visibility = if (onRefresh != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementRefresh.setOnClickListener { onRefresh?.invoke() }

            binding.mainAnnouncementDismiss.visibility = if (onDismiss != null) View.VISIBLE else View.GONE
            binding.mainAnnouncementDismiss.setOnClickListener {
                binding.mainAnnouncementCard.visibility = View.GONE
                onDismiss?.invoke()
            }

            profileAdapter.setActiveAnnouncement(
                text = text,
                url = url,
                supportUrl = supportUrl,
                onOpenUrl = onOpenUrl,
                onSupport = onSupport,
            )
            // Usage is currently rendered by profile cards; keep signature for compatibility.
            usage?.let { /* kept for API stability; per-profile usage is rendered from Profile fields */ }
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
        button.elevation = elevationDp * context.resources.displayMetrics.density
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
            updateModeSegment(normalized, animate = true)
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

    private fun updateModeSegment(mode: TunnelState.Mode, animate: Boolean) {
        val container = binding.modeSegmentContainer
        val thumb = binding.modeSegmentThumb
        if (container.width == 0) {
            container.post { updateModeSegment(mode, animate = false) }
            return
        }

        val inset = container.paddingStart + container.paddingEnd
        val slotWidth = ((container.width - inset) / 2).coerceAtLeast(1)

        val lp = (thumb.layoutParams as FrameLayout.LayoutParams).apply {
            width = slotWidth
        }
        thumb.layoutParams = lp

        val targetX = if (mode == TunnelState.Mode.Global) slotWidth.toFloat() else 0f
        thumb.animate().cancel()
        thumb.animate()
            .translationX(targetX)
            .setDuration(if (animate) 160L else 0L)
            .start()

        val selectedColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val normalColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val isRule = mode != TunnelState.Mode.Global
        binding.modeLabelRule.setTextColor(if (isRule) selectedColor else normalColor)
        binding.modeLabelGlobal.setTextColor(if (isRule) normalColor else selectedColor)
    }

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
            profileAdapter.apply {
                val ids = profiles.map { it.uuid }.toSet()
                expandedProfileUuids.retainAll { it in ids }
                patchDataSet(this::profiles, profiles, id = { it.uuid })
            }
            profileAdapter.setExpandedUuids(expandedProfileUuids.toSet())
        }
    }

    suspend fun patchProxyGroups(
        names: List<String>,
        running: Boolean,
        mode: TunnelState.Mode?,
        lastGroupHint: String?,
        offlinePreviewByProfile: Map<UUID, Map<String, List<String>>> = emptyMap(),
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
        val expanded = expandedProfileUuids.add(profile.uuid)
        if (!expanded) {
            expandedProfileUuids.remove(profile.uuid)
        }
        profileAdapter.setExpandedUuids(expandedProfileUuids.toSet())
        if (expanded && clashRunningState && profile.active) {
            binding.profileList.postDelayed({
                val distance = (binding.profileList.height * 0.36f).toInt().coerceAtLeast(120)
                binding.profileList.smoothScrollBy(0, distance)
            }, 120L)
        }
        profileExpandChanged.trySend(Unit)
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

            if (onCheckUpdates != null) {
                binding.aboutSupportButton.apply {
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

    init {
        binding.self = this
        binding.tunnelStarting = false
        binding.hasProfiles = false
        applyHomeBackgroundStyle()

        binding.profileList.also {
            it.applyLinearAdapter(context, profileAdapter)
            (it.layoutManager as? LinearLayoutManager)?.stackFromEnd = true
            it.itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = true
                changeDuration = 280
                moveDuration = 220
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

        binding.modeLabelRule.setOnClickListener {
            requests.trySend(Request.PatchModeRule)
        }
        binding.modeLabelGlobal.setOnClickListener {
            requests.trySend(Request.PatchModeGlobal)
        }
        binding.modeSegmentContainer.post {
            updateModeSegment(currentModeSegment, animate = false)
        }

        binding.mainNavProfiles.setOnClickListener { requests.trySend(Request.OpenProfiles) }
        binding.mainNavLogs.setOnClickListener { requests.trySend(Request.OpenSettings) }
        binding.mainNavRouting.setOnClickListener { requests.trySend(Request.OpenRouting) }
        binding.mainNavConnections.setOnClickListener { requests.trySend(Request.OpenConnections) }

        val alignFab: () -> Unit = {
            binding.root.post { alignAddProfileFabToProfiles() }
        }
        binding.mainBottomNavCard.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignFab() }
        binding.mainNavProfiles.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignFab() }
        binding.mainAddProfileFab.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignFab() }
        alignFab()
    }

    private fun alignAddProfileFabToProfiles() {
        val fab = binding.mainAddProfileFab
        val profiles = binding.mainNavProfiles
        if (fab.width == 0 || profiles.width == 0) return

        val fabLocation = IntArray(2)
        val profilesLocation = IntArray(2)
        fab.getLocationOnScreen(fabLocation)
        profiles.getLocationOnScreen(profilesLocation)

        val fabCenterX = fabLocation[0] + fab.width / 2f
        val profilesCenterX = profilesLocation[0] + profiles.width / 2f
        val deltaX = profilesCenterX - fabCenterX

        if (kotlin.math.abs(deltaX) > 0.5f) {
            fab.translationX += deltaX
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
