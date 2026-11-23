package universe.agentic.clash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets

data class McpSettingsUi(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val token: String = "",
    val status: String = "",
)

@Composable
fun McpSettingsScreen(
    insets: Insets,
    title: String,
    state: McpSettingsUi,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onCopyEndpoint: () -> Unit,
    onCopyToken: () -> Unit,
) {
    val statusText = when {
        !state.enabled -> stringResource(R.string.mcp_status_off)
        state.status == "listening" -> stringResource(R.string.mcp_status_listening)
        state.status.isBlank() -> stringResource(R.string.mcp_status_stopped)
        else -> state.status
    }
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            IosSwitchRow(
                title = stringResource(R.string.mcp_enable),
                summary = statusText,
                checked = state.enabled,
                onCheckedChange = onEnabled,
            )
            Text(
                text = stringResource(R.string.mcp_intro),
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.mcp_endpoint),
                    subtitle = state.endpoint,
                    onClick = onCopyEndpoint,
                    disclosure = false,
                ),
                index = 0,
                count = 2,
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.mcp_token),
                    subtitle = state.token.ifBlank { stringResource(R.string.mcp_token_empty) },
                    onClick = onCopyToken,
                    disclosure = false,
                ),
                index = 1,
                count = 2,
            )
            Spacer(Modifier.height(20.dp))
            GroupTitle(stringResource(R.string.mcp_features))
            McpToolRow(
                name = "status",
                summary = stringResource(R.string.mcp_tool_status),
                index = 0,
                count = 8,
            )
            McpToolRow(
                name = "list_proxies",
                summary = stringResource(R.string.mcp_tool_list_proxies),
                index = 1,
                count = 8,
            )
            McpToolRow(
                name = "select_proxy",
                summary = stringResource(R.string.mcp_tool_select_proxy),
                index = 2,
                count = 8,
            )
            McpToolRow(
                name = "list_profiles",
                summary = stringResource(R.string.mcp_tool_list_profiles),
                index = 3,
                count = 8,
            )
            McpToolRow(
                name = "set_profile",
                summary = stringResource(R.string.mcp_tool_set_profile),
                index = 4,
                count = 8,
            )
            McpToolRow(
                name = "reload",
                summary = stringResource(R.string.mcp_tool_reload),
                index = 5,
                count = 8,
            )
            McpToolRow(
                name = "start",
                summary = stringResource(R.string.mcp_tool_start),
                index = 6,
                count = 8,
            )
            McpToolRow(
                name = "stop",
                summary = stringResource(R.string.mcp_tool_stop),
                index = 7,
                count = 8,
            )
            Text(
                text = stringResource(R.string.mcp_tips),
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
    }
}

@Composable
private fun McpToolRow(
    name: String,
    summary: String,
    index: Int,
    count: Int,
) {
    IosPreferenceRow(
        row = IosDetail(
            title = name,
            subtitle = summary,
            disclosure = false,
        ),
        index = index,
        count = count,
    )
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = IosType.section,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}
