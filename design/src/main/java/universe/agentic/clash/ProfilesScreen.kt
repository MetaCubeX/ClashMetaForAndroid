package universe.agentic.clash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import nota.ui.ios.shellIosGroupShape
import nota.ui.theme.ShellShapes
import nota.ui.theme.rememberShellPressAlpha
import nota.ui.theme.shellPressBackground
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.core.model.ProfilePreview
import com.github.kr328.clash.core.model.ProfilePreviewGroup
import com.github.kr328.clash.core.model.ProfilePreviewMember
import com.github.kr328.clash.core.model.ProfilePreviewProvider
import com.github.kr328.clash.core.model.ProfilePreviewProxy
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.UUID

data class GeoFileUi(
    val name: String,
    val detail: String,
)

@Composable
fun ProfilesScreen(
    insets: Insets,
    title: String,
    profiles: List<Profile>,
    loaded: Boolean,
    updatable: Boolean,
    allUpdating: Boolean,
    updating: Set<UUID>,
    now: Long,
    onBack: () -> Unit,
    providers: List<ProfileProvider.External>,
    onRefreshProviders: () -> Unit,
    onCreateProvider: (ProfileProvider) -> Unit,
    onUpdateAll: () -> Unit,
    onActive: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onShare: (Profile) -> Unit,
    onPreview: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    geoFiles: List<GeoFileUi>,
    profileFileDetails: Map<String, String>,
    onOpenFiles: (Profile) -> Unit,
    onOpenGeo: () -> Unit,
    onReorder: (List<UUID>) -> Unit,
    previewProfile: Profile?,
    previewLoading: Boolean,
    previewResult: ProfilePreview?,
    previewError: String?,
    onDismissPreview: () -> Unit,
    embedded: Boolean = false,
    sectionJump: Int = 0,
    sectionIndex: Int = 0,
) {
    var section by remember { mutableStateOf(0) }
    var createMenu by remember { mutableStateOf(false) }
    val openCreateMenu = {
        createMenu = true
        onRefreshProviders()
    }
    LaunchedEffect(sectionJump) {
        if (sectionJump != 0) section = sectionIndex
    }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        embedded = embedded,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (updatable) {
                    IosRefreshIcon(
                        icon = rememberVectorPainter(Icons.Filled.Refresh),
                        description = stringResource(R.string.update_all),
                        busy = allUpdating,
                        onClick = onUpdateAll,
                    )
                }
                Box {
                    BarIcon(
                        image = Icons.Filled.Add,
                        description = stringResource(R.string._new),
                        onClick = openCreateMenu,
                    )
                    IosPopupMenu(
                        expanded = createMenu,
                        onDismiss = { createMenu = false },
                        groups = createMenuGroups(
                            providers = providers,
                            onCreateProvider = onCreateProvider,
                        ),
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(IosContentTop))
            IosSegmentedControl(
                items = listOf(
                    stringResource(R.string.profiles),
                    stringResource(R.string.files),
                ),
                selected = section,
                onSelect = { section = it },
            )
            Spacer(Modifier.height(IosContentTop))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (section == 1) {
                    ProfileFilesPane(
                        profiles = profiles,
                        geoFiles = geoFiles,
                        profileFileDetails = profileFileDetails,
                        onOpenFiles = onOpenFiles,
                        onOpenGeo = onOpenGeo,
                        onEdit = onEdit,
                    )
                } else {
                    ProfileList(
                        profiles = profiles,
                        loaded = loaded,
                        now = now,
                        onCreate = openCreateMenu,
                        onActive = onActive,
                        updating = updating,
                        onUpdate = onUpdate,
                        onEdit = onEdit,
                        onShare = onShare,
                        onPreview = onPreview,
                        onDuplicate = onDuplicate,
                        onDelete = onDelete,
                        onReorder = onReorder,
                    )
                }
            }
        }
    }
    if (previewProfile != null) {
        ProfilePreviewSheet(
            profile = previewProfile,
            loading = previewLoading,
            result = previewResult,
            error = previewError,
            onDismiss = onDismissPreview,
        )
    }
}

