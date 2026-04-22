package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.design.adapter.AppAdapter
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.databinding.DesignAccessControlBinding
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.AccessControlMode
import kotlinx.coroutines.*

class AccessControlDesign(
    context: Context,
    uiStore: UiStore,
    private val selected: MutableSet<String>,
    mode: AccessControlMode,
) : Design<AccessControlDesign.Request>(context) {
    private enum class NamespaceFilter {
        All,
        Ru,
        Cn,
        Com,
        Other,
    }

    enum class Request {
        ReloadApps,
        SelectAll,
        SelectNone,
        SelectInvert,
        Import,
        Export,
        ChangeMode,
    }

    var pendingMode: AccessControlMode? = null
        private set

    private val binding = DesignAccessControlBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = AppAdapter(context, selected)

    private val menu: AccessControlMenu by lazy {
        AccessControlMenu(context, binding.menuView, uiStore, requests)
    }
    private var allApps: List<AppInfo> = emptyList()
    private var namespaceFilter: NamespaceFilter = NamespaceFilter.All

    val apps: List<AppInfo>
        get() = adapter.apps

    override val root: View
        get() = binding.root

    suspend fun patchApps(apps: List<AppInfo>) {
        allApps = apps
        applyFilter(binding.searchInput.text?.toString().orEmpty())
        updateCounter()
    }

    suspend fun rebindAll() {
        withContext(Dispatchers.Main) {
            adapter.rebindAll()
            updateCounter()
        }
    }

    fun setMode(mode: AccessControlMode) {
        when (mode) {
            AccessControlMode.AcceptAll -> binding.modeGroup.check(binding.modeAllButton.id)
            AccessControlMode.AcceptSelected -> binding.modeGroup.check(binding.modeAllowButton.id)
            AccessControlMode.DenySelected -> binding.modeGroup.check(binding.modeDenyButton.id)
        }
    }

    fun requestModeAcceptAll() = requestModeChange(AccessControlMode.AcceptAll)
    fun requestModeAcceptSelected() = requestModeChange(AccessControlMode.AcceptSelected)
    fun requestModeDenySelected() = requestModeChange(AccessControlMode.DenySelected)

    private fun requestModeChange(mode: AccessControlMode) {
        pendingMode = mode
        requests.trySend(Request.ChangeMode)
    }

    private fun updateCounter() {
        val total = allApps.size
        val selectedCount = allApps.count { it.packageName in selected }
        binding.counterView.text = context.getString(
            R.string.app_routing_counter,
            selectedCount,
            total
        )
    }

    private suspend fun applyFilter(keyword: String) {
        val filtered = withContext(Dispatchers.Default) {
            allApps.filter { app ->
                val keywordMatched = keyword.isBlank() ||
                    app.label.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
                val namespaceMatched = when (namespaceFilter) {
                    NamespaceFilter.All -> true
                    NamespaceFilter.Ru -> app.packageName.startsWith("ru.")
                    NamespaceFilter.Cn -> app.packageName.startsWith("cn.")
                    NamespaceFilter.Com -> app.packageName.startsWith("com.")
                    NamespaceFilter.Other ->
                        !app.packageName.startsWith("ru.") &&
                            !app.packageName.startsWith("cn.") &&
                            !app.packageName.startsWith("com.")
                }
                keywordMatched && namespaceMatched
            }
        }
        adapter.swapDataSet(adapter::apps, filtered, false)
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
        binding.activityBarLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val recycler = binding.mainList.recyclerList
            val top = surface.insets.top + binding.activityBarLayout.height
            if (recycler.paddingTop != top) {
                recycler.setPadding(recycler.paddingLeft, top, recycler.paddingRight, recycler.paddingBottom)
            }
        }

        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.searchInput.addTextChangedListener {
            launch {
                applyFilter(it?.toString().orEmpty())
                updateCounter()
            }
        }
        binding.namespaceFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            namespaceFilter = when (checkedIds.firstOrNull()) {
                binding.filterRuChip.id -> NamespaceFilter.Ru
                binding.filterCnChip.id -> NamespaceFilter.Cn
                binding.filterComChip.id -> NamespaceFilter.Com
                binding.filterOtherChip.id -> NamespaceFilter.Other
                else -> NamespaceFilter.All
            }
            launch {
                applyFilter(binding.searchInput.text?.toString().orEmpty())
                updateCounter()
            }
        }

        setMode(mode)
        updateCounter()
    }
}