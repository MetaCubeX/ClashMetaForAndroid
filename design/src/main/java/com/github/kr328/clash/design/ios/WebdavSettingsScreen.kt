package com.github.kr328.clash.design.ios

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.getHtml

data class WebdavSettingsUiState(
    val url: String?,
    val lanUrl: String?,
    val username: String?,
    val passwordEmpty: Boolean,
    val remotePath: String?,
    val autoSync: Boolean,
    val status: String,
)

@Composable
fun WebdavSettingsScreen(
    insets: Insets,
    title: String,
    state: WebdavSettingsUiState,
    onBack: () -> Unit,
    onUrl: () -> Unit,
    onLanUrl: () -> Unit,
    onUsername: () -> Unit,
    onPassword: () -> Unit,
    onRemotePath: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
) {
    val insetBottom = with(LocalDensity.current) { insets.bottom.toDp() }
    // Scaffold already applies a non-zero bottom inset. Zero leaves the last action on the screen edge.
    val bottomPad = if (insetBottom < 24.dp) 72.dp else 24.dp
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = bottomPad),
        ) {
            HtmlTip(R.string.webdav_tips, top = 8.dp, bottom = 8.dp)
            Text(
                text = state.status,
                style = IosType.subhead,
                color = IosTheme.colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 16.dp),
            )
            GroupTitle(stringResource(R.string.webdav_server))
            ValueRow(
                title = stringResource(R.string.webdav_url),
                summary = fieldSummary(
                    state.url,
                    placeholder = R.string.webdav_url_placeholder,
                    empty = R.string.webdav_url_placeholder,
                ),
                index = 0,
                count = 2,
                onClick = onUrl,
            )
            ValueRow(
                title = stringResource(R.string.webdav_lan_url),
                summary = fieldSummary(
                    state.lanUrl,
                    placeholder = R.string.webdav_unset,
                    empty = R.string.webdav_unset,
                ),
                index = 1,
                count = 2,
                onClick = onLanUrl,
            )
            HtmlTip(R.string.webdav_lan_hint, top = 6.dp, bottom = 8.dp)
            ValueRow(
                title = stringResource(R.string.webdav_username),
                summary = fieldSummary(
                    state.username,
                    placeholder = null,
                    empty = R.string.webdav_unset,
                ),
                index = 0,
                count = 3,
                onClick = onUsername,
            )
            ValueRow(
                title = stringResource(R.string.webdav_password),
                summary = stringResource(
                    if (state.passwordEmpty) R.string.webdav_unset else R.string.webdav_password_set,
                ),
                index = 1,
                count = 3,
                onClick = onPassword,
            )
            ValueRow(
                title = stringResource(R.string.webdav_remote_path),
                summary = fieldSummary(
                    state.remotePath,
                    placeholder = R.string.webdav_remote_path_default,
                    empty = R.string.webdav_remote_path_default,
                ),
                index = 2,
                count = 3,
                onClick = onRemotePath,
            )
            Spacer(Modifier.height(22.dp))
            GroupTitle(stringResource(R.string.webdav_sync))
            IosSwitchRow(
                title = stringResource(R.string.webdav_auto_sync),
                summary = stringResource(R.string.webdav_auto_sync_summary),
                checked = state.autoSync,
                index = 0,
                count = 5,
                onCheckedChange = onAutoSync,
            )
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.webdav_test),
                    onClick = onTest,
                ),
                index = 1,
                count = 5,
            )
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.webdav_sync_now),
                    onClick = onSync,
                ),
                index = 2,
                count = 5,
            )
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.webdav_pull),
                    onClick = onPull,
                ),
                index = 3,
                count = 5,
            )
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.webdav_push),
                    onClick = onPush,
                ),
                index = 4,
                count = 5,
            )
        }
    }
}

@Composable
private fun ValueRow(
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val side = summary?.takeIf { it.fitsTrailingValue() }
    IosPreferenceRow(
        IosDetail(
            title = title,
            subtitle = if (side == null) summary else null,
            value = side,
            onClick = onClick,
        ),
        index = index,
        count = count,
    )
}

@Composable
private fun fieldSummary(
    text: String?,
    @StringRes placeholder: Int?,
    @StringRes empty: Int,
): String? {
    if (text == null) {
        return if (placeholder == null) null else stringResource(placeholder)
    }
    if (text.isEmpty()) return stringResource(empty)
    return text
}

@Composable
private fun HtmlTip(
    @StringRes id: Int,
    top: Dp = 8.dp,
    bottom: Dp = 18.dp,
) {
    val context = LocalContext.current
    val text = remember(id, context) { context.getHtml(id).asIosText() }
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = top, bottom = bottom),
    )
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
    )
}

private fun Spanned.asIosText(): AnnotatedString {
    val value = toString()
    return buildAnnotatedString {
        append(value)
        getSpans(0, length, StyleSpan::class.java).forEach { span ->
            if ((span.style and Typeface.BOLD) == 0) return@forEach
            val start = getSpanStart(span).coerceIn(0, value.length)
            val end = getSpanEnd(span).coerceIn(start, value.length)
            if (start < end) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}
