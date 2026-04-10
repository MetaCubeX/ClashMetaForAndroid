package com.github.kr328.clash

import com.github.kr328.clash.design.AdvancedSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class AdvancedSettingsActivity : BaseActivity<AdvancedSettingsDesign>() {
    override suspend fun main() {
        val design = AdvancedSettingsDesign(
            this,
            ServiceStore(this),
        )

        setContentDesign(design)
        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // no-op; keep design alive until activity finishes
                }
            }
        }
    }
}
