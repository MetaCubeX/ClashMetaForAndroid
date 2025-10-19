package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.design.components.BaseSettingsScreen
import com.github.kr328.clash.design.components.ResetConfirmDialog
import com.github.kr328.clash.design.components.settingsList
import com.github.kr328.clash.design.theme.AppDimensions
import com.github.kr328.clash.design.util.*
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Restore
import top.yukonga.miuix.kmp.icon.icons.useful.Save

@Composable
fun MetaFeatureSettingsScreen(design: MetaFeatureSettingsDesign) {
    val dialogs = design.dialogManager

    val config = design.config
    val onConfigChange: (ConfigurationOverride) -> Unit = { design.config = it; design.onConfigChange(it) }
    val onRequestClose: () -> Unit = {
        design.requests.trySend(MetaFeatureSettingsDesign.Request.Close)
        Unit
    }
    val onRequestReset: () -> Unit = {
        design.requests.trySend(MetaFeatureSettingsDesign.Request.ResetOverride)
        Unit
    }
    val onRequestSave: () -> Unit = {
        design.requests.trySend(MetaFeatureSettingsDesign.Request.Save)
        Unit
    }

    // 使用共享映射，避免重复定义
    val modeMapping = CommonMappings.tunnelMode
    val logLevelMapping = CommonMappings.logLevel

    val sections = settingsList {
        section(MLang.section_settings) {
            triState(
                title = MLang.unified_delay,
                value = config.unifiedDelay,
                onChange = { v -> onConfigChange(config.copy(unifiedDelay = v)) },
                summary = MLang.unified_delay_summary
            )
            triState(
                title = MLang.geodata_mode,
                value = config.geodataMode,
                onChange = { v -> onConfigChange(config.copy(geodataMode = v)) },
                summary = MLang.geodata_mode_summary
            )
            triState(
                title = MLang.tcp_concurrent,
                value = config.tcpConcurrent,
                onChange = { v -> onConfigChange(config.copy(tcpConcurrent = v)) },
                summary = MLang.tcp_concurrent_summary
            )
            dropdown(
                title = MLang.find_process_mode,
                mapping = SelectionMapping(
                    values = listOf(
                        ConfigurationOverride.FindProcessMode.Off,
                        ConfigurationOverride.FindProcessMode.Strict,
                        ConfigurationOverride.FindProcessMode.Always,
                        null
                    ),
                    labels = listOf(
                        MLang.process_mode_off,
                        MLang.process_mode_strict,
                        MLang.process_mode_always,
                        MLang.tristate_not_modify
                    )
                ),
                value = config.findProcessMode,
                onChange = { mode -> onConfigChange(config.copy(findProcessMode = mode)) }
            )
        }
        section(MLang.section_sniffer) {
            triState(
                title = MLang.sniff_policy,
                value = config.sniffer.enable,
                onChange = { v -> onConfigChange(config.copy(sniffer = config.sniffer.copy(enable = v))) }
            )

            arrow(
                title = StringFormatters.formatSniffPortsTitle(StringFormatters.Protocol.http),
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditHttpPorts); Unit },
                summary = config.sniffer.sniff.http.ports.toPortsSummary()
            )
            triState(
                title = StringFormatters.formatSniffOverrideTitle(StringFormatters.Protocol.http),
                value = config.sniffer.sniff.http.overrideDestination,
                onChange = { v ->
                    onConfigChange(
                        config.copy(
                            sniffer = config.sniffer.copy(
                                sniff = config.sniffer.sniff.copy(
                                    http = config.sniffer.sniff.http.copy(overrideDestination = v)
                                )
                            )
                        )
                    )
                }
            )

            arrow(
                title = StringFormatters.formatSniffPortsTitle(StringFormatters.Protocol.tls),
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditTlsPorts); Unit },
                summary = config.sniffer.sniff.tls.ports.toPortsSummary()
            )
            triState(
                title = StringFormatters.formatSniffOverrideTitle(StringFormatters.Protocol.tls),
                value = config.sniffer.sniff.tls.overrideDestination,
                onChange = { v ->
                    onConfigChange(
                        config.copy(
                            sniffer = config.sniffer.copy(
                                sniff = config.sniffer.sniff.copy(
                                    tls = config.sniffer.sniff.tls.copy(overrideDestination = v)
                                )
                            )
                        )
                    )
                }
            )

            arrow(
                title = StringFormatters.formatSniffPortsTitle(StringFormatters.Protocol.quic),
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditQuicPorts); Unit },
                summary = config.sniffer.sniff.quic.ports.toPortsSummary()
            )
            triState(
                title = StringFormatters.formatSniffOverrideTitle(StringFormatters.Protocol.quic),
                value = config.sniffer.sniff.quic.overrideDestination,
                onChange = { v ->
                    onConfigChange(
                        config.copy(
                            sniffer = config.sniffer.copy(
                                sniff = config.sniffer.sniff.copy(
                                    quic = config.sniffer.sniff.quic.copy(overrideDestination = v)
                                )
                            )
                        )
                    )
                }
            )

            triState(
                title = MLang.force_dns_mapping,
                value = config.sniffer.forceDnsMapping,
                onChange = { v -> onConfigChange(config.copy(sniffer = config.sniffer.copy(forceDnsMapping = v))) }
            )
            triState(
                title = MLang.parse_pure_ip,
                value = config.sniffer.parsePureIp,
                onChange = { v -> onConfigChange(config.copy(sniffer = config.sniffer.copy(parsePureIp = v))) }
            )
            triState(
                title = MLang.override_dest_addr,
                value = config.sniffer.overrideDestination,
                onChange = { v -> onConfigChange(config.copy(sniffer = config.sniffer.copy(overrideDestination = v))) }
            )
            arrow(
                title = MLang.force_resolve_domain,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditForceDomains); Unit },
                summary = config.sniffer.forceDomain.toDomainsSummary()
            )
            arrow(
                title = MLang.skip_domain,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditSkipDomains); Unit },
                summary = config.sniffer.skipDomain.toDomainsSummary()
            )
            arrow(
                title = MLang.skip_src_address,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditSkipSrcAddresses); Unit },
                summary = config.sniffer.skipSrcAddress.toAddressesSummary()
            )
            arrow(
                title = MLang.skip_dst_address,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.EditSkipDstAddresses); Unit },
                summary = config.sniffer.skipDstAddress.toAddressesSummary()
            )
        }
        section(MLang.section_geo_files) {
            arrow(
                title = MLang.import_geoip,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.ImportGeoIp); Unit },
                summary = MLang.import_hint
            )
            arrow(
                title = MLang.import_geosite,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.ImportGeoSite); Unit },
                summary = MLang.import_hint
            )
            arrow(
                title = MLang.import_country,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.ImportCountry); Unit },
                summary = MLang.import_hint
            )
            arrow(
                title = MLang.import_asn,
                onClick = { design.requests.trySend(MetaFeatureSettingsDesign.Request.ImportASN); Unit },
                summary = MLang.import_hint
            )
        }
    }

    BaseSettingsScreen(
        title = MLang.meta_page_title,
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
            // RESET 确认对话框
            ResetConfirmDialog(
                state = dialogs.getDialog(DialogKeys.RESET),
                onConfirm = onRequestReset
            )
        }
    )
}

