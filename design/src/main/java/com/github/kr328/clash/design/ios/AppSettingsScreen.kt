package com.github.kr328.clash.design.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.common.AppLanguages
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.ui.Insets

data class AppSettingsUiState(
    val autoRestart: Boolean = false,
    val appLanguage: String = AppLanguages.FOLLOW_SYSTEM,
    val darkMode: DarkMode = DarkMode.Auto,
    val hideAppIcon: Boolean = false,
    val hideFromRecents: Boolean = false,
    val dynamicNotification: Boolean = true,
    val running: Boolean = false,
) {
    val showTrafficEnabled: Boolean get() = !running
}

@Composable
fun AppSettingsScreen(
    insets: Insets,
    title: String,
    state: AppSettingsUiState,
    onBack: () -> Unit,
    onAutoRestart: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDarkMode: (DarkMode) -> Unit,
    onHideIcon: (Boolean) -> Unit,
    onHideFromRecents: (Boolean) -> Unit,
    onShowTraffic: (Boolean) -> Unit,
) {
    var languageOpen by remember { mutableStateOf(false) }
    var darkModeOpen by remember { mutableStateOf(false) }
    val languageTags = listOf(AppLanguages.FOLLOW_SYSTEM) + AppLanguages.supported.map { it.tag }
    val languageLabels = listOf(stringResource(R.string.follow_system)) +
        AppLanguages.supported.map { it.nativeName }
    val languageIndex = languageTags.indexOf(state.appLanguage).let { if (it < 0) 0 else it }
    val darkModes = DarkMode.values()
    val darkLabels = listOf(
        stringResource(R.string.follow_system_android_10),
        stringResource(R.string.always_light),
        stringResource(R.string.always_dark),
    )
    val darkIndex = darkModes.indexOf(state.darkMode)

    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            GroupLabel(stringResource(R.string.behavior))
            IosSwitchRow(
                title = stringResource(R.string.auto_restart),
                summary = stringResource(R.string.allow_clash_auto_restart),
                checked = state.autoRestart,
                index = 0,
                count = 1,
                onCheckedChange = onAutoRestart,
            )
            Spacer(Modifier.height(22.dp))
            GroupLabel(stringResource(R.string.interface_))
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.app_language),
                    subtitle = languageLabels.getOrNull(languageIndex),
                    onClick = { languageOpen = true },
                ),
                index = 0,
                count = 4,
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.dark_mode),
                    subtitle = darkLabels.getOrNull(darkIndex),
                    onClick = { darkModeOpen = true },
                ),
                index = 1,
                count = 4,
            )
            IosSwitchRow(
                title = stringResource(R.string.hide_app_icon_title),
                summary = stringResource(R.string.hide_app_icon_desc).withDialCodeKeptIntact(),
                checked = state.hideAppIcon,
                index = 2,
                count = 4,
                onCheckedChange = onHideIcon,
            )
            IosSwitchRow(
                title = stringResource(R.string.hide_from_recents_title),
                summary = stringResource(R.string.hide_from_recents_desc),
                checked = state.hideFromRecents,
                index = 3,
                count = 4,
                onCheckedChange = onHideFromRecents,
            )
            Spacer(Modifier.height(22.dp))
            GroupLabel(stringResource(R.string.service))
            IosSwitchRow(
                title = stringResource(R.string.show_traffic),
                summary = stringResource(R.string.show_traffic_summary),
                checked = state.dynamicNotification,
                enabled = state.showTrafficEnabled,
                index = 0,
                count = 1,
                onCheckedChange = onShowTraffic,
            )
        }
    }

    if (languageOpen) {
        IosOptionSheet(
            title = stringResource(R.string.app_language),
            options = languageLabels,
            selected = languageIndex,
            onSelect = { onLanguage(languageTags[it]) },
            onDismiss = { languageOpen = false },
        )
    }

    if (darkModeOpen) {
        IosOptionSheet(
            title = stringResource(R.string.dark_mode),
            options = darkLabels,
            selected = darkIndex,
            onSelect = { onDarkMode(darkModes[it]) },
            onDismiss = { darkModeOpen = false },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
    )
}

private const val DIAL_CODE = "*#*#252746382#*#*"

/** Word joiners so the dial code wraps as one token, not in the middle. */
private fun String.withDialCodeKeptIntact(): String {
    val at = indexOf(DIAL_CODE)
    if (at < 0) return this
    val glued = buildString(DIAL_CODE.length * 2) {
        DIAL_CODE.forEachIndexed { index, ch ->
            append(ch)
            if (index < DIAL_CODE.lastIndex) append('\u2060')
        }
    }
    return replace(DIAL_CODE, glued)
}
