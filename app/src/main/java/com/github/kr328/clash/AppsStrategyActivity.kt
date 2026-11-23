package com.github.kr328.clash

import com.github.kr328.clash.design.AppsStrategyDesign

class AppsStrategyActivity : BaseActivity<AppsStrategyDesign>() {
    override suspend fun main() {
        val shell = AppsStrategyShell(this, embedded = false) { setContentDesign(it) }
        shell.run()
    }
}
