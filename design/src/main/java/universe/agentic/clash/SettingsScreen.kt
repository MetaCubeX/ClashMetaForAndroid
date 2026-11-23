package universe.agentic.clash

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
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.ui.Insets

data class SettingsRootStatus(
    val loaded: Boolean = false,
    val darkMode: DarkMode = DarkMode.Auto,
    val tunStack: String = "mips",
    val webdav: String = "",
    val mcpEnabled: Boolean = false,
    val mcpListening: Boolean = false,
)

@Composable
fun SettingsScreen(
    insets: Insets,
    title: String,
    status: SettingsRootStatus,
    onBack: () -> Unit,
    onApp: () -> Unit,
    onNetwork: () -> Unit,
    onOverride: () -> Unit,
    onMeta: () -> Unit,
    onWebdav: () -> Unit,
    onMcp: () -> Unit,
    onAbout: () -> Unit,
    embedded: Boolean = false,
) {
    val appValue = if (status.loaded) darkModeLabel(status.darkMode) else null
    val networkValue = if (status.loaded) tunStackLabel(status.tunStack) else null
    val webdavValue = status.webdav.takeIf { status.loaded && it.isNotEmpty() }
    val mcpValue = if (status.loaded) mcpLabel(status) else null
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        embedded = embedded,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            IosInsetGroup(
                title = stringResource(R.string.section_preferences),
                rows = listOf(
                    valueLink(
                        title = stringResource(R.string.app),
                        value = appValue,
                        onClick = onApp,
                        leadingIcon = R.drawable.ic_baseline_settings,
                    ),
                    valueLink(
                        title = stringResource(R.string.network),
                        value = networkValue,
                        onClick = onNetwork,
                        leadingIcon = R.drawable.ic_baseline_vpn_lock,
                    ),
                ),
            )
            Spacer(Modifier.height(12.dp))
            IosInsetGroup(
                title = stringResource(R.string.section_advanced),
                rows = listOf(
                    IosDetail(
                        title = stringResource(R.string.override),
                        subtitle = stringResource(R.string.settings_override_summary),
                        onClick = onOverride,
                        leadingIcon = R.drawable.ic_baseline_edit,
                    ),
                    IosDetail(
                        title = stringResource(R.string.meta_features),
                        subtitle = stringResource(R.string.settings_meta_summary),
                        onClick = onMeta,
                        leadingIcon = R.drawable.ic_baseline_meta,
                    ),
                    valueLink(
                        title = stringResource(R.string.webdav_sync),
                        value = webdavValue,
                        onClick = onWebdav,
                        leadingIcon = R.drawable.ic_baseline_sync,
                    ),
                    valueLink(
                        title = stringResource(R.string.mcp_service),
                        value = mcpValue,
                        onClick = onMcp,
                        leadingIcon = R.drawable.ic_baseline_extension,
                    ),
                ),
            )
            Spacer(Modifier.height(12.dp))
            IosInsetGroup(
                title = stringResource(R.string.section_other),
                rows = listOf(
                    IosDetail(
                        title = stringResource(R.string.about),
                        onClick = onAbout,
                        leadingIcon = R.drawable.ic_outline_info,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun valueLink(
    title: String,
    value: String?,
    onClick: () -> Unit,
    leadingIcon: Int,
): IosDetail {
    val side = value?.takeIf { it.fitsTrailingValue() }
    return IosDetail(
        title = title,
        value = side,
        subtitle = if (side == null) value else null,
        onClick = onClick,
        leadingIcon = leadingIcon,
    )
}

@Composable
private fun darkModeLabel(mode: DarkMode): String = when (mode) {
    DarkMode.Auto -> stringResource(R.string.follow_system_android_10)
    DarkMode.ForceLight -> stringResource(R.string.always_light)
    DarkMode.ForceDark -> stringResource(R.string.always_dark)
}

@Composable
private fun tunStackLabel(mode: String): String {
    val values = listOf("system", "gvisor", "mixed", "mips")
    val labels = listOf(
        stringResource(R.string.tun_stack_system),
        stringResource(R.string.tun_stack_gvisor),
        stringResource(R.string.tun_stack_mixed),
        stringResource(R.string.tun_stack_mips),
    )
    return labels.getOrNull(values.indexOf(mode)) ?: mode
}

@Composable
private fun mcpLabel(status: SettingsRootStatus): String = when {
    !status.mcpEnabled -> stringResource(R.string.mcp_status_off)
    status.mcpListening -> stringResource(R.string.mcp_status_listening)
    else -> stringResource(R.string.mcp_status_stopped)
}
