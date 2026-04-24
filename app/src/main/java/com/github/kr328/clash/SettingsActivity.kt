package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.dialog.showAboutDialog
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(this)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartTheme ->
                            startActivity(ThemeSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAppRouting ->
                            startActivity(AccessControlActivity::class.intent)
                        SettingsDesign.Request.StartFeatures ->
                            startActivity(FeaturesSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAdvanced ->
                            startActivity(AdvancedSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAnnouncement ->
                            startActivity(AnnouncementSettingsActivity::class.intent)
                        SettingsDesign.Request.StartLogs ->
                            startActivity(LogsActivity::class.intent)
                        SettingsDesign.Request.StartAbout ->
                            showAboutDialog(
                                context = this@SettingsActivity,
                                versionName = queryAppVersionName(),
                                coreVersion = queryCoreVersionName(),
                                supportUrl = UiStore(this@SettingsActivity).supportUrl
                                    .takeIf { it.isNotBlank() },
                            )
                    }
                }
            }
        }
    }

    private suspend fun queryAppVersionName(): String = withContext(Dispatchers.IO) {
        val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        val semver = Regex("""(\d+\.\d+\.\d+)""").find(raw)?.groupValues?.getOrNull(1) ?: raw
        val channel = if (BuildConfig.DEBUG) "Debug" else "Release"
        "$semver.$channel"
    }

    private suspend fun queryCoreVersionName(): String = withContext(Dispatchers.IO) {
        val raw = Bridge.nativeCoreVersion().replace("_", "-")
        val semver = Regex("""v?(\d+\.\d+\.\d+)""").find(raw)?.groupValues?.getOrNull(1)
        val normalized = semver ?: raw
        "Mihomo $normalized"
    }
}
