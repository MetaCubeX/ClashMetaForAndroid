package com.github.kr328.clash.design.ios

import androidx.compose.runtime.Composable
import com.github.kr328.clash.design.ui.Insets

@Composable
fun MetaFeatureSettingsScreen(
    insets: Insets,
    title: String,
    groups: List<SettingGroup>,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    SettingsPage(
        insets = insets,
        title = title,
        groups = groups,
        onBack = onBack,
        onReset = onReset,
    )
}
