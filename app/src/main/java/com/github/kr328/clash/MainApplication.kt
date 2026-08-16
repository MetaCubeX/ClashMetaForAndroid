package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.os.Process
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.clashDir
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
            // Setup auto destroy UI observer (must register before Remote.launch
            // so our callback is in the listener list before any visibility event)
            val uiStore = UiStore(this)
            var autoDestroyJob: Job? = null

            // 含未保存编辑或正在等待外部结果（文件选择器、VPN 授权等）的 Activity，
            // 存活时不允许杀 UI 进程
            val protectedActivities: Set<Class<*>> = setOf(
                NewProfileActivity::class.java,
                FilesActivity::class.java,
                LogcatActivity::class.java,
                MetaFeatureSettingsActivity::class.java,
                AccessControlActivity::class.java,
                OverrideSettingsActivity::class.java,
                PropertiesActivity::class.java,
            )

            ApplicationObserver.onVisibleChanged { visible ->
                autoDestroyJob?.cancel()
                autoDestroyJob = null
                if (!visible && uiStore.autoDestroyUI) {
                    autoDestroyJob = Global.launch {
                        // 持续监测：有等待回调/编辑中则等待，无保护条件持续 3 秒才杀
                        var idleMs = 0
                        while (idleMs < 3000) {
                            delay(500)
                            val protectedNow = LogcatService.running ||
                                ApplicationObserver.createdActivities.any { it.javaClass in protectedActivities } ||
                                MainActivity.pendingExternalRequest
                            if (protectedNow) {
                                idleMs = 0
                            } else {
                                idleMs += 500
                            }
                        }
                        Log.d("AutoDestroyUI: Killing UI process to free memory")
                        try {
                            File(clashDir, "auto_destroy.log").appendText(
                                "${java.util.Date()} AutoDestroyUI: Killing UI process\n"
                            )
                        } catch (e: Exception) { }
                        Process.killProcess(Process.myPid())
                    }
                }
            }

            Remote.launch()
        } else {
            sendServiceRecreated()
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val geoipFile = File(clashDir, "geoip.metadb")
        if (geoipFile.exists() && geoipFile.lastModified() < updateDate) {
            geoipFile.delete()
        }
        if (!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it)
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if (geositeFile.exists() && geositeFile.lastModified() < updateDate) {
            geositeFile.delete()
        }
        if (!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it)
            }
        }

        val asnFile = File(clashDir, "ASN.mmdb")
        if (asnFile.exists() && asnFile.lastModified() < updateDate) {
            asnFile.delete()
        }
        if (!asnFile.exists()) {
            FileOutputStream(asnFile).use {
                assets.open("ASN.mmdb").copyTo(it)
            }
        }

        val bundleMRSFile = File(clashDir, "BundleMRS.7z")
        if (bundleMRSFile.exists() && bundleMRSFile.lastModified() < updateDate) {
            bundleMRSFile.delete()
        }
        if (!bundleMRSFile.exists()) {
            FileOutputStream(bundleMRSFile).use {
                assets.open("BundleMRS.7z").copyTo(it)
            }
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
