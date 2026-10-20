package com.github.kr328.clash

import android.os.Bundle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class ProxyActivity : BaseActivity<ProxyDesign>() {
    private var restoredGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredGroup = savedInstanceState?.getString(STATE_GROUP)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        design?.currentGroupName?.let { outState.putString(STATE_GROUP, it) }
    }

    override suspend fun main() {
        val names = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        val states = List(names.size) { ProxyState("") }
        val unorderedStates = names.indices.associate { names[it] to states[it] }
        val reloadLock = Semaphore(10)
        val reloadSeq = IntArray(names.size)
        val selectSeq = IntArray(names.size)
        // Non-zero only for the reload a speed test itself starts. A dropped result must not clear it.
        val clearOnTicket = IntArray(names.size)
        val seqLock = Mutex()
        val index = initialGroupIndex(names)

        val design = ProxyDesign(
            this,
            names,
            index,
            uiStore,
            states,
        ) { group ->
            uiStore.proxyLastGroup = group
            intent.putExtra(EXTRA_PROXY_GROUP, group)
        }

        setContentDesign(design)

        suspend fun startQuery(groupIndex: Int, fromUrlTest: Boolean) {
            val (ticket, selectAtStart) = seqLock.withLock {
                val next = ++reloadSeq[groupIndex]
                if (fromUrlTest) clearOnTicket[groupIndex] = next
                next to selectSeq[groupIndex]
            }
            val group = reloadLock.withPermit {
                withClash { queryProxyGroup(names[groupIndex], uiStore.proxySort) }
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
                unorderedStates,
                group.type,
                clearUrlTesting,
            )
        }

        // Direct mode and a fully hidden list are empty. Do not Reload index 0.
        if (index >= 0) {
            design.requests.trySend(ProxyDesign.Request.Reload(index))
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newNames = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                            }

                            if (newNames != names) {
                                relaunchShowingCurrent()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> relaunchShowingCurrent()
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                design.requests.trySend(ProxyDesign.Request.Reload(idx))
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            if (it.index !in names.indices) return@onReceive
                            launch { startQuery(it.index, fromUrlTest = false) }
                        }
                        is ProxyDesign.Request.Select -> {
                            if (it.index !in names.indices) return@onReceive
                            seqLock.withLock { selectSeq[it.index]++ }
                            withClash {
                                patchSelector(names[it.index], it.name)

                                states[it.index].now = it.name
                            }

                            design.requestRedrawVisible()
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            if (it.index !in names.indices) return@onReceive
                            launch {
                                withClash {
                                    healthCheck(names[it.index])
                                }
                                startQuery(it.index, fromUrlTest = true)
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            design.showModeSwitchTips()

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

    private fun initialGroupIndex(names: List<String>): Int {
        if (names.isEmpty()) return -1
        // Rotation only keeps uiMode. Prefer the group that was on screen, then the launch extra.
        restoredGroup?.takeIf { it.isNotEmpty() }?.let { saved ->
            val savedIndex = names.indexOf(saved)
            if (savedIndex >= 0) return savedIndex
        }
        intent.getStringExtra(EXTRA_PROXY_GROUP)?.takeIf { it.isNotEmpty() }?.let { extra ->
            val extraIndex = names.indexOf(extra)
            if (extraIndex >= 0) return extraIndex
        }
        val last = names.indexOf(uiStore.proxyLastGroup)
        if (last >= 0) return last
        return 0
    }

    private fun relaunchShowingCurrent() {
        val next = ProxyActivity::class.intent
        design?.currentGroupName?.let { next.putExtra(EXTRA_PROXY_GROUP, it) }
        startActivity(next)
        finish()
    }

    companion object {
        const val EXTRA_PROXY_GROUP = "proxy_group"
        private const val STATE_GROUP = "proxy_shown_group"
    }
}
