package com.github.kr328.clash.design.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.components.StandardListScreen
import com.github.kr328.clash.design.modifiers.standardCardPadding
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow

@Composable
fun SettingsScreen(
    onMainRequest: (MainDesign.Request) -> Unit,
    onSettingsRequest: (SettingsDesign.Request) -> Unit
) {
    val settingsEntries: List<Triple<String, SettingsDesign.Request, Boolean>> = listOf(
        Triple(MLang.entry_app, SettingsDesign.Request.StartApp, true),
        Triple(MLang.entry_network, SettingsDesign.Request.StartNetwork, true),
        Triple(MLang.entry_override, SettingsDesign.Request.StartOverride, true),
        Triple(MLang.entry_meta_features, SettingsDesign.Request.StartMetaFeature, true)
    )
    val proxyEntries: List<Triple<String, MainDesign.Request, Boolean>> = listOf(
        Triple(MLang.entry_profiles, MainDesign.Request.OpenProfiles, true),
        Triple(MLang.entry_providers, MainDesign.Request.OpenProviders, true)
    )
    val otherEntries: List<Triple<String, MainDesign.Request, Boolean>> = listOf(
        Triple(MLang.entry_logs, MainDesign.Request.OpenLogs, true),
        Triple(MLang.entry_about, MainDesign.Request.OpenAbout, true)
    )

    StandardListScreen(
        title = MLang.settings_page_title,
        onBack = null
    ) {
        item {
            @Composable
            fun <T> Section(title: String, entries: List<Triple<String, T, Boolean>>, onClick: (T) -> Unit) {
                SmallTitle(title)
                Card(Modifier.standardCardPadding()) {
                    entries.forEach { (t, req, enabled) ->
                        SuperArrow(
                            title = t,
                            onClick = { onClick(req) },
                            enabled = enabled
                        )
                    }
                }
            }

            Section(MLang.section_global, settingsEntries, onSettingsRequest)
            Section(MLang.section_proxy, proxyEntries, onMainRequest)
            Section(MLang.section_other, otherEntries, onMainRequest)
        }
    }
}

