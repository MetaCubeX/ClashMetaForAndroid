package com.github.kr328.clash.design

import android.content.Context
import androidx.compose.runtime.*
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.constants.ProxyConstants
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.proxy.ProxyGroupState
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProxyDesign(
    context: Context,
    val overrideMode: TunnelState.Mode?,
    initialGroupNames: List<String>,
    val uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {

    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()
        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    val proxyGroups = mutableStateMapOf<Int, ProxyGroupState>()

    var currentPage by mutableStateOf(0)

    var sortByDelay by mutableStateOf(false)

    private var isUrlTestingByPage = mutableStateMapOf<Int, Boolean>()
    private var testingStartTimeByPage = mutableStateMapOf<Int, Long>()
    private var showMenu by mutableStateOf(false)

    internal var groupNames by mutableStateOf(initialGroupNames)

    var proxyLayoutType by mutableStateOf(1)
    internal val modes = TunnelState.Mode.values()
    internal var currentMode by mutableStateOf(TunnelState.Mode.Rule)

    init {
        sortByDelay = uiStore.proxySortByDelay
        proxyLayoutType = uiStore.proxyLayoutType
        currentMode = overrideMode ?: TunnelState.Mode.Rule
        val lastGroupName = uiStore.proxyLastGroup
        if (lastGroupName.isNotEmpty()) {
            val idx = groupNames.indexOf(lastGroupName)
            if (idx >= 0) {
                currentPage = idx
            }
        }
    }

    fun toggleSortByDelay() {
        sortByDelay = !sortByDelay
        uiStore.proxySortByDelay = sortByDelay
    }

    fun toggleProxyLayoutType() {
        proxyLayoutType = (proxyLayoutType + 1) % ProxyConstants.UI.LAYOUT_TYPE_COUNT
        uiStore.proxyLayoutType = proxyLayoutType
    }

    @Composable
    override fun Content() {
        com.github.kr328.clash.design.screen.ProxyScreen(
            proxyDesign = this,
            running = true
        )
    }

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) = updateStateOnMain {
        proxyGroups[position] = ProxyGroupState(
            proxies = proxies,
            selectable = selectable,
            parent = parent,
            links = links,
            urlTesting = false,
            testingUpdatedDelays = false
        )
    }

    fun requestRedrawVisible() {
    }

    fun requestUrlTesting(pageIndex: Int = currentPage) {
        if (isUrlTestingByPage[pageIndex] == true) {
            val startTime = testingStartTimeByPage[pageIndex] ?: 0
            if (System.currentTimeMillis() - startTime < ProxyConstants.DelayTest.TEST_IN_PROGRESS_TIMEOUT) {
                return
            }
        }

        val targetGroup = proxyGroups[pageIndex]
        if (targetGroup == null || targetGroup.proxies.isEmpty()) {
            return
        }
        isUrlTestingByPage[pageIndex] = true
        testingStartTimeByPage[pageIndex] = System.currentTimeMillis()
        val updatedGroup = targetGroup.copy(urlTesting = true, testingUpdatedDelays = true)
        proxyGroups[pageIndex] = updatedGroup
        requests.trySend(Request.UrlTest(pageIndex))
    }

    fun stopUrlTesting(pageIndex: Int = currentPage) {
        runOnMain {
            isUrlTestingByPage[pageIndex] = false
            testingStartTimeByPage.remove(pageIndex)
            val targetGroup = proxyGroups[pageIndex]?.copy(urlTesting = false, testingUpdatedDelays = false)
            if (targetGroup != null) {
                proxyGroups[pageIndex] = targetGroup
            }
        }
    }

    fun isPageTesting(pageIndex: Int): Boolean {
        return isUrlTestingByPage[pageIndex] == true
    }

    fun cleanupTimeoutTests() {
        val currentTime = System.currentTimeMillis()
        val timeoutPages = mutableListOf<Int>()

        testingStartTimeByPage.forEach { (pageIndex, startTime) ->
            if (currentTime - startTime > ProxyConstants.DelayTest.TEST_CLEANUP_TIMEOUT) {
                timeoutPages.add(pageIndex)
            }
        }

        timeoutPages.forEach { pageIndex ->
            stopUrlTesting(pageIndex)
        }
    }

    fun updateProxyDelay(groupIndex: Int, proxyName: String, delay: Int) {
        val group = proxyGroups[groupIndex] ?: return
        val state = group.links[proxyName] ?: return
        val currentDelay = state.delay
        if (currentDelay == delay) return

        runOnMain {
            val freshGroup = proxyGroups[groupIndex]
            if (freshGroup != null) {
                val targetState = freshGroup.links[proxyName]
                if (targetState != null) {
                    val newLinks = freshGroup.links.toMutableMap().apply {
                        this[proxyName] = targetState.copy(delay = delay)
                    }
                    proxyGroups[groupIndex] = freshGroup.copy(links = newLinks)
                }
            }
        }
    }

    fun updateProxyDelays(groupIndex: Int, delays: Map<String, Int>) {
        if (delays.isEmpty()) return
        val group = proxyGroups[groupIndex] ?: return

        runOnMain {
            val fresh = proxyGroups[groupIndex]
            if (fresh != null) {
                val newLinks = fresh.links.toMutableMap()
                var changed = false
                delays.forEach { (name, d) ->
                    val st = newLinks[name]
                    if (st != null && st.delay != d) {
                        newLinks[name] = st.copy(delay = d)
                        changed = true
                    }
                }
                if (changed) {
                    proxyGroups[groupIndex] = fresh.copy(
                        links = newLinks,
                        testingUpdatedDelays = fresh.testingUpdatedDelays
                    )
                }
            }
        }
    }

    fun applyNewGroupNames(newNames: List<String>) {
        if (newNames == groupNames) return
        groupNames = newNames
        proxyGroups.clear()
        isUrlTestingByPage.clear()
        testingStartTimeByPage.clear()

        requests.trySend(Request.ReloadAll)
        val last = uiStore.proxyLastGroup
        currentPage = if (last.isNotEmpty()) {
            val idx = groupNames.indexOf(last)
            if (idx >= 0) idx else 0
        } else 0
    }

    fun updateProxySelection(groupIndex: Int, selectedProxyName: String) {
        val group = proxyGroups[groupIndex] ?: return
        val updatedParent = group.parent?.copy(now = selectedProxyName)
        if (updatedParent != null) {
            proxyGroups[groupIndex] = group.copy(parent = updatedParent)
        }
    }

    fun isCurrentPageTesting(): Boolean {
        return isPageTesting(currentPage)
    }

    fun stopAllTesting() {
        val testingPages = isUrlTestingByPage.keys.toList()
        testingPages.forEach { pageIndex ->
            stopUrlTesting(pageIndex)
        }
    }

    private val delayCache = mutableMapOf<String, Pair<Int, Long>>()

    fun getProxyDelayWithCache(proxyName: String): Int? {
        val cached = delayCache[proxyName]
        return if (cached != null && System.currentTimeMillis() - cached.second < ProxyConstants.DelayTest.CACHE_VALID_TIME) {
            cached.first
        } else null
    }

    fun cacheProxyDelay(proxyName: String, delay: Int) {
        delayCache[proxyName] = delay to System.currentTimeMillis()
    }

    fun startBackgroundDelayTest(pageIndex: Int) {
        this.launch {
            val group = proxyGroups[pageIndex] ?: return@launch
            val proxies = group.proxies

            if (proxies.isEmpty()) return@launch

            isUrlTestingByPage[pageIndex] = true
            testingStartTimeByPage[pageIndex] = System.currentTimeMillis()

            try {
                val concurrency = ProxyConstants.Concurrency.getConcurrency(proxies.size)

                val gName = context.let {
                    groupNames.getOrNull(pageIndex)
                } ?: return@launch

                requests.trySend(Request.UrlTest(pageIndex))

                val maxWaitTime = ProxyConstants.WaitTime.getMaxWaitTime(proxies.size)

                withTimeoutOrNull(maxWaitTime) {
                    while (isUrlTestingByPage[pageIndex] == true) {
                        delay(ProxyConstants.DelayTest.TEST_CHECK_INTERVAL)
                    }
                }

            } finally {
                stopUrlTesting(pageIndex)
            }
        }
    }

    fun updateProxyDelayRealtime(groupIndex: Int, proxyName: String, delay: Int) {
        val group = proxyGroups[groupIndex] ?: return
        val currentLinks = group.links.toMutableMap()
        val currentState = currentLinks[proxyName]

        if (currentState != null && currentState.delay != delay) {
            cacheProxyDelay(proxyName, delay)
            currentLinks[proxyName] = currentState.copy(delay = delay)
            proxyGroups[groupIndex] = group.copy(
                links = currentLinks,
                testingUpdatedDelays = true
            )

            if (sortByDelay) {
                triggerRealtimeSort(groupIndex)
            }
        }
    }

    private fun triggerRealtimeSort(groupIndex: Int) {
        this.launch {
            delay(ProxyConstants.UI.SORT_DEBOUNCE_DELAY)
            val group = proxyGroups[groupIndex]
            if (group != null && !group.urlTesting) {
                requestRedrawVisible()
            }
        }
    }

    fun updateProxyDelaysRealtime(groupIndex: Int, delays: Map<String, Int>) {
        if (delays.isEmpty()) return
        val group = proxyGroups[groupIndex] ?: return

        val currentLinks = group.links.toMutableMap()
        var hasChanges = false

        delays.forEach { (proxyName, delay) ->
            val currentState = currentLinks[proxyName]
            if (currentState != null && currentState.delay != delay) {
                cacheProxyDelay(proxyName, delay)
                currentLinks[proxyName] = currentState.copy(delay = delay)
                hasChanges = true
            }
        }

        if (hasChanges) {
            proxyGroups[groupIndex] = group.copy(
                links = currentLinks,
                testingUpdatedDelays = true
            )

            if (sortByDelay) {
                triggerRealtimeSort(groupIndex)
            }
        }
    }

    fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = delayCache.filter { (_, pair) ->
            currentTime - pair.second > ProxyConstants.DelayTest.CACHE_VALID_TIME
        }.keys
        expiredKeys.forEach { delayCache.remove(it) }
    }

    override fun onDestroy() {
        stopAllTesting()
        isUrlTestingByPage.clear()
        testingStartTimeByPage.clear()
        delayCache.clear()
        super.onDestroy()
    }
}
