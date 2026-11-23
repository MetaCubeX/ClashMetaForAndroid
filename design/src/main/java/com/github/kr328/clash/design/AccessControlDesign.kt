package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.common.util.uuid
import universe.agentic.clash.AccessControlScreen
import universe.agentic.clash.AgenticClashTheme
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetUi
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessControlDesign(
    context: Context,
    private val uiStore: UiStore,
    private val selected: MutableSet<String>,
    private val toggleMembers: (Set<String>) -> Unit,
) : Design<AccessControlDesign.Request>(context) {
    sealed interface Request {
        data object ReloadApps : Request
        data object SelectAll : Request
        data object SelectNone : Request
        data object SelectInvert : Request
        data object Import : Request
        data object Export : Request
        data class SaveAppSet(
            val id: String?,
            val name: String,
            val description: String,
            val packages: List<String>,
            val membershipFrozen: Boolean,
        ) : Request
        data class DeleteAppSet(val id: String) : Request
        data object LoadEditorApps : Request
    }

    /** Unfiltered `loadAccessControlApps` rows. Search must not replace this. */
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    private var searchQuery by mutableStateOf("")

    private var keyword by mutableStateOf("")

    private var selection by mutableStateOf(selected.toSet())

    var appSets by mutableStateOf<List<AppSetUi>>(emptyList())
        private set

    private var loading by mutableStateOf(true)

    private var hideSystemApps by mutableStateOf(!uiStore.accessControlSystemApp)

    private var sort by mutableStateOf(uiStore.accessControlSort)

    private var reversed by mutableStateOf(uiStore.accessControlReverse)

    var openSetId by mutableStateOf<String?>(null)

    var createSet by mutableStateOf(false)

    var appSetsOnly by mutableStateOf(false)

    var closeWhenEditorCloses by mutableStateOf(false)

    var editorApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var editorAppsReady by mutableStateOf(false)
        private set

    private var editorRequested = false

    private var insets by mutableStateOf(Insets.EMPTY)

    private var appsReady = false

    private var setsReady = false

    private var searchDebounce: Job? = null

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val activity = context as? Activity
            val currentTitle = activity?.title?.toString()?.takeIf { it.isNotBlank() }
            val legacyTitle = context.getString(R.string.access_control_packages)
            val title = currentTitle ?: legacyTitle
            AgenticClashTheme {
                AccessControlScreen(
                    insets = insets,
                    title = title,
                    query = searchQuery,
                    keyword = keyword,
                    apps = apps,
                    selection = selection,
                    appSets = appSets,
                    loading = loading,
                    hideSystem = hideSystemApps,
                    sort = sort,
                    reversed = reversed,
                    onBack = {
                        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onQueryChange = ::onSearchQueryChange,
                    onHideSystem = ::applyHideSystemApps,
                    onToggleApp = ::toggleApp,
                    onReplaceSelection = ::replaceSelection,
                    onToggleSet = ::toggleSet,
                    onSaveAppSet = { id, name, description, pkgs, frozen ->
                        request(Request.SaveAppSet(id, name, description, pkgs, frozen))
                    },
                    openSetId = openSetId,
                    onOpenSetConsumed = { openSetId = null },
                    onDeleteAppSet = { id ->
                        request(Request.DeleteAppSet(id))
                    },
                    editorApps = editorApps,
                    editorAppsReady = editorAppsReady,
                    onPrepareEditorApps = ::prepareEditorApps,
                    createSet = createSet,
                    onCreateSetConsumed = { createSet = false },
                    closeWhenEditorCloses = closeWhenEditorCloses,
                    onEditorClosed = {
                        if (closeWhenEditorCloses) {
                            (context as? Activity)?.finish()
                        }
                    },
                    onSelectAll = { request(Request.SelectAll) },
                    onSelectNone = { request(Request.SelectNone) },
                    onSelectInvert = { request(Request.SelectInvert) },
                    onSort = ::chooseSort,
                    onReverse = ::toggleReverse,
                    onImport = { request(Request.Import) },
                    onExport = { request(Request.Export) },
                    appSetsOnly = appSetsOnly,
                )
            }
        }
    }

    suspend fun patchApps(apps: List<AppInfo>) {
        withContext(Dispatchers.Main) {
            this@AccessControlDesign.apps = apps
            appsReady = true
            if (appsReady && setsReady) loading = false
        }
    }

    suspend fun patchAppSets(sets: List<AppSetUi>) {
        withContext(Dispatchers.Main) {
            this@AccessControlDesign.appSets = sets
            setsReady = true
            if (appsReady && setsReady) loading = false
        }
    }

    fun prepareEditorApps() {
        if (editorAppsReady || editorRequested) return
        editorRequested = true
        request(Request.LoadEditorApps)
    }

    fun clearEditorApps() {
        editorApps = emptyList()
        editorAppsReady = false
        editorRequested = false
    }

    suspend fun patchEditorApps(apps: List<AppInfo>) {
        withContext(Dispatchers.Main) {
            editorApps = apps
            editorAppsReady = true
        }
    }

    suspend fun rebindAll() {
        withContext(Dispatchers.Main) {
            selection = selected.toSet()
        }
    }

    private fun onSearchQueryChange(value: String) {
        searchQuery = value
        searchDebounce?.cancel()
        searchDebounce = launch {
            delay(SEARCH_DEBOUNCE_MS)
            withContext(Dispatchers.Main) {
                if (keyword != value) keyword = value
            }
        }
    }

    private fun applyHideSystemApps(hide: Boolean) {
        uiStore.accessControlSystemApp = !hide
        hideSystemApps = hide
        request(Request.ReloadApps)
    }

    private fun chooseSort(value: AppInfoSort) {
        uiStore.accessControlSort = value
        sort = value
        request(Request.ReloadApps)
    }

    private fun toggleReverse() {
        val next = !uiStore.accessControlReverse
        uiStore.accessControlReverse = next
        reversed = next
        request(Request.ReloadApps)
    }

    private fun toggleApp(packageName: String) {
        if (packageName in selected) {
            selected.remove(packageName)
        } else {
            selected.add(packageName)
        }
        selection = selected.toSet()
    }

    private fun replaceSelection(next: Set<String>) {
        selected.clear()
        selected.addAll(next)
        selection = selected.toSet()
    }

    private fun toggleSet(id: String) {
        val set = appSets.find { it.id == id } ?: return
        if (set.packages.isEmpty()) return
        toggleMembers(set.packages)
        selection = selected.toSet()
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
