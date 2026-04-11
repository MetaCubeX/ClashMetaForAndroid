package com.github.kr328.clash

import android.os.Bundle
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.TunnelState
import androidx.annotation.StringRes
import com.github.kr328.clash.design.ProxyChainDesign
import com.github.kr328.clash.design.ProxyChainDesign.ChainStatusKind
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID

private const val CHAIN_FIELD_SEP = '\u001F'

/**
 * Sets **dialer-proxy** on a proxy entry (config `proxies:` or provider YAML files).
 * **Use chain** also switches tunnel to Global and selects the outbound (desktop-style).
 * Opened from the Proxy screen overflow menu.
 */
class ProxyChainActivity : BaseActivity<ProxyChainDesign>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override suspend fun main() {
        try {
            mainImpl()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ProxyChainActivity,
                    e.message ?: e.toString(),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    private suspend fun mainImpl() {
        val profile = withProfile { queryActive() }
        if (profile == null) {
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
                        ProxyChainDesign.Request.Connect ->
                            launch { connectChain(uuid, design) { refreshDiskUi() } }
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

    private suspend fun waitForProxyEngineReady() {
        delay(350L)
        repeat(40) {
            val ok = runCatching {
                withClash { queryProxyGroupNames(false).isNotEmpty() }
            }.getOrDefault(false)
            if (ok) return
            delay(200L)
        }
    }

    private fun chainErr(@StringRes msg: Int): String = getString(msg)

    private suspend fun connectChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val outbound = design.selectedOutboundProxyName()?.trim().orEmpty()
        val dialer = design.selectedDialerProxyName()?.trim().orEmpty()
        val group = design.selectedOutboundGroupName()?.trim().orEmpty()
        if (outbound.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_outbound))
            return
        }
        if (dialer.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_dialer))
            return
        }
        if (outbound == dialer) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_same_proxy))
            return
        }
        if (group.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_outbound_group))
            return
        }
        if (!clashRunning) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_connect_need_vpn))
            return
        }
        design.setChainBusy(true)
        design.showChainStatus(ChainStatusKind.Progress, getString(R.string.proxy_chain_status_working))
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, outbound, dialer) }
            if (!ok) {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
                return
            }
            refreshDiskUi()
            waitForProxyEngineReady()
            uiStore.tunnelModePreference = TunnelState.Mode.Global.name
            val patched = withClash {
                val o = queryOverride(Clash.OverrideSlot.Session)
                o.mode = TunnelState.Mode.Global
                patchOverride(Clash.OverrideSlot.Session, o)
                patchSelector(group, outbound)
            }
            if (patched) {
                design.showChainStatus(
                    ChainStatusKind.Success,
                    getString(R.string.proxy_chain_status_success, outbound, dialer),
                )
            } else {
                design.showChainStatus(
                    ChainStatusKind.Warning,
                    getString(R.string.proxy_chain_connect_selector_failed),
                )
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
            design.showChainStatus(
                ChainStatusKind.Error,
                e.message?.takeIf { it.isNotBlank() } ?: chainErr(R.string.proxy_chain_not_found),
            )
        } finally {
            design.setChainBusy(false)
        }
    }

    private suspend fun applyChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val outbound = design.selectedOutboundProxyName()?.trim().orEmpty()
        val dialer = design.selectedDialerProxyName()?.trim().orEmpty()
        if (outbound.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_outbound))
            return
        }
        if (dialer.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_dialer))
            return
        }
        if (outbound == dialer) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_same_proxy))
            return
        }
        design.setChainBusy(true)
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, outbound, dialer) }
            if (ok) {
                refreshDiskUi()
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_status_saved_only))
            } else {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
            design.showChainStatus(
                ChainStatusKind.Error,
                e.message?.takeIf { it.isNotBlank() } ?: chainErr(R.string.proxy_chain_not_found),
            )
        } finally {
            design.setChainBusy(false)
        }
    }

    private suspend fun clearChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val outbound = design.selectedOutboundProxyName()?.trim().orEmpty()
        if (outbound.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_outbound))
            return
        }
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, outbound, null) }
            if (ok) {
                refreshDiskUi()
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared))
            } else {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
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
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared_all))
            } else {
                design.showChainStatus(ChainStatusKind.Warning, getString(R.string.proxy_chain_saved_empty))
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
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_saved_row))
            return
        }
        try {
            val ok = withProfile { setProxyDialerProxy(uuid, target, null) }
            if (ok) {
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared_selected))
            } else {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
            }
            refreshDiskUi()
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
