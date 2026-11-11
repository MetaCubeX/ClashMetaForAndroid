package com.github.kr328.clash

import com.github.kr328.clash.design.AutoSwitchSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore

class AutoSwitchSettingsActivity : BaseActivity<AutoSwitchSettingsDesign>() {
    override suspend fun main() {
        val design = AutoSwitchSettingsDesign(
            this,
            ServiceStore(this),
        )

        setContentDesign(design)
    }
}
