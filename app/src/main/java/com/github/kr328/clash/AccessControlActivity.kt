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
import com.github.kr328.clash.design.R as DesignR
import universe.agentic.clash.iosConfirm
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetIds
import com.github.kr328.clash.design.model.planBuiltinSetWrite
import com.github.kr328.clash.design.model.applyAppSetToggle
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.AppSetData
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
import java.util.UUID

class AccessControlActivity : BaseActivity<AccessControlDesign>() {
    private var selectedPackages: MutableSet<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        selectedPackages = savedInstanceState
            ?.getStringArrayList(STATE_SELECTED)
            ?.let { LinkedHashSet(it) }
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPackages?.let { outState.putStringArrayList(STATE_SELECTED, ArrayList(it)) }
    }

    override suspend fun main() {
        val configUuid = intent.uuid
        val editSetId = intent.getStringExtra(EXTRA_EDIT_SET)
        val createSet = intent.getBooleanExtra(EXTRA_NEW_SET, false) && editSetId == null
        val service = ServiceStore(this)

        var loadedSystemPackages = emptySet<String>()
        var loadedDomesticPackages = emptySet<String>()
        var loadedBrowserPackages = emptySet<String>()

        val restored = selectedPackages
        val loaded = withContext(Dispatchers.IO) {
            if (configUuid != null) {
                val found = service.appsStrategyConfigs.find { it.uuid == configUuid }
                    ?: return@withContext null
                found to (restored ?: found.packages.toMutableSet())
            } else {
                null to (restored ?: mutableSetOf<String>())
            }
        } ?: run {
            finish()
            return
        }
        val (config, selected) = loaded
        selectedPackages = selected

        defer {
            if (config == null) return@defer
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
        } else if (editSetId != null) {
            title = getString(DesignR.string.app_sets)
        }

        val design = AccessControlDesign(this, uiStore, selected) { members ->
            applyAppSetToggle(members, selected)
        }
        design.openSetId = editSetId
        design.createSet = createSet
        design.appSetsOnly = editSetId != null || createSet
        design.closeWhenEditorCloses = editSetId != null || createSet

        setContentDesign(design)

        design.requests.send(AccessControlDesign.Request.ReloadApps)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        AccessControlDesign.Request.ReloadApps -> {
                            design.clearEditorApps()
                            val loaded = loadApps(selected)
                            loadedSystemPackages = loaded.systemPackages
                            loadedDomesticPackages = loaded.domesticPackages
                            loadedBrowserPackages = loaded.browserPackages
                            design.patchApps(loaded.apps)
                            patchSets(
                                service,
                                loadedSystemPackages,
                                loadedDomesticPackages,
                                loadedBrowserPackages,
                                design,
                            )
                        }

                        is AccessControlDesign.Request.SaveAppSet -> {
                            withContext(Dispatchers.IO) {
                                val custom = service.customAppSets.toMutableList()
                                val targetId = it.id ?: UUID.randomUUID().toString()
                                val index = custom.indexOfFirst { c -> c.id == targetId }
                                if (AppSetIds.isBuiltin(targetId)) {
                                    val planned = planBuiltinSetWrite(
                                        defaultName = builtinAppSetFallbackName(
                                            this@AccessControlActivity,
                                            targetId,
                                        ),
                                        name = it.name,
                                        description = it.description,
                                        packages = it.packages,
                                        membershipFrozen = it.membershipFrozen,
                                    )
                                    if (planned == null) {
                                        if (index >= 0) custom.removeAt(index)
                                    } else {
                                        val record = AppSetData(
                                            id = targetId,
                                            name = planned.name,
                                            packages = planned.packages,
                                            isBuiltin = true,
                                            description = planned.description,
                                            membershipFrozen = planned.membershipFrozen,
                                        )
                                        if (index >= 0) custom[index] = record else custom.add(record)
                                    }
                                } else {
                                    val name = it.name.trim()
                                    if (name.isNotEmpty()) {
                                        val record = AppSetData(
                                            id = targetId,
                                            name = name,
                                            packages = it.packages,
                                            isBuiltin = false,
                                            description = it.description.trim(),
                                            membershipFrozen = false,
                                        )
                                        if (index >= 0) custom[index] = record else custom.add(record)
                                    }
                                }
                                service.customAppSets = custom
                            }
                            patchSets(
                                service,
                                loadedSystemPackages,
                                loadedDomesticPackages,
                                loadedBrowserPackages,
                                design,
                            )
                            if (design.closeWhenEditorCloses) finish()
                        }

                        is AccessControlDesign.Request.DeleteAppSet -> {
                            withContext(Dispatchers.IO) {
                                service.customAppSets = service.customAppSets.filter { c -> c.id != it.id }
                            }
                            patchSets(
                                service,
                                loadedSystemPackages,
                                loadedDomesticPackages,
                                loadedBrowserPackages,
                                design,
                            )
                            if (design.closeWhenEditorCloses) finish()
                        }

                        AccessControlDesign.Request.LoadEditorApps -> {
                            val apps = withContext(Dispatchers.IO) {
                                loadAccessControlApps(
                                    pm = packageManager,
                                    selfPackage = packageName,
                                    showSystem = true,
                                    selected = emptySet(),
                                    sort = AppInfoSort.Label,
                                    reverse = false,
                                    domesticCatalog = DomesticAppCatalog.packages(this@AccessControlActivity),
                                    browserCatalog = BrowserAppCatalog.packages(this@AccessControlActivity),
                                ).apps
                            }
                            design.patchEditorApps(apps)
                        }

                        AccessControlDesign.Request.SelectAll -> {
                            val visible = design.apps.map(AppInfo::packageName)
                            val next = selectAllVisible(selected, visible)
                            selected.clear()
                            selected.addAll(next)
                            design.rebindAll()
                        }

                        AccessControlDesign.Request.SelectNone -> {
                            selected.clear()

                            design.rebindAll()
                        }

                        AccessControlDesign.Request.SelectInvert -> {
                            val visible = design.apps.map(AppInfo::packageName)
                            val next = invertVisible(selected, visible)
                            selected.clear()
                            selected.addAll(next)
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
                                        val visible = design.apps.map(AppInfo::packageName).toSet()
                                        val installed = withContext(Dispatchers.IO) {
                                            packageManager.getInstalledPackages(0).mapTo(HashSet()) {
                                                it.packageName
                                            }
                                        }
                                        val next = importAccessSelection(
                                            selected,
                                            visible,
                                            installed,
                                            clipboardPackageNames(text),
                                        )
                                        if (next !== selected) {
                                            selected.clear()
                                            selected.addAll(next)
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

    private suspend fun patchSets(
        service: ServiceStore,
        systemPackages: Set<String>,
        domesticPackages: Set<String>,
        browserPackages: Set<String>,
        design: AccessControlDesign,
    ) {
        val customSets = withContext(Dispatchers.IO) { service.customAppSets }
        design.patchAppSets(
            assembleAppSets(
                this,
                customSets,
                systemPackages,
                domesticPackages,
                browserPackages,
            ),
        )
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
            false
        }
        if (!packagesChanged) return ExitAction(restartVpn = false, sync = false)
        val active = service.activeAppsStrategyConfigUuid
            ?.let { id -> service.appsStrategyConfigs.find { it.uuid == id } }
        val drivesVpn = active?.uuid == config?.uuid
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
        withProfile {
            webdavPerform(
                WebdavSync.ACTION_SCHEDULE,
                settings.url,
                settings.lanUrl,
                settings.username,
                settings.password,
                settings.remotePath,
            )
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
                browserCatalog = BrowserAppCatalog.packages(this@AccessControlActivity),
            )
        }

    companion object {
        const val EXTRA_EDIT_SET = "edit_set_id"
        const val EXTRA_NEW_SET = "new_app_set"
        private const val STATE_SELECTED = "access_control_selected"
    }
}
