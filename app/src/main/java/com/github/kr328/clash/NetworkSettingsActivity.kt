package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.webdav.WebdavAutoSync
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import com.github.kr328.clash.service.webdav.WebdavSync
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            clashRunning,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        NetworkSettingsDesign.Request.StartAccessControlList ->
                            startActivity(AccessControlActivity::class.intent)
                        NetworkSettingsDesign.Request.AccessControlChanged ->
                            launch { syncAccessControl(design) }
                    }
                }
            }
        }
    }

    private suspend fun syncAccessControl(design: NetworkSettingsDesign) {
        WebdavAutoSync.noteAccessControl(this)
        val settings = WebdavSettingsStore(this).load()
        if (!settings.autoSync || settings.url.isBlank() || settings.username.isBlank() || settings.password.isEmpty()) {
            return
        }
        try {
            withProfile {
                webdavPerform(
                    WebdavSync.ACTION_SYNC,
                    settings.url,
                    settings.username,
                    settings.password,
                    settings.remotePath,
                )
            }
        } catch (e: Exception) {
            design.showExceptionToast(e)
        }
    }
}
