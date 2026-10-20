package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.addCallback
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.AccessControlDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.ios.iosConfirm
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.applyAppSetToggle
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.webdav.WebdavAutoSync
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import com.github.kr328.clash.service.webdav.WebdavSync
import com.github.kr328.clash.util.requestClashReload
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AccessControlActivity : BaseActivity<AccessControlDesign>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    override suspend fun main() {
        val configUuid = intent.uuid
        val service = ServiceStore(this)

        val (config, selected) = withContext(Dispatchers.IO) {
            if (configUuid != null) {
                val cfg = service.appsStrategyConfigs.find { it.uuid == configUuid }
                if (cfg != null) {
                    cfg to cfg.packages.toMutableSet()
                } else {
                    null to service.accessControlPackages.toMutableSet()
                }
            } else {
                null to service.accessControlPackages.toMutableSet()
            }
        }

        defer {
            val running = clashRunning
            val packages = selected.toSet()
            val action = withContext(Dispatchers.IO) {
                persistPackages(service, config, packages, running)
            }
            if (action.restartVpn || action.sync) {
                scheduleExitWork(action)
            }
        }

        if (config != null && config.name.isNotBlank()) {
            title = config.name
        }

        val design = AccessControlDesign(this, uiStore, selected) { members ->
            applyAppSetToggle(members, selected)
        }

        setContentDesign(design)

        design.requests.send(AccessControlDesign.Request.ReloadApps)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        AccessControlDesign.Request.ReloadApps -> {
                            val loaded = loadApps(selected)
                            design.patchApps(loaded.apps)
                            design.patchBuiltinSets(loaded.systemPackages, loaded.domesticPackages)
                        }

                        AccessControlDesign.Request.SelectAll -> {
                            val all = withContext(Dispatchers.Default) {
                                design.apps.map(AppInfo::packageName)
                            }

                            selected.clear()
                            selected.addAll(all)

                            design.rebindAll()
                        }

                        AccessControlDesign.Request.SelectNone -> {
                            selected.clear()

                            design.rebindAll()
                        }

                        AccessControlDesign.Request.SelectInvert -> {
                            val all = withContext(Dispatchers.Default) {
                                design.apps.map(AppInfo::packageName).toSet() - selected
                            }

                            selected.clear()
                            selected.addAll(all)

                            design.rebindAll()
                        }

                        AccessControlDesign.Request.Import -> {
                            val confirmed = iosConfirm(
                                context = this@AccessControlActivity,
                                title = getString(com.github.kr328.clash.design.R.string.import_),
                                message = getString(com.github.kr328.clash.design.R.string.import_from_clipboard),
                                confirm = getString(com.github.kr328.clash.design.R.string.ok),
                                destructive = true,
                            )
                            if (confirmed) {
                                var failed = false
                                try {
                                    val text = getSystemService<ClipboardManager>()
                                        ?.primaryClip
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.text
                                    if (text == null) {
                                        failed = true
                                    } else {
                                        val packages = text.split("\n")
                                            .map { it.replace("\r", "").trim() }
                                            .filter { it.isNotEmpty() }
                                            .toSet()
                                        val matched = design.apps.map(AppInfo::packageName).intersect(packages)
                                        if (matched.isNotEmpty()) {
                                            selected.clear()
                                            selected.addAll(matched)
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failed = true
                                }
                                if (failed) {
                                    design.showToast(DesignR.string.geofile_import_failed, ToastDuration.Long)
                                }
                                design.rebindAll()
                            }
                        }

                        AccessControlDesign.Request.Export -> {
                            val clipboard = getSystemService<ClipboardManager>()

                            val data = ClipData.newPlainText(
                                "packages",
                                selected.joinToString("\n")
                            )

                            clipboard?.setPrimaryClip(data)
                        }
                    }
                }
            }
        }
    }

    private fun persistPackages(
        service: ServiceStore,
        config: AppsStrategyConfig?,
        packages: Set<String>,
        clashRunning: Boolean,
    ): ExitAction {
        val packagesChanged = if (config != null) {
            val changed = packages != config.packages.toSet()
            if (changed) {
                val updatedConfig = AppsStrategyConfig(
                    uuid = config.uuid,
                    name = config.name,
                    mode = config.mode,
                    packages = packages.toList(),
                )
                service.appsStrategyConfigs = service.appsStrategyConfigs.map {
                    if (it.uuid == config.uuid) updatedConfig else it
                }
                WebdavAutoSync.noteAppsStrategy(this, config.uuid)
            }
            changed
        } else {
            val changed = packages != service.accessControlPackages
            if (changed) {
                service.accessControlPackages = packages
                WebdavAutoSync.noteAccessControl(this)
            }
            changed
        }
        if (!packagesChanged) return ExitAction(restartVpn = false, sync = false)
        val activeId = service.activeAppsStrategyConfigUuid
        val active = activeId?.let { id -> service.appsStrategyConfigs.find { it.uuid == id } }
        val drivesVpn = if (config != null) active?.uuid == config.uuid else active == null
        return ExitAction(restartVpn = clashRunning && drivesVpn, sync = true)
    }

    private fun scheduleExitWork(action: ExitAction) {
        val app = applicationContext
        Global.launch(Dispatchers.Main) {
            if (action.restartVpn) {
                try {
                    restartClash(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Restart after access control change: ${e.message}", e)
                }
            }
            if (action.sync) {
                try {
                    syncAccessPackages(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("WebDAV after access control change: ${e.message}", e)
                }
            }
        }
    }

    private fun restartClash(app: Context) {
        Log.i("Reload tun after access control change")
        app.requestClashReload()
    }

    private suspend fun syncAccessPackages(app: Context) {
        val settings = WebdavSettingsStore(app).load()
        if (!settings.autoSync || !settings.hasEndpoint() || settings.username.isBlank() || settings.password.isEmpty()) {
            return
        }
        withTimeoutOrNull(SYNC_TIMEOUT_MS) {
            withProfile {
                webdavPerform(
                    WebdavSync.ACTION_SYNC,
                    settings.url,
                    settings.lanUrl,
                    settings.username,
                    settings.password,
                    settings.remotePath,
                )
            }
        }
    }

    private data class ExitAction(
        val restartVpn: Boolean,
        val sync: Boolean,
    )

    private suspend fun loadApps(selected: Set<String>): AccessControlApps =
        withContext(Dispatchers.IO) {
            loadAccessControlApps(
                pm = packageManager,
                selfPackage = packageName,
                showSystem = uiStore.accessControlSystemApp,
                selected = selected,
                sort = uiStore.accessControlSort,
                reverse = uiStore.accessControlReverse,
                domesticCatalog = DomesticAppCatalog.packages(this@AccessControlActivity),
            )
        }

    private companion object {
        const val SYNC_TIMEOUT_MS = 60_000L
    }
}