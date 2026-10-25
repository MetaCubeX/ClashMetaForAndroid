package com.github.kr328.clash

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.AppLanguages
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.installBundledGeoFile

@Suppress("unused")
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        if (base != null) AppLanguages.rememberBaseline(base)
        super.attachBaseContext(base?.let(AppLanguages::wrap))

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiverCompat(
            appLanguageReceiver,
            IntentFilter(Intents.ACTION_APP_LANGUAGE_CHANGED),
            Permissions.RECEIVE_SELF_BROADCASTS,
            exported = false,
        )

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

    private val appLanguageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intents.ACTION_APP_LANGUAGE_CHANGED) return
            if (!intent.hasExtra(Intents.EXTRA_APP_LANGUAGE)) return
            val tag = intent.getStringExtra(Intents.EXTRA_APP_LANGUAGE)
            AppLanguages.write(context, tag.orEmpty())
            AppLanguages.apply(context, tag)
        }
    }
}
