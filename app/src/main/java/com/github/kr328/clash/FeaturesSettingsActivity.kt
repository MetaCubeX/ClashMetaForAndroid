package com.github.kr328.clash

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.FeaturesSettingsDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class FeaturesSettingsActivity : BaseActivity<FeaturesSettingsDesign>() {
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = FeaturesSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // no-op; keep design alive until activity finishes
                }
                design.requests.onReceive {
                    when (it) {
                        FeaturesSettingsDesign.Request.StartGeoDataSource ->
                            startActivity(GeoDataSourceSettingsActivity::class.intent)
                    }
                }
            }
        }
    }
}
