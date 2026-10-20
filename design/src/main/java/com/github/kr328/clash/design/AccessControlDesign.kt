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
import com.github.kr328.clash.design.ios.AccessControlScreen
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.AppSetIds
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
    enum class Request {
        ReloadApps,
        SelectAll,
        SelectNone,
        SelectInvert,
        Import,
        Export,
    }

    /** Unfiltered `loadAccessControlApps` rows. Search must not replace this. */
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    private var searchQuery by mutableStateOf("")

    private var keyword by mutableStateOf("")

    private var selection by mutableStateOf(selected.toSet())

    private var systemPackages by mutableStateOf<Set<String>>(emptySet())

    private var domesticPackages by mutableStateOf<Set<String>>(emptySet())

    private var loading by mutableStateOf(true)

    private var hideSystemApps by mutableStateOf(!uiStore.accessControlSystemApp)

    private var sort by mutableStateOf(uiStore.accessControlSort)

    private var reversed by mutableStateOf(uiStore.accessControlReverse)

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
            val openedFromStrategy = activity?.intent?.uuid != null
            val title = if (!openedFromStrategy && (currentTitle == null || currentTitle == legacyTitle)) {
                context.getString(R.string.global_app_list)
            } else {
                currentTitle ?: legacyTitle
            }
            ClashMetaXTheme {
                AccessControlScreen(
                    insets = insets,
                    title = title,
                    query = searchQuery,
                    keyword = keyword,
                    apps = apps,
                    selection = selection,
                    systemPackages = systemPackages,
                    domesticPackages = domesticPackages,
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
                    onToggleSet = ::toggleSet,
                    onSelectAll = { request(Request.SelectAll) },
                    onSelectNone = { request(Request.SelectNone) },
                    onSelectInvert = { request(Request.SelectInvert) },
                    onSort = ::chooseSort,
                    onReverse = ::toggleReverse,
                    onImport = { request(Request.Import) },
                    onExport = { request(Request.Export) },
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

    suspend fun patchBuiltinSets(systemPackages: Set<String>, domesticPackages: Set<String>) {
        withContext(Dispatchers.Main) {
            this@AccessControlDesign.systemPackages = systemPackages
            this@AccessControlDesign.domesticPackages = domesticPackages
            setsReady = true
            if (appsReady && setsReady) loading = false
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
        // Stored false means system rows stay hidden. The switch is the opposite.
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

    private fun toggleSet(id: String) {
        val members = when (id) {
            AppSetIds.SYSTEM -> systemPackages
            AppSetIds.DOMESTIC -> domesticPackages
            else -> return
        }
        if (members.isEmpty()) return
        toggleMembers(members)
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
