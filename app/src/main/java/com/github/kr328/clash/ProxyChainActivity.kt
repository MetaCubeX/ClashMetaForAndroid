package com.github.kr328.clash

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.github.kr328.clash.design.ProxyChainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID

private const val CHAIN_FIELD_SEP = '\u001F'

/**
 * Sets **dialer-proxy** on a proxy entry (config `proxies:` or provider YAML files).
 * Opened from the Proxy screen overflow menu.
 */
class ProxyChainActivity : BaseActivity<ProxyChainDesign>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        val profile = withProfile { queryActive() }
        if (profile == null || !profile.imported) {
            withContext(Dispatchers.Main) {
                finish()
            }
            return
        }
        val uuid = profile.uuid

        val design = ProxyChainDesign(this)
        setContentDesign(design)

        suspend fun refreshDiskUi() {
            val raw = try {
                withProfile { listProxyDialerChains(uuid) }
            } catch (_: Exception) {
                emptyList()
            }
            val rows = raw.mapNotNull { line -> parseDiskChainLine(line) }
            withContext(Dispatchers.Main) {
                design.bindDiskChains(rows)
            }
        }

        refreshDiskUi()

        val groups = try {
            withClash { queryProxyGroupNames(false) }
        } catch (_: Exception) {
            emptyList()
        }

        val proxiesByGroup = LinkedHashMap<String, List<String>>()
        val sort = uiStore.proxySort
        for (g in groups) {
            val names = try {
                withClash {
                    queryProxyGroup(g, sort).proxies.map { it.name }
                }
            } catch (_: Exception) {
                emptyList()
            }
            proxiesByGroup[g] = names
        }

        withContext(Dispatchers.Main) {
            design.bindRuntime(groups, proxiesByGroup)
        }

        while (isActive) {
            select<Unit> {
                design.requests.onReceive { req ->
                    when (req) {
                        ProxyChainDesign.Request.Apply ->
                            launch { applyChain(uuid, design) { refreshDiskUi() } }
                        ProxyChainDesign.Request.Clear ->
                            launch { clearChain(uuid, design) { refreshDiskUi() } }
                        ProxyChainDesign.Request.ClearAllDiskChains ->
                            launch { clearAllDiskChains(uuid, design) { refreshDiskUi() } }
                        ProxyChainDesign.Request.ClearSelectedDiskChain ->
                            launch { clearSelectedDiskChain(uuid, design) { refreshDiskUi() } }
                    }
                }
            }
        }
    }

    private fun parseDiskChainLine(line: String): Triple<String, String, String>? {
        val p = line.split(CHAIN_FIELD_SEP)
        if (p.size != 3) return null
        return Triple(p[0], p[1], p[2])
    }

    private suspend fun applyChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val outbound = design.selectedOutboundProxyName()?.trim().orEmpty()
        val dialer = design.selectedDialerProxyName()?.trim().orEmpty()
        if (outbound.isEmpty()) {
            design.showToast(R.string.proxy_chain_pick_outbound, ToastDuration.Long)
            return
        }
        if (dialer.isEmpty()) {
            design.showToast(R.string.proxy_chain_pick_dialer, ToastDuration.Long)
            return
        }
        if (outbound == dialer) {
            design.showToast(R.string.proxy_chain_same_proxy, ToastDuration.Long)
            return
        }
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, outbound, dialer) }
            if (ok) {
                design.showToast(R.string.proxy_chain_applied, ToastDuration.Long)
                refreshDiskUi()
            } else {
                design.showToast(R.string.proxy_chain_not_found, ToastDuration.Long)
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }

    private suspend fun clearChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val outbound = design.selectedOutboundProxyName()?.trim().orEmpty()
        if (outbound.isEmpty()) {
            design.showToast(R.string.proxy_chain_pick_outbound, ToastDuration.Long)
            return
        }
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, outbound, null) }
            if (ok) {
                design.showToast(R.string.proxy_chain_cleared, ToastDuration.Long)
                refreshDiskUi()
            } else {
                design.showToast(R.string.proxy_chain_not_found, ToastDuration.Long)
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }

    private suspend fun clearAllDiskChains(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        try {
            val ok = withProfile { clearAllProxyDialerChains(uuid) }
            if (ok) {
                design.showToast(R.string.proxy_chain_cleared_all, ToastDuration.Long)
            } else {
                design.showToast(R.string.proxy_chain_saved_empty, ToastDuration.Long)
            }
            refreshDiskUi()
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }

    private suspend fun clearSelectedDiskChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val target = design.selectedDiskChainTarget()?.trim().orEmpty()
        if (target.isEmpty()) {
            design.showToast(R.string.proxy_chain_pick_saved_row, ToastDuration.Long)
            return
        }
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, target, null) }
            if (ok) {
                design.showToast(R.string.proxy_chain_cleared_selected, ToastDuration.Long)
            } else {
                design.showToast(R.string.proxy_chain_not_found, ToastDuration.Long)
            }
            refreshDiskUi()
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
