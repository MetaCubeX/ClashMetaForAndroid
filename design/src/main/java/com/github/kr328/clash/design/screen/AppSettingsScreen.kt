package com.github.kr328.clash.design.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.components.StandardListScreen
import com.github.kr328.clash.design.modifiers.standardCardPadding
import com.github.kr328.clash.design.util.finishActivity
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch

@Composable
fun AppSettingsScreen(design: AppSettingsDesign) {
    val context = LocalContext.current

    var autoRestart by remember { mutableStateOf(design.behavior.autoRestart) }
    var darkModeIndex by remember {
        mutableIntStateOf(
            when (design.uiStore.darkMode) {
                com.github.kr328.clash.design.model.DarkMode.Auto -> 0
                com.github.kr328.clash.design.model.DarkMode.ForceLight -> 1
                com.github.kr328.clash.design.model.DarkMode.ForceDark -> 2
            }
        )
    }
    var hideIcon by remember { mutableStateOf(design.uiStore.hideAppIcon) }
    var showTraffic by remember { mutableStateOf(design.srvStore.dynamicNotification) }

    // 保证外部状态变更时UI能及时同步
    LaunchedEffect(design.behavior.autoRestart) { autoRestart = design.behavior.autoRestart }
    LaunchedEffect(design.uiStore.hideAppIcon) { hideIcon = design.uiStore.hideAppIcon }
    LaunchedEffect(design.srvStore.dynamicNotification) { showTraffic = design.srvStore.dynamicNotification }

    StandardListScreen(
        title = MLang.app_settings_page_title,
        onBack = { context.finishActivity() }
    ) {
        item { SmallTitle(MLang.section_behavior) }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .standardCardPadding()
            ) {
                SuperSwitch(
                    title = MLang.auto_restart,
                    summary = MLang.auto_restart_summary,
                    checked = autoRestart,
                    onCheckedChange = {
                        autoRestart = it
                        design.behavior.autoRestart = it
                    }
                )
            }
        }

        item { SmallTitle(MLang.section_interface) }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .standardCardPadding()
            ) {
                Column {
                    val items = remember {
                        listOf(
                            MLang.theme_follow_system,
                            MLang.theme_always_light,
                            MLang.theme_always_dark
                        )
                    }
                    SuperDropdown(
                        title = MLang.theme,
                        summary = MLang.theme_summary,
                        items = items,
                        selectedIndex = darkModeIndex,
                        onSelectedIndexChange = { idx ->
                            darkModeIndex = idx
                            design.setDarkMode(idx)
                        },
                        enabled = true
                    )
                    SuperSwitch(
                        title = MLang.hide_icon,
                        summary = MLang.hide_icon_desc,
                        checked = hideIcon,
                        onCheckedChange = {
                            hideIcon = it
                            design.uiStore.hideAppIcon = it
                            design.onHideIconChange(it)
                        }
                    )
                }
            }
        }

        item { SmallTitle(MLang.section_service) }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .standardCardPadding()
            ) {
                SuperSwitch(
                    title = MLang.show_traffic,
                    summary = MLang.show_traffic_summary,
                    checked = showTraffic,
                    onCheckedChange = {
                        showTraffic = it
                        design.srvStore.dynamicNotification = it
                    }
                )
            }
        }
    }
}

