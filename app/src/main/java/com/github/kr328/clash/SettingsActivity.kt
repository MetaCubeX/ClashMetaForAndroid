package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.SettingsDesign
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<Design<*>>() {
    override suspend fun main() {
        val expandedPane = if (isExpandedSettingsWidth()) ExpandedSettingsPane(this, clashRunning) else null
        val design: Design<*> = expandedPane?.initialize() ?: SettingsDesign(this)

        defer {
            expandedPane?.save()
        }

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                if (expandedPane == null) {
                    (design as SettingsDesign).requests.onReceive {
                        when (it) {
                            SettingsDesign.Request.StartApp ->
                                startActivity(AppSettingsActivity::class.intent)
                            SettingsDesign.Request.StartNetwork ->
                                startActivity(NetworkSettingsActivity::class.intent)
                            SettingsDesign.Request.StartOverride ->
                                startActivity(OverrideSettingsActivity::class.intent)
                            SettingsDesign.Request.StartMetaFeature ->
                                startActivity(MetaFeatureSettingsActivity::class.intent)
                        }
                    }
                } else {
                    expandedPane.design.requests.onReceive { expandedPane.handleExpandedRequest(it) }
                    expandedPane.appDesign.requests.onReceive { expandedPane.handleAppRequest(it) }
                    expandedPane.networkDesign.requests.onReceive { expandedPane.handleNetworkRequest(it) }
                    expandedPane.overrideDesign.requests.onReceive { expandedPane.handleOverrideRequest(it) }
                    expandedPane.metaFeatureDesign.requests.onReceive { expandedPane.handleMetaFeatureRequest(it) }
                }
            }
        }
    }
}
