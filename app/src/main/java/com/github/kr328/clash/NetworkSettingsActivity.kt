package com.github.kr328.clash

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val vpnState = withContext(Dispatchers.IO) {
            StatusClient(this@NetworkSettingsActivity).vpnState()
        }

        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            clashRunning,
            vpnState,
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
                        NetworkSettingsDesign.Request.OpenSystemVpnSettings ->
                            try {
                                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                            } catch (e: ActivityNotFoundException) {
                                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                    }
                }
            }
        }
    }

}
