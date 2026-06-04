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
import com.github.kr328.clash.util.closeConnectionsAfterUserProxySwitchIfEnabled
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.util.showYamlPreview
import com.github.kr328.clash.util.applyYamlPreviewDirect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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

        suspend fun loadRuntimeGroupsWithRetry(): List<String> {
            repeat(8) { attempt ->
                val groups = runCatching {
                    withClash { queryProxyGroupNames(false) }
                }.getOrDefault(emptyList())
                if (groups.isNotEmpty()) return groups
                if (attempt < 7) delay(220L)
            }
            return emptyList()
        }

        val groups = loadRuntimeGroupsWithRetry()

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
                        ProxyChainDesign.Request.UseNow ->
                            launch { useNowChain(uuid, design) { refreshDiskUi() } }
                        ProxyChainDesign.Request.SaveChain ->
                            launch { saveChain(uuid, design) { refreshDiskUi() } }
                        ProxyChainDesign.Request.Preview ->
                            launch { previewChain(uuid, design) { refreshDiskUi() } }
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

    /** Validates the two pickers; on error shows status and returns null, else (firstHop, exit). */
    private fun validateChainSelection(design: ProxyChainDesign): Pair<String, String>? {
        val firstHop = design.selectedFirstHopName()?.trim().orEmpty()
        val exit = design.selectedExitName()?.trim().orEmpty()
        when {
            firstHop.isEmpty() ->
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_firsthop))
            exit.isEmpty() ->
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_exit))
            firstHop == exit ->
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_same_proxy))
            else -> return firstHop to exit
        }
        return null
    }

    /** Stage the dialer-proxy change; returns the preview JSON or null (status shown on failure). */
    private suspend fun stageChainPreview(
        uuid: UUID,
        target: String,
        dialer: String?,
        design: ProxyChainDesign,
    ): String? = try {
        withProfile { previewSetProxyDialerProxy(uuid, target, dialer) }
    } catch (e: Exception) {
        design.showExceptionToast(e)
        design.showChainStatus(
            ChainStatusKind.Error,
            e.message?.takeIf { it.isNotBlank() } ?: chainErr(R.string.proxy_chain_not_found),
        )
        null
    }

    /** Save the chain only — no tunnel-mode / selection change. Commits directly (no forced dialog). */
    private suspend fun saveChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val (firstHop, exit) = validateChainSelection(design) ?: return
        design.setChainBusy(true)
        try {
            val preview = stageChainPreview(uuid, exit, firstHop, design) ?: return
            if (applyYamlPreviewDirect(preview)) {
                refreshDiskUi()
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_status_saved_only))
            } else {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
            }
        } finally {
            design.setChainBusy(false)
        }
    }

    /** Save + switch to Global + select A in the GLOBAL group (stated to the user in the button). */
    private suspend fun useNowChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val (firstHop, exit) = validateChainSelection(design) ?: return
        if (!clashRunning) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_connect_need_vpn))
            return
        }
        design.setChainBusy(true)
        design.showChainStatus(ChainStatusKind.Progress, getString(R.string.proxy_chain_status_working))
        try {
            val preview = stageChainPreview(uuid, exit, firstHop, design) ?: return
            if (!applyYamlPreviewDirect(preview)) {
                design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_not_found))
                return
            }
            refreshDiskUi()
            waitForProxyEngineReady()
            uiStore.tunnelModePreference = TunnelState.Mode.Global.name
            // Use-now forces Global, where the active selector is the GLOBAL group → select A there.
            val patched = withClash {
                val o = queryOverride(Clash.OverrideSlot.Session)
                o.mode = TunnelState.Mode.Global
                patchOverride(Clash.OverrideSlot.Session, o)
                patchSelector("GLOBAL", exit)
            }
            if (patched) {
                closeConnectionsAfterUserProxySwitchIfEnabled { message, duration ->
                    design.showToast(message, duration)
                }
                design.showChainStatus(
                    ChainStatusKind.Success,
                    getString(R.string.proxy_chain_status_success, exit, firstHop),
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

    /** Optional preview: show the pending YAML change; applying it from the dialog saves the chain. */
    private suspend fun previewChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val (firstHop, exit) = validateChainSelection(design) ?: return
        val preview = stageChainPreview(uuid, exit, firstHop, design) ?: return
        showYamlPreview(preview) {
            refreshDiskUi()
            design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_status_saved_only))
        }
    }

    private suspend fun clearChain(
        uuid: UUID,
        design: ProxyChainDesign,
        refreshDiskUi: suspend () -> Unit,
    ) {
        val exit = design.selectedExitName()?.trim().orEmpty()
        if (exit.isEmpty()) {
            design.showChainStatus(ChainStatusKind.Error, chainErr(R.string.proxy_chain_pick_exit))
            return
        }
        try {
            val preview = withProfile { previewSetProxyDialerProxy(uuid, exit, null) }
            showYamlPreview(preview) {
                refreshDiskUi()
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared))
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
            val preview = withProfile { previewClearAllProxyDialerChains(uuid) }
            showYamlPreview(preview) {
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared_all))
                refreshDiskUi()
            }
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
            val preview = withProfile { previewSetProxyDialerProxy(uuid, target, null) }
            showYamlPreview(preview) {
                design.showChainStatus(ChainStatusKind.Success, getString(R.string.proxy_chain_cleared_selected))
                refreshDiskUi()
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
