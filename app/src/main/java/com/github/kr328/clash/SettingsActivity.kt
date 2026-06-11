package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SettingsDesign
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(this)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // Re-evaluate experimental/expert gates when returning to this
                    // screen (e.g. after toggling them in App settings).
                    if (it == Event.ActivityStart) design.applyExperimentalVisibility()
                }
                design.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartMetaFeatures ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartGeo ->
                            startActivity(GeoSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartDnsHosts ->
                            startActivity(DnsHostsSettingsActivity::class.intent)
                        SettingsDesign.Request.StartTunnels ->
                            startActivity(TunnelsSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                    }
                }
            }
        }
    }
}
