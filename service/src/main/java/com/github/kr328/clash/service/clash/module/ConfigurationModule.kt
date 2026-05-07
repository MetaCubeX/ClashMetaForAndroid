package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.GeoUrlSanitizer
import com.github.kr328.clash.service.util.ProxyDialerYamlEdit
import com.github.kr328.clash.service.util.ProxyGroupsYamlEdit
import com.github.kr328.clash.service.util.ProxyHardener
import com.github.kr328.clash.service.util.ensureBundledGeoAssets
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
    data class LoadException(val message: String)

    private fun fullThrowableMessage(e: Throwable): String = buildString {
        var x: Throwable? = e
        while (x != null) {
            append(x.message)
            append('\n')
            x = x.cause
        }
    }

    /** Mihomo [validateDialerProxies] — stale names after subscription / merged-group edits break [Clash.load]. */
    private fun isDialerProxyValidationFailure(e: Throwable): Boolean {
        val msg = fullThrowableMessage(e).lowercase()
        if (!msg.contains("dialer-proxy")) return false
        return msg.contains("not found") || msg.contains("circular")
    }

    /**
     * [outboundgroup.getProxies]/getProviders — stale member names in merged `proxy-groups` after
     * subscription update ([SubscriptionUpdateMerge]).
     */
    private fun extractQuotedNotFoundName(e: Throwable): String? {
        val re = Regex("'([^']*)'\\s+not\\s+found", RegexOption.IGNORE_CASE)
        return re.find(fullThrowableMessage(e))?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isProxyGroupStaleReferenceFailure(e: Throwable): Boolean {
        val msg = fullThrowableMessage(e).lowercase()
        if (!msg.contains("proxy group[")) return false
        return extractQuotedNotFoundName(e) != null
    }

    private val store = ServiceStore(service)
    private val reload = Channel<Unit>(Channel.CONFLATED)

    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_PROFILE_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
        }

        var loaded: UUID? = null

        reload.trySend(Unit)

        while (true) {
            var changed: UUID? = select {
                broadcasts.onReceive {
                    if (it.action == Intents.ACTION_PROFILE_CHANGED)
                        UUID.fromString(it.getStringExtra(Intents.EXTRA_UUID))
                    else
                        null
                }
                reload.onReceive {
                    null
                }
            }

            // Coalesce a burst of profile/override broadcasts within ~500ms without
            // unconditionally sleeping the full window - sleeps efficiently and exits early
            // once the storm settles, instead of `delay(500)` + busy `tryReceive`.
            val deadline = System.currentTimeMillis() + 500
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                val pending = withTimeoutOrNull(remaining) { broadcasts.receive() } ?: break
                changed = if (pending.action == Intents.ACTION_PROFILE_CHANGED)
                    UUID.fromString(pending.getStringExtra(Intents.EXTRA_UUID))
                else
                    null
            }
            while (reload.tryReceive().isSuccess) {
                changed = null
            }

            try {
                val current = store.activeProfile
                    ?: throw NullPointerException("No profile selected")

                if (current == loaded && changed != null && changed != loaded)
                    continue

                val active = ImportedDao().queryByUUID(current)
                    ?: throw NullPointerException("No profile selected")

                val profileDir = service.importedDir.resolve(active.uuid.toString())

                suspend fun applyPostLoad() {
                    val staleProviderKeySelections = SelectionDao().querySelections(active.uuid)
                        .filter { it.selected.matches(Regex("^sub\\d+$", RegexOption.IGNORE_CASE)) }
                    for (s in staleProviderKeySelections) {
                        SelectionDao().removeSelected(s.uuid, s.proxy)
                    }

                    val sessionOverride = Clash.queryOverride(Clash.OverrideSlot.Session)
                    val hardened = ProxyHardener.applyTo(
                        configuration = sessionOverride,
                        mode = store.proxyHardeningMode,
                        seedGeoMirrors = store.seedDefaultGeoMirrors,
                    )
                    if (hardened) {
                        Clash.patchOverride(Clash.OverrideSlot.Session, sessionOverride)
                    }

                    val remove = SelectionDao().querySelections(active.uuid)
                        .filterNot { Clash.patchSelector(it.proxy, it.selected) }
                        .map { it.proxy }

                    SelectionDao().removeSelections(active.uuid, remove)

                    StatusProvider.currentProfile = active.name

                    service.sendProfileLoaded(current)
                    loaded = current

                    Log.d("Active profile loaded")
                }

                var dialerRecoveryAttempted = false
                var loadFailures = 0
                while (true) {
                    try {
                        GeoUrlSanitizer.sanitizeProfile(profileDir)
                        service.ensureBundledGeoAssets()
                        Clash.load(profileDir).await()
                        applyPostLoad()
                        break
                    } catch (e: Exception) {
                        loadFailures++
                        if (loadFailures > 48) {
                            Log.e("Profile load: recovery limit exceeded", e)
                            if (loaded == null) {
                                return enqueueEvent(LoadException(e.message ?: "Unknown"))
                            }
                            break
                        }
                        when {
                            !dialerRecoveryAttempted &&
                                isDialerProxyValidationFailure(e) &&
                                ProxyDialerYamlEdit.clearAllDialerProxies(profileDir) -> {
                                dialerRecoveryAttempted = true
                                Log.w(
                                    "Invalid dialer-proxy in YAML (e.g. renamed nodes); stripped all dialer-proxy and retrying load",
                                )
                            }
                            isProxyGroupStaleReferenceFailure(e) -> {
                                val stale = extractQuotedNotFoundName(e)
                                if (stale != null &&
                                    ProxyGroupsYamlEdit.removeStaleNameFromAllProxyGroups(profileDir, stale)
                                ) {
                                    Log.w(
                                        "Stale proxy-group reference removed from config.yaml; retrying load (name omitted)",
                                    )
                                } else {
                                    Log.e("Failed to load active profile, keeping runtime alive", e)
                                    if (loaded == null) {
                                        return enqueueEvent(LoadException(e.message ?: "Unknown"))
                                    }
                                    break
                                }
                            }
                            else -> {
                                Log.e("Failed to load active profile, keeping runtime alive", e)
                                if (loaded == null) {
                                    return enqueueEvent(LoadException(e.message ?: "Unknown"))
                                }
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Failed to load active profile, keeping runtime alive", e)
                if (loaded == null) {
                    return enqueueEvent(LoadException(e.message ?: "Unknown"))
                }
            }
        }
    }
}
