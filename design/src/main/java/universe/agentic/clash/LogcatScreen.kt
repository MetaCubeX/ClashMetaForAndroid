package universe.agentic.clash

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.common.compat.preferredLocale
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.format
import nota.ui.theme.shellPressBackground

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    insets: Insets,
    title: String,
    streaming: Boolean,
    messages: List<LogMessage>,
    level: LogMessage.Level?,
    keyword: String,
    onBack: () -> Unit,
    onKeyword: (String) -> Unit,
    onLevel: (LogMessage.Level?) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: (LogMessage) -> Unit,
) {
    val colors = IosTheme.colors
    val context = LocalContext.current
    val allLabel = remember(context) { allLevelsLabel(context) }
    val levelLabels = mapOf(
        LogMessage.Level.Debug to stringResource(R.string.debug),
        LogMessage.Level.Info to stringResource(R.string.info),
        LogMessage.Level.Warning to stringResource(R.string.warning),
        LogMessage.Level.Error to stringResource(R.string.error),
        LogMessage.Level.Silent to stringResource(R.string.silent),
    )
    val rows = remember(messages, level, keyword, streaming, context, levelLabels) {
        visibleRows(messages, level, keyword, streaming, context, levelLabels)
    }
    val listState = rememberLazyListState()
    var showLevels by remember { mutableStateOf(false) }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = {
            if (streaming) {
                IosTextButton(text = stringResource(R.string.close), onClick = onClose)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosNavIcon(
                        icon = painterResource(R.drawable.ic_baseline_publish),
                        description = stringResource(R.string.export),
                        onClick = onExport,
                    )
                    IosNavIcon(
                        icon = painterResource(R.drawable.ic_baseline_delete),
                        description = stringResource(R.string.delete),
                        onClick = onDelete,
                        tint = IosTheme.colors.red,
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            val selectable = listOf(
                LogMessage.Level.Debug,
                LogMessage.Level.Info,
                LogMessage.Level.Warning,
                LogMessage.Level.Error,
                LogMessage.Level.Silent,
            )
            val levelNames = listOf(allLabel) + selectable.map { levelLabels[it] ?: it.name }
            val levelIndex = if (level == null) 0 else selectable.indexOf(level).let { if (it < 0) 0 else it + 1 }
            Box {
                IosPreferenceRow(
                    IosDetail(
                        title = stringResource(R.string.log_level),
                        value = levelNames.getOrNull(levelIndex),
                        disclosure = false,
                        onClick = { showLevels = true },
                    ),
                )
                IosPopupMenu(
                    expanded = showLevels,
                    onDismiss = { showLevels = false },
                    actions = levelNames.mapIndexed { index, label ->
                        IosAction(
                            title = label,
                            marked = index == levelIndex,
                            onClick = {
                                onLevel(if (index == 0) null else selectable[index - 1])
                            },
                        )
                    },
                )
            }
            IosSearchField(
                value = keyword,
                onValueChange = onKeyword,
                placeholder = stringResource(R.string.search),
            )
            if (rows.isEmpty()) {
                IosEmptyState(
                    message = stringResource(
                        if (keyword.isNotEmpty()) R.string.search_empty else R.string.list_empty,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                StickToNewest(streaming = streaming, rows = rows, listState = listState)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                ) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        val interaction = remember(row.key) { MutableInteractionSource() }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .shellPressBackground(interactionSource = interaction)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {},
                                    onLongClick = { onCopy(row.message) },
                                ),
                        ) {
                            IosListRow(
                                row = IosDetail(
                                    title = row.message.message,
                                    subtitle = "${row.time}  ${levelLabels[row.message.level] ?: row.message.level.name}",
                                ),
                                showDivider = index < rows.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Streaming keeps the newest visible row at the top only while the vertical offset is still 0.
 * A plain column is used: reversing the data, not the layout, puts that row first.
 */
@Composable
private fun StickToNewest(
    streaming: Boolean,
    rows: List<LogRow>,
    listState: LazyListState,
) {
    if (!streaming) return
    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    LaunchedEffect(rows) {
        if (atTop && rows.isNotEmpty()) listState.scrollToItem(0)
    }
}

private data class LogRow(
    val key: String,
    val message: LogMessage,
    val time: String,
)

private fun visibleRows(
    messages: List<LogMessage>,
    level: LogMessage.Level?,
    keyword: String,
    streaming: Boolean,
    context: Context,
    levelLabels: Map<LogMessage.Level, String>,
): List<LogRow> {
    val seen = HashMap<String, Int>()
    val rows = ArrayList<LogRow>()
    for (message in messages) {
        val identity = "${message.time.time}:${message.level.name}:${message.message}"
        val ordinal = seen[identity] ?: 0
        seen[identity] = ordinal + 1
        if (level != null && message.level != level) continue
        val time = message.time.format(context, includeDate = false, includeTime = true)
        val levelLabel = levelLabels[message.level] ?: message.level.name
        if (keyword.isNotEmpty() && !matches(message, time, levelLabel, keyword)) continue
        rows.add(LogRow("$identity#$ordinal", message, time))
    }
    if (streaming) rows.reverse()
    return rows
}

private fun matches(message: LogMessage, time: String, levelLabel: String, keyword: String): Boolean {
    return time.contains(keyword, ignoreCase = true) ||
        levelLabel.contains(keyword, ignoreCase = true) ||
        message.level.name.contains(keyword, ignoreCase = true) ||
        message.message.contains(keyword, ignoreCase = true)
}

private fun allLevelsLabel(context: Context): String {
    return if (context.resources.configuration.preferredLocale.language == "zh") "全部" else "All"
}

