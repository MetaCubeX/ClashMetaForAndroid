package com.github.kr328.clash

import com.github.kr328.clash.design.OverrideSettingsDesign
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class OverrideSettingsActivity : BaseActivity<OverrideSettingsDesign>() {
    override suspend fun main() {
        val configuration = loadPersistOverride()

        defer {
            savePersistOverride(configuration)
        }

        val design = OverrideSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        OverrideSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    clearPersistOverride()
                                }

                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
