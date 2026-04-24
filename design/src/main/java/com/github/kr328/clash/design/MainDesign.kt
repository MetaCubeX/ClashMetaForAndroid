package com.github.kr328.clash.design

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.floor
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
    private var mainPowerConnectRevealAnim: ValueAnimator? = null
    private var lastPowerVisualMode: Int = -1 // 0=off, 1=starting, 2=running
    private val mainPowerOffIconResId: Int by lazy { resolveRuntimeDrawable("proxy_off") }
    private val mainPowerOnIconResId: Int by lazy { resolveRuntimeDrawable("proxy_on") }

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
            // Hide the legacy global card unconditionally.
            binding.mainAnnouncementCard.visibility = android.view.View.GONE

            profileAdapter.setActiveAnnouncement(
                text = text,
                url = url,
                supportUrl = supportUrl,
                onOpenUrl = onOpenUrl,
                onSupport = onSupport,
            )
            // Mark unused parameters as intentionally consumed.
            usage?.let { /* kept for API stability; per-profile usage is rendered from Profile fields */ }
            onDismiss?.let { /* dismiss has no place in the inline UI */ }
            onRefresh?.let { /* refresh is handled via the existing per-profile force-update icon */ }
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
        val running = clashRunningState
        val starting = tunnelStartingState && !running
        val mode = when {
            starting -> 1
            running -> 2
            else -> 0
        }

        // Do not restart the same animation/visual on repeated state patches.
        if (mode == lastPowerVisualMode) return
        lastPowerVisualMode = mode

        binding.mainPowerGlow.animate().cancel()
        mainPowerConnectRevealAnim?.cancel()
        mainPowerConnectRevealAnim = null

        when {
            running -> {
                // Keep a short colorization blend (OFF -> ON) even when state flips fast.
                binding.mainPowerIconBase.setImageResource(mainPowerOffIconResId)
                binding.mainPowerIconOverlay.setImageResource(mainPowerOnIconResId)
                binding.mainPowerIconOverlay.visibility = View.VISIBLE
                binding.mainPowerIconOverlay.alpha = 0f
                mainPowerConnectRevealAnim = startDiscreteColorize(
                    durationMs = 360L,
                    steps = 10
                ) {
                        binding.mainPowerIconBase.setImageResource(mainPowerOnIconResId)
                        binding.mainPowerIconOverlay.visibility = View.GONE
                        binding.mainPowerIconOverlay.alpha = 0f
                    }

                // No persistent ring when active.
                binding.mainPowerGlow.alpha = 0f
                binding.mainPowerGlow.scaleX = 1f
                binding.mainPowerGlow.scaleY = 1f
            }
            starting -> {
                // Colorize effect: full-size ON layer fades in over OFF, while center glow simulates ignition.
                binding.mainPowerIconBase.setImageResource(mainPowerOffIconResId)
                binding.mainPowerIconOverlay.setImageResource(mainPowerOnIconResId)
                binding.mainPowerIconOverlay.visibility = View.VISIBLE
                binding.mainPowerIconOverlay.alpha = 0f
                mainPowerConnectRevealAnim = startDiscreteColorize(durationMs = 560L, steps = 10)

                // Ignite glow only during connecting transition.
                binding.mainPowerGlow.alpha = 0f
                binding.mainPowerGlow.scaleX = 0.24f
                binding.mainPowerGlow.scaleY = 0.24f
                binding.mainPowerGlow.animate()
                    .alpha(0.62f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(560L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            else -> {
                binding.mainPowerIconBase.setImageResource(mainPowerOffIconResId)
                binding.mainPowerIconOverlay.visibility = View.GONE
                binding.mainPowerIconOverlay.alpha = 0f

                // Fully quiet glow in OFF state.
                binding.mainPowerGlow.alpha = 0f
                binding.mainPowerGlow.scaleX = 0.5f
                binding.mainPowerGlow.scaleY = 0.5f
            }
        }
    }

    private fun resolveRuntimeDrawable(name: String): Int {
        val runtime = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (runtime != 0) runtime else R.drawable.ic_clash
    }

    private fun startDiscreteColorize(
        durationMs: Long,
        steps: Int,
        onEnd: (() -> Unit)? = null,
    ): ValueAnimator {
        val safeSteps = steps.coerceIn(2, 16)
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val raw = animator.animatedValue as Float
                val stepped = floor(raw * safeSteps) / safeSteps
                binding.mainPowerIconOverlay.alpha = stepped.coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.mainPowerIconOverlay.alpha = 1f
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            val normalized = normalizeMode(mode)
            binding.mode = when (normalized) {
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                else -> context.getString(R.string.rule_mode)
            }

            val checkedId = when (normalized) {
                TunnelState.Mode.Global -> R.id.btn_mode_global
                else -> R.id.btn_mode_rule
            }
            if (binding.modeToggleGroup.checkedButtonId != checkedId) {
                binding.modeToggleGroup.check(checkedId)
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

    init {
        binding.self = this
        binding.tunnelStarting = false
        binding.hasProfiles = false
        applyHomeBackgroundStyle()

        binding.profileList.also {
            it.applyLinearAdapter(context, profileAdapter)
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
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
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

        binding.btnModeRule.setOnClickListener {
            requests.trySend(Request.PatchModeRule)
        }
        binding.btnModeGlobal.setOnClickListener {
            requests.trySend(Request.PatchModeGlobal)
        }
        binding.mainAddProfileFab.setOnClickListener {
            requests.trySend(Request.ToggleStatus)
        }

        binding.mainNavProfiles.setOnClickListener { requests.trySend(Request.OpenProfiles) }
        binding.mainNavLogs.setOnClickListener { requests.trySend(Request.OpenLogs) }
        binding.mainNavRouting.setOnClickListener { requests.trySend(Request.OpenRouting) }
        binding.mainNavConnections.setOnClickListener { requests.trySend(Request.OpenConnections) }
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
