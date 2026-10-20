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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.ui.Insets

@Composable
fun NewProfileScreen(
    insets: Insets,
    title: String,
    querying: Boolean,
    externals: List<ProfileProvider.External>,
    onBack: () -> Unit,
    onFile: () -> Unit,
    onUrl: () -> Unit,
    onQr: () -> Unit,
    onExternal: (ProfileProvider.External) -> Unit,
    onDetail: (ProfileProvider.External) -> Unit,
) {
    val builtin = listOf(
        IosDetail(
            title = stringResource(R.string.file),
            subtitle = stringResource(R.string.import_from_file),
            onClick = onFile,
        ),
        IosDetail(
            title = stringResource(R.string.url),
            subtitle = stringResource(R.string.import_from_url),
            onClick = onUrl,
        ),
        IosDetail(
            title = stringResource(R.string.qr),
            subtitle = stringResource(R.string.import_from_qr),
            onClick = onQr,
        ),
    )
    val detail = stringResource(R.string.detail)
    val externalRows = externals.map { provider ->
        IosDetail(
            title = provider.name,
            subtitle = provider.summary,
            trailing = detail,
            onClick = { onExternal(provider) },
            onTrailing = { onDetail(provider) },
        )
    }
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            PreferenceGroup(builtin)
            if (querying || externalRows.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel(stringResource(R.string.providers))
                if (querying) {
                    IosProgressRow(stringResource(R.string.loading))
                } else {
                    PreferenceGroup(externalRows)
                }
            }
        }
    }
}

@Composable
private fun PreferenceGroup(rows: List<IosDetail>) {
    val count = rows.size
    rows.forEachIndexed { index, row ->
        IosPreferenceRow(row = row, index = index, count = count)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
    )
}
