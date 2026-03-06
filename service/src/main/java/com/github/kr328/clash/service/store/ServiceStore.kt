package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppsStrategyConfig
import kotlinx.serialization.json.Json
import java.io.File
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
        defaultValue = "system"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    var appsStrategyConfigs: List<AppsStrategyConfig>
        get() {
            return if (appListsConfigsFile.exists()) {
                try {
                    json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                        appListsConfigsFile.readText()
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        set(value) {
            appListsConfigsFile.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AppsStrategyConfig.serializer()),
                    value
                )
            )
        }

    var activeAppsStrategyConfigUuid: UUID? by store.typedString(
        key = "active_apps_strategy_config_uuid",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )
}