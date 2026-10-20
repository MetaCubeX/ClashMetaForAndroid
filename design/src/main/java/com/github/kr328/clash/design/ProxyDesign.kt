package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ios.ClashMetaXTheme
import com.github.kr328.clash.design.ios.ProxyLinkUi
import com.github.kr328.clash.design.ios.ProxyPageUi
import com.github.kr328.clash.design.ios.ProxyScreen
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
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
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

    val currentGroupName: String?
        get() = pages.getOrNull(current)?.name

    override val root: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val title = (context as? Activity)?.title?.toString()
            ?.ifBlank { null }
            ?: context.getString(R.string.proxy)
        setContent {
            ClashMetaXTheme {
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
