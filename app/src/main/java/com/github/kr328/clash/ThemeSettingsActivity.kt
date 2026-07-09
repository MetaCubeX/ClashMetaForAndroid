package com.github.kr328.clash

import android.app.Activity
import com.github.kr328.clash.design.ThemeSettingsDesign
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class ThemeSettingsActivity : BaseActivity<ThemeSettingsDesign>() {

    /**
     * @param softMain route MainActivity through its soft path (design re-inflation, no Activity
     * destroy — Android 16 ContentCapture SIGABRT) instead of `recreate()`. Only valid for pure
     * theme-overlay changes (palette / accent / true-black); font scale and reset re-run
     * attachBaseContext and need the real recreate.
     */
    private fun staggerRecreate(activities: List<Activity>, softMain: Boolean) {
        activities.forEachIndexed { index, activity ->
            if (softMain && activity is MainActivity) {
                activity.requestSoftRecreate()
                return@forEachIndexed
            }
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
        fun newDesign() = ThemeSettingsDesign(this, uiStore) { syncNightModeFromUiStore() }
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
                                softMain = true,
                            )
                            val old = current
                            current = newDesign()
                            design = current
                            old.disposeForReplace()
                        }
                        ThemeSettingsDesign.Request.ReCreateAllActivities -> {
                            // Font scale / reset — attachBaseContext must re-run, soft path can't.
                            staggerRecreate(ApplicationObserver.createdActivities.toList(), softMain = false)
                        }
                    }
                }
            }
        }
    }
}
