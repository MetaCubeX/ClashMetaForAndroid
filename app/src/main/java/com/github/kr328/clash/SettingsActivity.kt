package com.github.kr328.clash

import com.github.kr328.clash.design.SettingsDesign

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val shell = SettingsShell(this, embedded = false) { setContentDesign(it) }
        shell.run()
    }
}
