package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import universe.agentic.clash.AgenticClashTheme
import universe.agentic.clash.ProviderRowUi
import universe.agentic.clash.ProxyLinkUi
import universe.agentic.clash.ProxyPageUi
import universe.agentic.clash.ProxyScreen
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    private val groupNames: List<String>,
    initialIndex: Int,
    private val uiStore: UiStore,
    private val states: List<ProxyState>,
    private val onUserGroup: (String) -> Unit,
    private val embedded: Boolean = false,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
        data class UpdateProvider(val index: Int, val provider: Provider) : Request()
    }

    private class MutableProvider(
        val index: Int,
        val provider: Provider,
        updatedAt: Long,
    ) {
        var updatedAt by mutableStateOf(updatedAt)
        var updating by mutableStateOf(false)
    }

    private data class Page(
        val name: String,
        val type: String = "",
        val proxies: List<Proxy> = emptyList(),
        val selectable: Boolean = false,
        val now: String = "",
        val loaded: Boolean = false,
        val loading: Boolean = false,
        val urlTesting: Boolean = false,
    )

    private var insets by mutableStateOf(Insets.EMPTY)
    private var current by mutableStateOf(initialIndex)
    private var sort by mutableStateOf(uiStore.proxySort)
    private var excludeNotSelectable by mutableStateOf(uiStore.proxyExcludeNotSelectable)
    private var pages by mutableStateOf(
        List(groupNames.size) { index ->
            Page(
                name = groupNames[index],
                loading = index == initialIndex,
            )
        }
    )
    private var relaunching = false
    private var providerLines by mutableStateOf<List<MutableProvider>>(emptyList())
    private var providersLoaded by mutableStateOf(false)
    private var providersUnavailable by mutableStateOf(false)
    private var providersNow by mutableStateOf(System.currentTimeMillis())
    var sectionJump by mutableIntStateOf(0)
        private set
    var sectionIndex by mutableIntStateOf(0)
        private set

    val currentGroupName: String?
        get() = pages.getOrNull(current)?.name

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(
            if (embedded) ViewCompositionStrategy.DisposeOnDetachedFromWindow
            else ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        val title = if (embedded) {
            context.getString(R.string.page_proxies)
        } else {
            (context as? Activity)?.title?.toString()?.ifBlank { null }
                ?: context.getString(R.string.proxy)
        }
        setContent {
            AgenticClashTheme {
                val page = pages.getOrNull(current)
                ProxyScreen(
                    insets = insets,
                    title = title,
                    groupNames = groupNames,
                    current = current,
                    page = page?.let {
                        ProxyPageUi(
                            name = it.name,
                            type = it.type,
                            now = it.now,
                            proxies = it.proxies,
                            selectable = it.selectable,
                            loaded = it.loaded,
                            loading = it.loading,
                            urlTesting = it.urlTesting,
                        )
                    },
                    links = pages.map { ProxyLinkUi(it.name, it.loaded, it.now) },
                    sort = sort,
                    excludeNotSelectable = excludeNotSelectable,
                    onBack = {
                        (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onPickGroup = ::pickGroup,
                    onSelect = ::select,
                    onUrlTest = ::requestUrlTesting,
                    onSort = ::applySort,
                    onExclude = ::setExclude,
                    providers = providerLines.map { line ->
                        ProviderRowUi(
                            index = line.index,
                            provider = line.provider,
                            updatedAt = line.updatedAt,
                            updating = line.updating,
                        )
                    },
                    providersNow = providersNow,
                    providersLoaded = providersLoaded,
                    providersUnavailable = providersUnavailable,
                    onUpdateProvider = ::requestProviderUpdate,
                    onUpdateAllProviders = ::requestProviderUpdateAll,
                    embedded = embedded,
                    sectionJump = sectionJump,
                    sectionIndex = sectionIndex,
                )
            }
        }
    }

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>,
        type: String,
        clearUrlTesting: Boolean = false,
    ) {
        withContext(Dispatchers.Main) {
            if (position !in pages.indices) return@withContext
            pages = pages.mapIndexed { index, page ->
                when {
                    index == position -> page.copy(
                        type = type,
                        proxies = proxies,
                        selectable = selectable,
                        now = parent.now,
                        loaded = true,
                        loading = false,
                        urlTesting = if (clearUrlTesting) false else page.urlTesting,
                    )
                    page.loaded -> {
                        val now = links[page.name]?.now
                        if (now != null && now != page.now) page.copy(now = now) else page
                    }
                    else -> page
                }
            }
        }
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            pages = pages.mapIndexed { index, page ->
                val now = states.getOrNull(index)?.now ?: return@mapIndexed page
                if (now == page.now) page else page.copy(now = now)
            }
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    fun requestUrlTesting() {
        val index = current
        val page = pages.getOrNull(index) ?: return
        if (page.urlTesting) return
        pages = pages.mapIndexed { i, item ->
            if (i == index) item.copy(urlTesting = true) else item
        }
        requests.trySend(Request.UrlTest(index))
    }

    suspend fun setProviders(list: List<Provider>) {
        withContext(Dispatchers.Main) {
            providerLines = list.mapIndexed { index, provider ->
                MutableProvider(index, provider, provider.updatedAt)
            }
            providersLoaded = true
            providersUnavailable = false
            providersNow = System.currentTimeMillis()
        }
    }

    suspend fun setProvidersUnavailable() {
        withContext(Dispatchers.Main) {
            providerLines = emptyList()
            providersLoaded = true
            providersUnavailable = true
        }
    }

    suspend fun notifyProviderChanged(index: Int) {
        withContext(Dispatchers.Main) {
            providerLines.getOrNull(index)?.let { line ->
                line.updating = false
                line.updatedAt = System.currentTimeMillis()
            }
        }
    }

    suspend fun notifyProviderUpdated(index: Int) {
        withContext(Dispatchers.Main) {
            providerLines.getOrNull(index)?.updating = false
        }
    }

    fun updateProviderElapsed() {
        providersNow = System.currentTimeMillis()
    }

    private fun requestProviderUpdate(index: Int) {
        val line = providerLines.getOrNull(index) ?: return
        if (line.updating) return
        if (line.provider.vehicleType == Provider.VehicleType.Inline) return
        line.updating = true
        requests.trySend(Request.UpdateProvider(line.index, line.provider))
    }

    private fun requestProviderUpdateAll() {
        providerLines.forEach { line ->
            if (line.updating) return@forEach
            if (line.provider.vehicleType == Provider.VehicleType.Inline) return@forEach
            line.updating = true
            requests.trySend(Request.UpdateProvider(line.index, line.provider))
        }
    }

    fun openSection(index: Int) {
        sectionIndex = index
        sectionJump += 1
    }

    fun showGroup(name: String) {
        openSection(0)
        val index = groupNames.indexOf(name)
        if (index !in pages.indices || index == current) return
        current = index
        pages = pages.mapIndexed { i, item ->
            if (i == index) item.copy(loading = true) else item
        }
        requests.trySend(Request.Reload(index))
    }

    fun reloadGroup(name: String) {
        val index = groupNames.indexOf(name)
        if (index !in pages.indices) return
        requests.trySend(Request.Reload(index))
    }

    private fun pickGroup(index: Int) {
        val page = pages.getOrNull(index) ?: return
        onUserGroup(page.name)
        if (index == current) return
        current = index
        pages = pages.mapIndexed { i, item ->
            if (i == index) item.copy(loading = true) else item
        }
        requests.trySend(Request.Reload(index))
    }

    private fun select(name: String) {
        val index = current
        val page = pages.getOrNull(index) ?: return
        if (!page.selectable) return
        requests.trySend(Request.Select(index, name))
    }

    private fun applySort(value: ProxySort) {
        if (value == sort) return
        uiStore.proxySort = value
        sort = value
        val index = current
        if (index !in pages.indices) return
        pages = pages.mapIndexed { i, page ->
            if (i == index) page.copy(loading = true) else page
        }
        requests.trySend(Request.Reload(index))
    }

    private fun setExclude(value: Boolean) {
        if (relaunching || value == excludeNotSelectable) return
        uiStore.proxyExcludeNotSelectable = value
        excludeNotSelectable = value
        relaunching = true
        requests.trySend(Request.ReLaunch)
    }

    init {
        if (context is AppCompatActivity) {
            context.window.decorView.setOnInsertsChangedListener {
                if (surface.insets != it) surface.insets = it
                insets = it
            }
        }
    }
}
