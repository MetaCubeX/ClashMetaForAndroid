package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.AppsStrategyDesign
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.requestAppListsConfigEdit
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.webdav.WebdavAutoSync
import com.github.kr328.clash.service.webdav.WebdavSettingsStore
import com.github.kr328.clash.service.webdav.WebdavSync
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*

class AppsStrategyActivity : BaseActivity<AppsStrategyDesign>() {
    override suspend fun main() {
        val design = AppsStrategyDesign(this)

        setContentDesign(design)

        design.fetch()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.fetch()
                        }

                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is AppsStrategyDesign.Request.Choose -> {
                            startActivity(AccessControlActivity::class.intent.setUUID(it.config.uuid))
                        }

                        is AppsStrategyDesign.Request.Edit -> {
                            val result = requestAppListsConfigEdit(
                                initialName = it.config.name,
                                initialMode = it.config.mode,
                                title = getString(com.github.kr328.clash.design.R.string.edit_config_name),
                                hint = getString(com.github.kr328.clash.design.R.string.config_name),
                                showDelete = true
                            )
                            if (result != null) {
                                if (result.deleted) {
                                    deleteConfig(it.config.uuid)
                                    design.fetch()
                                } else if (result.name != it.config.name || result.mode != it.config.mode) {
                                    updateConfig(it.config.uuid, result.name, result.mode)
                                    design.fetch()
                                }
                            }
                        }

                        is AppsStrategyDesign.Request.Active -> {
                            setActiveConfig(it.config.uuid)
                        }

                        AppsStrategyDesign.Request.Create -> {
                            val name = requestModelTextInput(
                                initial = "",
                                title = getString(com.github.kr328.clash.design.R.string.new_config),
                                hint = getString(com.github.kr328.clash.design.R.string.config_name)
                            )
                            if (name.isNotBlank()) {
                                createConfig(name)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun AppsStrategyDesign.fetch() {
        val configs = queryConfigs()
        val activeUuid = queryActiveUuid()
        patchConfigs(configs, activeUuid)
    }

    private suspend fun queryConfigs(): List<AppsStrategyConfig> {
        return withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            service.appsStrategyConfigs
        }
    }

    private suspend fun queryActiveUuid(): UUID? {
        return withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            service.activeAppsStrategyConfigUuid
        }
    }

    private suspend fun setActiveConfig(uuid: UUID) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            service.activeAppsStrategyConfigUuid = uuid
            WebdavAutoSync.noteActiveAppsStrategy(this@AppsStrategyActivity)
            design?.fetch()
            syncIfEnabled()
        }
    }

    private suspend fun deleteConfig(uuid: UUID) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            val configs = service.appsStrategyConfigs.filter { it.uuid != uuid }
            service.appsStrategyConfigs = configs
            if (service.activeAppsStrategyConfigUuid == uuid) {
                service.activeAppsStrategyConfigUuid = null
                WebdavAutoSync.noteActiveAppsStrategy(this@AppsStrategyActivity)
            }
            WebdavAutoSync.noteStrategyDeleted(this@AppsStrategyActivity, uuid)
            syncIfEnabled()
        }
    }

    private suspend fun createConfig(name: String) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            val configs = service.appsStrategyConfigs.toMutableList()
            val newConfig = AppsStrategyConfig(
                uuid = UUID.randomUUID(),
                name = name,
                mode = com.github.kr328.clash.service.model.AccessControlMode.AcceptAll,
                packages = emptyList()
            )
            configs.add(newConfig)
            service.appsStrategyConfigs = configs
            WebdavAutoSync.noteAppsStrategy(this@AppsStrategyActivity, newConfig.uuid)
            design?.fetch()
            syncIfEnabled()
        }
    }

    private suspend fun updateConfig(uuid: UUID, newName: String, newMode: AccessControlMode) {
        withContext(Dispatchers.IO) {
            val service = ServiceStore(this@AppsStrategyActivity)
            val configs = service.appsStrategyConfigs.map { config ->
                if (config.uuid == uuid) {
                    AppsStrategyConfig(
                        uuid = config.uuid,
                        name = newName,
                        mode = newMode,
                        packages = config.packages
                    )
                } else {
                    config
                }
            }
            service.appsStrategyConfigs = configs
            WebdavAutoSync.noteAppsStrategy(this@AppsStrategyActivity, uuid)
            syncIfEnabled()
        }
    }

    private suspend fun syncIfEnabled() {
        val settings = WebdavSettingsStore(this).load()
        if (!settings.autoSync || settings.url.isBlank() || settings.username.isBlank() || settings.password.isEmpty()) {
            return
        }
        withProfile {
            webdavPerform(
                WebdavSync.ACTION_SYNC,
                settings.url,
                settings.username,
                settings.password,
                settings.remotePath,
            )
        }
    }
}
