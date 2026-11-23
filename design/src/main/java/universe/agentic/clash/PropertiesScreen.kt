package universe.agentic.clash

import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    onIntervalPreset: (Long) -> Unit,
    onIntervalCustom: () -> Unit,
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
                IosTextButton(text = stringResource(R.string.save), onClick = onSave)
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
                onIntervalPreset = onIntervalPreset,
                onIntervalCustom = onIntervalCustom,
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
    onIntervalPreset: (Long) -> Unit,
    onIntervalCustom: () -> Unit,
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
        minutes % 60 == 0 -> stringResource(R.string.format_hours, minutes / 60)
        else -> stringResource(R.string.format_minutes, minutes)
    }
    val presets = listOf(0L, 15L, 30L, 60L, 360L, 720L, 1440L)
    val presetLabels = listOf(
        stringResource(R.string.disabled),
        stringResource(R.string.format_minutes, 15),
        stringResource(R.string.format_minutes, 30),
        stringResource(R.string.format_hours, 1),
        stringResource(R.string.format_hours, 6),
        stringResource(R.string.format_hours, 12),
        stringResource(R.string.format_hours, 24),
    )
    val currentMinutes = minutes.toLong()
    val presetIndex = presets.indexOf(currentMinutes)
    var intervalOpen by remember { mutableStateOf(false) }
    val ageSet = !profile.ageSecretKey.isNullOrBlank()
    val nameSide = sideValue(profile.name)
    val sourceText = if (file) stringResource(R.string.unavailable) else profile.source
    val sourceSide = sideValue(sourceText)
    val configCount = 4
        val contentRows = buildList {
            if (isShareableSubscriptionUrl(profile.source)) {
                add(
                    IosDetail(
                        title = stringResource(R.string.share_subscription_qr),
                        subtitle = stringResource(R.string.share_subscription_qr_tips),
                        disclosure = false,
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
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            HtmlFootnote(tips)
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.profile))
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.name),
                    subtitle = if (nameSide == null) profile.name.ifBlank { null } else null,
                    value = nameSide,
                    disclosure = false,
                    onClick = onName,
                ),
                index = 0,
                count = configCount,
            )
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.url),
                    subtitle = if (sourceSide == null) sourceText.ifBlank { null } else null,
                    value = sourceSide,
                    enabled = !file,
                    disclosure = false,
                    onClick = if (urlEditable) onUrl else null,
                ),
                index = 1,
                count = configCount,
            )
            Box {
                IosPreferenceRow(
                    row = IosDetail(
                        title = stringResource(R.string.auto_update),
                        value = intervalLabel,
                        enabled = !file,
                        disclosure = false,
                        onClick = if (file) null else { { intervalOpen = true } },
                    ),
                    index = 2,
                    count = configCount,
                )
                if (!file) {
                    IosPopupMenu(
                        expanded = intervalOpen,
                        onDismiss = { intervalOpen = false },
                        actions = presetLabels.mapIndexed { index, label ->
                            IosAction(
                                title = label,
                                marked = index == presetIndex,
                                onClick = { onIntervalPreset(presets[index]) },
                            )
                        } + IosAction(
                            title = stringResource(R.string.auto_update_custom),
                            marked = presetIndex < 0,
                            onClick = onIntervalCustom,
                        ),
                    )
                }
            }
            IosPreferenceRow(
                row = IosDetail(
                    title = stringResource(R.string.age_secret_key),
                    disclosure = false,
                    // Named reuse: webdav_password_set is the existing 已设置 / Set string.
                    value = stringResource(
                        if (ageSet) R.string.webdav_password_set else R.string.webdav_unset,
                    ),
                    onClick = onAgeKey,
                ),
                index = 3,
                count = configCount,
            )
            Spacer(Modifier.height(12.dp))
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
        style = IosType.section,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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
