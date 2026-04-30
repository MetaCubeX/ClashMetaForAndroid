package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.dialog.showAboutDialog
import com.github.kr328.clash.util.AppUpdateChecker
import com.github.kr328.clash.util.GitHubReleaseUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
                        SettingsDesign.Request.StartLanguage ->
                            startActivity(LanguageSettingsActivity::class.intent)
                        SettingsDesign.Request.StartTheme ->
                            startActivity(ThemeSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAppRouting ->
                            startActivity(AccessControlActivity::class.intent)
                        SettingsDesign.Request.StartFeatures ->
                            startActivity(FeaturesSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAdvanced ->
                            startActivity(FeaturesSettingsActivity::class.intent)
                        SettingsDesign.Request.StartLogs ->
                            startActivity(LogsActivity::class.intent)
                        SettingsDesign.Request.StartAbout ->
                            showAboutDialog(
                                context = this@SettingsActivity,
                                versionName = queryAppVersionName(),
                                coreVersion = queryCoreVersionName(),
                                onCheckUpdates = { setLoading, setStatus ->
                                    launch {
                                        setStatus(null)
                                        setLoading(true)
                                        try {
                                            checkForUpdates(setStatus)
                                        } finally {
                                            setLoading(false)
                                        }
                                    }
                                },
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

    private suspend fun checkForUpdates(setStatus: (String?) -> Unit) {
        val latest = GitHubReleaseUpdate.fetchLatest()
        if (latest == null) {
            setStatus(getString(com.github.kr328.clash.design.R.string.about_update_check_failed))
            return
        }

        val current = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }
        val hasUpdate = GitHubReleaseUpdate.compareVersions(latest.tagName, current) > 0
        if (!hasUpdate) {
            setStatus(getString(com.github.kr328.clash.design.R.string.about_update_latest))
            return
        }

        setStatus(getString(com.github.kr328.clash.design.R.string.about_update_available, latest.tagName))
        AppUpdateChecker.showUpdateNotification(this, latest)
    }
}
