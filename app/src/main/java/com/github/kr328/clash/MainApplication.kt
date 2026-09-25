package com.github.kr328.clash

import android.app.Application
import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import java.io.File

@Suppress("unused")
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
        } else {
            sendServiceRecreated()
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        listOf("geoip.metadb", "geosite.dat", "ASN.mmdb", "BundleMRS.7z").forEach {
            extractAssetIfOutdated(it, updateDate)
        }
    }

    private fun extractAssetIfOutdated(assetName: String, updateDate: Long) {
        val targetFile = File(clashDir, assetName)
        if (targetFile.exists() && targetFile.lastModified() >= updateDate) return

        assets.open(assetName).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
