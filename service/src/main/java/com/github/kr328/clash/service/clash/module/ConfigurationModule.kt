package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ProxyDialerYamlEdit
import com.github.kr328.clash.service.util.RuntimeSocksAuth
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.*

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
    data class LoadException(val message: String)

    /** Mihomo [validateDialerProxies] — stale names after subscription / merged-group edits break [Clash.load]. */
    private fun isDialerProxyValidationFailure(e: Throwable): Boolean {
        val msg = buildString {
            var x: Throwable? = e
            while (x != null) {
                append(x.message)
                append('\n')
                x = x.cause
            }
        }.lowercase()
        if (!msg.contains("dialer-proxy")) return false
        return msg.contains("not found") || msg.contains("circular")
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
            val changed: UUID? = select {
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
                    if (RuntimeSocksAuth.applyTo(sessionOverride)) {
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

                try {
                    Clash.load(profileDir).await()
                    applyPostLoad()
                } catch (e: Exception) {
                    if (loaded == null && isDialerProxyValidationFailure(e) &&
                        ProxyDialerYamlEdit.clearAllDialerProxies(profileDir)
                    ) {
                        Log.w(
                            "Invalid dialer-proxy in YAML (e.g. renamed nodes); stripped all dialer-proxy and retrying load",
                        )
                        try {
                            Clash.load(profileDir).await()
                            applyPostLoad()
                        } catch (e2: Exception) {
                            Log.e("Failed to load profile after dialer-proxy recovery", e2)
                            return enqueueEvent(LoadException(e2.message ?: "Unknown"))
                        }
                    } else {
                        Log.e("Failed to load active profile, keeping runtime alive", e)
                        if (loaded == null) {
                            return enqueueEvent(LoadException(e.message ?: "Unknown"))
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