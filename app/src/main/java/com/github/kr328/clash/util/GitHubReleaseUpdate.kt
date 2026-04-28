package com.github.kr328.clash.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.github.kr328.clash.BuildConfig
import com.github.kr328.clash.common.network.AppNetworkDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * GitHub Releases API helper for ClashFest updates (same endpoint as About / periodic checker).
 */
object GitHubReleaseUpdate {

    private const val PREFS_NAME = "app_update"
    private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"

    data class Info(
        val tagName: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val apkName: String?,
    )

    suspend fun fetchLatest(): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "https://api.github.com/repos/Nemu-x/ClashFest/releases/latest"
            val text = HttpTextFetcher.fetchUtf8(
                endpoint,
                connectTimeoutMs = AppNetworkDefaults.CONNECT_TIMEOUT_MS,
                readTimeoutMs = AppNetworkDefaults.READ_TIMEOUT_MS,
                headers = mapOf("User-Agent" to "ClashFest/${BuildConfig.VERSION_NAME}"),
            )
            val json = JSONObject(text)
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null

            if (assets != null) {
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until assets.length()) {
                    val item = assets.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    val url = item.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        candidates += item
                    }
                }
                val picked = candidates.firstOrNull {
                    val n = it.optString("name").lowercase(Locale.ROOT)
                    n.contains("alpha") && n.contains("universal")
                } ?: candidates.firstOrNull {
                    val n = it.optString("name").lowercase(Locale.ROOT)
                    n.contains("alpha") && n.contains("arm64-v8a")
                } ?: candidates.firstOrNull()

                apkUrl = picked?.optString("browser_download_url")
                apkName = picked?.optString("name")
            }

            Info(
                tagName = json.optString("tag_name"),
                body = json.optString("body"),
                htmlUrl = json.optString("html_url"),
                apkUrl = apkUrl,
                apkName = apkName,
            )
        }.getOrNull()
    }

    /**
     * Enqueues an APK download via [DownloadManager]. Persists [KEY_PENDING_DOWNLOAD_ID] for install flow.
     * @return download id, or -1 if enqueue failed.
     */
    fun enqueueApkDownload(context: Context, info: Info): Long {
        val url = info.apkUrl ?: return -1L
        val dm = context.getSystemService(DownloadManager::class.java) ?: return -1L

        val fileName = (info.apkName ?: "clashfest-${info.tagName}.apk")
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(com.github.kr328.clash.design.R.string.about_update_available, info.tagName))
            .setDescription(context.getString(com.github.kr328.clash.design.R.string.about_download_started))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val id = dm.enqueue(request)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, id)
            .apply()
        return id
    }

    fun compareVersions(left: String, right: String): Int {
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
