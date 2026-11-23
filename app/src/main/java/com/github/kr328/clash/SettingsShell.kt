package com.github.kr328.clash

import android.content.Context
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.webdav.WebdavLocalState
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import universe.agentic.clash.SettingsRootStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import com.github.kr328.clash.design.R as DesignR

internal fun Context.webdavHomeLabel(): String {
    val store = WebdavSettingsStore(this)
    val status = when {
        store.passwordUnreadable -> getString(DesignR.string.webdav_password_unreadable)
        !store.configured() -> getString(DesignR.string.webdav_unset)
        store.autoSync -> getString(DesignR.string.webdav_home_auto)
        else -> getString(DesignR.string.webdav_home_ready)
    }
    val syncedAt = WebdavLocalState(this).read().lastSyncAt
    if (syncedAt <= 0L) return status
    val ago = (System.currentTimeMillis() - syncedAt).coerceAtLeast(0L).elapsedIntervalString(this)
    return "$status · $ago"
}

internal class SettingsShell(
    private val host: BaseActivity<*>,
    private val embedded: Boolean,
    private val onMounted: suspend (SettingsDesign) -> Unit,
) {
    var page: SettingsDesign? = null
        private set

    private val refreshSignals = Channel<Unit>(Channel.CONFLATED)

    fun poke() {
        refreshSignals.trySend(Unit)
    }

    suspend fun run() {
        val design = SettingsDesign(host, embedded)
        page = design
        try {
            onMounted(design)
            refresh()
            while (coroutineContext.isActive) {
                select<Unit> {
                    refreshSignals.onReceive { refresh() }
                    if (!embedded) {
                        host.shellEvents().onReceive {
                            if (it == BaseActivity.Event.ActivityStart) refresh()
                        }
                    }
                    design.requests.onReceive {
                        when (it) {
                            SettingsDesign.Request.StartApp ->
                                host.startActivity(AppSettingsActivity::class.intent)
                            SettingsDesign.Request.StartNetwork ->
                                host.startActivity(NetworkSettingsActivity::class.intent)
                            SettingsDesign.Request.StartOverride ->
                                host.startActivity(OverrideSettingsActivity::class.intent)
                            SettingsDesign.Request.StartMetaFeature ->
                                host.startActivity(MetaFeatureSettingsActivity::class.intent)
                            SettingsDesign.Request.StartWebdav ->
                                host.startActivity(WebdavSettingsActivity::class.intent)
                            SettingsDesign.Request.StartMcp ->
                                host.startActivity(McpSettingsActivity::class.intent)
                            SettingsDesign.Request.StartAbout ->
                                host.startActivity(AboutActivity::class.intent)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            design.cancel()
            if (page === design) page = null
        }
    }

    private suspend fun refresh() {
        val next = withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            SettingsRootStatus(
                loaded = true,
                darkMode = host.shellUiStore().darkMode,
                tunStack = service.tunStackMode,
                webdav = host.webdavHomeLabel(),
                mcpEnabled = service.mcpEnabled,
                mcpListening = service.mcpStatus == "listening",
            )
        }
        page?.showStatus(next)
    }
}
