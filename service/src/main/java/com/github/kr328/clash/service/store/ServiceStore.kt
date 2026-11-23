package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import com.github.kr328.clash.service.model.AppSetData
import com.github.kr328.clash.service.model.legacyAccessControlNeedsMigration
import universe.agentic.device.clash.McpProtocol

import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

    private val filesDir = context.filesDir
    private val settingsDir = File(filesDir, "settings").apply { mkdirs() }
    private val appListsConfigsFile = File(settingsDir, "app_lists_configs.json")
    private val appSetsFile = File(settingsDir, "app_sets.json")
    private val migrationLockFile = File(settingsDir, "access_control_migration.lock")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = true
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "mips"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    var appsStrategyConfigs: List<AppsStrategyConfig>
        get() {
            if (!appListsConfigsFile.exists()) return emptyList()
            return json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                appListsConfigsFile.readText()
            )
        }
        set(value) {
            appListsConfigsFile.parentFile?.mkdirs()
            val tmp = File(appListsConfigsFile.parentFile, "app_lists_configs.json.tmp")
            tmp.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                    value
                )
            )
            if (!tmp.renameTo(appListsConfigsFile)) {
                tmp.delete()
            }
        }


    var customAppSets: List<AppSetData>
        get() {
            if (!appSetsFile.exists()) return emptyList()
            return runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(AppSetData.serializer()),
                    appSetsFile.readText()
                )
            }.getOrDefault(emptyList())
        }
        set(value) {
            appSetsFile.parentFile?.mkdirs()
            val tmp = File(appSetsFile.parentFile, "app_sets.json.tmp")
            tmp.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AppSetData.serializer()),
                    value
                )
            )
            if (!tmp.renameTo(appSetsFile)) {
                tmp.delete()
            }
        }

    var activeAppsStrategyConfigUuid: UUID? by store.typedString(
        key = "active_apps_strategy_config_uuid",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var mcpEnabled: Boolean by store.boolean(
        key = "mcp_enabled",
        defaultValue = false,
    )

    var mcpPort: Int by store.int(
        key = "mcp_port",
        defaultValue = McpProtocol.DEFAULT_PORT,
    )

    var mcpToken: String by store.string(
        key = "mcp_token",
        defaultValue = "",
    )

    var mcpStatus: String by store.string(
        key = "mcp_status",
        defaultValue = "",
    )

    var legacyAccessControlMigrated: Boolean by store.boolean(
        key = "legacy_access_control_migrated",
        defaultValue = false,
    )

    init {
        migrateLegacyAccessControl(context)
    }

    private fun migrateLegacyAccessControl(context: Context) {
        if (legacyAccessControlMigrated) return
        if (!legacyAccessControlNeedsMigration(accessControlMode, accessControlPackages)) {
            legacyAccessControlMigrated = true
            return
        }
        synchronized(MIGRATION_LOCK) {
            RandomAccessFile(migrationLockFile, "rw").use { access ->
                access.channel.lock().use {
                    if (legacyAccessControlMigrated) return@use
                    if (!legacyAccessControlNeedsMigration(accessControlMode, accessControlPackages)) return@use
                    val configs = appsStrategyConfigs
                    val active = activeAppsStrategyConfigUuid?.let { id -> configs.find { it.uuid == id } }
                    if (active == null) {
                        val migrated = AppsStrategyConfig.create(
                            name = context.getString(R.string.legacy_access_control_name),
                            mode = accessControlMode,
                            packages = accessControlPackages.toList(),
                        )
                        appsStrategyConfigs = configs + migrated
                        activeAppsStrategyConfigUuid = migrated.uuid
                    }
                    accessControlMode = AccessControlMode.AcceptAll
                    accessControlPackages = emptySet()
                    legacyAccessControlMigrated = true
                }
            }
        }
    }

    private companion object {
        val MIGRATION_LOCK = Any()
    }
}
