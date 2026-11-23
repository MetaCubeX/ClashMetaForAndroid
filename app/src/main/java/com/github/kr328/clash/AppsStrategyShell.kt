package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.AppsStrategyDesign
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetIds
import com.github.kr328.clash.design.requestAppListsConfigEdit
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.webdav.WebdavAutoSync
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import com.github.kr328.clash.service.webdav.WebdavSync
import com.github.kr328.clash.util.requestClashReload
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import universe.agentic.clash.AppSetCardUi
import universe.agentic.clash.iosConfirm
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal class AppsStrategyShell(
    private val host: BaseActivity<*>,
    private val embedded: Boolean,
    private val onMounted: suspend (AppsStrategyDesign) -> Unit,
) {
    var page: AppsStrategyDesign? = null
        private set
    private val refreshSignals = Channel<Unit>(Channel.CONFLATED)

    fun poke() {
        refreshSignals.trySend(Unit)
    }

    suspend fun refresh() {
        val current = page ?: return
        val board = withContext(Dispatchers.IO) { loadStrategyBoard() }
        current.patchConfigs(
            configs = board.configs,
            activeUuid = board.activeUuid,
            sets = board.sets,
            covered = board.covered,
        )
    }

    suspend fun run() {
        val design = AppsStrategyDesign(host, embedded)
        page = design
        try {
            onMounted(design)
            refresh()
            while (coroutineContext.isActive) {
                select<Unit> {
                    refreshSignals.onReceive { refresh() }
                    if (!embedded) {
                        host.shellEvents().onReceive {
                            when (it) {
                                BaseActivity.Event.ActivityStart -> refresh()
                                else -> Unit
                            }
                        }
                    }
                    design.requests.onReceive {
                        when (it) {
                            is AppsStrategyDesign.Request.Choose -> {
                                host.startActivity(
                                    AccessControlActivity::class.intent.setUUID(it.uuid),
                                )
                            }
                            is AppsStrategyDesign.Request.Edit -> {
                                val result = host.requestAppListsConfigEdit(
                                    initialName = it.config.name,
                                    initialMode = it.config.mode,
                                    title = host.getString(com.github.kr328.clash.design.R.string.edit_config_name),
                                    hint = host.getString(com.github.kr328.clash.design.R.string.config_name),
                                    showDelete = true,
                                )
                                if (result != null) {
                                    if (result.deleted) {
                                        val confirmed = iosConfirm(
                                            context = host,
                                            title = host.getString(com.github.kr328.clash.design.R.string.delete),
                                            message = it.config.name.ifBlank {
                                                host.getString(com.github.kr328.clash.design.R.string.delete)
                                            },
                                            confirm = host.getString(com.github.kr328.clash.design.R.string.delete),
                                            destructive = true,
                                        )
                                        if (confirmed) {
                                            deleteConfig(it.config.uuid)
                                            refresh()
                                        }
                                    } else if (result.name != it.config.name || result.mode != it.config.mode) {
                                        updateConfig(it.config.uuid, result.name, result.mode)
                                        refresh()
                                    }
                                }
                            }
                            is AppsStrategyDesign.Request.Active -> setActiveConfig(it.uuid)
                            AppsStrategyDesign.Request.Create -> {
                                val name = host.requestModelTextInput(
                                    initial = "",
                                    title = host.getString(com.github.kr328.clash.design.R.string.new_config),
                                    hint = host.getString(com.github.kr328.clash.design.R.string.config_name),
                                )
                                if (name.isNotBlank()) {
                                    createConfig(name)
                                }
                            }
                            AppsStrategyDesign.Request.CreateSet ->
                                host.startActivity(
                                    AccessControlActivity::class.intent.putExtra(
                                        AccessControlActivity.EXTRA_NEW_SET,
                                        true,
                                    ),
                                )
                            is AppsStrategyDesign.Request.EditSet ->
                                host.startActivity(
                                    AccessControlActivity::class.intent.putExtra(
                                        AccessControlActivity.EXTRA_EDIT_SET,
                                        it.id,
                                    ),
                                )
                            is AppsStrategyDesign.Request.DeleteSet -> {
                                if (AppSetIds.isBuiltin(it.id)) {
                                    return@onReceive
                                }
                                val confirmed = iosConfirm(
                                    context = host,
                                    title = host.getString(com.github.kr328.clash.design.R.string.delete_app_set),
                                    message = host.getString(com.github.kr328.clash.design.R.string.confirm_delete_app_set),
                                    confirm = host.getString(com.github.kr328.clash.design.R.string.delete),
                                    destructive = true,
                                )
                                if (confirmed) {
                                    deleteAppSet(it.id)
                                    refresh()
                                }
                            }
                            is AppsStrategyDesign.Request.OpenAccess ->
                                host.startActivity(
                                    AccessControlActivity::class.intent.setUUID(it.uuid),
                                )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            design.cancel()
            if (page === design) page = null
        }
    }

    private data class StrategyBoard(
        val configs: List<AppsStrategyConfig>,
        val activeUuid: UUID?,
        val sets: List<AppSetCardUi>,
        val covered: Map<UUID, String>,
    )

    private fun loadStrategyBoard(): StrategyBoard {
        val service = ServiceStore(host)
        val configs = service.appsStrategyConfigs
        val activeUuid = service.activeAppsStrategyConfigUuid
            ?.takeIf { id -> configs.any { it.uuid == id } }
        val loaded = loadAccessControlApps(
            pm = host.packageManager,
            selfPackage = host.packageName,
            showSystem = true,
            selected = emptySet(),
            sort = AppInfoSort.Label,
            reverse = false,
            domesticCatalog = DomesticAppCatalog.packages(host),
            browserCatalog = BrowserAppCatalog.packages(host),
        )
        val labels = loaded.apps.associate { it.packageName to it.label }
        val sets = assembleAppSets(
            host,
            service.customAppSets,
            loaded.systemPackages,
            loaded.domesticPackages,
            loaded.browserPackages,
        )
        val cards = sets.map { set ->
            AppSetCardUi(
                id = set.id,
                name = set.name,
                description = set.description,
                builtin = set.isBuiltin,
                members = memberPreview(set.packages, labels),
            )
        }
        val joiner = host.getString(com.github.kr328.clash.design.R.string.list_joiner)
        fun coveredText(packages: Collection<String>): String {
            val selected = packages.toSet()
            val names = sets.mapNotNull { set ->
                if (set.packages.isNotEmpty() && set.packages.all { it in selected }) set.name else null
            }
            if (names.isEmpty()) return ""
            return host.getString(
                com.github.kr328.clash.design.R.string.strategy_covered_sets,
                names.joinToString(joiner),
            )
        }
        return StrategyBoard(
            configs = configs,
            activeUuid = activeUuid,
            sets = cards,
            covered = configs.associate { it.uuid to coveredText(it.packages) },
        )
    }

    private fun memberPreview(packages: Set<String>, labels: Map<String, String>): String {
        if (packages.isEmpty()) {
            return host.getString(com.github.kr328.clash.design.R.string.app_set_summary_empty)
        }
        val names = packages.map { labels[it] ?: it }.sorted()
        val head = names.take(3).joinToString(host.getString(com.github.kr328.clash.design.R.string.list_joiner))
        val extra = names.size - 3
        return if (extra > 0) "$head +$extra" else head
    }

    private suspend fun deleteAppSet(id: String) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            service.customAppSets = service.customAppSets.filter { it.id != id }
        }
    }

    private suspend fun setActiveConfig(uuid: UUID?) {
        val changed = withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            val current = service.activeAppsStrategyConfigUuid
            val next = if (current == uuid) null else uuid
            if (current != next) {
                service.activeAppsStrategyConfigUuid = next
                WebdavAutoSync.noteActiveAppsStrategy(host)
                syncIfEnabled()
                true
            } else {
                false
            }
        }
        refresh()
        if (changed && host.shellClashRunning()) {
            host.applicationContext.requestClashReload()
        }
    }

    private suspend fun deleteConfig(uuid: UUID) {
        val wasActive = withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            val configs = service.appsStrategyConfigs.filter { it.uuid != uuid }
            service.appsStrategyConfigs = configs
            val active = service.activeAppsStrategyConfigUuid == uuid
            if (active) {
                service.activeAppsStrategyConfigUuid = null
                WebdavAutoSync.noteActiveAppsStrategy(host)
            }
            WebdavAutoSync.noteStrategyDeleted(host, uuid)
            syncIfEnabled()
            active
        }
        if (wasActive && host.shellClashRunning()) {
            host.applicationContext.requestClashReload()
        }
    }

    private suspend fun createConfig(name: String) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            val configs = service.appsStrategyConfigs.toMutableList()
            val newConfig = AppsStrategyConfig(
                uuid = UUID.randomUUID(),
                name = name,
                mode = AccessControlMode.AcceptAll,
                packages = emptyList(),
            )
            configs.add(newConfig)
            service.appsStrategyConfigs = configs
            WebdavAutoSync.noteAppsStrategy(host, newConfig.uuid)
            syncIfEnabled()
        }
        refresh()
    }

    private suspend fun updateConfig(uuid: UUID, newName: String, newMode: AccessControlMode) {
        val isActive = withContext(Dispatchers.IO) {
            val service = ServiceStore(host)
            val configs = service.appsStrategyConfigs.map { config ->
                if (config.uuid == uuid) {
                    AppsStrategyConfig(
                        uuid = config.uuid,
                        name = newName,
                        mode = newMode,
                        packages = config.packages,
                    )
                } else {
                    config
                }
            }
            service.appsStrategyConfigs = configs
            WebdavAutoSync.noteAppsStrategy(host, uuid)
            syncIfEnabled()
            service.activeAppsStrategyConfigUuid == uuid
        }
        if (isActive && host.shellClashRunning()) {
            host.applicationContext.requestClashReload()
        }
    }

    private suspend fun syncIfEnabled() {
        val settings = WebdavSettingsStore(host).load()
        if (!settings.autoSync || !settings.hasEndpoint() || settings.username.isBlank() || settings.password.isEmpty()) {
            return
        }
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                page?.showExceptionToast(e)
            }
        }
    }
}
