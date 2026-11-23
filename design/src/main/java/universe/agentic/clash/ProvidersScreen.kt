package universe.agentic.clash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import nota.ui.theme.ShellShapes
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.elapsedIntervalString

data class ProviderRowUi(
    val index: Int,
    val provider: Provider,
    val updatedAt: Long,
    val updating: Boolean,
)

@Composable
fun ProvidersScreen(
    insets: Insets,
    title: String,
    rows: List<ProviderRowUi>,
    now: Long,
    onBack: () -> Unit,
    onUpdate: (Int) -> Unit,
    onUpdateAll: () -> Unit,
) {
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = if (rows.isEmpty()) {
            null
        } else {
            {
                IosRefreshIcon(
                    icon = rememberVectorPainter(Icons.Filled.Refresh),
                    description = stringResource(R.string.update_all),
                    busy = rows.any { it.updating },
                    onClick = onUpdateAll,
                )
            }
        },
    ) {
        ProviderSections(
            rows = rows,
            now = now,
            onUpdate = onUpdate,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ProviderSections(
    rows: List<ProviderRowUi>,
    now: Long,
    onUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val proxyRows = rows.filter { it.provider.type == Provider.Type.Proxy }
    val ruleRows = rows.filter { it.provider.type == Provider.Type.Rule }
    val proxyTitle = stringResource(R.string.proxy)
    val ruleTitle = stringResource(R.string.rule)
    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IosEmptyState(message = stringResource(R.string.list_empty))
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        providerSection(
            key = "proxy",
            title = proxyTitle,
            rows = proxyRows,
            now = now,
            onUpdate = onUpdate,
        )
        providerSection(
            key = "rule",
            title = ruleTitle,
            rows = ruleRows,
            now = now,
            onUpdate = onUpdate,
        )
    }
}

private fun LazyListScope.providerSection(
    key: String,
    title: String,
    rows: List<ProviderRowUi>,
    now: Long,
    onUpdate: (Int) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = key) {
        Text(
            text = title,
            style = IosType.section,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
    item(key = "$key-card") {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(ShellShapes.Container)
                .background(IosTheme.colors.grouped),
        ) {
            rows.forEachIndexed { index, row ->
                ProviderLine(
                    row = row,
                    now = now,
                    showDivider = index < rows.lastIndex,
                    onUpdate = onUpdate,
                )
            }
        }
    }
}

@Composable
private fun ProviderLine(
    row: ProviderRowUi,
    now: Long,
    showDivider: Boolean,
    onUpdate: (Int) -> Unit,
) {
    val inline = row.provider.vehicleType == Provider.VehicleType.Inline
    val context = LocalContext.current
    val updated = if (inline || row.updatedAt <= 0L) {
        null
    } else {
        (now - row.updatedAt).elapsedIntervalString(context)
    }
    val detail = IosDetail(
        title = row.provider.name,
        subtitle = providerTypeLabel(row.provider),
        value = updated,
        disclosure = false,
    )
    if (inline) {
        IosListRow(row = detail, showDivider = showDivider)
    } else {
        IosSideActionRow(
            row = detail,
            actionIcon = rememberVectorPainter(Icons.Filled.Refresh),
            actionDescription = stringResource(R.string.update),
            onAction = { onUpdate(row.index) },
            showDivider = showDivider,
            actionSpins = true,
            actionBusy = row.updating,
        )
    }
}

@Composable
private fun providerTypeLabel(provider: Provider): String {
    val type = when (provider.type) {
        Provider.Type.Proxy -> stringResource(R.string.proxy)
        Provider.Type.Rule -> stringResource(R.string.rule)
    }
    val vehicle = when (provider.vehicleType) {
        Provider.VehicleType.HTTP -> stringResource(R.string.http)
        Provider.VehicleType.File -> stringResource(R.string.file)
        Provider.VehicleType.Inline -> stringResource(R.string.inline)
        Provider.VehicleType.Compatible -> stringResource(R.string.compatible)
    }
    return stringResource(R.string.format_provider_type, type, vehicle)
}
