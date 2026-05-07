package com.github.kr328.clash

import android.app.Activity
import com.github.kr328.clash.design.ThemeSettingsDesign
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class ThemeSettingsActivity : BaseActivity<ThemeSettingsDesign>() {

    private fun staggerRecreate(activities: List<Activity>) {
        activities.forEachIndexed { index, activity ->
            activity.window?.decorView?.postDelayed({
                if (activity.isFinishing) return@postDelayed
                if (activity.isDestroyed) return@postDelayed
                runCatching {
                    @Suppress("DEPRECATION")
                    activity.window?.setWindowAnimations(0)
                    activity.recreate()
                }
            }, index * 60L)
        }
    }

    override suspend fun main() {
        fun newDesign() = ThemeSettingsDesign(this, uiStore) { applyThemeFromUiStore() }
        var current = newDesign()
        setContentDesign(current)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
                current.requests.onReceive { request ->
                    when (request) {
                        ThemeSettingsDesign.Request.ReCreateOtherActivities -> {
                            staggerRecreate(
                                ApplicationObserver.createdActivities.filter { it !is ThemeSettingsActivity },
                            )
                            current.disposeForReplace()
                            current = newDesign()
                            window.decorView.post {
                                design = current
                            }
                        }
                        ThemeSettingsDesign.Request.ReCreateAllActivities -> {
                            staggerRecreate(ApplicationObserver.createdActivities.toList())
                        }
                    }
                }
            }
        }
    }
}
