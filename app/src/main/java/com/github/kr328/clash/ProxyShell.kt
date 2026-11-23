package com.github.kr328.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import com.github.kr328.clash.design.R as DesignR

internal class ProxyShell(
    private val host: BaseActivity<*>,
    private val embedded: Boolean,
    private val fallbackGroups: List<String> = emptyList(),
    private val onMounted: suspend (ProxyDesign) -> Unit,
    private val onActivityReplace: (() -> Unit)? = null,
) {
    var page: ProxyDesign? = null
        private set
    private var pendingGroup: String? = null
    private var pendingSection: Int? = null

    val currentGroup: String?
        get() = page?.currentGroupName

    private val reloads = kotlinx.coroutines.channels.Channel<Unit>(
        kotlinx.coroutines.channels.Channel.CONFLATED,
    )

    fun onProfileLoaded() {
        reloads.trySend(Unit)
    }

    fun showGroup(name: String) {
        val current = page
        if (current == null) {
            pendingGroup = name
            pendingSection = 0
        } else {
            current.showGroup(name)
        }
    }

    fun showSection(index: Int) {
        val current = page
        if (current == null) {
            pendingSection = index
        } else {
            current.openSection(index)
        }
    }

    fun reloadGroup(name: String) {
        val current = page
        if (current == null) {
            pendingGroup = name
            pendingSection = 0
        } else {
            current.reloadGroup(name)
        }
    }

    fun refreshProviders() {
        val current = page ?: return
        host.launch {
            try {
                val list = withClash { queryProviders().sorted() }
                if (page === current) current.setProviders(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (page === current) current.setProvidersUnavailable()
            }
        }
    }

    suspend fun run() {
        var prefer: String? = null
        try {
            while (coroutineContext.isActive) {
                val result = runSession(prefer)
                if (!result.replace) return
                if (!embedded) {
                    onActivityReplace?.invoke()
                    return
                }
                prefer = result.prefer
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            page?.cancel()
            page = null
        }
    }

    private data class SessionResult(
        val replace: Boolean,
        val prefer: String?,
    )

    private suspend fun runSession(prefer: String?): SessionResult {
        val previous = page
        page = null
        var leftover: ProxyDesign? = previous
        try {
            val store = host.shellUiStore()
            val names = try {
                withClash { queryProxyGroupNames(store.proxyExcludeNotSelectable) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("queryProxyGroupNames: ${e.message}", e)
                emptyList()
            }
            val chosen = pendingGroup ?: prefer
            val section = pendingSection
            pendingGroup = null
            pendingSection = null
            val states = List(names.size) { ProxyState("") }
            val unordered = names.indices.associate { names[it] to states[it] }
            val reloadLock = Semaphore(10)
            val reloadSeq = IntArray(names.size)
            val selectSeq = IntArray(names.size)
            val clearOnTicket = IntArray(names.size)
            val seqLock = Mutex()
            val index = initialIndex(names, chosen)
            val design = ProxyDesign(
                host,
                names,
                index,
                store,
                states,
                onUserGroup = { group ->
                    store.proxyLastGroup = group
                    if (!embedded) {
                        host.intent.putExtra(ProxyActivity.EXTRA_PROXY_GROUP, group)
                    }
                },
                embedded = embedded,
            )
            page = design
            onMounted(design)
            section?.let { design.openSection(it) }
            leftover?.cancel()
            leftover = null
            try {
                design.setProviders(withClash { queryProviders().sorted() })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                design.setProvidersUnavailable()
            }
            if (index >= 0) {
                design.requests.trySend(ProxyDesign.Request.Reload(index))
            }
            return collect(design, names, states, unordered, reloadLock, reloadSeq, selectSeq, clearOnTicket, seqLock, store)
        } finally {
            leftover?.cancel()
        }
    }

    private suspend fun collect(
        design: ProxyDesign,
        names: List<String>,
        states: List<ProxyState>,
        unordered: Map<String, ProxyState>,
        reloadLock: Semaphore,
        reloadSeq: IntArray,
        selectSeq: IntArray,
        clearOnTicket: IntArray,
        seqLock: Mutex,
        store: com.github.kr328.clash.design.store.UiStore,
    ): SessionResult = coroutineScope {
        val providerTicker = ticker(TimeUnit.MINUTES.toMillis(1))
        var replace = false
        var nextGroup: String? = null
        val session = this

        fun launchRequest(block: suspend () -> Unit) {
            session.launch {
                try {
                    confineProxyRequest(block) { error ->
                        design.showExceptionToast(error)
                    }
                } catch (e: CancellationException) {
                    val child = coroutineContext.job
                    if (child.isActive && session.coroutineContext.job.isActive) {
                        session.coroutineContext.job.cancel(e)
                    }
                    throw e
                }
            }
        }

        suspend fun startQuery(groupIndex: Int, fromUrlTest: Boolean) {
            val (ticket, selectAtStart) = seqLock.withLock {
                val next = ++reloadSeq[groupIndex]
                if (fromUrlTest) clearOnTicket[groupIndex] = next
                next to selectSeq[groupIndex]
            }
            val group = reloadLock.withPermit {
                withClash { queryProxyGroup(names[groupIndex], store.proxySort) }
            }
            val clearUrlTesting: Boolean = seqLock.withLock<Boolean?> {
                if (reloadSeq[groupIndex] != ticket) return@withLock null
                if (selectSeq[groupIndex] == selectAtStart) {
                    states[groupIndex].now = group.now
                }
                val clear = clearOnTicket[groupIndex] == ticket
                if (clear) clearOnTicket[groupIndex] = 0
                clear
            } ?: return
            design.updateGroup(
                groupIndex,
                group.proxies,
                group.type == "Selector",
                states[groupIndex],
                unordered,
                group.type,
                clearUrlTesting,
            )
        }

        while (isActive && !replace) {
            select<Unit> {
                if (embedded) {
                    reloads.onReceive {
                        val latest = try {
                            withClash { queryProxyGroupNames(store.proxyExcludeNotSelectable) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("queryProxyGroupNames: ${e.message}", e)
                            names
                        }
                        if (latest != names) {
                            replace = true
                            nextGroup = design.currentGroupName?.takeIf { it in latest }
                                ?: latest.firstOrNull()
                        }
                    }
                } else {
                    host.shellEvents().onReceive {
                        when (it) {
                            BaseActivity.Event.ProfileLoaded -> {
                                val latest = withClash {
                                    queryProxyGroupNames(store.proxyExcludeNotSelectable)
                                }
                                if (latest != names) {
                                    replace = true
                                    nextGroup = design.currentGroupName?.takeIf { it in latest }
                                        ?: latest.firstOrNull()
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> {
                            replace = true
                            nextGroup = design.currentGroupName
                        }
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                design.requests.trySend(ProxyDesign.Request.Reload(idx))
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            if (it.index !in names.indices) return@onReceive
                            launchRequest { startQuery(it.index, fromUrlTest = false) }
                        }
                        is ProxyDesign.Request.Select -> {
                            if (it.index !in names.indices) return@onReceive
                            seqLock.withLock { selectSeq[it.index]++ }
                            confineProxyRequest(
                                request = {
                                    withClash {
                                        patchSelector(names[it.index], it.name)
                                        states[it.index].now = it.name
                                    }
                                    design.requestRedrawVisible()
                                },
                                onFailure = { error -> design.showExceptionToast(error) },
                            )
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            if (it.index !in names.indices) return@onReceive
                            launchRequest {
                                withClash { healthCheck(names[it.index]) }
                                startQuery(it.index, fromUrlTest = true)
                            }
                        }
                        is ProxyDesign.Request.UpdateProvider -> {
                            launch {
                                try {
                                    withClash { updateProvider(it.provider.type, it.provider.name) }
                                    design.notifyProviderChanged(it.index)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    design.showExceptionToast(
                                        host.getString(
                                            DesignR.string.format_update_provider_failure,
                                            it.provider.name,
                                            e.message,
                                        ),
                                    )
                                    design.notifyProviderUpdated(it.index)
                                }
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            design.showModeSwitchTips()
                            confineProxyRequest(
                                request = {
                                    withClash {
                                        val override = queryOverride(Clash.OverrideSlot.Session)
                                        override.mode = it.mode
                                        patchOverride(Clash.OverrideSlot.Session, override)
                                    }
                                },
                                onFailure = { error -> design.showExceptionToast(error) },
                            )
                        }
                    }
                }
                if (host.shellStarted()) {
                    providerTicker.onReceive {
                        design.updateProviderElapsed()
                    }
                }
            }
        }
        if (replace) coroutineContext.job.cancelChildren()
        SessionResult(replace, nextGroup)
    }

    private fun initialIndex(names: List<String>, prefer: String?): Int {
        if (names.isEmpty()) return -1
        val candidates = listOfNotNull(prefer?.takeIf { it.isNotEmpty() }) + fallbackGroups
        for (name in candidates) {
            val index = names.indexOf(name)
            if (index >= 0) return index
        }
        val last = names.indexOf(host.shellUiStore().proxyLastGroup)
        if (last >= 0) return last
        return 0
    }
}
