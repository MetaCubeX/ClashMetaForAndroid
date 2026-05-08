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
import com.github.kr328.clash.service.util.ProxyHardener
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

    override fun closeConnection(id: String): Boolean {
        return Clash.closeConnection(id)
    }

    override fun closeAllConnections(): Int {
        return Clash.closeAllConnections()
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        val ok = Clash.patchSelector(group, name)
        val current = store.activeProfile
        if (!ok) {
            current?.let { SelectionDao().removeSelected(it, group) }

            return false
        }

        current?.let { SelectionDao().setSelected(Selection(it, group, name)) }
        syncSelectorAncestors(current, group)

        return ok
    }

    private fun syncSelectorAncestors(current: java.util.UUID?, selectedGroup: String) {
        if (selectedGroup.isBlank() || selectedGroup == "GLOBAL") return

        val groupNames = runCatching { Clash.queryGroupNames(false) }
            .getOrDefault(emptyList())
        val queriedGroups = linkedMapOf<String, ProxyGroup>()

        fun queryGroup(name: String): ProxyGroup? {
            queriedGroups[name]?.let { return it }
            return runCatching { Clash.queryGroup(name, ProxySort.Default) }
                .getOrNull()
                ?.also { queriedGroups[name] = it }
        }

        val visited = linkedSetOf<String>()
        var child = selectedGroup

        while (visited.add(child)) {
            val parent = groupNames.firstNotNullOfOrNull { candidate ->
                val parentGroup = queryGroup(candidate)
                if (candidate == "GLOBAL" || candidate == child || parentGroup == null) {
                    null
                } else if (parentGroup.proxies.none { it.name == child }) {
                    null
                } else {
                    candidate to parentGroup
                }
            } ?: break

            val (parentName, parentGroup) = parent
            if (parentGroup.type == Proxy.Type.Selector) {
                if (parentGroup.now != child && !Clash.patchSelector(parentName, child)) break
                current?.let { SelectionDao().setSelected(Selection(it, parentName, child)) }
            }
            child = parentName
        }

        val state = runCatching { Clash.queryTunnelState() }.getOrNull()
        if (state?.mode != TunnelState.Mode.Global) return

        val global = runCatching { Clash.queryGroup("GLOBAL", ProxySort.Default) }.getOrNull()
            ?: return
        if (global.proxies.none { it.name == child } || global.now == child) return
        if (Clash.patchSelector("GLOBAL", child)) {
            current?.let { SelectionDao().setSelected(Selection(it, "GLOBAL", child)) }
        }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        if (slot == Clash.OverrideSlot.Session) {
            ProxyHardener.applyTo(
                configuration = configuration,
                mode = store.proxyHardeningMode,
                seedGeoMirrors = store.seedDefaultGeoMirrors,
            )
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
