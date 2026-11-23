package universe.agentic.clash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import kotlinx.coroutines.launch
import nota.ui.theme.ShellShapes
import nota.ui.theme.shellPressBackground

@Composable
fun AboutScreen(
    insets: Insets,
    title: String,
    versionName: String,
    icon: ImageBitmap,
    noticesFocus: FocusRequester,
    focusNotices: Boolean,
    onFocusNoticesConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onNotices: () -> Unit,
) {
    var qrItem by remember { mutableStateOf<AboutItem?>(null) }
    val qrFocus = remember {
        (AboutContent.developer + AboutContent.community)
            .filter { it.qrRes != null }
            .associate { it.id to FocusRequester() }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(focusNotices) {
        if (focusNotices) {
            runCatching { noticesFocus.requestFocus() }
            onFocusNoticesConsumed()
        }
    }

    IosScreenScaffold(insets = insets, title = title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = IosContentTop, bottom = 24.dp),
        ) {
            IdentityHeader(versionName = versionName, icon = icon)
            Spacer(Modifier.height(12.dp))
            AboutGroup(
                title = stringResource(R.string.about_section_developer),
                items = AboutContent.developer,
                qrFocus = qrFocus,
                onOpen = onOpen,
                onCopy = onCopy,
                onQr = { qrItem = it },
            )
            Spacer(Modifier.height(12.dp))
            AboutGroup(
                title = stringResource(R.string.about_section_community),
                items = AboutContent.community,
                qrFocus = qrFocus,
                onOpen = onOpen,
                onCopy = onCopy,
                onQr = { qrItem = it },
            )
            Spacer(Modifier.height(12.dp))
            AboutGroup(
                title = stringResource(R.string.about_section_app_info),
                items = AboutContent.appInfo,
                onOpen = onOpen,
                onCopy = onCopy,
                onNotices = onNotices,
                noticesFocus = noticesFocus,
            )
        }
    }

    qrItem?.let { item ->
        ChannelQrDialog(
            item = item,
            onCopy = onCopy,
            onDismiss = {
                qrItem = null
                scope.launch { runCatching { qrFocus[item.id]?.requestFocus() } }
            },
        )
    }
}

@Composable
private fun IdentityHeader(versionName: String, icon: ImageBitmap) {
    val colors = IosTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.application_name),
                style = IosType.title3,
                color = colors.label,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = versionName,
                style = IosType.body,
                color = colors.secondaryLabel,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                style = IosType.footnote,
                color = colors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun AboutGroup(
    title: String,
    items: List<AboutItem>,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    qrFocus: Map<String, FocusRequester> = emptyMap(),
    onQr: (AboutItem) -> Unit = {},
    onNotices: () -> Unit = {},
    noticesFocus: FocusRequester? = null,
) {
    val copyLabel = stringResource(R.string.author_copy)
    val separator = stringResource(R.string.author_spoken_separator)
    val rows = buildList {
        items.forEach { item ->
            val label = item.labelRes?.let { stringResource(it) }
            val value = item.valueRes?.let { stringResource(it) } ?: item.value
            val copy = item.action == AboutAction.Copy
            val subPage = item.action == AboutAction.SubPage
            val actionHint = when (item.action) {
                AboutAction.Mail -> stringResource(R.string.opens_in_mail)
                AboutAction.Url -> stringResource(R.string.opens_in_browser)
                AboutAction.Copy -> copyLabel
                else -> null
            }
            val spoken = listOfNotNull(label, value, actionHint)
                .filter { it.isNotBlank() }
                .joinToString(separator)
            add(
                IosDetail(
                    title = label ?: value,
                        modifier = if (subPage && noticesFocus != null) {
                            Modifier.focusRequester(noticesFocus)
                        } else {
                            Modifier
                        },
                    subtitle = if (label != null) value.takeIf { it.isNotEmpty() } else null,
                    onClick = when (item.action) {
                        AboutAction.None -> null
                        AboutAction.Copy -> ({ onCopy(value) })
                        AboutAction.SubPage -> onNotices
                        else -> ({ onOpen(AboutContent.target(item)) })
                    },
                    trailingIcon = if (copy) R.drawable.ic_baseline_content_copy else null,
                    trailingIconDescription = if (copy) copyLabel else null,
                    disclosure = subPage,
                    external = item.action == AboutAction.Url || item.action == AboutAction.Mail,
                    externalDescription = if (item.action == AboutAction.Mail) {
                        stringResource(R.string.opens_in_mail)
                    } else {
                        null
                    },
                    spokenDescription = spoken,
                ),
            )
            if (item.qrRes != null) {
                add(
                    IosDetail(
                        title = stringResource(R.string.author_show_qr),
                        modifier = Modifier.focusRequester(qrFocus.getValue(item.id)),
                        leadingIcon = R.drawable.ic_baseline_qr_code,
                        onClick = { onQr(item) },
                        disclosure = false,
                        spokenDescription = listOf(
                            label ?: value,
                            stringResource(R.string.author_show_qr),
                        ).joinToString(separator),
                    ),
                )
            }
        }
    }
    IosInsetGroup(title = title, rows = rows)
}

@Composable
private fun ChannelQrDialog(
    item: AboutItem,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    val label = item.labelRes?.let { stringResource(it) } ?: item.value
    val value = item.valueRes?.let { stringResource(it) } ?: item.value
    val hint = item.qrHintRes?.let { stringResource(it) } ?: stringResource(R.string.author_show_qr)
    val description = stringResource(R.string.author_qr_description, label, value)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(ShellShapes.Container)
                    .background(colors.grouped)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$label · $value",
                    style = IosType.title3,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = hint,
                    style = IosType.footnote,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                item.qrRes?.let { res ->
                    Image(
                        painter = painterResource(res),
                        contentDescription = description,
                        modifier = Modifier.size(240.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.action == AboutAction.Copy) {
                        DialogPillButton(
                            text = stringResource(R.string.author_copy),
                            container = colors.track,
                            content = colors.label,
                            onClick = { onCopy(value) },
                        )
                    }
                    DialogPillButton(
                        text = stringResource(R.string.close),
                        container = colors.blue,
                        content = colors.onBlue,
                        pressTint = Color.Black.copy(alpha = 0.10f),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}
