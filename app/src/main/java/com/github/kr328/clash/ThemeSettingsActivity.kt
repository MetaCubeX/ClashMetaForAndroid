package com.github.kr328.clash

import com.github.kr328.clash.design.ThemeSettingsDesign
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
                    val list = com.github.kr328.clash.util.ApplicationObserver.createdActivities.toList()
                    list.forEachIndexed { index, activity ->
                        activity.window?.decorView?.postDelayed({
                            if (activity.isFinishing) return@postDelayed
                            if (activity.isDestroyed) return@postDelayed
                            runCatching { activity.recreate() }
                        }, index * 60L)
                    }
                }
            }
        }
    }
}
