package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ProxyActivity : BaseActivity<ProxyDesign>() {
    override suspend fun main() {
        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        val names = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        val states = List(names.size) { index -> ProxyState(names[index], "?") }
        val unorderedStates = names.indices.associate { names[it] to states[it] }
        val reloadLock = Semaphore(10)


        val initialIndex = run {
            val last = uiStore.proxyLastGroup
            if (last.isNotEmpty()) names.indexOf(last).takeIf { it >= 0 } ?: 0 else 0
        }


        val prefetched = if (names.isNotEmpty()) coroutineScope {
            names.mapIndexed { idx, name ->
                async {
                    try {
                        reloadLock.withPermit {
                            withClash { queryProxyGroup(name, uiStore.proxySort) }
                        }.also { states[idx].now = it.now }
                    } catch (_: Throwable) {
                        null
                    }
                }
            }.awaitAll()
        } else emptyList()

        val design = ProxyDesign(
            this,
            mode,
            names,
            uiStore
        )


        design.currentPage = initialIndex
        prefetched.forEachIndexed { index, group ->
            if (group != null) {
                design.updateGroup(
                    index,
                    group.proxies,
                    group.type == Proxy.Type.Selector,
                    states[index],
                    unorderedStates
                )
            }
        }

        setContentDesign(design)


        design.requests.trySend(ProxyDesign.Request.ReloadAll)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newNames = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                            }

                            if (newNames != names) {
                                startActivity(ProxyActivity::class.intent)

                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> {
                            startActivity(ProxyActivity::class.intent)

                            finish()
                        }
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                design.requests.trySend(ProxyDesign.Request.Reload(idx))
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            launch {
                                val group = reloadLock.withPermit {
                                    withClash {
                                        queryProxyGroup(names[it.index], uiStore.proxySort)
                                    }
                                }
                                val state = states[it.index]

                                state.now = group.now

                                design.updateGroup(
                                    it.index,
                                    group.proxies,
                                    group.type == Proxy.Type.Selector,
                                    state,
                                    unorderedStates
                                )
                            }
                        }
                        is ProxyDesign.Request.Select -> {
                            withClash {
                                patchSelector(names[it.index], it.name)

                                states[it.index].now = it.name
                            }

                            design.requestRedrawVisible()
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            launch {
                                try {

                                    withClash {
                                        healthCheck(names[it.index])
                                    }


                                    var lastDelays = mutableMapOf<String, Int>()
                                    var stableCount = 0
                                    val maxAttempts = 30

                                    repeat(maxAttempts) { attempt ->
                                        delay(500)


                                        val group = reloadLock.withPermit {
                                            withClash {
                                                queryProxyGroup(names[it.index], uiStore.proxySort)
                                            }
                                        }

                                        val state = states[it.index]
                                        state.now = group.now


                                        val currentDelays = group.proxies.associate { proxy ->
                                            proxy.name to proxy.delay
                                        }


                                        design.updateProxyDelays(it.index, currentDelays)


                                        design.updateGroup(
                                            it.index,
                                            group.proxies,
                                            group.type == Proxy.Type.Selector,
                                            state,
                                            unorderedStates
                                        )


                                        if (currentDelays == lastDelays) {
                                            stableCount++
                                            if (stableCount >= 2) {

                                                return@repeat
                                            }
                                        } else {
                                            stableCount = 0
                                            lastDelays = currentDelays.toMutableMap()
                                        }
                                    }
                                } finally {

                                    design.stopUrlTesting()
                                }
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)
                                o.mode = it.mode
                                patchOverride(Clash.OverrideSlot.Session, o)
                            }
                        }
                    }
                }
            }
        }
    }
}