package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.core.view.updatePadding
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

    /** Latest live filter text from the inline search box. */
    var inlineFilter: String = ""
        private set

    private val binding = DesignAccessControlBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = AppAdapter(context, selected)

    private val menu: AccessControlMenu by lazy {
        AccessControlMenu(context, binding.menuView, uiStore, requests)
    }

    val apps: List<AppInfo>
        get() = adapter.apps

    /** All apps currently loaded by the host (pre-filter). Used for accurate counters. */
    private var allApps: List<AppInfo> = emptyList()

    private var namespaceFilter: NamespaceFilter = NamespaceFilter.All

    override val root: View
        get() = binding.root

    suspend fun patchApps(apps: List<AppInfo>) {
        allApps = apps
        val filtered = applyFilterLocked(apps, inlineFilter)
        adapter.swapDataSet(adapter::apps, filtered, false)
        refreshCounter()
    }

    suspend fun rebindAll() {
        withContext(Dispatchers.Main) {
            adapter.rebindAll()
            refreshCounter()
        }
    }

    fun setMode(mode: AccessControlMode) {
        applyModeButtonsState(mode)
    }

    private fun refreshCounter() {
        val total = allApps.size
        val sel = allApps.count { it.packageName in selected }
        binding.counterView.text = context.getString(R.string.app_routing_counter, sel, total)
    }

    private fun matchesNamespace(app: AppInfo): Boolean = when (namespaceFilter) {
        NamespaceFilter.All -> true
        NamespaceFilter.Ru -> app.packageName.startsWith("ru.")
        NamespaceFilter.Cn -> app.packageName.startsWith("cn.")
        NamespaceFilter.Com -> app.packageName.startsWith("com.")
        NamespaceFilter.Other ->
            !app.packageName.startsWith("ru.") &&
                !app.packageName.startsWith("cn.") &&
                !app.packageName.startsWith("com.")
    }

    private fun applyFilterLocked(source: List<AppInfo>, keyword: String): List<AppInfo> =
        source.filter { app ->
            matchesNamespace(app) &&
                (keyword.isBlank() ||
                    app.label.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true))
        }

    private fun launchApplyFilteredList() {
        val filtered = applyFilterLocked(allApps, inlineFilter)
        launch {
            adapter.patchDataSet(adapter::apps, filtered, false, AppInfo::packageName)
            refreshCounter()
        }
    }

    private fun applyModeButtonsState(mode: AccessControlMode) {
        val id = when (mode) {
            AccessControlMode.AcceptAll -> R.id.mode_all_button
            AccessControlMode.AcceptSelected -> R.id.mode_allow_button
            AccessControlMode.DenySelected -> R.id.mode_deny_button
        }
        if (binding.modeGroup.checkedButtonId != id) {
            binding.modeGroup.check(id)
        }
    }

    fun requestModeAcceptAll() = requestModeChange(AccessControlMode.AcceptAll)
    fun requestModeAcceptSelected() = requestModeChange(AccessControlMode.AcceptSelected)
    fun requestModeDenySelected() = requestModeChange(AccessControlMode.DenySelected)

    private fun requestModeChange(mode: AccessControlMode) {
        pendingMode = mode
        requests.trySend(Request.ChangeMode)
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }

        // Расширенный bar (toolbar + поиск + переключатель режима) выше дефолтного
        // toolbar_height из common_recycler_list, поэтому верхние карточки списка
        // оказываются под баром. Подгоняем верхний padding RecyclerView под реальную
        // высоту бара после каждой раскладки.
        binding.activityBarLayout.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val recycler = binding.mainList.recyclerList
            if (recycler.paddingTop != v.height) {
                recycler.updatePadding(top = v.height)
            }
        }

        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.searchInput.addTextChangedListener {
            inlineFilter = it?.toString()?.trim().orEmpty()
            launchApplyFilteredList()
        }

        binding.namespaceFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            namespaceFilter = when (checkedIds.firstOrNull()) {
                binding.filterRuChip.id -> NamespaceFilter.Ru
                binding.filterCnChip.id -> NamespaceFilter.Cn
                binding.filterComChip.id -> NamespaceFilter.Com
                binding.filterOtherChip.id -> NamespaceFilter.Other
                else -> NamespaceFilter.All
            }
            launchApplyFilteredList()
        }

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.mode_all_button -> AccessControlMode.AcceptAll
                R.id.mode_allow_button -> AccessControlMode.AcceptSelected
                R.id.mode_deny_button -> AccessControlMode.DenySelected
                else -> return@addOnButtonCheckedListener
            }
            if (pendingMode == mode) return@addOnButtonCheckedListener
            pendingMode = mode
            requests.trySend(Request.ChangeMode)
        }
    }
}