package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.LandingProxy
import com.github.kr328.clash.service.model.LandingProxyType
import kotlinx.serialization.json.Json
import java.util.*

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
        defaultValue = "mips"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    var killSwitch by store.boolean(
        key = "kill_switch",
        defaultValue = false
    )

    var landingEnabled by store.boolean(
        key = "landing_enabled",
        defaultValue = false
    )

    var landingType: LandingProxyType by store.enum(
        key = "landing_type",
        defaultValue = LandingProxyType.Socks5,
        values = LandingProxyType.values()
    )

    var landingServer by store.string(
        key = "landing_server",
        defaultValue = ""
    )

    var landingPort by store.int(
        key = "landing_port",
        defaultValue = 0
    )

    var landingUsername by store.string(
        key = "landing_username",
        defaultValue = ""
    )

    var landingPassword by store.string(
        key = "landing_password",
        defaultValue = ""
    )

    var landingFrontGroup by store.string(
        key = "landing_front_group",
        defaultValue = ""
    )

    var landingSkipCertVerify by store.boolean(
        key = "landing_skip_cert_verify",
        defaultValue = false
    )

    var landingSni by store.string(
        key = "landing_sni",
        defaultValue = ""
    )

    var landingUdp by store.boolean(
        key = "landing_udp",
        defaultValue = true
    )

    /**
     * The landing proxy as JSON for [com.github.kr328.clash.core.Clash.setLandingProxy],
     * or null when disabled.
     */
    fun landingProxyJson(): String? {
        if (!landingEnabled)
            return null

        return Json.encodeToString(
            LandingProxy.serializer(),
            LandingProxy(
                type = landingType.wireName,
                server = landingServer.trim(),
                port = landingPort,
                username = landingUsername,
                password = landingPassword,
                frontGroup = landingFrontGroup,
                skipCertVerify = landingSkipCertVerify,
                sni = landingSni.trim(),
                udp = landingUdp,
            )
        )
    }
}