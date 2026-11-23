package universe.agentic.clash

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import nota.ui.ios.shellIosGroupShape
import nota.ui.theme.ShellShapes
import nota.ui.theme.shellPressBackground
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetCheckState
import com.github.kr328.clash.design.model.AppSetUi
import com.github.kr328.clash.design.model.appSetMembership
import com.github.kr328.clash.design.ui.Insets
import nota.ui.settings.PreferenceChevron

private val accessSorts = listOf(
    AppInfoSort.Label,
    AppInfoSort.PackageName,
    AppInfoSort.InstallTime,
    AppInfoSort.UpdateTime,
)

data class AppSetEditData(
    val id: String?,
    val name: String,
    val packages: Set<String>,
    val isBuiltin: Boolean,
    val description: String = "",
    val membershipFrozen: Boolean = false,
    val catalogPackages: Set<String> = emptySet(),
)

private fun AppSetUi.toEditData() = AppSetEditData(
    id = id,
    name = name,
    packages = packages,
    isBuiltin = isBuiltin,
    description = description,
    membershipFrozen = membershipFrozen,
    catalogPackages = if (isBuiltin) catalogPackages else packages,
)

private enum class MembershipMode {
    Catalog,
    Snapshot,
}

@Composable
fun AccessControlScreen(
    insets: Insets,
    title: String,
    query: String,
    keyword: String,
    apps: List<AppInfo>,
    selection: Set<String>,
    appSets: List<AppSetUi>,
    loading: Boolean,
    hideSystem: Boolean,
    sort: AppInfoSort,
    reversed: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onHideSystem: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onReplaceSelection: (Set<String>) -> Unit,
    onToggleSet: (String) -> Unit,
    onSaveAppSet: (id: String?, name: String, description: String, packages: List<String>, membershipFrozen: Boolean) -> Unit,
    onDeleteAppSet: (id: String) -> Unit,
    openSetId: String? = null,
    onOpenSetConsumed: () -> Unit = {},
    editorApps: List<AppInfo> = emptyList(),
    editorAppsReady: Boolean = false,
    onPrepareEditorApps: () -> Unit = {},
    createSet: Boolean = false,
    onCreateSetConsumed: () -> Unit = {},
    closeWhenEditorCloses: Boolean = false,
    onEditorClosed: () -> Unit = {},
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectInvert: () -> Unit,
    onSort: (AppInfoSort) -> Unit,
    onReverse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    appSetsOnly: Boolean = false,
) {
    val colors = IosTheme.colors

    val visible = remember(apps, keyword) {
        if (keyword.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<AppSetEditData?>(null) }
    LaunchedEffect(openSetId, appSets) {
        val id = openSetId ?: return@LaunchedEffect
        val set = appSets.find { it.id == id } ?: return@LaunchedEffect
        editingSet = set.toEditData()
        onOpenSetConsumed()
    }
    LaunchedEffect(createSet) {
        if (!createSet) return@LaunchedEffect
        editingSet = AppSetEditData(
            id = null,
            name = "",
            packages = emptySet(),
            isBuiltin = false,
        )
        onCreateSetConsumed()
    }
    LaunchedEffect(editingSet != null, editorAppsReady) {
        if (editingSet != null && !editorAppsReady) onPrepareEditorApps()
    }

    val sortLabels = listOf(
        stringResource(R.string.name),
        stringResource(R.string.package_name),
        stringResource(R.string.install_time),
        stringResource(R.string.update_time),
    )

    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = if (appSetsOnly) {
            null
        } else {
            {
                Box {
                    IosIconButton(
                        icon = R.drawable.ic_baseline_more_vert,
                        description = stringResource(R.string.more),
                        onClick = { menuOpen = true },
                    )
                    IosPopupMenu(
                        expanded = menuOpen,
                        onDismiss = {
                            menuOpen = false
                            sorting = false
                        },
                        actions = if (sorting) {
                            accessSorts.mapIndexed { index, mode ->
                                IosAction(
                                    title = sortLabels[index],
                                    marked = sort == mode,
                                    onClick = { onSort(mode) },
                                )
                            }
                        } else {
                            listOf(
                                IosAction(stringResource(R.string.select_all), onClick = onSelectAll),
                                IosAction(stringResource(R.string.select_none), onClick = onSelectNone),
                                IosAction(stringResource(R.string.select_invert), onClick = onSelectInvert),
                                IosAction(stringResource(R.string.sort)) {
                                    sorting = true
                                    menuOpen = true
                                },
                                IosAction(stringResource(R.string.reverse), marked = reversed, onClick = onReverse),
                                IosAction(stringResource(R.string.import_from_clipboard), onClick = onImport),
                                IosAction(stringResource(R.string.export_to_clipboard), onClick = onExport),
                            )
                        },
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!appSetsOnly) {
                IosSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.search),
                )
            }
            val appListState = rememberLazyListState()
            val appOrder = remember(visible) { visible.map { it.packageName } }
            LazyColumn(
                state = appListState,
                modifier = Modifier
                    .weight(1f)
                    .checkboxDragSelect(
                        listState = appListState,
                        order = appOrder,
                        selected = selection,
                        onSelected = onReplaceSelection,
                    ),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!appSetsOnly) {
                    item(key = "access-filter") {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            SectionLabel(stringResource(R.string.filter))
                            IosSwitchRow(
                                title = hideSystemAppsLabel(),
                                summary = null,
                                checked = hideSystem,
                                index = 0,
                                count = 1,
                                onCheckedChange = onHideSystem,
                            )
                        }
                    }
                }

                if (loading) {
                    item(key = "access-progress") {
                        IosProgressRow(stringResource(R.string.loading))
                    }
                } else {
                    // App Sets Section
                    item(key = "access-sets-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.app_sets),
                                style = IosType.footnote,
                                color = colors.secondaryLabel,
                            )
                            IosIconButton(
                                icon = R.drawable.ic_baseline_add,
                                description = stringResource(R.string.new_app_set),
                                onClick = {
                                    editingSet = AppSetEditData(
                                        id = null,
                                        name = "",
                                        packages = emptySet(),
                                        isBuiltin = false,
                                    )
                                },
                            )
                        }
                    }

                    item(key = "access-sets-card") {
                        if (appSets.isNotEmpty()) {
                            Column(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(ShellShapes.Container)
                                    .background(colors.grouped),
                            ) {
                                appSets.forEachIndexed { index, set ->
                                    val membership = appSetMembership(set.packages, selection)
                                    val enabled = set.packages.isNotEmpty()
                                    val summary = if (!enabled) {
                                        stringResource(R.string.app_set_summary_empty)
                                    } else {
                                        stringResource(R.string.app_set_summary, membership.total, membership.selectedCount)
                                    }

                                    SetRowContent(
                                        title = set.name,
                                        summary = summary,
                                        state = membership.state,
                                        isBuiltin = set.isBuiltin,
                                        enabled = enabled,
                                        onToggle = { onToggleSet(set.id) },
                                        onEdit = {
                                            editingSet = set.toEditData()
                                        },
                                    )

                                    if (index < appSets.lastIndex) {
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
                        }
                    }

                    if (!appSetsOnly) {
                        // App count header
                        item(key = "access-selected-count") {
                        Column(Modifier.fillMaxWidth()) {
                            SectionLabel(
                                pluralStringResource(
                                    R.plurals.selected_apps_count,
                                    selection.size,
                                    selection.size,
                                ),
                            )
                            Row(
                                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PillActionButton(
                                    label = stringResource(R.string.select_all),
                                    onClick = onSelectAll,
                                )
                                PillActionButton(
                                    label = stringResource(R.string.select_none),
                                    onClick = onSelectNone,
                                )
                                PillActionButton(
                                    label = stringResource(R.string.select_invert),
                                    onClick = onSelectInvert,
                                )
                            }
                        }
                        }

                        if (visible.isEmpty()) {
                            item(key = "access-empty") {
                                IosEmptyState(
                                    message = if (keyword.isEmpty()) {
                                        stringResource(R.string.app_set_summary_empty)
                                    } else {
                                        stringResource(R.string.search_empty)
                                    },
                                )
                            }
                        } else {
                            itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
                                val checked = app.packageName in selection
                                val shape = shellIosGroupShape(index, visible.size)
                                IosListRow(
                                    row = IosDetail(
                                        title = app.label,
                                        subtitle = app.packageName,
                                        selection = if (checked) {
                                            IosSelection.Checked
                                        } else {
                                            IosSelection.None
                                        },
                                        checkable = true,
                                        disclosure = false,
                                        emphasized = checked,
                                        onClick = { onToggleApp(app.packageName) },
                                    ),
                                    showDivider = index < visible.lastIndex,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .clip(shape),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Edit / Create App Set Dialog
        editingSet?.let { editData ->
            key(editData.id ?: "new-app-set") {
                AppSetEditModal(
                    initial = editData,
                    editorApps = editorApps,
                    editorAppsReady = editorAppsReady,
                    onSave = { id, name, description, packages, frozen ->
                        onSaveAppSet(id, name, description, packages, frozen)
                        editingSet = null
                    },
                    onDelete = { id ->
                        onDeleteAppSet(id)
                        editingSet = null
                    },
                    onDismiss = {
                        editingSet = null
                        if (closeWhenEditorCloses) onEditorClosed()
                    },
                )
            }
        }
    }
}

@Composable
private fun SetRowContent(
    title: String,
    summary: String,
    state: AppSetCheckState,
    isBuiltin: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .shellPressBackground(interactionSource = interaction)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = IosType.headline,
                    color = if (enabled) colors.label else colors.tertiaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IosStatusPill(
                    text = stringResource(
                        if (isBuiltin) R.string.app_set_builtin else R.string.app_set_custom,
                    ),
                    color = colors.track,
                    contentColor = colors.label,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = IosType.footnote,
                color = if (enabled) colors.secondaryLabel else colors.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Edit button
        IosIconButton(
            icon = R.drawable.ic_baseline_edit,
            description = stringResource(R.string.edit_app_set),
            onClick = onEdit,
        )

        Spacer(Modifier.width(4.dp))

        Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            val mark = when (state) {
                AppSetCheckState.Checked -> IosSelection.Checked
                AppSetCheckState.Indeterminate -> IosSelection.Mixed
                AppSetCheckState.Unchecked -> IosSelection.None
            }
            IosCheckBox(mark)
        }
    }
}

internal data class AppSetEditDraft(
    val id: String?,
    val name: String,
    val description: String,
    val query: String,
    val packages: Set<String>,
    val confirmDelete: Boolean,
    val isBuiltin: Boolean,
    val membershipFrozen: Boolean,
)

internal fun appSetEditDraft(loaded: AppSetEditData): AppSetEditDraft = AppSetEditDraft(
    id = loaded.id,
    name = loaded.name,
    description = loaded.description,
    query = "",
    packages = loaded.packages,
    confirmDelete = false,
    isBuiltin = loaded.isBuiltin,
    membershipFrozen = loaded.membershipFrozen,
)

/** A different loaded id drops the previous draft, including an open delete confirmation. */
internal fun reconcileAppSetEditDraft(
    draft: AppSetEditDraft,
    loaded: AppSetEditData,
): AppSetEditDraft = if (draft.id == loaded.id) draft else appSetEditDraft(loaded)

/** Delete stays on the id that armed this confirmation. A discarded confirmation deletes nothing. */
internal fun appSetEditDeleteId(draft: AppSetEditDraft): String? =
    if (draft.confirmDelete) draft.id else null

internal data class AppSetEditSave(
    val id: String?,
    val name: String,
    val description: String,
    val packages: List<String>,
    val membershipFrozen: Boolean,
)

/** Trimmed fields stay on the loaded id. A blank name is left blank for the caller to reject or replace. */
internal fun appSetEditSavePayload(
    bound: AppSetEditDraft,
    editedName: String,
    editedDescription: String,
    packages: Set<String>,
): AppSetEditSave = AppSetEditSave(
    id = bound.id,
    name = editedName.trim(),
    description = editedDescription.trim(),
    packages = packages.toList(),
    membershipFrozen = bound.membershipFrozen,
)

@Composable
private fun AppSetEditModal(
    initial: AppSetEditData,
    editorApps: List<AppInfo>,
    editorAppsReady: Boolean,
    onSave: (id: String?, name: String, description: String, packages: List<String>, membershipFrozen: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var query by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(initial.packages) }
    var membershipMode by remember {
        mutableStateOf(
            if (initial.isBuiltin && !initial.membershipFrozen) {
                MembershipMode.Catalog
            } else {
                MembershipMode.Snapshot
            },
        )
    }
    var confirmedFreeze by remember { mutableStateOf(initial.membershipFrozen) }
    var nameError by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmFreeze by remember { mutableStateOf(false) }
    var allowDismiss by remember { mutableStateOf(false) }
    val sheetClose = remember { mutableStateOf<(() -> Unit)?>(null) }
    var session by remember { mutableStateOf(appSetEditDraft(initial)) }
    val nextSession = reconcileAppSetEditDraft(session, initial)
    if (nextSession !== session) {
        session = nextSession
        name = nextSession.name
        description = nextSession.description
        query = nextSession.query
        selectedPackages = nextSession.packages
        membershipMode = if (nextSession.isBuiltin && !nextSession.membershipFrozen) {
            MembershipMode.Catalog
        } else {
            MembershipMode.Snapshot
        }
        confirmedFreeze = nextSession.membershipFrozen
        nameError = false
        confirmDelete = nextSession.confirmDelete
        confirmDiscard = false
        confirmFreeze = false
        allowDismiss = false
        section = 0
    }
    val deleteId = appSetEditDeleteId(session.copy(confirmDelete = confirmDelete))

    val orderedApps = remember(initial.id, editorApps) {
        editorApps.sortedWith(
            compareByDescending<AppInfo> { it.packageName in initial.packages }
                .thenBy { it.label.lowercase() },
        )
    }
    val filteredApps = remember(orderedApps, query) {
        if (query.isEmpty()) {
            orderedApps
        } else {
            orderedApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    val includedCount = orderedApps.count { it.packageName in selectedPackages }
    val memberState = rememberLazyListState()
    val memberOrder = remember(filteredApps) { filteredApps.map { it.packageName } }

    fun committedPackages(): Set<String> {
        val listed = orderedApps.map { it.packageName }.toSet()
        return selectedPackages.intersect(listed) + (session.packages - listed)
    }

    fun editSelection(next: Set<String>) {
        selectedPackages = next
        if (!initial.isBuiltin) return
        if (confirmedFreeze) {
            membershipMode = MembershipMode.Snapshot
            return
        }
        val listed = orderedApps.map { it.packageName }.toSet()
        membershipMode = if (next.intersect(listed) == initial.catalogPackages.intersect(listed)) {
            MembershipMode.Catalog
        } else {
            MembershipMode.Snapshot
        }
    }

    fun restoreCatalog() {
        confirmedFreeze = false
        membershipMode = MembershipMode.Catalog
        selectedPackages = initial.catalogPackages
    }

    val memberDirty = if (!initial.isBuiltin) {
        committedPackages() != initial.packages
    } else if (membershipMode == MembershipMode.Catalog) {
        initial.membershipFrozen
    } else {
        committedPackages() != initial.packages || !initial.membershipFrozen
    }
    val dirty = name.trim() != initial.name.trim() ||
        description.trim() != initial.description.trim() ||
        memberDirty

    fun commit(frozen: Boolean) {
        val payload = appSetEditSavePayload(session, name, description, committedPackages())
        onSave(payload.id, payload.name, payload.description, payload.packages, frozen)
    }

    fun trySave() {
        if (!session.isBuiltin && name.trim().isEmpty()) {
            nameError = true
            section = 0
            return
        }
        val saveFrozen = session.isBuiltin && membershipMode == MembershipMode.Snapshot
        if (saveFrozen && !confirmedFreeze) {
            confirmFreeze = true
            return
        }
        commit(saveFrozen)
    }

    val navBottom = iosSheetBottom()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    IosSheetFrame(
        onDismiss = onDismiss,
        attemptDismiss = {
            when {
                allowDismiss -> true
                confirmDelete || confirmDiscard || confirmFreeze -> {
                    confirmDelete = false
                    confirmDiscard = false
                    confirmFreeze = false
                    false
                }
                dirty -> {
                    confirmDiscard = true
                    false
                }
                else -> true
            }
        },
        panel = { close ->
            SideEffect { sheetClose.value = close }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = imeBottom)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(ShellShapes.SheetTop)
                    .background(colors.grouped)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (initial.id == null) {
                            stringResource(R.string.new_app_set)
                        } else {
                            stringResource(R.string.edit_app_set)
                        },
                        style = IosType.headline,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (!session.isBuiltin && session.id != null) {
                        IosIconButton(
                            icon = R.drawable.ic_baseline_delete,
                            description = stringResource(R.string.delete_app_set),
                            tint = colors.red,
                            onClick = { confirmDelete = true },
                        )
                    }
                    IosIconButton(
                        icon = R.drawable.ic_baseline_close,
                        description = stringResource(R.string.cancel),
                        onClick = close,
                    )
                }

                Spacer(Modifier.height(12.dp))

                IosSegmentedControl(
                    items = listOf(
                        stringResource(R.string.app_set_section_basic),
                        stringResource(R.string.app_set_section_members),
                    ),
                    selected = section,
                    onSelect = { section = it },
                    horizontalInset = 0.dp,
                )

                Spacer(Modifier.height(12.dp))

                if (section == 0) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        item(key = "app-set-basic") {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.app_set_name),
                                    style = IosType.footnote,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(Modifier.height(4.dp))
                                IosPlainField(
                                    value = name,
                                    onValueChange = {
                                        name = it
                                        nameError = false
                                    },
                                    placeholder = "",
                                )
                                if (nameError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.app_set_name_required),
                                        style = IosType.footnote,
                                        color = colors.red,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.app_set_description),
                                    style = IosType.footnote,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(Modifier.height(4.dp))
                                IosPlainField(
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = "",
                                )

                                if (initial.isBuiltin && membershipMode == MembershipMode.Snapshot) {
                                    Spacer(Modifier.height(8.dp))
                                    if (confirmedFreeze) {
                                        Text(
                                            text = stringResource(R.string.app_set_members_frozen),
                                            style = IosType.footnote,
                                            color = colors.secondaryLabel,
                                        )
                                    }
                                    IosTextButton(
                                        text = stringResource(R.string.restore_app_set_members),
                                        onClick = { restoreCatalog() },
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                AppSetMembersEntry(
                                    summary = if (editorAppsReady) {
                                        stringResource(
                                            R.string.member_apps_count,
                                            includedCount,
                                            orderedApps.size,
                                        )
                                    } else {
                                        stringResource(R.string.loading)
                                    },
                                    onClick = { section = 1 },
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        IosSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(R.string.search),
                            horizontalInset = 0.dp,
                            containerColor = colors.track,
                        )

                        Spacer(Modifier.height(8.dp))

                        if (editorAppsReady) {
                            Text(
                                text = stringResource(
                                    R.string.member_apps_count,
                                    includedCount,
                                    orderedApps.size,
                                ),
                                style = IosType.footnote,
                                color = colors.secondaryLabel,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val scoped = query.isNotEmpty()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PillActionButton(
                                label = stringResource(
                                    if (scoped) R.string.select_all_shown else R.string.select_all,
                                ),
                                onClick = {
                                    editSelection(selectedPackages + filteredApps.map { it.packageName })
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PillActionButton(
                                label = stringResource(
                                    if (scoped) R.string.select_none_shown else R.string.select_none,
                                ),
                                onClick = {
                                    val visible = filteredApps.map { it.packageName }.toSet()
                                    editSelection(selectedPackages - visible)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PillActionButton(
                                label = stringResource(
                                    if (scoped) R.string.select_invert_shown else R.string.select_invert,
                                ),
                                onClick = {
                                    val visible = filteredApps.map { it.packageName }.toSet()
                                    val inSet = selectedPackages.intersect(visible)
                                    val notInSet = visible - selectedPackages
                                    editSelection((selectedPackages - inSet) + notInSet)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        when {
                            !editorAppsReady -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IosProgressRow(stringResource(R.string.loading))
                                }
                            }
                            filteredApps.isEmpty() -> {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(ShellShapes.Container)
                                        .background(colors.background),
                                ) {
                                    IosEmptyState(
                                        message = stringResource(
                                            if (query.isEmpty()) R.string.list_empty else R.string.search_empty,
                                        ),
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    state = memberState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(ShellShapes.Container)
                                        .background(colors.background)
                                        .checkboxDragSelect(
                                            listState = memberState,
                                            order = memberOrder,
                                            selected = selectedPackages,
                                            onSelected = { editSelection(it) },
                                        ),
                                ) {
                                    itemsIndexed(filteredApps, key = { _, it -> it.packageName }) { index, app ->
                                        val inSet = app.packageName in selectedPackages
                                        val interaction = remember { MutableInteractionSource() }
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .drawBehind {
                                                    if (inSet) {
                                                        drawRect(colors.label.copy(alpha = 0.12f))
                                                    }
                                                }
                                                .shellPressBackground(interactionSource = interaction)
                                                .clickable(
                                                    interactionSource = interaction,
                                                    indication = null,
                                                ) {
                                                    editSelection(
                                                        if (inSet) {
                                                            selectedPackages - app.packageName
                                                        } else {
                                                            selectedPackages + app.packageName
                                                        },
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = app.label,
                                                    style = IosType.body,
                                                    color = colors.label,
                                                    fontWeight = if (inSet) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    style = IosType.footnote,
                                                    color = colors.secondaryLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IosCheckBox(
                                                if (inSet) IosSelection.Checked else IosSelection.None,
                                            )
                                        }
                                        if (index < filteredApps.lastIndex) {
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
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                IosButton(
                    text = stringResource(R.string.ok),
                    style = IosButtonStyle.Filled,
                    onClick = { trySave() },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(if (imeBottom > 0.dp) 16.dp else navBottom + 16.dp))
            }
        },
        overlay = {
            if (deleteId != null) {
                IosConfirmOverlay(
                    title = stringResource(R.string.delete_app_set),
                    message = stringResource(R.string.confirm_delete_app_set),
                    confirm = stringResource(R.string.delete),
                    destructive = true,
                    onFinished = { ok ->
                        val id = deleteId
                        confirmDelete = false
                        if (ok) onDelete(id)
                    },
                )
            }
            if (confirmDiscard) {
                IosConfirmOverlay(
                    title = stringResource(R.string.discard_app_set_edits),
                    message = stringResource(R.string.discard_app_set_edits_message),
                    confirm = stringResource(R.string.discard),
                    onFinished = { ok ->
                        confirmDiscard = false
                        if (ok) {
                            allowDismiss = true
                            sheetClose.value?.invoke()
                        }
                    },
                )
            }
            if (confirmFreeze) {
                IosConfirmOverlay(
                    title = stringResource(R.string.freeze_app_set_members),
                    message = stringResource(R.string.freeze_app_set_members_message),
                    confirm = stringResource(R.string.freeze_app_set),
                    onFinished = { ok ->
                        confirmFreeze = false
                        if (ok) commit(true)
                    },
                )
            }
        },
    )
}

@Composable
private fun AppSetMembersEntry(
    summary: String,
    onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ShellShapes.Control)
            .background(colors.track)
            .shellPressBackground(ShellShapes.Control, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_set_section_members),
                style = IosType.headline,
                color = colors.label,
            )
            Text(
                text = summary,
                style = IosType.footnote,
                color = colors.secondaryLabel,
            )
        }
        PreferenceChevron(color = colors.tertiaryLabel, chevronSize = 16.dp)
    }
}

@Composable
private fun IosPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = IosTheme.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(ShellShapes.Control)
            .background(colors.track)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) colors.blue else Color.Transparent,
                shape = ShellShapes.Control,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = colors.label,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(colors.blue),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = IosType.body,
                color = colors.tertiaryLabel,
            )
        }
    }
}

@Composable
private fun PillActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(ShellShapes.Pill)
            .shellPressBackground(ShellShapes.Pill, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = IosType.footnote,
            color = colors.blue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = IosType.section,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun hideSystemAppsLabel(): String {
    val config = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= 24) config.locales[0] else config.locale
    return when (locale.language) {
        "zh" -> when (locale.country) {
            "TW", "HK", "MO" -> "隱藏系統應用"
            else -> "隐藏系统应用"
        }
        else -> "Hide system apps"
    }
}
