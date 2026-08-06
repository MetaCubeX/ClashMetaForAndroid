package com.github.kr328.clash

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.DiagnosticsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.logsDir
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticsActivity : BaseActivity<DiagnosticsDesign>() {
    override suspend fun main() {
        val design = DiagnosticsDesign(this, fetchState())

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStart,
                        Event.ClashStop -> design.patch(fetchState())
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        DiagnosticsDesign.Request.EnableDebug -> enableDebug(design)
                        DiagnosticsDesign.Request.DisableDebug -> disableDebug(design)
                        DiagnosticsDesign.Request.Export -> exportDiagnostics(design)
                    }
                }
            }
        }
    }

    private suspend fun fetchState(): DiagnosticsDesign.State {
        val store = ServiceStore(this)

        val override = withClash {
            queryOverride(Clash.OverrideSlot.Persist)
        }

        val mode = if (clashRunning) {
            try {
                withClash {
                    queryTunnelState().mode.name
                }
            } catch (e: Exception) {
                Log.w("Query tunnel state: $e")

                null
            }
        } else {
            null
        }

        val connections = if (clashRunning) {
            try {
                val json = withClash {
                    Clash.queryConnections()
                }

                Json.decodeFromString(JsonArray.serializer(), json).size
            } catch (e: Exception) {
                Log.w("Query connections: $e")

                0
            }
        } else {
            0
        }

        return DiagnosticsDesign.State(
            clashRunning = clashRunning,
            appVersion = BuildConfig.VERSION_NAME,
            coreVersion = if (clashRunning) Bridge.nativeCoreVersion() else "-",
            mode = mode,
            tunStack = store.tunStackMode,
            logLevel = override.logLevel?.name,
            dnsEnhancedMode = override.dns.enhancedMode?.name,
            ipv6 = override.ipv6?.toString(),
            connections = connections,
        )
    }

    private suspend fun enableDebug(design: DiagnosticsDesign) {
        patchLogLevel(LogMessage.Level.Debug)

        startForegroundServiceCompat(LogcatService::class.intent)

        design.showToast(DesignR.string.diagnostics_debug_enabled, ToastDuration.Short)

        design.patch(fetchState())
    }

    private suspend fun disableDebug(design: DiagnosticsDesign) {
        patchLogLevel(null)

        stopService(LogcatService::class.intent)

        design.showToast(DesignR.string.diagnostics_debug_disabled, ToastDuration.Short)

        design.patch(fetchState())
    }

    private suspend fun patchLogLevel(level: LogMessage.Level?) {
        withClash {
            val configuration = queryOverride(Clash.OverrideSlot.Persist)

            patchOverride(
                Clash.OverrideSlot.Persist,
                configuration.copy(logLevel = level)
            )
        }
    }

    private suspend fun exportDiagnostics(design: DiagnosticsDesign) {
        val bundle = buildDiagnosticsBundle()

        if (bundle == null) {
            design.showToast(DesignR.string.diagnostics_export_failed, ToastDuration.Short)

            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", bundle)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, getString(DesignR.string.diagnostics_export)))
    }

    private suspend fun buildDiagnosticsBundle(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(cacheDir, "diagnostics").apply { mkdirs() }

            dir.listFiles()?.forEach { it.delete() }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

            val zip = File(dir, "diagnostics-$timestamp.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
                zos.writeEntry("info.txt", buildInfoText())

                if (clashRunning) {
                    zos.writeEntry("connections.json", Clash.queryConnections())
                }

                logsDir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { logFile ->
                        zos.putNextEntry(ZipEntry("logs/${logFile.name}"))

                        logFile.inputStream().use { it.copyTo(zos) }

                        zos.closeEntry()
                    }
            }

            zip
        } catch (e: Exception) {
            Log.e("Build diagnostics bundle: $e", e)

            null
        }
    }

    private suspend fun buildInfoText(): String {
        val store = ServiceStore(this)

        val builder = StringBuilder()

        builder.appendLine("ClashMetaForAndroid diagnostics")
        builder.appendLine("Generated: ${Date()}")
        builder.appendLine()

        builder.appendLine("App version: ${BuildConfig.VERSION_NAME}")
        builder.appendLine("Core version: ${if (clashRunning) Bridge.nativeCoreVersion() else "-"}")
        builder.appendLine("Clash running: $clashRunning")
        builder.appendLine()

        builder.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        builder.appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
        builder.appendLine()

        builder.appendLine("TUN stack: ${store.tunStackMode}")

        try {
            val override = withClash {
                queryOverride(Clash.OverrideSlot.Persist)
            }

            builder.appendLine("Mode: ${override.mode?.name}")
            builder.appendLine("Log level: ${override.logLevel?.name}")
            builder.appendLine("IPv6: ${override.ipv6}")
            builder.appendLine("DNS enhanced mode: ${override.dns.enhancedMode?.name}")
            builder.appendLine("DNS enable: ${override.dns.enable}")
            builder.appendLine("DNS prefer H3: ${override.dns.preferH3}")
        } catch (e: Exception) {
            builder.appendLine("Query override failed: $e")
        }

        return builder.toString()
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))

        write(content.toByteArray())

        closeEntry()
    }
}
