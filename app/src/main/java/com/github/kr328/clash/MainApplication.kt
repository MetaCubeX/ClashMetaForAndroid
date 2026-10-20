package com.github.kr328.clash

import android.app.Application
import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.installBundledGeoFile

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
        installBundledGeoFile(clashDir, "geoip.metadb", updateDate, true) {
            assets.open("geoip.metadb")
        }
        installBundledGeoFile(clashDir, "geosite.dat", updateDate, true) {
            assets.open("geosite.dat")
        }
        installBundledGeoFile(clashDir, "ASN.mmdb", updateDate, true) {
            assets.open("ASN.mmdb")
        }
        installBundledGeoFile(clashDir, "BundleMRS.7z", updateDate, false) {
            assets.open("BundleMRS.7z")
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
