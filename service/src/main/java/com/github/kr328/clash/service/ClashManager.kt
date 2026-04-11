package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.data.Selection
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.RuntimeSocksAuth
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel

class ClashManager(private val context: Context) : IClashManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    override fun queryTunnelState(): TunnelState {
        return Clash.queryTunnelState()
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
    }

    override fun queryConnectionsSnapshot(): String {
        return Clash.queryConnectionsSnapshot()
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    /**
     * In **Global** mode all traffic uses the [GLOBAL] adapter only. Changing a leaf inside
     * another group (e.g. MainGroup → Sweden) has no effect until [GLOBAL]’s active child is that
     * group — otherwise the engine keeps routing through whatever [GLOBAL] had selected (often the
     * first subscription). When the user picks a node in a non-GLOBAL group that is listed under
     * GLOBAL, we first patch GLOBAL to that group, then patch the leaf.
     */
    override fun patchSelector(group: String, name: String): Boolean {
        var syncedGlobal = false
        if (group != "GLOBAL") {
            val state = Clash.queryTunnelState()
            if (state.mode == TunnelState.Mode.Global) {
                val global = Clash.queryGroup("GLOBAL", ProxySort.Default)
                val children = global.proxies.map { it.name }.toSet()
                if (group in children) {
                    syncedGlobal = Clash.patchSelector("GLOBAL", group)
                }
            }
        }
        val ok = Clash.patchSelector(group, name)
        val current = store.activeProfile ?: return ok
        if (ok) {
            SelectionDao().setSelected(Selection(current, group, name))
            if (syncedGlobal) {
                SelectionDao().setSelected(Selection(current, "GLOBAL", group))
            }
        } else {
            SelectionDao().removeSelected(current, group)
        }
        return ok
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        if (slot == Clash.OverrideSlot.Session) {
            RuntimeSocksAuth.applyTo(configuration)
        }
        Clash.patchOverride(slot, configuration)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override suspend fun healthCheck(group: String) {
        return Clash.healthCheck(group).await()
    }

    override fun healthCheckAll() {
        Clash.healthCheckAll()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver = Clash.subscribeLogcat().also { c ->
                    launch {
                        try {
                            while (isActive) {
                                observer.newItem(c.receive())
                            }
                        } catch (e: CancellationException) {
                            // intended behavior
                            // ignore
                        } catch (e: Exception) {
                            Log.w("UI crashed", e)
                        } finally {
                            withContext(NonCancellable) {
                                c.cancel()

                                Clash.forceGc()
                            }
                        }
                    }
                }
            }
        }
    }
}