@Composable
private fun ProfileList(
    profiles: List<Profile>,
    loaded: Boolean,
    now: Long,
    updating: Set<UUID>,
    onCreate: () -> Unit,
    onActive: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onShare: (Profile) -> Unit,
    onPreview: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onReorder: (List<UUID>) -> Unit,
) {
    var rows by remember { mutableStateOf(profiles) }
    var dragId by remember { mutableStateOf<UUID?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<UUID, Int>() }
    val listState = rememberLazyListState()
    val reorder = rememberUpdatedState(onReorder)
    val saved = rememberUpdatedState(profiles)
    LaunchedEffect(profiles) {
        if (dragId == null) rows = profiles
    }
    val moveBy = rememberUpdatedState<(Float) -> Unit> { delta ->
        val id = dragId ?: return@rememberUpdatedState
        var index = rows.indexOfFirst { it.uuid == id }
        if (index < 0) return@rememberUpdatedState
        var offset = dragOffset + delta
        val next = rows.toMutableList()
        while (index < next.lastIndex) {
            val neighbor = heights[next[index + 1].uuid] ?: break
            if (neighbor <= 0 || offset <= neighbor / 2f) break
            offset -= neighbor
            val item = next.removeAt(index)
            next.add(index + 1, item)
            index += 1
        }
        while (index > 0) {
            val neighbor = heights[next[index - 1].uuid] ?: break
            if (neighbor <= 0 || offset >= -neighbor / 2f) break
            offset += neighbor
            val item = next.removeAt(index)
            next.add(index - 1, item)
            index -= 1
        }
        rows = next
        dragOffset = offset
    }
    val finish = rememberUpdatedState {
        val id = dragId
        dragId = null
        dragOffset = 0f
        if (id == null) return@rememberUpdatedState
        val uuids = rows.map { it.uuid }
        if (uuids != saved.value.map { it.uuid }) reorder.value(uuids)
    }
    LaunchedEffect(dragId) {
        val id = dragId ?: return@LaunchedEffect
        while (isActive && dragId == id) {
            val info = listState.layoutInfo.visibleItemsInfo.find { it.key == id }
            val threshold = 72f
            val delta = when {
                info == null -> 0f
                info.offset < listState.layoutInfo.viewportStartOffset + threshold &&
                    listState.canScrollBackward -> -28f
                info.offset + info.size > listState.layoutInfo.viewportEndOffset - threshold &&
                    listState.canScrollForward -> 28f
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                moveBy.value(delta)
            }
            delay(16)
        }
    }
    when {
        !loaded && profiles.isEmpty() -> IosProgressRow(stringResource(R.string.loading))
        profiles.isEmpty() -> IosEmptyState(
            message = stringResource(R.string.list_empty),
            action = stringResource(R.string._new),
            onAction = onCreate,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(rows, key = { _, profile -> profile.uuid }) { index, profile ->
                val dragging = dragId == profile.uuid
                val shape = shellIosGroupShape(index, rows.size)
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned { heights[profile.uuid] = it.size.height }
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            shadowElevation = if (dragging) 16f else 0f
                        }
                        .clip(shape)
                        .background(IosTheme.colors.grouped),
                ) {
                    ProfileRow(
                        profile = profile,
                        now = now,
                        showDivider = index < rows.lastIndex,
                        onActive = { onActive(profile) },
                        onEdit = { onEdit(profile) },
                        updating = profile.uuid in updating,
                        onUpdate = { onUpdate(profile) },
                        onShare = { onShare(profile) },
                        onPreview = { onPreview(profile) },
                        onDuplicate = { onDuplicate(profile) },
                        onDelete = { onDelete(profile) },
                        reorder = if (rows.size < 2) {
                            null
                        } else {
                            Modifier.pointerInput(profile.uuid) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragId = profile.uuid
                                        dragOffset = 0f
                                    },
                                    onDragEnd = { finish.value() },
                                    onDragCancel = { finish.value() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        moveBy.value(amount.y)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileFilesPane(
    profiles: List<Profile>,
    geoFiles: List<GeoFileUi>,
    profileFileDetails: Map<String, String>,
    onOpenFiles: (Profile) -> Unit,
    onOpenGeo: () -> Unit,
    onEdit: (Profile) -> Unit,
) {
    val imported = profiles.filter { it.imported }
    val active = imported.firstOrNull { it.active }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "profile-files") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(ShellShapes.Container)
                    .background(IosTheme.colors.grouped),
            ) {
                if (imported.isEmpty()) {
                    IosListRow(
                        row = IosDetail(
                            title = stringResource(R.string.files),
                            subtitle = stringResource(R.string.not_selected),
                            disclosure = false,
                        ),
                        showDivider = true,
                    )
                } else {
                    imported.forEachIndexed { index, profile ->
                        IosListRow(
                            row = IosDetail(
                                title = profile.name,
                                subtitle = profileFileDetails[profile.uuid.toString()]
                                    ?: stringResource(R.string.files),
                                onClick = { onOpenFiles(profile) },
                            ),
                            showDivider = true,
                        )
                    }
                }
                if (active != null) {
                    IosListRow(
                        row = IosDetail(
                            title = stringResource(R.string.age_secret_key),
                            subtitle = stringResource(
                                if (active.ageSecretKey.isNullOrBlank()) {
                                    R.string.webdav_unset
                                } else {
                                    R.string.webdav_password_set
                                },
                            ),
                            onClick = { onEdit(active) },
                        ),
                        showDivider = true,
                    )
                }
                if (geoFiles.isEmpty()) {
                    IosListRow(
                        row = IosDetail(
                            title = stringResource(R.string.geo_files),
                            subtitle = stringResource(R.string.list_empty),
                            onClick = onOpenGeo,
                        ),
                        showDivider = false,
                    )
                } else {
                    geoFiles.forEachIndexed { index, file ->
                        IosListRow(
                            row = IosDetail(
                                title = file.name,
                                subtitle = file.detail,
                                onClick = onOpenGeo,
                            ),
                            showDivider = index < geoFiles.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragGrip(modifier: Modifier) {
    val colors = IosTheme.colors
    val description = stringResource(R.string.reorder_profile)
    Column(
        modifier
            .size(44.dp)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                Modifier
                    .padding(vertical = 1.5.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .clip(ShellShapes.Pill)
                    .background(colors.tertiaryLabel),
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    now: Long,
    showDivider: Boolean,
    onActive: () -> Unit,
    onEdit: () -> Unit,
    updating: Boolean,
    onUpdate: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    reorder: Modifier? = null,
) {
    var menuOpen by remember(profile.uuid) { mutableStateOf(false) }
    val colors = IosTheme.colors
    val fraction = usageFraction(profile)
    val canUpdate = profile.imported && profile.type != Profile.Type.File
    val shareable = isShareableSubscriptionUrl(profile.source)
    val updateIcon = rememberVectorPainter(Icons.Filled.Refresh)
    val updateLabel = stringResource(R.string.update)
    val shareIcon = painterResource(R.drawable.ic_baseline_qr_code)
    val shareLabel = stringResource(R.string.share_subscription_qr)
    val previewIcon = painterResource(R.drawable.ic_baseline_view_list)
    val previewLabel = stringResource(R.string.preview_outbounds)
    val moreIcon = painterResource(R.drawable.ic_baseline_more_vert)
    val moreLabel = stringResource(R.string.more)
    val subtitle = profileSubtitle(profile, now, fraction)
    val interaction = remember(profile.uuid) { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (profile.active) {
                    Modifier.background(colors.label.copy(alpha = 0.12f))
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reorder != null) DragGrip(reorder)
            Column(
                Modifier
                    .weight(1f)
                    .shellPressBackground(interactionSource = interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { if (profile.active) onEdit() else onActive() },
                    )
                    .semantics { if (profile.active) selected = true }
                    .padding(
                        start = if (reorder == null) 16.dp else 0.dp,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = IosType.headline.copy(
                            fontWeight = if (profile.active) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IosNavIcon(
                        icon = previewIcon,
                        description = previewLabel,
                        onClick = onPreview,
                    )
                    Box {
                        IosNavIcon(
                            icon = moreIcon,
                            description = moreLabel,
                            onClick = { menuOpen = true },
                        )
                        IosPopupMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            actions = profileActions(
                                profile = profile,
                                onEdit = { onEdit() },
                                onShare = { onShare() },
                                onDuplicate = { onDuplicate() },
                                onDelete = { onDelete() },
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = IosType.footnote,
                        color = colors.secondaryLabel,
                        modifier = Modifier.weight(1f),
                    )
                    if (canUpdate) {
                        IosRefreshIcon(
                            icon = updateIcon,
                            description = updateLabel,
                            busy = updating,
                            onClick = onUpdate,
                        )
                    }
                    if (shareable) {
                        IosNavIcon(
                            icon = shareIcon,
                            description = shareLabel,
                            onClick = onShare,
                        )
                    }
                }
            }
        }
        if (fraction != null) {
            IosFractionBar(
                fraction = fraction,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

@Composable
private fun createMenuGroups(
    providers: List<ProfileProvider.External>,
    onCreateProvider: (ProfileProvider) -> Unit,
): List<IosMenuGroup> {
    val context = LocalContext.current
    val builtin = listOf(
        IosAction(stringResource(R.string.import_from_file)) {
            onCreateProvider(ProfileProvider.File(context))
        },
        IosAction(stringResource(R.string.import_from_url)) {
            onCreateProvider(ProfileProvider.Url(context))
        },
        IosAction(stringResource(R.string.import_from_qr)) {
            onCreateProvider(ProfileProvider.QR(context))
        },
    )
    val external = providers.map { provider ->
        IosAction(provider.summary.ifBlank { provider.name }) { onCreateProvider(provider) }
    }
    return buildList {
        add(IosMenuGroup(actions = builtin))
        if (external.isNotEmpty()) {
            add(IosMenuGroup(title = stringResource(R.string.providers), actions = external))
        }
    }
}

@Composable
private fun profileActions(
    profile: Profile,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
): List<IosAction> {
    val edit = stringResource(R.string.edit)
    val share = stringResource(R.string.share_subscription_qr)
    val duplicate = stringResource(R.string.duplicate)
    val delete = stringResource(R.string.delete)
    return buildList {
        add(IosAction(edit, onClick = onEdit))
        if (isShareableSubscriptionUrl(profile.source)) {
            add(IosAction(share, onClick = onShare))
        }
        if (profile.imported) {
            add(IosAction(duplicate, onClick = onDuplicate))
        }
        add(IosAction(delete, destructive = true, onClick = onDelete))
    }
}

@Composable
private fun profileSubtitle(profile: Profile, now: Long, fraction: Float?): AnnotatedString {
    val context = LocalContext.current
    val expiredColor = IosTheme.colors.red
    val typeName = when (profile.type) {
        Profile.Type.File -> stringResource(R.string.file)
        Profile.Type.Url -> stringResource(R.string.url)
        Profile.Type.External -> stringResource(R.string.external)
    }
    val typeLine = when {
        profile.pending -> stringResource(R.string.format_type_unsaved, typeName)
        profile.type == Profile.Type.Url -> null
        else -> typeName
    }
    val expiredLabel = stringResource(R.string.profile_expired)
    val expire = expireLine(profile, now, expiredLabel)
    val expired = profile.expire > 0L && profile.expire < now
    val updated = if (profile.expire > 0L || profile.updatedAt <= 0L) {
        null
    } else {
        (now - profile.updatedAt).elapsedIntervalString(context)
    }
    val lines = listOfNotNull(typeLine, trafficLine(profile, fraction), expire, updated)
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            if (expired && line == expire) {
                withStyle(SpanStyle(color = expiredColor)) { append(line) }
            } else {
                append(line)
            }
        }
    }
}

private fun trafficLine(profile: Profile, fraction: Float?): String? {
    val used = (profile.download + profile.upload).toBytesString()
    if (fraction != null) {
        return used + "/" + profile.total.toBytesString() + " · " + percentText(fraction)
    }
    if (profile.download < 2) return null
    return used
}

private fun percentText(fraction: Float): String {
    val pct = (fraction * 100f).coerceIn(0f, 100f)
    val number = if (pct > 0f && pct < 10f) {
        String.format(Locale.US, "%.1f", pct)
    } else {
        String.format(Locale.US, "%.0f", pct)
    }
    return "$number%"
}

private fun expireLine(profile: Profile, now: Long, expiredLabel: String): String? {
    if (profile.expire <= 0L) return null
    val date = profile.expire.toDateStr()
    return if (profile.expire < now) "$expiredLabel $date" else date
}

/**
 * Old binding: max 1000, integer (download + upload) / (total / 1000).
 * total/1000 == 0 throws. Skip the bar in that case.
 */
private fun usageFraction(profile: Profile): Float? {
    if (profile.total < 2) return null
    val divisor = profile.total / 1000
    if (divisor == 0L) return null
    val ticks = (profile.download + profile.upload) / divisor
    return (ticks.toFloat() / 1000f).coerceIn(0f, 1f)
}

private enum class PreviewTab { Groups, Nodes, Providers }

private sealed class PreviewLine {
    abstract val key: String

    data class Header(
        val index: Int,
        val group: ProfilePreviewGroup,
        val open: Boolean,
    ) : PreviewLine() {
        override val key = "h:$index"
    }

    data class IncludeAll(val index: Int) : PreviewLine() {
        override val key = "a:$index"
    }

    data class Member(
        val index: Int,
        val memberIndex: Int,
        val member: ProfilePreviewMember,
    ) : PreviewLine() {
        override val key = "m:$index:$memberIndex"
    }
}

@Composable
private fun ProfilePreviewSheet(
    profile: Profile,
    loading: Boolean,
    result: ProfilePreview?,
    error: String?,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    val body = when {
        loading || error != null -> null
        result == null -> null
        result.groups.isEmpty() && result.proxies.isEmpty() && result.providers.isEmpty() -> null
        else -> result
    }
    IosSheet(onDismiss = onDismiss, fill = body != null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = profile.name,
                style = IosType.title3,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.preview_outbounds),
                style = IosType.footnote,
                color = colors.secondaryLabel,
            )
        }
        when {
            loading -> IosProgressRow(stringResource(R.string.loading))
            error != null -> IosEmptyState(
                title = stringResource(R.string.preview_failed),
                message = error,
            )
            body == null -> IosEmptyState(message = stringResource(R.string.preview_empty))
            else -> PreviewSheetBody(body, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewSheetBody(result: ProfilePreview, modifier: Modifier) {
    val tabs = remember(result) {
        buildList {
            if (result.groups.isNotEmpty()) add(PreviewTab.Groups)
            if (result.proxies.isNotEmpty()) add(PreviewTab.Nodes)
            if (result.providers.isNotEmpty()) add(PreviewTab.Providers)
        }
    }
    var index by remember(tabs) { mutableStateOf(0) }
    var query by remember(tabs) { mutableStateOf("") }
    val selected = index.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
    val labels = tabs.map { tab ->
        stringResource(
            when (tab) {
                PreviewTab.Groups -> R.string.preview_groups
                PreviewTab.Nodes -> R.string.preview_nodes
                PreviewTab.Providers -> R.string.preview_providers
            },
        )
    }
    if (tabs.size > 1) {
        IosSegmentedControl(
            items = labels,
            selected = selected,
            onSelect = {
                index = it
                query = ""
            },
        )
    }
    val tab = tabs[selected]
    Column(modifier.fillMaxWidth()) {
        if (previewSearchable(tab, result)) {
            IosSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search),
                containerColor = IosTheme.colors.background,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                PreviewTab.Groups -> PreviewGroupList(result.groups, query)
                PreviewTab.Nodes -> PreviewProxyList(result.proxies, query)
                PreviewTab.Providers -> PreviewProviderList(result.providers, query)
            }
        }
    }
}

private fun previewSearchable(tab: PreviewTab, result: ProfilePreview): Boolean = when (tab) {
    PreviewTab.Groups -> result.groups.size > 8 || result.groups.any { it.members.size > 20 }
    PreviewTab.Nodes -> result.proxies.size > 8
    PreviewTab.Providers -> result.providers.size > 8
}

@Composable
private fun PreviewGroupList(groups: List<ProfilePreviewGroup>, query: String) {
    var open by remember { mutableStateOf(-1) }
    val lines = remember(groups, query, open) { previewGroupLines(groups, query, open) }
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IosEmptyState(
                message = stringResource(
                    if (query.isEmpty()) R.string.list_empty else R.string.search_empty,
                ),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(count = lines.size, key = { lines[it].key }) { row ->
            when (val line = lines[row]) {
                is PreviewLine.Header -> {
                    val expandable = line.group.members.isNotEmpty() || line.group.includeAll
                    IosListRow(
                        row = IosDetail(
                            title = line.group.name,
                            subtitle = groupDetail(line.group),
                            disclosure = expandable && !line.open,
                            emphasized = line.open,
                            onClick = if (expandable) {
                                { open = if (line.open) -1 else line.index }
                            } else {
                                null
                            },
                        ),
                        showDivider = row < lines.lastIndex,
                    )
                }
                is PreviewLine.IncludeAll -> IosListRow(
                    row = IosDetail(
                        title = stringResource(R.string.preview_include_all),
                        disclosure = false,
                    ),
                    showDivider = row < lines.lastIndex,
                )
                is PreviewLine.Member -> IosListRow(
                    row = IosDetail(
                        title = line.member.name,
                        value = memberDetail(line.member),
                        disclosure = false,
                    ),
                    showDivider = row < lines.lastIndex,
                )
            }
        }
    }
}

private fun previewGroupLines(
    groups: List<ProfilePreviewGroup>,
    query: String,
    open: Int,
): List<PreviewLine> {
    val needle = query.trim()
    return buildList {
        groups.forEachIndexed { index, group ->
            val nameHit = needle.isEmpty() || group.name.contains(needle, ignoreCase = true)
            val matched = if (needle.isEmpty() || nameHit) {
                group.members
            } else {
                group.members.filter { member ->
                    member.name.contains(needle, ignoreCase = true) ||
                        member.type.contains(needle, ignoreCase = true)
                }
            }
            val memberHit = needle.isNotEmpty() && matched.isNotEmpty() && !nameHit
            if (!nameHit && !memberHit && index != open) return@forEachIndexed
            val expanded = index == open || memberHit
            add(PreviewLine.Header(index, group, expanded))
            if (!expanded) return@forEachIndexed
            if (group.includeAll && (needle.isEmpty() || nameHit)) add(PreviewLine.IncludeAll(index))
            matched.forEachIndexed { memberIndex, member ->
                add(PreviewLine.Member(index, memberIndex, member))
            }
        }
    }
}

@Composable
private fun groupDetail(group: ProfilePreviewGroup): String? {
    val count = if (group.members.isNotEmpty()) {
        stringResource(R.string.format_elements, group.members.size)
    } else {
        null
    }
    val include = if (group.includeAll) stringResource(R.string.preview_include_all) else null
    return listOfNotNull(group.type.ifBlank { null }, count, include)
        .joinToString(" · ")
        .ifBlank { null }
}

@Composable
private fun PreviewProxyList(proxies: List<ProfilePreviewProxy>, query: String) {
    val shown = remember(proxies, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            proxies
        } else {
            proxies.filter { proxy ->
                proxy.name.contains(needle, ignoreCase = true) ||
                    proxy.type.contains(needle, ignoreCase = true)
            }
        }
    }
    PreviewValueList(
        count = shown.size,
        emptyQuery = query.isEmpty(),
        key = { shown[it].name + "#$it" },
        title = { shown[it].name },
        value = { shown[it].type.ifBlank { null } },
    )
}

@Composable
private fun PreviewProviderList(providers: List<ProfilePreviewProvider>, query: String) {
    val shown = remember(providers, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            providers
        } else {
            providers.filter { provider ->
                provider.name.contains(needle, ignoreCase = true) ||
                    provider.type.contains(needle, ignoreCase = true)
            }
        }
    }
    PreviewValueList(
        count = shown.size,
        emptyQuery = query.isEmpty(),
        key = { shown[it].name + "#$it" },
        title = { shown[it].name },
        value = { shown[it].type.ifBlank { null } },
    )
}

@Composable
private fun PreviewValueList(
    count: Int,
    emptyQuery: Boolean,
    key: (Int) -> String,
    title: @Composable (Int) -> String,
    value: @Composable (Int) -> String?,
) {
    if (count == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            IosEmptyState(
                message = stringResource(
                    if (emptyQuery) R.string.list_empty else R.string.search_empty,
                ),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(count = count, key = key) { index ->
            IosListRow(
                row = IosDetail(
                    title = title(index),
                    value = value(index),
                    disclosure = false,
                ),
                showDivider = index < count - 1,
            )
        }
    }
}

@Composable
private fun memberDetail(member: ProfilePreviewMember): String? {
    return listOfNotNull(member.type.ifBlank { null }, memberKindLabel(member))
        .joinToString(" · ")
        .ifBlank { null }
}

@Composable
private fun memberKindLabel(member: ProfilePreviewMember): String? = when (member.kind) {
    "group" -> stringResource(R.string.preview_groups)
    "provider" -> stringResource(R.string.preview_providers)
    else -> null
}

@Composable
private fun BarIcon(
    image: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = rememberShellPressAlpha(interaction)
    Box(
        Modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = description,
            tint = IosTheme.colors.label,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = pressed },
        )
    }
}
