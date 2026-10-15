package com.github.kr328.clash

import com.github.kr328.clash.design.WebdavSettingsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.webdav.WebdavLocalState
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import com.github.kr328.clash.service.webdav.WebdavSync
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class WebdavSettingsActivity : BaseActivity<WebdavSettingsDesign>() {
    private var busy = false

    override suspend fun main() {
        val store = WebdavSettingsStore(this)
        val design = WebdavSettingsDesign(
            this,
            store,
            WebdavLocalState(this).lastMessage(),
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
                design.requests.onReceive {
                    val action = when (it) {
                        WebdavSettingsDesign.Request.Test -> WebdavSync.ACTION_TEST
                        WebdavSettingsDesign.Request.Sync -> WebdavSync.ACTION_SYNC
                        WebdavSettingsDesign.Request.Pull -> WebdavSync.ACTION_PULL
                        WebdavSettingsDesign.Request.Push -> WebdavSync.ACTION_PUSH
                    }
                    runAction(design, store, action)
                }
            }
        }
    }

    private suspend fun runAction(
        design: WebdavSettingsDesign,
        store: WebdavSettingsStore,
        action: String,
    ) {
        if (busy) return
        busy = true
        try {
            val settings = store.load()
            val message = design.withProgress { update ->
                withProfile {
                    webdavPerform(
                        action,
                        settings.url,
                        settings.lanUrl,
                        settings.username,
                        settings.password,
                        settings.remotePath,
                    ) { phase, current, total, label ->
                        design.launch { update(phase, current, total, label) }
                    }
                }
            }
            design.setStatus(message)
            design.showToast(message, ToastDuration.Long)
        } catch (e: Exception) {
            design.showExceptionToast(e)
        } finally {
            busy = false
        }
    }
}
