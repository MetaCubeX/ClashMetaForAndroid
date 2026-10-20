package com.github.kr328.clash.design.ios

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.getHtml

@Composable
fun ApkBrokenScreen(
    insets: Insets,
    title: String,
    onBack: () -> Unit,
    onReleases: () -> Unit,
) {
    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            HtmlTip(R.string.application_broken_tips)
            GroupTitle(stringResource(R.string.reinstall))
            IosPreferenceRow(
                IosDetail(
                    title = stringResource(R.string.github_releases),
                    subtitle = stringResource(R.string.meta_github_url).breakAfterSlash(),
                    onClick = onReleases,
                ),
                index = 0,
                count = 1,
            )
        }
    }
}

@Composable
private fun HtmlTip(@StringRes id: Int) {
    val context = LocalContext.current
    val text = remember(id, context) { context.getHtml(id).asIosText() }
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 18.dp),
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
