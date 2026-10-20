package com.github.kr328.clash.design.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

@Composable
fun SettingsScreen(
    insets: Insets,
    title: String,
    onBack: () -> Unit,
    onApp: () -> Unit,
    onNetwork: () -> Unit,
    onOverride: () -> Unit,
    onMeta: () -> Unit,
    onWebdav: () -> Unit,
) {
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            IosInsetGroup(
                title = stringResource(R.string.section_preferences),
                rows = listOf(
                    IosDetail(
                        title = stringResource(R.string.app),
                        subtitle = stringResource(R.string.settings_app_summary),
                        onClick = onApp,
                    ),
                    IosDetail(
                        title = stringResource(R.string.network),
                        subtitle = stringResource(R.string.settings_network_summary),
                        onClick = onNetwork,
                    ),
                ),
            )
            Spacer(Modifier.height(22.dp))
            IosInsetGroup(
                title = stringResource(R.string.section_advanced),
                rows = listOf(
                    IosDetail(
                        title = stringResource(R.string.override),
                        subtitle = stringResource(R.string.settings_override_summary),
                        onClick = onOverride,
                    ),
                    IosDetail(
                        title = stringResource(R.string.meta_features),
                        subtitle = stringResource(R.string.settings_meta_summary),
                        onClick = onMeta,
                    ),
                    IosDetail(
                        title = stringResource(R.string.webdav_sync),
                        subtitle = stringResource(R.string.settings_webdav_summary),
                        onClick = onWebdav,
                    ),
                ),
            )
        }
    }
}
