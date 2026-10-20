package com.github.kr328.clash.design.ios

import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.getHtml
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.service.model.Profile

@Composable
fun PropertiesScreen(
    insets: Insets,
    title: String,
    profile: Profile?,
    processing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onName: () -> Unit,
    onUrl: () -> Unit,
    onInterval: () -> Unit,
    onAgeKey: () -> Unit,
    onShare: () -> Unit,
    onBrowse: () -> Unit,
) {
    val context = LocalContext.current
    val tips = remember(context) { context.getHtml(R.string.tips_properties) }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = {
            if (processing) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = IosTheme.colors.blue,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                BarText(stringResource(R.string.save), onSave)
            }
        },
    ) {
        if (profile == null) {
            IosProgressRow(stringResource(R.string.loading))
        } else {
            PropertiesBody(
                profile = profile,
                tips = tips,
                onName = onName,
                onUrl = onUrl,
                onInterval = onInterval,
                onAgeKey = onAgeKey,
                onShare = onShare,
                onBrowse = onBrowse,
            )
        }
    }
}

@Composable
private fun PropertiesBody(
    profile: Profile,
    tips: Spanned,
    onName: () -> Unit,
    onUrl: () -> Unit,
    onInterval: () -> Unit,
    onAgeKey: () -> Unit,
    onShare: () -> Unit,
    onBrowse: () -> Unit,
) {
    val file = profile.type == Profile.Type.File
    val urlEditable = profile.type == Profile.Type.Url
    val minutes = (profile.interval / 1000 / 60).toInt()
    val intervalLabel = when {
        file -> stringResource(R.string.unavailable)
        profile.interval == 0L -> stringResource(R.string.disabled)
        else -> stringResource(R.string.format_minutes, minutes)
    }
    val ageSet = !profile.ageSecretKey.isNullOrBlank()
    val nameSide = sideValue(profile.name)
    val sourceText = if (file) stringResource(R.string.unavailable) else profile.source
    val sourceSide = sideValue(sourceText)
    val configRows = listOf(
            IosDetail(
                title = stringResource(R.string.name),
                subtitle = if (nameSide == null) profile.name.ifBlank { null } else null,
                value = nameSide,
                onClick = onName,
            ),
            IosDetail(
                title = stringResource(R.string.url),
                subtitle = if (sourceSide == null) sourceText.ifBlank { null } else null,
                value = sourceSide,
                enabled = !file,
                onClick = if (urlEditable) onUrl else null,
            ),
            IosDetail(
                title = stringResource(R.string.auto_update),
                value = intervalLabel,
                enabled = !file,
                onClick = if (file) null else onInterval,
            ),
            IosDetail(
                title = stringResource(R.string.age_secret_key),
                // Named reuse: webdav_password_set is the existing 已设置 / Set string.
                value = stringResource(
                    if (ageSet) R.string.webdav_password_set else R.string.webdav_unset,
                ),
                onClick = onAgeKey,
            ),
        )
        val contentRows = buildList {
            if (isShareableSubscriptionUrl(profile.source)) {
                add(
                    IosDetail(
                        title = stringResource(R.string.share_subscription_qr),
                        subtitle = stringResource(R.string.share_subscription_qr_tips),
                        onClick = onShare,
                    ),
                )
            }
            add(
                IosDetail(
                    title = stringResource(R.string.browse_files),
                    subtitle = stringResource(R.string.browse_configuration_providers),
                    onClick = onBrowse,
                ),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            HtmlFootnote(tips)
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.profile))
            PreferenceGroup(configRows)
            Spacer(Modifier.height(22.dp))
            PreferenceGroup(contentRows)
        }
}

/** Right-side value is one line and at most 160dp. Wider text stays under the title. */
private fun sideValue(text: String): String? = text.takeIf { it.fitsTrailingValue() }

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

@Composable
private fun HtmlFootnote(text: Spanned) {
    val color = IosTheme.colors.secondaryLabel.toArgb()
    AndroidView(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        factory = { context ->
            TextView(context).apply {
                this.text = text
                setTextColor(color)
                textSize = 13f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.text = text
            view.setTextColor(color)
        },
    )
}

@Composable
private fun BarText(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = IosType.body, color = IosTheme.colors.blue)
    }
}
