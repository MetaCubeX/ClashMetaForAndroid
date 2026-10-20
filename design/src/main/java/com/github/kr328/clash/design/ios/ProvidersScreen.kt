package com.github.kr328.clash.design.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    val proxyRows = rows.filter { it.provider.type == Provider.Type.Proxy }
    val ruleRows = rows.filter { it.provider.type == Provider.Type.Rule }
    val proxyTitle = stringResource(R.string.proxy)
    val ruleTitle = stringResource(R.string.rule)
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = if (rows.isEmpty()) {
            null
        } else {
            { NavAction(stringResource(R.string.update_all), onClick = onUpdateAll) }
        },
    ) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IosEmptyState(message = stringResource(R.string.list_empty))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
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
            style = IosType.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, top = 18.dp, end = 16.dp, bottom = 6.dp),
        )
    }
    items(count = rows.size, key = { rows[it].index }) { index ->
        ProviderLine(
            row = rows[index],
            now = now,
            showDivider = index < rows.lastIndex,
            onUpdate = onUpdate,
        )
    }
}

@Composable
private fun ProviderLine(
    row: ProviderRowUi,
    now: Long,
    showDivider: Boolean,
    onUpdate: (Int) -> Unit,
) {
    if (row.updating) {
        IosProgressRow(row.provider.name)
        return
    }
    val inline = row.provider.vehicleType == Provider.VehicleType.Inline
    val context = LocalContext.current
    val updated = if (inline || row.updatedAt <= 0L) {
        null
    } else {
        (now - row.updatedAt).elapsedIntervalString(context)
    }
    IosListRow(
        row = IosDetail(
            title = row.provider.name,
            subtitle = providerTypeLabel(row.provider),
            value = updated,
            trailing = if (inline) null else stringResource(R.string.update),
            onTrailing = if (inline) null else {
                { onUpdate(row.index) }
            },
        ),
        showDivider = showDivider,
    )
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

@Composable
private fun NavAction(text: String, onClick: () -> Unit) {
    val interaction = remember(text) { MutableInteractionSource() }
    Text(
        text = text,
        style = IosType.body,
        color = IosTheme.colors.blue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}
