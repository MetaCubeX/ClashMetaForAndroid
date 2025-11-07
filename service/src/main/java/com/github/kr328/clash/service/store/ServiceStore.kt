package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AutoSwitchStrategyType
import com.github.kr328.clash.service.model.WeeklyAutoSwitchSchedule
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

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

    var autoSwitchStrategy: AutoSwitchStrategyType by store.enum(
        key = "auto_switch_strategy",
        defaultValue = AutoSwitchStrategyType.None,
        values = AutoSwitchStrategyType.values(),
    )

    private var autoSwitchWeeklyScheduleRaw: WeeklyAutoSwitchSchedule? by store.typedString(
        key = "auto_switch_weekly_schedule",
        from = {
            if (it.isBlank()) {
                null
            } else {
                runCatching { json.decodeFromString<WeeklyAutoSwitchSchedule>(it) }.getOrNull()
            }
        },
        to = { schedule ->
            schedule?.let { json.encodeToString(it) } ?: ""
        },
    )

    var autoSwitchWeeklySchedule: WeeklyAutoSwitchSchedule
        get() = autoSwitchWeeklyScheduleRaw ?: WeeklyAutoSwitchSchedule()
        set(value) {
            autoSwitchWeeklyScheduleRaw = value
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
