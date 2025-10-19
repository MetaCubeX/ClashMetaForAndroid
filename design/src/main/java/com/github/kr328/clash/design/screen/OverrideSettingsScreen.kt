package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.OverrideSettingsDesign
import com.github.kr328.clash.design.components.*
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.util.*
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Restore
import top.yukonga.miuix.kmp.icon.icons.useful.Save

@Composable
fun OverrideSettingsScreen(design: OverrideSettingsDesign) {
    val dialogs = design.dialogManager

    val config = design.config
    val onConfigChange: (ConfigurationOverride) -> Unit = { newConfig ->
        design.config = newConfig
        design.onConfigChange(newConfig)
    }
    val onRequestClose: () -> Unit = {
        design.requests.trySend(OverrideSettingsDesign.Request.Close)
        Unit
    }
    val onRequestReset: () -> Unit = {
        design.requests.trySend(OverrideSettingsDesign.Request.ResetOverride)
        Unit
    }
    val onRequestSave: () -> Unit = {
        design.requests.trySend(OverrideSettingsDesign.Request.Save)
        Unit
    }

    // 使用共享映射
    val modeMapping = CommonMappings.tunnelMode
    val logLevelMapping = CommonMappings.logLevel

    val sections = settingsList {
        section(MLang.section_general) {
            arrow(
                title = MLang.http_port,
                onClick = { dialogs.getDialog(DialogKeys.HTTP_PORT).open() },
                summary = config.httpPort.toPortSummary()
            )
            arrow(
                title = MLang.socks_port,
                onClick = { dialogs.getDialog(DialogKeys.SOCKS_PORT).open() },
                summary = config.socksPort.toPortSummary()
            )
            arrow(
                title = MLang.redirect_port,
                onClick = { dialogs.getDialog(DialogKeys.REDIRECT_PORT).open() },
                summary = config.redirectPort.toPortSummary()
            )
            arrow(
                title = MLang.tproxy_port,
                onClick = { dialogs.getDialog(DialogKeys.TPROXY_PORT).open() },
                summary = config.tproxyPort.toPortSummary()
            )
            arrow(
                title = MLang.mixed_port,
                onClick = { dialogs.getDialog(DialogKeys.MIXED_PORT).open() },
                summary = config.mixedPort.toPortSummary()
            )
            arrow(
                title = MLang.authentication,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditAuthentication); Unit },
                summary = config.authentication.toItemsSummary()
            )
            triState(
                title = MLang.allow_lan,
                value = config.allowLan,
                onChange = { v -> onConfigChange(config.copy(allowLan = v)) }
            )
            triState(
                title = MLang.ipv6,
                value = config.ipv6,
                onChange = { v -> onConfigChange(config.copy(ipv6 = v)) }
            )
            arrow(
                title = MLang.bind_address,
                onClick = { dialogs.getDialog(DialogKeys.BIND_ADDRESS).open() },
                summary = config.bindAddress.toStringSummary()
            )
            arrow(
                title = MLang.external_controller,
                onClick = { dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER).open() },
                summary = config.externalController.toStringSummary()
            )
            arrow(
                title = MLang.external_controller_tls,
                onClick = { dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER_TLS).open() },
                summary = config.externalControllerTLS.toStringSummary()
            )
            arrow(
                title = MLang.external_controller_allow_origins,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditCorsOrigins); Unit },
                summary = config.externalControllerCors.allowOrigins.toDomainsSummary()
            )
            triState(
                title = MLang.external_controller_allow_private,
                value = config.externalControllerCors.allowPrivateNetwork,
                onChange = { v ->
                    onConfigChange(
                        config.copy(
                            externalControllerCors = config.externalControllerCors.copy(
                                allowPrivateNetwork = v
                            )
                        )
                    )
                }
            )
            arrow(
                title = MLang.secret,
                onClick = { dialogs.getDialog(DialogKeys.SECRET).open() },
                summary = config.secret.toSecretSummary()
            )
            dropdown(
                title = MLang.mode,
                mapping = modeMapping,
                value = config.mode,
                onChange = { mode -> onConfigChange(config.copy(mode = mode)) }
            )
            dropdown(
                title = MLang.log_level,
                mapping = logLevelMapping,
                value = config.logLevel,
                onChange = { level -> onConfigChange(config.copy(logLevel = level)) }
            )
            arrow(
                title = MLang.hosts,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditHosts); Unit },
                summary = config.hosts.toMappingsSummary()
            )
        }
        section(MLang.section_dns) {
            triState(
                title = MLang.sniffer,
                value = config.sniffer.enable,
                onChange = { v -> onConfigChange(config.copy(sniffer = config.sniffer.copy(enable = v))) }
            )
            triState(
                title = MLang.h3_prefer,
                value = config.dns.preferH3,
                onChange = { v -> onConfigChange(config.copy(dns = config.dns.copy(preferH3 = v))) }
            )
            arrow(
                title = MLang.dns_listen,
                onClick = { dialogs.getDialog(DialogKeys.DNS_LISTEN).open() },
                summary = config.dns.listen.toStringSummary()
            )
            triState(
                title = MLang.append_system_dns,
                value = config.app.appendSystemDns,
                onChange = { v -> onConfigChange(config.copy(app = config.app.copy(appendSystemDns = v))) }
            )
            triState(
                title = MLang.dns_ipv6,
                value = config.dns.ipv6,
                onChange = { v -> onConfigChange(config.copy(dns = config.dns.copy(ipv6 = v))) }
            )
            triState(
                title = MLang.use_hosts,
                value = config.dns.useHosts,
                onChange = { v -> onConfigChange(config.copy(dns = config.dns.copy(useHosts = v))) }
            )
            dropdown(
                title = MLang.enhanced_mode,
                items = listOf(
                    MLang.enhanced_none,
                    MLang.enhanced_fakeip,
                    MLang.enhanced_mapping,
                    MLang.tristate_not_modify
                ),
                selectedIndex = when (config.dns.enhancedMode) {
                    ConfigurationOverride.DnsEnhancedMode.None -> 0
                    ConfigurationOverride.DnsEnhancedMode.FakeIp -> 1
                    ConfigurationOverride.DnsEnhancedMode.Mapping -> 2
                    null -> 3
                },
                onSelectedIndexChange = { idx ->
                    val newMode = when (idx) {
                        0 -> ConfigurationOverride.DnsEnhancedMode.None
                        1 -> ConfigurationOverride.DnsEnhancedMode.FakeIp
                        2 -> ConfigurationOverride.DnsEnhancedMode.Mapping
                        else -> null
                    }
                    onConfigChange(config.copy(dns = config.dns.copy(enhancedMode = newMode)))
                }
            )
            arrow(
                title = MLang.name_server,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditNameServers); Unit },
                summary = config.dns.nameServer.toServersSummary()
            )
            arrow(
                title = MLang.fallback_name_server,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditFallbackDns); Unit },
                summary = config.dns.fallback.toServersSummary()
            )
            arrow(
                title = MLang.default_name_server,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditDefaultDns); Unit },
                summary = config.dns.defaultServer.toServersSummary()
            )
            arrow(
                title = MLang.fakeip_filter,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditFakeIpFilter); Unit },
                summary = config.dns.fakeIpFilter.toDomainsSummary()
            )
            dropdown(
                title = MLang.fakeip_filter_mode,
                items = listOf(MLang.filter_blacklist, MLang.filter_whitelist, MLang.tristate_not_modify),
                selectedIndex = when (config.dns.fakeIPFilterMode) {
                    ConfigurationOverride.FilterMode.BlackList -> 0
                    ConfigurationOverride.FilterMode.WhiteList -> 1
                    null -> 2
                },
                onSelectedIndexChange = { idx ->
                    val newMode = when (idx) {
                        0 -> ConfigurationOverride.FilterMode.BlackList
                        1 -> ConfigurationOverride.FilterMode.WhiteList
                        else -> null
                    }
                    onConfigChange(config.copy(dns = config.dns.copy(fakeIPFilterMode = newMode)))
                }
            )
            triState(
                title = MLang.geoip_fallback,
                value = config.dns.fallbackFilter.geoIp,
                onChange = { v ->
                    onConfigChange(
                        config.copy(
                            dns = config.dns.copy(
                                fallbackFilter = config.dns.fallbackFilter.copy(
                                    geoIp = v
                                )
                            )
                        )
                    )
                }
            )
            arrow(
                title = MLang.geoip_fallback_whitelist,
                onClick = { dialogs.getDialog(DialogKeys.GEOIP_CODE).open() },
                summary = config.dns.fallbackFilter.geoIpCode.toStringSummary()
            )
            arrow(
                title = MLang.domain_fallback,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditFallbackDomains); Unit },
                summary = config.dns.fallbackFilter.domain.toDomainsSummary()
            )
            arrow(
                title = MLang.ipcidr_fallback,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditFallbackIpCidr); Unit },
                summary = config.dns.fallbackFilter.ipcidr.toSegmentsSummary()
            )
            arrow(
                title = MLang.nameserver_policy,
                onClick = { design.requests.trySend(OverrideSettingsDesign.Request.EditNameServerPolicy); Unit },
                summary = config.dns.nameserverPolicy.toPoliciesSummary()
            )
        }
    }

    BaseSettingsScreen(
        title = MLang.override_page_title,
        sections = sections,
        onBack = onRequestClose,
        actions = {
            IconButton(
                modifier = Modifier.padding(end = AppDimensions.spacing_xxl),
                onClick = onRequestSave
            ) {
                Icon(MiuixIcons.Useful.Save, contentDescription = MLang.action_save)
            }
            IconButton(
                modifier = Modifier.padding(end = AppDimensions.spacing_lg),
                onClick = { dialogs.getDialog(DialogKeys.RESET).open() }
            ) {
                Icon(MiuixIcons.Useful.Restore, contentDescription = MLang.action_reset)
            }
        },
        dialogs = {
            // 端口输入对话框
            PortInputDialog(
                title = MLang.http_port,
                state = dialogs.getDialog(DialogKeys.HTTP_PORT),
                currentValue = config.httpPort,
                onConfirm = { value ->
                    onConfigChange(config.copy(httpPort = value))
                    dialogs.getDialog(DialogKeys.HTTP_PORT).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.HTTP_PORT).close() }
            )

            PortInputDialog(
                title = MLang.socks_port,
                state = dialogs.getDialog(DialogKeys.SOCKS_PORT),
                currentValue = config.socksPort,
                onConfirm = { value ->
                    onConfigChange(config.copy(socksPort = value))
                    dialogs.getDialog(DialogKeys.SOCKS_PORT).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.SOCKS_PORT).close() }
            )

            PortInputDialog(
                title = MLang.redirect_port,
                state = dialogs.getDialog(DialogKeys.REDIRECT_PORT),
                currentValue = config.redirectPort,
                onConfirm = { value ->
                    onConfigChange(config.copy(redirectPort = value))
                    dialogs.getDialog(DialogKeys.REDIRECT_PORT).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.REDIRECT_PORT).close() }
            )

            PortInputDialog(
                title = MLang.tproxy_port,
                state = dialogs.getDialog(DialogKeys.TPROXY_PORT),
                currentValue = config.tproxyPort,
                onConfirm = { value ->
                    onConfigChange(config.copy(tproxyPort = value))
                    dialogs.getDialog(DialogKeys.TPROXY_PORT).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.TPROXY_PORT).close() }
            )

            PortInputDialog(
                title = MLang.mixed_port,
                state = dialogs.getDialog(DialogKeys.MIXED_PORT),
                currentValue = config.mixedPort,
                onConfirm = { value ->
                    onConfigChange(config.copy(mixedPort = value))
                    dialogs.getDialog(DialogKeys.MIXED_PORT).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.MIXED_PORT).close() }
            )

            // 字符串输入对话框
            StringInputDialog(
                title = MLang.bind_address,
                state = dialogs.getDialog(DialogKeys.BIND_ADDRESS),
                currentValue = config.bindAddress,
                label = MLang.input_bind_address_label,
                onConfirm = { value ->
                    onConfigChange(config.copy(bindAddress = value))
                    dialogs.getDialog(DialogKeys.BIND_ADDRESS).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.BIND_ADDRESS).close() }
            )

            StringInputDialog(
                title = MLang.external_controller,
                state = dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER),
                currentValue = config.externalController,
                label = MLang.input_external_controller_label,
                onConfirm = { value ->
                    onConfigChange(config.copy(externalController = value))
                    dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER).close() }
            )

            StringInputDialog(
                title = MLang.external_controller_tls,
                state = dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER_TLS),
                currentValue = config.externalControllerTLS,
                label = MLang.input_external_controller_tls_label,
                onConfirm = { value ->
                    onConfigChange(config.copy(externalControllerTLS = value))
                    dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER_TLS).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.EXTERNAL_CONTROLLER_TLS).close() }
            )

            StringInputDialog(
                title = MLang.secret,
                state = dialogs.getDialog(DialogKeys.SECRET),
                currentValue = config.secret,
                label = MLang.input_secret_label,
                onConfirm = { value ->
                    onConfigChange(config.copy(secret = value))
                    dialogs.getDialog(DialogKeys.SECRET).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.SECRET).close() }
            )

            StringInputDialog(
                title = MLang.dns_listen,
                state = dialogs.getDialog(DialogKeys.DNS_LISTEN),
                currentValue = config.dns.listen,
                label = MLang.input_dns_listen_label,
                onConfirm = { value ->
                    onConfigChange(config.copy(dns = config.dns.copy(listen = value)))
                    dialogs.getDialog(DialogKeys.DNS_LISTEN).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.DNS_LISTEN).close() }
            )

            StringInputDialog(
                title = MLang.geoip_fallback_whitelist,
                state = dialogs.getDialog(DialogKeys.GEOIP_CODE),
                currentValue = config.dns.fallbackFilter.geoIpCode,
                label = MLang.input_geoip_code_label,
                onConfirm = { value ->
                    onConfigChange(
                        config.copy(
                            dns = config.dns.copy(
                                fallbackFilter = config.dns.fallbackFilter.copy(
                                    geoIpCode = value
                                )
                            )
                        )
                    )
                    dialogs.getDialog(DialogKeys.GEOIP_CODE).close()
                },
                onDismiss = { dialogs.getDialog(DialogKeys.GEOIP_CODE).close() }
            )

            // RESET 确认对话框
            ResetConfirmDialog(
                state = dialogs.getDialog(DialogKeys.RESET),
                onConfirm = onRequestReset
            )
        }
    )
}

