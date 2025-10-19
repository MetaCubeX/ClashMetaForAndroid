package com.github.kr328.clash.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun <T> EditorItemList(
    items: List<T>,
    paddingValues: PaddingValues,
    headerTitle: String? = null,
    onItemClick: (Int, T) -> Unit = { _, _ -> },
    onDeleteItem: (Int, T) -> Unit = { _, _ -> },
    showDeleteButton: Boolean = true,
    itemContent: @Composable (index: Int, item: T) -> Unit,
    itemRightContent: @Composable ((index: Int, item: T) -> Unit)? = null
) {
    val scrollBehavior = MiuixScrollBehavior()

    LazyColumn(
        modifier = Modifier
            .height(getWindowSize().height.dp)
            .padding(top = 16.dp)
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = paddingValues
    ) {
        if (headerTitle != null) {
            item {
                SmallTitle(headerTitle)
            }
        }

        itemsIndexed(items) { index, item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(index, item) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        itemContent(index, item)
                    }

                    if (itemRightContent != null) {
                        itemRightContent(index, item)
                    }


                    if (showDeleteButton) {
                        IconButton(
                            onClick = { onDeleteItem(index, item) }
                        ) {
                            Icon(
                                MiuixIcons.Useful.Delete,
                                contentDescription = MLang.action_delete,
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SimpleTextItem(
    index: Int,
    text: String,
    showIndex: Boolean = true
) {
    Text(
        text = if (showIndex) "${index + 1}. $text" else text,
        style = MiuixTheme.textStyles.body1
    )
}

@Composable
fun KeyValueItem(
    key: String,
    value: String
) {
    Column {
        Text(
            text = key,
            style = MiuixTheme.textStyles.body1
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }
}

