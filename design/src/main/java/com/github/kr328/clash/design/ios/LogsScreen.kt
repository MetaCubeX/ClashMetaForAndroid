package com.github.kr328.clash.design.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.format

@Composable
fun LogsScreen(
    insets: Insets,
    title: String,
    files: List<LogFile>,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpen: (LogFile) -> Unit,
) {
    val colors = IosTheme.colors
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = if (files.isEmpty()) {
            null
        } else {
            {
                IosNavIcon(
                    icon = painterResource(R.drawable.ic_baseline_delete),
                    description = stringResource(R.string.delete_all_logs),
                    onClick = onDeleteAll,
                    tint = IosTheme.colors.red,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.logcat_open),
                    subtitle = stringResource(R.string.logcat_open_summary),
                    onClick = onStart,
                ),
            )
            Text(
                text = stringResource(R.string.history),
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp),
            )
            if (files.isEmpty()) {
                IosEmptyState(
                    message = stringResource(R.string.list_empty),
                    modifier = Modifier.weight(1f),
                )
            } else {
                val context = LocalContext.current
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(files, key = { _, file -> file.fileName }) { index, file ->
                        IosListRow(
                            row = IosDetail(
                                title = file.date.format(context),
                                onClick = { onOpen(file) },
                            ),
                            showDivider = index < files.lastIndex,
                        )
                    }
                }
            }
        }
    }
}
