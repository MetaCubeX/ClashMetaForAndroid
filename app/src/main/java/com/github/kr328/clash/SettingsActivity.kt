package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.dialog.showAboutDialog
import com.github.kr328.clash.util.HttpTextFetcher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.Intent
import android.net.Uri

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
        val latest = withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "https://api.github.com/repos/Nemu-x/ClashFest/releases/latest"
                val text = HttpTextFetcher.fetchUtf8(
                    endpoint,
                    connectTimeoutMs = 15_000,
                    readTimeoutMs = 15_000,
                    headers = mapOf("User-Agent" to "ClashFest/${BuildConfig.VERSION_NAME}"),
                )
                val json = JSONObject(text)
                json.optString("tag_name") to json.optString("html_url")
            }.getOrNull()
        }
        if (latest == null) {
            setStatus(getString(com.github.kr328.clash.design.R.string.about_update_check_failed))
            return
        }

        val current = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }
        val hasUpdate = compareVersions(latest.first, current) > 0
        if (!hasUpdate) {
            setStatus(getString(com.github.kr328.clash.design.R.string.about_update_latest))
            return
        }

        setStatus(getString(com.github.kr328.clash.design.R.string.about_update_available, latest.first))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(com.github.kr328.clash.design.R.string.about_update_available, latest.first))
            .setMessage(getString(com.github.kr328.clash.design.R.string.about_open_release))
            .setPositiveButton(com.github.kr328.clash.design.R.string.about_open_release) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.second)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun compareVersions(left: String, right: String): Int {
        fun semverTriplet(v: String): IntArray? {
            val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return null
            return intArrayOf(
                m.groupValues[1].toIntOrNull() ?: 0,
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].toIntOrNull() ?: 0,
            )
        }

        val a = semverTriplet(left)
        val b = semverTriplet(right)
        if (a != null && b != null) {
            for (i in 0..2) {
                if (a[i] != b[i]) return a[i].compareTo(b[i])
            }
            return 0
        }
        return left.compareTo(right)
    }
}
