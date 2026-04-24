package com.github.kr328.clash

import com.github.kr328.clash.design.ThemeSettingsDesign
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class ThemeSettingsActivity : BaseActivity<ThemeSettingsDesign>() {
    override suspend fun main() {
        val design = ThemeSettingsDesign(this, uiStore)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    // No service-driven state on this screen.
                }
                design.requests.onReceive {
                    ApplicationObserver.createdActivities.forEach { activity ->
                        activity.recreate()
                    }
                }
            }
        }
    }
}
